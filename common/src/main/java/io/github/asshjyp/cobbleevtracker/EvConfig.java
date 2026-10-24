package io.github.asshjyp.cobbleevtracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración en {@code config/cobbleevtracker/config.json}. Se lee al arrancar Minecraft;
 * si no existe, se crea con los valores por defecto. El menú de opciones la cambia y la guarda.
 */
public final class EvConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIR = Services.PLATFORM.getConfigDir().resolve(CobbleEvTracker.MOD_ID);
	private static final Path PATH = DIR.resolve("config.json");
	/** Tope del desplazamiento del HUD; el HUD además nunca se sale de la pantalla. */
	public static final int MAX_OFFSET = 999;
	/** -4 anula el margen de 4 px: el HUD puede quedar pegado al borde. */
	public static final int MIN_OFFSET = -4;
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.0f;

	/** Esquina de la pantalla donde se dibuja el HUD. */
	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	/** Qué Pokémon enseña el HUD: el seleccionado en el party de Cobblemon (↑/↓) o uno fijo. */
	public enum HudMode { FOLLOW_SELECTION, FIXED }

	/** Cómo se avisa de los EVs ganados (combates, vitaminas, bayas…). */
	public enum GainMode { TOAST, CHAT, NONE }

	// Gson ignora este campo al leer; solo sirve de ayuda a quien edite el archivo
	@SuppressWarnings("unused")
	private String _info = "Todo se cambia también desde el menú (tecla N o Mod Menu). hudCorner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT o BOTTOM_RIGHT; hudOffsetX/hudOffsetY lo separan de esa esquina (en píxeles de la interfaz) y hudScale es su tamaño (0.5 a 2). Lo más cómodo es el botón \"Mover y escalar HUD\" del menú. hudMode: FOLLOW_SELECTION o FIXED. gainMode: TOAST, CHAT o NONE. Los objetivos van en goals.json.";

	// --- HUD ---
	public boolean hudEnabled = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	/** Separación extra desde la esquina, hacia el centro (para esquivar minimapas, FPS, otros HUD). */
	public int hudOffsetX = 0;
	public int hudOffsetY = 0;
	/** Tamaño del HUD (1 = normal). Se cambia con la rueda en "Mover y escalar HUD". */
	public float hudScale = 1.0f;
	public HudMode hudMode = HudMode.FOLLOW_SELECTION;
	/** UUID del Pokémon fijo (modo FIXED); vacío si no hay ninguno. */
	public String fixedPokemon = "";
	/** Si es true, el HUD solo sale cuando el Pokémon tiene objetivo. */
	public boolean onlyWithGoal = false;

	// --- Avisos ---
	public boolean notifyGoalReached = true;
	public boolean notifyOverGoal = true;
	public GainMode gainMode = GainMode.TOAST;
	/** EV yield del salvaje al empezar un combate contra él (apagado por defecto). */
	public boolean showRivalYield = false;

	public static EvConfig load() {
		EvConfig config = new EvConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				EvConfig read = GSON.fromJson(reader, EvConfig.class);
				if (read != null) {
					// El texto de ayuda siempre es el de esta versión, no el que hubiera en el archivo
					read._info = config._info;
					config = read;
				}
			} catch (IOException | JsonParseException e) {
				// No se sobrescribe: así no se pierde lo que se haya editado a mano
				CobbleEvTracker.LOGGER.warn("No se pudo leer {}; se usan los valores por defecto", PATH, e);
				return config.sanitized();
			}
		}
		config = config.sanitized();
		config.save();
		return config;
	}

	/** Corrige enums desconocidos (Gson los deja en null) y textos nulos. */
	private EvConfig sanitized() {
		if (hudCorner == null) hudCorner = HudCorner.TOP_LEFT;
		if (hudMode == null) hudMode = HudMode.FOLLOW_SELECTION;
		if (gainMode == null) gainMode = GainMode.TOAST;
		if (fixedPokemon == null) fixedPokemon = "";
		hudOffsetX = Math.clamp(hudOffsetX, MIN_OFFSET, MAX_OFFSET);
		hudOffsetY = Math.clamp(hudOffsetY, MIN_OFFSET, MAX_OFFSET);
		hudScale = Float.isNaN(hudScale) ? 1.0f : Math.clamp(hudScale, MIN_SCALE, MAX_SCALE);
		return this;
	}

	/** Guarda el archivo (crea los campos nuevos si faltaban). */
	public void save() {
		try {
			Files.createDirectories(DIR);
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			CobbleEvTracker.LOGGER.warn("No se pudo guardar {}", PATH, e);
		}
	}
}
