package io.github.asshjyp.cobbleevtracker;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Arranque en NeoForge (solo cliente): tecla y capa del HUD en el bus del mod, tick en el bus del juego y botón
 * "Config" de la lista de mods; el resto está en common (CobbleEvTracker).
 */
@Mod(value = CobbleEvTracker.MOD_ID, dist = Dist.CLIENT)
public class CobbleEvTrackerNeoForge {
	public CobbleEvTrackerNeoForge(IEventBus modBus, ModContainer container) {
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(CobbleEvTracker.OPEN_MENU));
		// Encima de todas las capas de vanilla, como HudRenderCallback en Fabric
		modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
				ResourceLocation.fromNamespaceAndPath(CobbleEvTracker.MOD_ID, "hud"),
				(graphics, deltaTracker) -> EvHud.render(graphics)));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CobbleEvTracker.onClientTick(Minecraft.getInstance()));
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new EvConfigScreen(parent));
		CobbleEvTracker.init();
	}
}
