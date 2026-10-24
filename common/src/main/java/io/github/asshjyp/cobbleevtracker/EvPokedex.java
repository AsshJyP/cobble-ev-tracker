package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.pokemon.FormData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * C. EVs que da cada especie, en la pestaña de stats de la Pokédex de Cobblemon: un "+2" verde a la derecha de la
 * barra de cada stat que da EVs y, al pasar el ratón por esa columna, "EVs que da al derrotarlo: +2 Ataque".
 * Es información pública de la especie (la de cualquier Pokédex o wiki), la misma que ya usa "EVs del rival".
 * <p>
 * Los mixins de PokedexGUI y StatsWidget solo llaman aquí. Medidas de StatsWidget (iguales en Cobblemon 1.7.3 y 1.8.2,
 * comprobado con javap): widget de 139 × 42, fila i en pY + 4 + 6·i, barras de pX + 55 a pX + 130, texto a escala 0,5.
 */
public final class EvPokedex {
	private static final int FIRST_ROW_Y = 4;
	private static final int ROW_HEIGHT = 6;
	private static final int YIELD_X = 132;
	private static final int WIDGET_WIDTH = 139;
	private static final float TEXT_SCALE = 0.5f;
	private static final int GREEN = 0xFF2E9E46;

	/** EVs que da la forma que enseña ahora la Pokédex (orden de EvStats), o null si aún no hay ninguna. */
	private static int[] yield;

	private EvPokedex() {}

	/** La Pokédex va a enseñar las stats base de esta forma (PokedexGUI.updateTabInfoElement). */
	public static void onFormShown(FormData form) {
		yield = form == null ? null : EvStats.read(form.getEvYield());
	}

	private static boolean active() {
		return CobbleEvTracker.CONFIG.showPokedexYield && yield != null && EvStats.sum(yield) > 0;
	}

	/** Después de dibujar las barras de stats base (StatsWidget.renderBaseStats). */
	public static void renderYield(GuiGraphics graphics, int pX, int pY) {
		if (!active()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < EvStats.COUNT; i++) {
			if (yield[i] <= 0) {
				continue;
			}
			graphics.pose().pushPose();
			graphics.pose().translate(pX + YIELD_X, pY + FIRST_ROW_Y + ROW_HEIGHT * i, 0);
			graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
			graphics.drawString(font, "+" + yield[i], 0, 0, GREEN, false);
			graphics.pose().popPose();
		}
	}

	/** Tooltip al pasar el ratón por la columna de "+N" (StatsWidget.renderWidget, solo en la vista de stats base). */
	public static void renderTooltip(GuiGraphics graphics, int pX, int pY, int mouseX, int mouseY) {
		if (!active()) {
			return;
		}
		boolean over = mouseX >= pX + YIELD_X - 1 && mouseX < pX + WIDGET_WIDTH
				&& mouseY >= pY + FIRST_ROW_Y && mouseY < pY + FIRST_ROW_Y + ROW_HEIGHT * EvStats.COUNT;
		if (over) {
			graphics.renderTooltip(Minecraft.getInstance().font,
					Component.translatable("cobbleevtracker.pokedex.yield", EvStats.describe(yield)), mouseX, mouseY);
		}
	}
}
