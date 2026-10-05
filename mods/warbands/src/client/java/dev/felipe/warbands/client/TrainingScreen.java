package dev.felipe.warbands.client;

import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TrainingBlockEntity;
import dev.felipe.warbands.town.TrainingMenu;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.ItemStack;

/** Any training building: the Town Center's stockpile, one train button per unit, and this building's queue. */
public class TrainingScreen extends AbstractContainerScreen<TrainingMenu> {
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int RESOURCE_X = 8;
	private static final int RESOURCE_Y = 17;
	private static final int RESOURCE_SPACING = 42;
	private static final int BUTTON_Y = 40;
	private static final int BUTTON_WIDTH = 80;
	private static final int QUEUE_X = 8;
	private static final int QUEUE_Y = 78;
	private static final int QUEUE_SPACING = 20;
	private static final int BAR_WIDTH = (TownCenterBlockEntity.MAX_QUEUE - 1) * QUEUE_SPACING + 18;

	private final List<Button> trainButtons = new ArrayList<>();

	private final Identifier texture;

	public TrainingScreen(TrainingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 206);
		this.texture = menu.site().guiTexture();
		this.inventoryLabelY = TrainingMenu.INVENTORY_Y - 12;
	}

	@Override
	protected void init() {
		super.init();
		trainButtons.clear();
		List<UnitType> units = UnitType.trainedAt(this.menu.site());
		int total = units.size() * (BUTTON_WIDTH + 4) - 4;
		int start = (this.imageWidth - total) / 2;
		for (int i = 0; i < units.size(); i++) {
			UnitType unit = units.get(i);
			Button button = Button.builder(unit.displayName(), b -> clickButton(unit.ordinal()))
					.bounds(this.leftPos + start + i * (BUTTON_WIDTH + 4), this.topPos + BUTTON_Y, BUTTON_WIDTH, 20)
					.tooltip(Tooltip.create(trainTooltip(unit)))
					.build();
			trainButtons.add(this.addRenderableWidget(button));
		}
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
				text.append("\n").append(Component.literal(unit.cost(resource) + " ").append(resource.displayName()).withStyle(ChatFormatting.GRAY));
			}
		}
		text.append("\n").append(Component.translatable("gui.warbands.train_time", unit.trainTicks() / 20).withStyle(ChatFormatting.DARK_GRAY));
		if (unit.tooltip() != null) {
			text.append("\n").append(unit.tooltip().copy().withStyle(ChatFormatting.DARK_GRAY));
		}
		return text;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		List<UnitType> units = UnitType.trainedAt(this.menu.site());
		for (int i = 0; i < units.size() && i < trainButtons.size(); i++) {
			UnitType unit = units.get(i);
			Button button = trainButtons.get(i);
			button.active = this.menu.canQueue(unit);
			Component reason = blockedReason(unit);
			button.setTooltip(Tooltip.create(reason == null ? trainTooltip(unit)
					: trainTooltip(unit).copy().append("\n").append(reason.copy().withStyle(ChatFormatting.RED))));
		}
	}

	private Component blockedReason(UnitType unit) {
		if (this.menu.link() == TrainingBlockEntity.LINK_NO_TOWN) {
			return Component.translatable("gui.warbands.reason.no_town");
		}
		if (this.menu.link() == TrainingBlockEntity.LINK_TOO_EARLY) {
			return this.menu.site().requiredAge().notReachedReason();
		}
		if (this.menu.population() + this.menu.reserved() >= this.menu.populationCap()) {
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

	private static String compact(int value) {
		if (value < 1000) {
			return String.valueOf(value);
		}
		if (value < 10_000) {
			return String.format(Locale.ROOT, "%.1fk", Math.floor(value / 100.0) / 10.0);
		}
		return (value / 1000) + "k";
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
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
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		int reserved = this.menu.reserved();
		boolean full = this.menu.population() + reserved >= this.menu.populationCap();
		String used = reserved > 0 ? this.menu.population() + "+" + reserved : String.valueOf(this.menu.population());
		Component population = Component.translatable("gui.warbands.population", used, this.menu.populationCap());
		graphics.text(this.font, population, this.imageWidth - 8 - this.font.width(population), this.titleLabelY,
				full ? 0xFFB02020 : LABEL_COLOR, false);

		for (Resource resource : Resource.values()) {
			graphics.text(this.font, compact(this.menu.stock(resource)),
					RESOURCE_X + resource.ordinal() * RESOURCE_SPACING + 18, RESOURCE_Y + 5, resource.color(), false);
		}
		graphics.text(this.font, Component.translatable("gui.warbands.paid_from_town"), 8, BUTTON_Y + 22, LABEL_COLOR, false);

		Component status;
		int statusColor = LABEL_COLOR;
		if (this.menu.link() == TrainingBlockEntity.LINK_NO_TOWN) {
			status = Component.translatable("gui.warbands.no_town");
			statusColor = 0xFFB02020;
		} else if (this.menu.link() == TrainingBlockEntity.LINK_TOO_EARLY) {
			status = this.menu.site().requiredAge().notReachedStatus();
			statusColor = 0xFFB02020;
		} else if (this.menu.isWrecked()) {
			status = Component.translatable("gui.warbands.wrecked");
			statusColor = 0xFFB02020;
		} else if (this.menu.queueSize() == 0 && full) {
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
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		for (Resource resource : Resource.values()) {
			int rx = this.leftPos + RESOURCE_X + resource.ordinal() * RESOURCE_SPACING;
			if (isInside(mouseX, mouseY, rx, this.topPos + RESOURCE_Y, RESOURCE_SPACING - 2, 16)) {
				graphics.setTooltipForNextFrame(this.font, List.of(
						resource.displayName().copy().append(": " + this.menu.stock(resource)),
						Component.translatable("gui.warbands.paid_from_town").withStyle(ChatFormatting.GRAY)),
						Optional.empty(), mouseX, mouseY);
			}
		}
		int slot = queueSlotAt(mouseX, mouseY);
		UnitType unit = slot < 0 ? null : this.menu.queued(slot);
		if (unit != null) {
			graphics.setTooltipForNextFrame(this.font, List.of(unit.displayName(),
					Component.translatable("gui.warbands.cancel").withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int slot = queueSlotAt(event.x(), event.y());
		if (slot >= 0 && this.menu.queued(slot) != null) {
			clickButton(TrainingMenu.CANCEL_BUTTON_OFFSET + slot);
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
