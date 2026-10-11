package io.github.asshjyp.cobbleevtracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class CobbleEvTracker implements ClientModInitializer {
	public static final String MOD_ID = "cobbleevtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final EvConfig CONFIG = EvConfig.load();

	/** Abre el menú de opciones. N por defecto (H es de Cobble Hunt Log y K de Catch Rate Display); se cambia en Opciones > Controles. */
	private static final KeyMapping OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.cobbleevtracker.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.cobbleevtracker"));

	@Override
	public void onInitializeClient() {
		EvGoals.load();
		PartyWatcher.init();
		PartyWatcher.register(new EvNotifier());
		// Línea de log para comprobar la detección en latest.log
		PartyWatcher.register((pokemon, before, after) -> LOGGER.info("[Cobble EV Tracker] EVs de {}: {} -> {}",
				pokemon.getDisplayName(false).getString(), Arrays.toString(before), Arrays.toString(after)));
		EvNotifier.init();
		EvHud.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick solo da true jugando (sin otra pantalla abierta)
			while (OPEN_MENU.consumeClick()) {
				client.setScreen(new EvConfigScreen(client.screen));
			}
		});
		LOGGER.info("Cobble EV Tracker cargado");
	}
}
