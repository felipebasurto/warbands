package dev.felipe.warbands.gametest;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModGameRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Runs the scenarios ticked in {@code mods/warbands/testing/checklist.md}, each on its own patch of one world, and
 * reports every result instead of stopping at the first failure. {@code GAMETEST_ONLY=raids.won,camps.*} (or
 * {@code all}) overrides the checklist for one run. See {@code scripts/game-test.sh}.
 */
public class WarbandsClientGameTest implements FabricClientGameTest {
	private static final String CHECKLIST = "testing/checklist.md";
	private static final Pattern ITEM = Pattern.compile("^\\s*- \\[([ xX])\\] `?([a-z0-9_.-]+)`?");
	/** Scenario sites are this far apart along x, so nothing one builds or spawns reaches another. */
	private static final int SPACING = 2000;

	interface Body {
		void run(Run run);
	}

	/** One checklist entry: its id, what it proves, and the code. */
	record Scenario(String id, String what, Body body) {
	}

	static List<Scenario> all() {
		List<Scenario> all = new ArrayList<>();
		CoreScenarios.register(all);
		RaidScenarios.register(all);
		BattleScenarios.register(all);
		CampScenarios.register(all);
		FuzzScenarios.register(all);
		ReleaseScenarios.register(all);
		ShootScenarios.register(all);
		return all;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		List<Scenario> scenarios = all();
		List<Scenario> selected = select(scenarios);
		Warbands.LOGGER.info("[gametest] PLAN: {} of {} scenarios: {}", selected.size(), scenarios.size(),
				selected.stream().map(Scenario::id).toList());
		List<String> passed = new ArrayList<>();
		Map<String, String> failed = new LinkedHashMap<>();
		List<Scenario> flat = selected.stream().filter(sc -> !isNormal(sc.id()) && !sc.id().equals("release.reload")).toList();
		List<Scenario> normal = selected.stream().filter(sc -> isNormal(sc.id())).toList();
		if (!flat.isEmpty()) {
			runGroup(context, flat, false, passed, failed);
		}
		if (!normal.isEmpty()) {
			runGroup(context, normal, true, passed, failed);
		}
		if (selected.stream().anyMatch(sc -> sc.id().equals("release.reload"))) {
			try {
				ReleaseScenarios.reload(context);
				passed.add("release.reload");
				Warbands.LOGGER.info("[gametest] PASS release.reload (world closed and reopened)");
			} catch (AssertionError | RuntimeException e) {
				failed.put("release.reload", e.getMessage());
				Warbands.LOGGER.error("[gametest] FAIL release.reload: {}", e.getMessage(), e);
			}
		}
		Warbands.LOGGER.info("[gametest] SUMMARY passed {}: {}", passed.size(), passed);
		if (!failed.isEmpty()) {
			failed.forEach((id, why) -> Warbands.LOGGER.error("[gametest] SUMMARY failed {}: {}", id, why));
			throw new AssertionError(failed.size() + " scenario(s) failed: " + failed.keySet());
		}
		Warbands.LOGGER.info("[gametest] ALL SELECTED WARBANDS CHECKS PASSED");
	}

	/** Scenarios that need the terrain players actually get; the rest keep the flat world (fixed offsets, no trees or water). */
	private static final java.util.Set<String> NORMAL_WORLD = java.util.Set.of("camps.fuzz", "camps.findsite");
	/** The marketing shoot ({@code shoot.*}) needs real biomes too. */
	private static boolean isNormal(String id) {
		return NORMAL_WORLD.contains(id) || id.startsWith("shoot.");
	}

	/** Fixed seed of the normal-world tests, so a failure can be reproduced. */
	static final String NORMAL_SEED = "warbands";

	private static void runGroup(ClientGameTestContext context, List<Scenario> selected, boolean normalTerrain,
			List<String> passed, Map<String, String> failed) {
		var builder = context.worldBuilder().setUseConsistentSettings(true);
		if (normalTerrain) {
			builder.adjustSettings(state -> {
				state.getNormalPresetList().stream().filter(entry -> entry.preset().is(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL))
						.findFirst().ifPresent(state::setWorldType);
				state.setSeed(NORMAL_SEED);
			});
		}
		try (TestSingleplayerContext world = builder.create()) {
			world.getClientLevel().waitForChunksRender();
			Run.joinedWithHandbook = world.getServer().computeOnServer(server ->
					Run.player(server).getInventory().contains(new net.minecraft.world.item.ItemStack(dev.felipe.warbands.registry.ModItems.HANDBOOK)));
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
			for (int i = 0; i < selected.size(); i++) {
				Scenario scenario = selected.get(i);
				long start = System.currentTimeMillis();
				Warbands.LOGGER.info("[gametest] START {} - {}", scenario.id(), scenario.what());
				try {
					scenario.body().run(prepare(context, world, (i + 1) * SPACING));
					passed.add(scenario.id());
					Warbands.LOGGER.info("[gametest] PASS {} ({} s)", scenario.id(), (System.currentTimeMillis() - start) / 1000);
				} catch (AssertionError | RuntimeException e) {
					failed.put(scenario.id(), e.getMessage());
					Warbands.LOGGER.error("[gametest] FAIL {}: {}", scenario.id(), e.getMessage(), e);
				} finally {
					cleanUp(context, world);
				}
			}
		}
	}

