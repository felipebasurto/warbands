package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.army;
import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.core;
import static dev.felipe.warbands.gametest.Run.place;
import static dev.felipe.warbands.gametest.Run.player;
import static dev.felipe.warbands.gametest.Run.soldier;
import static dev.felipe.warbands.gametest.Run.townCenter;
import static dev.felipe.warbands.gametest.Run.training;
import static dev.felipe.warbands.gametest.Run.trainingCore;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.client.GuideScreen;
import dev.felipe.warbands.client.Handbook;
import dev.felipe.warbands.client.TownCenterScreen;
import dev.felipe.warbands.entity.ArcherEntity;
import dev.felipe.warbands.entity.CrossbowmanEntity;
import dev.felipe.warbands.entity.KnightEntity;
import dev.felipe.warbands.entity.MilitiaEntity;
import dev.felipe.warbands.entity.RamEntity;
import dev.felipe.warbands.entity.SmithEntity;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.SpearmanEntity;
import dev.felipe.warbands.entity.StableHorseEntity;
import dev.felipe.warbands.entity.WarhorseEntity;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.AgeRegistry;
import dev.felipe.warbands.town.ArmyRegistry;
import dev.felipe.warbands.town.BlacksmithBlockEntity;
import dev.felipe.warbands.town.HouseRegistry;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownCenterBlock;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.UnitType;
import dev.felipe.warbands.town.Upgrade;
import dev.felipe.warbands.town.UpgradeRegistry;
import dev.felipe.warbands.town.WatchTowerBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The core loop (blueprints, training, combat, guidance), the ages, the later buildings, and the photo gallery. */
final class CoreScenarios {
	private CoreScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("core.playtest", "/warbands playtest: a Castle town with every building, an army, the kit, and the camp teleport (photo)",
				CoreScenarios::playtest));
		all.add(new Scenario("core.blueprints", "placement, terrain clearing, stone costs, town spacing, protected blocks, Town Center break rules",
				CoreScenarios::blueprints));
		all.add(new Scenario("core.training", "stockpile deposits, training three units, their owner and gear, costs, first advancements",
				CoreScenarios::trainUnits));
		all.add(new Scenario("core.combat", "soldiers attack what their owner hits, walk behind and teleport past 24 blocks",
				CoreScenarios::combat));
		all.add(new Scenario("core.guidance", "Town Center menu, handbook and advancement tab open (screenshots)", CoreScenarios::guidance));
		all.add(new Scenario("ages.progression", "Village -> Feudal -> Castle: costs, gating, gear, Barracks/Range/Stable, later units, stable horses",
				CoreScenarios::progression));
		all.add(new Scenario("ages.buildings", "Blacksmith + Forging, smith scenery, Watch Tower posting, Siege Workshop ram and its size",
				CoreScenarios::buildings));
		all.add(new Scenario("gallery.town", "photos: Town Center and house, then the Town Center at each age", CoreScenarios::galleryTown));
		all.add(new Scenario("gallery.army", "photos: one of each Castle unit and the ram", CoreScenarios::galleryArmy));
		all.add(new Scenario("gallery.buildings", "photos: every building, stable horses, the smith, and every building menu",
				CoreScenarios::galleryBuildings));
		all.add(new Scenario("gallery.guide", "photos: every guide page", CoreScenarios::galleryGuide));
		all.add(new Scenario("lang", "en_us and es_es have the same keys; every blueprint says what it's for; every handbook page fits", CoreScenarios::lang));
	}

	// ---- core ----

	private static void blueprints(Run run) {
		BlockPos ground = run.ground;
		check(Run.joinedWithHandbook, "new players get the handbook");
		BlockPos[] centers = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos clicked = ground.offset(0, 0, 14);
			player.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 15.5, 180.0F, 10.0F); // yaw 180 = facing north
			BlockPos center = BlueprintPlacer.centerFor(clicked, Direction.NORTH, Blueprint.TOWN_CENTER);
			check(center.equals(ground.offset(0, 0, 8)), "town center goes in front of the clicked block");

			// Uneven, cluttered terrain: a dirt mound, a stone pillar and a pit under the site.
			level.setBlockAndUpdate(center.offset(2, 1, 1), Blocks.DIRT.defaultBlockState());
			level.setBlockAndUpdate(center.offset(2, 2, 1), Blocks.GRASS_BLOCK.defaultBlockState());
			level.setBlockAndUpdate(center.offset(-3, 1, -2), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(center.offset(-3, 2, -2), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(center.offset(4, -1, 4), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(center.offset(4, -2, 4), Blocks.AIR.defaultBlockState());

			level.setBlockAndUpdate(center.offset(1, 1, 1), Blocks.CHEST.defaultBlockState());
			check(BlueprintPlacer.check(level, center, Blueprint.TOWN_CENTER, player) == BlueprintPlacer.Problem.BLOCKED,
					"a chest (built block) blocks the site");
			level.setBlockAndUpdate(center.offset(1, 1, 1), Blocks.AIR.defaultBlockState());
			BlockPos obstacle = BlueprintPlacer.findObstacle(level, center, Blueprint.TOWN_CENTER);
			check(obstacle == null, "no obstacle expected, found " + (obstacle == null ? "" : level.getBlockState(obstacle) + " at " + obstacle.subtract(center)));
			check(BlueprintPlacer.check(level, center, Blueprint.TOWN_CENTER, player) == BlueprintPlacer.Problem.NONE,
					"natural terrain doesn't block the site");
			check(BlueprintPlacer.place(level, center, Blueprint.TOWN_CENTER, player), "town center placed");
			check(level.getBlockState(center.offset(4, -1, 4)).is(Blocks.COBBLESTONE), "pit under the site was filled");
			var moundCell = level.getBlockState(center.offset(2, 2, 1));
			check(!moundCell.is(Blocks.DIRT) && !moundCell.is(Blocks.GRASS_BLOCK),
					"natural mound was replaced by the town, got " + moundCell);

			BlockPos farHouse = BlueprintPlacer.centerFor(ground.offset(120, 0, 14), Direction.NORTH, Blueprint.HOUSE);
			check(BlueprintPlacer.checkServer(level, farHouse, Blueprint.HOUSE, player) == BlueprintPlacer.Problem.NEEDS_TOWN,
					"houses need a Town Center nearby");
			BlockPos house = BlueprintPlacer.centerFor(ground.offset(14, 0, 14), Direction.NORTH, Blueprint.HOUSE);
			check(BlueprintPlacer.checkServer(level, house, Blueprint.HOUSE, player) == BlueprintPlacer.Problem.NEED_STONE,
					"houses cost stone from the Town Center");
			TownCenterBlockEntity town = townCenter(level, center.above());
			town.deposit(new ItemStack(Items.COBBLESTONE, 64));
			check(BlueprintPlacer.checkServer(level, house, Blueprint.HOUSE, player) == BlueprintPlacer.Problem.NONE, "house fits near the town center");
			check(BlueprintPlacer.place(level, house, Blueprint.HOUSE, player), "house placed");
			check(town.stock(Resource.STONE) == 64 - Blueprint.HOUSE.stoneCost(), "house took its stone");
			check(BlueprintPlacer.check(level, center, Blueprint.TOWN_CENTER, player) == BlueprintPlacer.Problem.BLOCKED,
					"can't build on top of an existing building");
			BlockPos midHouse = BlueprintPlacer.centerFor(ground.offset(0, 0, 58), Direction.NORTH, Blueprint.HOUSE);
			check(Math.sqrt(midHouse.distSqr(center)) > 40, "test site is beyond the old 32-block range");
			check(BlueprintPlacer.checkServer(level, midHouse, Blueprint.HOUSE, player) != BlueprintPlacer.Problem.NEEDS_TOWN,
					"buildings may go up to 64 blocks from the Town Center");
			BlockPos nearTown = BlueprintPlacer.centerFor(ground.offset(0, 0, 90), Direction.NORTH, Blueprint.TOWN_CENTER);
			check(BlueprintPlacer.checkServer(level, nearTown, Blueprint.TOWN_CENTER, player) == BlueprintPlacer.Problem.TOWN_TOO_CLOSE,
					"no second Town Center within 128 blocks");
			BlockPos farTown = BlueprintPlacer.centerFor(ground.offset(0, 0, 160), Direction.NORTH, Blueprint.TOWN_CENTER);
			check(BlueprintPlacer.checkServer(level, farTown, Blueprint.TOWN_CENTER, player) == BlueprintPlacer.Problem.NONE,
					"a second Town Center 150 blocks away is fine");
			check(UnitType.RAM.icon() == ModItems.BATTERING_RAM, "the ram's icon is the Battering Ram, not a door");
			for (Blueprint blueprint : Blueprint.values()) {
				Vec3i size = level.getStructureManager().get(blueprint.structureId()).orElseThrow().getSize();
				check(size.getX() == blueprint.size() && size.getZ() == blueprint.size() && size.getY() == blueprint.height(),
						blueprint.getName() + " structure is " + blueprint.size() + "x" + blueprint.height() + "x" + blueprint.size() + " (file: " + size + ")");
			}
			return new BlockPos[] {center, house};
		});
		BlockPos center = centers[0];
		BlockPos houseCenter = centers[1];
		BlockPos corePos = center.above();

		run.server(server -> {
			ServerLevel level = server.overworld();
			check(level.getBlockState(corePos).is(ModBlocks.TOWN_CENTER), "town center block at the middle");
			check(level.getBlockState(center.offset(0, 1, 4)).isAir(), "entrance on the south side");
			check(level.getBlockState(houseCenter.offset(1, 1, 0)).is(ModBlocks.HEARTH), "hearth inside the house");
			check(HouseRegistry.countNear(level, corePos, TownCenterBlockEntity.HOUSE_RADIUS, player(server).getUUID()) == 1, "house registered");
			check(HouseRegistry.countNear(level, corePos, TownCenterBlockEntity.HOUSE_RADIUS, UUID.randomUUID()) == 0, "houses only count for their owner");
			check(townCenter(level, corePos).isOwner(player(server)), "placer owns the town center");

			// Structure blocks can't be mined for materials.
			BlockPos wall = center.offset(-4, 2, 0);
			check(level.getBlockState(wall).is(Blocks.STONE_BRICKS) || level.getBlockState(wall).is(Blocks.MOSSY_STONE_BRICKS)
					|| level.getBlockState(wall).is(Blocks.CRACKED_STONE_BRICKS), "west wall is stone bricks");
			player(server).gameMode.destroyBlock(wall);
			check(level.getBlockState(wall).isAir(), "structure block broken");
			check(level.getEntitiesOfClass(ItemEntity.class, new AABB(wall).inflate(3)).isEmpty(), "structure block dropped nothing");

			// Explosions, lost support and fluids go through the loot table: still nothing.
			BlockPos roof = center.offset(0, 6, 0);
			while (!StructureBlocks.contains(level, roof) && roof.getY() > center.getY()) {
				roof = roof.below();
			}
			check(StructureBlocks.contains(level, roof), "found a protected block to test drops");
			check(Block.getDrops(level.getBlockState(roof), level, roof, null).isEmpty(), "protected blocks have no loot");

			// The Town Center core only breaks for its owner while sneaking: a stray click can't end the town.
			ServerPlayer owner = player(server);
			BlockState coreState = level.getBlockState(corePos);
			check(coreState.getDestroyProgress(owner, level, corePos) == 0.0F, "mining the Town Center makes no progress unless sneaking");
			owner.gameMode.destroyBlock(corePos);
			check(level.getBlockState(corePos).is(ModBlocks.TOWN_CENTER)
					&& TownRegistry.nearestOwned(level, corePos, owner.getUUID(), 1) != null, "the Town Center survives a break without sneaking");
			owner.setShiftKeyDown(true);
			check(coreState.getDestroyProgress(owner, level, corePos) > 0.0F, "sneaking mines the Town Center");
			check(TownCenterBlock.beforeBreak(level, owner, corePos, coreState, level.getBlockEntity(corePos)), "the owner may break it while sneaking");
			owner.setShiftKeyDown(false);
		});
	}

	private static void trainUnits(Run run) {
		Run.Town town = run.placeTown();
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			TownCenterBlockEntity center = townCenter(level, town.core());
			center.deposit(new ItemStack(Items.COBBLESTONE, 64));
			place(level, player, run.ground.offset(14, 0, 14), Blueprint.HOUSE);
			check(center.deposit(new ItemStack(Items.BREAD, 64)).isEmpty(), "bread accepted");
			check(center.deposit(new ItemStack(Items.OAK_LOG, 16)).isEmpty(), "logs accepted");
			check(center.deposit(new ItemStack(Items.GOLD_INGOT, 10)).isEmpty(), "gold accepted");
			check(center.deposit(new ItemStack(Items.ROTTEN_FLESH, 5)).getCount() == 5, "rotten flesh refused");
			check(center.train(player, UnitType.MILITIA), "train militia 1");
			check(center.train(player, UnitType.MILITIA), "train militia 2");
			check(center.train(player, UnitType.ARCHER), "train archer");
		});
		run.fastForward(town.core(), 3 * 30 * 20);
		run.waitFor(server -> ArmyRegistry.count(server, player(server).getUUID()) == 3, "3 soldiers trained");
		run.server(server -> {
			List<SoldierEntity> army = army(server);
			check(army.size() == 3, "three soldiers in the world, got " + army.size());
			check(army.stream().filter(s -> s instanceof MilitiaEntity).count() == 2, "two militia");
			check(army.stream().filter(s -> s instanceof ArcherEntity).count() == 1, "one archer");
			for (SoldierEntity soldier : army) {
				check(soldier.isTame() && player(server).getUUID().equals(soldier.ownerId()), "soldier owned by player");
				check(!soldier.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "soldier armed");
				check(SoldierEntity.isFriendlyFire(player(server), soldier), "soldiers can't hurt their owner");
			}
			TownCenterBlockEntity center = townCenter(server.overworld(), town.core());
			// 320 food - 2*60, 64 wood - 25, 90 gold - 2*20 - 45
			check(center.stock(Resource.FOOD) == 200 && center.stock(Resource.WOOD) == 39 && center.stock(Resource.GOLD) == 5, "costs were paid");
			for (String step : List.of(WarbandsAdvancements.FOUND_TOWN, WarbandsAdvancements.HOUSING,
					WarbandsAdvancements.FIRST_RECRUIT, WarbandsAdvancements.ARCHER)) {
				check(Run.hasAdvancement(server, step), "advancement " + step);
			}
		});
	}

	private static void combat(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 12.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			for (UnitType type : List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.ARCHER)) {
				soldier(level, type, player.position().add(level.getRandom().nextInt(3) - 1, 0, 2), player);
			}
		});
		run.context.waitTicks(30);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
			zombie.snapTo(player.getX() + 4, player.getY(), player.getZ() + 4, 0, 0);
			zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.COMMAND, null);
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // don't burn in daylight
			level.addFreshEntity(zombie);
			player.attack(zombie);
		});
		run.waitFor(server -> server.overworld().getEntitiesOfClass(Zombie.class, new AABB(player(server).blockPosition()).inflate(40)).isEmpty(),
				"soldiers killed the zombie");

		// Soldiers walk up to 24 blocks behind, then teleport.
		Map<UUID, Vec3> before = run.compute(server -> army(server).stream().collect(Collectors.toMap(SoldierEntity::getUUID, SoldierEntity::position)));
		run.tp(ground.getX() - 19.5, ground.getY() + 1, ground.getZ() + 12.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		// None may jump across the map in half a second. Only soldiers that started well inside 24 blocks count.
		String jumped = run.compute(server -> {
			Vec3 player = player(server).position();
			StringBuilder out = new StringBuilder();
			for (SoldierEntity soldier : army(server)) {
				Vec3 start = before.getOrDefault(soldier.getUUID(), soldier.position());
				double moved = soldier.position().distanceTo(start);
				if (start.distanceTo(player) < 20 && moved >= 6) {
					out.append(String.format(" %s from %.1f blocks moved %.1f;", soldier.getName().getString(), start.distanceTo(player), moved));
				}
			}
			return out.toString();
		});
		check(jumped.isEmpty(), "no instant teleport under 24 blocks" + jumped);
		run.tp(ground.getX() + 50.5, ground.getY() + 1, ground.getZ() + 14.5, 180.0F, 0.0F);
		run.waitFor(server -> army(server).size() == 3 && army(server).stream().allMatch(s -> s.distanceTo(player(server)) < 12),
				"soldiers caught up with the player");
	}

	private static void guidance(Run run) {
		Run.Town town = run.placeTown();
		run.tp(town.center().getX() + 0.5, town.center().getY() + 1, town.center().getZ() + 2.5, 180.0F, 30.0F);
		run.context.waitTicks(5);
		run.server(server -> player(server).openMenu(townCenter(server.overworld(), town.core())));
		run.context.waitForScreen(TownCenterScreen.class);
		run.context.waitTicks(10);
		run.cleanShot("warbands-menu");
		run.context.setScreen(() -> null);
		run.context.runOnClient(minecraft -> Handbook.open());
		run.context.waitTicks(5);
		run.cleanShot("warbands-handbook");
		run.context.runOnClient(minecraft -> {
			var advancements = minecraft.player.connection.getAdvancements();
			advancements.setSelectedTab(advancements.get(Warbands.id("root")), false);
			minecraft.setScreen(new AdvancementsScreen(advancements));
		});
		run.context.waitTicks(10);
		run.cleanShot("warbands-advancements");
		run.context.setScreen(() -> null);
	}

	// ---- ages and later buildings ----

	/** Village -> Feudal -> Castle with every training building, and one of each later unit trained. */
	private static void progression(Run run) {
		BlockPos ground = run.ground;
		Run.Town town = run.placeTown();
		BlockPos corePos = town.core();
		run.tp(ground.getX() + 30.5, ground.getY() + 1, ground.getZ() + 30.5, 180.0F, 0.0F);
		run.context.waitTicks(5);
		run.stockUp(corePos);
		UUID militia = run.compute(server -> soldier(server.overworld(), UnitType.MILITIA, player(server).position(), player(server)).getUUID());
		run.server(server -> {
			ServerLevel level = server.overworld();
			TownCenterBlockEntity center = townCenter(level, corePos);
			ServerPlayer player = player(server);
			BlockPos barracks = BlueprintPlacer.centerFor(ground.offset(-14, 0, 14), Direction.NORTH, Blueprint.BARRACKS);
			check(BlueprintPlacer.checkServer(level, barracks, Blueprint.BARRACKS, player) == BlueprintPlacer.Problem.TOO_EARLY,
					"the Barracks needs the Feudal Age");
			check(!center.tryAdvance(player, new SimpleContainer(new ItemStack(Items.DIAMOND))), "Feudal needs a diamond block");
			int stone = center.stock(Resource.STONE);
			check(center.tryAdvance(player, new SimpleContainer(new ItemStack(ModItems.BANDIT_TROPHY))), "Feudal research started");
			check(center.stock(Resource.STONE) == stone - Age.FEUDAL.cost(Resource.STONE), "Feudal took its stone");
		});
		run.fastForward(corePos, Age.FEUDAL.researchTicks());
		run.waitFor(server -> townCenter(server.overworld(), corePos).age() == Age.FEUDAL, "reached the Feudal Age");
		// Soldiers re-equip once a second. (If an earlier scenario reached Castle, the army is already Castle.)
		run.waitFor(server -> AgeRegistry.get(server, player(server).getUUID()) != Age.FEUDAL
				|| server.overworld().getEntity(militia) instanceof SoldierEntity m && m.getMainHandItem().is(Items.IRON_SWORD),
				"Feudal militia carry iron swords", 60);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			check(AgeRegistry.get(server, player.getUUID()).atLeast(Age.FEUDAL), "the army is at least Feudal");
			place(level, player, ground.offset(-14, 0, 14), Blueprint.BARRACKS);
			place(level, player, ground.offset(0, 0, -10), Blueprint.ARCHERY_RANGE);
			check(townCenter(level, corePos).tryAdvance(player, new SimpleContainer(new ItemStack(ModItems.FORT_TROPHY))), "Castle research started");
		});
		run.fastForward(corePos, Age.CASTLE.researchTicks());
		run.waitFor(server -> townCenter(server.overworld(), corePos).age() == Age.CASTLE, "reached the Castle Age");
		BlockPos[] cores = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos stable = place(level, player, ground.offset(14, 0, -10), Blueprint.STABLE);
			BlockPos barracks = trainingCore(level, BlueprintPlacer.centerFor(ground.offset(-14, 0, 14), Direction.NORTH, Blueprint.BARRACKS), Blueprint.BARRACKS);
			BlockPos range = trainingCore(level, BlueprintPlacer.centerFor(ground.offset(0, 0, -10), Direction.NORTH, Blueprint.ARCHERY_RANGE), Blueprint.ARCHERY_RANGE);
			check(training(level, barracks).train(player, UnitType.SPEARMAN), "train spearman");
			check(training(level, range).train(player, UnitType.CROSSBOWMAN), "train crossbowman");
			check(training(level, stable).train(player, UnitType.KNIGHT), "train knight");
			check(!training(level, stable).train(player, UnitType.SPEARMAN), "a Stable can't train spearmen");
			List<StableHorseEntity> horses = level.getEntitiesOfClass(StableHorseEntity.class, new AABB(stable).inflate(5));
			check(horses.size() == 2, "two horses in the Stable, got " + horses.size());
			for (StableHorseEntity horse : horses) {
				check(level.noCollision(horse), "stable horse stands in a free stall at " + horse.blockPosition().subtract(stable));
				check(horse.isInvulnerable() && !horse.isPushable() && !horse.canBeLeashed() && !horse.isBaby(),
						"stable horses are adult scenery (invulnerable, not pushable or leashable)");
				check(horse.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS && !horse.isVehicle(),
						"stable horses can't be ridden or tamed");
			}
			return new BlockPos[] {barracks, range, stable};
		});
		for (BlockPos core : cores) {
			run.fastForward(core, UnitType.KNIGHT.trainTicks());
		}
		run.waitFor(server -> ArmyRegistry.count(server, player(server).getUUID()) == 4, "3 later units trained");
		// Knights mount on their first ticks and everyone re-equips once a second.
		run.waitFor(server -> army(server).stream().anyMatch(s -> s instanceof KnightEntity && s.getVehicle() instanceof WarhorseEntity)
				&& army(server).stream().filter(s -> s instanceof MilitiaEntity).allMatch(s -> s.getMainHandItem().is(Items.DIAMOND_SWORD)),
				"the Castle army is mounted and re-armed", 60);
		run.server(server -> {
			List<SoldierEntity> army = army(server);
			check(army.stream().anyMatch(s -> s instanceof SpearmanEntity && s.getMainHandItem().is(Items.DIAMOND_SPEAR)), "Castle spearman carries a diamond spear");
			check(army.stream().anyMatch(s -> s instanceof KnightEntity && s.getVehicle() instanceof WarhorseEntity), "the knight rides a warhorse");
			for (SoldierEntity knight : army.stream().filter(s -> s instanceof KnightEntity).toList()) {
				double seat = knight.getY() - knight.getVehicle().getY();
				check(seat > 0.5 && seat < 1.0, "the knight sits in the saddle (feet " + String.format("%.2f", seat) + " above the horse's)");
			}
			check(army.stream().filter(s -> s instanceof MilitiaEntity).allMatch(s -> s.getMainHandItem().is(Items.DIAMOND_SWORD)),
					"Castle militia carry diamond swords");
			server.overworld().destroyBlock(cores[2], false);
		});
		run.waitFor(server -> server.overworld().getEntitiesOfClass(StableHorseEntity.class, new AABB(cores[2]).inflate(6)).isEmpty(),
				"stable horses leave with the Stable", 100);
	}

	/** The Blacksmith, the Watch Tower and the Siege Workshop, on a Castle town. */
	private static void buildings(Run run) {
		BlockPos ground = run.ground;
		run.town(Age.CASTLE);
		run.tp(ground.getX() + 30.5, ground.getY() + 1, ground.getZ() + 30.5, 180.0F, 0.0F);
		run.context.waitTicks(5);
		BlockPos[] cores = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos smithy = core(level, place(level, player, ground.offset(-14, 0, -10), Blueprint.BLACKSMITH), Blueprint.BLACKSMITH, BlacksmithBlockEntity.class);
			BlockPos tower = core(level, place(level, player, ground.offset(14, 0, 26), Blueprint.WATCH_TOWER), Blueprint.WATCH_TOWER, WatchTowerBlockEntity.class);
			BlockPos workshop = place(level, player, ground.offset(-14, 0, 26), Blueprint.SIEGE_WORKSHOP);
			check(training(level, workshop).train(player, UnitType.RAM), "train battering ram");
			BlacksmithBlockEntity blacksmith = (BlacksmithBlockEntity) level.getBlockEntity(smithy);
			check(blacksmith.research(player, Upgrade.FORGING), "Forging research started");
			check(!UpgradeRegistry.has(server, player.getUUID(), Upgrade.FORGING), "research takes time: not owned yet");
			check(!blacksmith.research(player, Upgrade.FLETCHING), "one research at a time");
			check(!blacksmith.research(player, Upgrade.BLAST_FORGING), "Blast Forging needs Forging first");
			for (int i = 0; i < Upgrade.FORGING.researchTicks(); i++) {
				blacksmith.serverTick(level);
			}
			check(UpgradeRegistry.has(server, player.getUUID(), Upgrade.FORGING), "Forging done after 40 s");
			BlockPos second = BlueprintPlacer.centerFor(ground.offset(-14, 0, 10), Direction.NORTH, Blueprint.BLACKSMITH);
			check(BlueprintPlacer.checkServer(level, second, Blueprint.BLACKSMITH, player) == BlueprintPlacer.Problem.ONE_PER_TOWN,
					"a second Blacksmith for the same town is refused");
			List<SmithEntity> smiths = level.getEntitiesOfClass(SmithEntity.class, new AABB(smithy).inflate(4));
			check(smiths.size() == 1, "one smith in the Blacksmith, got " + smiths.size());
			Entity smith = smiths.getFirst();
			check(!(smith instanceof AbstractVillager), "the smith is not a villager");
			check(level.noCollision(smith) && smith.isInvulnerable() && !smith.isPushable(), "the smith stands free, invulnerable and unpushable");
			for (UnitType type : List.of(UnitType.ARCHER, UnitType.CROSSBOWMAN, UnitType.ARCHER, UnitType.CROSSBOWMAN)) {
				soldier(level, type, new Vec3(tower.getX() + 3.5, tower.getY(), tower.getZ() + 4.5), player);
			}
			WatchTowerBlockEntity watch = (WatchTowerBlockEntity) level.getBlockEntity(tower);
			for (int i = 1; i <= 4; i++) {
				check(watch.post(player), "posted ranged soldier " + i + " of 4 (every spot fills)");
			}
			return new BlockPos[] {smithy, tower, workshop};
		});
		run.fastForward(cores[2], UnitType.RAM.trainTicks());
		run.waitFor(server -> army(server).stream().anyMatch(s -> s instanceof RamEntity), "battering ram trained");
		run.server(server -> {
			SoldierEntity ram = army(server).stream().filter(s -> s instanceof RamEntity).findFirst().orElseThrow();
			check(Math.abs(ram.getScale() - 1.1F) < 0.01F && ram.getBbWidth() > 1.5F && ram.getBbWidth() < 1.6F,
					"the ram is 1.1x the model and fits 2-wide gates (" + ram.getBbWidth() + ")");
			server.overworld().destroyBlock(cores[0], false);
		});
		run.waitFor(server -> server.overworld().getEntitiesOfClass(SmithEntity.class, new AABB(cores[0]).inflate(6)).isEmpty(),
				"the smith leaves with the Blacksmith", 100);
	}

	// ---- gallery ----

	private static void galleryTown(Run run) {
		BlockPos ground = run.ground;
		Run.Town town = run.placeTown();
		BlockPos center = town.center();
		BlockPos house = run.compute(server -> {
			townCenter(server.overworld(), town.core()).deposit(new ItemStack(Items.COBBLESTONE, 64));
			return place(server.overworld(), player(server), ground.offset(14, 0, 14), Blueprint.HOUSE);
		});
		run.lookAt(center.offset(15, 9, 21), Vec3.atCenterOf(center.offset(0, 7, 0)));
		run.screenshot("warbands-town-center");
		run.lookAt(house.offset(-8, 5, 11), Vec3.atCenterOf(house.offset(0, 4, 0)));
		run.screenshot("warbands-house");
		run.command("effect give @p minecraft:slow_falling 60 0 true");
		run.lookAt(center.offset(-22, 16, -20), Vec3.atCenterOf(center.offset(5, 3, 5)));
		run.screenshot("warbands-town-back");
		run.lookAt(center.offset(-2, 2, 2), Vec3.atCenterOf(center.offset(0, 1, -3)));
		run.screenshot("warbands-town-inside");
		run.stockUp(town.core());
		run.advance(town.core(), Age.FEUDAL);
		run.lookAt(center.offset(15, 9, 21), Vec3.atCenterOf(center.offset(0, 7, 0)));
		run.screenshot("warbands-town-feudal");
		run.advance(town.core(), Age.CASTLE);
		run.lookAt(center.offset(15, 9, 21), Vec3.atCenterOf(center.offset(0, 7, 0)));
		run.screenshot("warbands-town-castle");
	}

	private static void galleryArmy(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 0.0F, 15.0F);
		run.context.waitTicks(10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
			List<UnitType> order = List.of(UnitType.MILITIA, UnitType.ARCHER, UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.KNIGHT);
			for (int i = 0; i < order.size(); i++) {
				soldier(level, order.get(i), new Vec3(player.getX() - 4.4 + i * 2.2, player.getY(), player.getZ() + 5.5), player);
			}
			soldier(level, UnitType.RAM, new Vec3(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 26.5), player);
		});
		run.context.waitTicks(20); // knights mount their horses
		run.server(server -> {
			ServerPlayer player = player(server);
			List<SoldierEntity> army = army(server);
			List<Class<? extends SoldierEntity>> order = List.of(MilitiaEntity.class, ArcherEntity.class, SpearmanEntity.class,
					CrossbowmanEntity.class, KnightEntity.class);
			for (int i = 0; i < order.size(); i++) {
				SoldierEntity soldier = army.stream().filter(order.get(i)::isInstance).findFirst().orElseThrow();
				soldier.setNoAi(true);
				Entity body = soldier.getVehicle() != null ? soldier.getVehicle() : soldier;
				if (body instanceof Mob mob) {
					mob.setNoAi(true);
				}
				body.snapTo(player.getX() - 4.4 + i * 2.2, player.getY(), player.getZ() + 5.5, 180.0F, 0.0F);
				soldier.setYHeadRot(180.0F);
				soldier.setYBodyRot(180.0F);
			}
		});
		run.context.waitTicks(10);
		run.cleanShot("warbands-army-castle");
		BlockPos ramSpot = ground.offset(0, 1, 26);
		run.server(server -> {
			SoldierEntity ram = army(server).stream().filter(s -> s instanceof RamEntity).findFirst().orElseThrow();
			ram.setNoAi(true);
			ram.snapTo(ramSpot.getX() + 0.5, ramSpot.getY(), ramSpot.getZ() + 0.5, 140.0F, 0.0F);
			ram.setYHeadRot(140.0F);
			ram.setYBodyRot(140.0F);
		});
		run.lookAt(ramSpot.offset(2, 1, 3), Vec3.atBottomCenterOf(ramSpot).add(0, 0.6, 0));
		run.context.waitTicks(10);
		run.cleanShot("warbands-ram");
		run.lookAt(ramSpot.offset(0, 0, -5), Vec3.atBottomCenterOf(ramSpot).add(0, 0.7, 0));
		run.server(server -> army(server).stream().filter(s -> s instanceof RamEntity).forEach(ram -> {
			ram.setYRot(0.0F);
			ram.setYBodyRot(0.0F);
			ram.setYHeadRot(0.0F);
		}));
		run.context.waitTicks(10);
		run.cleanShot("warbands-ram-side");
	}

	/** A Castle town with every building: the fixture for the building photos and menus. */
	static BlockPos[] everyBuilding(Run run) {
		BlockPos ground = run.ground;
		run.town(Age.CASTLE);
		run.tp(ground.getX() + 30.5, ground.getY() + 1, ground.getZ() + 30.5, 180.0F, 0.0F);
		run.context.waitTicks(5);
		return run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos barracks = place(level, player, ground.offset(-14, 0, 14), Blueprint.BARRACKS);
			BlockPos range = place(level, player, ground.offset(0, 0, -10), Blueprint.ARCHERY_RANGE);
			BlockPos stable = place(level, player, ground.offset(14, 0, -10), Blueprint.STABLE);
			BlockPos smithy = core(level, place(level, player, ground.offset(-14, 0, -10), Blueprint.BLACKSMITH), Blueprint.BLACKSMITH, BlacksmithBlockEntity.class);
			BlockPos tower = core(level, place(level, player, ground.offset(14, 0, 26), Blueprint.WATCH_TOWER), Blueprint.WATCH_TOWER, WatchTowerBlockEntity.class);
			BlockPos workshop = place(level, player, ground.offset(-14, 0, 26), Blueprint.SIEGE_WORKSHOP);
			return new BlockPos[] {smithy, tower, barracks, range, stable, workshop};
		});
	}

	private static void galleryBuildings(Run run) {
		BlockPos[] cores = everyBuilding(run);
		BlockPos corePos = run.ground.offset(0, 1, 8);
		run.command("effect give @p minecraft:slow_falling 60 0 true");
		String[] names = {"blacksmith", "barracks", "stable", "siege-workshop"};
		BlockPos[] shown = {cores[0], cores[2], cores[4], cores[5]};
		for (int i = 0; i < names.length; i++) {
			run.lookAt(shown[i].offset(8, 4, 11), Vec3.atCenterOf(shown[i].offset(0, 2, 0)));
			run.screenshot("warbands-building-" + names[i]);
		}
		run.lookAt(cores[1].offset(9, 6, 9), Vec3.atCenterOf(cores[1].offset(0, 6, 0)));
		run.screenshot("warbands-building-watch-tower");
		run.lookAt(cores[4].offset(0, 0, 3), Vec3.atCenterOf(cores[4].offset(0, 0, -1)));
		run.screenshot("warbands-stable-horses");
		run.lookAt(cores[0].offset(0, 0, 2), Vec3.atCenterOf(cores[0].offset(-1, 1, 0)));
		run.screenshot("warbands-smith");
		run.menuShot(corePos, "warbands-ui-town-center-castle");
		run.menuShot(cores[2], "warbands-ui-barracks");
		run.menuShot(cores[3], "warbands-ui-archery-range");
		run.menuShot(cores[4], "warbands-ui-stable");
		run.menuShot(cores[0], "warbands-ui-blacksmith");
		run.menuShot(cores[1], "warbands-ui-watch-tower");
		run.menuShot(cores[5], "warbands-ui-siege-workshop");
	}

	private static void galleryGuide(Run run) {
		run.context.runOnClient(minecraft -> Handbook.open());
		run.context.waitTicks(5);
		int pages = run.context.computeOnClient(minecraft -> Handbook.pages().size());
		for (int i = 0; i < pages; i++) {
			run.cleanShot(String.format("warbands-guide-%02d", i + 1));
			run.context.runOnClient(minecraft -> {
				if (minecraft.screen instanceof GuideScreen guide) {
					guide.turn(1);
				}
			});
			run.context.waitTicks(2);
		}
		run.context.setScreen(() -> null);
	}

	private static void lang(Run run) {
		run.context.runOnClient(minecraft -> {
			Set<String> english = Run.langKeys(minecraft, "en_us");
			Set<String> spanish = Run.langKeys(minecraft, "es_es");
			Set<String> onlyEnglish = new HashSet<>(english);
			onlyEnglish.removeAll(spanish);
			Set<String> onlySpanish = new HashSet<>(spanish);
			onlySpanish.removeAll(english);
			check(onlyEnglish.isEmpty() && onlySpanish.isEmpty(), "en_us and es_es have the same keys (only en: " + onlyEnglish
					+ ", only es: " + onlySpanish + ")");
			for (Blueprint blueprint : Blueprint.values()) {
				check(english.contains("tooltip.warbands.blueprint." + blueprint.getName() + ".purpose"), blueprint.getName() + " blueprint tooltip says what it's for");
			}
			for (String language : List.of("en_us", "es_es")) {
				Map<String, String> texts = Run.lang(minecraft, language);
				List<String> overflowing = new ArrayList<>();
				for (Handbook.Page page : Handbook.pages()) {
					String body = texts.getOrDefault("handbook.warbands." + page.id() + ".body", "");
					int over = GuideScreen.overflow(minecraft.font, page, body);
					if (over > 0) {
						overflowing.add(page.id() + " +" + over + " lines");
					}
				}
				check(overflowing.isEmpty(), "every handbook page fits in " + language + " (too long: " + overflowing + ")");
			}
		});
	}

	/** The tester's shortcut builds the whole town in one go, and its menu's camp teleport works. */
	private static void playtest(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 180.0F, 0.0F);
		run.context.waitTicks(5);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			check(dev.felipe.warbands.Playtest.setUp(level, player), "the playtest town is built");
			TownCenterBlockEntity town = TownRegistry.nearestOwned(level, player.blockPosition(), player.getUUID(),
					TownCenterBlockEntity.HOUSE_RADIUS);
			check(town != null && town.age() == Age.CASTLE, "it's a Castle Age town");
			check(town.stock(Resource.GOLD) >= 10_000, "with a full stockpile");
			int buildings = dev.felipe.warbands.town.BuildingRegistry.near(level, town.getBlockPos(), TownCenterBlockEntity.HOUSE_RADIUS,
					player.getUUID()).size();
			check(buildings == 9, "every building stands (" + buildings + " of 9, with the Town Center)");
			check(ArmyRegistry.ids(server, player.getUUID()).size() >= 11, "an army of every unit");
			check(player.getInventory().contains(new ItemStack(ModItems.WARHORSE_WHISTLE)), "the kit is given");
			dev.felipe.warbands.Playtest.menu(player);
			check(dev.felipe.warbands.camp.Camps.buy(level, town, player, dev.felipe.warbands.camp.CampTier.FORT, false),
					"a free War Map from the menu");
			check(dev.felipe.warbands.Playtest.toCamp(level, player), "Go to camp teleports");
			BlockPos camp = dev.felipe.warbands.camp.CampRegistry.active(level, player.getUUID()).orElseThrow().center();
			check(Math.abs(player.getX() - camp.getX()) < 40 && Math.abs(player.getZ() - camp.getZ()) < 40,
					"the player lands just outside the camp");
			check(player.getY() > level.getMinY() + 1 && level.getBlockState(player.blockPosition().below()).isSolid(),
					"on solid ground, not in the void (y " + (int) player.getY() + ")");
			check(player.getInventory().contains(new ItemStack(ModItems.PLAYTEST_GUIDE)), "the Tester's Guide is in the kit");
			check(dev.felipe.warbands.Playtest.home(level, player), "Go home teleports");
			check(player.blockPosition().distSqr(town.getBlockPos()) < 20 * 20, "the player lands at the Town Center");
			player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
		});
		run.context.waitTicks(20);
		run.lookAt(ground.offset(0, 25, 12), Vec3.atCenterOf(ground.offset(0, 0, -22)));
		run.screenshot("warbands-playtest-town");
	}
}
