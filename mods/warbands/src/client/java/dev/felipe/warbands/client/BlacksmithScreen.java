package dev.felipe.warbands.client;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.BlacksmithBlockEntity;
import dev.felipe.warbands.town.BlacksmithMenu;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.Upgrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Blacksmith: the Town Center's stockpile, the research in progress (the bar under the stock), and two columns of
 * upgrade buttons, Feudal on the left and Castle on the right.
 */
public class BlacksmithScreen extends AbstractContainerScreen<BlacksmithMenu> {
	private static final Identifier TEXTURE = Warbands.id("textures/gui/blacksmith.png");
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int RESOURCE_X = 8;
	private static final int RESOURCE_Y = 17;
	private static final int RESOURCE_SPACING = 42;
	private static final int COLUMN_X = 8;
	private static final int COLUMN_STEP = 82;
	private static final int HEADER_Y = 42;
	private static final int BUTTON_Y = 53;
	private static final int BUTTON_WIDTH = 78;
	private static final int BUTTON_STEP = 21;
	private static final int BAR_Y = 36;

	private final List<Button> researchButtons = new ArrayList<>();

	public BlacksmithScreen(BlacksmithMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 236);
		this.inventoryLabelY = BlacksmithMenu.INVENTORY_Y - 12;
	}

	@Override
	protected void init() {
		super.init();
		researchButtons.clear();
		int feudalRow = 0;
		int castleRow = 0;
		for (Upgrade upgrade : Upgrade.values()) {
			boolean castle = upgrade.age() == Age.CASTLE;
			int row = castle ? castleRow++ : feudalRow++;
			Button button = Button.builder(upgrade.displayName(), b -> clickButton(upgrade.ordinal()))
					.bounds(this.leftPos + COLUMN_X + (castle ? COLUMN_STEP : 0), this.topPos + BUTTON_Y + row * BUTTON_STEP, BUTTON_WIDTH, 20)
					.tooltip(Tooltip.create(researchTooltip(upgrade)))
					.build();
			researchButtons.add(this.addRenderableWidget(button));
		}
		updateButtons();
	}

	private void clickButton(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private static MutableComponent researchTooltip(Upgrade upgrade) {
		MutableComponent text = upgrade.displayName().copy().withStyle(ChatFormatting.WHITE);
		for (Resource resource : Resource.values()) {
			if (upgrade.cost(resource) > 0) {
				text.append("\n").append(Component.literal(upgrade.cost(resource) + " ").append(resource.displayName()).withStyle(ChatFormatting.GRAY));
			}
		}
		text.append("\n").append(upgrade.effect().copy().withStyle(ChatFormatting.DARK_GRAY));
		return text;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		Upgrade[] upgrades = Upgrade.values();
		Upgrade researching = this.menu.researching();
		for (int i = 0; i < upgrades.length && i < researchButtons.size(); i++) {
			Upgrade upgrade = upgrades[i];
			Button button = researchButtons.get(i);
			boolean owned = this.menu.owns(upgrade);
			Component reason = owned ? null : blockedReason(upgrade);
			button.active = !owned && reason == null;
			// Owned upgrades read as done at a glance, not just greyed out like unaffordable ones.
			if (owned) {
				button.setMessage(Component.translatable("gui.warbands.upgrade_done", upgrade.displayName()).withStyle(ChatFormatting.GREEN));
			} else if (upgrade == researching) {
				button.setMessage(Component.literal(Math.round(this.menu.researchProgress() * 100) + "%").withStyle(ChatFormatting.GOLD));
			} else {
				button.setMessage(upgrade.displayName());
			}
			MutableComponent tip = researchTooltip(upgrade);
			if (owned) {
				tip.append("\n").append(Component.translatable("gui.warbands.upgrade_owned").withStyle(ChatFormatting.GRAY));
			} else if (upgrade == researching) {
				tip.append("\n").append(Component.translatable("gui.warbands.upgrade_researching").withStyle(ChatFormatting.GOLD));
			} else if (reason != null) {
				tip.append("\n").append(reason.copy().withStyle(ChatFormatting.RED));
			}
			button.setTooltip(Tooltip.create(tip));
		}
	}

	private @Nullable Component blockedReason(Upgrade upgrade) {
		if (this.menu.link() == BlacksmithBlockEntity.LINK_NO_TOWN) {
			return Component.translatable("gui.warbands.reason.no_town");
		}
		if (this.menu.link() == BlacksmithBlockEntity.LINK_WRECKED) {
			return Component.translatable("gui.warbands.reason.wrecked");
		}
		if (this.menu.link() == BlacksmithBlockEntity.LINK_VILLAGE) {
			return Component.translatable("gui.warbands.reason.need_feudal");
		}
		if (!this.menu.townAge().atLeast(upgrade.age())) {
			return Component.translatable("gui.warbands.reason.need_castle");
		}
		Upgrade first = upgrade.requires();
		if (first != null && !this.menu.owns(first)) {
			return Component.translatable("gui.warbands.reason.upgrade_first", first.displayName());
		}
		if (this.menu.researching() != null) {
			return Component.translatable("gui.warbands.reason.upgrade_busy");
		}
		if (!this.menu.canAfford(upgrade)) {
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		for (Resource resource : Resource.values()) {
			graphics.item(new ItemStack(resource.icon()), x + RESOURCE_X + resource.ordinal() * RESOURCE_SPACING, y + RESOURCE_Y);
		}
		int filled = Math.round((this.imageWidth - 14) * this.menu.researchProgress());
		if (filled > 0) {
			graphics.fill(x + 7, y + BAR_Y, x + 7 + filled, y + BAR_Y + 3, 0xFFE08A2A);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		for (Resource resource : Resource.values()) {
			graphics.text(this.font, compact(this.menu.stock(resource)),
					RESOURCE_X + resource.ordinal() * RESOURCE_SPACING + 18, RESOURCE_Y + 5, resource.color(), false);
		}
		Component status;
		int statusColor = LABEL_COLOR;
		if (this.menu.link() == BlacksmithBlockEntity.LINK_NO_TOWN) {
			status = Component.translatable("gui.warbands.no_town");
			statusColor = 0xFFB02020;
		} else if (this.menu.link() == BlacksmithBlockEntity.LINK_WRECKED) {
			status = Component.translatable("gui.warbands.wrecked");
			statusColor = 0xFFB02020;
		} else if (this.menu.link() == BlacksmithBlockEntity.LINK_VILLAGE) {
			status = Component.translatable("gui.warbands.village");
			statusColor = 0xFFB02020;
		} else if (this.menu.researching() != null) {
			status = Component.translatable("gui.warbands.researching", this.menu.researching().displayName());
			statusColor = 0xFFA05A10;
		} else {
			status = Component.translatable("gui.warbands.paid_from_town");
		}
		graphics.text(this.font, status, this.imageWidth - 8 - this.font.width(status), this.titleLabelY, statusColor, false);
		graphics.text(this.font, Age.FEUDAL.displayName(), COLUMN_X, HEADER_Y, LABEL_COLOR, false);
		graphics.text(this.font, Age.CASTLE.displayName(), COLUMN_X + COLUMN_STEP, HEADER_Y,
				this.menu.townAge().atLeast(Age.CASTLE) ? LABEL_COLOR : 0xFF8B8B8B, false);
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
	}

	private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
