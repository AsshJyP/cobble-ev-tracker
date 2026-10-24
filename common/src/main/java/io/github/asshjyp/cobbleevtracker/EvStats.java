package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.EVs;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Las 6 stats con EVs, siempre en el mismo orden que el resumen de Cobblemon. Los arrays int[6] del mod siguen este orden. */
public final class EvStats {
	public static final Stats[] STATS = {
			Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
	/** Sufijos de las claves cortas de Cobblemon ({@code cobblemon.ui.stats.*}): "PS", "At.", "Vel."… */
	private static final String[] SHORT_KEYS = {"hp", "atk", "def", "sp_atk", "sp_def", "speed"};
	public static final int COUNT = STATS.length;
	/** 252 */
	public static final int MAX_STAT = EVs.MAX_STAT_VALUE;
	/** 510 */
	public static final int MAX_TOTAL = EVs.MAX_TOTAL_VALUE;

	private EvStats() {}

	public static int[] read(EVs evs) {
		int[] values = new int[COUNT];
		for (int i = 0; i < COUNT; i++) {
			values[i] = evs.getOrDefault(STATS[i]);
		}
		return values;
	}

	/** EV yield de una especie o forma, en el orden de {@link #STATS}. */
	public static int[] read(Map<Stat, Integer> yield) {
		int[] values = new int[COUNT];
		for (int i = 0; i < COUNT; i++) {
			values[i] = yield.getOrDefault(STATS[i], 0);
		}
		return values;
	}

	public static int sum(int[] values) {
		int total = 0;
		for (int v : values) {
			total += v;
		}
		return total;
	}

	/** Nombre corto de Cobblemon (PS, At., Def., At. esp., Def. esp., Vel.). */
	public static Component shortName(int index) {
		return Component.translatable("cobblemon.ui.stats." + SHORT_KEYS[index]);
	}

	/** Nombre completo de Cobblemon (Ataque Especial, Velocidad…). */
	public static Component fullName(int index) {
		return STATS[index].getDisplayName();
	}

	/** "252 At. / 252 Vel. / 4 PS": las stats distintas de 0, de mayor a menor (empate: orden del resumen). */
	public static Component compact(int[] evs) {
		Component result = null;
		for (int value = MAX_STAT; value > 0; value--) {
			for (int i = 0; i < COUNT; i++) {
				if (evs[i] != value) {
					continue;
				}
				Component part = Component.literal(value + " ").append(shortName(i));
				result = result == null ? part : Component.empty().append(result).append(" / ").append(part);
			}
		}
		return result == null ? Component.literal("0") : result;
	}

	/** "+2 Ataque, +1 Velocidad" con las stats distintas de 0, o null si todas son 0. */
	public static Component describe(int[] amounts) {
		Component result = null;
		for (int i = 0; i < COUNT; i++) {
			if (amounts[i] == 0) {
				continue;
			}
			Component part = Component.literal((amounts[i] > 0 ? "+" : "") + amounts[i] + " ").append(fullName(i));
			result = result == null ? part : Component.empty().append(result).append(", ").append(part);
		}
		return result;
	}
}
