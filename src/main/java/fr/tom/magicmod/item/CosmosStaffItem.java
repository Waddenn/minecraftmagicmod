package fr.tom.magicmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import java.util.List;

public class CosmosStaffItem extends Item {
    public CosmosStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW; 
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!level.isClientSide() && user instanceof Player player) {
            Vec3 look = player.getLookAngle();
            Vec3 targetPos = player.getEyePosition().add(look.scale(4.0));
            double radius = 5.0;
            AABB area = player.getBoundingBox().inflate(radius).move(look.scale(2.0));
            
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area);
            
            for (LivingEntity entity : entities) {
                if (entity != player) {
                    Vec3 entityPos = entity.position().add(0, entity.getBbHeight() / 2, 0);
                    Vec3 toTarget = targetPos.subtract(entityPos);
                    
                    double strength = 0.2;
                    double damping = 0.8;
                    
                    Vec3 currentVel = entity.getDeltaMovement();
                    Vec3 newVel = currentVel.scale(damping).add(toTarget.scale(strength));
                    
                    entity.setDeltaMovement(newVel);
                    entity.setNoGravity(true);
                    entity.fallDistance = 0;
                }
            }
        } 
        
        if (level.isClientSide()) {
             Vec3 look = user.getLookAngle();
             Vec3 particlePos = user.getEyePosition().add(look.scale(1.0 + Math.random()));
             level.addParticle(ParticleTypes.PORTAL, particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!level.isClientSide() && user instanceof Player player) {
             Vec3 look = player.getLookAngle();
             AABB area = player.getBoundingBox().inflate(6.0).move(look.scale(2.0));
             List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area);
             
             for (LivingEntity entity : entities) {
                 if (entity != player) {
                     entity.setNoGravity(false);
                     entity.setDeltaMovement(look.scale(3.0));
                 }
             }
             return true;
        }
        return false;
    }
}
