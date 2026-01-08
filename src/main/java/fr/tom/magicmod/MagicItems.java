package fr.tom.magicmod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;

import fr.tom.magicmod.item.MagicWandItem;
import fr.tom.magicmod.item.CreeperWandItem;
import fr.tom.magicmod.item.CosmosStaffItem;
import fr.tom.magicmod.item.NecromancerStaffItem;
import fr.tom.magicmod.item.SpectralGrimoireItem;


public class MagicItems {
    public static final ResourceKey<Item> MAGIC_WAND_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "magic_wand"));
    public static final Item MAGIC_WAND = register(MAGIC_WAND_KEY, new MagicWandItem(new Item.Properties().setId(MAGIC_WAND_KEY)));

    public static final ResourceKey<Item> CREEPER_WAND_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "creeper_wand"));
    public static final Item CREEPER_WAND = register(CREEPER_WAND_KEY, new CreeperWandItem(new Item.Properties().setId(CREEPER_WAND_KEY)));

    public static final ResourceKey<Item> COSMOS_STAFF_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "cosmos_staff"));
    public static final Item COSMOS_STAFF = register(COSMOS_STAFF_KEY, new CosmosStaffItem(new Item.Properties().setId(COSMOS_STAFF_KEY)));

    public static final ResourceKey<Item> NECROMANCER_STAFF_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "necromancer_staff"));
    public static final Item NECROMANCER_STAFF = register(NECROMANCER_STAFF_KEY, new NecromancerStaffItem(new Item.Properties().setId(NECROMANCER_STAFF_KEY)));

    // Register Items
    public static final ResourceKey<Item> SPECTRAL_GRIMOIRE_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "spectral_grimoire"));
    public static final Item SPECTRAL_GRIMOIRE = register(SPECTRAL_GRIMOIRE_KEY, new SpectralGrimoireItem(new Item.Properties().setId(SPECTRAL_GRIMOIRE_KEY).stacksTo(1)));

    private static Item register(ResourceKey<Item> key, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void registerModItems() {
        MagicMod.LOGGER.info("Registering Mod Items for " + MagicMod.MOD_ID);
        
        // Add to creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(MAGIC_WAND);
            entries.accept(CREEPER_WAND);
            entries.accept(COSMOS_STAFF);
            entries.accept(NECROMANCER_STAFF);
            entries.accept(SPECTRAL_GRIMOIRE); // Changed item
        });
    }
}
