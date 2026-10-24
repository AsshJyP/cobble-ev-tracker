package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

/**
 * Avisos: EVs ganados (toast o chat), stat que llega a su objetivo, stat que se pasa,
 * objetivo completo y, si se activa, el EV yield del salvaje al empezar el combate.
 */
public final class EvNotifier implements PartyWatcher.Listener {
	private static final SystemToast.SystemToastId GAIN_TOAST = new SystemToast.SystemToastId(4000);
	private static final SystemToast.SystemToastId GOAL_TOAST = new SystemToast.SystemToastId(6000);
	private static final SystemToast.SystemToastId OVER_TOAST = new SystemToast.SystemToastId(8000);
	private static final SystemToast.SystemToastId RIVAL_TOAST = new SystemToast.SystemToastId(5000);

	/** Último combate ya mirado para el EV yield del rival (para avisar una sola vez por combate). */
	private static UUID rivalBattleId;

	@Override
	public void onEvsChanged(Pokemon pokemon, int[] before, int[] after) {
		Minecraft client = Minecraft.getInstance();
		EvConfig config = CobbleEvTracker.CONFIG;
		Component name = pokemon.getDisplayName(false);

		int[] diff = new int[EvStats.COUNT];
		for (int i = 0; i < EvStats.COUNT; i++) {
			diff[i] = after[i] - before[i];
		}
		Component gained = EvStats.describe(diff);
		if (gained != null) {
			switch (config.gainMode) {
				case TOAST -> SystemToast.add(client.getToasts(), GAIN_TOAST, name, gained);
				case CHAT -> chat(client, Component.translatable("cobbleevtracker.gain.chat",
						name.copy().withStyle(ChatFormatting.GOLD), gained));
				case NONE -> {}
			}
		}

		int[] goal = EvGoals.get(pokemon.getUuid());
		if (goal == null) {
			return;
		}
		for (int i = 0; i < EvStats.COUNT; i++) {
			// Pasado: antes estaba dentro del objetivo y ahora lo supera
			if (after[i] > goal[i] && before[i] <= goal[i]) {
				if (config.notifyOverGoal) {
					SystemToast.add(client.getToasts(), OVER_TOAST,
							Component.translatable("cobbleevtracker.over.toast.title", EvStats.fullName(i)),
							Component.translatable("cobbleevtracker.over.toast.message", name, after[i], goal[i]));
					chat(client, Component.translatable("cobbleevtracker.over.chat",
							name.copy().withStyle(ChatFormatting.GOLD), after[i], EvStats.fullName(i), goal[i])
							.withStyle(ChatFormatting.RED));
					play(client, SoundEvents.NOTE_BLOCK_BASS.value());
				}
			} else if (goal[i] > 0 && after[i] == goal[i] && before[i] < goal[i] && config.notifyGoalReached) {
				SystemToast.add(client.getToasts(), GOAL_TOAST,
						Component.translatable("cobbleevtracker.goal.toast.title", EvStats.fullName(i)),
						Component.translatable("cobbleevtracker.goal.toast.message", name, after[i], goal[i]));
				play(client, SoundEvents.EXPERIENCE_ORB_PICKUP);
			}
		}
		if (config.notifyGoalReached && matches(after, goal) && !matches(before, goal)) {
			SystemToast.add(client.getToasts(), GOAL_TOAST,
					Component.translatable("cobbleevtracker.complete.toast.title"),
					Component.translatable("cobbleevtracker.complete.toast.message", name));
			play(client, SoundEvents.PLAYER_LEVELUP);
		}
	}

	/** Todas las stats justo en su objetivo. */
	private static boolean matches(int[] evs, int[] goal) {
		for (int i = 0; i < EvStats.COUNT; i++) {
			if (evs[i] != goal[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * D. EV yield del rival: al empezar un combate propio contra un salvaje, un toast con lo que da
	 * (dato público de especie, el mismo del PokePad y Cobblenav). Solo del combate en el que ya estás.
	 */
	static void tickRival(Minecraft client) {
		ClientBattle battle = client.level == null ? null : CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			rivalBattleId = null;
			return;
		}
		UUID battleId = battle.getBattleId();
		if (battleId.equals(rivalBattleId)) {
			return;
		}
		ClientBattleActor wild = battle.getWildActor();
		if (!CobbleEvTracker.CONFIG.showRivalYield || battle.getSpectating() || wild == null) {
			rivalBattleId = battleId;
			return;
		}
		for (ActiveClientBattlePokemon active : wild.getActivePokemon()) {
			ClientBattlePokemon pokemon = active.getBattlePokemon();
			if (pokemon == null) {
				continue;
			}
			Species species = pokemon.getSpecies();
			FormData form = species.getForm(pokemon.getProperties().getAspects());
			Component yield = EvStats.describe(EvStats.read(form.getEvYield()));
			SystemToast.add(client.getToasts(), RIVAL_TOAST,
					Component.translatable("cobbleevtracker.rival.toast.title", species.getTranslatedName()),
					yield != null ? yield : Component.translatable("cobbleevtracker.rival.toast.none"));
			rivalBattleId = battleId;
			return;
		}
		// Aún sin Pokémon activo: se vuelve a mirar el próximo tick
	}

	private static void chat(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("[EV] ").withStyle(ChatFormatting.DARK_AQUA)
					.append(message), false);
		}
	}

	private static void play(Minecraft client, SoundEvent sound) {
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
	}
}
