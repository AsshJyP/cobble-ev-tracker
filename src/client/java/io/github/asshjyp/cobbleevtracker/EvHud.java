package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.client.storage.ClientStorageManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Panel de entrenamiento: nombre del Pokémon, una barra por stat (valor / objetivo) y el total sobre 510.
 * Colores: gris = 0, azul = en progreso, verde = objetivo cumplido, rojo = pasado del objetivo.
 */
public final class EvHud {
	private static final int MARGIN = 4;
	private static final int PADDING = 4;
	private static final int ROW_GAP = 1;
	private static final int COLUMN_GAP = 4;
	private static final int BAR_WIDTH = 50;
	private static final int BAR_HEIGHT = 5;
	private static final int BACKGROUND = 0x80000000;
	private static final int BAR_BACKGROUND = 0xFF303030;

	public static final int GRAY = 0xFF8A8A8A;
	public static final int BLUE = 0xFF4A90E2;
	public static final int GREEN = 0xFF55CC55;
	public static final int RED = 0xFFE05555;

	private EvHud() {}

	static void init() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	/**
	 * Pokémon que se entrena: el fijo (si el modo es "Fijo" y sigue en el party) o el seleccionado
	 * con ↑/↓ en el party de Cobblemon. Null si no hay ninguno.
	 */
	public static Pokemon target() {
		ClientStorageManager storage = CobblemonClient.INSTANCE.getStorage();
		ClientParty party = storage.getParty();
		EvConfig config = CobbleEvTracker.CONFIG;
		if (config.hudMode == EvConfig.HudMode.FIXED) {
			UUID fixed = parseUuid(config.fixedPokemon);
			return fixed == null ? null : party.findByUUID(fixed);
		}
		List<Pokemon> slots = party.getSlots();
		int selected = storage.getSelectedSlot();
		if (selected >= 0 && selected < slots.size() && slots.get(selected) != null) {
			return slots.get(selected);
		}
		for (Pokemon pokemon : party) {
			if (pokemon != null) {
				return pokemon;
			}
		}
		return null;
	}

	static UUID parseUuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Color de la barra de una stat según su valor y su objetivo (sin objetivo, el tope es 252). */
	public static int color(int value, int goal, boolean hasGoal) {
		int target = hasGoal ? goal : EvStats.MAX_STAT;
		if (hasGoal && value > goal) return RED;
		if (value >= target && target > 0) return GREEN;
		if (value == 0) return GRAY;
		return BLUE;
	}


	/** Lo que enseña el HUD: nombre, EVs actuales y objetivo (null si no tiene). */
	public record Content(Component name, int[] evs, int[] goal) {}

	public static Content content(Pokemon pokemon) {
		return new Content(pokemon.getDisplayName(false), EvStats.read(pokemon.getEvs()), EvGoals.get(pokemon.getUuid()));
	}

	/** Ejemplo para la pantalla de mover y escalar cuando no hay Pokémon en el equipo. */
	public static Content sample() {
		return new Content(Component.translatable("cobbleevtracker.hud.sample"),
				new int[]{4, 252, 0, 0, 0, 180}, EvGoals.Template.PHYSICAL_SWEEPER.evs());
	}

	/** Ancho y alto del recuadro sin escalar, en píxeles de la interfaz. */
	public static int[] size(Font font, Content content) {
		int contentWidth = Math.max(rowWidth(font), Math.max(font.width(content.name()), font.width(totalLine(content))));
		int rowHeight = font.lineHeight + ROW_GAP;
		// Nombre + 6 stats + total
		return new int[]{contentWidth + PADDING * 2, rowHeight * (EvStats.COUNT + 2) - ROW_GAP + PADDING * 2};
	}

	/**
	 * Esquina superior izquierda del recuadro ya escalado (ancho w, alto h): se ancla a la esquina
	 * elegida, se separa con el desplazamiento y nunca se sale de la pantalla.
	 */
	public static int[] position(EvConfig config, int screenWidth, int screenHeight, int w, int h) {
		int x = switch (config.hudCorner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN + config.hudOffsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - w - MARGIN - config.hudOffsetX;
		};
		int y = switch (config.hudCorner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN + config.hudOffsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - h - MARGIN - config.hudOffsetY;
		};
		return new int[]{Math.clamp(x, 0, Math.max(0, screenWidth - w)), Math.clamp(y, 0, Math.max(0, screenHeight - h))};
	}

