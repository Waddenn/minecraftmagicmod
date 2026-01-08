package fr.tom.magicmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;

import fr.tom.magicmod.entity.FloatingWeaponEntity;
import fr.tom.magicmod.MagicEntities;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SpectralGrimoireItem extends Item {
    private static final int MAX_WEAPONS = 5;
    
    public SpectralGrimoireItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            List<FloatingWeaponEntity> orbitingWeapons = getOrbitingWeapons(serverLevel, player);
            
            if (orbitingWeapons.isEmpty()) {
                // SUMMON MODE: START CHARGING
                player.startUsingItem(hand);
                return InteractionResult.CONSUME;
            } else {
                // LAUNCH MODE: INSTANT FIRE
                FloatingWeaponEntity weaponToLaunch = orbitingWeapons.stream()
                    .min(Comparator.comparingDouble(w -> w.distanceTo(player)))
                    .orElse(orbitingWeapons.get(0));
                
                // AIMING LOGIC
                net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
                net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
                net.minecraft.world.phys.Vec3 targetPos = eyePos.add(lookVec.scale(50.0));
                
                net.minecraft.world.phys.Vec3 swordPos = weaponToLaunch.position();
                net.minecraft.world.phys.Vec3 launchDir = targetPos.subtract(swordPos).normalize();
                
                weaponToLaunch.launch(launchDir);
                
                level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.2f);
                
                player.getCooldowns().addCooldown(stack, 5); 
                return InteractionResult.SUCCESS;
            }
        }
        
        // Client side: allow using if no weapons (to show charge anim)
        // We can't check server entities easily here, but usually returning CONSUME is enough
        return InteractionResult.CONSUME;
    }
    
    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (livingEntity instanceof Player player && !level.isClientSide() && level instanceof ServerLevel serverLevel) {
             int useTime = getUseDuration(stack, livingEntity) - remainingUseDuration;
             
             // Trigger summon at exactly 1 second (20 ticks)
             if (useTime == 20) {
                 // SUMMON
                for (int i = 0; i < MAX_WEAPONS; i++) {
                    FloatingWeaponEntity weapon = new FloatingWeaponEntity(MagicEntities.FLOATING_WEAPON, serverLevel);
                    weapon.setPos(player.getX(), player.getY() + 1.5, player.getZ());
                    weapon.setOwner(player);
                    weapon.setOrbitIndex(i);
                    serverLevel.addFreshEntity(weapon);
                }
                
                // Feedback
                serverLevel.sendParticles(ParticleTypes.ENCHANT, 
                    player.getX(), player.getY() + 1, player.getZ(), 
                    50, 1.0, 1.0, 1.0, 0.5);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
                
                player.getCooldowns().addCooldown(stack, 40);
                player.stopUsingItem(); // Finish using
             }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }
    
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW; // Animation style (holding up)
    }

    private List<FloatingWeaponEntity> getOrbitingWeapons(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(FloatingWeaponEntity.class, 
            player.getBoundingBox().inflate(32.0),
            weapon -> {
                try {
                    return weapon.getOwner() != null 
                        && weapon.getOwner().equals(player)
                        && !weapon.isLaunched(); // Only count orbiting weapons
                } catch (Exception e) {
                    return false;
                }
            });
    }
}
