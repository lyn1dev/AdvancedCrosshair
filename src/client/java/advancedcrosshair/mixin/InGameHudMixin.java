package advancedcrosshair.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
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
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;
        if (options == null || !options.getPerspective().isFirstPerson()) {
            return;
        }

        if (options.hudHidden) {
            return;
        }

        // Stop the default crosshair from rendering.
        ci.cancel();

        // Decide the crosshair color.
        boolean isCriticalHitReady = isReadyForCriticalHit();
        boolean isAttackReady = !isCriticalHitReady && isLookingAtLivingEntityWithReadyAttack();

        int crosshairColor;
        if (isCriticalHitReady) {
            crosshairColor = 0xFF0080FF; // Crit color
        } else if (isAttackReady) {
            crosshairColor = 0xFFFF3333; // Attackable color
        } else {
            crosshairColor = 0xBFFFFFFF; // Default color
        }

        // Draw the new crosshair and attack indicator.
        renderColoredCrosshair(context, crosshairColor);
        renderAttackIndicator(context);
    }

    private void renderColoredCrosshair(DrawContext context, int color) {
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        int crosshairSize = 4;
        int thickness = 1;
        int gap = 0;
        
        // Horizontal line
        context.fill(centerX - crosshairSize - gap, centerY, 
                    centerX - gap, centerY + thickness, color);
        context.fill(centerX + gap, centerY,
                    centerX + crosshairSize + gap + 1, centerY + thickness, color);
        
        // Vertical line
        context.fill(centerX, centerY - crosshairSize - gap,
                    centerX + thickness, centerY - gap, color);
        context.fill(centerX, centerY + gap,
                    centerX + thickness, centerY + crosshairSize + gap + 1, color);
    }

    private boolean isReadyForCriticalHit() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        // Velocity provides instant client-side feedback for falling state.
        boolean isFalling = client.player.getVelocity().y < 0.0D 
                         && !client.player.isOnGround() 
                         && !client.player.isClimbing() 
                         && !client.player.isTouchingWater(); // More reliable than isSwimming().

        if (!isFalling) return false;
        
        // Check for conditions that prevent critical hits.
        if (client.player.hasStatusEffect(StatusEffects.BLINDNESS)) return false;
        if (client.player.hasVehicle()) return false;
        if (client.player.isSprinting()) return false;

        return isLookingAtValidTarget();
    }

    private boolean isLookingAtValidTarget() {
        if (client == null || client.player == null) {
            return false;
        }

        if (client.targetedEntity instanceof LivingEntity livingTarget) {
            // hurtTime is ignored so the color doesn't flicker on hit.
            return livingTarget.isAlive();
        }

        return false;
    }

    private boolean isLookingAtLivingEntityWithReadyAttack() {
        // Simple check for a valid target.
        return isLookingAtValidTarget();
    }

    private void renderAttackIndicator(DrawContext context) {
        if (client == null || client.player == null) {
            return;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown >= 1.0F) {
            return; // No indicator needed if attack is fully charged.
        }

        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;

        int indicatorWidth = 16;
        int indicatorHeight = 2;
        int indicatorOffset = 10;

        int x = centerX - indicatorWidth / 2;
        int y = centerY + indicatorOffset;

        // Background of the indicator bar.
        context.fill(x, y, x + indicatorWidth, y + indicatorHeight, 0x80808080);

        // Foreground showing cooldown progress.
        int progressWidth = (int) (indicatorWidth * attackCooldown);
        context.fill(x, y, x + progressWidth, y + indicatorHeight, 0xFFFFFFFF);
    }
}
