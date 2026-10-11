package io.github.asshjyp.cobbleevtracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Objetivos de EVs por Pokémon en {@code config/cobbleevtracker/goals.json}, guardados por UUID
 * (así siguen valiendo aunque cambies el orden del party o lo dejes un tiempo en el PC).
 * Cada objetivo es un int[6] en el orden de {@link EvStats#STATS}.
 */
public final class EvGoals {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = EvConfig.DIR.resolve("goals.json");
	private static final Type TYPE = new TypeToken<HashMap<String, int[]>>() {}.getType();

	/** Repartos típicos. Los valores van en el orden PS, At., Def., At. esp., Def. esp., Vel. */
	public enum Template {
		PHYSICAL_SWEEPER(4, 252, 0, 0, 0, 252),
		SPECIAL_SWEEPER(4, 0, 0, 252, 0, 252),
		PHYSICAL_WALL(252, 0, 252, 0, 4, 0),
		SPECIAL_WALL(252, 0, 4, 0, 252, 0);

		private final int[] values;

		Template(int... values) {
			this.values = values;
		}

		public int[] evs() {
			return values.clone();
		}
	}

	private static Map<String, int[]> goals = new HashMap<>();

	private EvGoals() {}

	static void load() {
		if (!Files.exists(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			Map<String, int[]> read = GSON.fromJson(reader, TYPE);
			if (read != null) {
				// Solo se quedan los objetivos válidos (6 valores de 0 a 252, total ≤ 510 y no todo 0)
				read.values().removeIf(goal -> !isValid(goal) || EvStats.sum(goal) == 0);
				goals = read;
			}
		} catch (IOException | JsonParseException e) {
			CobbleEvTracker.LOGGER.warn("No se pudo leer {}; se empieza sin objetivos", PATH, e);
		}
	}

	public static boolean isValid(int[] goal) {
		if (goal == null || goal.length != EvStats.COUNT) {
			return false;
		}
		for (int v : goal) {
			if (v < 0 || v > EvStats.MAX_STAT) {
				return false;
			}
		}
		return EvStats.sum(goal) <= EvStats.MAX_TOTAL;
	}

	/** Objetivo del Pokémon, o null si no tiene. */
	public static int[] get(UUID pokemon) {
		int[] goal = goals.get(pokemon.toString());
		return goal == null ? null : goal.clone();
	}

	/** Cambia el objetivo (todo 0 = lo borra). No guarda el archivo: llamar a {@link #save()} después. */
	public static void set(UUID pokemon, int[] goal) {
		if (!isValid(goal)) {
			return;
		}
		if (EvStats.sum(goal) == 0) {
			goals.remove(pokemon.toString());
		} else {
			goals.put(pokemon.toString(), goal.clone());
		}
	}

	public static void save() {
		try {
			Files.createDirectories(EvConfig.DIR);
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(goals, TYPE, writer);
			}
		} catch (IOException e) {
			CobbleEvTracker.LOGGER.warn("No se pudo guardar {}", PATH, e);
		}
	}
}
