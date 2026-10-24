package io.github.asshjyp.cobbleevtracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Lo común a Fabric y NeoForge. Cada loader registra OPEN_MENU a su manera (KeyBindingHelper / RegisterKeyMappingsEvent),
 * dibuja EvHud.render en su capa del HUD (HudRenderCallback / RegisterGuiLayersEvent) y llama a onClientTick al final
 * de cada tick del cliente (ClientTickEvents.END_CLIENT_TICK / ClientTickEvent.Post).
 */
public final class CobbleEvTracker {
	public static final String MOD_ID = "cobbleevtracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final EvConfig CONFIG = EvConfig.load();

	/** Abre el menú de opciones. N por defecto (H es de Cobble Hunt Log y K de Catch Rate Display); se cambia en Opciones > Controles. */
	public static final KeyMapping OPEN_MENU = new KeyMapping(
			"key.cobbleevtracker.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.cobbleevtracker");

	private CobbleEvTracker() {}

	/** Al arrancar el cliente, después de registrar la tecla. */
	public static void init() {
		EvGoals.load();
		PartyWatcher.register(new EvNotifier());
		// Línea de log para comprobar la detección en latest.log
		PartyWatcher.register((pokemon, before, after) -> LOGGER.info("[Cobble EV Tracker] EVs de {}: {} -> {}",
				pokemon.getDisplayName(false).getString(), Arrays.toString(before), Arrays.toString(after)));
		LOGGER.info("Cobble EV Tracker cargado ({})", Services.PLATFORM.getPlatformName());
	}

	/** Al final de cada tick del cliente. */
	public static void onClientTick(Minecraft client) {
		PartyWatcher.tick(client);
		BattleSummary.tick(client);
		EvNotifier.tickRival(client);
		// consumeClick solo da true jugando (sin otra pantalla abierta)
		while (OPEN_MENU.consumeClick()) {
			client.setScreen(new EvConfigScreen(client.screen));
		}
	}
}
