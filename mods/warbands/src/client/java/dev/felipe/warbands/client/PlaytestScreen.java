package dev.felipe.warbands.client;

import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.raid.RaidKind;
import dev.felipe.warbands.town.Age;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Tester's Guide panel: every playtest shortcut as a button, grouped (travel, raids, camps by age, you and your
 * town, the world), each with a tooltip saying what it does. Buttons run {@code /warbands} and vanilla commands, so
 * the server still checks cheats; the panel closes so you see what happened.
 */
public class PlaytestScreen extends Screen {
	private static final int MAX_WIDTH = 400;
	private static final int MARGIN = 10;
	private static final int BUTTON_HEIGHT = 16;
	private static final int GAP = 3;
	private static final int TITLE_GAP = 12;

	private static final int BORDER = 0xFF0F3A3A;
	private static final int PANEL = 0xF0E8E4D4;
	private static final int TITLE = 0xFF135250;
	private static final int TEXT = 0xFF3A2A1A;
	private static final int NOTE = 0xFF6B5E4A;

	private record Action(Component label, String command, Component hover) {
	}

	private record Section(Component title, List<Action> actions) {
	}

	private record Label(Component text, int x, int y) {
	}

	private final List<Label> labels = new ArrayList<>();
	private int top;
	private int panelWidth;
	private int columns = 1;
	private int panelHeight;

	public PlaytestScreen() {
		super(Component.translatable("item.warbands.playtest_guide"));
	}

	private static List<Section> sections() {
		List<Section> sections = new ArrayList<>();
		sections.add(new Section(Component.translatable("playtest.warbands.travel"), List.of(
				action("home", "warbands home"),
				action("camp_tp", "warbands camp tp"),
				action("camp_abandon", "warbands camp abandon"))));

		List<Action> raids = new ArrayList<>();
		for (RaidKind kind : RaidKind.values()) {
			raids.add(new Action(kind.displayName(), "warbands raid " + kind.serializedName(),
					Component.translatable("playtest.warbands.raid_hover", kind.displayName())));
		}
		raids.add(action("skip", "warbands raid skip"));
		sections.add(new Section(Component.translatable("playtest.warbands.raids"), raids));

		for (Age age : List.of(Age.VILLAGE, Age.FEUDAL, Age.CASTLE)) {
			List<Action> camps = new ArrayList<>();
			for (CampTier kind : CampTier.kinds(age)) {
				camps.add(new Action(kind.displayName(), "warbands camp " + kind.serializedName(),
						Component.translatable("playtest.warbands.camp_hover", kind.displayName())));
			}
			sections.add(new Section(Component.translatable("playtest.warbands.camps", age.displayName()), camps));
		}

		sections.add(new Section(Component.translatable("playtest.warbands.you"), List.of(
				action("stock", "warbands playtest stock"),
				action("heal", "warbands playtest heal"),
				action("kit", "warbands playtest kit"),
				action("creative", "gamemode creative"),
				action("survival", "gamemode survival"))));
		sections.add(new Section(Component.translatable("playtest.warbands.world"), List.of(
				action("day", "time set day"),
				action("night", "time set midnight"),
				action("clear", "weather clear"),
				action("rain", "weather thunder"))));
		return sections;
	}

	private static Action action(String key, String command) {
		return new Action(Component.translatable("playtest.warbands." + key), command,
				Component.translatable("playtest.warbands." + key + "_hover"));
	}

	private int left() {
		return (this.width - this.panelWidth) / 2;
	}

	@Override
	protected void init() {
		this.labels.clear();
		List<Section> sections = sections();
		// One column when it fits the window; otherwise two, each section going to the shorter column.
		this.columns = 1;
		this.panelWidth = Math.min(this.width - 20, MAX_WIDTH);
		if (measure(sections) > this.height - 8) {
			this.columns = 2;
			this.panelWidth = Math.min(this.width - 20, MAX_WIDTH * 3 / 2);
		}
		this.panelHeight = measure(sections);
		this.top = Math.max(4, (this.height - this.panelHeight) / 2);
		place(sections);
	}

	private int columnWidth() {
		return (this.panelWidth - 2 * MARGIN - (this.columns - 1) * MARGIN) / this.columns;
	}

	/** Assigns sections to columns by height; returns the panel height (title and footer included). */
	private int measure(List<Section> sections) {
		int[] heights = new int[this.columns];
		for (Section section : sections) {
			int shortest = 0;
			for (int c = 1; c < this.columns; c++) {
				if (heights[c] < heights[shortest]) {
					shortest = c;
				}
			}
			heights[shortest] += sectionHeight(section);
		}
		int tallest = 0;
		for (int h : heights) {
			tallest = Math.max(tallest, h);
		}
		return MARGIN + TITLE_GAP + 4 + tallest + TITLE_GAP + 4;
	}

	private int sectionHeight(Section section) {
		int rows = 1;
		int x = 0;
		for (Action action : section.actions()) {
			int w = this.font.width(action.label()) + 10;
			if (x + w > columnWidth() && x > 0) {
				rows++;
				x = 0;
			}
			x += w + GAP;
		}
		return TITLE_GAP + rows * (BUTTON_HEIGHT + GAP) + 3;
	}

	/** Flows each section's buttons into rows under its title, in its column. */
	private void place(List<Section> sections) {
		int[] ys = new int[this.columns];
		Arrays.fill(ys, this.top + MARGIN + TITLE_GAP + 4);
		for (Section section : sections) {
			int c = 0;
			for (int i = 1; i < this.columns; i++) {
				if (ys[i] < ys[c]) {
					c = i;
				}
			}
			int x0 = left() + MARGIN + c * (columnWidth() + MARGIN);
			int right = x0 + columnWidth();
			int y = ys[c];
			this.labels.add(new Label(section.title(), x0, y));
			y += TITLE_GAP;
			int x = x0;
			for (Action action : section.actions()) {
				int w = this.font.width(action.label()) + 10;
				if (x + w > right && x > x0) {
					x = x0;
					y += BUTTON_HEIGHT + GAP;
				}
				this.addRenderableWidget(Button.builder(action.label(), b -> run(action.command()))
						.bounds(x, y, w, BUTTON_HEIGHT).tooltip(Tooltip.create(action.hover())).build());
				x += w + GAP;
			}
			ys[c] = y + BUTTON_HEIGHT + GAP + 3;
		}
	}

	private void run(String command) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.sendCommand(command);
		}
		this.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = left();
		graphics.fill(x, this.top, x + this.panelWidth, this.top + this.panelHeight, BORDER);
		graphics.fill(x + 2, this.top + 2, x + this.panelWidth - 2, this.top + this.panelHeight - 2, PANEL);
		Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
		graphics.text(this.font, title, x + (this.panelWidth - this.font.width(title)) / 2, this.top + MARGIN, TITLE, false);
		for (Label label : this.labels) {
			graphics.text(this.font, label.text(), label.x(), label.y(), TEXT, false);
		}
		Component note = Component.translatable("playtest.warbands.needs_cheats");
		graphics.text(this.font, note, x + (this.panelWidth - this.font.width(note)) / 2, this.top + this.panelHeight - MARGIN - 2, NOTE, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
