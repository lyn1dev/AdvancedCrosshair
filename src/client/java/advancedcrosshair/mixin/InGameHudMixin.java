package advancedcrosshair.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    /** Sentinel meaning "draw the crosshair exactly the way vanilla would". */
    private static final int NO_TINT = 0;
    private static final int CRIT_TINT = 0xFF0080FF;
    /** Sprite the vanilla HUD uses for the crosshair itself; resource packs restyle it but cannot rename it. */
    private static final String CROSSHAIR_SPRITE_PATH = "hud/crosshair";
    private static final int ATTACK_TINT = 0xFFFF3333;

    @Shadow private MinecraftClient client;

    /**
     * Vanilla draws the crosshair with the sprite it looked up from the GUI atlas,
     * which is whatever the active resource pack provides. Rather than cancelling
     * the method and drawing our own shape, we intercept that one draw and re-issue
     * it with a tint, so the pack's artwork is what changes color.
     *
     * <p>The same overload also draws the attack indicator later in the method, so
     * rather than depending on call order we check the sprite and only tint the
     * crosshair. Everything else is forwarded untouched.
     */
    @Redirect(
        method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
        )
    )
    private void advancedcrosshair$tintCrosshair(DrawContext context, RenderPipeline pipeline,
                                                 Identifier sprite, int x, int y, int width, int height) {
        int tint = CROSSHAIR_SPRITE_PATH.equals(sprite.getPath()) ? crosshairTint() : NO_TINT;
        if (tint == NO_TINT) {
            context.drawGuiTexture(pipeline, sprite, x, y, width, height);
            return;
        }

        // The vanilla crosshair pipeline blends by inverting against the backdrop,
        // which would swallow the tint, so switch to the plain GUI pipeline while
        // keeping the same sprite.
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, tint);
    }

    private int crosshairTint() {
        if (client == null || client.player == null || client.world == null) {
            return NO_TINT;
        }
        if (!isLookingAtValidTarget()) {
            return NO_TINT;
        }
        return isReadyForCriticalHit() ? CRIT_TINT : ATTACK_TINT;
    }

    private boolean isReadyForCriticalHit() {
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

        return true;
    }

    private boolean isLookingAtValidTarget() {
        if (client.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        } else if (client.targetedEntity instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        }

        return false;
    }
}
