package advancedcrosshair.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
// import net.minecraft.util.hit.HitResult; // Not strictly needed if projectEntities is used primarily
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// import java.util.List; // Not strictly needed if projectEntities is used primarily
// import java.util.function.Predicate; // Not strictly needed

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

            // Check what we're looking at AND if attack is ready
            boolean aimingAtLivingEntityWithFullAttack = isLookingAtLivingEntityWithFullAttack();

            // Set crosshair color based on target and attack readiness
            if (aimingAtLivingEntityWithFullAttack) {
                RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F); // Red
            } else {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // White
            }

            // Enable blending for proper rendering (for both crosshair and attack indicator)
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Draw the crosshair using the current color
            // This respects resource pack changes to the crosshair texture
            context.drawTexture(GUI_ICONS_TEXTURE, centerX - 7, centerY - 7, 0, 0, 15, 15);

            // Render attack indicator if enabled
            // It will now use the same color shader as the crosshair (red or white)
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
            
            // Check targetedEntity before casting and accessing its properties
            // Also ensure client.targetedEntity is a LivingEntity
            if (client.targetedEntity instanceof LivingEntity livingTarget && attackCooldown >= 1.0F) {
                // Check if the player's attack cooldown per tick is significant enough for "readiness"
                // and the target is not in its hurt (invulnerability) phase.
                isReady = client.player.getAttackCooldownProgressPerTick() > 5.0F;
                isReady = isReady & livingTarget.hurtTime <= 0;
            }
            
            // Determine texture Y-coordinate for attack indicator
            // 68 is typically for "ready" or "charged" state, 94 for "charging" or default.
            // If attack is not fully cooled down (attackCooldown < 1.0F), it's definitely not "isReady" by the above logic.
            // So, textureY will be 94 if attackCooldown < 1.0F.
            // If attackCooldown >= 1.0F, then textureY depends on the 'isReady' flag.
            int textureY = isReady ? 68 : 94; 

            if (attackCooldown < 1.0F) {
                // Draw the charging progress bar
                int width = (int)(attackCooldown * 17.0F);
                // The charging bar typically uses texture Y=94.
                context.drawTexture(GUI_ICONS_TEXTURE, centerX - 8, centerY - 7 + 16, 36, 94, width, 4);
            }
            // Note: This method, as in the original, only draws the charging bar.
            // If you want to draw the "fully charged" icon (like the small sword/chevrons),
            // you would add that logic here, and it would also be tinted by the current shader color.
            // For example, if attackCooldown >= 1.0F and isReady:
            // context.drawTexture(GUI_ICONS_TEXTURE, centerX - 8, centerY - 7 + 16, /* u */, textureY, /* width */, /* height */);
        }
    }

    private boolean isLookingAtLivingEntityWithFullAttack() {
        // Ensure client, player, world, and interactionManager are not null
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }

        // First, check if the player's attack is fully charged
        float attackCooldown = client.player.getAttackCooldownProgress(0.0F);
        if (attackCooldown < 1.0F) {
            return false; // Attack not ready, so crosshair shouldn't turn red
        }

        // Get player's reach distance
        double reachDistance = client.interactionManager.getReachDistance();
        
        // Perform a raycast to find the entity the player is looking at
        EntityHitResult entityHitResult = projectEntities(client.player, reachDistance);

        if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
            // Check if the hit entity is alive and not in its hurt (invulnerability) phase
            if (livingEntity.isAlive() && livingEntity.hurtTime <= 0) {
                return true; // Looking at a valid, attackable living entity, and attack is ready
            }
        }
        
        // Fallback: Check Minecraft's own targeted entity, if the raycast didn't yield a suitable target
        // This can sometimes be more reliable or align better with vanilla mechanics.
        if (client.targetedEntity instanceof LivingEntity livingTargeted && 
            livingTargeted.isAlive() && 
            livingTargeted.hurtTime <= 0) {
            // Optionally, verify distance if relying on client.targetedEntity
            // Vec3d eyePos = client.player.getCameraPosVec(1.0F);
            // if (livingTargeted.getPos().squaredDistanceTo(eyePos) <= reachDistance * reachDistance) {
            // return true;
            // }
             return true; // If client.targetedEntity is valid and attack is ready.
        }


        return false; // No suitable living entity found or attack not ready
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
            entity,                     // The entity performing the cast
            eyePosition,                // Start of the ray
            traceEnd,                   // End of the ray
            searchBox,                  // Bounding box to check for entities
            (entityToTest) -> !entityToTest.isSpectator() && entityToTest.isAttackable(), // CORRECTED: Predicate to filter entities
            maxDistance * maxDistance   // Squared maximum distance (ProjectileUtil often uses squared distances)
        );
    }
}