	/**
	 * Al contrario que {@link #position}: guarda en el config la esquina más cercana al centro del recuadro
	 * y la separación hasta ella. Así el HUD sigue pegado a esa esquina si cambia la resolución.
	 */
	public static void anchorAt(EvConfig config, int screenWidth, int screenHeight, int x, int y, int w, int h) {
		boolean left = x + w / 2 < screenWidth / 2;
		boolean top = y + h / 2 < screenHeight / 2;
		config.hudCorner = top
				? (left ? EvConfig.HudCorner.TOP_LEFT : EvConfig.HudCorner.TOP_RIGHT)
				: (left ? EvConfig.HudCorner.BOTTOM_LEFT : EvConfig.HudCorner.BOTTOM_RIGHT);
		config.hudOffsetX = (left ? x : screenWidth - w - x) - MARGIN;
		config.hudOffsetY = (top ? y : screenHeight - h - y) - MARGIN;
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		EvConfig config = CobbleEvTracker.CONFIG;
		// F1 (hideGui), F3 y cualquier pantalla abierta lo ocultan, salvo "Mover y escalar HUD" de otro mod
		if (!config.hudEnabled || client.level == null
				|| (client.screen != null && !isOtherHudEditor(client.screen))
				|| client.options.hideGui || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// En combate, la interfaz de Cobblemon ocupa las cuatro esquinas
		if (CobblemonClient.INSTANCE.getBattle() != null) {
			return;
		}
		Pokemon pokemon = target();
		if (pokemon == null) {
			return;
		}
		Content content = content(pokemon);
		if (config.onlyWithGoal && content.goal() == null) {
			return;
		}
		int[] size = size(client.font, content);
		int w = scaled(size[0], config.hudScale);
		int h = scaled(size[1], config.hudScale);
		int[] pos = position(config, graphics.guiWidth(), graphics.guiHeight(), w, h);
		draw(graphics, client.font, content, pos[0], pos[1], config.hudScale);
	}

	/**
	 * Pantalla "Mover y escalar HUD" de otro mod de AsshJyP (Cobble Hunt Log…): con ella abierta este HUD
	 * se sigue viendo, para colocar los dos juntos. Convención compartida, sin depender del otro mod:
	 * clase llamada {@code HudEditScreen} en un paquete {@code io.github.asshjyp.*}. La propia no cuenta,
	 * porque ya dibuja su vista previa.
	 */
	static boolean isOtherHudEditor(Screen screen) {
		Class<?> type = screen.getClass();
		return !(screen instanceof HudEditScreen)
				&& type.getSimpleName().equals("HudEditScreen")
				&& type.getName().startsWith("io.github.asshjyp.");
	}

	public static int scaled(int length, float scale) {
		return Math.round(length * scale);
	}

	/** Dibuja el recuadro con su esquina superior izquierda en (x, y) y el tamaño multiplicado por scale. */
	public static void draw(GuiGraphics graphics, Font font, Content content, int x, int y, float scale) {
		int[] evs = content.evs();
		int[] goal = content.goal();
		int[] size = size(font, content);
		int labelWidth = labelWidth(font);
		int rowWidth = rowWidth(font);
		int rowHeight = font.lineHeight + ROW_GAP;

		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.fill(0, 0, size[0], size[1], BACKGROUND);
		int left = PADDING;
		int lineY = PADDING;
		graphics.drawString(font, content.name().copy().withStyle(ChatFormatting.GOLD), left, lineY, 0xFFFFFF);
		lineY += rowHeight;
		for (int i = 0; i < EvStats.COUNT; i++) {
			int target = goal != null ? goal[i] : EvStats.MAX_STAT;
			int color = color(evs[i], target, goal != null);
			graphics.drawString(font, EvStats.shortName(i), left, lineY, 0xDDDDDD);
			int barX = left + labelWidth + COLUMN_GAP;
			int barY = lineY + (font.lineHeight - 1 - BAR_HEIGHT) / 2;
			graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BACKGROUND);
			// Objetivo 0 con EVs = pasado: barra llena en rojo
			int filled = target == 0
					? (evs[i] > 0 ? BAR_WIDTH : 0)
					: Math.min(BAR_WIDTH, evs[i] * BAR_WIDTH / target);
			if (filled > 0) {
				graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, color);
			}
			String value = evs[i] + "/" + target;
			int valueColor = color == GRAY ? GRAY : color == BLUE ? 0xFFFFFF : color;
			graphics.drawString(font, value, left + rowWidth - font.width(value), lineY, valueColor);
			lineY += rowHeight;
		}
		graphics.drawString(font, totalLine(content), left, lineY, 0xFFFFFF);
		graphics.pose().popPose();
	}

	private static Component totalLine(Content content) {
		int total = EvStats.sum(content.evs());
		return Component.translatable("cobbleevtracker.hud.total", total, EvStats.MAX_TOTAL)
				.withStyle(total >= EvStats.MAX_TOTAL ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	private static int labelWidth(Font font) {
		int width = 0;
		for (int i = 0; i < EvStats.COUNT; i++) {
			width = Math.max(width, font.width(EvStats.shortName(i)));
		}
		return width;
	}

	private static int rowWidth(Font font) {
		return labelWidth(font) + COLUMN_GAP + BAR_WIDTH + COLUMN_GAP + font.width("252/252");
	}
}
