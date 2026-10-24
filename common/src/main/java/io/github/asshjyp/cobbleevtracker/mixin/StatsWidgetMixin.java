package io.github.asshjyp.cobbleevtracker.mixin;

import com.cobblemon.mod.common.client.gui.pokedex.widgets.StatsWidget;
import io.github.asshjyp.cobbleevtracker.EvPokedex;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dibuja los EVs que da la especie junto a las barras de stats base de la Pokédex. renderBaseStats es de Cobblemon
 * (remap = false); renderWidget es de Minecraft (AbstractWidget) y se remapea. Si no encuentra dónde engancharse,
 * no se aplica (defaultRequire 0).
 */
@Mixin(StatsWidget.class)
public abstract class StatsWidgetMixin {
	@Inject(method = "renderBaseStats", at = @At("TAIL"), remap = false)
	private void cobbleevtracker$drawYield(GuiGraphics graphics, CallbackInfo ci) {
		StatsWidget self = (StatsWidget) (Object) this;
		EvPokedex.renderYield(graphics, self.getPX(), self.getPY());
	}

	@Inject(method = "renderWidget", at = @At("TAIL"))
	private void cobbleevtracker$yieldTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		StatsWidget self = (StatsWidget) (Object) this;
		if (self.getSelectedStatTypeIndex() == StatsWidget.STAT_TYPE_BASE) {
			EvPokedex.renderTooltip(graphics, self.getPX(), self.getPY(), mouseX, mouseY);
		}
	}
}
