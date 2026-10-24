package io.github.asshjyp.cobbleevtracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Evoluciones de cada especie, leídas de los JSON que trae el jar de Cobblemon
 * ({@code data/cobblemon/species/generationN/<especie>.json}). El servidor no envía las evoluciones
 * al cliente (sí las stats base), así que es la única forma de saberlas sin internet. Las especies
 * que añada un servidor no están aquí: para ellas no se sigue ninguna evolución.
 */
public final class EvolutionLookup {
	/** Nombre de especie (ej. "magikarp") → ruta de su JSON dentro del jar. Se llena la primera vez. */
	private static Map<String, Path> files;
	/** "especie" o "especie/forma" → resultados de evolución tal cual ("gyarados", "raichu alolan"…). */
	private static final Map<String, List<String>> CACHE = new HashMap<>();

	private EvolutionLookup() {}

	/**
	 * Resultados de evolución de una especie; si la forma tiene los suyos propios (ej. Slowpoke de Galar),
	 * esos. Lista vacía si no evoluciona o no se encuentra.
	 */
	public static List<String> evolutions(String species, String form) {
		return CACHE.computeIfAbsent(species + "/" + form, key -> read(species, form));
	}

	private static List<String> read(String species, String form) {
		Path path = files().get(species);
		if (path == null) {
			return List.of();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			if (json.has("forms")) {
				for (JsonElement element : json.getAsJsonArray("forms")) {
					JsonObject formJson = element.getAsJsonObject();
					if (formJson.has("name") && formJson.get("name").getAsString().equalsIgnoreCase(form)
							&& formJson.has("evolutions")) {
						return results(formJson);
					}
				}
			}
			return results(json);
		} catch (IOException | RuntimeException e) {
			CobbleEvTracker.LOGGER.warn("No se pudieron leer las evoluciones de {}", species, e);
			return List.of();
		}
	}

	private static List<String> results(JsonObject json) {
		List<String> results = new ArrayList<>();
		if (json.has("evolutions")) {
			for (JsonElement element : json.getAsJsonArray("evolutions")) {
				JsonObject evolution = element.getAsJsonObject();
				if (evolution.has("result")) {
					results.add(evolution.get("result").getAsString());
				}
			}
		}
		return results;
	}

	private static Map<String, Path> files() {
		if (files != null) {
			return files;
		}
		files = new HashMap<>();
		Optional<Path> root = Services.PLATFORM.findModResource("cobblemon", "data/cobblemon/species");
		if (root.isPresent()) {
			try (Stream<Path> paths = Files.walk(root.get())) {
				paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
					String name = p.getFileName().toString();
					files.put(name.substring(0, name.length() - ".json".length()), p);
				});
			} catch (IOException e) {
				CobbleEvTracker.LOGGER.warn("No se pudo leer la lista de especies de Cobblemon", e);
			}
		}
		return files;
	}
}
