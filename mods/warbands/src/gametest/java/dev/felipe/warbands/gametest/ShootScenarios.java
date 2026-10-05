package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.activeRaiders;
import static dev.felipe.warbands.gametest.Run.army;
import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.player;

import dev.felipe.warbands.Playtest;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Threat;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.gametest.Cinema.Rank;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.AgeRegistry;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * The marketing photo shoot: spectator camera, no HUD, 1920x1080, in real biomes of the normal-terrain world. Not part of
 * any regression run (unticked in the checklist): {@code scripts/shoot.sh warbands} runs it and collects the pictures.
 */
final class ShootScenarios {
	private ShootScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("shoot.army", "a Castle army in ranks on a meadow: aerial, low hero and wide pictures", ShootScenarios::armyInRanks));
		all.add(new Scenario("shoot.ui", "every building menu, every guide page, the advancement tab and the HUD, 1920x1080 with the interface on", base -> ui(base, "shoot-ui", "en_us")));
		all.add(new Scenario("shoot.ui-es", "the same interface pictures in Spanish (es_es), named shoot-es-*", base -> ui(base, "shoot-es", "es_es")));
		all.add(new Scenario("shoot.howto", "the first steps as pictures: blueprint outline, the house, the recruiting menu, the army with the horn (1920x1080, HUD on)", ShootScenarios::howto));
		all.add(new Scenario("shoot.ages", "the same town at Village, Feudal and Castle Age from one camera", ShootScenarios::ages));
		all.add(new Scenario("shoot.film-build", "video: a Town Center rising layer by layer while the camera circles it, 5 s", ShootScenarios::build));
		all.add(new Scenario("shoot.moods", "the same small army at dawn, dusk, night, in rain and in snow, hero and wide pictures", ShootScenarios::moods));
		all.add(new Scenario("shoot.portraits", "every unit type close up, plus a line-up of all of them", ShootScenarios::portraits));
		all.add(new Scenario("shoot.town", "a full Castle town from the air, four angles around it", ShootScenarios::town));
		all.add(new Scenario("shoot.war", "a staged battle: two armies in melee, arrows in the air, blasts, a burning town and smoke columns, no AI involved", (run) -> war(run, WarMode.STILLS)));
		all.add(new Scenario("shoot.film-war", "video: the staged war from the air down to the clash, fires and blasts running, 8 s at 20 fps", run -> war(run, WarMode.FILM)));
		all.add(new Scenario("shoot.film-melee", "video: the same line-up with the AI on, a real melee seen from the ground, 8 s", run -> war(run, WarMode.MELEE)));
		all.add(new Scenario("shoot.battle", "a Feudal raid meets the defenders; pictures from the air while they fight", ShootScenarios::battle));
		all.add(new Scenario("shoot.assault", "your army storms enemy camps: guards and attackers posed in melee, arrows in the air, fire and blasts on the walls", run -> assault(run, false)));
		all.add(new Scenario("shoot.film-assault", "video: the army charges a Fort or Keep and fights its guards for real, 8 s", run -> assault(run, true)));
		all.add(new Scenario("shoot.camps", "every camp kind in its home biome, one aerial picture each", ShootScenarios::camps));
	}

	/** A 1920x1080 picture of whatever is on screen (menu, guide, world with the HUD); mouse parked so no tooltip shows. */
	private static void uiShot(Run run, String name) {
		run.context.runOnClient(minecraft -> {
			minecraft.getToastManager().clear();
			minecraft.gui.getChat().clearMessages(false);
			minecraft.gui.clearTitles();
		});
		run.context.getInput().setCursorPos(0, 0);
		run.context.waitTicks(3);
		run.context.takeScreenshot(name);
	}

	/** Real 1920x1080 window: withSize() would draw the interface at the old window's scale in a corner of the picture. */
	private static void window(Run run, int width, int height) {
		run.context.runOnClient(minecraft -> minecraft.getWindow().setWindowed(width, height));
		run.context.waitTicks(10);
	}

	private static void uiMenu(Run run, BlockPos core, String name) {
		run.tp(core.getX() + 0.5, core.getY(), core.getZ() + 5.5, 180.0F, 14.0F);
		run.context.waitTicks(5);
		run.server(server -> {
			if (server.overworld().getBlockEntity(core) instanceof net.minecraft.world.MenuProvider provider) {
				player(server).openMenu(provider);
			} else {
				throw new AssertionError("No menu at " + core);
			}
		});
		run.context.waitTicks(15);
		uiShot(run, name);
		run.context.setScreen(() -> null);
		run.context.waitTicks(2);
	}

	/** The interface pictures the platforms ask for: menus, the guide, the advancement tab and the HUD with an army. */
	/** Switches the client's language and waits for the resource reload (the menus and the guide read it live). */
	private static void setLanguage(Run run, String code) {
		run.context.runOnClient(minecraft -> {
			minecraft.options.languageCode = code;
			minecraft.getLanguageManager().setSelected(code);
			minecraft.reloadResourcePacks();
		});
		run.context.waitTicks(100);
	}

	private static void ui(Run base, String prefix, String language) {
		setLanguage(base, language);
		base.command("gamemode survival @a");
		base.command("time set 4000");
		base.command("weather clear");
		BlockPos site = Cinema.flatSpot(base, 40, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
		base.tp(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 180.0F, 0.0F);
		base.context.waitTicks(40);
		Run run = new Run(base.context, base.world, site);
		BlockPos[] cores = CoreScenarios.everyBuilding(run);
		run.context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(8)); // an empty slot: no big item in the picture
		window(run, Cinema.WIDTH, Cinema.HEIGHT);
		BlockPos corePos = run.ground.offset(0, 1, 8);
		uiMenu(run, corePos, prefix + "-town-center");
		uiMenu(run, cores[2], prefix + "-barracks");
		uiMenu(run, cores[3], prefix + "-archery-range");
		uiMenu(run, cores[4], prefix + "-stable");
		uiMenu(run, cores[0], prefix + "-blacksmith");
		uiMenu(run, cores[1], prefix + "-watch-tower");
		uiMenu(run, cores[5], prefix + "-siege-workshop");

		run.tp(corePos.getX() + 0.5, corePos.getY(), corePos.getZ() + 14.5, 180.0F, 8.0F); // outdoors, the town behind the blur
		run.context.waitTicks(30);
		run.context.runOnClient(minecraft -> dev.felipe.warbands.client.Handbook.open());
		run.context.waitTicks(5);
		int pages = run.context.computeOnClient(minecraft -> dev.felipe.warbands.client.Handbook.pages().size());
		for (int i = 0; i < pages; i++) {
			uiShot(run, String.format(prefix + "-guide-%02d", i + 1));
			run.context.runOnClient(minecraft -> {
				if (minecraft.screen instanceof dev.felipe.warbands.client.GuideScreen guide) {
					guide.turn(1);
				}
			});
			run.context.waitTicks(2);
		}
		run.context.setScreen(() -> null);

		run.context.runOnClient(minecraft -> {
			var advancements = minecraft.player.connection.getAdvancements();
			advancements.setSelectedTab(advancements.get(Warbands.id("root")), false);
			minecraft.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
		});
		run.context.waitTicks(10);
		uiShot(run, prefix + "-advancements");
		run.context.setScreen(() -> null);

		armyHud(run, corePos, prefix + "-hud");
		window(run, 854, 480);
		setLanguage(run, "en_us");
		check(pages > 0, "the guide has pages (" + pages + ")");
	}

	/** Selects the hotbar slot holding the item with this registry path, on the client (the server's choice isn't synced back). */
	private static void select(Run run, String path) {
		run.context.runOnClient(minecraft -> {
			var inventory = minecraft.player.getInventory();
			for (int slot = 0; slot < 9; slot++) {
				if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inventory.getItem(slot).getItem()).getPath().equals(path)) {
					inventory.setSelectedSlot(slot);
					return;
				}
			}
			throw new AssertionError("no " + path + " in the hotbar");
		});
		run.context.waitTicks(5);
	}

	/** The HUD with an army: hotbar with the starter items and the horn in hand, 21 soldiers in ranks facing the town. */
	private static void armyHud(Run run, BlockPos corePos, String name) {
		run.command("give @p warbands:handbook");
		run.command("give @p warbands:war_horn");
		run.context.waitTicks(10);
		Map<SoldierEntity, Cinema.Spot> ranks = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
			ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
			Map<SoldierEntity, Cinema.Spot> placed = Cinema.spawnRanks(level, player, corePos.offset(0, 0, 10), List.of(
					new Rank(UnitType.SPEARMAN, 7), new Rank(UnitType.MILITIA, 7), new Rank(UnitType.ARCHER, 7)));
			Map<SoldierEntity, Cinema.Spot> facing = new IdentityHashMap<>();
			placed.forEach((soldier, spot) -> facing.put(soldier, new Cinema.Spot(spot.pos(), 180.0F)));
			return facing;
		});
		run.tp(corePos.getX() + 0.5, corePos.getY(), corePos.getZ() + 18.5, 180.0F, 6.0F);
		run.context.waitTicks(30); // knights mount; chunks and light settle
		run.server(server -> Cinema.pose(ranks));
		select(run, "war_horn");
		run.context.waitTicks(15);
		uiShot(run, name);
	}

	/** The first steps of the guide as pictures: the blueprint's outline, the building, the recruiting menu, the army and its horn. */
	private static void howto(Run base) {
		base.command("gamemode survival @a");
		base.command("time set 4000");
		base.command("weather clear");
		BlockPos site = Cinema.flatSpot(base, 40, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
		base.tp(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 180.0F, 0.0F);
		base.context.waitTicks(40);
		Run run = new Run(base.context, base.world, site);
		Run.Town town = run.placeTown();
		run.stockUp(town.core());
		window(run, Cinema.WIDTH, Cinema.HEIGHT);
		BlockPos house = run.ground.offset(14, 0, 14);
		// 1. holding the blueprint shows where it fits (green) or what is in the way (red)
		run.command("give @p warbands:house_blueprint");
		run.context.waitTicks(10); // the new stack reaches the client
		run.tp(house.getX() + 0.5, house.getY() + 1, house.getZ() + 7.5, 0.0F, 38.0F);
		select(run, "house_blueprint");
		run.context.waitTicks(30);
		uiShot(run, "shoot-howto-1-blueprint");
		// 2. the house stands
		run.server(server -> Run.place(server.overworld(), player(server), house, Blueprint.HOUSE));
		run.tp(house.getX() + 0.5, house.getY() + 1, house.getZ() + 14.5, 180.0F, 12.0F);
		run.context.waitTicks(40);
		uiShot(run, "shoot-howto-2-house");
		// 3. the Town Center, where soldiers are trained
		run.server(server -> Run.townCenter(server.overworld(), town.core()).deposit(new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 32)));
		uiMenu(run, town.core(), "shoot-howto-3-recruit");
		// 4. the army, and the horn that commands it
		armyHud(run, town.core(), "shoot-howto-4-army-and-horn");
		window(run, 854, 480);
		check(true, "howto photographed");
	}

	/** The same town at each age: Village, Feudal (barracks, archery range), Castle (stable, blacksmith, tower), one camera. */
	private static void ages(Run base) {
		Cinema.begin(base, 4500);
		try {
			BlockPos site = Cinema.flatSpot(base, 46, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
			base.tp(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 180.0F, 0.0F);
			base.context.waitTicks(40);
			Run run = new Run(base.context, base.world, site);
			run.command("gamemode survival @a");
			Run.Town town = run.placeTown();
			run.stockUp(town.core());
			BlockPos ground = run.ground;
			Blueprint[] feudal = {Blueprint.BARRACKS, Blueprint.ARCHERY_RANGE};
			BlockPos[] feudalAt = {ground.offset(-14, 0, 14), ground.offset(0, 0, -10)};
			Blueprint[] castle = {Blueprint.STABLE, Blueprint.BLACKSMITH, Blueprint.WATCH_TOWER};
			BlockPos[] castleAt = {ground.offset(14, 0, -10), ground.offset(-14, 0, -10), ground.offset(14, 0, 26)};
			run.server(server -> Run.place(server.overworld(), player(server), ground.offset(14, 0, 14), Blueprint.HOUSE));
			agesShot(run, "shoot-ages-1-village", town, site);
			run.advance(town.core(), Age.FEUDAL);
			for (int i = 0; i < feudal.length; i++) {
				Blueprint blueprint = feudal[i];
				BlockPos at = feudalAt[i];
				run.server(server -> Run.place(server.overworld(), player(server), at, blueprint));
			}
			agesShot(run, "shoot-ages-2-feudal", town, site);
			run.advance(town.core(), Age.CASTLE);
			for (int i = 0; i < castle.length; i++) {
				Blueprint blueprint = castle[i];
				BlockPos at = castleAt[i];
				run.server(server -> Run.place(server.overworld(), player(server), at, blueprint));
			}
			agesShot(run, "shoot-ages-3-castle", town, site);
		} finally {
			Cinema.end(base);
		}
	}

	private static void agesShot(Run run, String name, Run.Town town, BlockPos site) {
		run.command("gamemode spectator @a");
		run.context.waitTicks(20);
		Cinema.relight(run, site, 60);
		Vec3 target = Vec3.atCenterOf(town.center()).add(0, 3, 6);
		Cinema.shot(run, name, target.add(0, 15, 31), target);
		run.command("gamemode survival @a");
		run.context.waitTicks(5);
	}

	/**
	 * A building going up layer by layer: the Town Center is placed once to record which blocks it adds, the ground is put back, and the
	 * blocks reappear from the bottom up while the camera circles it (film-build, 100 frames).
	 */
	private static void build(Run base) {
		Cinema.begin(base, 4500);
		try {
			BlockPos site = Cinema.flatSpot(base, 34, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
			base.command("gamemode survival @a");
			base.tp(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 180.0F, 0.0F);
			base.context.waitTicks(40);
			Run run = new Run(base.context, base.world, site);
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = run.compute(server -> {
				net.minecraft.server.level.ServerLevel level = server.overworld();
				java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> map = new java.util.HashMap<>();
				for (BlockPos p : BlockPos.betweenClosed(site.offset(-18, -3, -6), site.offset(18, 18, 34))) {
					map.put(p.immutable(), level.getBlockState(p));
				}
				return map;
			});
			run.placeTown();
			java.util.List<java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> added = run.compute(server -> {
				net.minecraft.server.level.ServerLevel level = server.overworld();
				java.util.List<java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> list = new java.util.ArrayList<>();
				for (var e : before.entrySet()) {
					var now = level.getBlockState(e.getKey());
					if (now != e.getValue()) {
						list.add(java.util.Map.entry(e.getKey(), now));
						level.setBlock(e.getKey(), e.getValue(), 2);
					}
				}
				list.sort(java.util.Comparator.comparingInt((java.util.Map.Entry<BlockPos, ?> e) -> e.getKey().getY()));
				check(list.size() > 200, "the Town Center added blocks to reveal (" + list.size() + ")");
				return list;
			});
			int minY = added.getFirst().getKey().getY();
			int maxY = added.getLast().getKey().getY();
			int[] revealed = {0};
			Vec3 target = Vec3.atCenterOf(site.offset(0, 4, 15));
			base.command("gamemode spectator @a");
			Cinema.relight(base, site, 50);
			java.util.List<Cinema.Key> path = java.util.List.of(
					new Cinema.Key(target.add(20, 9, 20), target), new Cinema.Key(target.add(0, 8, 26), target), new Cinema.Key(target.add(-20, 9, 20), target));
			Cinema.record(base, "film-build", 100, path, frame -> {
				double progress = Math.min(1.0, frame / 80.0);
				int limit = minY + (int) Math.round(progress * (maxY - minY + 1));
				base.server(server -> {
					while (revealed[0] < added.size() && added.get(revealed[0]).getKey().getY() <= limit) {
						var e = added.get(revealed[0]++);
						server.overworld().setBlock(e.getKey(), e.getValue(), 2);
					}
				});
			});
			check(revealed[0] == added.size(), "the whole building appeared");
		} finally {
			Cinema.end(base);
		}
	}

	/** One look of {@link #mood}: where, when and in what weather the army is photographed. */
	private record Mood(String tag, int time, String weather, ResourceKey<Biome>[] biomes) {
	}

	@SuppressWarnings("unchecked")
	private static Mood mood(String tag, int time, String weather, ResourceKey<Biome>... biomes) {
		return new Mood(tag, time, weather, biomes);
	}

	/** The same army in other light and other biomes, so the gallery shows the mod is not just a sunny meadow. */
	private static void moods(Run run) {
		for (Mood mood : List.of(
				mood("dawn", 23400, "clear", Biomes.MEADOW, Biomes.PLAINS),
				mood("desert", 5000, "clear", Biomes.DESERT, Biomes.SAVANNA, Biomes.PLAINS),
				mood("dusk", 12300, "clear", Biomes.SAVANNA, Biomes.PLAINS),
				mood("rain", 3000, "rain", Biomes.FOREST, Biomes.DARK_FOREST, Biomes.PLAINS),
				mood("cherry", 2500, "clear", Biomes.CHERRY_GROVE, Biomes.MEADOW))) {
			Cinema.begin(run, mood.time());
			try {
				BlockPos site = Cinema.flatSpot(run, 16, mood.biomes());
				run.tp(site.getX() + 0.5, site.getY() + 25, site.getZ() + 40.5, 180.0F, 30.0F);
				run.context.waitTicks(40);
				run.command("weather " + mood.weather());
				Map<SoldierEntity, Cinema.Spot> places = run.compute(server -> {
					ServerLevel level = server.overworld();
					ServerPlayer player = player(server);
					AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
					ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
					return Cinema.spawnRanks(level, player, site, List.of(
							new Rank(UnitType.SPEARMAN, 10), new Rank(UnitType.ARCHER, 10), new Rank(UnitType.KNIGHT, 6)));
				});
				run.context.waitTicks(30);
				run.server(server -> Cinema.pose(places));
				run.context.waitTicks(10);
				Cinema.relight(run, site, 70);
				Cinema.shot(run, "shoot-mood-" + mood.tag() + "-hero", Cinema.eye(run, site, 6, 1.6, 14), Cinema.at(site, 0, 2, -3));
				Cinema.shot(run, "shoot-mood-" + mood.tag() + "-wide", Cinema.eye(run, site, -8, 6, 12), Cinema.at(site, 0, 1, -3));
			} finally {
				Cinema.end(run);
			}
		}
	}

	/** Every unit type close up, in a row, so each skin and weapon can be judged and shown. */
	private static void portraits(Run run) {
		Cinema.begin(run, 1500);
		try {
			BlockPos site = Cinema.flatSpot(run, 34, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
			run.tp(site.getX() + 0.5, site.getY() + 15, site.getZ() + 20.5, 180.0F, 30.0F);
			run.context.waitTicks(40);
			UnitType[] types = {UnitType.MILITIA, UnitType.ARCHER, UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.KNIGHT, UnitType.RAM};
			double[] xs = {-26, -17, -8, 1, 11, 24};
			Map<SoldierEntity, Cinema.Spot> places = run.compute(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
				ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
				Map<SoldierEntity, Cinema.Spot> placed = new IdentityHashMap<>();
				for (int i = 0; i < types.length; i++) {
					double x = site.getX() + 0.5 + xs[i];
					double z = site.getZ() + 0.5;
					double y = Run.groundAt(level, (int) Math.floor(x), (int) Math.floor(z)).getY() + 1;
					placed.put(Run.soldier(level, types[i], new Vec3(x, y, z), player), new Cinema.Spot(new Vec3(x, y, z), 0.0F));
				}
				return placed;
			});
			run.context.waitTicks(30);
			run.server(server -> Cinema.pose(places));
			run.context.waitTicks(10);
			Cinema.relight(run, site, 50);
			Cinema.shot(run, "shoot-portrait-lineup", Cinema.eye(run, site, -1, 3.0, 26), Cinema.at(site, -1, 1.4, 0));
			for (int i = 0; i < types.length; i++) {
				boolean big = types[i] == UnitType.KNIGHT || types[i] == UnitType.RAM;
				double dist = types[i] == UnitType.RAM ? 8 : big ? 5.5 : 3.6;
				Cinema.shot(run, "shoot-portrait-" + types[i].name().toLowerCase(), Cinema.eye(run, site, xs[i] + 1.0, 1.4, dist),
						Cinema.at(site, xs[i], big ? 1.7 : 1.3, 0));
			}
		} finally {
			Cinema.end(run);
		}
	}

	private static void armyInRanks(Run run) {
		Cinema.begin(run, 11500);
		try {
			BlockPos site = Cinema.flatSpot(run, 16, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.PLAINS, Biomes.FOREST);
			run.tp(site.getX() + 0.5, site.getY() + 25, site.getZ() + 40.5, 180.0F, 30.0F);
			run.context.waitTicks(40);
			Map<SoldierEntity, Cinema.Spot> places = run.compute(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
				ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
				Map<SoldierEntity, Cinema.Spot> placed = Cinema.spawnRanks(level, player, site, List.of(
						new Rank(UnitType.MILITIA, 14), new Rank(UnitType.SPEARMAN, 14), new Rank(UnitType.ARCHER, 12),
						new Rank(UnitType.CROSSBOWMAN, 12), new Rank(UnitType.KNIGHT, 8)));
				Run.soldier(level, UnitType.RAM, new Vec3(site.getX() + 14.5, site.getY() + 1, site.getZ() - 4.5), player);
				return placed;
			});
			run.context.waitTicks(30); // knights mount their horses
			run.server(server -> Cinema.pose(places));
			run.context.waitTicks(10);
			Cinema.relight(run, site, 70);
			Vec3 mid = Cinema.at(site, 0, 1, -5).add(0, 0.5, 0);
			Cinema.shot(run, "shoot-army-aerial", Cinema.eye(run, site, -9, 5, 11), mid);
			Cinema.shot(run, "shoot-army-hero", Cinema.eye(run, site, 4, 1.3, 8), Cinema.at(site, 0, 2, -4));
			Cinema.shot(run, "shoot-army-wide", Cinema.eye(run, site, 0, 12, 6), Cinema.at(site, 0, 0, -6));
			Cinema.shot(run, "shoot-army-flank", Cinema.eye(run, site, 10, 3.5, 3), Cinema.at(site, -2, 2, -5));
			check(places.size() >= 50, "the army stands in ranks (" + places.size() + " soldiers)");
		} finally {
			Cinema.end(run);
		}
	}

	private static void town(Run run) {
		Cinema.begin(run, 11500);
		try {
			BlockPos site = Cinema.flatSpot(run, 34, Biomes.PLAINS, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.FOREST);
			run.tp(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 180.0F, 0.0F);
			run.context.waitTicks(40);
			run.command("gamemode survival @a");
			BlockPos core = run.compute(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				if (!Playtest.setUp(level, player)) {
					BlockPos centre = dev.felipe.warbands.blueprint.BlueprintPlacer.centerFor(site.offset(0, 0, 16), net.minecraft.core.Direction.NORTH,
							dev.felipe.warbands.blueprint.Blueprint.TOWN_CENTER);
					var why = dev.felipe.warbands.blueprint.BlueprintPlacer.checkServer(level, centre, dev.felipe.warbands.blueprint.Blueprint.TOWN_CENTER, player);
					BlockPos obstacle = dev.felipe.warbands.blueprint.BlueprintPlacer.findObstacle(level, centre, dev.felipe.warbands.blueprint.Blueprint.TOWN_CENTER);
					throw new AssertionError("the Castle town is built: " + why + (obstacle == null ? "" : " by " + level.getBlockState(obstacle) + " at " + obstacle.subtract(centre))
							+ ", player at " + player.blockPosition() + " facing " + player.getDirection());
				}
				ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
				TownCenterBlockEntity town = TownRegistry.nearestOwned(level, player.blockPosition(), player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
				check(town != null, "the town stands");
				return town.getBlockPos();
			});
			run.command("gamemode spectator @a");
			run.context.waitTicks(30);
			Cinema.relight(run, site, 90);
			Vec3 target = Vec3.atCenterOf(core).add(0, 2, 0);
			for (int i = 0; i < 4; i++) {
				double angle = Math.toRadians(45 + i * 90);
				Cinema.shot(run, "shoot-town-" + i, target.add(Math.cos(angle) * 30, 13, Math.sin(angle) * 30), target);
			}
			Cinema.shot(run, "shoot-town-street", target.add(0, 3, 17), target.add(0, 4, 0));
		} finally {
			Cinema.end(run);
		}
	}

	// ---- the staged war ----

	/** One row of a battle line: {@code count} units of {@code type}, {@code depth} blocks behind the front line. */
	private record Row(UnitType type, int count, double depth) {
	}

	private static final List<Row> LINE = List.of(new Row(UnitType.MILITIA, 14, 1.4), new Row(UnitType.SPEARMAN, 14, 4.0),
			new Row(UnitType.ARCHER, 12, 6.6), new Row(UnitType.CROSSBOWMAN, 12, 9.2));

	private interface Factory {
		SoldierEntity make(UnitType type, Vec3 pos);
	}

	/**
	 * Everything about this scene is placed, nothing is left to the AI, so it can't fail the way a real fight can: the
	 * defenders (your army, facing north) hold the line in front of a Castle town, the attackers (raiders, facing south)
	 * meet them in melee across a 3-block gap; archers stand behind each, with arrows frozen in the air above; fires and
	 * blasts are lit the moment a picture is taken, and signal fires smoke over the attackers' camp.
	 */
	private enum WarMode {
		STILLS, FILM, MELEE
	}

	private static void war(Run run, WarMode mode) {
		Cinema.begin(run, 11800);
		try {
			BlockPos site = Cinema.flatSpot(run, 40, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
			run.server(server -> server.overworld().getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server));
			run.tp(site.getX() + 0.5, site.getY() + 30, site.getZ() + 55.5, 180.0F, 30.0F);
			run.context.waitTicks(20);
			Cinema.settle(run);

			Run townRun = new Run(run.context, run.world, site.offset(0, 0, 8));
			Run.Town town = townRun.town(Age.CASTLE);
			List<BlockPos> roofs = new ArrayList<>(List.of(town.center()));
			for (int[] house : new int[][] {{-13, 17}, {13, 17}}) {
				try {
					BlockPos core = run.compute(server -> Run.place(server.overworld(), player(server), townRun.ground.offset(house[0], 0, house[1]), Blueprint.HOUSE));
					roofs.add(core);
				} catch (AssertionError | RuntimeException e) {
					Warbands.LOGGER.warn("[gametest] shoot.war: no room for the house at {}: {}", house[0], e.getMessage());
				}
			}
			run.tp(site.getX() + 0.5, site.getY() + 30, site.getZ() + 55.5, 180.0F, 30.0F);

			Map<SoldierEntity, Cinema.Spot> places = run.compute(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
				ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
				Map<SoldierEntity, Cinema.Spot> placed = new IdentityHashMap<>();
				side(placed, site, +1, 180.0F, (type, pos) -> Run.soldier(level, type, pos, player));
				side(placed, site, -1, 0.0F, (type, pos) -> Run.raider(level, type, pos, Age.CASTLE, null));
				BlockPos ramSpot = site.offset(-11, 1, -9);
				placed.put(Run.raider(level, UnitType.RAM, Vec3.atBottomCenterOf(ramSpot), Age.CASTLE, null),
						new Cinema.Spot(Vec3.atBottomCenterOf(ramSpot), 0.0F));
				for (int[] fire : new int[][] {{-20, -14}, {21, -12}, {-6, -22}, {9, -24}}) {
					BlockPos at = Run.groundAt(level, site.getX() + fire[0], site.getZ() + fire[1]).above();
					level.setBlock(at, Blocks.HAY_BLOCK.defaultBlockState(), 2);
					level.setBlock(at.above(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 2);
				}
				frozenArrows(level, site, +1, 14);
				frozenArrows(level, site, -1, 10);
				return placed;
			});
			run.context.waitTicks(30); // knights mount their horses
			run.server(server -> Cinema.pose(places));
			run.context.waitTicks(10);
			Cinema.relight(run, site, 70);
			Runnable all = () -> {
				run.server(server -> {
					ServerLevel level = server.overworld();
					ignite(level, roofs);
					for (SoldierEntity soldier : places.keySet()) {
						if (soldier.getRandom().nextInt(3) == 0) {
							soldier.swing(InteractionHand.MAIN_HAND);
						}
					}
				});
				run.context.waitTicks(4); // let the fire's light reach the client before the blasts
				run.server(server -> blasts(server.overworld(), site));
			};

			if (mode != WarMode.STILLS) {
				film(run, mode, site, places, roofs);
				return;
			}
			Cinema.shot(run, "shoot-war-aerial", Cinema.eye(run, site, 22, 10, 12), Cinema.at(site, 0, 2, 2), all);
			Cinema.shot(run, "shoot-war-defenders", Cinema.eye(run, site, -3, 2.4, 13), Cinema.at(site, 0, 2.5, -6), all);
			Cinema.shot(run, "shoot-war-raiders", Cinema.eye(run, site, 5, 2.2, -17), Cinema.at(site, 0, 3, 6), all);
			Cinema.shot(run, "shoot-war-clash", Cinema.eye(run, site, -13, 1.7, 0), Cinema.at(site, 2, 1.7, 0), all);
			Cinema.shot(run, "shoot-war-town", Cinema.eye(run, site, 24, 9, 6), Cinema.at(town.center(), 0, 5, 0), all);
			Cinema.shot(run, "shoot-war-overhead", Cinema.eye(run, site, 0, 30, 2), Cinema.at(site, 0, 0, 0), all);
			check(places.size() >= 50, "the war is staged (" + places.size() + " fighters)");
		} finally {
			Cinema.end(run);
		}
	}

	/** The two videos of the war scene: scripted fire and swings, or the AI let loose. */
	private static void film(Run run, WarMode mode, BlockPos site, Map<SoldierEntity, Cinema.Spot> places, List<BlockPos> roofs) {
		run.server(server -> ignite(server.overworld(), roofs));
		List<Cinema.Key> path;
		if (mode == WarMode.FILM) {
			path = List.of(
					new Cinema.Key(Cinema.eye(run, site, 32, 22, 24), Cinema.at(site, 0, 2, 0)),
					new Cinema.Key(Cinema.eye(run, site, 28, 9, 6), Cinema.at(site, 0, 2, 0)),
					new Cinema.Key(Cinema.eye(run, site, 24, 3.2, 1), Cinema.at(site, 2, 2, 0)),
					new Cinema.Key(Cinema.eye(run, site, 21, 2.6, -1), Cinema.at(site, -4, 2, 0)));
		} else {
			run.server(server -> places.keySet().forEach(soldier -> {
				soldier.setNoAi(false);
				soldier.setInvulnerable(false);
				if (soldier.getVehicle() instanceof net.minecraft.world.entity.Mob mount) {
					mount.setNoAi(false);
					mount.setInvulnerable(false);
				}
			}));
			path = List.of(
					new Cinema.Key(Cinema.eye(run, site, -22, 4, 10), Cinema.at(site, 0, 1.8, 0)),
					new Cinema.Key(Cinema.eye(run, site, -15, 2.6, 4), Cinema.at(site, 0, 1.8, 0)),
					new Cinema.Key(Cinema.eye(run, site, -10, 2.4, 0), Cinema.at(site, 3, 1.8, 0)));
		}
		List<SoldierEntity> fighters = new ArrayList<>(places.keySet());
		Cinema.record(run, mode == WarMode.FILM ? "film-war" : "film-melee", 160, path, frame -> run.server(server -> {
			if (mode == WarMode.FILM) {
				for (int i = frame % 5; i < fighters.size(); i += 5) {
					fighters.get(i).swing(InteractionHand.MAIN_HAND);
				}
				if (frame % 24 == 0) {
					blasts(server.overworld(), site);
				}
			}
		}));
		check(places.size() >= 50, "the war is filmed (" + places.size() + " fighters)");
	}

	/** Cuts the trees on the approach to a camp (south side, where the army and the camera stand) so nothing hides the fight. */
	private static void clearTrees(ServerLevel level, BlockPos camp, int half) {
		for (int dx = -40; dx <= 40; dx++) {
			for (int dz = half + 1; dz <= half + 40; dz++) {
				for (int y = -14; y <= 24; y++) {
					BlockPos pos = Run.groundAt(level, camp.getX() + dx, camp.getZ() + dz).offset(0, y, 0);
					var state = level.getBlockState(pos);
					if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** One army of {@link #LINE}: {@code sign} +1 stands north-to-south in front of the town facing north, -1 attacks from the north. */
	private static void side(Map<SoldierEntity, Cinema.Spot> placed, BlockPos site, int sign, float yaw, Factory factory) {
		for (Row row : LINE) {
			for (int i = 0; i < row.count(); i++) {
				double x = site.getX() + 0.5 + (i - (row.count() - 1) / 2.0) * 1.8;
				double z = site.getZ() + 0.5 + sign * row.depth();
				Vec3 pos = new Vec3(x, site.getY() + 1, z);
				placed.put(factory.make(row.type(), pos), new Cinema.Spot(pos, yaw));
			}
		}
		for (int flank : new int[] {-1, 1}) {
			Vec3 pos = new Vec3(site.getX() + 0.5 + flank * 19, site.getY() + 1, site.getZ() + 0.5 + sign * 5.0);
			placed.put(factory.make(UnitType.KNIGHT, pos), new Cinema.Spot(pos, yaw));
		}
	}

	/** Arrows hanging in the air over one army, in an arc from its archers towards the enemy; they drift too slowly to land. */
	private static void frozenArrows(ServerLevel level, BlockPos site, int sign, int count) {
		for (int k = 0; k < count; k++) {
			double x = site.getX() + 0.5 + ((k * 7) % 11 - 5) * 2.2;
			double along = sign * 8.0 - sign * (k % 7) * 3.0;
			double height = 5.0 + 9.0 * Math.sin(Math.PI * (k % 7) / 6.0);
			double slope = Math.cos(Math.PI * (k % 7) / 6.0) * 0.7;
			Vec3 direction = new Vec3(0.0, slope, -sign).normalize();
			Arrow arrow = new Arrow(level, x, site.getY() + 1 + height, site.getZ() + 0.5 + along, new ItemStack(Items.ARROW), null);
			arrow.setNoGravity(true);
			arrow.setDeltaMovement(direction.scale(0.001));
			arrow.snapTo(x, site.getY() + 1 + height, site.getZ() + 0.5 + along,
					(float) Math.toDegrees(-Math.atan2(direction.x, direction.z)), (float) Math.toDegrees(-Math.asin(direction.y)));
			if (k % 3 == 0) {
				arrow.setRemainingFireTicks(20 * 600);
			}
			level.addFreshEntity(arrow);
		}
	}

	/** Fire on top of every roof (skipped where the roof is not there). */
	private static void ignite(ServerLevel level, List<BlockPos> roofs) {
		for (BlockPos roof : roofs) {
			for (int[] d : new int[][] {{0, 0}, {2, 1}, {-2, -1}, {1, -2}}) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, roof.offset(d[0], 0, d[1]));
				level.setBlock(top, Blocks.FIRE.defaultBlockState(), 2);
			}
		}
	}

	/** Explosion clouds, flames, smoke and sparks along the front, as if the siege engines were landing shots. */
	private static void blasts(ServerLevel level, BlockPos site) {
		double[][] at = {{-8, 1, -4.5}, {6, 1.2, -6}, {-2, 6, -12}};
		for (double[] p : at) {
			double x = site.getX() + 0.5 + p[0];
			double y = site.getY() + p[1];
			double z = site.getZ() + 0.5 + p[2];
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, x, y, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 1, z, 14, 1.2, 1.2, 1.2, 0.03);
			level.sendParticles(ParticleTypes.FLAME, true, true, x, y, z, 45, 1.2, 0.8, 1.2, 0.12);
			level.sendParticles(ParticleTypes.LAVA, true, true, x, y, z, 14, 1.0, 0.5, 1.0, 0.0);
		}
	}

	private static void battle(Run run) {
		Cinema.begin(run, 11500);
		try {
			BlockPos site = Cinema.flatSpot(run, 40, Biomes.MEADOW, Biomes.PLAINS, Biomes.CHERRY_GROVE, Biomes.FOREST);
			Run local = new Run(run.context, run.world, site);
			run.tp(site.getX() + 0.5, site.getY() + 30, site.getZ() + 45.5, 180.0F, 30.0F);
			run.context.waitTicks(40);
			Run.Town town = local.town(Age.FEUDAL);
			Cinema.relight(run, town.center(), 60);
			run.server(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
				Vec3 at = Vec3.atBottomCenterOf(town.center().offset(0, 1, 9));
				List<UnitType> types = List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.SPEARMAN,
						UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER);
				for (int i = 0; i < types.size(); i++) {
					Run.soldier(level, types.get(i), at.add((i % 6 - 2.5) * 2.0, 0, (i / 6) * 2.2), player);
				}
				check(Raids.startNow(level, town.core()), "a raid starts");
				Raids.skipWarning(level, town.core());
			});
			run.waitFor(server -> !activeRaiders(server, town.core()).isEmpty(), "the raiders arrive", 200);
			for (int waited = 0; waited < 20 * 90 && !closeCombat(run, town.core()); waited += 10) {
				run.context.waitTicks(10);
			}
			for (int frame = 0; frame < 7; frame++) {
				Vec3[] focus = run.compute(server -> {
					List<SoldierEntity> raiders = activeRaiders(server, town.core());
					return raiders.isEmpty() ? null : new Vec3[] {Cinema.centroid(raiders), Cinema.centroid(army(server))};
				});
				if (focus == null) {
					Warbands.LOGGER.info("[gametest] shoot.battle: the raid ended after {} pictures", frame);
					break;
				}
				Vec3 mid = focus[0].add(focus[1]).scale(0.5).add(0, 1, 0);
				double angle = Math.toRadians(35 + frame * 55);
				double distance = frame % 3 == 2 ? 9 : 17;
				Cinema.shot(run, "shoot-battle-" + frame, mid.add(Math.cos(angle) * distance, frame % 3 == 2 ? 3 : 9, Math.sin(angle) * distance), mid);
				run.context.waitTicks(25);
			}
			try {
				run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), town.core()), "the fight is over", 20 * 60);
			} catch (AssertionError e) {
				Warbands.LOGGER.info("[gametest] shoot.battle: the fight was still going after the pictures; leaving it");
			}
		} finally {
			Cinema.end(run);
		}
	}

	/** True once a raider and one of your soldiers are within 12 blocks of each other. */
	private static boolean closeCombat(Run run, BlockPos core) {
		return run.compute(server -> {
			List<SoldierEntity> mine = army(server);
			return activeRaiders(server, core).stream().anyMatch(r -> mine.stream().anyMatch(s -> s.distanceToSqr(r) < 144.0));
		});
	}

	/**
	 * Builds camps of the tiers that have a home-biome site and storms each with a posed army from the south: the
	 * guards are frozen (no AI) facing the army, arrows hang between them, fires burn on the camp and blasts go off as
	 * the pictures are taken.
	 */
	private static void assault(Run run, boolean film) {
		Cinema.begin(run, 11800);
		try {
			BlockPos home = Cinema.flatSpot(run, 10, Biomes.PLAINS, Biomes.MEADOW, Biomes.FOREST);
			run.server(server -> server.overworld().getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server));
			run.tp(home.getX() + 0.5, home.getY() + 30, home.getZ() + 0.5, 180.0F, 30.0F);
			run.context.waitTicks(30);
			UUID stranger = UUID.randomUUID();
			int taken = 0;
			for (CampTier tier : CampTier.values()) {
				if (film && (taken > 0 || tier != CampTier.FORT && tier != CampTier.KEEP)) {
					continue;
				}
				Camp camp = run.compute(server -> {
					ServerLevel level = server.overworld();
					level.getChunk(home);
					BlockPos site = Camps.siteFor(level, home, tier);
					if (site == null) {
						return null;
					}
					var layout = tier.layouts().get(0);
					Cinema.loadAround(level, site, layout.half() + 40);
					Camps.build(level, Camp.chosen(stranger, home, tier, site, Rotation.NONE, layout.id(), Threat.NORMAL));
					return dev.felipe.warbands.camp.CampRegistry.all(level).stream()
							.filter(c -> c.layoutId().equals(layout.id()) && c.owner().equals(stranger)).findFirst().orElse(null);
				});
				if (camp == null) {
					continue;
				}
				run.context.waitTicks(20);
				int half = camp.layout().half();
				BlockPos c = camp.center();
				BlockPos front = c.offset(0, 0, half + 11);
				run.server(server -> clearTrees(server.overworld(), c, half));
				Map<SoldierEntity, Cinema.Spot> places = run.compute(server -> {
					ServerLevel level = server.overworld();
					ServerPlayer player = player(server);
					AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
					ArmyOrders.set(server, player.getUUID(), Stance.HOLD);
					Map<SoldierEntity, Cinema.Spot> placed = new IdentityHashMap<>();
					UnitType[] rows = {UnitType.SPEARMAN, UnitType.MILITIA, UnitType.ARCHER, UnitType.CROSSBOWMAN, UnitType.KNIGHT};
					int[] counts = {13, 13, 12, 10, 7};
					for (int row = 0; row < rows.length; row++) {
						for (int i = 0; i < counts[row]; i++) {
							double x = front.getX() + 0.5 + (i - (counts[row] - 1) / 2.0) * 1.8;
							double z = front.getZ() + 0.5 + row * 2.2;
							Vec3 pos = new Vec3(x, Run.groundAt(level, (int) Math.floor(x), (int) Math.floor(z)).getY() + 1, z);
							placed.put(Run.soldier(level, rows[row], pos, player), new Cinema.Spot(pos, 180.0F));
						}
					}
					for (UUID id : camp.guards()) {
						if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob guard) {
							guard.setNoAi(true);
							guard.setInvulnerable(true);
							guard.setYRot(0.0F);
							guard.setYHeadRot(0.0F);
							guard.setYBodyRot(0.0F);
						}
					}
					frozenArrows(level, front.offset(0, 0, -2), +1, 16);
					return placed;
				});
				run.context.waitTicks(30);
				run.server(server -> Cinema.pose(places));
				run.context.waitTicks(10);
				Cinema.relight(run, c, half + 40);
				if (film) {
					run.server(server -> {
						ServerLevel level = server.overworld();
						places.keySet().forEach(soldier -> {
							soldier.setNoAi(false);
							soldier.setInvulnerable(false);
							if (soldier.getVehicle() instanceof net.minecraft.world.entity.Mob mount) {
								mount.setNoAi(false);
								mount.setInvulnerable(false);
							}
						});
						for (UUID id : camp.guards()) {
							if (level.getEntity(id) instanceof net.minecraft.world.entity.Mob guard) {
								guard.setNoAi(false);
								guard.setInvulnerable(false);
							}
						}
						ArmyOrders.set(server, player(server).getUUID(), Stance.CHARGE);
					});
					List<Cinema.Key> path = List.of(
							new Cinema.Key(Cinema.at(front, -16, 8, 16), Cinema.at(c, 0, 3, half * 0.5)),
							new Cinema.Key(Cinema.at(front, -12, 4, 6), Cinema.at(c, 0, 3, half - 2)),
							new Cinema.Key(Cinema.at(front, -8, 3, -2), Cinema.at(c, 0, 3, half - 4)));
					Cinema.record(run, "film-assault", 160, path, null);
					taken++;
					continue;
				}
				Runnable fire = () -> run.server(server -> {
					ServerLevel level = server.overworld();
					for (int[] d : new int[][] {{-4, -3}, {3, 2}, {6, -6}, {-7, 4}, {0, 6}}) {
						BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c.offset(d[0], 0, d[1]));
						level.setBlock(top, Blocks.FIRE.defaultBlockState(), 2);
					}
					for (double[] p : new double[][] {{-5, 2, half - 1}, {5, 3, half - 3}, {0, 5, half - 6}}) {
						double x = c.getX() + 0.5 + p[0];
						double y = c.getY() + p[1];
						double z = c.getZ() + 0.5 + p[2];
						level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, x, y, z, 1, 0, 0, 0, 0);
						level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 1, z, 14, 1.2, 1.2, 1.2, 0.03);
						level.sendParticles(ParticleTypes.FLAME, true, true, x, y, z, 45, 1.2, 0.8, 1.2, 0.12);
					}
				});
				String id = tier.serializedName();
				Cinema.shot(run, "shoot-assault-" + id + "-wide", Cinema.at(front, -14, 9, 20), Cinema.at(c, 0, 3, half * 0.4), fire);
				Cinema.shot(run, "shoot-assault-" + id + "-front", Cinema.at(front, 3, 2.5, 7), Cinema.at(c, 0, 3, half - 2), fire);
				Cinema.shot(run, "shoot-assault-" + id + "-flank", Cinema.at(front, 22, 6, -4), Cinema.at(c, 0, 3, half - 4), fire);
				taken++;
			}
			check(taken > 0, "at least one camp kind found a site to storm");
		} finally {
			Cinema.end(run);
		}
	}

	private static void camps(Run run) {
		Cinema.begin(run, 3000);
		try {
			BlockPos home = Cinema.flatSpot(run, 10, Biomes.PLAINS, Biomes.MEADOW, Biomes.FOREST);
			run.tp(home.getX() + 0.5, home.getY() + 30, home.getZ() + 0.5, 180.0F, 30.0F);
			run.context.waitTicks(30);
			UUID stranger = UUID.randomUUID();
			for (CampTier tier : CampTier.values()) {
				Camp camp = run.compute(server -> {
					ServerLevel level = server.overworld();
					level.getChunk(home);
					BlockPos site = Camps.siteFor(level, home, tier);
					if (site == null) {
						return null;
					}
					var layout = tier.layouts().get(0);
					Cinema.loadAround(level, site, layout.half() + 24);
					Camps.build(level, Camp.chosen(stranger, home, tier, site, Rotation.NONE, layout.id(), Threat.NORMAL));
					return dev.felipe.warbands.camp.CampRegistry.all(level).stream()
							.filter(c -> c.layoutId().equals(layout.id()) && c.owner().equals(stranger)).findFirst().orElse(null);
				});
				if (camp == null) {
					Warbands.LOGGER.info("[gametest] shoot.camps: no home-biome site for {} near here, skipped", tier.serializedName());
					continue;
				}
				run.context.waitTicks(15);
				int half = camp.layout().half();
				BlockPos c = camp.center();
				Cinema.shot(run, "shoot-camp-" + tier.serializedName(), Cinema.at(c, half * 1.3 + 12, half * 0.9 + 14, half * 1.5 + 16),
						Cinema.at(c, 0, 3, 0));
			}
		} finally {
			Cinema.end(run);
		}
	}
}
