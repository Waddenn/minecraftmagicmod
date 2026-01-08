package fr.tom.magicmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;

public class NecromancerStaffItem extends Item {
    public NecromancerStaffItem(Properties properties) {
        super(properties);
    }

    // Unique ID for the Necromancer's Pact health modifier
    private static final net.minecraft.resources.Identifier HEALTH_MODIFIER_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("magicmod", "necromancer_pact");

    private void modifyMaxHealth(Player player, double amount) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance healthAttribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            net.minecraft.world.entity.ai.attributes.AttributeModifier existingModifier = healthAttribute.getModifier(HEALTH_MODIFIER_ID);
            double currentDebt = 0.0;
            
            if (existingModifier != null) {
                currentDebt = existingModifier.amount();
                healthAttribute.removeModifier(HEALTH_MODIFIER_ID);
            }
            
            // Calculate new debt
            double newDebt = currentDebt + amount;
            
            // SAFEGUARD: The modifier must NEVER be positive (bonus health). 
            // It represents a PENALTY (debt). Can be 0 or negative.
            if (newDebt > 0) {
                newDebt = 0;
            }
            
            // Only apply if there is a debt
            if (newDebt < 0) {
                net.minecraft.world.entity.ai.attributes.AttributeModifier newModifier = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    HEALTH_MODIFIER_ID, 
                    newDebt, 
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                );
                healthAttribute.addTransientModifier(newModifier);
            }
            
            // Handle Visual Health Updates
            if (amount > 0) {
                 // Restoring health: Heal the void
                 player.heal((float)amount);
            } else {
                 // Paying health: Clamp if over max
                 if (player.getHealth() > player.getMaxHealth()) {
                     player.setHealth(player.getMaxHealth());
                 }
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                // The Devourer (Finisher): Sacrifice minions to summoning Evoker Fangs
                double range = 50.0;
                AABB area = player.getBoundingBox().inflate(range);
                java.util.List<WitherSkeleton> minions = level.getEntitiesOfClass(WitherSkeleton.class, area);
                
                int sacrificedCount = 0;
                for (WitherSkeleton minion : minions) {
                    if (minion.getTeam() != null && minion.getTeam().getName().equals("NecroArmy")) {
                        // SINGLE JAW: User requested only one jaw instead of a cluster
                        level.addFreshEntity(new net.minecraft.world.entity.projectile.EvokerFangs(level, minion.getX(), minion.getY(), minion.getZ(), player.getYRot(), 10, null));
                        
                        // Prepare minion for execution
                        minion.removeAllEffects();
                        minion.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255));
                        minion.setHealth(0.5f);
                        
                        // RESTORE PACT: Handled by Soul Link event (MagicMod.java) upon death
                        // We do not refund here anymore to avoid double-counting and premature healing
                        
                        // Visuals
                        level.addParticle(ParticleTypes.SCULK_SOUL, minion.getX(), minion.getY() + 1, minion.getZ(), 0, 0.1, 0);
                        sacrificedCount++;
                    }
                }
                
                if (sacrificedCount > 0) {
                     player.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.0f);
                     // Cooldown: 3 seconds (60 ticks) for the Finisher
                     player.getCooldowns().addCooldown(stack, 60);
                     return InteractionResult.SUCCESS;
                }
            }
        }
        
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // "SPEAR" (Trident) animation looks more like pointing a staff than "BOW"
        // It also avoids the FOV zoom/slowdown associated with bows
        return ItemUseAnimation.SPEAR; 
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int ticksUsed = 72000 - remainingUseTicks;

        if (!level.isClientSide() && user instanceof Player player) {
            ServerLevel serverLevel = (ServerLevel) level;
            
            // Charging Sound (delayed to avoid playing on quick clicks for Orders)
            // Orders are < 20 ticks. So at 20 ticks, we are officially "Charging".
            if (ticksUsed == 20) {
                 player.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0f, 0.8f);
            }

            // Spawn every 2 seconds (40 ticks)
            if (ticksUsed % 40 == 0 && ticksUsed > 0) {
                // Check Limit (Blood Pact)
                double range = 50.0;
                AABB area = player.getBoundingBox().inflate(range);
                java.util.List<WitherSkeleton> existingMinions = level.getEntitiesOfClass(WitherSkeleton.class, area);
                long allyCount = existingMinions.stream()
                    .filter(m -> m.getTeam() != null && m.getTeam().getName().equals("NecroArmy"))
                    .count();
                    
                if (allyCount >= 5) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 0.5f); // "Click" fail sound
                    return;
                }

                // Summon Wither Skeleton
                WitherSkeleton minion = EntityType.WITHER_SKELETON.create(serverLevel, EntitySpawnReason.EVENT);
                if (minion != null) {
                    // Position: 5 blocks in front
                    double x = player.getX() + player.getLookAngle().x * 5.0;
                    double z = player.getZ() + player.getLookAngle().z * 5.0;
                    
                    // Find Ground Y (Snap to terrain)
                    net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, player.getY(), z);
                    
                    // 1. If target is inside solid block, scan UP (escape wall/hill)
                    int safety = 0;
                    while (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && safety++ < 10) {
                        pos.move(0, 1, 0);
                    }
                    
                    // 2. If target is in air, scan DOWN (find floor)
                    safety = 0;
                    while (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty() && safety++ < 10) {
                         pos.move(0, -1, 0);
                    }
                    
                    double finalY = pos.getY();
                    minion.setPos(x, finalY - 2.0, z); // Spawn underground for rising animation
                    
                    minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    minion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                    
                    minion.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 0)); 
                    minion.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
                    minion.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                    
                    // PREVENT DESPAWN: Essential for "Soul Link" refund to work correctly
                    minion.setPersistenceRequired(); 
                    
                    Scoreboard scoreboard = level.getScoreboard();
                    PlayerTeam team = scoreboard.getPlayerTeam("NecroArmy");
                    if (team == null) {
                        team = scoreboard.addPlayerTeam("NecroArmy");
                        team.setAllowFriendlyFire(false);
                        team.setSeeFriendlyInvisibles(true);
                        team.setColor(net.minecraft.ChatFormatting.DARK_GREEN);
                    }
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                    scoreboard.addPlayerToTeam(minion.getStringUUID(), team); 
                    
                    // TAGGING: Mark this minion as belonging to the player for "Soul Link" refund on death
                    // We use Vanilla Tags because getPersistentData() is not available in these mappings
                    minion.addTag("NecromancerOwner:" + player.getStringUUID());
                    
                    serverLevel.addFreshEntity(minion);
                    
                    // Visuals: SOUL ERUPTION
                    // Visuals: SOUL ERUPTION
                    serverLevel.sendParticles(ParticleTypes.POOF, x, finalY, z, 25, 0.6, 0.1, 0.6, 0.05);
                    serverLevel.sendParticles(ParticleTypes.SMOKE, x, finalY, z, 15, 0.3, 0.1, 0.3, 0.05);
                    serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, finalY + 0.2, z, 15, 0.4, 0.5, 0.4, 0.1);
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, finalY, z, 8, 0.4, 0.0, 0.4, 0.05);
                    serverLevel.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, finalY, z, 10, 0.5, 0.1, 0.5, 0.1); 
                    
                    level.playSound(null, x, finalY, z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.NEUTRAL, 1.0f, 0.5f);
                    level.playSound(null, x, finalY, z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.NEUTRAL, 0.5f, 0.5f);

                    // COST: Max Health Reduction (Blood Pact)
                    // Reduces Max Health by 2 Hearts (4.0). Can only be regained by sacrificing the minion.
                    modifyMaxHealth(player, -4.0);
                }
            }
        }
        
        // Client-side visual effects
        // Client-side visual effects
        if (level.isClientSide()) {
             // Calculate charge percentage
             boolean isCharged = ticksUsed >= 20;

             if (isCharged) {
                 // HIGH INTENSITY: Spiraling Soul Fire (Ground Only)
                 // Create a flat vortex effect at the player's feet
                 double radius = 1.5;
                 double speed = 0.2; // Fast rotation
                 
                 for (int i = 0; i < 3; i++) { // Dense particles
                     double angle = (level.getGameTime() * speed + i * (Math.PI * 2 / 3)) % (Math.PI * 2);
                     double px = user.getX() + Math.cos(angle) * radius;
                     double pz = user.getZ() + Math.sin(angle) * radius;
                     
                     // GROUND LEVEL ONLY: Just above feet
                     double py = user.getY() + 0.1; 
                     
                     // No vertical speed (or very slight drift)
                     level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0, 0.01, 0);
                     
                     // Occasional "Pop"
                     if (user.getRandom().nextFloat() < 0.1) {
                         level.addParticle(ParticleTypes.SCULK_CHARGE_POP, px, py + 0.2, pz, 0, 0.05, 0);
                     }
                 }
             } else {
                 // LOW INTENSITY: Gathering Energy
                 // Particles move TOWARDS the staff/player
                 if (user.getRandom().nextFloat() < 0.4) { // Moderate density
                     double radius = 2.5;
                     double theta = user.getRandom().nextFloat() * Math.PI * 2;
                     double px = user.getX() + Math.cos(theta) * radius;
                     double py = user.getY() + user.getRandom().nextFloat() * 2.0;
                     double pz = user.getZ() + Math.sin(theta) * radius;
                     
                     // Velocity towards player
                     double vx = (user.getX() - px) * 0.05;
                     double vy = 0.05; // Slight rise
                     double vz = (user.getZ() - pz) * 0.05;
                     
                     level.addParticle(ParticleTypes.SCULK_SOUL, px, py, pz, vx, vy, vz);
                 }
             }
        }
    }

}
