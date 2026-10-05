package dev.felipe.warbands.client;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.WatchTowerBlockEntity;
import dev.felipe.warbands.town.WatchTowerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Posts ranged soldiers on the roof, or sends them back to Follow. */
public class WatchTowerScreen extends AbstractContainerScreen<WatchTowerMenu> {
	private static final Identifier TEXTURE = Warbands.id("textures/gui/watch_tower.png");
	private static final int LABEL_COLOR = 0xFF404040;

	private Button post;
	private Button recall;

	public WatchTowerScreen(WatchTowerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 168);
		this.inventoryLabelY = WatchTowerMenu.INVENTORY_Y - 12;
	}

	@Override
	protected void init() {
		super.init();
		this.post = Button.builder(Component.translatable("gui.warbands.post"), b -> click(WatchTowerMenu.POST_BUTTON))
				.bounds(this.leftPos + 8, this.topPos + 36, 78, 20)
				.build();
		this.recall = Button.builder(Component.translatable("gui.warbands.recall"), b -> click(WatchTowerMenu.RECALL_BUTTON))
				.bounds(this.leftPos + 90, this.topPos + 36, 78, 20)
				.build();
		this.addRenderableWidget(this.post);
		this.addRenderableWidget(this.recall);
	}

	private void click(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		Component postReason = postReason();
		this.post.active = postReason == null;
		this.post.setTooltip(Tooltip.create(postReason == null
				? Component.translatable("gui.warbands.post_tip")
				: postReason));
		boolean canRecall = this.menu.link() == WatchTowerBlockEntity.LINK_OK && this.menu.posted() > 0;
		this.recall.active = canRecall;
		this.recall.setTooltip(Tooltip.create(canRecall
				? Component.translatable("gui.warbands.recall_tip")
				: Component.translatable("gui.warbands.reason.none_posted")));
	}

	private Component postReason() {
		if (this.menu.link() == WatchTowerBlockEntity.LINK_NO_TOWN) {
			return Component.translatable("gui.warbands.reason.no_town");
		}
		if (this.menu.link() == WatchTowerBlockEntity.LINK_NOT_CASTLE) {
			return Component.translatable("gui.warbands.reason.need_castle");
		}
		if (this.menu.posted() >= WatchTowerBlockEntity.CAP) {
			return Component.translatable("gui.warbands.reason.tower_full");
		}
		if (this.menu.nearby() <= 0) {
			return Component.translatable("gui.warbands.reason.no_ranged");
		}
		return null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		Component count = Component.translatable("gui.warbands.posted", this.menu.posted(), WatchTowerBlockEntity.CAP);
		int color = this.menu.posted() >= WatchTowerBlockEntity.CAP ? 0xFFB02020 : LABEL_COLOR;
		graphics.text(this.font, count, 8, 22, color, false);
	}
}
