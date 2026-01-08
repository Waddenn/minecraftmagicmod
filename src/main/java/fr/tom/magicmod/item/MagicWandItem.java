package fr.tom.magicmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

public class MagicWandItem extends Item {
    public MagicWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) level;
            
            // 1. Raycast to find target block (Range 50)
            BlockHitResult hitResult = (BlockHitResult) player.pick(50.0D, 0.0F, false);
            
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                // 2. Spawn Lightning Bolt
                LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.EVENT);
                if (lightning != null) {
                    Vec3 vec = hitResult.getLocation();
                    lightning.setPos(vec.x, vec.y, vec.z);
                    serverLevel.addFreshEntity(lightning);
                }

                // 3. Levitation Effect (Area 4x4x4 around impact)
                AABB area = new AABB(hitResult.getBlockPos()).inflate(4);
                for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (entity != player) { 
                        entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1)); 
                    }
                }
            }
        } else {
            // Client-side effects
            level.playSound(player, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            
             for (int i = 0; i < 5; i++) {
                double d = (double) i / 5.0D;
                double x = player.getX() + player.getLookAngle().x * d * 2.0;
                double y = player.getEyeY() + player.getLookAngle().y * d * 2.0;
                double z = player.getZ() + player.getLookAngle().z * d * 2.0;
                level.addParticle(ParticleTypes.GLOW, x, y, z, 0.0D, 0.0D, 0.0D);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
