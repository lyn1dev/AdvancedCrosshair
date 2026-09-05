package advancedcrosshair.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public class InGameHudMixin {

    /** Sentinel meaning "draw the crosshair exactly the way vanilla would". */
    private static final int NO_TINT = 0;
    private static final int CRIT_TINT = 0xFF0080FF;
    /** Sprite the vanilla HUD uses for the crosshair itself; resource packs restyle it but cannot rename it. */
    private static final String CROSSHAIR_SPRITE_PATH = "hud/crosshair";
    private static final int ATTACK_TINT = 0xFFFF3333;

    @Shadow private Minecraft minecraft;

    /**
     * Vanilla draws the crosshair with the sprite it looked up from the GUI atlas,
     * which is whatever the active resource pack provides. Rather than cancelling
     * the method and drawing our own shape, we intercept that one blit and re-issue
     * it with a tint, so the pack's artwork is what changes color.
     *
     * <p>The same overload also draws the attack indicator later in the method, so
     * rather than depending on call order we check the sprite and only tint the
     * crosshair. Everything else is forwarded untouched.
     */
    @Redirect(
        method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
        )
    )
    private void advancedcrosshair$tintCrosshair(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                                 Identifier sprite, int x, int y, int width, int height) {
        int tint = CROSSHAIR_SPRITE_PATH.equals(sprite.getPath()) ? crosshairTint() : NO_TINT;
        if (tint == NO_TINT) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }

        // The vanilla crosshair pipeline blends by inverting against the backdrop,
        // which would swallow the tint, so switch to the plain GUI pipeline while
        // keeping the same sprite.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, tint);
    }

    private int crosshairTint() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return NO_TINT;
        }
        if (!isLookingAtValidTarget()) {
            return NO_TINT;
        }
        return isReadyForCriticalHit() ? CRIT_TINT : ATTACK_TINT;
    }

    private boolean isReadyForCriticalHit() {
        // Velocity provides instant client-side feedback for falling state.
        boolean isFalling = minecraft.player.getDeltaMovement().y < 0.0D
                         && !minecraft.player.onGround()
                         && !minecraft.player.onClimbable()
                         && !minecraft.player.isInWater(); // More reliable than isSwimming().

        if (!isFalling) return false;

        // Check for conditions that prevent critical hits.
        if (minecraft.player.hasEffect(MobEffects.BLINDNESS)) return false;
        if (minecraft.player.isPassenger()) return false;
        if (minecraft.player.isSprinting()) return false;

        return true;
    }

    private boolean isLookingAtValidTarget() {
        if (minecraft.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        } else if (minecraft.crosshairPickEntity instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        }

        return false;
    }
}
