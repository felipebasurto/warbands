package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.player;
import static dev.felipe.warbands.gametest.Run.townCenter;

import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.camp.CampLayout;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Threat;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Spoils;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.WarlordEntity;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModGameRules;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.RelicPedestalBlock;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.UnitType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** War Maps and enemy camps: buying, building on arrival, guards, claiming, payout, revenge, abandoning. */
final class CampScenarios {
	private CampScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("camps.others", "Bandit Camp and Fort of another commander: built, guarded, unbreakable standard; a player outside the party can't claim it (photos)",
				CampScenarios::others));
		all.add(new Scenario("camps.caches", "the two stash chests are the ones nearest the banner, not the first the scan hits",
				CampScenarios::caches));
		all.add(new Scenario("camps.claim-away", "the owner is offline: a bystander beside the banner is refused; a player who hit a guard claims, and the trophy and relic are left there",
				CampScenarios::claimAway));
		all.add(new Scenario("camps.keep", "a Castle War Map: Keep 250-400 blocks out, built on arrival, gate, claimed after its guards fall, trophy, revenge, payout (photo)",
				CampScenarios::keep));
		all.add(new Scenario("camps.abandon", "abandoning a camp: an unbuilt one is forgotten, a built one stays and can still be taken; a new map can be bought",
				CampScenarios::abandon));
		all.add(new Scenario("camps.keeps-player-builds", "a player's chest in a camp's footprint: the camp is dropped when the player arrives, the map's cost comes back, the chest survives",
				CampScenarios::keepsPlayerBuilds));
		all.add(new Scenario("camps.revenge-off", "taking a Fort with raids turned off schedules no revenge raid", CampScenarios::revengeOff));
		all.add(new Scenario("camps.kinds", "every camp kind builds, keeps its guards (vanilla ones too: persistent, no drops), one of each per bag cycle (photos)",
				CampScenarios::kinds));
		all.add(new Scenario("camps.bag", "the War Map draws every kind of the age before repeating, never the same twice in a row",
				CampScenarios::bag));
		all.add(new Scenario("camps.traps", "camp traps: a stepped alarm plate sallies the guards, dispensers are sealed, caltrops hurt, kegs break no blocks, all off once claimed",
				CampScenarios::traps));
		all.add(new Scenario("camps.warlords", "warlords: each Castle kind has one (the Keep's is the boss), the standard holds while he stands, he drops his named blade",
				CampScenarios::warlords));
		all.add(new Scenario("camps.relay", "redstone contraptions: the alarm powers the war relays, the portcullis shuts, the guns fire, rigged planks drop; smashing the relays opens the gate; broken camp guns spill no ammo",
				CampScenarios::relay));
		all.add(new Scenario("camps.twin-sally", "the twin Fort's side gates open on alarm; both armies can path in and out of both wards with the central portcullis shut (photo)",
				CampScenarios::twinSally));
		all.add(new Scenario("camps.threat", "threat: Hard camps get more and tougher guards than Easy, extra players and a win streak add guards, raids grow with difficulty",
				CampScenarios::threat));
		all.add(new Scenario("camps.relics", "relics: the first win at each fantasy camp gives its relic once, the pedestal enshrines it, the dead sap the town's foes",
				CampScenarios::relics));
	}

	private static Camp active(MinecraftServer server) {
		return CampRegistry.active(server.overworld(), player(server).getUUID()).orElseThrow();
	}

	/** Goes to the player's camp and waits until it's built. Returns its standard. */
	private static BlockPos visit(Run run) {
		BlockPos site = run.compute(server -> active(server).center());
		run.tp(site.getX() + 0.5, site.getY() + 20, site.getZ() + 40.5, 180.0F, 30.0F);
		run.waitFor(server -> active(server).built(), "the camp is built when the player arrives", 20 * 30);
		return run.compute(server -> Camps.standardPos(active(server)));
	}

	/** Kills (not discards) every guard of the player's camp, vanilla mobs included. */
	private static void killGuards(Run run) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			for (UUID id : active(server).guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					guard.kill(level);
				}
			}
		});
		run.waitFor(server -> Camps.guardsLeft(server.overworld(), active(server)) == 0, "every guard is dead", 40);
	}

	private static void others(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 60.5, 180.0F, 0.0F);
		run.context.waitTicks(20);
		BlockPos[] shots = run.compute(server -> {
			ServerLevel level = server.overworld();
			BlockPos bandit = ground.offset(-30, 0, 90);
			BlockPos fort = ground.offset(30, 0, 90);
			UUID stranger = UUID.randomUUID();
			Camps.build(level, Camp.chosen(stranger, ground, CampTier.BANDIT_CAMP, bandit, Rotation.NONE));
			Camps.build(level, Camp.chosen(stranger, ground, CampTier.FORT, fort, Rotation.NONE));
			Camp camp = CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.BANDIT_CAMP).findFirst().orElseThrow();
			check(level.getEntitiesOfClass(SoldierEntity.class, new AABB(bandit).inflate(10), SoldierEntity::isRaider).size()
					== garrison(camp), "a Bandit Camp is guarded by its raiders (" + garrison(camp) + " on this threat)");
			BlockPos standard = Camps.standardPos(camp);
			check(level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD), "the Bandit Camp has its standard");
			ServerPlayer player = player(server);
			check(level.getBlockState(standard).getDestroyProgress(player, level, standard) == 0.0F, "the Camp Standard can't be mined");
			for (UUID id : camp.guards()) {
				if (level.getEntity(id) instanceof SoldierEntity guard) {
					guard.kill(level);
				}
			}
			// The commander is offline. Outside the party range, even a fighter would be refused. This player never fought.
			player.teleportTo(standard.getX() + 80.5, standard.getY(), standard.getZ() + 0.5);
			Camps.claim(level, standard, player);
			check(level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD) && !CampRegistry.all(level).stream()
					.filter(c -> c.tier() == CampTier.BANDIT_CAMP).findFirst().orElseThrow().conquered(),
					"outside the party range, another commander's camp can't be claimed even with its guards dead");
			return new BlockPos[] {bandit, fort};
		});
		run.command("effect give @p minecraft:slow_falling 60 0 true");
		run.lookAt(shots[0].offset(14, 12, 18), Vec3.atCenterOf(shots[0].offset(0, 1, 0)));
		run.screenshot("warbands-camp-bandit");
		run.lookAt(shots[1].offset(18, 14, 24), Vec3.atCenterOf(shots[1].offset(0, 2, 0)));
		run.screenshot("warbands-camp-fort");
	}

	/** Every chunk the camp can touch, loaded before {@link Camps#build} reads the ground. */
	private static void loadAround(ServerLevel level, BlockPos site, int reach) {
		for (int cx = site.getX() - reach >> 4; cx <= site.getX() + reach >> 4; cx++) {
			for (int cz = site.getZ() - reach >> 4; cz <= site.getZ() + reach >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
	}

	/** Rotated so the footprint scan's first chest is not one of the two nearest the banner. */
	private static void caches(Run run) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			BlockPos site = run.ground.offset(0, 0, 40);
			loadAround(level, site, 20);
			Camps.build(level, Camp.chosen(player(server).getUUID(), run.ground, CampTier.SMUGGLERS_DOCK, site,
					Rotation.CLOCKWISE_90, "smugglers_warehouse", Threat.NORMAL));
			Camp camp = CampRegistry.all(level).stream().filter(c -> c.layoutId().equals("smugglers_warehouse")).findFirst()
					.orElseThrow();
			BlockPos standard = Camps.standardPos(camp);
			int half = camp.layout().half();
			BlockPos center = camp.center();
			List<BlockPos> chests = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 0, -half),
					center.offset(half, camp.layout().height(), half))) {
				if (level.getBlockState(pos).is(Blocks.CHEST)) {
					chests.add(pos.immutable());
				}
			}
			check(chests.size() >= 3, "the warehouse has at least three chests (" + chests.size() + ")");
			chests.sort((a, b) -> Spoils.cacheOrder(standard, a, b));
			int stashes = 0;
			for (int i = 0; i < chests.size(); i++) {
				BlockPos pos = chests.get(i);
				check(level.getBlockEntity(pos) instanceof ChestBlockEntity, "chest at " + pos.toShortString());
				ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
				boolean stash = CampTier.SMUGGLERS_DOCK.cacheTable().equals(chest.getLootTable());
				if (i < Spoils.MAX_CACHES) {
					check(stash && chest.getCustomName() != null, "the chest " + i + " nearest the banner is a named stash");
					stashes++;
				} else {
					check(!stash, "a farther chest stays decor");
				}
			}
			check(stashes == Spoils.MAX_CACHES, "two stashes");
		});
	}

	/** Owner offline: a bystander is refused; a player who hit a guard claims, and the prizes stay on the ground. */
	private static void claimAway(Run run) {
		UUID commander = UUID.randomUUID();
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos idleAt = run.ground.offset(240, 0, 240);
			Camp idle = new Camp(UUID.randomUUID(), run.ground, CampTier.BANDIT_CAMP, idleAt, Rotation.NONE, true, List.of(),
					false, false, false, true, 0L, false, "", Threat.NORMAL, true);
			var data = level.getAttachedOrCreate(ModAttachments.CAMPS);
			List<Camp> camps = new ArrayList<>(data.camps());
			camps.add(idle);
			level.setAttached(ModAttachments.CAMPS, new CampRegistry.Data(List.copyOf(camps), data.bags()));
			BlockPos idleStandard = Camps.standardPos(idle);
			player.teleportTo(idleStandard.getX() + 0.5, idleStandard.getY(), idleStandard.getZ() + 1.5);
			check(player.blockPosition().closerThan(idleStandard, Threat.PARTY_RANGE), "the bystander is beside the banner");
			Camps.claim(level, idleStandard, player);
			check(!CampRegistry.all(level).stream().filter(c -> c.center().equals(idleAt)).findFirst().orElseThrow().conquered(),
					"a bystander beside the banner cannot claim for an absent commander");

			BlockPos site = run.ground.offset(0, 0, 50);
			loadAround(level, site, 24);
			Camps.build(level, Camp.chosen(commander, run.ground, CampTier.WITCH_COVEN, site, Rotation.NONE));
			Camp camp = CampRegistry.all(level).stream().filter(c -> c.owner().equals(commander)).findFirst().orElseThrow();
			boolean struck = false;
			for (UUID id : camp.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					if (!struck) {
						guard.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
						struck = true;
					}
					guard.kill(level);
				}
			}
			check(struck, "the coven has a guard to fight");
			check(CampRegistry.participants(level, camp).contains(player.getUUID()), "hitting a guard records the fighter");
		});
		run.waitFor(server -> {
			Camp camp = CampRegistry.all(server.overworld()).stream().filter(c -> c.owner().equals(commander)).findFirst()
					.orElseThrow();
			return Camps.guardsLeft(server.overworld(), camp) == 0;
		}, "the coven's guards are dead", 40);
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camp camp = CampRegistry.all(level).stream().filter(c -> c.owner().equals(commander)).findFirst().orElseThrow();
			BlockPos standard = Camps.standardPos(camp);
			ServerPlayer player = player(server);
			player.teleportTo(standard.getX() + 0.5, standard.getY(), standard.getZ() + 1.5);
			Camps.claim(level, standard, player);
			Camp taken = CampRegistry.all(level).stream().filter(c -> c.owner().equals(commander)).findFirst().orElseThrow();
			check(taken.conquered(), "the camp is claimed for the absent commander");
			check(!player.getInventory().contains(stack -> stack.is(CampTier.WITCH_COVEN.trophy())),
					"the ally does not pocket the trophy");
			check(!player.getInventory().contains(stack -> stack.is(ModItems.HAG_CHARM)), "the ally does not pocket the relic");
			AABB near = new AABB(standard).inflate(3);
			check(level.getEntitiesOfClass(ItemEntity.class, near, e -> e.getItem().is(CampTier.WITCH_COVEN.trophy())).size() == 1,
					"the trophy is left at the banner");
			check(level.getEntitiesOfClass(ItemEntity.class, near, e -> e.getItem().is(ModItems.HAG_CHARM)).size() == 1,
					"the relic is left at the banner");
			check(Relics.earned(level, commander, CampTier.WITCH_COVEN), "the relic is marked earned for the commander");
			check(!Relics.earned(level, player.getUUID(), CampTier.WITCH_COVEN), "the ally did not earn the relic");
			Camps.claim(level, standard, player);
			check(level.getEntitiesOfClass(ItemEntity.class, near, e -> e.getItem().is(CampTier.WITCH_COVEN.trophy())).size() == 1,
					"a second claim leaves no second trophy");
		});
	}

	/** The guards a built camp should have: its layout's garrison at its threat. */
	private static int garrison(Camp camp) {
		return Camps.garrison(camp.layout(), camp.tier().age(), camp.threat().extraGuards()).size();
	}

	private static void keep(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos core = town.core();
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			check(Camps.buy(level, townCenter(level, core), player, CampTier.KEEP), "a Castle town sells a War Map");
			check(player.getInventory().contains(stack -> Camps.mapTarget(stack) != null), "the player gets the War Map (a filled map)");
			ItemStack map = player.getInventory().getNonEquipmentItems().stream().filter(stack -> Camps.mapTarget(stack) != null)
					.findFirst().orElse(ItemStack.EMPTY);
			var decorations = map.get(DataComponents.MAP_DECORATIONS);
			check(decorations != null && decorations.decorations().containsKey("warbands_camp")
					&& decorations.decorations().containsKey("warbands_town"), "the map marks the camp and the town");
			Camp camp = active(server);
			check(camp.tier() == CampTier.KEEP, "a Castle town's map leads to a Keep");
			double distance = Math.sqrt(Math.pow(camp.center().getX() - core.getX(), 2) + Math.pow(camp.center().getZ() - core.getZ(), 2));
			check(distance >= Camps.MIN_DISTANCE - 1 && distance <= Camps.MAX_DISTANCE + 1, "the Keep is 250-400 blocks away (" + Math.round(distance) + ")");
			check(!camp.built(), "the camp waits to be built until someone comes near");
			check(Camps.whyNot(level, townCenter(level, core), player.getUUID()) != null, "one War Map at a time");
		});
		BlockPos standard = visit(run);
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camp camp = active(server);
			check(level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD), "the Keep has its Camp Standard");
			check(Camps.guardsLeft(level, camp) == garrison(camp), "the Keep has its " + garrison(camp) + " guards");
			int gate = 0;
			int half = camp.layout().half();
			for (BlockPos pos : BlockPos.betweenClosed(camp.center().offset(-half, 0, -half), camp.center().offset(half, 6, half))) {
				if (level.getBlockState(pos).is(ModBlocks.REINFORCED_GATE)) {
					gate++;
				}
			}
			// The concentric layout has a portcullis for its inner gate instead.
			int gates = camp.layoutId().equals("keep_concentric") ? 0 : 12;
			check(gate == gates, camp.layoutId() + "'s gate is " + gates + " Reinforced Gate blocks (" + gate + ")");
			Camps.claim(level, standard, player(server));
			check(CampRegistry.active(level, player(server).getUUID()).isPresent(), "a camp can't be claimed while guards stand");
		});
		BlockPos site = run.compute(server -> active(server).center());
		run.command("effect give @p minecraft:slow_falling 30 0 true");
		run.lookAt(site.offset(22, 16, 26), Vec3.atCenterOf(site.offset(0, 4, 0)));
		run.screenshot("warbands-camp-keep");
		killGuards(run);
		int food = run.compute(server -> townCenter(server.overworld(), core).stock(Resource.FOOD));
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Camps.claim(level, standard, player);
			check(CampRegistry.active(level, player.getUUID()).isEmpty(), "with the guards gone, the Keep is claimed");
			check(!level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD), "the standard falls: no second claim");
			check(level.getBlockState(standard).is(Blocks.CHEST), "the standard leaves a War Chest in its place");
			check(level.getBlockEntity(standard) instanceof ChestBlockEntity chest
					&& CampTier.KEEP.lootTable().equals(chest.getLootTable()) && chest.hasCustomName(),
					"the War Chest is named and rolls the Keep's loot on first open");
			check(player.getInventory().contains(stack -> stack.is(ModItems.KEEP_TROPHY)), "the player gets the Keep Trophy");
			check(!player.getInventory().contains(stack -> Camps.mapTarget(stack) != null)
					&& player.getInventory().contains(stack -> core.equals(Camps.mapHome(stack))), "the War Map now leads home");
			check(Run.hasAdvancement(server, WarbandsAdvancements.CAMP_KEEP), "advancement for taking a Keep");
			check(RaidRegistry.get(level, core).map(RaidRegistry.TownRaids::retaliation).orElse(false), "a revenge raid is scheduled");
		});
		run.tp(town.center().getX() + 0.5, town.center().getY() + 1, town.center().getZ() + 14.5, 180.0F, 0.0F);
		run.waitFor(server -> CampRegistry.all(server.overworld()).stream().anyMatch(c -> c.tier() == CampTier.KEEP && c.paid()),
				"the plunder reaches the Town Center once it's loaded", 20 * 20);
		run.server(server -> {
			int gained = townCenter(server.overworld(), core).stock(Resource.FOOD) - food;
			check(gained == CampTier.KEEP.plunder(Resource.FOOD), "the Keep's plunder is in the stockpile (+" + gained + " food)");
		});
		// Back at the camp: it burns, then it's cleared away.
		run.tp(site.getX() + 0.5, site.getY() + 20, site.getZ() + 30.5, 180.0F, 30.0F);
		run.command("effect give @p minecraft:slow_falling 60 0 true");
		run.waitFor(server -> CampRegistry.all(server.overworld()).stream().anyMatch(c -> c.tier() == CampTier.KEEP && c.razed()),
				"the taken Keep burns down", 70 * 20);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(!level.getBlockState(standard.above(4)).is(ModBlocks.REINFORCED_GATE)
					&& StructureBlocks.countIn(level, site.offset(-12, 0, -12), site.offset(12, 15, 12)) == 0,
					"the burnt Keep left no structure blocks");
			check(level.getBlockState(standard).is(Blocks.CHEST)
					&& level.getBlockEntity(standard) instanceof ChestBlockEntity chest
					&& CampTier.KEEP.lootTable().equals(chest.getLootTable()),
					"the War Chest survives the burn-down, loot still unrolled");
		});
	}

	private static void abandon(Run run) {
		Run.Town town = run.placeTown();
		BlockPos core = town.core();
		run.stockUp(core);
		// An unbuilt camp: abandoned, it's forgotten, and a new map can be bought.
		BlockPos first = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			check(Camps.buy(level, townCenter(level, core), player, CampTier.BANDIT_CAMP), "a Village town sells a War Map to a Bandit Camp");
			BlockPos site = active(server).center();
			check(Camps.whyNot(level, townCenter(level, core), player.getUUID()) != null, "one War Map at a time");
			check(Camps.abandon(level, player), "the camp is abandoned");
			check(CampRegistry.active(level, player.getUUID()).isEmpty(), "no camp to take after abandoning");
			check(CampRegistry.all(level).stream().noneMatch(c -> c.center().equals(site)), "an unbuilt abandoned camp is forgotten");
			check(Camps.whyNot(level, townCenter(level, core), player.getUUID()) == null, "a new War Map can be bought");
			check(!Camps.abandon(level, player), "nothing to abandon twice");
			int food = townCenter(level, core).stock(Resource.FOOD);
			check(Camps.buy(level, townCenter(level, core), player, CampTier.BANDIT_CAMP), "a second War Map is bought");
			check(townCenter(level, core).stock(Resource.FOOD) == food - CampTier.BANDIT_CAMP.cost(Resource.FOOD), "and paid for again: no refund");
			return site;
		});
		// A built camp: abandoned, it stays with its guards, and can still be taken.
		BlockPos standard = visit(run);
		Camp built = run.compute(CampScenarios::active);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			check(Camps.abandon(level, player), "a built camp is abandoned");
			check(CampRegistry.active(level, player.getUUID()).isEmpty(), "it no longer counts as your camp to take");
			check(Camps.guardsLeft(level, built) == garrison(built), "its guards are still there");
			for (UUID id : built.guards()) {
				Entity guard = level.getEntity(id);
				if (guard instanceof SoldierEntity soldier) {
					soldier.kill(level);
				}
			}
			Camps.claim(level, standard, player);
			check(!level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD), "an abandoned camp can still be taken once its guards fall");
		});
		// The first site, never built: walking there builds nothing.
		run.tp(first.getX() + 0.5, first.getY() + 10, first.getZ() + 20.5, 180.0F, 30.0F);
		run.holds(server -> server.overworld().getEntitiesOfClass(SoldierEntity.class, new AABB(first).inflate(20), SoldierEntity::isRaider).isEmpty(),
				"an abandoned camp that was never built never appears", 100);
	}

	private static void keepsPlayerBuilds(Run run) {
		Run.Town town = run.placeTown();
		BlockPos core = town.core();
		run.stockUp(core);
		BlockPos[] spot = new BlockPos[1];
		int[] food = new int[1];
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			food[0] = townCenter(level, core).stock(Resource.FOOD);
			check(Camps.buy(level, townCenter(level, core), player, CampTier.BANDIT_CAMP), "a War Map is bought");
			BlockPos site = active(server).center();
			level.getChunk(site.getX() >> 4, site.getZ() >> 4);
			BlockPos chest = site.atY(level.getHeight(Heightmap.Types.WORLD_SURFACE, site.getX(), site.getZ()) + 1);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
			spot[0] = chest;
		});
		run.tp(spot[0].getX() + 0.5, spot[0].getY() + 20, spot[0].getZ() + 40.5, 180.0F, 30.0F);
		run.waitFor(server -> CampRegistry.active(server.overworld(), player(server).getUUID()).isEmpty(),
				"a camp with a player's chest in its footprint is dropped", 20 * 30);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(level.getBlockState(spot[0]).is(Blocks.CHEST), "the player's chest survives");
			check(townCenter(level, core).stock(Resource.FOOD) == food[0], "the map's cost goes back to the Town Center");
			check(CampRegistry.all(level).stream().noneMatch(c -> c.built() && c.center().getX() == spot[0].getX()), "no camp was built there");
		});
	}

	private static void revengeOff(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		run.waitFor(server -> RaidRegistry.get(server.overworld(), core).isPresent(), "the Feudal town is on the raid calendar", 60);
		run.server(server -> {
			ServerLevel level = server.overworld();
			level.getGameRules().set(ModGameRules.RAIDS, false, server);
			check(Camps.buy(level, townCenter(level, core), player(server), CampTier.FORT), "a Feudal town sells a War Map to a Fort");
			check(active(server).tier() == CampTier.FORT, "it leads to a Fort");
		});
		BlockPos standard = visit(run);
		killGuards(run);
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camps.claim(level, standard, player(server));
			check(!level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD), "the Fort is taken");
			check(!RaidRegistry.get(level, core).orElseThrow().retaliation(), "no revenge raid while raids are off");
		});
	}

	/**
	 * Every layout of every kind builds and stands guard with the garrison its threat calls for (the test world is on
	 * Easy); vanilla-mob guards are persistent, tagged, and counted.
	 */
	private static void kinds(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 60.5, 180.0F, 0.0F);
		run.context.waitTicks(20);
		List<Camp> camps = run.compute(server -> {
			ServerLevel level = server.overworld();
			List<Camp> built = new ArrayList<>();
			UUID stranger = UUID.randomUUID();
			int i = 0;
			for (CampTier tier : CampTier.values()) {
				for (CampLayout layout : tier.layouts()) {
					BlockPos site = ground.offset(i % 6 * 60 - 150, 0, 110 + i / 6 * 60);
					int reach = layout.half() + 16;
					// Every chunk the camp or its wandering guards can touch, or they park in an unloaded chunk.
					for (int cx = site.getX() - reach >> 4; cx <= site.getX() + reach >> 4; cx++) {
						for (int cz = site.getZ() - reach >> 4; cz <= site.getZ() + reach >> 4; cz++) {
							level.setChunkForced(cx, cz, true);
							level.getChunk(cx, cz, ChunkStatus.FULL, true); // synchronous load, so build sees it right away
						}
					}
					Camps.build(level, Camp.chosen(stranger, ground, tier, site, Rotation.NONE, layout.id(), Threat.NORMAL));
					built.add(CampRegistry.all(level).stream().filter(c -> c.layoutId().equals(layout.id()) && c.owner().equals(stranger))
							.findFirst().orElseThrow());
					i++;
				}
			}
			return built;
		});
		run.context.waitTicks(10); // forced chunks need a tick or two before their entities are tracked
		run.server(server -> {
			ServerLevel level = server.overworld();
			for (Camp camp : camps) {
				CampLayout layout = camp.layout();
				String name = layout.id();
				List<CampTier.Guard> garrison = Camps.garrison(layout, camp.tier().age(), camp.threat().extraGuards());
				check(camp.built(), name + " built");
				check(camp.guards().size() == garrison.size(),
						name + " spawned its " + garrison.size() + " guards (spawned " + camp.guards().size() + ")");
				check(level.getBlockState(Camps.standardPos(camp)).is(ModBlocks.CAMP_STANDARD), name + " has its standard");
				for (int g = 0; g < camp.guards().size(); g++) {
					Entity entity = level.getEntity(camp.guards().get(g));
					if (entity == null) {
						CampTier.Guard spec = garrison.get(g);
						check(false, name + " guard #" + g + " " + spec.type().toShortString() + " gone at " + spec.spot().toShortString());
						continue;
					}
					if (!entity.isAlive() && entity instanceof LivingEntity dead) {
						check(false, name + " guard " + dead.getType().toString() + " died: "
								+ (dead.getLastDamageSource() == null ? "?" : dead.getLastDamageSource().getLocalizedDeathMessage(dead).getString())
								+ " at " + dead.blockPosition().toShortString());
					}
					if (entity instanceof Mob mob && !(mob instanceof SoldierEntity)) {
						check(mob.isPersistenceRequired(), name + " vanilla guards never despawn");
						check(mob.entityTags().contains(Camps.GUARD_TAG), name + " vanilla guards drop nothing, give no XP");
						if (mob.is(net.minecraft.tags.EntityTypeTags.BURN_IN_DAYLIGHT)) {
							check(mob.hasEffect(MobEffects.FIRE_RESISTANCE), name + " undead guards don't burn at dawn");
						}
					}
				}
				check(Camps.guardsLeft(level, camp) == camp.guards().size(),
						name + " guards still alive (" + Camps.guardsLeft(level, camp) + "/" + camp.guards().size() + ")");
			}
		});
		// Screenshots of the new layouts, from the south-east, far enough to see the whole camp.
		run.command("effect give @p minecraft:slow_falling 120 0 true");
		for (String id : List.of("bandit_stockade", "poachers_treehouse", "fort_barbican", "quarry_terraces", "coven_stilts",
				"frozen_starfort", "keep_moat", "keep_concentric", "cavalry_paddocks", "crypt_catacomb", "illager_watchtower")) {
			Camp camp = camps.stream().filter(c -> c.layoutId().equals(id)).findFirst().orElseThrow();
			int half = camp.layout().half();
			run.lookAt(camp.center().offset(half + 6, half + 8, half + 14), Vec3.atCenterOf(camp.center().offset(0, 1, 0)));
			run.screenshot("warbands-camp-" + id.replace('_', '-'));
		}
	}

	/** Builds these layouts for a stranger side by side north of the ground, chunks forced, and returns them. */
	private static List<Camp> buildLayouts(Run run, String... ids) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 60.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		List<Camp> camps = run.compute(server -> {
			ServerLevel level = server.overworld();
			UUID stranger = UUID.randomUUID();
			List<Camp> built = new ArrayList<>();
			for (int i = 0; i < ids.length; i++) {
				String id = ids[i];
				CampTier tier = java.util.Arrays.stream(CampTier.values())
						.filter(t -> t.layouts().stream().anyMatch(l -> l.id().equals(id))).findFirst().orElseThrow();
				BlockPos site = ground.offset(i * 60 - (ids.length - 1) * 30, 0, 100);
				int reach = tier.layout(id).half() + 16;
				for (int cx = site.getX() - reach >> 4; cx <= site.getX() + reach >> 4; cx++) {
					for (int cz = site.getZ() - reach >> 4; cz <= site.getZ() + reach >> 4; cz++) {
						level.setChunkForced(cx, cz, true);
						level.getChunk(cx, cz, ChunkStatus.FULL, true);
					}
				}
				Camps.build(level, Camp.chosen(stranger, ground, tier, site, Rotation.NONE, id, Threat.NORMAL));
				built.add(CampRegistry.all(level).stream().filter(c -> c.layoutId().equals(id) && c.owner().equals(stranger))
						.findFirst().orElseThrow());
			}
			return built;
		});
		run.context.waitTicks(30);
		return camps;
	}

	private static long extendedPistons(ServerLevel level, Camp camp) {
		return blocks(level, camp, Blocks.STICKY_PISTON).stream()
				.filter(pos -> level.getBlockState(pos).getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED)).count();
	}

	/** A reachable standard in a calm camp does not prove its ground troops can leave once the alarm shuts the divider. */
	private static void twinSally(Run run) {
		Camp camp = buildLayouts(run, "fort_twin").get(0);
		List<SoldierEntity> probes = run.compute(server -> {
			ServerLevel level = server.overworld();
			for (UUID id : camp.guards()) {
				if (level.getEntity(id) instanceof Mob guard) {
					guard.setNoAi(true);
				}
			}
			List<SoldierEntity> out = new ArrayList<>();
			BlockPos origin = camp.center().offset(-15, 0, -15);
			for (int x : new int[] {0, 30}) {
				for (int z : new int[] {12, 17}) {
					SoldierEntity ally = Run.soldier(level, UnitType.MILITIA,
							Vec3.atBottomCenterOf(origin.offset(x, 1, z)), player(server));
					SoldierEntity enemy = Run.raider(level, UnitType.MILITIA,
							Vec3.atBottomCenterOf(origin.offset(x == 0 ? 4 : 26, 1, z)), Age.FEUDAL, null);
					ally.setNoAi(true);
					enemy.setNoAi(true);
					// NoAI skips gravity too; these fixed probes stand on the camp's floor.
					ally.setOnGround(true);
					enemy.setOnGround(true);
					out.add(ally);
					out.add(enemy);
				}
			}
			Camps.alarm(level, camp, player(server));
			return out;
		});
		run.context.waitTicks(30);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(extendedPistons(level, camp) == 4, "the central portcullis is shut during the sally");
			BlockPos origin = camp.center().offset(-15, 0, -15);
			for (int x : new int[] {1, 29}) {
				for (int z : new int[] {12, 13, 17, 18}) {
					for (int y : new int[] {1, 2}) {
						var state = level.getBlockState(origin.offset(x, y, z));
						check(state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN),
								"side sally gate open at " + x + "," + y + "," + z);
					}
				}
			}
			for (int i = 0; i < probes.size(); i += 2) {
				SoldierEntity ally = probes.get(i);
				SoldierEntity enemy = probes.get(i + 1);
				var inward = ally.getNavigation().createPath(enemy, 0);
				var outward = enemy.getNavigation().createPath(ally, 0);
				check(inward != null && inward.canReach(), "your militia can enter ward through side gate " + i / 2);
				check(outward != null && outward.canReach(), "enemy militia can leave ward through side gate " + i / 2);
			}
		});
		run.lookAt(camp.center().offset(-24, 15, 24), Vec3.atCenterOf(camp.center()));
		run.screenshot("warbands-fort-twin-sally-gates");
	}

	/**
	 * The barbican fort's portcullis and guns, and the treehouse's rigged bridge planks, run on vanilla redstone from
	 * the war relays: calm, all quiet; alarmed, the gate shuts, the guns fire, the planks drop; relays smashed, the
	 * gate opens again. A broken camp gun spills no arrows.
	 */
	private static void relay(Run run) {
		List<Camp> camps = buildLayouts(run, "fort_barbican", "poachers_treehouse");
		Camp fort = camps.get(0);
		Camp tree = camps.get(1);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(blocks(level, fort, ModBlocks.PORTCULLIS).size() == 4, "the barbican's portcullis has its 4 grates");
			check(blocks(level, fort, ModBlocks.WAR_RELAY).size() >= 8, "the barbican has its gate and gun relays");
			check(extendedPistons(level, fort) == 0, "calm: the portcullis is open");
			check(extendedPistons(level, tree) == 2, "calm: the treehouse bridges' rigged planks are held in place");
			check(blocks(level, fort, ModBlocks.WAR_RELAY).stream().noneMatch(pos -> level.getBlockState(pos).getValue(
					dev.felipe.warbands.camp.WarRelayBlock.POWERED)), "calm: no relay is powered");
			Camps.alarm(level, fort, player(server));
			Camps.alarm(level, tree, player(server));
		});
		run.context.waitTicks(40);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(Camps.alarmedAt(level, fort.center()), "the fort is alarmed");
			check(extendedPistons(level, fort) == 4, "alarmed: the portcullis slammed shut (" + extendedPistons(level, fort) + "/4)");
			check(extendedPistons(level, tree) == 0, "alarmed: the rigged planks were pulled away");
			AABB box = new AABB(fort.center()).inflate(fort.layout().half() + 8);
			check(!level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box).isEmpty(),
					"alarmed: the barbican's guns are firing");
			// sabotage: smash the gate's relays
			for (BlockPos pos : blocks(level, fort, ModBlocks.WAR_RELAY)) {
				if (level.getBlockState(pos.east()).is(Blocks.STICKY_PISTON) || level.getBlockState(pos.west()).is(Blocks.STICKY_PISTON)) {
					level.destroyBlock(pos, false);
				}
			}
			// a player breaking a camp gun gets no ammo
			BlockPos gun = blocks(level, fort, Blocks.DISPENSER).get(0);
			player(server).gameMode.destroyBlock(gun);
		});
		run.context.waitTicks(10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(extendedPistons(level, fort) == 0, "relays smashed: the portcullis opens (" + extendedPistons(level, fort) + " still shut)");
			AABB box = new AABB(fort.center()).inflate(fort.layout().half() + 8);
			check(level.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(Items.ARROW)).isEmpty(),
					"a broken camp gun spills no arrows");
		});
		run.lookAt(fort.center().offset(4, 10, 24), Vec3.atCenterOf(fort.center().offset(0, 1, 6)));
		run.screenshot("warbands-camp-barbican-alarmed");
	}

	/** Easy vs Hard, one player vs three, a streak: the threat numbers, and a Hard camp really has more, tougher guards. */
	private static void threat(Run run) {
		run.command("difficulty easy");
		Threat[] easy = run.compute(server -> {
			ServerLevel level = server.overworld();
			UUID owner = UUID.randomUUID();
			return new Threat[] {Threat.of(level, owner, Age.FEUDAL, 12, 1), Threat.of(level, owner, Age.FEUDAL, 12, 3)};
		});
		int easyRaid = run.compute(server -> Threat.raidBonus(server.overworld(), run.ground));
		run.command("difficulty hard");
		Threat hard = run.compute(server -> Threat.of(server.overworld(), UUID.randomUUID(), Age.FEUDAL, 12, 1));
		int hardRaid = run.compute(server -> Threat.raidBonus(server.overworld(), run.ground));
		check(easy[0].extraGuards() < 0 && hard.extraGuards() > 0, "Easy drops guards, Hard adds them (" + easy[0].extraGuards()
				+ " / " + hard.extraGuards() + ")");
		check(hard.health() > easy[0].health() && hard.damage() > easy[0].damage(), "Hard guards are tougher");
		check(easy[1].extraGuards() == easy[0].extraGuards() + 2 && easy[1].bossHealth() > easy[0].bossHealth(),
				"two more players: two more guards and a tougher warlord");
		check(easy[0].stars() == 1 && hard.stars() == 3 && easy[1].stars() == 2, "stars: Easy 1, Hard 3, a party adds one");
		check(hardRaid - easyRaid == 4, "raids: Hard brings 4 more raiders than Easy");
		List<Camp> camps = buildLayouts(run, "bandit_stockade");
		run.command("difficulty easy");
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camp camp = camps.get(0);
			check(camp.threat().hard(), "the camp was built on Hard");
			check(camp.guards().size() == camp.layout().guards().size() + camp.threat().extraGuards(),
					"a Hard stockade has " + camp.guards().size() + " guards (layout " + camp.layout().guards().size() + ")");
			Mob guard = camp.guards().stream().map(level::getEntity).filter(e -> e instanceof SoldierEntity s && !s.isPosted())
					.map(Mob.class::cast).findFirst().orElseThrow();
			check(guard.getMaxHealth() > guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue(),
					"Hard guards carry the health bonus (" + guard.getMaxHealth() + ")");
		});
	}

	/** A Castle town's maps cycle through all four Castle kinds before any repeats, never twice the same in a row. */
	private static void bag(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos core = town.core();
		run.stockUp(core);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			List<CampTier> drawn = new ArrayList<>();
			for (int i = 0; i < CampTier.kinds(Age.CASTLE).size() + 1; i++) {
				for (int j = 0; j < 8; j++) {
					townCenter(level, core).deposit(new ItemStack(Items.BREAD, 64));
					townCenter(level, core).deposit(new ItemStack(Items.COBBLESTONE, 64));
					townCenter(level, core).deposit(new ItemStack(Items.OAK_LOG, 64));
					townCenter(level, core).deposit(new ItemStack(Items.GOLD_BLOCK, 64));
				}
				check(Camps.buy(level, townCenter(level, core), player), "War Map " + (i + 1) + " is sold");
				Camp camp = CampRegistry.active(level, player.getUUID()).orElseThrow();
				check(CampTier.kinds(Age.CASTLE).contains(camp.tier()), "the map leads to a Castle-age kind");
				// The drawn kind, before a missing biome falls back to the plain kind: the bag remembers it.
				drawn.add(CampRegistry.bag(level, player.getUUID()).last().orElseThrow());
				check(Camps.abandon(level, player), "camp " + (i + 1) + " abandoned");
			}
			check(new HashSet<>(drawn.subList(0, CampTier.kinds(Age.CASTLE).size())).size() == CampTier.kinds(Age.CASTLE).size(),
					"the bag serves every kind once before refilling: " + drawn);
			boolean repeats = false;
			for (int i = 1; i < drawn.size(); i++) {
				repeats |= drawn.get(i) == drawn.get(i - 1);
			}
			check(!repeats, "never the same kind twice in a row, refill included: " + drawn);
			Run.roundTrip(level, ModAttachments.CAMPS, "the camps and the War Map bag");
		});
	}

	/** Every block of a kind inside a built camp's bounds, in world order. */
	private static List<BlockPos> blocks(ServerLevel level, Camp camp, net.minecraft.world.level.block.Block block) {
		int half = camp.layout().half() + 4;
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(camp.center().offset(-half, -2, -half),
				camp.center().offset(half, camp.layout().height() + 2, half))) {
			if (level.getBlockState(pos).is(block)) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	private static void traps(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 60.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camps.build(level, Camp.chosen(player(server).getUUID(), ground, CampTier.FORT, ground.offset(-40, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(UUID.randomUUID(), ground, CampTier.KEEP, ground.offset(40, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(UUID.randomUUID(), ground, CampTier.BANDIT_CAMP, ground.offset(0, 0, 90), Rotation.NONE));
			for (Camp camp : CampRegistry.all(level)) {
				int half = camp.layout().half() + 4;
				for (int cx = (camp.center().getX() - half) >> 4; cx <= (camp.center().getX() + half) >> 4; cx++) {
					for (int cz = (camp.center().getZ() - half) >> 4; cz <= (camp.center().getZ() + half) >> 4; cz++) {
						level.setChunkForced(cx, cz, true);
						level.getChunk(cx, cz, ChunkStatus.FULL, true);
					}
				}
			}
		});
		run.context.waitTicks(30); // guards land in the entity-ticking sections
		Object[] trap = run.compute(server -> {
			ServerLevel level = server.overworld();
			Camp fort = CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.FORT).findFirst().orElseThrow();
			Camp keep = CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.KEEP).findFirst().orElseThrow();
			Camp bandit = CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.BANDIT_CAMP).findFirst().orElseThrow();
			List<BlockPos> plates = blocks(level, fort, ModBlocks.ALARM_PLATE);
			check(plates.size() == 1, "the Fort has its alarm plate");
			List<BlockPos> spikes = blocks(level, fort, ModBlocks.CALTROPS);
			check(spikes.size() >= 2, "the Fort's gate lane has caltrops (" + spikes.size() + ")");
			List<BlockPos> dispensers = blocks(level, fort, Blocks.DISPENSER);
			check(!dispensers.isEmpty(), "the Fort's arrow slit has a dispenser");
			check(blocks(level, fort, Blocks.TRIPWIRE_HOOK).size() == 2 && !blocks(level, fort, Blocks.TRIPWIRE).isEmpty(),
					"the Fort's tripwire is strung");
			check(keep.built(), "the Keep is built");
			int modBlocks = 0;
			for (BlockPos pos : BlockPos.betweenClosed(keep.center().offset(-16, -2, -16), keep.center().offset(16, 18, 16))) {
				if (level.getBlockState(pos).getBlock() == ModBlocks.POWDER_KEG
						|| level.getBlockState(pos).getBlock() == ModBlocks.CALTROPS) {
					modBlocks++;
				}
			}
			StringBuilder dump = new StringBuilder();
			for (BlockPos pos : BlockPos.betweenClosed(keep.center().offset(-4, -2, 4), keep.center().offset(5, 2, 11))) {
				dump.append(pos.subtract(keep.center())).append('=').append(level.getBlockState(pos).getBlock().getDescriptionId()).append(' ');
			}
			check(!blocks(level, keep, ModBlocks.POWDER_KEG).isEmpty(),
					"the Keep has powder kegs (modded: " + modBlocks + ") near: " + dump);
			check(blocks(level, bandit, Blocks.SPRUCE_TRAPDOOR).size() >= 1
					&& !blocks(level, bandit, Blocks.SWEET_BERRY_BUSH).isEmpty(), "the Bandit Camp's pitfall is dug");
			return new Object[] {fort, keep, plates.get(0), spikes.get(0), dispensers.get(0)};
		});
		Camp fort = (Camp) trap[0];
		Camp keep = (Camp) trap[1];
		BlockPos plate = (BlockPos) trap[2];
		BlockPos spikes = (BlockPos) trap[3];
		BlockPos dispenser = (BlockPos) trap[4];
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(Camps.armedAt(level, plate) && Camps.armedAt(level, spikes), "the Fort's traps are armed");
			InteractionResult sealed = UseBlockCallback.EVENT.invoker().interact(player(server), level,
					InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dispenser), Direction.UP, dispenser, false));
			check(sealed == InteractionResult.FAIL, "camp dispensers can't be opened while the camp stands");
		});
		// A cow steps on the alarm plate: the bell rings and the guards sally out.
		UUID cow = run.compute(server -> {
			ServerLevel level = server.overworld();
			Mob mob = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
			mob.snapTo(plate.getX() + 0.5, plate.getY(), plate.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(mob);
			return mob.getUUID();
		});
		run.waitFor(server -> {
			ServerLevel level = server.overworld();
			Entity intruder = level.getEntity(cow);
			return intruder != null && CampRegistry.all(level).stream()
					.filter(c -> c.tier() == CampTier.FORT).findFirst().orElseThrow().guards().stream()
					.map(level::getEntity).filter(Mob.class::isInstance).map(Mob.class::cast)
					.anyMatch(guard -> guard.getTarget() == intruder);
		}, "the alarm plate rings and the guards sally out", 20 * 10);
		// Caltrops hurt what stands on them.
		UUID calf = run.compute(server -> {
			ServerLevel level = server.overworld();
			Mob mob = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.snapTo(spikes.getX() + 0.5, spikes.getY(), spikes.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(mob);
			return mob.getUUID();
		});
		run.context.waitTicks(15);
		run.server(server -> {
			Entity hurt = server.overworld().getEntity(calf);
			check(hurt == null || !hurt.isAlive() || ((LivingEntity) hurt).getHealth() < ((LivingEntity) hurt).getMaxHealth(),
					"caltrops hurt whoever walks on them");
		});
		// A powder keg in the Keep blows up when powered but breaks no blocks.
		run.server(server -> {
			ServerLevel level = server.overworld();
			BlockPos keg = blocks(level, keep, ModBlocks.POWDER_KEG).get(0);
			BlockPos control = keg.offset(0, 1, 0);
			level.setBlock(control, Blocks.STONE.defaultBlockState(), 3);
			level.setBlockAndUpdate(keg.offset(-1, 0, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
			check(level.getBlockState(keg).isAir(), "a powered keg blows up");
			check(level.getBlockState(control).is(Blocks.STONE), "the blast breaks no blocks");
		});
		// Claimed fort: traps off, dispensers still sealed until it has burnt down.
		run.server(server -> {
			ServerLevel level = server.overworld();
			for (UUID id : fort.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					guard.kill(level);
				}
			}
		});
		run.waitFor(server -> Camps.guardsLeft(server.overworld(), fort) == 0, "the Fort's guards fall", 40);
		// With the guards down (the camp is still armed until claimed) a cow stands on the arrow slit's tripwire: the
		// dispenser fires, and its arrow hits the cow, not thin air. Earlier, a sallied guard could stand in the line of fire.
		UUID mark = run.compute(server -> {
			ServerLevel level = server.overworld();
			BlockPos wire = blocks(level, fort, Blocks.TRIPWIRE).stream()
					.min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(dispenser))).orElseThrow();
			Mob mob = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.snapTo(wire.getX() + 0.5, wire.getY(), wire.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(mob);
			return mob.getUUID();
		});
		run.context.waitTicks(30);
		run.server(server -> {
			ServerLevel level = server.overworld();
			int left = level.getBlockEntity(dispenser) instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity box
					? box.getItem(0).getCount() : -1;
			check(left >= 0 && left < 9, "the tripwire fires the dispenser (" + left + " of 9 arrows left)");
			for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : level.getEntitiesOfClass(
					net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, new AABB(dispenser).inflate(24))) {
				check(arrow.pickup != net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED,
						"a trap arrow can't be picked up");
			}
			LivingEntity target = (LivingEntity) level.getEntity(mark);
			StringBuilder seen = new StringBuilder();
			for (Entity e : level.getEntities((Entity) null, new AABB(dispenser).inflate(6), e -> !(e instanceof ServerPlayer))) {
				seen.append(' ').append(e.getType().toShortString()).append('@')
						.append(String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", e.getX(), e.getY(), e.getZ()));
			}
			check(target == null || target.getHealth() < target.getMaxHealth(),
					"the trap's arrow hits whoever tripped the wire (dispenser " + dispenser.toShortString() + ", near:" + seen + ")");
		});
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camps.claim(level, Camps.standardPos(fort), player(server));
			check(CampRegistry.active(level, player(server).getUUID()).isEmpty(), "the Fort is claimed");
			check(!Camps.armedAt(level, plate), "claiming the camp disarms its traps");
			InteractionResult open = UseBlockCallback.EVENT.invoker().interact(player(server), level,
					InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dispenser), Direction.UP, dispenser, false));
			check(open == InteractionResult.FAIL, "a claimed camp's dispensers stay sealed while it burns (its ammo isn't loot)");
		});
	}

	private static void warlords(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 60.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			UUID stranger = UUID.randomUUID();
			// The Keep is the player's own map target so claiming it is legal once it falls.
			Camps.build(level, Camp.chosen(player(server).getUUID(), ground, CampTier.KEEP, ground.offset(-60, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(stranger, ground, CampTier.CAVALRY_ENCAMPMENT, ground.offset(-20, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(stranger, ground, CampTier.SUNKEN_CRYPT, ground.offset(20, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(stranger, ground, CampTier.ILLAGER_HALL, ground.offset(60, 0, 90), Rotation.NONE));
			for (Camp camp : CampRegistry.all(level)) {
				int half = camp.layout().half() + 4;
				for (int cx = (camp.center().getX() - half) >> 4; cx <= (camp.center().getX() + half) >> 4; cx++) {
					for (int cz = (camp.center().getZ() - half) >> 4; cz <= (camp.center().getZ() + half) >> 4; cz++) {
						level.setChunkForced(cx, cz, true);
						level.getChunk(cx, cz, ChunkStatus.FULL, true);
					}
				}
			}
		});
		run.context.waitTicks(30);
		Object[] found = run.compute(server -> {
			ServerLevel level = server.overworld();
			WarlordEntity[] lords = new WarlordEntity[4];
			for (CampTier tier : new CampTier[] {CampTier.KEEP, CampTier.CAVALRY_ENCAMPMENT, CampTier.SUNKEN_CRYPT, CampTier.ILLAGER_HALL}) {
				Camp camp = CampRegistry.all(level).stream().filter(c -> c.tier() == tier).findFirst().orElseThrow();
				List<WarlordEntity> inCamp = camp.guards().stream().map(level::getEntity)
						.filter(WarlordEntity.class::isInstance).map(WarlordEntity.class::cast).toList();
				check(inCamp.size() == 1, tier + " has exactly one warlord among its guards");
				WarlordEntity warlord = inCamp.get(0);
				check(warlord.isRaider(), "the " + tier + " warlord defends his camp");
				check(warlord.getCustomName() != null && !warlord.getCustomName().getString().isBlank(),
						"the " + tier + " warlord goes by a name");
				lords[tier == CampTier.KEEP ? 0 : tier == CampTier.CAVALRY_ENCAMPMENT ? 1 : tier == CampTier.SUNKEN_CRYPT ? 2 : 3] = warlord;
			}
			check(lords[0].isBoss() && lords[0].getMaxHealth() > lords[1].getMaxHealth(),
					"the Keep's warlord is the real boss (" + lords[0].getMaxHealth() + " vs " + lords[1].getMaxHealth() + ")");
			check(!lords[1].isBoss() && !lords[2].isBoss() && !lords[3].isBoss(), "the other Castle warlords are mini-bosses");
			return new Object[] {lords};
		});
		WarlordEntity[] lords = (WarlordEntity[]) found[0];
		run.command("effect give @p minecraft:slow_falling 30 0 true");
		run.lookAt(lords[0].blockPosition().offset(4, 8, 10), Vec3.atCenterOf(lords[0].blockPosition().above()));
		run.screenshot("warbands-warlord");
		// The keep's standard holds while its warlord stands: every guard but him falls, claim still refused.
		Camp keep = run.compute(server -> CampRegistry.all(server.overworld()).stream()
				.filter(c -> c.tier() == CampTier.KEEP).findFirst().orElseThrow());
		run.server(server -> {
			ServerLevel level = server.overworld();
			for (UUID id : keep.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard && !(guard instanceof WarlordEntity)) {
					guard.kill(level);
				}
			}
		});
		run.waitFor(server -> Camps.guardsLeft(server.overworld(), keep) == 1, "only the Keep's warlord stands", 40);
		run.server(server -> {
			ServerLevel level = server.overworld();
			Camps.claim(level, Camps.standardPos(keep), player(server));
			check(!CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.KEEP).findFirst().orElseThrow().conquered(),
					"the Keep's standard can't be claimed while its warlord stands");
			lords[0].kill(level);
		});
		run.waitFor(server -> Camps.guardsLeft(server.overworld(), keep) == 0, "the Keep's warlord falls", 40);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(level.getEntitiesOfClass(ItemEntity.class, new AABB(lords[0].blockPosition()).inflate(6),
					item -> item.getItem().is(Items.NETHERITE_SWORD)
							&& Identifier.fromNamespaceAndPath("warbands", "warlord_blade")
									.equals(item.getItem().get(DataComponents.ITEM_MODEL))
							&& item.getItem().get(DataComponents.CUSTOM_NAME) != null).size() == 1,
					"the Keep's warlord drops his named blade once");
			Camps.claim(level, Camps.standardPos(keep), player(server));
			check(CampRegistry.all(level).stream().filter(c -> c.tier() == CampTier.KEEP).findFirst().orElseThrow().conquered(),
					"the Keep's standard is claimed once its warlord falls");
		});
	}

	private static void relics(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos ground = run.ground;
		run.server(server -> {
			ServerLevel level = server.overworld();
			level.getGameRules().set(ModGameRules.RAIDS, false, server);
			ServerPlayer player = player(server);
			Camps.build(level, Camp.chosen(player.getUUID(), ground, CampTier.WITCH_COVEN, ground.offset(-30, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(player.getUUID(), ground, CampTier.WITCH_COVEN, ground.offset(30, 0, 90), Rotation.NONE));
			Camps.build(level, Camp.chosen(player.getUUID(), ground, CampTier.BANDIT_CAMP, ground.offset(60, 0, 90), Rotation.NONE));
			for (Camp camp : CampRegistry.all(level)) {
				int half = camp.layout().half() + 4;
				for (int cx = (camp.center().getX() - half) >> 4; cx <= (camp.center().getX() + half) >> 4; cx++) {
					for (int cz = (camp.center().getZ() - half) >> 4; cz <= (camp.center().getZ() + half) >> 4; cz++) {
						level.setChunkForced(cx, cz, true);
						level.getChunk(cx, cz, ChunkStatus.FULL, true);
					}
				}
			}
		});
		run.context.waitTicks(20);
		Object[] out = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			// The first Witch Coven hands its relic; the second coven of the same kind doesn't.
			Camp first = campAt(level, ground.offset(-30, 0, 90));
			for (UUID id : first.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					guard.kill(level);
				}
			}
			Camps.claim(level, Camps.standardPos(first), player);
			check(relics(player, ModItems.HAG_CHARM) == 1, "the first Witch Coven hands its Hag's Charm");
			Camp second = campAt(level, ground.offset(30, 0, 90));
			for (UUID id : second.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					guard.kill(level);
				}
			}
			Camps.claim(level, Camps.standardPos(second), player);
			check(relics(player, ModItems.HAG_CHARM) == 1, "a second Witch Coven hands no second charm");
			// A non-fantasy kind gives no relic at all.
			Camp bandit = campAt(level, ground.offset(60, 0, 90));
			for (UUID id : bandit.guards()) {
				if (level.getEntity(id) instanceof LivingEntity guard) {
					guard.kill(level);
				}
			}
			Camps.claim(level, Camps.standardPos(bandit), player);
			check(relics(player, ModItems.HAG_CHARM) == 1 && relics(player, ModItems.FROST_SIGIL) == 0
					&& relics(player, ModItems.GRAVE_LANTERN) == 0 && relics(player, ModItems.OMEN_BANNER) == 0,
					"a Bandit Camp gives no relic");
			// The pedestal enshrines a relic it touches, inside its town's reach only.
			BlockPos pedestal = town.core().offset(4, 0, 4);
			level.setBlock(pedestal, ModBlocks.RELIC_PEDESTAL.defaultBlockState(), 3);
			level.getBlockState(pedestal).useItemOn(new ItemStack(ModItems.HAG_CHARM), level, player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(pedestal), Direction.UP, pedestal, false));
			check(Relics.has(level, town.core(), CampTier.WITCH_COVEN), "the Hag's Charm is enshrined");
			check(!Relics.enshrine(level, town.core(), CampTier.WITCH_COVEN), "the same relic won't stack");
			check(level.getBlockState(pedestal).getValue(RelicPedestalBlock.HAG)
					&& !level.getBlockState(pedestal).getValue(RelicPedestalBlock.FROST)
					&& level.getBlockState(pedestal).getLightEmission() > 0, "the pedestal lights the Hag's Charm gem and glows");
			check(Relics.enshrine(level, town.core(), CampTier.SUNKEN_CRYPT), "the Grave Lantern enshrines");
			Run.roundTrip(level, ModAttachments.RELICS, "enshrined and earned relics");
			// A pedestal placed later shows the whole collection at once, at full light.
			Relics.enshrine(level, town.core(), CampTier.FROZEN_OUTPOST);
			Relics.enshrine(level, town.core(), CampTier.ILLAGER_HALL);
			BlockPos full = pedestal.offset(2, 0, 0);
			level.setBlock(full, ModBlocks.RELIC_PEDESTAL.defaultBlockState(), 3);
			ModBlocks.RELIC_PEDESTAL.setPlacedBy(level, full, level.getBlockState(full), player, ItemStack.EMPTY);
			check(level.getBlockState(full).getLightEmission() == 15, "a new pedestal shows all four relics, fully lit");
			// The Grave Lantern's dead sap a hostile mob near the Town Center.
			LivingEntity zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			zombie.setPos(Vec3.atCenterOf(town.core().offset(10, 0, 10)));
			level.addFreshEntity(zombie);
			return new Object[] {zombie.getUUID(), pedestal};
		});
		run.context.waitTicks(Relics.EFFECT_TICKS + 10);
		run.server(server -> {
			ServerLevel level = server.overworld();
			LivingEntity zombie = (LivingEntity) level.getEntity((UUID) out[0]);
			check(zombie != null && zombie.hasEffect(MobEffects.WEAKNESS), "the Grave Lantern saps foes near the town");
			BlockPos pedestal = (BlockPos) out[1];
			check(level.getBlockState(pedestal).is(ModBlocks.RELIC_PEDESTAL), "the Relic Pedestal stands");
		});
		run.lookAt(((BlockPos) out[1]).offset(4, 5, 4), Vec3.atCenterOf((BlockPos) out[1]));
		run.screenshot("warbands-relic-pedestal");
		// The Cavalry Encampment's whistle calls a horse the player can ride, and it stays.
		UUID horse = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			BlockPos floor = Run.groundAt(level, town.core().getX() - 8, town.core().getZ());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WARHORSE_WHISTLE));
			player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
			check(player.getMainHandItem().isEmpty(), "the whistle is used up");
			return level.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Horse.class, new AABB(floor).inflate(3))
					.stream().findFirst().map(Entity::getUUID).orElseThrow(() -> new AssertionError("Check failed: the whistle calls a horse"));
		});
		run.context.waitTicks(40);
		run.server(server -> {
			ServerPlayer player = player(server);
			Entity entity = server.overworld().getEntity(horse);
			check(entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse h && h.isAlive(), "the called warhorse stays");
			var called = (net.minecraft.world.entity.animal.equine.AbstractHorse) entity;
			check(called.isTamed() && called.getOwnerReference() != null
					&& player.getUUID().equals(called.getOwnerReference().getUUID()), "the warhorse is tamed to the caller");
			check(called.isSaddled() && called.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY).is(Items.DIAMOND_HORSE_ARMOR),
					"the warhorse is saddled and in diamond barding");
			check(called.mobInteract(player, InteractionHand.MAIN_HAND).consumesAction() || player.getVehicle() == called,
					"the player can mount the warhorse");
		});
	}

	private static Camp campAt(ServerLevel level, BlockPos center) {
		return CampRegistry.all(level).stream().filter(c -> c.center().equals(center)).findFirst().orElseThrow();
	}

	private static int relics(ServerPlayer player, net.minecraft.world.item.Item relic) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(relic)) {
				count += player.getInventory().getItem(i).getCount();
			}
		}
		return count;
	}
}
