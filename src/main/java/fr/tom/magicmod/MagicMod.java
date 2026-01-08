package fr.tom.magicmod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MagicMod implements ModInitializer {
	public static final String MOD_ID = "magicmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Magic Mod!");
		MagicItems.registerModItems();
		MagicEntities.registerEntities();

        // Soul Link Logic: Refund health when minion dies
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton && !entity.level().isClientSide()) {
                // Find tag starting with "NecromancerOwner:"
                java.util.Set<String> tags = entity.getTags();
                String ownerTagPrefix = "NecromancerOwner:";
                String ownerUUIDString = null;
                
                for (String tag : tags) {
                    if (tag.startsWith(ownerTagPrefix)) {
                        ownerUUIDString = tag.substring(ownerTagPrefix.length());
                        break;
                    }
                }
                
                if (ownerUUIDString != null) {
                    try {
                        java.util.UUID ownerUUID = java.util.UUID.fromString(ownerUUIDString);
                        // Use Server Player List to find player even if in another dimension or far away
                        net.minecraft.server.MinecraftServer server = ((net.minecraft.server.level.ServerLevel)entity.level()).getServer();
                        net.minecraft.world.entity.player.Player owner = server.getPlayerList().getPlayer(ownerUUID);
                        
                        System.out.println("DEBUG: Minion died. Owner UUID: " + ownerUUID + ". Player Found: " + (owner != null));
                        
                        if (owner != null) {
                             // Refund the specific amount (2.0 = 1 heart)
                             net.minecraft.resources.Identifier HEALTH_MODIFIER_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("magicmod", "necromancer_pact");
                             net.minecraft.world.entity.ai.attributes.AttributeInstance healthAttribute = owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                             
                             if (healthAttribute != null) {
                                net.minecraft.world.entity.ai.attributes.AttributeModifier existingModifier = healthAttribute.getModifier(HEALTH_MODIFIER_ID);
                                double currentDebt = 0.0;
                                if (existingModifier != null) {
                                    currentDebt = existingModifier.amount();
                                    healthAttribute.removeModifier(HEALTH_MODIFIER_ID);
                                }
                                
                                // Reimburse 4.0 health (2 Hearts) to match the cost
                                double newDebt = currentDebt + 4.0;
                                if (newDebt > 0) newDebt = 0; // Cap at 0
                                
                                if (newDebt < 0) {
                                    healthAttribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                        HEALTH_MODIFIER_ID, newDebt, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                                    ));
                                }
                                
                                // Notify player
                                owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§aA minion has fallen. Your soul fragment returns."), true);
                                owner.heal(2.0f); // Small comfort heal
                             }
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID format in tag, ignore
                    }
                }
            }
        });
        
        // Minion AI Tick: Manage Follow and Teleport logic
        // We use END_WORLD_TICK to iterate over entities and update their behavior continuously
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world -> {
            // Processing every tick might be heavy, maybe skip some ticks? 
            // For now, simple is better.
            
            for (net.minecraft.server.level.ServerPlayer player : world.players()) {
                // Optimize: Only search if player has minions? 
                // Hard to know without iterating entities.
                // Better loop: Iterate entities and find owner.
            }
            
            // Getting all entities can be expensive using getEntities().
            // But we need to find OUR minions.
            // Let's iterate over ALL loaded entities in the server world.
            // Note: This iterates distinct entities.
            
            for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
                if (entity instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton minion) {
                    // Check if it's a Necromancer Minion
                    java.util.Set<String> tags = minion.getTags();
                    String ownerUUIDString = null;
                     for (String tag : tags) {
                        if (tag.startsWith("NecromancerOwner:")) {
                            ownerUUIDString = tag.substring("NecromancerOwner:".length());
                            break;
                        }
                    }
                    
                    if (ownerUUIDString != null) {
                         try {
                            java.util.UUID ownerUUID = java.util.UUID.fromString(ownerUUIDString);
                            net.minecraft.world.entity.player.Player owner = world.getPlayerByUUID(ownerUUID);
                            
                            if (owner != null) {
                                // UNIFIED AI: Fight or Follow
                                // No modes. Just instinct.
                                
                                // 0. REALITY CHECK: Should I stop fighting?
                                if (minion.getTarget() != null) {
                                    double distToOwner = minion.distanceToSqr(owner);
                                    boolean tooFarFromOwner = distToOwner > 324; // > 18 blocks : Come back!
                                    
                                    boolean cantSeeTarget = !minion.hasLineOfSight(minion.getTarget()) && minion.distanceToSqr(minion.getTarget()) > 25; // > 5 blocks & blocked view : Give up
                                    
                                    if (tooFarFromOwner || cantSeeTarget) {
                                        minion.setTarget(null); // Stop attacking
                                    }
                                }
                                
                                // 1. COMBAT PRIORITY: Scan for enemies nearby (16 blocks)
                                // Only scan if we don't already have a valid target
                                if (minion.getTarget() == null || !minion.getTarget().isAlive()) {
                                    net.minecraft.world.phys.AABB aggroBox = minion.getBoundingBox().inflate(16.0);
                                    java.util.List<net.minecraft.world.entity.Mob> enemies = world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, aggroBox, e -> 
                                         e != minion
                                         && e.isAlive()
                                         && !e.getUUID().equals(minion.getUUID())
                                         && !(e instanceof net.minecraft.world.entity.TamableAnimal && ((net.minecraft.world.entity.TamableAnimal)e).isTame())
                                         && !(e instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton && e.getTeam() == minion.getTeam())
                                         && !(e.getTeam() != null && e.getTeam().isAlliedTo(minion.getTeam()))
                                    );
                                    
                                    if (!enemies.isEmpty()) {
                                        // Found food! Attack priority.
                                        enemies.sort((e1, e2) -> Double.compare(e1.distanceToSqr(minion), e2.distanceToSqr(minion)));
                                        minion.setTarget(enemies.get(0));
                                        minion.setAggressive(true);
                                    }
                                }
                                
                                // 2. FOLLOW PRIORITY: If we are NOT fighting, follow the master.
                                if (minion.getTarget() == null || !minion.getTarget().isAlive()) {
                                    double distSqr = minion.distanceToSqr(owner);
                                    
                                    // Teleport if lost/stuck (> 20 blocks)
                                    if (distSqr > 400) {
                                        minion.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                                        minion.setDeltaMovement(0, 0, 0);
                                    }
                                    
                                    // Run to owner if hanging behind (> 5 blocks)
                                    else if (distSqr > 25) {
                                         minion.getNavigation().moveTo(owner, 1.35); // Run fast
                                    }
                                }
                            }
                         } catch (Exception e) {
                             // Ignore invalid tags
                         }
                    }
                }
            }
        });
	}
}
