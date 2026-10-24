package io.github.asshjyp.cobbleevtracker.mixin;

import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.asshjyp.cobbleevtracker.EvPokedex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Al cambiar de especie o forma, la Pokédex llama a form.getBaseStats() para el widget de stats; aquí se guarda
 * esa misma forma para enseñar sus EVs. Mismo código en Cobblemon 1.7.3 y 1.8.2 (javap). Si cambia, no se aplica
 * (defaultRequire 0) y la Pokédex sigue como siempre.
 */
@Mixin(value = PokedexGUI.class, remap = false)
public abstract class PokedexGUIMixin {
	@WrapOperation(method = "updateTabInfoElement",
			at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/FormData;getBaseStats()Ljava/util/Map;"))
	private Map<?, ?> cobbleevtracker$rememberForm(FormData form, Operation<Map<?, ?>> original) {
		EvPokedex.onFormShown(form);
		return original.call(form);
	}
}