	/** Moves the player to a fresh site and resets the world rules a scenario may have changed. */
	private static Run prepare(ClientGameTestContext context, TestSingleplayerContext world, int x) {
		world.getServer().runCommand("difficulty easy");
		world.getServer().runCommand("gamemode survival @a");
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand("tp @p " + (x + 0.5) + " 100 15.5 180 10");
		context.waitTicks(5);
		world.getClientLevel().waitForChunksRender();
		BlockPos ground = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, server);
			level.getGameRules().set(ModGameRules.RAIDS, true, server);
			BlockPos g = Run.groundAt(level, x, 0);
			Run.player(server).teleportTo(x + 0.5, g.getY() + 1, 15.5);
			Run.player(server).setHealth(Run.player(server).getMaxHealth());
			return g;
		});
		context.waitTicks(5);
		return new Run(context, world, ground);
	}

	/**
	 * Closes screens, removes every soldier, raider and mount the scenario left behind (they'd follow the player or
	 * count toward the population cap), and forgets raids and camps, so the next scenario starts clean.
	 */
	private static void cleanUp(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			AABB everywhere = new AABB(Run.player(server).blockPosition()).inflate(600);
			for (Entity entity : level.getEntitiesOfClass(Entity.class, everywhere,
					e -> e instanceof SoldierEntity || e instanceof Mob && e.getFirstPassenger() instanceof SoldierEntity)) {
				entity.discard();
			}
			level.removeAttached(ModAttachments.RAIDS);
			level.removeAttached(ModAttachments.CAMPS);
			level.removeAttached(ModAttachments.ARMY_ORDERS);
		});
		context.waitTicks(2);
	}

	/** The scenarios to run: GAMETEST_ONLY if set, else the ticked checklist items. Unknown or missing ids fail fast. */
	private static List<Scenario> select(List<Scenario> scenarios) {
		Map<String, Boolean> checklist = readChecklist();
		List<String> known = scenarios.stream().map(Scenario::id).toList();
		List<String> unknown = checklist.keySet().stream().filter(id -> !known.contains(id)).toList();
		List<String> missing = known.stream().filter(id -> !checklist.containsKey(id)).toList();
		if (!unknown.isEmpty() || !missing.isEmpty()) {
			throw new AssertionError(CHECKLIST + " is out of date. Not scenarios: " + unknown + ". Missing from the checklist: " + missing);
		}
		String only = System.getenv("GAMETEST_ONLY");
		if (only != null && !only.isBlank()) {
			List<String> patterns = List.of(only.trim().split("\\s*,\\s*"));
			List<Scenario> chosen = scenarios.stream().filter(s -> patterns.stream().anyMatch(p -> matches(p, s.id()))).toList();
			if (chosen.isEmpty()) {
				throw new AssertionError("GAMETEST_ONLY=" + only + " matches no scenario. Known: " + known);
			}
			return chosen;
		}
		return scenarios.stream().filter(s -> checklist.get(s.id())).toList();
	}

	private static boolean matches(String pattern, String id) {
		if (pattern.equals("all")) {
			return true;
		}
		return pattern.endsWith("*") ? id.startsWith(pattern.substring(0, pattern.length() - 1)) : id.equals(pattern);
	}

	/** id -> ticked, from the first {@code testing/checklist.md} found walking up from the run directory. */
	private static Map<String, Boolean> readChecklist() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path file = dir.resolve(CHECKLIST);
			if (Files.isRegularFile(file)) {
				Map<String, Boolean> items = new LinkedHashMap<>();
				try {
					for (String line : Files.readAllLines(file)) {
						Matcher m = ITEM.matcher(line);
						if (m.find()) {
							items.put(m.group(2), !m.group(1).isBlank());
						}
					}
				} catch (IOException e) {
					throw new AssertionError("Can't read " + file, e);
				}
				Warbands.LOGGER.info("[gametest] checklist: {}", file);
				return items;
			}
		}
		throw new AssertionError("No " + CHECKLIST + " above " + Path.of("").toAbsolutePath());
	}
}
