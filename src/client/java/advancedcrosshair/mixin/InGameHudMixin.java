package advancedcrosshair.mixin;
import net.minecraft.util.Identifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.AttackIndicator;
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

    private static final Identifier CROSSHAIR_TEXTURE = Identifier.of("minecraft:textures/gui/sprites/hud/crosshair.png");
    private static final Identifier ATTACK_INDICATOR_PROGRESS_TEXTURE = Identifier.of("minecraft:textures/gui/sprites/hud/crosshair_attack_indicator_progress.png");
    private static final Identifier ATTACK_INDICATOR_FULL_TEXTURE = Identifier.of("minecraft:textures/gui/sprites/hud/crosshair_attack_indicator_full.png");

    @Inject(
        method = "renderCrosshair",
        at = @At("HEAD"),
        cancellable = true
    )
    private void changeCrosshairColor(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter, CallbackInfo ci) {
        // Ensure client, player, and world are initialized
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;
        if (options == null || !options.getPerspective().isFirstPerson()) {
            return;
        }

        // Cancel the original crosshair rendering
        ci.cancel();

        // Check if crosshairs are hidden
        if (options.hudHidden) {
            return;
        }

        // Get screen center
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        // Determine crosshair color
        boolean isCriticalHitReady = isReadyForCriticalHit();
        boolean isAttackReady = !isCriticalHitReady && isLookingAtLivingEntityWithReadyAttack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (isCriticalHitReady) {
            RenderSystem.setShaderColor(0.0F, 0.0F, 1.0F, 1.0F); // Blue
        } else if (isAttackReady) {
            RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F); // Red
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.75F); // Semi-transparent white
        }

        // Draw the crosshair
        context.drawTexture(CROSSHAIR_TEXTURE, centerX - 7, centerY - 7, 0, 0, 15, 15, 15, 15);

        // Draw attack indicator
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderAttackIndicator(context, centerX, centerY);

        // Reset color and disable blend
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private void renderAttackIndicator(DrawContext context, int centerX, int centerY) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        if (client.options.getAttackIndicator().getValue() != AttackIndicator.CROSSHAIR) {
            return;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        boolean isReady = false;

        if (client.targetedEntity instanceof LivingEntity livingTarget && attackCooldown >= 1.0F) {
            isReady = client.player.getAttackCooldownProgressPerTick() > 5.0F;
            isReady &= (livingTarget.hurtTime <= 0);
        }

        int indicatorX = centerX - 8;
        int indicatorY = centerY + 9;

        if (attackCooldown < 1.0F) {
            int progressWidth = (int)(attackCooldown * 18.0F);
            if (progressWidth > 0) {
                context.drawTexture(ATTACK_INDICATOR_PROGRESS_TEXTURE, indicatorX, indicatorY, 0, 0, progressWidth, 4, 18, 4);
            }
        } else if (isReady) {
            context.drawTexture(ATTACK_INDICATOR_FULL_TEXTURE, indicatorX, indicatorY, 0, 0, 18, 4, 18, 4);
        }
    }

    private boolean isReadyForCriticalHit() {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown < 1.0F) {
            return false;
        }

        if (client.player.getVelocity().y >= 0.0D || client.player.isOnGround() ||
            client.player.isClimbing() || client.player.isSwimming()) {
            return false;
        }

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
        Vec3d lookVector = client.player.getRotationVec(1.0f);

        EntityHitResult entityHitResult = projectEntities(client.player, reachDistance);
        if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.isAlive() && livingEntity.hurtTime <= 0) {
                double actualDistance = eyePosition.distanceTo(entityHitResult.getPos());
                if (actualDistance <= reachDistance) {
                    return true;
                }
            }
        }

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
}
