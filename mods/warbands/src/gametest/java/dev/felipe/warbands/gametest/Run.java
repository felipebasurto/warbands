package dev.felipe.warbands.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.TrainingBlockEntity;
import dev.felipe.warbands.town.UnitType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One scenario's run: its own patch of the world ({@link #ground}, 2000 blocks from every other scenario's), the test
 * context, and the helpers every scenario shares. Checks log {@code [gametest] OK: ...} and throw on failure.
 */
final class Run {
	static final int TIMEOUT = 20 * 60;

	final ClientGameTestContext context;
	final TestSingleplayerContext world;
	/** The ground block the scenario is built around. The player stands at +15 z, facing north, like a builder. */
	final BlockPos ground;

	Run(ClientGameTestContext context, TestSingleplayerContext world, BlockPos ground) {
		this.context = context;
		this.world = world;
		this.ground = ground;
	}

	/** A Town Center placed at this site (centre and core block). */
	record Town(BlockPos center, BlockPos core) {
	}

	// ---- server access ----

	void server(Consumer<MinecraftServer> action) {
		world.getServer().runOnServer(action::accept);
	}

	<T> T compute(Function<MinecraftServer, T> action) {
		return world.getServer().computeOnServer(action::apply);
	}

	void command(String command) {
		world.getServer().runCommand(command);
	}

	// ---- fixtures ----

	/** Set by the runner before the first scenario clears the inventory: did joining hand out the handbook? */
	static boolean joinedWithHandbook;

	/**
	 * Places a Town Center here (as the player, standing south of it and facing north) and advances it to {@code age}.
	 * A Village town starts with an empty stockpile; older towns keep what's left after paying for their ages.
	 */
	Town town(Age age) {
		Town town = placeTown();
		if (age.atLeast(Age.FEUDAL)) {
			stockUp(town.core());
			advance(town.core(), Age.FEUDAL);
		}
		if (age.atLeast(Age.CASTLE)) {
			advance(town.core(), Age.CASTLE);
		}
		return town;
	}

