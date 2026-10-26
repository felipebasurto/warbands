package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.check;
import static dev.felipe.warbands.gametest.Run.player;
import static dev.felipe.warbands.gametest.Run.townCenter;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import dev.felipe.warbands.blueprint.SiteCleanup;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Threat;
import dev.felipe.warbands.entity.WarlordEntity;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.UnitType;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.ArmyRegistry;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.raid.RaidRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

/** Release regressions for player property and rewards; isolated from ordinary gameplay fixtures. */
final class ReleaseScenarios {
	private ReleaseScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("release.age-feedback", "real age-up and routine awards keep chat, titles and toast counts bounded", ReleaseScenarios::ageFeedback));
		all.add(new Scenario("release.camp-feedback", "owner and offline commander conquest keep rewards and revenge readable without title spam", ReleaseScenarios::campFeedback));
		all.add(new Scenario("release.warbow", "Arha's Warbow shows its own model at rest and each pull frame while drawn (photos)", ReleaseScenarios::warbow));
		all.add(new Scenario("release.mcp", "authenticated MCP commands and handbook screenshot in this disposable world", ReleaseScenarios::mcp));
		all.add(new Scenario("release.cleanup", "cleanup preserves old drops, vanilla container spills of every former debris type and commander rewards", ReleaseScenarios::cleanup));
		all.add(new Scenario("release.terrain", "building and camp approach clearing removes unsupported plants without terrain loot", ReleaseScenarios::terrain));
		all.add(new Scenario("release.hoppers", "hoppers and hopper minecarts leave protected rewards while collecting normal items", ReleaseScenarios::hoppers));
		all.add(new Scenario("release.guards", "unavailable tracked guards block conquest after a saved camp round trip", ReleaseScenarios::guards));
		all.add(new Scenario("release.payout", "conquest payout is not repeated after saved camp data is decoded", ReleaseScenarios::payout));
		all.add(new Scenario("release.boss-loot", "all four warlords leave their signature weapon once and the expected bonus loot", ReleaseScenarios::bossLoot));
		all.add(new Scenario("release.reload", "close and reopen the saved world: stock, queue, soldier, camp, revenge and earned relic persist", r -> reload(r.context)));
	}

	/** Observe the received HUD, rather than counting calls in the server implementation. */
	private static Object field(Object object, Class<?> declaringClass, String name) {
		try {
			var field = declaringClass.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(object);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Verified 26.1.2 HUD field changed: " + name, e);
		}
	}

	private static void clearFeedback(Run run) {
		run.context.waitTicks(5);
		run.context.runOnClient(minecraft -> {
			minecraft.gui.getChat().clearMessages(false);
			minecraft.gui.clearTitles();
			minecraft.getToastManager().clear();
		});
	}

	private static void checkFeedback(Run run, int messages, int maxToasts, String requiredText, String photo) {
		run.context.waitTicks(5);
		run.context.runOnClient(minecraft -> {
			List<?> received = (List<?>) field(minecraft.gui.getChat(), ChatComponent.class, "allMessages");
			String text = received.stream().map(message -> ((GuiMessage) message).content().getString())
					.collect(java.util.stream.Collectors.joining("\n"));
			check(received.size() == messages, "received " + messages + " compact chat message(s): " + text);
			check(text.contains(requiredText), "essential instruction survives: " + requiredText);
			check(field(minecraft.gui, net.minecraft.client.gui.Gui.class, "title") == null,
					"no full-screen milestone title obscures play");
			var toasts = minecraft.getToastManager();
			int count = ((List<?>) field(toasts, toasts.getClass(), "visibleToasts")).size()
					+ ((java.util.Deque<?>) field(toasts, toasts.getClass(), "queued")).size();
			check(count <= maxToasts, "at most " + maxToasts + " milestone/recipe toasts, got " + count);
		});
		// Deliberately retain notifications: ordinary cleanShot would hide this regression.
		run.context.waitTicks(30);
		run.context.takeScreenshot(photo);
	}

	private static void ageFeedback(Run run) {
		Run.Town town = run.town(Age.VILLAGE);
		run.stockUp(town.core());
		for (Age age : List.of(Age.FEUDAL, Age.CASTLE)) {
			clearFeedback(run);
			run.advance(town.core(), age);
			checkFeedback(run, 1, 2, "guide", "warbands-feedback-" + age.serializedName());
		}
		clearFeedback(run);
		run.server(server -> {
			for (var id : List.of(WarbandsAdvancements.BARRACKS, WarbandsAdvancements.ARCHERY_RANGE,
					WarbandsAdvancements.STABLE)) {
				WarbandsAdvancements.award(player(server), id);
				var holder = server.getAdvancements().get(Warbands.id(id));
				check(holder != null && player(server).getAdvancements().getOrStartProgress(holder).isDone(),
						"quiet routine advancement still earns credit: " + id);
			}
		});
		checkFeedback(run, 0, 0, "", "warbands-feedback-routine");
		run.server(server -> player(server).getInventory().setItem(0, new ItemStack(ModItems.WAR_HORN)));
		run.context.waitTicks(5);
		run.server(server -> {
			var horn = server.getAdvancements().get(Warbands.id("war_horn"));
			check(horn != null && player(server).getAdvancements().getOrStartProgress(horn).isDone(),
					"obtaining the horn still earns its inventory-triggered advancement");
		});
		checkFeedback(run, 0, 1, "", "warbands-feedback-horn");
	}

	private static void campFeedback(Run run) {
		Run.Town town = run.town(Age.VILLAGE);
		for (CampTier tier : List.of(CampTier.BANDIT_CAMP, CampTier.FORT, CampTier.KEEP, CampTier.WITCH_COVEN)) {
			boolean away = tier == CampTier.WITCH_COVEN;
			clearFeedback(run);
			run.server(server -> {
				ServerLevel level = server.overworld();
				var player = player(server);
				// Away: an ally who already fought. This scenario checks the conquest message, not the claim gate.
				Camp camp = new Camp(away ? UUID.randomUUID() : player.getUUID(), town.core(), tier,
						run.ground.offset(0, 0, 50), Rotation.NONE, true, List.of(), false, false, false,
						true, 0L, false, "", Threat.NORMAL, true);
				level.setAttached(ModAttachments.CAMPS, new CampRegistry.Data(List.of(camp), java.util.Map.of()));
				if (away) {
					CampRegistry.addParticipant(level, camp, player.getUUID());
				}
				BlockPos standard = Camps.standardPos(camp);
				player.teleportTo(standard.getX() + 0.5, standard.getY(), standard.getZ() + 2.5);
				Camps.claim(level, standard, player);
				check(CampRegistry.all(level).getFirst().conquered(), "compact feedback still conquers " + tier);
			});
			checkFeedback(run, tier.age().atLeast(Age.FEUDAL) ? 2 : 1, 1,
					away ? "banner" : tier == CampTier.KEEP ? "war prize" : "Deposit", "warbands-feedback-" + tier.serializedName());
		}
	}

	/** Photos of the held bow at rest and mid-draw; the pull frames are chosen by use duration, so draw for real. */
	private static void warbow(Run run) {
		run.server(server -> {
			ItemStack bow = new ItemStack(Items.BOW);
			bow.set(DataComponents.ITEM_MODEL, Warbands.id("arha_warbow"));
			var player = player(server);
			player.getInventory().setItem(0, bow);
			player.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
			player.getInventory().setSelectedSlot(0);
		});
		run.context.waitTicks(10);
		run.context.takeScreenshot("warbands-warbow-rest");
		run.context.getInput().holdMouse(1); // right button, like a player drawing the bow
		for (int frame = 0; frame < 3; frame++) {
			run.context.waitTicks(frame == 0 ? 3 : 6);
			run.context.takeScreenshot("warbands-warbow-pull-" + frame);
		}
		run.server(server -> {
			check(player(server).isUsingItem(), "the player is really drawing the bow");
		});
		run.context.getInput().releaseMouse(1);
	}

	private record SavedState(BlockPos core, UUID owner, UUID soldier, int[] stock, int queue, BlockPos camp) {
	}

	/** Owns both world lifetimes; the runner calls it after closing the ordinary scenario world. */
	static void reload(ClientGameTestContext context) {
		SavedState expected;
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			world.getClientLevel().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			BlockPos ground = world.getServer().computeOnServer(server -> Run.groundAt(server.overworld(), 0, 0));
			Run run = new Run(context, world, ground);
			Run.Town town = run.town(Age.VILLAGE);
			run.stockUp(town.core());
			expected = run.compute(server -> {
				ServerLevel level = server.overworld();
				var owner = player(server);
				var center = townCenter(level, town.core());
				check(center.train(owner, UnitType.MILITIA), "a paid training queue is present before saving");
				var soldier = Run.soldier(level, UnitType.MILITIA, Vec3.atBottomCenterOf(ground.offset(4, 1, 10)), owner);
				soldier.setNoAi(true);
				BlockPos site = ground.offset(1000, 0, 0);
				Camp camp = Camp.chosen(owner.getUUID(), town.core(), CampTier.WITCH_COVEN, site, Rotation.NONE);
				level.setAttached(ModAttachments.CAMPS, new CampRegistry.Data(List.of(camp), java.util.Map.of()));
				Raids.retaliate(level, town.core(), owner.getUUID());
				check(Relics.award(level, camp, owner.getUUID()) != null, "a once-only relic is marked earned before saving");
				int[] stock = java.util.Arrays.stream(Resource.values()).mapToInt(center::stock).toArray();
				return new SavedState(town.core(), owner.getUUID(), soldier.getUUID(), stock, center.queueSize(), site);
			});
			save = world.getWorldSave();
			context.setScreen(() -> null);
		}
		try (TestSingleplayerContext world = save.open()) {
			world.getClientLevel().waitForChunksRender();
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				var town = townCenter(level, expected.core());
				for (Resource resource : Resource.values()) {
					check(town.stock(resource) == expected.stock()[resource.ordinal()], "saved stock persists: " + resource);
				}
				check(town.queueSize() == expected.queue(), "paid training queue persists across world reopen");
				check(ArmyRegistry.ids(server, expected.owner()).contains(expected.soldier()), "army registry retains the soldier UUID");
				check(level.getEntity(expected.soldier()) instanceof dev.felipe.warbands.entity.SoldierEntity soldier
						&& expected.owner().equals(soldier.ownerId()), "the soldier entity and its owner survive world reopen");
				check(CampRegistry.all(level).stream().anyMatch(c -> c.center().equals(expected.camp()) && c.owner().equals(expected.owner())),
						"the active camp survives world reopen");
				check(RaidRegistry.get(level, expected.core()).map(RaidRegistry.TownRaids::retaliation).orElse(false), "pending revenge survives world reopen");
				check(Relics.earned(level, expected.owner(), CampTier.WITCH_COVEN), "earned relic survives world reopen");
			});
			context.setScreen(() -> null);
		}
	}

	/** The companion MCP client sets a block after its observations; no personal world is involved. */
	private static void mcp(Run run) {
		BlockPos marker = run.ground.offset(4, 1, 14);
		run.server(server -> {
			player(server).getInventory().setItem(0, new ItemStack(ModItems.HANDBOOK));
			server.overworld().setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
		});
		Warbands.LOGGER.info("[gametest] MCP_RELEASE_READY {} {} {}", marker.getX(), marker.getY(), marker.getZ());
		run.waitFor(server -> server.overworld().getBlockState(marker).is(Blocks.GOLD_BLOCK),
				"the companion MCP client completed observations, screenshot and world command", 20 * 30);
		run.context.setScreen(() -> null);
	}

	private static void cleanup(Run run) {
		List<ItemEntity> drops = new ArrayList<>();
		List<Item> chestItems = List.of(Items.APPLE, Items.STICK, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS,
				Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.SNOWBALL, Items.OAK_SAPLING, Items.OAK_LEAVES,
				Items.DANDELION, Items.EMERALD);
		run.server(server -> {
			ServerLevel level = server.overworld();
			BlockPos at = run.ground.offset(8, 1, 0);
			drops.add(drop(level, at, new ItemStack(Items.DIAMOND), false));
			drops.add(drop(level, at.offset(0, 0, 3), new ItemStack(Items.WHEAT_SEEDS), false));
		});
		run.context.waitTicks(5);
		run.server(server -> {
			ServerLevel level = server.overworld();
			SiteCleanup.afterBuild(level, run.ground, 3, 4);
			BlockPos at = run.ground.offset(8, 1, 0);
			drops.add(drop(level, at.offset(2, 0, 0), new ItemStack(ModItems.HAG_CHARM), true));
			// Vanilla container spills have no thrower; their item types do not prove cleanup origin.
			SimpleContainer contents = new SimpleContainer(chestItems.stream().map(item -> new ItemStack(item, 4)).toArray(ItemStack[]::new));
			Containers.dropContents(level, at, contents);
			check(level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
					.filter(item -> chestItems.stream().anyMatch(item.getItem()::is)).allMatch(item -> item.getOwner() == null),
					"vanilla container contents have no thrower");
		});
		run.context.waitTicks(120);
		run.server(server -> {
			check(drops.stream().noneMatch(ItemEntity::isRemoved), "old drops and protected commander reward survive cleanup");
			List<ItemEntity> nearby = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(run.ground).inflate(16));
			for (Item item : chestItems) {
				int count = nearby.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
				int expected = item == Items.WHEAT_SEEDS ? 5 : 4;
				check(count == expected, "unrelated container contents survive: " + item + " expected " + expected + ", got " + count);
			}
		});
	}

	private static void terrain(Run run) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			Blueprint house = Blueprint.HOUSE;
			BlockPos crop = run.ground.above(2);
			level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), Block.UPDATE_ALL);
			check(BlueprintPlacer.build(level, house.structureId(), run.ground, house.half(), house.height(), Rotation.NONE) != null,
					"House builds over the terrain fixture");
			BlockPos lane = run.ground.offset(0, 0, house.half() + 2);
			level.setBlock(lane.above(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(lane.above(2), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
			BlueprintPlacer.clearApproach(level, run.ground, house.half(), house.height(), Direction.SOUTH, 1, 3);
			check(level.getBlockState(lane.above()).isAir() && level.getBlockState(lane.above(2)).isAir(),
					"camp approach clears the natural support and grass above it");
		});
		run.context.waitTicks(120);
		run.server(server -> check(server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(run.ground).inflate(16)).isEmpty(),
				"construction and approach clearing leave no terrain loot"));
	}

	private static ItemEntity drop(ServerLevel level, BlockPos at, ItemStack stack, boolean reward) {
		ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, stack);
		if (reward) {
			item.addTag(Camps.REWARD_TAG);
			item.setTarget(UUID.randomUUID());
			item.setUnlimitedLifetime();
			item.setInvulnerable(true);
		}
		level.addFreshEntity(item);
		return item;
	}

	private static void hoppers(Run run) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			SimpleContainer hopper = new SimpleContainer(5);
			MinecartHopper cart = new MinecartHopper(EntityType.HOPPER_MINECART, level);
			for (var container : List.of(hopper, cart)) {
				ItemEntity reward = drop(level, run.ground.above(), new ItemStack(ModItems.HAG_CHARM), true);
				check(!HopperBlockEntity.addItem(container, reward) && !reward.isRemoved() && container.isEmpty(),
						"hopper collection leaves the commander's relic alone: " + container.getClass().getSimpleName());
				ItemEntity ordinary = drop(level, run.ground.above(), new ItemStack(Items.DIAMOND), false);
				check(HopperBlockEntity.addItem(container, ordinary) && ordinary.isRemoved(),
						"normal loot is still collected: " + container.getClass().getSimpleName());
				reward.discard();
			}
		});
	}

	private static Camp savedCamp(Run run, BlockPos town, List<UUID> guards, boolean conquered) {
		return new Camp(run.compute(server -> player(server).getUUID()), town, CampTier.BANDIT_CAMP,
				run.ground.offset(0, 0, 50), Rotation.NONE, true, guards, conquered, false, false, false,
				0L, false, "", Threat.NORMAL, true);
	}

	private static void guards(Run run) {
		Camp camp = savedCamp(run, run.ground, List.of(UUID.randomUUID()), false);
		run.server(server -> {
			ServerLevel level = server.overworld();
			level.setAttached(ModAttachments.CAMPS, new CampRegistry.Data(List.of(camp), java.util.Map.of()));
			Run.roundTrip(level, ModAttachments.CAMPS, "tracked camp with an unavailable living guard");
			check(Camps.guardsLeft(level, CampRegistry.all(level).getFirst()) == 1, "a missing tracked guard counts as alive");
			Camps.claim(level, Camps.standardPos(camp), player(server));
			check(!CampRegistry.all(level).getFirst().conquered(), "an unavailable guard prevents conquest and rewards");
		});
	}

	private static void payout(Run run) {
		Run.Town town = run.town(Age.VILLAGE);
		Camp camp = savedCamp(run, town.core(), List.of(), true);
		run.server(server -> {
			ServerLevel level = server.overworld();
			int before = townCenter(level, town.core()).stock(Resource.FOOD);
			level.setAttached(ModAttachments.CAMPS, new CampRegistry.Data(List.of(camp), java.util.Map.of()));
			Camps.payOut(level, town.core());
			check(townCenter(level, town.core()).stock(Resource.FOOD) == before + CampTier.BANDIT_CAMP.plunder(Resource.FOOD),
					"the first payout adds the camp's food");
			Run.roundTrip(level, ModAttachments.CAMPS, "paid camp");
			var data = level.getAttachedOrCreate(ModAttachments.CAMPS);
			var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
			var codec = ModAttachments.CAMPS.persistenceCodec();
			level.setAttached(ModAttachments.CAMPS, codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow());
			Camps.payOut(level, town.core());
			check(townCenter(level, town.core()).stock(Resource.FOOD) == before + CampTier.BANDIT_CAMP.plunder(Resource.FOOD),
					"decoded paid camp does not pay twice");
		});
	}

	private static void bossLoot(Run run) {
		String[] kinds = {"keep", "illager_hall", "cavalry_encampment", "sunken_crypt"};
		String[] models = {"warlord_blade", "brakk_cleaver", "arha_warbow", "mos_gravebane"};
		for (int i = 0; i < kinds.length; i++) {
			int index = i;
			BlockPos at = run.ground.offset(0, 1, 30 + i * 12);
			run.server(server -> {
				ServerLevel level = server.overworld();
				WarlordEntity lord = ModEntities.WARLORD.create(level, EntitySpawnReason.COMMAND);
				check(lord != null, "warlord fixture created");
				lord.setCampKind(kinds[index]);
				lord.setBoss(index == 0);
				lord.setNoAi(true);
				lord.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				level.addFreshEntity(lord);
				lord.kill(level);
			});
			run.context.waitTicks(2);
			run.server(server -> {
				List<ItemEntity> items = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5));
				Identifier model = Warbands.id(models[index]);
				check(items.stream().filter(item -> model.equals(item.getItem().get(DataComponents.ITEM_MODEL)))
						.mapToInt(item -> item.getItem().getCount()).sum() == 1, kinds[index] + " drops its signature once");
				check(items.stream().anyMatch(item -> item.getItem().is(Items.EMERALD)), kinds[index] + " drops emeralds");
				check(items.stream().anyMatch(item -> item.getItem().is(Items.EXPERIENCE_BOTTLE)), kinds[index] + " drops XP bottles");
				check(items.stream().anyMatch(item -> item.getItem().is(Items.ENCHANTED_BOOK)), kinds[index] + " drops an enchanted book");
				if (index == 0) {
					check(items.stream().anyMatch(item -> {
						Identifier id = item.getItem().get(DataComponents.ITEM_MODEL);
						return id != null && List.of("warlord_helm", "warlord_cuirass", "warlord_greaves", "warlord_sabatons").contains(id.getPath());
					}), "Vorak also drops a Plate piece");
				}
			});
		}
	}
}
