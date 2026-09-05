package advancedcrosshair.mixin;

import advancedcrosshair.config.AdvancedCrosshairConfig;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.debug.DebugHudProfile;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    /** Sentinel meaning "draw exactly what vanilla would have drawn". */
    @Unique private static final int NO_TINT = 0;
    /** Sprite the vanilla HUD uses for the crosshair; packs restyle it but cannot rename it. */
    @Unique private static final String CROSSHAIR_SPRITE_PATH = "hud/crosshair";
    @Unique private static final String ATTACK_INDICATOR_PREFIX = "hud/crosshair_attack_indicator";

    /**
     * How far down the attack indicator has to move to clear a scaled-up
     * crosshair. Set while drawing the crosshair, which vanilla always does
     * first, and consumed by the indicator draws later in the same method.
     */
    @Unique private int advancedcrosshair$indicatorOffset;

    @Shadow private MinecraftClient client;

    /**
     * Vanilla draws the crosshair with the sprite it resolved from the GUI atlas,
     * which is whatever the active resource pack provides. Instead of cancelling
     * the method and drawing our own shape, we intercept that draw and re-issue it
     * scaled and tinted, so the pack's own artwork is what changes.
     *
     * <p>The same overload also draws the attack indicator, so we switch on the
     * sprite rather than on call order: the crosshair is scaled and tinted, the
     * indicator is only shifted down to stay clear of it, and anything else is
     * forwarded untouched.
     */
    @Redirect(
        method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
        )
    )
    private void advancedcrosshair$drawSprite(DrawContext context, RenderPipeline pipeline,
                                              Identifier sprite, int x, int y, int width, int height) {
        AdvancedCrosshairConfig config = AdvancedCrosshairConfig.get();
        if (!config.modEnabled) {
            advancedcrosshair$indicatorOffset = 0;
            context.drawGuiTexture(pipeline, sprite, x, y, width, height);
            return;
        }

        String path = sprite.getPath();

        if (CROSSHAIR_SPRITE_PATH.equals(path)) {
            int scaledWidth = Math.max(1, Math.round(width * config.crosshairScale));
            int scaledHeight = Math.max(1, Math.round(height * config.crosshairScale));
            // Half the growth is how much further the crosshair now reaches below centre.
            advancedcrosshair$indicatorOffset = (scaledHeight - height) / 2;

            int drawX = x + width / 2 - scaledWidth / 2;
            int drawY = y + height / 2 - scaledHeight / 2;

            int tint = advancedcrosshair$tint(config);
            if (tint == NO_TINT) {
                context.drawGuiTexture(pipeline, sprite, drawX, drawY, scaledWidth, scaledHeight);
            } else {
                // The vanilla crosshair pipeline blends by inverting against the
                // backdrop, which would swallow the tint, so draw the same sprite
                // through the plain GUI pipeline instead.
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, drawX, drawY, scaledWidth, scaledHeight, tint);
            }
            return;
        }

        if (path.startsWith(ATTACK_INDICATOR_PREFIX)) {
            context.drawGuiTexture(pipeline, sprite, x, y + advancedcrosshair$indicatorOffset, width, height);
            return;
        }

        context.drawGuiTexture(pipeline, sprite, x, y, width, height);
    }

    /** The attack indicator's progress bar uses the sub-rectangle overload. */
    @Redirect(
        method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V"
        )
    )
    private void advancedcrosshair$drawSpriteRegion(DrawContext context, RenderPipeline pipeline,
                                                    Identifier sprite, int textureWidth, int textureHeight,
                                                    int u, int v, int x, int y, int width, int height) {
        AdvancedCrosshairConfig config = AdvancedCrosshairConfig.get();
        int offset = config.modEnabled && sprite.getPath().startsWith(ATTACK_INDICATOR_PREFIX)
                ? advancedcrosshair$indicatorOffset
                : 0;
        context.drawGuiTexture(pipeline, sprite, textureWidth, textureHeight, u, v, x, y + offset, width, height);
    }

    /**
     * Vanilla skips the flat crosshair entirely while the F3 debug screen draws
     * its three-dimensional one. Reporting that entry as hidden puts the normal
     * crosshair back, which is what the option asks for.
     */
    @Redirect(
        method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/debug/DebugHudProfile;isEntryVisible(Lnet/minecraft/util/Identifier;)Z"
        )
    )
    private boolean advancedcrosshair$debugCrosshairVisible(DebugHudProfile profile, Identifier entry) {
        boolean visible = profile.isEntryVisible(entry);
        if (!visible) {
            return false;
        }
        AdvancedCrosshairConfig config = AdvancedCrosshairConfig.get();
        return !(config.modEnabled && config.showWithDebugHud);
    }

    @Unique
    private int advancedcrosshair$tint(AdvancedCrosshairConfig config) {
        if (client == null || client.player == null || client.world == null) {
            return NO_TINT;
        }
        if (!advancedcrosshair$isLookingAtValidTarget()) {
            return NO_TINT;
        }

        // A crit-capable moment still counts as a normal hit, so when the crit
        // colour is switched off we fall through to the normal one rather than
        // showing nothing.
        if (config.criticalHitEnabled && advancedcrosshair$isReadyForCriticalHit()) {
            return config.criticalHitColor;
        }
        return config.normalHitEnabled ? config.normalHitColor : NO_TINT;
    }

    @Unique
    private boolean advancedcrosshair$isReadyForCriticalHit() {
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

    @Unique
    private boolean advancedcrosshair$isLookingAtValidTarget() {
        if (client.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        } else if (client.targetedEntity instanceof LivingEntity livingTarget) {
            return livingTarget.isAlive();
        }

        return false;
    }
}
