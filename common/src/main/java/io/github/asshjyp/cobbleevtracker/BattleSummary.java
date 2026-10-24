package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * B. Resumen al terminar un combate propio: un solo aviso con todos los EVs que ganó cada Pokémon del equipo
 * ("Combate terminado: Lopunny +2 Ataque, +1 Velocidad"), en vez de un aviso por cada cambio.
 * <p>
 * Igual que Cobble Hunt Log: el combate empieza cuando {@code CobblemonClient.getBattle()} deja de ser null y termina
 * cuando vuelve a serlo (o cambia de id). Se copian los EVs del equipo al empezar y se comparan al terminar, después de
 * una pequeña espera: el último rival cae justo antes del final y sus EVs pueden llegar unos ticks más tarde.
 * Lo que cambia fuera de un combate (vitaminas, bayas) no cuenta como combate.
 */
public final class BattleSummary {
	/** Ticks de espera tras el combate antes de comparar (1 s). */
	private static final int SETTLE_TICKS = 20;
	private static final SystemToast.SystemToastId SUMMARY_TOAST = new SystemToast.SystemToastId(8000);

	private enum State { IDLE, IN_BATTLE, SETTLING }

	private static State state = State.IDLE;
	private static UUID battleId;
	private static int settleTicks;
	/** EVs de cada Pokémon del equipo al empezar el combate. */
	private static final Map<UUID, int[]> START = new HashMap<>();

	private BattleSummary() {}

	/**
	 * ¿Los cambios de EVs de ahora son de un combate? (durante el combate o en la espera de después).
	 * EvNotifier lo usa para no avisar dos veces en modo "Resumen al final".
	 */
	public static boolean isCollecting() {
		return state != State.IDLE;
	}

	/** Al final de cada tick del cliente (lo llama CobbleEvTracker.onClientTick). */
	static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			// Fuera de un mundo no hay resumen que dar
			state = State.IDLE;
			START.clear();
			return;
		}
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		// Solo combates propios: como espectador no ganas EVs
		UUID ownBattle = battle == null || battle.getSpectating() ? null : battle.getBattleId();
		switch (state) {
			case IDLE -> {
				if (ownBattle != null) {
					start(ownBattle);
				}
			}
			case IN_BATTLE -> {
				if (!battleId.equals(ownBattle)) {
					state = State.SETTLING;
					settleTicks = SETTLE_TICKS;
				}
			}
			case SETTLING -> {
				// Si empieza otro combate antes de acabar la espera, se cierra el anterior ya
				if (ownBattle != null || --settleTicks <= 0) {
					finish(client);
					if (ownBattle != null) {
						start(ownBattle);
					}
				}
			}
		}
	}

	private static void start(UUID id) {
		state = State.IN_BATTLE;
		battleId = id;
		START.clear();
		for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
			if (pokemon != null) {
				START.put(pokemon.getUuid(), EvStats.read(pokemon.getEvs()));
			}
		}
	}

	private static void finish(Minecraft client) {
		state = State.IDLE;
		List<Component> lines = new ArrayList<>();
		for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
			int[] before = pokemon == null ? null : START.get(pokemon.getUuid());
			if (before == null) {
				continue;
			}
			int[] after = EvStats.read(pokemon.getEvs());
			int[] diff = new int[EvStats.COUNT];
			for (int i = 0; i < EvStats.COUNT; i++) {
				diff[i] = after[i] - before[i];
			}
			Component gained = EvStats.describe(diff);
			if (gained != null) {
				lines.add(Component.translatable("cobbleevtracker.summary.line",
						pokemon.getDisplayName(false).copy().withStyle(ChatFormatting.GOLD), gained));
			}
		}
		START.clear();
		EvConfig config = CobbleEvTracker.CONFIG;
		boolean wanted = config.gainTiming == EvConfig.GainTiming.BATTLE_SUMMARY
				|| config.gainTiming == EvConfig.GainTiming.BOTH;
		if (lines.isEmpty() || !wanted) {
			return;
		}
		CobbleEvTracker.LOGGER.info("[Cobble EV Tracker] Resumen del combate: {}",
				String.join(" | ", lines.stream().map(Component::getString).toList()));
		Component title = Component.translatable("cobbleevtracker.summary.title");
		if (config.gainMode == EvConfig.GainMode.CHAT) {
			for (Component line : lines) {
				EvNotifier.chat(client, Component.empty().append(title).append(" — ").append(line));
			}
		} else {
			// Un solo aviso, una línea por Pokémon (multiline parte el texto en los saltos de línea)
			MutableComponent message = Component.empty();
			for (int i = 0; i < lines.size(); i++) {
				message.append(i == 0 ? Component.empty() : Component.literal("\n")).append(lines.get(i));
			}
			client.getToasts().addToast(SystemToast.multiline(client, SUMMARY_TOAST, title, message));
		}
	}
}
