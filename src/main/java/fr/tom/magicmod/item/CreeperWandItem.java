package fr.tom.magicmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class CreeperWandItem extends Item {
    public CreeperWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            // 1. Protection: Give Resistance V (100% protection) for 1 second (20 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4)); // Amplifier 4 = Level 5 = 100% reduction

            // 2. Explosion: At player position, Power 3.0 (Creeper size)
            // Interaction.MOB means it damages blocks/mobs standardly.
            level.explode(player, player.getX(), player.getY(), player.getZ(), 3.0F, Level.ExplosionInteraction.MOB);
        } else {
            // Client-side sound (Fuse sound)
            level.playSound(player, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        return InteractionResult.SUCCESS;
    }
}
