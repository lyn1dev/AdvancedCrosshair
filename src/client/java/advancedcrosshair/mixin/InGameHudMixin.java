package advancedcrosshair.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {

    @Shadow private Minecraft minecraft;

    @Inject(
        method = "renderCrosshair",
        at = @At("HEAD"),
        cancellable = true
    )
    private void changeCrosshairColor(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        Options options = minecraft.options;
        if (options == null || !options.getCameraType().isFirstPerson()) {
            return;
        }

        if (options.hideGui) {
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
        renderColoredCrosshair(graphics, crosshairColor);
        renderAttackIndicator(graphics);
    }

    private void renderColoredCrosshair(GuiGraphicsExtractor graphics, int color) {
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        int crosshairSize = 4;
        int thickness = 1;
        int gap = 0;
        
        // Horizontal line
        graphics.fill(centerX - crosshairSize - gap, centerY, 
                    centerX - gap, centerY + thickness, color);
        graphics.fill(centerX + gap, centerY,
                    centerX + crosshairSize + gap + 1, centerY + thickness, color);
        
        // Vertical line
        graphics.fill(centerX, centerY - crosshairSize - gap,
                    centerX + thickness, centerY - gap, color);
        graphics.fill(centerX, centerY + gap,
                    centerX + thickness, centerY + crosshairSize + gap + 1, color);
    }

    private boolean isReadyForCriticalHit() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }

        // Velocity provides instant client-side feedback for falling state.
        boolean isFalling = minecraft.player.getDeltaMovement().y < 0.0D 
                         && !minecraft.player.onGround() 
                         && !minecraft.player.onClimable() 
                         && !minecraft.player.isInWater(); // More reliable than isSwimming().

        if (!isFalling) return false;
        
        // Check for conditions that prevent critical hits.
        if (minecraft.player.hasEffect(MobEffects.BLINDNESS)) return false;
        if (minecraft.player.isPassenger()) return false;
        if (minecraft.player.isSprinting()) return false;

        return isLookingAtValidTarget();
    }

    private boolean isLookingAtValidTarget() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }

        if (minecraft.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        } else if (minecraft.crosshairTarget instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        }

        return false;
    }

    private boolean isLookingAtLivingEntityWithReadyAttack() {
        // Simple check for a valid target.
        return isLookingAtValidTarget();
    }

    private void renderAttackIndicator(GuiGraphicsExtractor graphics) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        float attackCooldown = minecraft.player.getAttackStrengthScale(0.0F);
        if (attackCooldown >= 1.0F) {
            return; // No indicator needed if attack is fully charged.
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        int indicatorWidth = 16;
        int indicatorHeight = 2;
        int indicatorOffset = 10;

        int x = centerX - indicatorWidth / 2;
        int y = centerY + indicatorOffset;

        // Background of the indicator bar.
        graphics.fill(x, y, x + indicatorWidth, y + indicatorHeight, 0x80808080);

        // Foreground showing cooldown progress.
        int progressWidth = (int) (indicatorWidth * attackCooldown);
        graphics.fill(x, y, x + progressWidth, y + indicatorHeight, 0xFFFFFFFF);
    }
}
