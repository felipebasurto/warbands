package dev.felipe.warbands.client;

import dev.felipe.warbands.HandbookItem;
import dev.felipe.warbands.PlaytestGuideItem;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.HorseRenderer;

public class WarbandsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.MILITIA,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/militia.png")));
		EntityRendererRegistry.register(ModEntities.ARCHER,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/archer.png")));
		EntityRendererRegistry.register(ModEntities.SPEARMAN,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/spearman.png")));
		EntityRendererRegistry.register(ModEntities.CROSSBOWMAN,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/crossbowman.png")));
		EntityRendererRegistry.register(ModEntities.KNIGHT,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/knight.png")));
		EntityRendererRegistry.register(ModEntities.WARLORD,
				context -> new SoldierRenderer<>(context, Warbands.id("textures/entity/warlord.png")));
		EntityRendererRegistry.register(ModEntities.WARHORSE, HorseRenderer::new);
		EntityRendererRegistry.register(ModEntities.STABLE_HORSE, HorseRenderer::new);
		EntityRendererRegistry.register(ModEntities.SMITH, SmithRenderer::new);
		ModelLayerRegistry.registerModelLayer(RamModel.LAYER, RamModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.RAM, RamRenderer::new);
		MenuScreens.register(ModMenus.TOWN_CENTER, TownCenterScreen::new);
		MenuScreens.register(ModMenus.BARRACKS, TrainingScreen::new);
		MenuScreens.register(ModMenus.ARCHERY_RANGE, TrainingScreen::new);
		MenuScreens.register(ModMenus.BLACKSMITH, BlacksmithScreen::new);
		MenuScreens.register(ModMenus.STABLE, TrainingScreen::new);
		MenuScreens.register(ModMenus.SIEGE_WORKSHOP, TrainingScreen::new);
		MenuScreens.register(ModMenus.WATCH_TOWER, WatchTowerScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(BlueprintPreview::tick);
		HandbookItem.openScreen = Handbook::open;
		PlaytestGuideItem.openScreen = () -> Minecraft.getInstance().setScreen(new PlaytestScreen());
	}
}
