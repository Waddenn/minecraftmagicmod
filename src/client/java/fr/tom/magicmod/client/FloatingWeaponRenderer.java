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
    public void extractRenderState(FloatingWeaponEntity entity, FloatingWeaponRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        
        // Calculate spin
        float time = (entity.tickCount + partialTick) * 10.0f;
        state.spinRotation = time;
        
        // Extract orientation state
        state.isLaunched = entity.isLaunched();
        state.yRot = entity.getViewYRot(partialTick);
        state.xRot = entity.getViewXRot(partialTick);
        
        // Populate item state
        this.itemModelResolver.updateForNonLiving(state.item, this.swordStack, ItemDisplayContext.FIXED, entity);
    }

    @Override
    public void submit(FloatingWeaponRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        
        if (state.isLaunched) {
            // Projectile Orientation
            poseStack.translate(0.0f, 0.5f, 0.0f);
            
            // Align with velocity vector 
            // Standard entity rotation: 180 - yRot aligns -Z with forward
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
            
            // Item Model Alignment (Diamond Sword)
            // We need tip (+Y) to point FORWARD (-Z relative to entity)
            // 1. Rotate around X axis by -90 deg: Moves +Y (Tip) to -Z (Forward)
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            
            // 2. Rotate around Z axis by -45 deg: Handles the sword texture being diagonal in item model
            // This makes the blade straight instead of diagonal
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f)); 
            
            poseStack.scale(1.0f, 1.0f, 1.0f);
            
        } else {
            // Orbiting Animation
            poseStack.translate(0.0f, 0.5f, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spinRotation));
            poseStack.mulPose(Axis.ZP.rotationDegrees(135.0f));
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
    }
}
