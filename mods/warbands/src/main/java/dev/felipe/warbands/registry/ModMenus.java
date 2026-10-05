package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.BlacksmithMenu;
import dev.felipe.warbands.town.WatchTowerMenu;
import dev.felipe.warbands.town.TownCenterMenu;
import dev.felipe.warbands.town.TrainingMenu;
import dev.felipe.warbands.town.TrainingSite;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<TownCenterMenu> TOWN_CENTER = Registry.register(BuiltInRegistries.MENU,
			Warbands.id("town_center"), new MenuType<>(TownCenterMenu::new, FeatureFlags.VANILLA_SET));
	public static final MenuType<TrainingMenu> BARRACKS = training("barracks", TrainingSite.BARRACKS);
	public static final MenuType<TrainingMenu> ARCHERY_RANGE = training("archery_range", TrainingSite.ARCHERY_RANGE);
	public static final MenuType<TrainingMenu> STABLE = training("stable", TrainingSite.STABLE);
	public static final MenuType<TrainingMenu> SIEGE_WORKSHOP = training("siege_workshop", TrainingSite.SIEGE_WORKSHOP);
	public static final MenuType<WatchTowerMenu> WATCH_TOWER = Registry.register(BuiltInRegistries.MENU,
			Warbands.id("watch_tower"), new MenuType<>(WatchTowerMenu::new, FeatureFlags.VANILLA_SET));
	public static final MenuType<BlacksmithMenu> BLACKSMITH = Registry.register(BuiltInRegistries.MENU,
			Warbands.id("blacksmith"), new MenuType<>(BlacksmithMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	private static MenuType<TrainingMenu> training(String name, TrainingSite site) {
		return Registry.register(BuiltInRegistries.MENU, Warbands.id(name),
				new MenuType<>((containerId, inventory) -> new TrainingMenu(site, containerId, inventory), FeatureFlags.VANILLA_SET));
	}

	public static void initialize() {
	}
}
