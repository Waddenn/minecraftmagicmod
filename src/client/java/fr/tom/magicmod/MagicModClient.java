package fr.tom.magicmod;

import net.fabricmc.api.ClientModInitializer;

public class MagicModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Client initialization logic - No manual item property registration needed in 1.21.4+
		
		// Register entity renderers
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
			MagicEntities.FLOATING_WEAPON, 
			fr.tom.magicmod.client.FloatingWeaponRenderer::new
		);
	}
}
