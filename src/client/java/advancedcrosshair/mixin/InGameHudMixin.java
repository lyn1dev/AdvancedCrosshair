package advancedcrosshair.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Shadow private MinecraftClient client;

    @Inject(
        method = "renderCrosshair",
        at = @At("HEAD"),
        cancellable = true
    )
    private void changeCrosshairColor(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Ensure client, player, and world are initialized
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;
        if (options == null || !options.getPerspective().isFirstPerson()) {
            return;
        }

        // Check if crosshairs are hidden
        if (options.hudHidden) {
            return;
        }

        // Cancel the original crosshair rendering
        ci.cancel();

        // Determine crosshair color based on game state
        boolean isCriticalHitReady = isReadyForCriticalHit();
        boolean isAttackReady = !isCriticalHitReady && isLookingAtLivingEntityWithReadyAttack();

        // Determine color as ARGB integer
        int crosshairColor;
        if (isCriticalHitReady) {
            crosshairColor = 0xFF0080FF; // Blue (ARGB: Alpha=FF, Red=00, Green=80, Blue=FF)
        } else if (isAttackReady) {
            crosshairColor = 0xFFFF3333; // Red (ARGB: Alpha=FF, Red=FF, Green=33, Blue=33)
        } else {
            crosshairColor = 0xBFFFFFFF; // Semi-transparent white (ARGB: Alpha=BF, Red=FF, Green=FF, Blue=FF)
        }

        // Render the crosshair with the specific color
        renderColoredCrosshair(context, crosshairColor);

        // Render the attack indicator
        renderAttackIndicator(context);
    }

    private void renderColoredCrosshair(DrawContext context, int color) {
        // Get screen center
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        // Crosshair dimensions
        int crosshairSize = 4;
        int thickness = 1;
        int gap = 0; // Small gap in the center
        
        // Draw horizontal line (left and right parts)
        context.fill(centerX - crosshairSize - gap, centerY, 
                    centerX - gap, centerY + thickness, color);
        context.fill(centerX + gap, centerY,
                    centerX + crosshairSize + gap + 1, centerY + thickness, color);
        
        // Draw vertical line (top and bottom parts)
        context.fill(centerX, centerY - crosshairSize - gap,
                    centerX + thickness, centerY - gap, color);
        context.fill(centerX, centerY + gap,
                    centerX + thickness, centerY + crosshairSize + gap + 1, color);
    }

    private boolean isReadyForCriticalHit() {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown < 0.8F) {
            return false;
        }

        // Check if player is falling (required for critical hits)
        if (client.player.getVelocity().y >= 0.0D || client.player.isOnGround() ||
            client.player.isClimbing() || client.player.isSwimming()) {
            return false;
        }

        // Check status effects that prevent critical hits
        if (client.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }

        if (client.player.hasVehicle()) {
            return false;
        }

        if (client.player.isSprinting()) {
            return false;
        }

        return isLookingAtValidTarget(1.0F);
    }

    private boolean isLookingAtValidTarget(float minAttackCooldown) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown < minAttackCooldown) {
            return false;
        }

        double reachDistance = 3.0D;
        Vec3d eyePosition = client.player.getCameraPosVec(1.0f);

        // Check raycast hit result
        EntityHitResult entityHitResult = projectEntities(client.player, reachDistance);
        if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.isAlive() && livingEntity.hurtTime <= 0) {
                double actualDistance = eyePosition.distanceTo(entityHitResult.getPos());
                if (actualDistance <= reachDistance) {
                    return true;
                }
            }
        }

        // Check targeted entity
        if (client.targetedEntity instanceof LivingEntity livingTargeted &&
            livingTargeted.isAlive() && livingTargeted.hurtTime <= 0) {

            Vec3d entityCenter = livingTargeted.getPos().add(0, livingTargeted.getHeight() / 2, 0);
            double distanceToEntity = eyePosition.distanceTo(entityCenter);
            Box entityBox = livingTargeted.getBoundingBox();
            double distanceToBox = entityBox.getCenter().distanceTo(eyePosition);
            double finalDistance = Math.min(distanceToEntity, distanceToBox);

            if (finalDistance <= reachDistance) {
                return true;
            }
        }

        return false;
    }

    private boolean isLookingAtLivingEntityWithReadyAttack() {
        return isLookingAtValidTarget(1.0F);
    }

    private EntityHitResult projectEntities(Entity entity, double maxDistance) {
        if (entity == null) {
            return null;
        }

        Vec3d eyePosition = entity.getCameraPosVec(1.0f);
        Vec3d lookVector = entity.getRotationVec(1.0f);
        Vec3d traceEnd = eyePosition.add(lookVector.multiply(maxDistance));

        Box searchBox = entity.getBoundingBox()
            .stretch(lookVector.multiply(maxDistance))
            .expand(1.0D, 1.0D, 1.0D);

        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
            entity,
            eyePosition,
            traceEnd,
            searchBox,
            (entityToTest) -> !entityToTest.isSpectator() && entityToTest.isAttackable(),
            maxDistance * maxDistance
        );
    }

    private void renderAttackIndicator(DrawContext context) {
        if (client == null || client.player == null) {
            return;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown >= 1.0F) {
            return; // Don't render if attack is fully charged
        }

        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        // Indicator dimensions
        int indicatorWidth = 16;
        int indicatorHeight = 2;
        int indicatorOffset = 10; // Distance below the crosshair

        int x = centerX - indicatorWidth / 2;
        int y = centerY + indicatorOffset;

        // Background bar (grey)
        context.fill(x, y, x + indicatorWidth, y + indicatorHeight, 0x80808080);

        // Progress bar (white)
        int progressWidth = (int) (indicatorWidth * attackCooldown);
        context.fill(x, y, x + progressWidth, y + indicatorHeight, 0xFFFFFFFF);
    }
}
