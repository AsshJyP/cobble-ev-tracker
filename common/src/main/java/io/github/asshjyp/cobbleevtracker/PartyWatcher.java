package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guarda una copia de los EVs de cada Pokémon del party (por UUID) y avisa cuando cambian.
 * Cobblemon actualiza los EVs del cliente con {@code EVsUpdatePacket}; aquí solo se compara cada tick
 * con el tick anterior (como mucho 6 × 6 números), sin mixins.
 */
public final class PartyWatcher {
	/** Ticks tras entrar a un mundo en los que solo se copian los EVs, sin avisar (el party aún se está sincronizando). */
	private static final int GRACE_TICKS = 40;

	public interface Listener {
		void onEvsChanged(Pokemon pokemon, int[] before, int[] after);
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final Map<UUID, int[]> SNAPSHOT = new HashMap<>();
	private static int graceTicks = GRACE_TICKS;

	private PartyWatcher() {}

	public static void register(Listener listener) {
		LISTENERS.add(listener);
	}

	/** Al final de cada tick del cliente (lo llama CobbleEvTracker.onClientTick). */
	static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			SNAPSHOT.clear();
			graceTicks = GRACE_TICKS;
			return;
		}
		boolean silent = graceTicks > 0;
		if (silent) {
			graceTicks--;
		}
		Set<UUID> seen = new HashSet<>();
		for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
			if (pokemon == null) {
				continue;
			}
			UUID id = pokemon.getUuid();
			seen.add(id);
			int[] now = EvStats.read(pokemon.getEvs());
			int[] before = SNAPSHOT.put(id, now);
			// Un Pokémon que acaba de entrar al party (del PC, captura, intercambio) solo se copia
			if (before != null && !silent && !Arrays.equals(before, now)) {
				LISTENERS.forEach(l -> l.onEvsChanged(pokemon, before, now));
			}
		}
		SNAPSHOT.keySet().retainAll(seen);
	}
}
