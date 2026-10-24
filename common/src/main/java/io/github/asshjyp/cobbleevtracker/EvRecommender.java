package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reparto de EVs recomendado. Los EVs se mantienen al evolucionar, así que se calcula para la
 * <b>evolución final</b> si la cadena es lineal (Magikarp → Gyarados, Mareep → Flaaffy → Ampharos).
 * Si hay varias evoluciones distintas (Eevee, Slowpoke…), se usa la forma actual.
 * <p>
 * Regla según las stats base (sin internet; es una regla general, no el set competitivo exacto).
 * "Ataque" es el físico o el especial, el mayor (empate: físico):
 * <ul>
 *   <li><b>Atacante rápido</b> (Vel. ≥ 80 y ataque ≥ 75): 252 ataque / 252 Vel. / 4 PS.</li>
 *   <li><b>Apoyo rápido</b> (Vel. ≥ 80 y ataque &lt; 75, ej. Jumpluff): 252 PS / 252 Vel. / 4 en su mejor defensa.</li>
 *   <li><b>Atacante lento</b> (ataque ≥ 110 o mayor que PS y defensas, ej. Snorlax, Ampharos): 252 PS / 252 ataque / 4 en su mejor defensa.</li>
 *   <li><b>Muro</b> (el resto, ej. Blissey, Ferrothorn): 252 PS / 252 en su mejor defensa / 4 en la otra.</li>
 * </ul>
 */
public final class EvRecommender {
	private static final int HP = 0;
	private static final int ATTACK = 1;
	private static final int DEFENCE = 2;
	private static final int SPECIAL_ATTACK = 3;
	private static final int SPECIAL_DEFENCE = 4;
	private static final int SPEED = 5;
	private static final int FAST = 80;
	private static final int USEFUL_ATTACK = 75;
	private static final int STRONG = 110;
	/** Ninguna cadena real pasa de 2 evoluciones; el tope solo evita bucles en datos raros. */
	private static final int MAX_STEPS = 4;

	public enum Role { FAST_ATTACKER, FAST_SUPPORT, SLOW_ATTACKER, WALL }

	/**
	 * evs en el orden de {@link EvStats#STATS}; base = stats base usadas; basedOn = especie para la que se
	 * calcula (la evolución final o la actual); branches = tiene varias evoluciones y se usó la actual.
	 */
	public record Recommendation(int[] evs, Role role, boolean special, int[] base, Species basedOn, boolean branches) {
		/** Por qué se recomienda: "rápido y con buen Ataque", "lento y resistente"… */
		public Component reason() {
			boolean attacker = role == Role.FAST_ATTACKER || role == Role.SLOW_ATTACKER;
			return Component.translatable("cobbleevtracker.recommend.reason." + role.name().toLowerCase(Locale.ROOT)
					+ (attacker ? special ? ".special" : ".physical" : ""));
		}
	}

	private EvRecommender() {}

	public static Recommendation recommend(Pokemon pokemon) {
		Species species = pokemon.getSpecies();
		FormData form = pokemon.getForm();
		boolean branches = false;
		Set<String> visited = new HashSet<>();
		for (int step = 0; step < MAX_STEPS && visited.add(species.getResourceIdentifier().getPath()); step++) {
			List<String> results = EvolutionLookup.evolutions(species.getResourceIdentifier().getPath(), form.getName());
			if (results.isEmpty()) {
				break;
			}
			// "raichu alolan" → especie "raichu" y aspecto "alolan"
			Set<String> names = new LinkedHashSet<>();
			for (String result : results) {
				names.add(result.trim().split("\\s+")[0].toLowerCase(Locale.ROOT));
			}
			if (names.size() > 1) {
				branches = true;
				break;
			}
			Species next = PokemonSpecies.getByName(names.iterator().next());
			if (next == null) {
				break;
			}
			// Aspectos del resultado + los del Pokémon (región, etc.) para elegir la forma de la evolución
			List<String> parts = new ArrayList<>(Arrays.asList(results.getFirst().trim().split("\\s+")));
			parts.removeFirst();
			Set<String> aspects = new HashSet<>(pokemon.getAspects());
			aspects.addAll(parts);
			species = next;
			form = next.getForm(aspects);
		}
		return recommend(EvStats.read(form.getBaseStats()), species, branches);
	}

	private static Recommendation recommend(int[] base, Species basedOn, boolean branches) {
		int[] evs = new int[EvStats.COUNT];
		boolean special = base[SPECIAL_ATTACK] > base[ATTACK];
		int offense = special ? SPECIAL_ATTACK : ATTACK;
		int bestDefence = base[DEFENCE] >= base[SPECIAL_DEFENCE] ? DEFENCE : SPECIAL_DEFENCE;
		int otherDefence = bestDefence == DEFENCE ? SPECIAL_DEFENCE : DEFENCE;
		int maxBulk = Math.max(base[HP], Math.max(base[DEFENCE], base[SPECIAL_DEFENCE]));

		Role role;
		if (base[SPEED] >= FAST && base[offense] >= USEFUL_ATTACK) {
			role = Role.FAST_ATTACKER;
			evs[offense] = 252;
			evs[SPEED] = 252;
			evs[HP] = 4;
		} else if (base[SPEED] >= FAST) {
			role = Role.FAST_SUPPORT;
			evs[HP] = 252;
			evs[SPEED] = 252;
			evs[bestDefence] = 4;
		} else if (base[offense] >= STRONG || base[offense] > maxBulk) {
			role = Role.SLOW_ATTACKER;
			evs[HP] = 252;
			evs[offense] = 252;
			evs[bestDefence] = 4;
		} else {
			role = Role.WALL;
			evs[HP] = 252;
			evs[bestDefence] = 252;
			evs[otherDefence] = 4;
		}
		return new Recommendation(evs, role, special, base, basedOn, branches);
	}
}
