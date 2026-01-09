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
import net.minecraft.world.entity.Entity;
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
                // Smart Selection: Choose the sword most aligned with player's look direction
                // This prevents "sideways" launches by picking the sword arguably "in front" of the camera.
                final net.minecraft.world.phys.Vec3 playerLook = player.getLookAngle();
                final net.minecraft.world.phys.Vec3 playerEye = player.getEyePosition();
                
                FloatingWeaponEntity weaponToLaunch = orbitingWeapons.stream()
                    .max(Comparator.comparingDouble(w -> {
                        net.minecraft.world.phys.Vec3 dirToSword = w.position().subtract(playerEye).normalize();
                        return dirToSword.dot(playerLook);
                    }))
                    .orElse(orbitingWeapons.get(0));
                
                // AIMING LOGIC
                // Perform a RayCast (Clip) to find exactly what the player is looking at
                net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
                net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
                net.minecraft.world.phys.Vec3 endPos = eyePos.add(lookVec.scale(100.0)); // 100 blocks range
                
                // 1. Block Raycast
                net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                    eyePos, endPos, 
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                    net.minecraft.world.level.ClipContext.Fluid.NONE, 
                    player
                );
                
                net.minecraft.world.phys.HitResult blockHit = level.clip(context);
                net.minecraft.world.phys.Vec3 targetPos = blockHit.getLocation(); // Default to block hit
                
                // 2. Entity Raycast (Aim Assist)
                // Check if we are pointing at an entity. If so, prioritize it!
                net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(100.0)).inflate(1.0);
                
                net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player, 
                    eyePos, 
                    endPos, 
                    searchBox, 
                    (e) -> !e.isSpectator() && e.isPickable() && e != player && !(e instanceof FloatingWeaponEntity), 
                    100.0 * 100.0 // Distance squared limit
                );
                
                // If we hit an entity, and it's closer than the block hit (or we missed blocks)
                if (entityHit != null) {
                    double distToBlock = blockHit.getLocation().distanceToSqr(eyePos);
                    double distToEntity = entityHit.getLocation().distanceToSqr(eyePos);
                    
                    if (distToEntity < distToBlock || blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                        // AIM ASSIST: Target the CENTER of the entity
                        // This fixes parallax issues where aiming at the edge of a mob sends the projectile to the wall behind it.
                        targetPos = entityHit.getEntity().getBoundingBox().getCenter();
                    }
                }
                
                // If we hit nothing, use the far end point
                if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS && entityHit == null) {
                    targetPos = endPos;
                }
                
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
            
            // PHASE 1: CHARGING (0-20 ticks)
            if (useTime < 20) {
                 if (useTime % 5 == 0) {
                     // Paladin Charging: Geometric Holy Light
                     serverLevel.sendParticles(ParticleTypes.END_ROD, 
                         player.getX(), player.getY() + 1, player.getZ(), 
                         10, 0.5, 0.5, 0.5, 0.1);
                     // Holy Hum
                     level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                         SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
                 }
                 return;
            }

            // PHASE 2: RITUAL SUMMONING (Every 12 ticks after 20) -> SLOWER (0.6s)
            if ((useTime - 20) % 12 == 0) {
                List<FloatingWeaponEntity> existingWeapons = getAllWeapons(serverLevel, player);
                
                // Ensure existing are recalling
                for (FloatingWeaponEntity w : existingWeapons) {
                    if (!w.isReturning()) w.setReturning(true);
                }
                
                int currentCount = existingWeapons.size();
                
                if (currentCount < MAX_WEAPONS) {
                    // Spawn ONE sword
                    FloatingWeaponEntity weapon = new FloatingWeaponEntity(MagicEntities.FLOATING_WEAPON, serverLevel);
                    weapon.setPos(player.getX(), player.getY() + 1.5, player.getZ());
                    weapon.setOwner(player);
                    
                    // Assign Smart Slot (No Shifting) - BEFORE spawning
                    assignFreeSlot(existingWeapons, weapon);
                    
                    serverLevel.addFreshEntity(weapon);
                    existingWeapons.add(weapon);
                    
                    // FX: Forge Sound (Heavy Anvil)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.0f);
                    
                    // FX: Forging Sparks
                    serverLevel.sendParticles(ParticleTypes.SCRAPE, 
                        player.getX(), player.getY() + 1.5, player.getZ(), 
                        20, 0.5, 0.5, 0.5, 0.1);
                } 
                else {
                    // Ritual Complete (Full Set)
                    // Paladin Finale: Electric Trident Thunder
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
                    
                    // Holy Flash
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, 
                        player.getX(), player.getY() + 1.5, player.getZ(), 
                        1, 0.0, 0.0, 0.0, 0.0);
                        
                    player.stopUsingItem();
                    player.getCooldowns().addCooldown(stack, 60);
                }
            }
        }
    }

    private void assignFreeSlot(List<FloatingWeaponEntity> existingWeapons, FloatingWeaponEntity newWeapon) {
        boolean[] used = new boolean[MAX_WEAPONS];
        for (FloatingWeaponEntity w : existingWeapons) {
            int idx = w.getOrbitIndex();
            if (idx >= 0 && idx < MAX_WEAPONS) {
                used[idx] = true;
            }
        }
        
        for (int i = 0; i < MAX_WEAPONS; i++) {
            if (!used[i]) {
                newWeapon.setOrbitIndex(i);
                return;
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

    private List<FloatingWeaponEntity> getAllWeapons(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(FloatingWeaponEntity.class, 
            player.getBoundingBox().inflate(256.0),
            weapon -> {
                try {
                    return weapon.getOwner() != null 
                        && weapon.getOwner().equals(player);
                } catch (Exception e) {
                    return false;
                }
            });
    }
}
