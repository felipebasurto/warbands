package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.activeRaid;
import static dev.felipe.warbands.gametest.Run.activeRaiders;
import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.place;
import static dev.felipe.warbands.gametest.Run.player;
import static dev.felipe.warbands.gametest.Run.raider;
import static dev.felipe.warbands.gametest.Run.soldier;
import static dev.felipe.warbands.gametest.Run.townCenter;
import static dev.felipe.warbands.gametest.Run.training;
import static dev.felipe.warbands.gametest.Run.trainingCore;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.entity.ArcherEntity;
import dev.felipe.warbands.entity.CrossbowmanEntity;
import dev.felipe.warbands.entity.KnightEntity;
import dev.felipe.warbands.entity.RamEntity;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.entity.WarhorseEntity;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.raid.RaidComposition;
import dev.felipe.warbands.raid.RaidKind;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.raid.RaidRegistry.TownRaids;
import dev.felipe.warbands.raid.RaidRegistry.ActiveRaid;
import dev.felipe.warbands.raid.RaidRegistry.Phase;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.raid.Wrecking;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.BuildingRegistry;
import dev.felipe.warbands.town.Repairs;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The raider faction, raids (the happy paths and the corner cases), wrecking, rams, and repairs. */
final class RaidScenarios {
	private RaidScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("raiders.faction", "raiders can't be claimed; 10 of your soldiers fight 5 on sight and win (photo)", RaidScenarios::faction));
		all.add(new Scenario("raids.won", "a Castle raid: one at a time, size, spawn distance, ram on Charge, deaths counted one by one, gold, advancement (photo)",
				RaidScenarios::won));
		all.add(new Scenario("raids.kinds", "each raid kind brings its own roster; Shield Wall is tougher, Cavalry knights ride fast",
				RaidScenarios::kinds));
		all.add(new Scenario("raids.bag", "the per-town bag never serves the same raid kind twice in a row, across refills",
				RaidScenarios::bag));
		all.add(new Scenario("raids.village-revenge", "a Village town on the raid calendar (it took a Feudal camp) is raided as Feudal, not a crash",
				RaidScenarios::villageRevenge));
		all.add(new Scenario("raids.plunder", "a raider 5 s at the core takes a fifth of every resource, then they retreat and vanish", RaidScenarios::plunder));
		all.add(new Scenario("raids.defended", "you within 12 blocks of the Town Center: a raider on the core loots nothing", RaidScenarios::defended));
		all.add(new Scenario("raids.unfound-raiders", "raiders the raid can't find (unloaded) don't count as dead: no free win, no gold",
				RaidScenarios::unfoundRaiders));
		all.add(new Scenario("raids.owner-away", "owner leaves: no plunder and the timeout clock stops; owner back: the raid carries on",
				RaidScenarios::ownerAway));
		all.add(new Scenario("raids.timeout", "ten minutes of attack without a result: they leave with nothing", RaidScenarios::timeout));
		all.add(new Scenario("raids.off-switches", "gamerule off and Peaceful each end a raid and remove the raiders", RaidScenarios::offSwitches));
		all.add(new Scenario("raids.town-destroyed", "breaking the Town Center mid-raid sends the raiders home and forgets the raid",
				RaidScenarios::townDestroyed));
		all.add(new Scenario("raids.stale-raider", "a raider whose raid is over removes itself; a camp guard stays", RaidScenarios::staleRaider));
		all.add(new Scenario("raids.wrecking", "raiders break building blocks (no drop) and your fences (drop), never with mobGriefing off",
				RaidScenarios::wrecking));
		all.add(new Scenario("raids.rams", "raider rams obey mobGriefing, your ram doesn't; rams break the Reinforced Gate; player blocks drop",
				RaidScenarios::rams));
		all.add(new Scenario("repair.buildings", "a half-wrecked Barracks stops, Repair refuses during a raid, then restores it for 1 stone a block",
				RaidScenarios::repair));
	}

	// ---- helpers ----

	/** Starts a raid now, skips the minute of warning, and waits for the raiders. */
	private static void startRaid(Run run, BlockPos core) {
		startRaid(run, core, null);
	}

	/** {@code kind} (null for the bag's draw) is the raid kind the raid should come as. */
	private static void startRaid(Run run, BlockPos core, @Nullable RaidKind kind) {
		run.server(server -> {
			check(Raids.startNow(server.overworld(), core, kind), "a raid starts on the Town Center");
			Raids.skipWarning(server.overworld(), core);
		});
		run.waitFor(server -> !activeRaiders(server, core).isEmpty(), "raiders arrive after the warning", 100);
	}

	private static int[] stock(MinecraftServer server, BlockPos core) {
		TownCenterBlockEntity town = townCenter(server.overworld(), core);
		return new int[] {town.stock(Resource.FOOD), town.stock(Resource.WOOD), town.stock(Resource.STONE), town.stock(Resource.GOLD)};
	}

	private static boolean sameStock(MinecraftServer server, BlockPos core, int[] before) {
		return java.util.Arrays.equals(stock(server, core), before);
	}

	/** A chest set to roll {@code loot} on first open, within {@code radius} columns of {@code near}. */
	private static @Nullable BlockPos warChest(ServerLevel level, BlockPos near, int radius, ResourceKey<LootTable> loot) {
		for (BlockPos pos : BlockPos.betweenClosed(near.offset(-radius, -10, -radius), near.offset(radius, 10, radius))) {
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && loot.equals(chest.getLootTable())) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static Phase phase(MinecraftServer server, BlockPos core) {
		ActiveRaid active = activeRaid(server, core);
		return active == null ? null : active.phase();
	}

	/** Replaces the saved raid with {@code change}d copy (for skipping time in tests). */
	private static void edit(ServerLevel level, BlockPos core, java.util.function.UnaryOperator<ActiveRaid> change) {
		RaidRegistry.TownRaids raids = RaidRegistry.get(level, core).orElseThrow();
		RaidRegistry.put(level, new RaidRegistry.TownRaids(raids.town(), raids.owner(), raids.nextRaid(), raids.won(), raids.retaliation(),
				Optional.of(change.apply(raids.active().orElseThrow())), raids.bag(), raids.last()));
	}

	private static void freeze(MinecraftServer server, BlockPos core) {
		for (SoldierEntity raider : activeRaiders(server, core)) {
			raider.setNoAi(true);
			if (raider.getVehicle() instanceof Mob mount) {
				mount.setNoAi(true);
			}
		}
	}

	private static long drops(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), e -> e.getItem().is(item)).size();
	}

	// ---- scenarios ----

	private static void faction(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 0.0F, 15.0F);
		run.context.waitTicks(10);
		List<UnitType> kinds = List.of(UnitType.MILITIA, UnitType.ARCHER, UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.KNIGHT);
		List<UUID> lineup = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			List<UUID> ids = new ArrayList<>();
			for (int i = 0; i < kinds.size(); i++) {
				SoldierEntity r = raider(level, kinds.get(i), new Vec3(player.getX() - 4.4 + i * 2.2, player.getY(), player.getZ() + 5.5), Age.CASTLE, null);
				r.setNoAi(true);
				ids.add(r.getUUID());
				check(r.isRaider() && !r.isTame(), "raiders are an ownerless faction");
				check(r.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS && !r.isTame(), "a raider can't be claimed with a right-click");
			}
			return ids;
		});
		run.context.waitTicks(20);
		run.server(server -> {
			for (UUID id : lineup) {
				if (server.overworld().getEntity(id) instanceof SoldierEntity r) {
					Entity body = r.getVehicle() != null ? r.getVehicle() : r;
					body.snapTo(body.getX(), body.getY(), body.getZ(), 180.0F, 0.0F);
					r.setYHeadRot(180.0F);
					r.setYBodyRot(180.0F);
					if (body instanceof Mob mob) {
						mob.setNoAi(true);
					}
				}
			}
		});
		run.context.waitTicks(10);
		run.cleanShot("warbands-raiders");
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			dev.felipe.warbands.town.AgeRegistry.raise(server, player.getUUID(), Age.CASTLE);
			// Two to one: an even fight between equal soldiers is a coin toss (a 6-v-5 run lost), and this checks behaviour.
			for (UnitType type : List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.SPEARMAN,
					UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.KNIGHT, UnitType.KNIGHT, UnitType.ARCHER)) {
				soldier(level, type, player.position().add(0, 0, -2), player);
			}
			for (UUID id : lineup) {
				if (level.getEntity(id) instanceof SoldierEntity r) {
					r.setNoAi(false);
					if (r.getVehicle() instanceof Mob mount) {
						mount.setNoAi(false);
					}
				}
			}
		});
		run.waitFor(server -> lineup.stream().noneMatch(id -> server.overworld().getEntity(id) instanceof SoldierEntity s && s.isAlive()),
				"your soldiers fight raiders on sight and win", 20 * 60);
	}

	private static void won(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos core = town.core();
		int goldBefore = run.compute(server -> townCenter(server.overworld(), core).stock(Resource.GOLD));
		startRaid(run, core, RaidKind.WARBAND);
		BlockPos raidSpawn = run.compute(server -> activeRaid(server, core).spawn());
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(RaidRegistry.isActive(level, core), "the raid is active");
			check(!Raids.startNow(level, core), "only one raid at a time");
			List<SoldierEntity> raiders = activeRaiders(server, core);
			int expected = Raids.sizeFor(level, core, Age.CASTLE, 0, false);
			check(raiders.size() == expected, "a first Castle raid brings " + expected + " raiders on this difficulty, got " + raiders.size());
			check(raiders.stream().allMatch(r -> r.isRaider() && core.equals(r.marchTarget())), "raiders march on the Town Center");
			double near = raiders.stream().mapToDouble(r -> Math.sqrt(r.blockPosition().distSqr(core))).min().orElse(0);
			check(near > 40 && near < 72, "raiders arrive 48-64 blocks out (nearest " + Math.round(near) + ")");
			check(raiders.stream().anyMatch(r -> r instanceof RamEntity && r.stance() == Stance.CHARGE), "a Castle raid brings a ram on Charge");
			check(raiders.stream().noneMatch(SoldierEntity::isTame), "raiders have no owner");
			freeze(server, core); // hold still for the photo
		});
		BlockPos view = run.compute(server -> activeRaiders(server, core).getFirst().blockPosition());
		run.command("effect give @p minecraft:slow_falling 30 0 true");
		BlockPos eye = view.offset(view.getX() > core.getX() ? 9 : -9, 6, view.getZ() > core.getZ() ? 9 : -9);
		run.lookAt(eye, Vec3.atCenterOf(view));
		run.screenshot("warbands-raid");
		run.tp(town.center().getX() + 0.5, town.center().getY() + 1, town.center().getZ() + 14.5, 180.0F, 0.0F);
		run.context.waitTicks(40); // knights have mounted by now
		// Deaths count one at a time: the knight first, and its horse goes with it.
		UUID horse = run.compute(server -> {
			SoldierEntity knight = activeRaiders(server, core).stream().filter(r -> r instanceof KnightEntity).findFirst().orElseThrow();
			check(knight.getVehicle() instanceof WarhorseEntity, "the raider knight rides a warhorse");
			UUID id = knight.getVehicle().getUUID();
			knight.kill(server.overworld());
			return id;
		});
		run.waitFor(server -> server.overworld().getEntity(horse) == null, "the raider knight's horse leaves with it", 60);
		run.waitFor(server -> {
			ActiveRaid active = activeRaid(server, core);
			return active != null && active.raiders().size() == active.total() - 1;
		}, "the raid counts one raider fewer", 40);
		// Killed, not discarded: a raid counts deaths (a raider it can't find may just be in an unloaded chunk).
		run.server(server -> activeRaiders(server, core).forEach(r -> r.kill(server.overworld())));
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), "the raid is defeated", 100);
		run.server(server -> {
			ServerLevel level = server.overworld();
			int paid = townCenter(level, core).stock(Resource.GOLD) - goldBefore;
			check(paid == 10 * RaidComposition.goldPerRaider(Age.CASTLE), "a won Castle raid pays 25 gold per raider (paid " + paid + ")");
			check(RaidRegistry.get(level, core).orElseThrow().won() == 1, "the win is counted (next raids grow)");
			check(RaidRegistry.get(level, core).orElseThrow().nextRaid() > level.getGameTime() + Raids.MIN_INTERVAL - 40,
					"the next raid is 4-6 days away");
			check(RaidComposition.size(Age.CASTLE, 1, false) == 11 && RaidComposition.size(Age.CASTLE, 1, true) == 12,
					"raids grow by one per win, and a revenge raid by one more");
			check(Run.hasAdvancement(server, WarbandsAdvancements.RAID_DEFENDED), "advancement for defending a raid");
			// The raiders' war chest: where they came from, or in a ring around the Town Center.
			BlockPos chestPos = warChest(level, raidSpawn, 6, RaidKind.WARBAND.lootTable());
			if (chestPos == null) {
				chestPos = warChest(level, core, 8, RaidKind.WARBAND.lootTable());
			}
			check(chestPos != null, "the beaten raiders left their war chest behind");
			boolean signalFire = false;
			if (chestPos != null) {
				for (BlockPos near : BlockPos.betweenClosed(chestPos.offset(-2, -2, -2), chestPos.offset(2, 2, 2))) {
					BlockState state = level.getBlockState(near);
					if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.SIGNAL_FIRE)) {
						signalFire = true;
					}
				}
			}
			check(signalFire, "a signal campfire burns beside the war chest");
		});
	}

	/** Every kind on a Castle town, forced: the roster, the dress and the spoils are the kind's own. */
	private static void kinds(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos core = town.core();
		run.server(server -> check(RaidKind.forAge(Age.FEUDAL).size() == 3 && RaidKind.forAge(Age.CASTLE).size() == 5,
				"a Feudal town draws 3 raid kinds and a Castle town all 5"));
		for (RaidKind kind : RaidKind.values()) {
			startRaid(run, core, kind);
			run.server(server -> {
				ActiveRaid active = activeRaid(server, core);
				check(active != null && active.kind() == kind, "the raid knows it is a " + kind.serializedName());
				List<UnitType> want = new ArrayList<>(RaidComposition.units(kind, Age.CASTLE, active.total()));
				List<UnitType> got = activeRaiders(server, core).stream()
						.map(r -> Arrays.stream(UnitType.values()).filter(t -> t.entityType() == r.getType()).findFirst().orElseThrow())
						.sorted().collect(java.util.stream.Collectors.toList());
				check(want.stream().sorted().toList().equals(got.stream().sorted().toList()),
						kind.serializedName() + " brings its own roster (" + got + ")");
				if (kind == RaidKind.SHIELD_WALL) {
					check(activeRaiders(server, core).stream().allMatch(r -> r.getAttribute(Attributes.ARMOR) != null
							&& r.getAttribute(Attributes.ARMOR).hasModifier(Warbands.id("raid_shield_wall"))),
							"Shield Wall raiders carry extra armor");
				}
				if (kind == RaidKind.NIGHT_STALKERS) {
					check(activeRaiders(server, core).stream().allMatch(
							r -> r instanceof ArcherEntity || r instanceof CrossbowmanEntity), "Night Stalkers are all ranged");
				}
			});
			if (kind == RaidKind.CAVALRY) {
				// The knights mount their warhorses on the first tick.
				run.waitFor(server -> activeRaiders(server, core).stream().filter(r -> r instanceof KnightEntity)
						.allMatch(r -> r.getVehicle() instanceof WarhorseEntity), "the Cavalry's knights ride", 60);
				run.server(server -> check(activeRaiders(server, core).stream().filter(r -> r instanceof KnightEntity)
						.allMatch(r -> ((WarhorseEntity) r.getVehicle()).getAttribute(Attributes.MOVEMENT_SPEED)
								.hasModifier(RaidKind.CAVALRY_HORSE_ID)),
						"Cavalry horses are bred faster"));
			}
			run.server(server -> activeRaiders(server, core).forEach(r -> r.kill(server.overworld())));
			run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), kind.serializedName() + " raid ends", 200);
		}
	}

	/**
	 * Draws against an empty bag 10 times: each refill must not serve the kind the town just had. The last kind comes
	 * from the saved record, so this also proves a finished raid keeps it.
	 */
	private static void bag(Run run) {
		Run.Town town = run.town(Age.CASTLE);
		BlockPos core = town.core();
		RaidKind[] last = {null};
		for (int i = 0; i < 10; i++) {
			int draw = i + 1;
			run.server(server -> {
				ServerLevel level = server.overworld();
				// The registry entry may not exist yet (Raids.tick creates it lazily on the first town tick).
				TownRaids raids = RaidRegistry.get(level, core).orElse(new TownRaids(core, townCenter(level, core).owner(),
						level.getGameTime(), 0, false, Optional.empty()));
				RaidRegistry.put(level, new TownRaids(raids.town(), raids.owner(), level.getGameTime(), raids.won(),
						raids.retaliation(), Optional.empty(), List.of(), raids.last()));
				check(Raids.startNow(level, core), "raid " + draw + " starts");
				Raids.skipWarning(level, core);
			});
			run.waitFor(server -> !activeRaiders(server, core).isEmpty(), "raiders arrive", 100);
			run.server(server -> {
				RaidKind kind = activeRaid(server, core).kind();
				check(last[0] == null || kind != last[0],
						"draw " + draw + ": " + kind.serializedName() + " isn't " + (last[0] == null ? "?" : last[0].serializedName()) + " again");
				last[0] = kind;
				activeRaiders(server, core).forEach(r -> r.kill(server.overworld()));
			});
			run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), "the raid ends", 200);
			run.server(server -> check(RaidRegistry.get(server.overworld(), core).flatMap(TownRaids::last)
					.equals(Optional.ofNullable(last[0])), "draw " + draw + ": the finished raid remembers its kind"));
			run.server(server -> Run.roundTrip(server.overworld(), ModAttachments.RAIDS, "the raid calendar and kind bag"));
		}
	}

	/**
	 * A Village town gets a raid entry when it takes a Feudal camp (the operator's {@code /warbands camp} works at any
	 * age). No raid kind starts below Feudal, so the kind bag must still fill: raided as Feudal, like start() does.
	 */
	private static void villageRevenge(Run run) {
		Run.Town town = run.town(Age.VILLAGE);
		BlockPos core = town.core();
		run.server(server -> {
			ServerLevel level = server.overworld();
			// Overdue by more than a day: it starts whatever the time of day.
			RaidRegistry.put(level, new TownRaids(core, townCenter(level, core).owner(), level.getGameTime() - Raids.DAY - 1, 0,
					true, Optional.empty()));
		});
		run.waitFor(server -> RaidRegistry.isActive(server.overworld(), core), "the Village town's revenge raid starts", 100);
		run.server(server -> {
			check(activeRaid(server, core).age() == Age.FEUDAL, "it comes as a Feudal raid");
			Raids.skipWarning(server.overworld(), core);
		});
		run.waitFor(server -> !activeRaiders(server, core).isEmpty(), "raiders arrive", 100);
		run.server(server -> activeRaiders(server, core).forEach(r -> r.kill(server.overworld())));
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), "the raid ends", 200);
	}

	private static void plunder(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		int[] before = run.compute(server -> {
			freeze(server, core);
			// Out of the 12-block defence range, still near enough for the raid to run.
			player(server).teleportTo(core.getX() + 30.5, core.getY() + 1, core.getZ() + 0.5);
			SoldierEntity thief = activeRaiders(server, core).stream().filter(r -> !(r instanceof RamEntity) && !(r instanceof KnightEntity))
					.findFirst().orElseThrow();
			thief.teleportTo(core.getX() + 0.5, core.getY(), core.getZ() + 2.5);
			return stock(server, core);
		});
		run.waitFor(server -> phase(server, core) == Phase.RETREAT, "a raider at the core plunders, then they retreat", 20 * 15);
		run.server(server -> {
			int[] after = stock(server, core);
			for (Resource resource : Resource.values()) {
				int i = resource.ordinal();
				int expected = before[i] - (int) Math.floor(before[i] * Raids.PLUNDER_SHARE);
				check(after[i] == expected, resource.name().toLowerCase() + ": raiders took a fifth (" + before[i] + " -> " + after[i] + ", expected " + expected + ")");
			}
			// Walking 40 blocks out takes a minute: put the thief there, where retreating raiders vanish.
			activeRaiders(server, core).forEach(r -> r.teleportTo(core.getX() + 45.5, core.getY(), core.getZ() + 0.5));
		});
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core) && activeRaiders(server, core).isEmpty(),
				"after the plunder the raid ends and raiders 40 blocks out vanish", 100);
		run.server(server -> check(RaidRegistry.get(server.overworld(), core).orElseThrow().won() == 0, "a plundered raid isn't a win"));
	}

	private static void defended(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		int[] before = run.compute(server -> {
			freeze(server, core);
			player(server).teleportTo(core.getX() + 6.5, core.getY() + 1, core.getZ() + 0.5);
			SoldierEntity thief = activeRaiders(server, core).stream().filter(r -> !(r instanceof RamEntity) && !(r instanceof KnightEntity))
					.findFirst().orElseThrow();
			thief.teleportTo(core.getX() + 0.5, core.getY(), core.getZ() + 2.5);
			return stock(server, core);
		});
		run.holds(server -> phase(server, core) == Phase.ATTACK && sameStock(server, core, before),
				"with the owner next to the Town Center, a raider on the core loots nothing", 20 * 7);
	}

	private static void unfoundRaiders(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		int[] before = run.compute(server -> {
			// Removed without dying, the way a raider in an unloaded chunk is missing from the level.
			for (SoldierEntity raider : activeRaiders(server, core)) {
				Entity vehicle = raider.getVehicle();
				raider.discard();
				if (vehicle != null) {
					vehicle.discard();
				}
			}
			return stock(server, core);
		});
		run.holds(server -> {
			ActiveRaid active = activeRaid(server, core);
			return active != null && active.phase() == Phase.ATTACK && active.raiders().size() == active.total() && sameStock(server, core, before);
		}, "raiders that can't be found still count: no win, no gold", 80);
	}

	private static void ownerAway(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		int[] before = run.compute(server -> {
			freeze(server, core);
			SoldierEntity thief = activeRaiders(server, core).stream().filter(r -> !(r instanceof KnightEntity)).findFirst().orElseThrow();
			// Leave (120 blocks: past the 96-block owner range) and put a thief on the core in the same tick.
			player(server).teleportTo(core.getX() + 120.5, core.getY() + 1, core.getZ() + 0.5);
			thief.teleportTo(core.getX() + 0.5, core.getY(), core.getZ() + 2.5);
			return stock(server, core);
		});
		int elapsed = run.compute(server -> activeRaid(server, core).elapsed());
		run.holds(server -> phase(server, core) == Phase.ATTACK && sameStock(server, core, before),
				"with the owner away, a raider on the core steals nothing", 20 * 8);
		run.server(server -> check(activeRaid(server, core).elapsed() <= elapsed + 20,
				"the timeout clock stood still while the owner was away (" + elapsed + " -> " + activeRaid(server, core).elapsed() + ")"));
		run.tp(town.center().getX() + 0.5, town.center().getY() + 1, town.center().getZ() + 14.5, 180.0F, 0.0F);
		run.waitFor(server -> phase(server, core) == Phase.RETREAT && !sameStock(server, core, before),
				"with the owner back, the raid carries on (the thief plunders)", 20 * 20);
	}

	private static void timeout(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		int[] before = run.compute(server -> {
			freeze(server, core);
			// Nine minutes 59 seconds of attack already on the clock.
			edit(server.overworld(), core, a -> new ActiveRaid(a.phase(), a.phaseStart(), a.started(), a.spawn(), a.raiders(), a.total(), a.age(),
					0, Raids.TIMEOUT_TICKS - 20));
			return stock(server, core);
		});
		run.waitFor(server -> phase(server, core) == Phase.RETREAT, "after ten minutes the raiders give up", 80);
		run.server(server -> check(sameStock(server, core, before), "a timed-out raid takes nothing"));
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core) && activeRaiders(server, core).isEmpty(),
				"retreating raiders far from the town vanish", 20 * 70);
		run.server(server -> check(RaidRegistry.get(server.overworld(), core).orElseThrow().won() == 0, "a timed-out raid isn't a win"));
	}

	private static void offSwitches(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		run.command("gamerule warbands:raids false");
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core) && activeRaiders(server, core).isEmpty(),
				"turning the gamerule off ends the raid and removes the raiders", 100);
		run.server(server -> check(!Raids.raidsOn(server.overworld()), "no raids with the gamerule off"));
		run.command("gamerule warbands:raids true");
		startRaid(run, core);
		List<UUID> ids = run.compute(server -> activeRaiders(server, core).stream().map(Entity::getUUID).toList());
		run.command("difficulty peaceful");
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core) && ids.stream().allMatch(id -> server.overworld().getEntity(id) == null),
				"Peaceful ends the raid and removes the raiders", 100);
		run.server(server -> check(!Raids.raidsOn(server.overworld()), "no raids on Peaceful"));
		run.command("difficulty easy");
	}

	private static void townDestroyed(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		startRaid(run, core);
		List<UUID> ids = run.compute(server -> activeRaiders(server, core).stream().map(Entity::getUUID).toList());
		run.server(server -> server.overworld().destroyBlock(core, false));
		run.waitFor(server -> RaidRegistry.get(server.overworld(), core).isEmpty() && ids.stream().allMatch(id -> server.overworld().getEntity(id) == null),
				"a raid on a destroyed Town Center is forgotten and its raiders leave", 100);
	}

	private static void staleRaider(Run run) {
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		UUID[] ids = run.compute(server -> {
			ServerLevel level = server.overworld();
			Vec3 spot = Vec3.atBottomCenterOf(core.offset(0, 0, 30));
			SoldierEntity stale = raider(level, UnitType.MILITIA, spot, Age.FEUDAL, core);
			SoldierEntity guard = raider(level, UnitType.MILITIA, spot.add(3, 0, 0), Age.FEUDAL, null);
			stale.setNoAi(true);
			guard.setNoAi(true);
			return new UUID[] {stale.getUUID(), guard.getUUID()};
		});
		run.waitFor(server -> server.overworld().getEntity(ids[0]) == null, "a raider of a raid that isn't happening removes itself", 150);
		run.server(server -> check(server.overworld().getEntity(ids[1]) instanceof SoldierEntity s && s.isAlive(),
				"a raider with no raid (a camp guard) stays"));
	}

	private static void wrecking(Run run) {
		Run.Town town = run.placeTown();
		BlockPos core = town.core();
		BlockPos ground = run.ground;
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			TownCenterBlockEntity center = townCenter(level, core);
			for (int i = 0; i < 4; i++) {
				center.deposit(new ItemStack(Items.COBBLESTONE, 64));
			}
			BlockPos house = BlueprintPlacer.centerFor(ground.offset(14, 0, 14), Direction.NORTH, Blueprint.HOUSE);
			place(level, player, ground.offset(14, 0, 14), Blueprint.HOUSE);
			int missingBefore = Repairs.missingAround(level, center);
			SoldierEntity wrecker = raider(level, UnitType.MILITIA, new Vec3(house.getX() - 3.5, house.getY() + 1, house.getZ() + 0.5), Age.CASTLE, null);
			check(Wrecking.wreck(level, wrecker, core, player.getUUID()), "a raider breaks a block of a house");
			check(Repairs.missingAround(level, center) == missingBefore + 1, "the broken block counts as missing");
			check(level.getEntitiesOfClass(ItemEntity.class, new AABB(house).inflate(6)).isEmpty(), "wrecked building blocks drop nothing");
			wrecker.discard();

			// The player's own fence, away from any building: it breaks, and drops.
			BlockPos fence = ground.offset(30, 1, 40);
			level.setBlockAndUpdate(fence, Blocks.OAK_FENCE.defaultBlockState());
			SoldierEntity other = raider(level, UnitType.MILITIA, Vec3.atBottomCenterOf(fence.offset(1, 0, 0)), Age.CASTLE, null);
			check(Wrecking.wreck(level, other, core, player.getUUID()) && level.getBlockState(fence).isAir(), "a raider breaks a player's fence");
			check(drops(level, fence, Items.OAK_FENCE) == 1, "the player's fence drops as an item");

			// mobGriefing off: nothing breaks.
			level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
			level.setBlockAndUpdate(fence, Blocks.OAK_FENCE.defaultBlockState());
			check(!Wrecking.wreck(level, other, core, player.getUUID()) && level.getBlockState(fence).is(Blocks.OAK_FENCE),
					"with mobGriefing off raiders break nothing");
			SoldierEntity third = raider(level, UnitType.MILITIA, new Vec3(house.getX() - 3.5, house.getY() + 1, house.getZ() + 0.5), Age.CASTLE, null);
			check(!Wrecking.wreck(level, third, core, player.getUUID()), "with mobGriefing off raiders leave buildings alone too");
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
		});
	}

	private static void rams(Run run) {
		BlockPos ground = run.ground;
		run.tp(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 20.5, 180.0F, 0.0F);
		run.context.waitTicks(10);
		// A ram faces north at each spot with a fence just ahead of it. Rams on Charge smash every half second.
		BlockPos raiderSpot = ground.offset(-10, 1, 0);
		BlockPos ownSpot = ground.offset(10, 1, 0);
		BlockPos gateSpot = ground.offset(20, 1, 0);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
			level.setBlockAndUpdate(raiderSpot.north(), Blocks.OAK_FENCE.defaultBlockState());
			level.setBlockAndUpdate(ownSpot.north(), Blocks.OAK_FENCE.defaultBlockState());
			level.setBlockAndUpdate(gateSpot.north(), ModBlocks.REINFORCED_GATE.defaultBlockState());
			SoldierEntity enemy = raider(level, UnitType.RAM, Vec3.atBottomCenterOf(raiderSpot), Age.CASTLE, null);
			SoldierEntity own = soldier(level, UnitType.RAM, Vec3.atBottomCenterOf(ownSpot), player);
			SoldierEntity gate = soldier(level, UnitType.RAM, Vec3.atBottomCenterOf(gateSpot), player);
			ArmyOrders.set(server, player.getUUID(), Stance.CHARGE); // your rams follow the army's order
			for (SoldierEntity ram : List.of(enemy, own, gate)) {
				ram.command(Stance.CHARGE);
				ram.setNoAi(true);
				ram.snapTo(ram.getX(), ram.getY(), ram.getZ(), 180.0F, 0.0F);
			}
		});
		run.waitFor(server -> server.overworld().getBlockState(ownSpot.north()).isAir(), "your ram on Charge breaks a fence even with mobGriefing off", 60);
		run.waitFor(server -> server.overworld().getBlockState(gateSpot.north()).isAir(), "a ram breaks the Reinforced Gate", 60);
		run.server(server -> {
			ServerLevel level = server.overworld();
			check(drops(level, ownSpot.north(), Items.OAK_FENCE) == 1, "a fence you placed drops when a ram breaks it");
			check(drops(level, gateSpot.north(), ModBlocks.REINFORCED_GATE.asItem()) == 0, "the Reinforced Gate drops nothing");
			check(level.getBlockState(raiderSpot.north()).is(Blocks.OAK_FENCE), "a raider ram breaks nothing with mobGriefing off");
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
		});
		run.waitFor(server -> server.overworld().getBlockState(raiderSpot.north()).isAir(), "with mobGriefing on, the raider ram breaks the fence", 60);
	}

	private static void repair(Run run) {
		BlockPos ground = run.ground;
		Run.Town town = run.town(Age.FEUDAL);
		BlockPos core = town.core();
		BlockPos barracks = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			place(level, player, ground.offset(-14, 0, 14), Blueprint.BARRACKS);
			BlockPos b = trainingCore(level, BlueprintPlacer.centerFor(ground.offset(-14, 0, 14), Direction.NORTH, Blueprint.BARRACKS), Blueprint.BARRACKS);
			check(!BuildingRegistry.isWrecked(level, b), "an intact Barracks isn't wrecked");
			BuildingRegistry.Building building = BuildingRegistry.at(level, b).orElseThrow();
			for (BlockPos pos : BlockPos.betweenClosed(building.min(), building.max())) {
				if (!BuildingRegistry.isWrecked(level, b) && StructureBlocks.contains(level, pos) && !pos.equals(b) && level.getBlockEntity(pos) == null) {
					level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					StructureBlocks.release(level, pos.immutable());
				}
			}
			check(BuildingRegistry.isWrecked(level, b), "a Barracks below half its blocks is wrecked");
			check(training(level, b).train(player, UnitType.SPEARMAN), "training can still be queued at a wrecked Barracks");
			return b;
		});
		// Repair is refused while a raid is on.
		startRaid(run, core);
		run.server(server -> check(!townCenter(server.overworld(), core).repair(player(server)), "no repairs during a raid"));
		run.server(server -> activeRaiders(server, core).forEach(r -> r.kill(server.overworld())));
		run.waitFor(server -> !RaidRegistry.isActive(server.overworld(), core), "the raid is over", 100);
		int[] before = run.compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			TownCenterBlockEntity center = townCenter(level, core);
			int stone = center.stock(Resource.STONE);
			int missing = Repairs.missingAround(level, center);
			check(missing > 100, "the gutted Barracks is missing hundreds of blocks (" + missing + ")");
			check(center.repair(player), "the Repair button works");
			check(center.repairing() && !center.repair(player), "a second click while it rebuilds is refused");
			check(center.stock(Resource.STONE) == stone, "no stone is spent before a block goes back");
			return new int[] {stone, missing};
		});
		run.waitFor(server -> !townCenter(server.overworld(), core).repairing(), "the town rebuilds block by block", 20 * 20);
		run.server(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			TownCenterBlockEntity center = townCenter(level, core);
			int left = Repairs.missingAround(level, center);
			check(left == 0, "nothing is missing after the repair (" + left + " left: " + Repairs.describeMissing(level, center) + ")");
			check(center.stock(Resource.STONE) == before[0] - before[1] * Repairs.STONE_PER_BLOCK, "repairs cost 1 stone per block");
			check(!BuildingRegistry.isWrecked(level, barracks), "the repaired Barracks works again");
			check(Run.hasAdvancement(server, WarbandsAdvancements.REPAIR), "advancement for repairing");
			int stoneNow = center.stock(Resource.STONE);
			check(!center.repair(player) && center.stock(Resource.STONE) == stoneNow, "nothing left to repair, and that costs no stone");
		});
	}
}
