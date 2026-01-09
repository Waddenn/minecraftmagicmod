package fr.tom.magicmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class FloatingWeaponEntity extends AbstractArrow {
    // Projectile handles owner
    private int orbitIndex = 0;
    private float orbitAngle = 0;
    private static final float ORBIT_RADIUS = 2.0f;
    private static final float ORBIT_SPEED = 0.05f; // Adjusted speed
    private static final double DAMAGE = 6.0;

    // Custom Synched Data for "Launched" state (AbstractArrow doesn't have a specific "launched" flag exposed easily for client rendering sync if we want to separate Orbit vs Projectile mode cleanly, though we could use shotFromCrossbow or similar, existing custom flag is safer)
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LAUNCHED = 
        SynchedEntityData.defineId(FloatingWeaponEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    // Synced Orbit Index to ensure Client/Server agree on position offset
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ORBIT_INDEX = 
        SynchedEntityData.defineId(FloatingWeaponEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    
    // Client-side Visuals for smooth launch transition
    public Vec3 visualLaunchOffset = Vec3.ZERO;
    public long clientLaunchTime = 0;

    public FloatingWeaponEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(DAMAGE);
        // "pickup" field might be protected or named differently. 
        // If not accessible, we rely on getDefaultPickupItem returning EMPTY.
        // this.pickup = AbstractArrow.Pickup.DISALLOWED; 
        this.setNoGravity(true); // No gravity initially (while orbiting)
    }

    // Server-side launch tracking
    private long serverLaunchTime = 0;
    private int groundDuration = 0;

    @Override
    protected ItemStack getDefaultPickupItem() {
        // Return a valid item to prevent "0 minecraft:air" serialization errors
        return new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
    }

    // Projectile has setOwner(Entity)
    public void setOwner(Player player) {
        super.setOwner(player);
    }
    
    public void setOrbitIndex(int index) {
        this.entityData.set(ORBIT_INDEX, index);
        // Persistence via Tags
        if (!this.level().isClientSide()) {
            // Remove old tags
            this.getTags().removeIf(tag -> tag.startsWith("OrbitIndex:"));
            this.addTag("OrbitIndex:" + index);
        }
    }
    
    public int getOrbitIndex() {
        return this.entityData.get(ORBIT_INDEX);
    }

    public void launch(Vec3 direction) {
        this.entityData.set(LAUNCHED, true);
        if (!this.level().isClientSide()) {
            this.addTag("Launched");
            this.serverLaunchTime = this.level().getGameTime();
        }
        // this.setNoGravity(false); // Disable gravity enable - keep it noGravity=true for straight flight
        
        // AbstractArrow shoot logic
        // method signature: shoot(x, y, z, velocity, inaccuracy)
        this.shoot(direction.x, direction.y, direction.z, 2.5f, 0.0f);
        
        // Force rotation update immediately to prevent 1-tick incorrect rendering
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        this.setYRot((float)(Math.atan2(direction.x, direction.z) * (180F / (float)Math.PI)));
        this.setXRot((float)(Math.atan2(direction.y, horizontalDistance) * (180F / (float)Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // Play launch sound
        this.playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.0F);
    }

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    private boolean clientLaunched = false;

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LAUNCHED.equals(key)) {
            boolean fired = this.entityData.get(LAUNCHED);
            if (fired && !clientLaunched) {
                // Just launched on client side.
                // Snap rotation to velocity to prevent "Swoosh" interpolation from orbit angle.
                this.clientLaunched = true;
                
                // We need to estimate rotation from velocity because updateRotation() might not have run yet
                // or we want to force the snap before rendering.
                Vec3 velocity = this.getDeltaMovement();
                if (velocity.lengthSqr() > 0.001) {
                    double horizontalDistance = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
                    float yaw = (float)(Math.atan2(velocity.x, velocity.z) * (180F / (float)Math.PI));
                    float pitch = (float)(Math.atan2(velocity.y, horizontalDistance) * (180F / (float)Math.PI));
                    
                    this.setYRot(yaw);
                    this.setXRot(pitch);
                    this.yRotO = yaw;
                    this.xRotO = pitch;
                }
            }
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.tickCount == 0) {
            // Restore state from Tags (Persistence)
            if (this.getTags().contains("Launched")) {
                this.entityData.set(LAUNCHED, true);
            }
            for (String tag : this.getTags()) {
                if (tag.startsWith("OrbitIndex:")) {
                    try {
                        int index = Integer.parseInt(tag.substring("OrbitIndex:".length()));
                        this.entityData.set(ORBIT_INDEX, index);
                    } catch (NumberFormatException e) {
                        // Ignore malformed tag
                    }
                }
            }
        }

        if (!isLaunched()) {
            this.baseTick(); 
            
            // Server-Side Authority only.
            // Client relies on Renderer for smooth interpolation.
            if (!this.level().isClientSide()) {
                Entity owner = this.getOwner();
                if (owner instanceof Player player && owner.isAlive()) {
                    // DESPAWN LOGIC: If player is not holding the Grimoire, disappear.
                    boolean holdingGrimoire = player.getMainHandItem().is(fr.tom.magicmod.MagicItems.SPECTRAL_GRIMOIRE) 
                                           || player.getOffhandItem().is(fr.tom.magicmod.MagicItems.SPECTRAL_GRIMOIRE);
                    
                    if (!holdingGrimoire) {
                        this.disappear();
                        return;
                    }

                    long time = this.level().getGameTime();
                    int index = getOrbitIndex();
                    
                    float currentAngle = (time * ORBIT_SPEED) + (float) (index * (Math.PI * 2 / 5.0));
                    
                    double x = owner.getX() + Math.cos(currentAngle) * ORBIT_RADIUS;
                    double y = owner.getY() + 1.5 + Math.sin(currentAngle * 3) * 0.3;
                    double z = owner.getZ() + Math.sin(currentAngle) * ORBIT_RADIUS;
                    
                    this.setPos(x, y, z);
                    this.setDeltaMovement(Vec3.ZERO);
                } else if (owner == null) {
                     this.disappear();
                }
            }
        } else {
            super.tick();
            
            if (!this.level().isClientSide()) {
                // Lifespan Checks
                if (serverLaunchTime == 0 && isLaunched()) {
                    // Recover from reload
                    serverLaunchTime = this.level().getGameTime();
                }
                
                // 1. Flight Timeout: 10 seconds (200 ticks)
                if (serverLaunchTime > 0 && this.level().getGameTime() - serverLaunchTime > 200) {
                    this.disappear();
                    return;
                }
                
                // 2. Ground Timeout: 2 seconds (40 ticks)
                // 2. Ground/Stuck Timeout: 2 minutes (2400 ticks)
                // Since 'inGround' field is not accessible, check if velocity is effectively zero.
                if (this.getDeltaMovement().lengthSqr() < 0.001) {
                    this.groundDuration++;
                    
                    // User Request: Launched swords stuck in ground should vanish if Grimoire is un-equipped.
                    Entity owner = this.getOwner();
                    if (owner instanceof Player player) {
                        boolean holdingGrimoire = player.getMainHandItem().is(fr.tom.magicmod.MagicItems.SPECTRAL_GRIMOIRE) 
                                               || player.getOffhandItem().is(fr.tom.magicmod.MagicItems.SPECTRAL_GRIMOIRE);
                        if (!holdingGrimoire) {
                            this.disappear();
                            return;
                        }
                    }

                    if (this.groundDuration > 2400) {
                         // User requested no timed despawn for stuck swords
                         // keeping the counter logic valid but disabling the trigger
                         // actually just removing the block is cleaner
                    }
                } else {
                    this.groundDuration = 0;
                }
            }
            
            if (this.level().isClientSide() && this.getDeltaMovement().lengthSqr() > 0.1) {
                 Vec3 movement = this.getDeltaMovement();
                 level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() - movement.x * 0.5, 
                        this.getY() + 0.5 - movement.y * 0.5, 
                        this.getZ() - movement.z * 0.5,
                        0, 0, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // Prepare collision filtering: Ignore other swords and the owner
        if (entity instanceof FloatingWeaponEntity) {
            return false;
        }
        if (this.getOwner() != null && this.getOwner().equals(entity)) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); 
        // Sound handled by getHitGroundSoundEvent override
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); 
        builder.define(LAUNCHED, false);
        builder.define(ORBIT_INDEX, 0); // Default index 0
    }
    
    // NBT Persistence handled via Scoreboard Tags in tick()
    /* 
     * NBT methods omitted due to mapping/wrapper issues.
     * State is saved via entity.getTags() automatically by vanilla.
     */
    private void disappear() {
        if (!this.level().isClientSide()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, this.getSoundSource(), 1.0F, 1.0F);
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
        }
        this.discard();
    }
}
