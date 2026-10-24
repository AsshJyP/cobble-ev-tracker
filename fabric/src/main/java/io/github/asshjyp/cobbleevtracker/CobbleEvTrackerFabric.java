package io.github.asshjyp.cobbleevtracker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Arranque en Fabric: registra la tecla, el tick y el HUD; el resto está en common (CobbleEvTracker). */
public class CobbleEvTrackerFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeyBindingHelper.registerKeyBinding(CobbleEvTracker.OPEN_MENU);
		ClientTickEvents.END_CLIENT_TICK.register(CobbleEvTracker::onClientTick);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> EvHud.render(graphics));
		CobbleEvTracker.init();
	}
}