	/** A Village Town Center with an empty stockpile, its entrance facing the player (south). */
	Town placeTown() {
		BlockPos center = compute(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			player.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 15.5, 180.0F, 10.0F);
			BlockPos c = BlueprintPlacer.centerFor(ground.offset(0, 0, 14), Direction.NORTH, Blueprint.TOWN_CENTER);
			check(BlueprintPlacer.place(level, c, Blueprint.TOWN_CENTER, player), "fixture: Town Center placed");
			return c;
		});
		return new Town(center, center.above());
	}

	/** Plenty of everything: enough for both ages, a dozen buildings and a War Map. */
	void stockUp(BlockPos core) {
		server(server -> {
			TownCenterBlockEntity town = townCenter(server.overworld(), core);
			for (int i = 0; i < 12; i++) {
				town.deposit(new ItemStack(Items.BREAD, 64));
				town.deposit(new ItemStack(Items.COBBLESTONE, 64));
				town.deposit(new ItemStack(Items.COBBLESTONE, 64));
				town.deposit(new ItemStack(Items.OAK_LOG, 64));
			}
			town.deposit(new ItemStack(Items.GOLD_BLOCK, 30));
		});
	}

	/** Pays for the next age (the stockpile must hold its cost) and runs the research to the end. */
	void advance(BlockPos core, Age age) {
		net.minecraft.world.item.Item item = age.requirement();
		server(server -> check(townCenter(server.overworld(), core).tryAdvance(player(server), new SimpleContainer(new ItemStack(item))),
				"fixture: " + age.serializedName() + " research started"));
		fastForward(core, age.researchTicks());
		check(compute(server -> townCenter(server.overworld(), core).age() == age), "fixture: town reached " + age.serializedName());
	}

	/** Places {@code blueprint} as if the player clicked {@code clicked} facing north, and returns its core. */
	static BlockPos place(ServerLevel level, ServerPlayer player, BlockPos clicked, Blueprint blueprint) {
		BlockPos center = BlueprintPlacer.centerFor(clicked, Direction.NORTH, blueprint);
		BlueprintPlacer.Problem problem = BlueprintPlacer.checkServer(level, center, blueprint, player);
		BlockPos obstacle = BlueprintPlacer.findObstacle(level, center, blueprint);
		check(problem == BlueprintPlacer.Problem.NONE, blueprint.getName() + " can be placed (" + problem
				+ (obstacle == null ? "" : ", " + level.getBlockState(obstacle) + " at " + obstacle.subtract(center)) + ")");
		int stone = townCenterNear(level, center, player).stock(dev.felipe.warbands.town.Resource.STONE);
		check(BlueprintPlacer.place(level, center, blueprint, player), blueprint.getName() + " placed");
		check(townCenterNear(level, center, player).stock(dev.felipe.warbands.town.Resource.STONE) == stone - blueprint.stoneCost(),
				blueprint.getName() + " took its stone");
		return switch (blueprint) {
			case STABLE, BARRACKS, ARCHERY_RANGE, SIEGE_WORKSHOP -> trainingCore(level, center, blueprint);
			default -> center;
		};
	}

	/** A soldier of {@code owner}'s army, as if trained. */
	static SoldierEntity soldier(ServerLevel level, UnitType type, Vec3 pos, ServerPlayer owner) {
		SoldierEntity soldier = type.entityType().create(level, EntitySpawnReason.MOB_SUMMONED);
		soldier.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(soldier.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
		soldier.assignOwner(owner.getUUID(), owner.getName().getString());
		level.addFreshEntity(soldier);
		return soldier;
	}

	/** A raider of {@code age}; {@code raidTown} makes it part of that town's raid (null: a free raider, like a camp guard). */
	static SoldierEntity raider(ServerLevel level, UnitType type, Vec3 pos, Age age, @Nullable BlockPos raidTown) {
		SoldierEntity raider = type.entityType().create(level, EntitySpawnReason.COMMAND);
		raider.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		raider.finalizeSpawn(level, level.getCurrentDifficultyAt(raider.blockPosition()), EntitySpawnReason.COMMAND, null);
		raider.makeRaider(age, raidTown);
		level.addFreshEntity(raider);
		return raider;
	}

	// ---- time ----

	/**
	 * Runs a building's own tick {@code ticks} times at once, on the server thread. Research and training are timers on
	 * the block entity, so this is the real code path without the wait. ({@code /tick sprint} doesn't help: the client
	 * test harness steps the server one tick per client tick.)
	 */
	void fastForward(BlockPos pos, int ticks) {
		server(server -> {
			ServerLevel level = server.overworld();
			BlockState state = level.getBlockState(pos);
			for (int i = 0; i < ticks; i++) {
				switch (level.getBlockEntity(pos)) {
					case TownCenterBlockEntity town -> town.serverTick(level, pos, state);
					case TrainingBlockEntity building -> building.serverTick(level, pos, state);
					case null, default -> throw new AssertionError("Nothing to fast-forward at " + pos);
				}
			}
		});
	}

	void waitFor(Predicate<MinecraftServer> condition, String what) {
		waitFor(condition, what, TIMEOUT);
	}

	void waitFor(Predicate<MinecraftServer> condition, String what, int timeout) {
		for (int i = 0; i < timeout; i++) {
			if (compute(condition::test)) {
				Warbands.LOGGER.info("[gametest] OK: {}", what);
				return;
			}
			context.waitTick();
		}
		throw new AssertionError("Timed out waiting for: " + what);
	}

	/** Waits {@code ticks} and then checks {@code condition} held the whole time (sampled every tick). */
	void holds(Predicate<MinecraftServer> condition, String what, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (!compute(condition::test)) {
				throw new AssertionError("Check failed after " + i + " ticks: " + what);
			}
			context.waitTick();
		}
		Warbands.LOGGER.info("[gametest] OK: {}", what);
	}

	// ---- camera ----

	void tp(double x, double y, double z, float yaw, float pitch) {
		command("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
	}

	/** Teleports the player to {@code eye} looking at {@code target}. */
	void lookAt(BlockPos eye, Vec3 target) {
		Vec3 from = Vec3.atBottomCenterOf(eye);
		double dx = target.x - from.x;
		double dy = target.y - (from.y + 1.62);
		double dz = target.z - from.z;
		float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
		float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		tp(from.x, from.y, from.z, yaw, pitch);
	}

	void screenshot(String name) {
		context.waitTicks(20);
		world.getClientLevel().waitForChunksRender();
		cleanShot(name);
	}

	/** Screenshot without advancement toasts, titles or chat, and with the mouse parked in a corner, so no tooltip covers the menu. */
	void cleanShot(String name) {
		context.runOnClient(minecraft -> {
			minecraft.getToastManager().clear();
			minecraft.gui.getChat().clearMessages(false);
			minecraft.gui.clearTitles(); // an age-up title from a fixture would cover the picture
		});
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	/** Stands in front of a building's core block, opens its menu and takes a picture. */
	void menuShot(BlockPos core, String name) {
		tp(core.getX() + 0.5, core.getY(), core.getZ() + 2.5, 180.0F, 30.0F);
		context.waitTicks(5);
		server(server -> {
			if (server.overworld().getBlockEntity(core) instanceof MenuProvider provider) {
				player(server).openMenu(provider);
			} else {
				throw new AssertionError("No menu at " + core);
			}
		});
		context.waitTicks(15);
		cleanShot(name);
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	// ---- lookups ----

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** The player's soldiers within 96 blocks. */
	static List<SoldierEntity> army(MinecraftServer server) {
		ServerPlayer player = player(server);
		return server.overworld().getEntitiesOfClass(SoldierEntity.class, new AABB(player.blockPosition()).inflate(96),
				s -> player.getUUID().equals(s.ownerId()));
	}

	/** The living raiders of the raid on {@code town}. */
	static List<SoldierEntity> activeRaiders(MinecraftServer server, BlockPos town) {
		ServerLevel level = server.overworld();
		return RaidRegistry.get(level, town).flatMap(RaidRegistry.TownRaids::active)
				.map(active -> active.raiders().stream().map(level::getEntity)
						.filter(e -> e instanceof SoldierEntity s && s.isAlive()).map(e -> (SoldierEntity) e).toList())
				.orElse(List.of());
	}

	static RaidRegistry.@Nullable ActiveRaid activeRaid(MinecraftServer server, BlockPos town) {
		return RaidRegistry.get(server.overworld(), town).flatMap(RaidRegistry.TownRaids::active).orElse(null);
	}

	static TownCenterBlockEntity townCenter(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof TownCenterBlockEntity townCenter) {
			return townCenter;
		}
		throw new AssertionError("No town center at " + pos);
	}

	static TownCenterBlockEntity townCenterNear(ServerLevel level, BlockPos pos, ServerPlayer player) {
		TownCenterBlockEntity town = TownRegistry.nearestOwned(level, pos, player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			throw new AssertionError("No town center near " + pos);
		}
		return town;
	}

	static BlockPos trainingCore(ServerLevel level, BlockPos center, Blueprint blueprint) {
		return core(level, center, blueprint, TrainingBlockEntity.class);
	}

	/** The block entity of type {@code kind} inside the building centred at {@code center}. */
	static BlockPos core(ServerLevel level, BlockPos center, Blueprint blueprint, Class<?> kind) {
		int half = blueprint.half();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 0, -half), center.offset(half, blueprint.height() - 1, half))) {
			if (kind.isInstance(level.getBlockEntity(pos))) {
				return pos.immutable();
			}
		}
		throw new AssertionError("No " + kind.getSimpleName() + " in the " + blueprint.getName());
	}

	static TrainingBlockEntity training(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof TrainingBlockEntity building) {
			return building;
		}
		throw new AssertionError("No training building at " + pos);
	}

	static boolean hasAdvancement(MinecraftServer server, String id) {
		AdvancementHolder holder = server.getAdvancements().get(Warbands.id(id));
		return holder != null && player(server).getAdvancements().getOrStartProgress(holder).isDone();
	}

	static Set<String> langKeys(net.minecraft.client.Minecraft minecraft, String language) {
		return new HashSet<>(lang(minecraft, language).keySet());
	}

	/** The mod's lang file as read from resources, key to text. */
	static java.util.Map<String, String> lang(net.minecraft.client.Minecraft minecraft, String language) {
		try (var reader = new InputStreamReader(minecraft.getResourceManager()
				.open(Identifier.fromNamespaceAndPath(Warbands.MOD_ID, "lang/" + language + ".json")), StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			java.util.Map<String, String> texts = new java.util.HashMap<>();
			json.entrySet().forEach(entry -> texts.put(entry.getKey(), entry.getValue().getAsString()));
			return texts;
		} catch (java.io.IOException e) {
			throw new AssertionError("Can't read " + language + ".json", e);
		}
	}

	static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Check failed: " + what);
		}
		Warbands.LOGGER.info("[gametest] OK: {}", what);
	}

	/**
	 * Saves an attachment's value the way the world does (NBT, with registries) and reads it back: the value must
	 * encode, and decode to an equal value. Scenarios never reload the world, so this is where a codec that can't
	 * write (e.g. a map keyed by a non-string) shows up.
	 */
	static <T> void roundTrip(ServerLevel level, AttachmentType<T> type, String what) {
		T value = level.getAttachedOrCreate(type);
		Codec<T> codec = type.persistenceCodec();
		check(codec != null, what + " is saved");
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		DataResult<Tag> saved = codec.encodeStart(ops, value);
		check(saved.error().isEmpty(), what + " saves" + saved.error().map(e -> ": " + e.message()).orElse(""));
		DataResult<T> loaded = codec.parse(ops, saved.getOrThrow());
		check(loaded.error().isEmpty(), what + " loads back" + loaded.error().map(e -> ": " + e.message()).orElse(""));
		check(value.equals(loaded.getOrThrow()), what + " loads back unchanged");
	}

	/** The ground block at (x, z), for a scenario's site. */
	static BlockPos groundAt(ServerLevel level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
	}
}
