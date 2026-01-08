package fr.tom.magicmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import fr.tom.magicmod.entity.FloatingWeaponEntity;

public class FloatingWeaponRenderer extends EntityRenderer<FloatingWeaponEntity, FloatingWeaponRenderer.FloatingWeaponRenderState> {
    
    private final ItemModelResolver itemModelResolver;
    private final ItemStack swordStack = new ItemStack(Items.DIAMOND_SWORD);

    public FloatingWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public FloatingWeaponRenderState createRenderState() {
        return new FloatingWeaponRenderState();
    }

    @Override
    public boolean shouldRender(FloatingWeaponEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        // Always render to prevent culling issues caused by visual offset (Orbit smoothing)
        // intersecting with narrow First-Person frustum while hitbox is lagging.
        return true;
    }


    @Override
    public void extractRenderState(FloatingWeaponEntity entity, FloatingWeaponRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        
        // Calculate spin
        float time = (entity.tickCount + partialTick) * 10.0f;
        state.spinRotation = time;
        
        // Extract orientation state
        state.isLaunched = entity.isLaunched();
        state.yRot = entity.getViewYRot(partialTick);
        state.xRot = entity.getViewXRot(partialTick);
        
        // VISUAL INTERPOLATION LOGIC
        // 1. Calculate where the sword "should" be if it were still orbiting (Target Orbit Position)
        // We need this to maintain continuity when it switches to "Launched"
        state.orbitOffset = Vec3.ZERO;
        
        // Helper vars for orbit calculation
        float orbitSpeed = 0.05f; 
        float angle = ((entity.tickCount + partialTick) * orbitSpeed) + (float) (entity.getOrbitIndex() * (Math.PI * 2 / 5.0));
        
        if (!state.isLaunched) {
            Entity owner = entity.getOwner();
            if (owner instanceof Player) {
                double ownerX = Mth.lerp(partialTick, owner.xo, owner.getX());
                double ownerY = Mth.lerp(partialTick, owner.yo, owner.getY());
                double ownerZ = Mth.lerp(partialTick, owner.zo, owner.getZ());
                
                double targetX = ownerX + Math.cos(angle) * 2.0f; 
                double targetY = ownerY + 1.5 + Math.sin(angle * 3) * 0.3;
                double targetZ = ownerZ + Math.sin(angle) * 2.0f; 
                
                double entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
                double entityY = Mth.lerp(partialTick, entity.yo, entity.getY());
                double entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
                
                // Calculate and STORE the visual offset
                // This difference effectively "snaps" the entity visual to the orbit visual
                Vec3 currentOffset = new Vec3(targetX - entityX, targetY - entityY, targetZ - entityZ);
                state.orbitOffset = currentOffset;
                
                // Keep the entity's cache updated so we have the last known good offset when launch happens
                entity.visualLaunchOffset = currentOffset;
                entity.clientLaunchTime = 0; // Reset
            }
        } else {
             // LAUNCHED MODE - DECAY
             if (entity.clientLaunchTime == 0) {
                 entity.clientLaunchTime = entity.level().getGameTime();
             }
             
             long timeSinceLaunch = entity.level().getGameTime() - entity.clientLaunchTime;
             float decayDuration = 5.0f; // Ticks to decay (0.25s)
             
             // Calculate decay factor (1.0 to 0.0)
             // We add partialTick for smooth decay frame-by-frame
             float progress = (timeSinceLaunch + partialTick) / decayDuration;
             float decay = 1.0f - Mth.clamp(progress, 0.0f, 1.0f);
             
             // Apply decayed offset
             if (decay > 0.0f) {
                 state.orbitOffset = entity.visualLaunchOffset.scale(decay);
             } else {
                 state.orbitOffset = Vec3.ZERO;
             }
        }
        
        // Populate item state
        this.itemModelResolver.updateForNonLiving(state.item, this.swordStack, ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(FloatingWeaponRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        
        // Apply Visual Offset (Works for both Orbiting and Decaying Launch)
        if (!state.orbitOffset.equals(Vec3.ZERO)) {
             poseStack.translate(state.orbitOffset.x, state.orbitOffset.y, state.orbitOffset.z);
        }
        
        if (state.isLaunched) {
            // Projectile Orientation
            poseStack.translate(0.0f, 0.15f, 0.0f);
            
            // Align with velocity vector 
            // We use standard Arrow/Trident alignment where local X is Forward.
            // 1. Rotate Y: (yRot - 90) puts +X pointing in the direction of flight (for standard Yaw=0 -> +Z system)
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
            
            // 2. Rotate Z: Apply pitch. 
            // If xRot is + for Up (Entity logic), rotating +Z moves +X (Forward) towards +Y (Up).
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
            
            // 3. Item Model Alignment (Diamond Sword)
            // Context.NONE gives raw model (Diagonal: Bottom-Left to Top-Right).
            // Rotate -45 degrees on Z to align Tip (+1, +1) to Forward (+X).
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f)); 
            
            poseStack.scale(1.0f, 1.0f, 1.0f);
            
        } else {
            // Orbiting Animation
            
            // Offset already applied globally
            
            poseStack.translate(0.0f, 0.5f, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spinRotation));
            // Context.NONE raw model is diagonal.
            // -45 -> Horizontal (+X). -135 -> Vertical Down (-Y).
            poseStack.mulPose(Axis.ZP.rotationDegrees(-135.0f));
            poseStack.scale(0.8f, 0.8f, 0.8f); // Reduced from 1.2f
        }
        
        // Render item with full brightness
        state.item.submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0); 
        
        poseStack.popPose();
    }

    public static class FloatingWeaponRenderState extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float spinRotation;
        public boolean isLaunched;
        public float yRot;
        public float xRot;
        public Vec3 orbitOffset = Vec3.ZERO;
    }
}
