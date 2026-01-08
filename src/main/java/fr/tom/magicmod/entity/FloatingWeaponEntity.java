package fr.tom.magicmod.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.util.List;
import java.util.UUID;

public class FloatingWeaponEntity extends Entity {
    private UUID ownerUUID;
    private int orbitIndex = 0;
    private float orbitAngle = 0;
    private static final float ORBIT_RADIUS = 2.0f;
    private static final float ORBIT_SPEED = 0.08f;
    private static final float DAMAGE = 4.0f;
    
    // State machine
    private boolean isLaunched = false;
    private Vec3 launchDirection = Vec3.ZERO;
    private int lifeTime = 0;
    private boolean isStuck = false;
    
    // Synched Data
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LAUNCHED = 
        SynchedEntityData.defineId(FloatingWeaponEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public FloatingWeaponEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }
    
    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
    }
    
    public void setOrbitIndex(int index) {
        this.orbitIndex = index;
        this.orbitAngle = (float) (index * (Math.PI * 2 / 5.0));
    }
    
    public void launch(Vec3 direction) {
        this.isLaunched = true;
        this.launchDirection = direction.normalize(); 
        this.entityData.set(LAUNCHED, true);
        this.lifeTime = 0; 
        
        // Initial Velocity (Power of the throw)
        this.setDeltaMovement(this.launchDirection.scale(2.5)); // High initial speed
        
        // Initial rotation aligned with velocity
        double d = this.launchDirection.horizontalDistance();
        this.setYRot((float)(net.minecraft.util.Mth.atan2(this.launchDirection.x, this.launchDirection.z) * 57.2957763671875));
        this.setXRot((float)(net.minecraft.util.Mth.atan2(this.launchDirection.y, d) * 57.2957763671875));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }
    
    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (level().isClientSide()) {
            // Client logic (Particles)
            if (isLaunched()) {
                 Vec3 movement = this.getDeltaMovement();
                 if (movement.lengthSqr() > 0.1) {
                    level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() - movement.x * 0.5, 
                        this.getY() + 0.5 - movement.y * 0.5, 
                        this.getZ() - movement.z * 0.5,
                        0, 0, 0);
                 }
            } else {
               // Orbiting particles...
                if (random.nextFloat() < 0.1) {
                    level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + (random.nextDouble() - 0.5) * 0.5,
                        this.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                        this.getZ() + (random.nextDouble() - 0.5) * 0.5,
                        0, 0.02, 0);
                }
            }
            return;
        }

        // Server Logic
        Player owner = getOwner();
        
        // Despawn checks
        if (!isLaunched && (owner == null || !owner.isAlive() || owner.distanceTo(this) > 32.0)) {
            this.discard();
            return;
        }
        
        if (isLaunched()) {
            if (this.isStuck) {
                 // Stuck Logic: Don't move, just exist until timer runs out
                 this.setDeltaMovement(Vec3.ZERO);
                 
                 lifeTime++;
                 if (lifeTime > 400) { // 20 seconds
                     this.discard();
                 }
                 return;
            }

            // --- PHYSICS SIMULATION ---
            
            // 1. Move using current velocity
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            
            // 2. Update Rotation to follow velocity vector
            Vec3 noteVelocity = this.getDeltaMovement();
            if (noteVelocity.horizontalDistanceSqr() > 0.01) {
                // Update rotation towards movement direction (Projectile.lerpRotation logic)
                double d = noteVelocity.horizontalDistance();
                this.setYRot((float)(net.minecraft.util.Mth.atan2(noteVelocity.x, noteVelocity.z) * 57.2957763671875));
                this.setXRot((float)(net.minecraft.util.Mth.atan2(noteVelocity.y, d) * 57.2957763671875));
            }
            
            // 3. Apply Drag (Air resistance)
            // Tridents use 0.99 water / 0.99 air. We'll use 0.99 for "heavy" feel.
            this.setDeltaMovement(this.getDeltaMovement().scale(0.99));
            
            // 4. Apply Gravity
            // Only if not NoGravity. Swords are heavy.
            if (!this.isNoGravity()) {
                // Subtract gravity from Y velocity
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.05, 0)); 
            }
            
            lifeTime++;
            
            // --- COLLISION LOGIC ---
            // Entity Collision
            AABB collider = this.getBoundingBox().inflate(0.3); // Slightly larger hit box
            List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, collider);
            
            boolean hit = false;
            for (LivingEntity target : targets) {
                if (target != owner && target.isAlive() && (owner == null || !isAllied(target, owner))) {
                    target.hurt(level().damageSources().playerAttack(owner), DAMAGE * 1.5f);
                    hit = true;
                    // Don't stick on entities, just pass through or maybe knockback? 
                    // For now, let's keep pass-through behavior or we can stick to entity (complex).
                    // User said "stuck", usually implies blocks.
                }
            }
            
            // Block Collision (checking checks from 'move')
            // 'horizontalCollision' and 'verticalCollision' are set by move()
            if (this.horizontalCollision || this.verticalCollision) {
                // STUCK LOGIC
                this.isStuck = true;
                this.setDeltaMovement(Vec3.ZERO);
                this.setNoGravity(true);
                this.playSound(net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
                
                // Reset lifetime to give it time to be seen
                this.lifeTime = 0; 
            }
            
            // Max flight time before auto-despawn (if void or sky)
            if (lifeTime > 200 && !isStuck) { // 10 seconds flight
                this.discard();
            }
            
        } else {
            // Orbiting Motion
            if (owner != null) {
                orbitAngle += ORBIT_SPEED;
                if (orbitAngle > Math.PI * 2) {
                    orbitAngle -= Math.PI * 2;
                }
                
                double x = owner.getX() + Math.cos(orbitAngle) * ORBIT_RADIUS;
                double y = owner.getY() + 1.5 + Math.sin(orbitAngle * 3) * 0.3;
                double z = owner.getZ() + Math.sin(orbitAngle) * ORBIT_RADIUS;
                
                this.setPos(x, y, z);
                
                // Passive damage while orbiting
                AABB damageBox = this.getBoundingBox().inflate(0.5);
                List<LivingEntity> nearbyMobs = level().getEntitiesOfClass(LivingEntity.class, damageBox);
                
                for (LivingEntity mob : nearbyMobs) {
                    if (mob != owner && mob.isAlive() && !isAllied(mob, owner)) {
                        mob.hurt(level().damageSources().playerAttack(owner), DAMAGE);
                        Vec3 direction = mob.position().subtract(owner.position()).normalize();
                        mob.setDeltaMovement(mob.getDeltaMovement().add(direction.scale(0.3)));
                    }
                }
            }
        }
    }
    
    private boolean isAllied(LivingEntity entity, Player owner) {
        if (entity instanceof Player) {
            return true;
        }
        if (entity.getTeam() != null && owner.getTeam() != null) {
            return entity.getTeam().equals(owner.getTeam());
        }
        return false;
    }
    
    public Player getOwner() {
        if (ownerUUID != null && level() != null) {
            return level().getPlayerByUUID(ownerUUID);
        }
        return null;
    }
    
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED, false);
    }
    
    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putBoolean("Launched", this.isLaunched);
    }
    
    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        this.isLaunched = in.getBooleanOr("Launched", false); // Default false
        this.entityData.set(LAUNCHED, this.isLaunched);
    }
    
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // Invulnerable
    }
}
