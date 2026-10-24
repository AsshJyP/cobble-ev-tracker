package io.github.asshjyp.cobbleevtracker;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.asshjyp.cobbleevtracker.EvConfig.GainMode;
import io.github.asshjyp.cobbleevtracker.EvConfig.GainTiming;
import io.github.asshjyp.cobbleevtracker.EvConfig.HudMode;
import io.github.asshjyp.cobbleevtracker.EvGoals.Template;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Menú de opciones con widgets vanilla (sin Cloth Config). Se abre con la tecla N o desde Mod Menu.
 * Los cambios se aplican al momento; config.json y goals.json se guardan al cerrar la pantalla.
 */
public class EvConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 10;
	/** Separación entre filas y alto de los títulos de sección; más apretados en pantallas bajas (ver init). */
	private int row = 24;
	private int section = 16;
	private static final int GOAL_COLUMNS = 3;
	private static final int GOAL_COLUMN_GAP = 8;
	private static final int TEXT_ERROR = 0xFF5555;
	private static final int TEXT_NORMAL = 0xE0E0E0;

	private final Screen parent;
	/** Pokémon cuyo objetivo se edita (el mismo que enseña el HUD); null si el party está vacío. */
	private Pokemon target;
	private final EditBox[] goalBoxes = new EditBox[EvStats.COUNT];
	/** Lo escrito en las cajas; solo pasa a EvGoals si es válido (cada stat ≤ 252 y total ≤ 510). */
	private final int[] draft = new int[EvStats.COUNT];
	/** Una opción del botón de plantillas: la recomendada para el Pokémon actual o una fija. */
	private record Preset(Component label, int[] evs, Tooltip tooltip) {}

	/**
	 * Posición en la lista de plantillas (0 = recomendada si hay Pokémon). Se mantiene al reconstruir
	 * si el Pokémon es el mismo; con otro Pokémon vuelve a la ★, que es la suya.
	 */
	private int selectedPreset;
	/** Pokémon para el que se eligió {@link #selectedPreset}. */
	private UUID presetTarget;
	/** Cambiar el modo o el Pokémon fijo cambia el objetivo que se edita: se reconstruye en el próximo tick. */
	private boolean needsRebuild;
	// Posiciones calculadas en init() para lo que se dibuja en render()
	private int hudTitleY;
	private int goalTitleY;
	private int alertsTitleY;
	private final int[] labelX = new int[EvStats.COUNT];
	private final int[] labelY = new int[EvStats.COUNT];

	public EvConfigScreen(Screen parent) {
		super(Component.literal("Cobble EV Tracker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		EvConfig config = CobbleEvTracker.CONFIG;
		target = EvHud.target();
		int left = width / 2 - BUTTON_WIDTH - GAP / 2;
		int right = width / 2 + GAP / 2;
		int fullWidth = BUTTON_WIDTH * 2 + GAP;
		// 3 títulos de sección, 8 filas, separaciones y el botón "Listo"
		// 3 títulos de sección y 9 filas; si no cabe con el espaciado normal, se aprieta (los botones siguen midiendo 20)
		row = 24;
		section = 16;
		if (section * 3 + row * 9 + 6 * 2 + 8 + BUTTON_HEIGHT + 28 > height) {
			row = 21;
			section = 12;
		}
		int contentHeight = section * 3 + row * 9 + 6 * 2 + 8 + BUTTON_HEIGHT;
		int y = Math.max(28, (height - contentHeight) / 2 + 8);

		// --- HUD ---
		hudTitleY = y;
		y += section;
		addRenderableWidget(CycleButton.onOffBuilder(config.hudEnabled)
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.hud"),
						(button, value) -> config.hudEnabled = value));
		addRenderableWidget(Button.builder(Component.translatable("cobbleevtracker.config.edit_hud"),
						button -> minecraft.setScreen(new HudEditScreen(this)))
				.bounds(right, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("cobbleevtracker.config.edit_hud.tooltip")))
				.build());
		y += row;
		addRenderableWidget(CycleButton.<HudMode>builder(mode -> Component.translatable(
						"cobbleevtracker.config.hud_mode." + mode.name().toLowerCase(Locale.ROOT)))
				.withValues(HudMode.values())
				.withInitialValue(config.hudMode)
				.withTooltip(mode -> Tooltip.create(Component.translatable(
						"cobbleevtracker.config.hud_mode." + mode.name().toLowerCase(Locale.ROOT) + ".tooltip")))
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.hud_mode"),
						(button, value) -> {
							config.hudMode = value;
							// Al pasar a "Fijo" sin Pokémon fijo válido, se fija el que se estaba enseñando
							if (value == HudMode.FIXED && partyPokemon(config.fixedPokemon) == null && target != null) {
								config.fixedPokemon = target.getUuid().toString();
							}
							needsRebuild = true;
						}));
		addFixedPokemonButton(right, y);
		y += row;
		addRenderableWidget(CycleButton.onOffBuilder(config.onlyWithGoal)
				.withTooltip(value -> Tooltip.create(Component.translatable("cobbleevtracker.config.only_with_goal.tooltip")))
				.create(left, y, fullWidth, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.only_with_goal"),
						(button, value) -> config.onlyWithGoal = value));
		y += row + 6;

		// --- Objetivo del Pokémon actual ---
		goalTitleY = y;
		y += section;
		int[] goal = target == null ? null : EvGoals.get(target.getUuid());
		int columnWidth = (fullWidth - GOAL_COLUMN_GAP * (GOAL_COLUMNS - 1)) / GOAL_COLUMNS;
		int labelWidth = 0;
		for (int i = 0; i < EvStats.COUNT; i++) {
			labelWidth = Math.max(labelWidth, font.width(EvStats.shortName(i)));
		}
		for (int i = 0; i < EvStats.COUNT; i++) {
			int column = i % GOAL_COLUMNS;
			int rowY = y + (i / GOAL_COLUMNS) * row;
			int columnX = left + column * (columnWidth + GOAL_COLUMN_GAP);
			labelX[i] = columnX;
			labelY[i] = rowY + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
			int boxX = columnX + labelWidth + 6;
			EditBox box = new EditBox(font, boxX, rowY, columnX + columnWidth - boxX, BUTTON_HEIGHT, EvStats.fullName(i));
			box.setMaxLength(3);
			box.setFilter(text -> text.matches("\\d*"));
			draft[i] = goal == null ? 0 : goal[i];
			box.setValue(String.valueOf(draft[i]));
			box.setTooltip(Tooltip.create(EvStats.fullName(i)));
			box.active = target != null;
			box.setEditable(target != null);
			int index = i;
			box.setResponder(text -> {
				draft[index] = text.isEmpty() ? 0 : Integer.parseInt(text);
				commitDraft();
			});
			goalBoxes[i] = addRenderableWidget(box);
		}
		y += row * 2;
		List<Preset> presets = presets();
		UUID targetId = target == null ? null : target.getUuid();
		if (!Objects.equals(targetId, presetTarget)) {
			selectedPreset = 0;
			presetTarget = targetId;
		}
		selectedPreset = Math.clamp(selectedPreset, 0, presets.size() - 1);
		addRenderableWidget(CycleButton.<Preset>builder(Preset::label)
				.withValues(presets)
				.withInitialValue(presets.get(selectedPreset))
				.withTooltip(Preset::tooltip)
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.template"),
						(button, value) -> selectedPreset = presets.indexOf(value))).active = target != null;
		int halfWidth = (BUTTON_WIDTH - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("cobbleevtracker.config.apply"),
						button -> fillBoxes(presets.get(selectedPreset).evs()))
				.bounds(right, y, halfWidth, BUTTON_HEIGHT)
				.build()).active = target != null;
		addRenderableWidget(Button.builder(Component.translatable("cobbleevtracker.config.clear"),
						button -> fillBoxes(new int[EvStats.COUNT]))
				.bounds(right + BUTTON_WIDTH - halfWidth, y, halfWidth, BUTTON_HEIGHT)
				.build()).active = target != null;
		y += row + 6;

		// --- Avisos ---
		alertsTitleY = y;
		y += section;
		addRenderableWidget(CycleButton.onOffBuilder(config.notifyGoalReached)
				.withTooltip(value -> Tooltip.create(Component.translatable("cobbleevtracker.config.notify_goal.tooltip")))
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.notify_goal"),
						(button, value) -> config.notifyGoalReached = value));
		addRenderableWidget(CycleButton.onOffBuilder(config.notifyOverGoal)
				.withTooltip(value -> Tooltip.create(Component.translatable("cobbleevtracker.config.notify_over.tooltip")))
				.create(right, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.notify_over"),
						(button, value) -> config.notifyOverGoal = value));
		y += row;
		addRenderableWidget(CycleButton.<GainTiming>builder(timing -> Component.translatable(
						"cobbleevtracker.config.gain_timing." + timing.name().toLowerCase(Locale.ROOT)))
				.withValues(GainTiming.values())
				.withInitialValue(config.gainTiming)
				.withTooltip(timing -> Tooltip.create(Component.translatable(
						"cobbleevtracker.config.gain_timing." + timing.name().toLowerCase(Locale.ROOT) + ".tooltip")))
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.gain_timing"),
						(button, value) -> config.gainTiming = value));
		// NONE no se ofrece: para no avisar está "Ganancias: Apagado"
		addRenderableWidget(CycleButton.<GainMode>builder(mode -> Component.translatable(
						"cobbleevtracker.config.gain_mode." + mode.name().toLowerCase(Locale.ROOT)))
				.withValues(GainMode.TOAST, GainMode.CHAT)
				.withInitialValue(config.gainMode == GainMode.CHAT ? GainMode.CHAT : GainMode.TOAST)
				.withTooltip(mode -> Tooltip.create(Component.translatable("cobbleevtracker.config.gain_mode.tooltip")))
				.create(right, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.gain_mode"),
						(button, value) -> config.gainMode = value));
		y += row;
		addRenderableWidget(CycleButton.onOffBuilder(config.showRivalYield)
				.withTooltip(value -> Tooltip.create(Component.translatable("cobbleevtracker.config.rival_yield.tooltip")))
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobbleevtracker.config.rival_yield"),
						(button, value) -> config.showRivalYield = value));
		y += row + 8;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, y, 200, BUTTON_HEIGHT)
				.build());
		updateBoxColors();
	}

	/** Botón que recorre los Pokémon del party para el modo "Fijo". Sin party, queda desactivado. */
	private void addFixedPokemonButton(int x, int y) {
		EvConfig config = CobbleEvTracker.CONFIG;
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
			if (pokemon != null) {
				party.add(pokemon);
			}
		}
		Component caption = Component.translatable("cobbleevtracker.config.fixed_pokemon");
		if (party.isEmpty()) {
			addRenderableWidget(Button.builder(Component.translatable("cobbleevtracker.config.fixed_pokemon.none")
							.withStyle(ChatFormatting.GRAY), button -> {})
					.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build()).active = false;
			return;
		}
		Pokemon initial = partyPokemon(config.fixedPokemon);
		CycleButton<Pokemon> button = addRenderableWidget(CycleButton.<Pokemon>builder(pokemon -> pokemon.getDisplayName(false))
				.withValues(party)
				.withInitialValue(initial != null ? initial : party.getFirst())
				.withTooltip(pokemon -> Tooltip.create(Component.translatable("cobbleevtracker.config.fixed_pokemon.tooltip")))
				.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, caption, (b, value) -> {
					config.fixedPokemon = value.getUuid().toString();
					needsRebuild = true;
				}));
		// Solo tiene sentido en modo "Fijo"
		button.active = config.hudMode == HudMode.FIXED;
	}

	/**
	 * Plantillas del botón: primero la recomendada para el Pokémon actual (según sus stats base),
	 * después las 4 fijas.
	 */
	private List<Preset> presets() {
		List<Preset> presets = new ArrayList<>();
		if (target != null) {
			EvRecommender.Recommendation rec = EvRecommender.recommend(target);
			int[] b = rec.base();
			MutableComponent tooltip = Component.translatable("cobbleevtracker.config.template.recommended.tooltip",
					rec.basedOn().getTranslatedName(), rec.reason(),
					b[0] + "/" + b[1] + "/" + b[2] + "/" + b[3] + "/" + b[4] + "/" + b[5]);
			if (rec.branches()) {
				tooltip.append("\n").append(Component.translatable("cobbleevtracker.config.template.recommended.branches"));
			} else if (rec.basedOn() != target.getSpecies()) {
				tooltip.append("\n").append(Component.translatable("cobbleevtracker.config.template.recommended.evolution",
						target.getSpecies().getTranslatedName(), rec.basedOn().getTranslatedName()));
			}
			tooltip.append("\n").append(Component.translatable("cobbleevtracker.config.template.recommended.disclaimer"));
			presets.add(new Preset(
					Component.translatable("cobbleevtracker.config.template.recommended", EvStats.compact(rec.evs())),
					rec.evs(), Tooltip.create(tooltip)));
		}
		for (Template template : Template.values()) {
			presets.add(new Preset(EvStats.compact(template.evs()), template.evs(), null));
		}
		return presets;
	}

	/** Pokémon del party con ese UUID (texto), o null. */
	private static Pokemon partyPokemon(String uuid) {
		UUID id = EvHud.parseUuid(uuid);
		return id == null ? null : CobblemonClient.INSTANCE.getStorage().getParty().findByUUID(id);
	}

	private void fillBoxes(int[] values) {
		// setValue llama al responder de cada caja, que actualiza draft y guarda si es válido
		for (int i = 0; i < EvStats.COUNT; i++) {
			goalBoxes[i].setValue(String.valueOf(values[i]));
		}
	}

	/** Pasa lo escrito a EvGoals si es válido; si no, deja el objetivo anterior y marca en rojo lo que falla. */
	private void commitDraft() {
		if (target != null && EvGoals.isValid(draft)) {
			EvGoals.set(target.getUuid(), draft);
		}
		updateBoxColors();
	}

	private void updateBoxColors() {
		boolean totalOver = EvStats.sum(draft) > EvStats.MAX_TOTAL;
		for (int i = 0; i < EvStats.COUNT; i++) {
			goalBoxes[i].setTextColor(draft[i] > EvStats.MAX_STAT || totalOver ? TEXT_ERROR : TEXT_NORMAL);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (needsRebuild) {
			needsRebuild = false;
			rebuildWidgets();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - BUTTON_WIDTH - GAP / 2;
		int rightEdge = width / 2 + GAP / 2 + BUTTON_WIDTH;
		graphics.drawCenteredString(font, title, width / 2, Math.max(12, hudTitleY - 20), 0xFFFFFF);
		graphics.drawString(font, Component.translatable("cobbleevtracker.config.section.hud")
				.withStyle(ChatFormatting.YELLOW), left, hudTitleY + 2, 0xFFFFFF);
		Component goalTitle = target == null
				? Component.translatable("cobbleevtracker.config.section.goal.none")
				: Component.translatable("cobbleevtracker.config.section.goal", target.getDisplayName(false));
		graphics.drawString(font, goalTitle.copy().withStyle(ChatFormatting.YELLOW), left, goalTitleY + 2, 0xFFFFFF);
		int total = EvStats.sum(draft);
		Component totalText = Component.translatable("cobbleevtracker.config.goal_total", total, EvStats.MAX_TOTAL)
				.withStyle(total > EvStats.MAX_TOTAL ? ChatFormatting.RED : ChatFormatting.GRAY);
		graphics.drawString(font, totalText, rightEdge - font.width(totalText), goalTitleY + 2, 0xFFFFFF);
		for (int i = 0; i < EvStats.COUNT; i++) {
			graphics.drawString(font, EvStats.shortName(i), labelX[i], labelY[i], 0xFFFFFF);
		}
		graphics.drawString(font, Component.translatable("cobbleevtracker.config.section.alerts")
				.withStyle(ChatFormatting.YELLOW), left, alertsTitleY + 2, 0xFFFFFF);
	}

	@Override
	public void removed() {
		// Se llama al cerrar con Esc, con "Listo" o si otra pantalla la reemplaza
		CobbleEvTracker.CONFIG.save();
		EvGoals.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
