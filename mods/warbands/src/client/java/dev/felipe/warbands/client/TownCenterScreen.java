package dev.felipe.warbands.client;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Repairs;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownCenterMenu;
import dev.felipe.warbands.town.TrainingSite;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TownCenterScreen extends AbstractContainerScreen<TownCenterMenu> {
	private static final Identifier TEXTURE = Warbands.id("textures/gui/town_center.png");
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int RESOURCE_X = 8;
	private static final int RESOURCE_Y = 17;
	private static final int RESOURCE_SPACING = 42;
	private static final int BUTTON_X = 48;
	private static final int BUTTON_Y = 40;
	private static final int BUTTON_WIDTH = 58;
	private static final int BUTTON_SPACING = 62;
	private static final int QUEUE_X = 8;
	private static final int QUEUE_Y = 78;
	private static final int QUEUE_SPACING = 20;
	private static final int BAR_WIDTH = (TownCenterBlockEntity.MAX_QUEUE - 1) * QUEUE_SPACING + 18;
	private static final int ADVANCE_X = 8;
	private static final int ADVANCE_Y = 100;
	/** The age bar under the Advance button spans the whole row; the button leaves room for the icon buttons. */
	private static final int ADVANCE_WIDTH = 160;
	private static final int ADVANCE_BUTTON_WIDTH = 118;
	private static final int ICON_BUTTON_SPACING = 21;
	private static final int ICON_BUTTON_X = ADVANCE_X + ADVANCE_BUTTON_WIDTH + 3;
	private static final int ICON_BUTTON_SIZE = 18;

	private final Map<UnitType, Button> trainButtons = new EnumMap<>(UnitType.class);
	private Button advanceButton;
	private Button repairButton;
	private Button warMapButton;

	public TownCenterScreen(TownCenterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 220);
		this.inventoryLabelY = TownCenterMenu.INVENTORY_Y - 12;
	}

	@Override
	protected void init() {
		super.init();
		trainButtons.clear();
		List<UnitType> units = UnitType.trainedAt(TrainingSite.TOWN_CENTER);
		for (int i = 0; i < units.size(); i++) {
			UnitType unit = units.get(i);
			Button button = Button.builder(unit.displayName(), b -> clickButton(unit.ordinal()))
					.bounds(this.leftPos + BUTTON_X + i * BUTTON_SPACING, this.topPos + BUTTON_Y, BUTTON_WIDTH, 20)
					.tooltip(Tooltip.create(trainTooltip(unit)))
					.build();
			trainButtons.put(unit, this.addRenderableWidget(button));
		}
		this.advanceButton = Button.builder(Component.translatable("gui.warbands.advance", Component.empty()), b -> clickButton(TownCenterMenu.ADVANCE_BUTTON))
				.bounds(this.leftPos + ADVANCE_X, this.topPos + ADVANCE_Y, ADVANCE_BUTTON_WIDTH, 18)
				.build();
		this.addRenderableWidget(this.advanceButton);
		this.repairButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> clickButton(TownCenterMenu.REPAIR_BUTTON))
				.bounds(this.leftPos + ICON_BUTTON_X, this.topPos + ADVANCE_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
				.build());
		this.warMapButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> clickButton(this.menu.campActive() && Minecraft.getInstance().hasShiftDown()
				? TownCenterMenu.ABANDON_CAMP_BUTTON : TownCenterMenu.WAR_MAP_BUTTON))
				.bounds(this.leftPos + ICON_BUTTON_X + ICON_BUTTON_SPACING, this.topPos + ADVANCE_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
				.build());
		updateButtons();
	}

	private void clickButton(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private static Component trainTooltip(UnitType unit) {
		MutableComponent text = Component.translatable("gui.warbands.train", unit.displayName()).withStyle(ChatFormatting.WHITE);
		for (Resource resource : Resource.values()) {
			if (unit.cost(resource) > 0) {
				text.append("\n").append(Component.literal(unit.cost(resource) + " ").append(resource.displayName())
						.withStyle(ChatFormatting.GRAY));
			}
		}
		text.append("\n").append(Component.translatable("gui.warbands.train_time", unit.trainTicks() / 20)
				.withStyle(ChatFormatting.DARK_GRAY));
		return text;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		trainButtons.forEach((unit, button) -> {
			button.active = this.menu.canQueue(unit);
			Component reason = blockedReason(unit);
			button.setTooltip(Tooltip.create(reason == null ? trainTooltip(unit)
					: trainTooltip(unit).copy().append("\n").append(reason.copy().withStyle(ChatFormatting.RED))));
		});
		Age next = this.menu.age().next();
		if (this.menu.researching() && next != null) {
			this.advanceButton.setMessage(Component.translatable("gui.warbands.advancing", Math.round(this.menu.researchProgress() * 100)));
			this.advanceButton.active = false;
		} else if (next == null) {
			this.advanceButton.setMessage(this.menu.age().displayName());
			this.advanceButton.active = false;
		} else {
			this.advanceButton.setMessage(Component.translatable("gui.warbands.advance", next.displayName()));
			this.advanceButton.active = this.menu.canAdvance();
		}
		this.advanceButton.setTooltip(Tooltip.create(advanceTooltip()));
		int missing = this.menu.missingBlocks();
		int cost = missing * Repairs.STONE_PER_BLOCK;
		this.repairButton.active = missing > 0 && !this.menu.raided() && this.menu.stock(Resource.STONE) >= cost;
		MutableComponent repair = Component.translatable("gui.warbands.repair").withStyle(ChatFormatting.WHITE);
		if (missing == 0) {
			repair.append("\n").append(Component.translatable("gui.warbands.repair_none").withStyle(ChatFormatting.GRAY));
		} else {
			repair.append("\n").append(Component.translatable("gui.warbands.repair_cost", missing, cost).withStyle(ChatFormatting.GRAY));
			if (this.menu.raided()) {
				repair.append("\n").append(Component.translatable("gui.warbands.reason.repair_raid").withStyle(ChatFormatting.RED));
			} else if (this.menu.stock(Resource.STONE) < cost) {
				repair.append("\n").append(Component.translatable("gui.warbands.reason.repair_stone").withStyle(ChatFormatting.RED));
			}
		}
		repair.append("\n").append(Component.translatable("gui.warbands.repair_rule").withStyle(ChatFormatting.DARK_GRAY));
		this.repairButton.setTooltip(Tooltip.create(repair));

		CampTier tier = CampTier.forAge(this.menu.age());
		boolean affordable = true;
		// Since camp kinds the map can lead to any of the age's kinds: name the age and list them, not just the plain one.
		MutableComponent map = Component.translatable("gui.warbands.war_map", this.menu.age().displayName()).withStyle(ChatFormatting.WHITE);
		MutableComponent kinds = Component.empty();
		for (CampTier kind : CampTier.kinds(this.menu.age())) {
			if (!kinds.getSiblings().isEmpty()) {
				kinds.append(", ");
			}
			kinds.append(kind.displayName());
		}
		map.append("\n").append(Component.translatable("gui.warbands.war_map_kinds", kinds).withStyle(ChatFormatting.GRAY));
		for (Resource resource : Resource.values()) {
			if (tier.cost(resource) > 0) {
				map.append("\n").append(Component.literal(tier.cost(resource) + " ").append(resource.displayName()).withStyle(ChatFormatting.GRAY));
				affordable &= this.menu.stock(resource) >= tier.cost(resource);
			}
		}
		map.append("\n").append(Component.translatable("gui.warbands.war_map_plunder").withStyle(ChatFormatting.GOLD));
		for (Resource resource : Resource.values()) {
			if (tier.plunder(resource) > 0) {
				map.append("\n").append(Component.literal(" + " + tier.plunder(resource) + " ").append(resource.displayName())
						.withStyle(ChatFormatting.GREEN));
			}
		}
		if (this.menu.campActive()) {
			map.append("\n").append(Component.translatable("gui.warbands.reason.war_map_active").withStyle(ChatFormatting.RED));
			map.append("\n").append(Component.translatable("gui.warbands.war_map_abandon").withStyle(ChatFormatting.YELLOW));
		} else if (!affordable) {
			map.append("\n").append(Component.translatable("gui.warbands.reason.resources").withStyle(ChatFormatting.RED));
		}
		map.append("\n").append(Component.translatable("gui.warbands.war_map_rule").withStyle(ChatFormatting.DARK_GRAY));
		// With a camp to take, the button stays clickable: a shift-click abandons it.
		this.warMapButton.active = this.menu.campActive() || affordable;
		this.warMapButton.setTooltip(Tooltip.create(map));
	}

	private Component advanceTooltip() {
		Age next = this.menu.age().next();
		if (next == null) {
			MutableComponent text = Component.translatable("gui.warbands.age_max").withStyle(ChatFormatting.WHITE);
			appendBenefits(text, this.menu.age(), "gui.warbands.age_current_benefits");
			return text;
		}
		MutableComponent text = next.displayName().copy().withStyle(ChatFormatting.WHITE);
		appendBenefits(text, next, "gui.warbands.age_benefits");
		for (Resource resource : Resource.values()) {
			if (next.cost(resource) > 0) {
				text.append("\n").append(Component.literal(next.cost(resource) + " ").append(resource.displayName()).withStyle(ChatFormatting.GRAY));
			}
		}
		Item requirement = next.requirement();
		if (requirement != null) {
			text.append("\n").append(Component.translatable("gui.warbands.age_item", Component.translatable(requirement.getDescriptionId()))
					.withStyle(ChatFormatting.GRAY));
		}
		text.append("\n").append(Component.translatable("gui.warbands.train_time", next.researchTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
		if (this.menu.researching()) {
			text.append("\n").append(Component.translatable("gui.warbands.reason.age_busy").withStyle(ChatFormatting.RED));
		} else {
			if (!this.menu.canAffordAge(next)) {
				text.append("\n").append(Component.translatable("gui.warbands.reason.age_resources").withStyle(ChatFormatting.RED));
			}
			if (!this.menu.hasRequirement(next)) {
				text.append("\n").append(Component.translatable("gui.warbands.reason.age_item",
						requirement == null ? Component.empty() : Component.translatable(requirement.getDescriptionId())).withStyle(ChatFormatting.RED));
			}
		}
		return text;
	}

	/** Where the population limit comes from, and the rule for houses. */
	private List<Component> populationTooltip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.warbands.population_split", this.menu.population(), this.menu.committed()));
		lines.add(Component.translatable("gui.warbands.population_limit", this.menu.populationCap()));
		lines.add(Component.translatable("gui.warbands.population_base", TownCenterBlockEntity.BASE_POPULATION).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("gui.warbands.population_houses", this.menu.houses(),
				this.menu.houses() * TownCenterBlockEntity.POPULATION_PER_HOUSE).withStyle(ChatFormatting.GRAY));
		int ageBonus = this.menu.age().populationBonus();
		if (ageBonus > 0) {
			lines.add(Component.translatable("gui.warbands.population_age", this.menu.age().displayName(), ageBonus).withStyle(ChatFormatting.GRAY));
		}
		if (this.menu.otherQueues() > 0) {
			lines.add(Component.translatable("gui.warbands.population_queued", this.menu.otherQueues()).withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("gui.warbands.house_rule.1").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("gui.warbands.house_rule.2").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** What an age gives: shown before advancing so the cost has a reason. */
	private static void appendBenefits(MutableComponent text, Age age, String headerKey) {
		text.append("\n").append(Component.translatable(headerKey).withStyle(ChatFormatting.GOLD));
		for (Component benefit : age.benefits()) {
			text.append("\n").append(Component.literal("  ").append(benefit).withStyle(ChatFormatting.GREEN));
		}
	}

	/** Why a unit can't be queued right now, or null if it can. */
	private Component blockedReason(UnitType unit) {
		if (this.menu.population() + this.menu.committed() >= this.menu.populationCap()) {
			return Component.translatable("gui.warbands.reason.population");
		}
		if (this.menu.queueSize() >= TownCenterBlockEntity.MAX_QUEUE) {
			return Component.translatable("gui.warbands.reason.queue");
		}
		if (!this.menu.canAfford(unit)) {
			return Component.translatable("gui.warbands.reason.resources");
		}
		return null;
	}

	/** 512 -> "512", 1234 -> "1.2k", 10268 -> "10k". */
	private static String compact(int value) {
		if (value < 1000) {
			return String.valueOf(value);
		}
		if (value < 10_000) {
			return String.format(java.util.Locale.ROOT, "%.1fk", Math.floor(value / 100.0) / 10.0);
		}
		return (value / 1000) + "k";
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

		for (Resource resource : Resource.values()) {
			graphics.item(new ItemStack(resource.icon()), x + RESOURCE_X + resource.ordinal() * RESOURCE_SPACING, y + RESOURCE_Y);
		}
		for (int i = 0; i < TownCenterBlockEntity.MAX_QUEUE; i++) {
			UnitType unit = this.menu.queued(i);
			if (unit != null) {
				graphics.item(new ItemStack(unit.icon()), x + QUEUE_X + i * QUEUE_SPACING, y + QUEUE_Y);
			}
		}

		int barY = y + QUEUE_Y + 20;
		graphics.fill(x + QUEUE_X, barY, x + QUEUE_X + BAR_WIDTH, barY + 3, 0xFF373737);
		int filled = Math.round(BAR_WIDTH * this.menu.progress());
		if (filled > 0) {
			graphics.fill(x + QUEUE_X, barY, x + QUEUE_X + filled, barY + 3, this.menu.isBlocked() ? 0xFFC03030 : 0xFF4CBB17);
		}

		int ageY = y + ADVANCE_Y + 20;
		graphics.fill(x + ADVANCE_X, ageY, x + ADVANCE_X + ADVANCE_WIDTH, ageY + 3, 0xFF373737);
		int ageFilled = Math.round(ADVANCE_WIDTH * this.menu.researchProgress());
		if (ageFilled > 0) {
			graphics.fill(x + ADVANCE_X, ageY, x + ADVANCE_X + ageFilled, ageY + 3, 0xFF3A7BD5);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);

		// One number: soldiers alive plus the ones in training (they already hold a slot). The tooltip splits it.
		int committed = this.menu.committed();
		int used = this.menu.population() + committed;
		boolean full = used >= this.menu.populationCap();
		Component population = Component.translatable("gui.warbands.population", used, this.menu.populationCap());
		graphics.text(this.font, population, this.imageWidth - 8 - this.font.width(population), this.titleLabelY,
				full ? 0xFFB02020 : LABEL_COLOR, false);

		for (Resource resource : Resource.values()) {
			graphics.text(this.font, compact(this.menu.stock(resource)),
					RESOURCE_X + resource.ordinal() * RESOURCE_SPACING + 18, RESOURCE_Y + 5, resource.color(), false);
		}

		graphics.text(this.font, Component.translatable("gui.warbands.deposit"), TownCenterMenu.DEPOSIT_X, BUTTON_Y + 4, LABEL_COLOR, false);

		List<UnitType> units = UnitType.trainedAt(TrainingSite.TOWN_CENTER);
		for (int i = 0; i < units.size(); i++) {
			int costX = BUTTON_X + i * BUTTON_SPACING + 2;
			for (Resource resource : Resource.values()) {
				int cost = units.get(i).cost(resource);
				if (cost > 0) {
					String label = cost + resource.displayName().getString().substring(0, 1).toUpperCase();
					graphics.text(this.font, label, costX, BUTTON_Y + 23, resource.color(), false);
					costX += this.font.width(label) + 5;
				}
			}
		}

		Component status;
		int statusColor = LABEL_COLOR;
		if (this.menu.queueSize() == 0 && full) {
			status = Component.translatable("gui.warbands.need_houses");
			statusColor = 0xFFB02020;
		} else if (this.menu.queueSize() == 0) {
			status = Component.translatable("gui.warbands.idle");
		} else if (this.menu.isBlocked()) {
			status = Component.translatable("gui.warbands.need_houses");
			statusColor = 0xFFB02020;
		} else {
			status = Component.literal(Math.round(this.menu.progress() * 100) + "%");
		}
		graphics.text(this.font, status, QUEUE_X + BAR_WIDTH + 6, QUEUE_Y + 5, statusColor, false);

		Component raid = nextRaidLabel();
		if (raid != null) {
			graphics.text(this.font, raid, this.imageWidth - 8 - this.font.width(raid), this.inventoryLabelY,
					this.menu.nextRaidDays() == 0 ? 0xFFB02020 : LABEL_COLOR, false);
		}
	}

	/** "Raid: tonight" / "Raid: in 3 days", or null before raids start. */
	private @Nullable Component nextRaidLabel() {
		int days = this.menu.nextRaidDays();
		if (days < 0) {
			return null;
		}
		return days == 0 ? Component.translatable("gui.warbands.next_raid.tonight") : Component.translatable("gui.warbands.next_raid.days", days);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawIconButtons(graphics);

		if (isInside(mouseX, mouseY, this.leftPos + this.imageWidth / 2, this.topPos + this.titleLabelY - 2, this.imageWidth / 2 - 6, 12)) {
			graphics.setTooltipForNextFrame(this.font, populationTooltip(), Optional.empty(), mouseX, mouseY);
		}

		Component raid = nextRaidLabel();
		if (raid != null && isInside(mouseX, mouseY, this.leftPos + this.imageWidth - 8 - this.font.width(raid),
				this.topPos + this.inventoryLabelY - 2, this.font.width(raid), 12)) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.translatable("gui.warbands.next_raid.rule"), 200)
					, mouseX, mouseY);
		}

		for (Resource resource : Resource.values()) {
			int rx = this.leftPos + RESOURCE_X + resource.ordinal() * RESOURCE_SPACING;
			if (isInside(mouseX, mouseY, rx, this.topPos + RESOURCE_Y, RESOURCE_SPACING - 2, 16)) {
				List<Component> lines = new ArrayList<>();
				lines.add(resource.displayName().copy().append(": " + this.menu.stock(resource)));
				lines.add(Component.translatable("gui.warbands.accepts." + resource.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
				graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
		int slot = queueSlotAt(mouseX, mouseY);
		UnitType unit = slot < 0 ? null : this.menu.queued(slot);
		if (unit != null) {
			graphics.setTooltipForNextFrame(this.font, List.of(unit.displayName(),
					Component.translatable("gui.warbands.cancel").withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
		}
	}

	/** Icons on the icon buttons, drawn over them. */
	private void drawIconButtons(GuiGraphicsExtractor graphics) {
		graphics.item(new ItemStack(Items.ANVIL), this.repairButton.getX() + 1, this.repairButton.getY() + 1);
		graphics.item(new ItemStack(ModItems.WAR_MAP), this.warMapButton.getX() + 1, this.warMapButton.getY() + 1);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int slot = queueSlotAt(event.x(), event.y());
		if (slot >= 0 && this.menu.queued(slot) != null) {
			clickButton(TownCenterMenu.CANCEL_BUTTON_OFFSET + slot);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int queueSlotAt(double mouseX, double mouseY) {
		for (int i = 0; i < TownCenterBlockEntity.MAX_QUEUE; i++) {
			if (isInside(mouseX, mouseY, this.leftPos + QUEUE_X + i * QUEUE_SPACING, this.topPos + QUEUE_Y, 18, 18)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
