package advancedcrosshair.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects; // Import for StatusEffects
import net.minecraft.util.Identifier;
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

    @Shadow
    private MinecraftClient client;

    private static final Identifier GUI_ICONS_TEXTURE = new Identifier("textures/gui/icons.png");

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void changeCrosshairColor(DrawContext context, CallbackInfo ci) {
        // Ensure client, player, and world are initialized
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;
        // Ensure options is not null
        if (options == null) {
            return;
        }

        if (options.getPerspective().isFirstPerson()) {
            // Cancel the original crosshair rendering
            ci.cancel();

            // Check if crosshairs are enabled
            if (options.hudHidden) {
                return;
            }

            // Get screen center
            int centerX = context.getScaledWindowWidth() / 2;
            int centerY = context.getScaledWindowHeight() / 2;

            // Determine crosshair color based on conditions
            boolean isCriticalHitReady = isReadyForCriticalHit();
            boolean isAttackReady = !isCriticalHitReady && isLookingAtLivingEntityWithReadyAttack(); // Only check for red if not already blue

            if (isCriticalHitReady) {
                RenderSystem.setShaderColor(0.0F, 0.0F, 1.0F, 1.0F); // Blue for critical hit ready
            } else if (isAttackReady) {
                RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F); // Red for attack ready
            } else {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // White by default
            }

            // Enable blending for proper rendering (for both crosshair and attack indicator)
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Draw the crosshair using the current color
            // This respects resource pack changes to the crosshair texture
            context.drawTexture(GUI_ICONS_TEXTURE, centerX - 7, centerY - 7, 0, 0, 15, 15);

            // Render attack indicator if enabled
            // It will now use the same color shader as the crosshair (blue, red, or white)
            renderAttackIndicator(context, centerX, centerY);

            // Reset shader color and disable blend AFTER both crosshair and attack indicator are drawn
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // Reset to white
            RenderSystem.disableBlend();
        }
    }

    private void renderAttackIndicator(DrawContext context, int centerX, int centerY) {
        // Ensure client, player, and options are not null
        if (client == null || client.player == null || client.options == null) {
            return;
        }
        GameOptions options = client.options;

        if (options.getAttackIndicator().getValue() == net.minecraft.client.option.AttackIndicator.CROSSHAIR) {
            float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
            boolean isReady = false;
            
            // The 'isReady' for the indicator itself should still be based on 100% cooldown for vanilla behavior,
            // or you could adjust this threshold as well if desired for the indicator's readiness state.
            if (client.targetedEntity instanceof LivingEntity livingTarget && attackCooldown >= 1.0F) {
                // Check if the player's attack cooldown per tick is significant enough for "readiness"
                // and the target is not in its hurt (invulnerability) phase.
                isReady = client.player.getAttackCooldownProgressPerTick() > 5.0F;
                isReady = isReady & livingTarget.hurtTime <= 0;
            }
            
            // Determine texture Y-coordinate for attack indicator
            // 68 is typically for "ready" or "charged" state, 94 for "charging" or default.
            int textureY = isReady ? 68 : 94; 

            if (attackCooldown < 1.0F) {
                // Draw the charging progress bar
                int width = (int)(attackCooldown * 17.0F);
                // The charging bar typically uses texture Y=94.
                context.drawTexture(GUI_ICONS_TEXTURE, centerX - 8, centerY - 7 + 16, 36, 94, width, 4);
            }
            // If you want to draw the "fully charged" icon (like the small sword/chevrons)
            // when attackCooldown >= 1.0F and isReady:
            // context.drawTexture(GUI_ICONS_TEXTURE, centerX - 8, centerY - 7 + 16, 36, textureY, 9, 4); // Example values, adjust U, width, height
        }
    }

    // New method to check for critical hit conditions
    private boolean isReadyForCriticalHit() {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);

        // 1. Attack must be fully charged for a guaranteed strong hit with crit
        if (attackCooldown < 1.0F) {
            return false;
        }

        // 2. Player must be falling
        // Check for negative Y-velocity to ensure active descent
        if (client.player.getVelocity().y >= 0.0D || client.player.isOnGround() || client.player.isClimbing() || client.player.isSwimming()) {
            return false;
        }

        // 3. Player must not be on a ladder/vine (covered by isClimbing())
        // 4. Player must not be in water (covered by isSwimming() and getVelocity().y >= 0.0D)
        // 5. Player must not be affected by Blindness
        if (client.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }

        // 6. Player must not be riding an entity
        if (client.player.hasVehicle()) {
            return false;
        }

        // 7. Player must not be sprinting (Java Edition specific crit condition)
        if (client.player.isSprinting()) {
            return false;
        }

        // 8. Check if looking at a valid LivingEntity
        return isLookingAtValidTarget(1.0F); // Use 1.0F cooldown as it's for critical hit
    }

    // Helper method to check if looking at a living entity with a given cooldown threshold
    private boolean isLookingAtValidTarget(float minAttackCooldown) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown < minAttackCooldown) {
            return false;
        }

        // Force 3 block reach limit regardless of gamemode (except creative/spectator)
        double reachDistance = 3.0D;
        
        // Get player's eye position and look direction
        Vec3d eyePosition = client.player.getCameraPosVec(1.0f);
        Vec3d lookVector = client.player.getRotationVec(1.0f);
        Vec3d traceEnd = eyePosition.add(lookVector.multiply(reachDistance));
        
        EntityHitResult entityHitResult = projectEntities(client.player, reachDistance);

        if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
            if (livingEntity.isAlive() && livingEntity.hurtTime <= 0) {
                // Double-check: calculate actual distance to the hit point
                double actualDistance = eyePosition.distanceTo(entityHitResult.getPos());
                if (actualDistance <= 3.0D) {
                    return true;
                }
            }
        }
        
        // Fallback: check client.targetedEntity but verify it's within 3 blocks
        if (client.targetedEntity instanceof LivingEntity livingTargeted && 
            livingTargeted.isAlive() && 
            livingTargeted.hurtTime <= 0) {
            
            // Calculate distance from player's eye to the entity's center
            Vec3d entityCenter = livingTargeted.getPos().add(0, livingTargeted.getHeight() / 2, 0);
            double distanceToEntity = eyePosition.distanceTo(entityCenter);
            
            // Also check distance to the entity's bounding box edges
            Box entityBox = livingTargeted.getBoundingBox();
            double distanceToBox = entityBox.getCenter().distanceTo(eyePosition);
            
            // Use the smaller distance (more accurate for hit detection)
            double finalDistance = Math.min(distanceToEntity, distanceToBox);
            
            if (finalDistance <= 3.0D) {
                return true;
            }
        }

        return false;
    }

    // Renamed and refactored the original method to use the new helper
    private boolean isLookingAtLivingEntityWithReadyAttack() {
        // Red crosshair condition: attack at least 91% charged and looking at an attackable entity
        return isLookingAtValidTarget(1.0F);
    }
    
    /**
     * Helper method to perform a raycast and find the first entity intersected.
     * This is similar to how vanilla Minecraft determines targeted entities.
     * @param entity The entity performing the raycast (usually the player).
     * @param maxDistance The maximum distance for the raycast.
     * @return EntityHitResult if an entity is hit, otherwise null.
     */
    private EntityHitResult projectEntities(Entity entity, double maxDistance) {
        // Ensure the entity performing the raycast is not null
        if (entity == null) {
            return null;
        }
        
        Vec3d eyePosition = entity.getCameraPosVec(1.0f); // Eye position of the entity
        Vec3d lookVector = entity.getRotationVec(1.0f);   // Direction the entity is looking
        Vec3d traceEnd = eyePosition.add(lookVector.multiply(maxDistance)); // End point of the raycast
        
        // Define the bounding box for the raycast search area
        Box searchBox = entity.getBoundingBox().stretch(lookVector.multiply(maxDistance)).expand(1.0D, 1.0D, 1.0D);
        
        // Perform the raycast using ProjectileUtil
        // The predicate checks if the entity is not a spectator and is attackable
        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
            entity,                 // The entity performing the cast
            eyePosition,            // Start of the ray
            traceEnd,               // End of the ray
            searchBox,              // Bounding box to check for entities
            (entityToTest) -> !entityToTest.isSpectator() && entityToTest.isAttackable(), // Predicate to filter entities
            maxDistance * maxDistance   // Squared maximum distance (ProjectileUtil often uses squared distances)
        );
    }
}