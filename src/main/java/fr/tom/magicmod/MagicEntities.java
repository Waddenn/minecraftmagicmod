package fr.tom.magicmod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;

import fr.tom.magicmod.entity.FloatingWeaponEntity;

public class MagicEntities {
    public static final ResourceKey<EntityType<?>> FLOATING_WEAPON_KEY = ResourceKey.create(
        Registries.ENTITY_TYPE, 
        Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "floating_weapon")
    );
    
    public static final EntityType<FloatingWeaponEntity> FLOATING_WEAPON = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        FLOATING_WEAPON_KEY,
        EntityType.Builder.<FloatingWeaponEntity>of(FloatingWeaponEntity::new, MobCategory.MISC)
            .sized(0.3f, 0.3f)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(FLOATING_WEAPON_KEY)
    );
    
    public static void registerEntities() {
        MagicMod.LOGGER.info("Registering Entities for " + MagicMod.MOD_ID);
    }
}
