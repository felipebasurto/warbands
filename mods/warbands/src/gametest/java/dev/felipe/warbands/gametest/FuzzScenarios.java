package dev.felipe.warbands.gametest;

import static dev.felipe.warbands.gametest.Run.check;

import dev.felipe.warbands.Ground;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.blueprint.SiteCleanup;
import dev.felipe.warbands.camp.CampLayout;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Threat;
import dev.felipe.warbands.gametest.WarbandsClientGameTest.Scenario;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The "play every camp a thousand times" check: every layout, in all four rotations, built on real terrain around the
 * world (hills, forests, water edges, snow...), each checked for what players saw go wrong. The terrain box of each
 * site is saved first and put back after every build, so all builds meet the same untouched ground. Set
 * {@code WARBANDS_FUZZ_MODE} (real, synthetic, all), {@code WARBANDS_FUZZ_LAYOUTS} (comma list) and
 * {@code WARBANDS_FUZZ_PER_BUCKET} narrow or widen a run.
 */
final class FuzzScenarios {
	private static final int MAX_HALF = 22;
	private static final int BOX_HALF = MAX_HALF + SiteCleanup.TREE_MARGIN + 2;
	private static final int BELOW = 14;
	private static final int ABOVE = 80;
	/** The test world is flat and shallow: everything is lifted so sunken camps have earth to dig into. */
	private static final int RAISE = 20;
	private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private FuzzScenarios() {
	}

	static void register(List<Scenario> all) {
		all.add(new Scenario("camps.findsite", "the game finds a home-biome site for every camp kind on the normal world (hit rate per kind), and each site can be built on",
				FuzzScenarios::findSites));
		all.add(new Scenario("camps.loot", "every camp kind has a cache loot table that resolves and rolls items",
				FuzzScenarios::cacheLoot));
		all.add(new Scenario("camps.fuzz", "every camp layout in every rotation on real terrain: standard free and reachable, guards free, no leaves left to decay, no plants left to pop, no overhang, foundation filled",
				FuzzScenarios::fuzz));
	}

	private record Combo(CampTier tier, CampLayout layout, Rotation rotation) {
	}

	/** How the ground around a site is reshaped before a camp goes on it (the test world is flat, real worlds are not). */
	private enum Terrain {
		FLAT, SLOPE, BUMPY, CLIFF, PIT, MOUND, FOREST, LAKE
	}

	/** A place to build on: real ground picked from the seeded world, or a synthetic shape laid over one. */
	private record Site(String label, int x, int z, Terrain shape, boolean steep) {
	}

	private static void findSites(Run run) {
		List<String> misses = new ArrayList<>();
		java.util.Map<CampTier, Integer> hits = new java.util.LinkedHashMap<>();
		int towns = Integer.parseInt(env("WARBANDS_FINDSITE_TOWNS", "9"));
		for (int town = 0; town < towns; town++) {
			int tx = run.ground.getX() + (town % 3) * 1500 - 1500;
			int tz = run.ground.getZ() + (town / 3) * 1500 + 200;
			BlockPos townPos = new BlockPos(tx, 70, tz);
			for (CampTier tier : CampTier.values()) {
				BlockPos site = run.compute(server -> {
					server.overworld().getChunk(townPos);
					return Camps.siteFor(server.overworld(), townPos, tier);
				});
				if (site != null) {
					hits.merge(tier, 1, Integer::sum);
				}
			}
		}
		for (CampTier tier : CampTier.values()) {
			int hit = hits.getOrDefault(tier, 0);
			Warbands.LOGGER.info("[gametest] FINDSITE {}: {} of {} towns found a site", tier, hit, towns);
			if (hit == 0) {
				misses.add(tier.toString());
			}
		}
		// biome homes (mountains, swamp, snow) are legitimately absent around some towns, and the game then falls back to
		// a kind that fits any ground; but most kinds must be found, and every kind without a home biome always
		check(misses.size() <= 3, "no site found in " + towns + " towns for: " + misses);
		for (CampTier tier : CampTier.values()) {
			check(tier.hasBiomeHome() || hits.getOrDefault(tier, 0) >= towns - 2, tier + " has no home biome, so nearly every town must find a site");
		}
	}

	private static void cacheLoot(Run run) {
		List<String> problems = run.compute(server -> {
			List<String> out = new ArrayList<>();
			for (CampTier tier : CampTier.values()) {
				var table = server.reloadableRegistries().getLootTable(tier.cacheTable());
				if (table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
					out.add(tier + " has no cache table");
					continue;
				}
				var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server.overworld())
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
						.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
				int rolled = 0;
				for (int i = 0; i < 20; i++) {
					rolled += table.getRandomItems(params).size();
				}
				if (rolled == 0) {
					out.add(tier + " cache rolled nothing in 20 tries");
				}
			}
			return out;
		});
		check(problems.isEmpty(), String.join("; ", problems));
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

	/**
	 * Real-terrain sites: samples the seeded world with the generator's own heights (no chunk loading), buckets the
	 * candidates by biome and by how steep they are, and keeps a few of each, so forests, snow, desert, swamp, beaches,
	 * mountains and cliff edges are all in. Sites that fail {@link Camps#tooSteep} are flagged: the game would never
	 * pick them, so they only face the basic checks.
	 */
	private static List<Site> realSites(ServerLevel level, int originX, int originZ) {
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		int sea = generator.getSeaLevel();
		java.util.Map<String, List<Site>> buckets = new java.util.LinkedHashMap<>();
		RandomSource rng = RandomSource.create(7);
		int perBucket = Integer.parseInt(env("WARBANDS_FUZZ_PER_BUCKET", "2"));
		for (int i = 0; i < 1500; i++) {
			int x = originX + rng.nextInt(9000) - 4500;
			int z = originZ + rng.nextInt(9000) - 4500;
			int surface = generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
			int floor = generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random);
			if (surface <= sea - 1) {
				continue; // open ocean: no camp is ever built in it
			}
			String biome = generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
					net.minecraft.core.QuartPos.fromBlock(surface), net.minecraft.core.QuartPos.fromBlock(z), random.sampler())
					.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			String kind = biome.contains("desert") || biome.contains("badlands") ? "desert"
					: biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove") ? "snow"
					: biome.contains("swamp") || biome.contains("mangrove") ? "swamp"
					: biome.contains("jungle") || biome.contains("forest") || biome.contains("taiga") ? "forest"
					: biome.contains("peak") || biome.contains("slope") || biome.contains("hill") || biome.contains("mountain") ? "mountain"
					: biome.contains("beach") || biome.contains("shore") ? "beach"
					: "plains";
			boolean steep = Camps.tooSteep(level, x, z) || surface != floor;
			String bucket = kind + (steep ? "-steep" : "");
			List<Site> list = buckets.computeIfAbsent(bucket, k -> new ArrayList<>());
			if (list.size() < perBucket) {
				list.add(new Site(bucket + " " + biome, x, z, null, steep));
			}
		}
		List<Site> sites = new ArrayList<>();
		buckets.values().forEach(sites::addAll);
		return sites;
	}

	private static void fuzz(Run run) {
		List<Combo> combos = new ArrayList<>();
		String only = env("WARBANDS_FUZZ_LAYOUTS", "");
		for (CampTier tier : CampTier.values()) {
			for (CampLayout layout : tier.layouts()) {
				if (!only.isEmpty() && !List.of(only.split(",")).contains(layout.id())) {
					continue;
				}
				for (Rotation rotation : Rotation.values()) {
					combos.add(new Combo(tier, layout, rotation));
				}
			}
		}
		String mode = env("WARBANDS_FUZZ_MODE", "all");
		BlockPos base = run.ground;
		List<Site> sites = new ArrayList<>();
		if (!mode.equals("synthetic")) {
			sites.addAll(run.compute(server -> realSites(server.overworld(), base.getX(), base.getZ())));
		}
		if (!mode.equals("real")) {
			// deterministic worst cases laid over the nearest ground: they stay in even when no seed shows them
			for (Terrain terrain : Terrain.values()) {
				sites.add(new Site("synthetic " + terrain, base.getX() + 300 + terrain.ordinal() * 150, base.getZ() + 300, terrain, terrain != Terrain.FLAT));
			}
		}
		List<String> failures = new ArrayList<>();
		int built = 0;
		long nanos = 0;
		String only_site = env("WARBANDS_FUZZ_SITE", "");
		for (Site site : sites) {
			if (!only_site.isEmpty() && !site.label().contains(only_site)) {
				continue;
			}
			BlockPos floor = run.compute(server -> prepareSite(server.overworld(), site.x(), site.z()));
			run.context.waitTicks(10); // forced chunks need a few ticks before entities added to them are tracked
			run.server(server -> clearMobs(server.overworld(), floor));
			BlockPos min = floor.offset(-BOX_HALF, -BELOW, -BOX_HALF);
			Vec3i size = new Vec3i(BOX_HALF * 2 + 1, BELOW + ABOVE, BOX_HALF * 2 + 1);
			StructureTemplate snapshot = site.shape() == null ? run.compute(server -> {
				StructureTemplate template = new StructureTemplate();
				template.fillFromWorld(server.overworld(), min, size, false, List.of());
				return template;
			}) : null;
			StructureTemplate synthetic = site.shape() != null ? run.compute(server -> {
				StructureTemplate template = new StructureTemplate();
				template.fillFromWorld(server.overworld(), min, size, false, List.of());
				return template;
			}) : null;
			StructureTemplate saved = snapshot != null ? snapshot : synthetic;
			java.util.Map<CampTier, Boolean> roughness = new java.util.HashMap<>();
			for (Combo combo : combos) {
				// the game never picks ground that fails tooSteep, so sunken layouts (which need real earth around them)
				// only go where it would; everything else also faces the rough spots, with the basic checks
				boolean rough = site.shape() == null ? roughness.computeIfAbsent(combo.tier(), t -> run.compute(server -> Camps.unsuitableSite(server.overworld(), site.x(), site.z(), t))) : site.steep();
				if (rough && combo.layout().sink() > 0) {
					continue;
				}
				long t0 = System.nanoTime();
				List<String> problems = run.compute(server -> {
					clearItems(server.overworld(), floor);
					BlockPos at = floor;
					if (site.shape() != null) {
						reshape(server.overworld(), floor, site.shape());
						at = floor.above(RAISE);
					}
					return buildAndCheck(server.overworld(), at, combo, !rough);
				});
				run.context.waitTicks(3); // the site cleanup's item watch runs on ticks: whatever it lets through is what players see
				List<String> drops = run.compute(server -> drops(server.overworld(), floor, combo));
				problems = new ArrayList<>(problems);
				problems.addAll(drops);
				nanos += System.nanoTime() - t0;
				built++;
				for (String problem : problems) {
					String line = combo.layout().id() + " rot=" + combo.rotation() + " on " + site.label() + " (" + site.x() + "," + site.z() + "): " + problem;
					failures.add(line);
					Warbands.LOGGER.error("[gametest] FUZZ FAIL {}", line);
				}
				run.server(server -> restore(server.overworld(), saved, min));
			}
		}
		Warbands.LOGGER.info("[gametest] FUZZ built {} camps on {} sites ({} ms each on average), {} problems", built, sites.size(),
				built == 0 ? 0 : nanos / built / 1_000_000, failures.size());
		check(built > 0, "the fuzz built nothing");
		check(failures.isEmpty(), failures.size() + " fuzz problems in " + built + " builds, first: " + (failures.isEmpty() ? "" : failures.get(0)));
	}

	private static void clearItems(ServerLevel level, BlockPos floor) {
		var box = new net.minecraft.world.phys.AABB(floor).inflate(BOX_HALF + 8, BELOW + ABOVE, BOX_HALF + 8);
		for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
			item.discard();
		}
	}

	/** Items lying around a fresh camp after the cleanup had its ticks: seeds, sticks, berries and leaf litter players complained about. */
	private static List<String> drops(ServerLevel level, BlockPos floor, Combo combo) {
		List<String> problems = new ArrayList<>();
		int half = combo.layout().half();
		var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(floor).inflate(half + 6, combo.layout().height() + 10, half + 6));
		if (!dropped.isEmpty()) {
			var first = dropped.get(0);
			problems.add(dropped.size() + " item drops next to the new camp, e.g. " + first.getItem().getHoverName().getString()
					+ " at " + first.blockPosition().toShortString() + " (block there: " + level.getBlockState(first.blockPosition()).getBlock().getName().getString()
					+ ", below: " + level.getBlockState(first.blockPosition().below()).getBlock().getName().getString() + ")");
			dropped.forEach(Entity::discard);
		}
		return problems;
	}

	/** Worldgen animals in the box would suffocate in a build and drop items: not the camp's doing. */
	private static void clearMobs(ServerLevel level, BlockPos floor) {
		var box = new net.minecraft.world.phys.AABB(floor).inflate(BOX_HALF + 8, BELOW + ABOVE, BOX_HALF + 8);
		for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			entity.discard();
		}
	}

	/** Height change at (dx, dz) from the site's flat ground for each terrain shape. */
	private static int lift(Terrain terrain, int dx, int dz) {
		return switch (terrain) {
			case SLOPE -> Math.floorDiv(dx + 20, 4) - 5;
			case BUMPY -> (int) Math.round(2.2 * Math.sin(dx * 0.35) + 2.2 * Math.cos(dz * 0.3) + Math.sin((dx + dz) * 0.9));
			case CLIFF -> dx > 3 ? 5 : 0;
			case PIT -> dx * dx + dz * dz < 100 ? -4 : 0;
			case MOUND -> Math.max(0, 9 - (int) Math.sqrt(dx * dx + dz * dz) / 3);
			case LAKE -> dz > 0 ? -3 : 0;
			default -> 0;
		};
	}

	/** Reshapes the ground in the site's box (dirt over stone, grass on top), plants trees or floods it. */
	private static void reshape(ServerLevel level, BlockPos floor, Terrain terrain) {
		RandomSource random = RandomSource.create(floor.asLong() ^ terrain.ordinal());
		for (int dx = -BOX_HALF; dx <= BOX_HALF; dx++) {
			for (int dz = -BOX_HALF; dz <= BOX_HALF; dz++) {
				int top = floor.getY() + RAISE + lift(terrain, dx, dz);
				for (int y = floor.getY() - 6; y <= floor.getY() + RAISE + 14; y++) {
					BlockPos pos = new BlockPos(floor.getX() + dx, y, floor.getZ() + dz);
					BlockState state = y < top - 3 ? Blocks.STONE.defaultBlockState()
							: y < top ? Blocks.DIRT.defaultBlockState()
							: y == top ? Blocks.GRASS_BLOCK.defaultBlockState()
							: terrain == Terrain.LAKE && y <= floor.getY() + RAISE - 1 ? Blocks.WATER.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					if (terrain == Terrain.LAKE && y == top && top < floor.getY() + RAISE) {
						state = Blocks.SAND.defaultBlockState();
					}
					level.setBlock(pos, state, SILENT);
				}
				if (terrain != Terrain.LAKE || dz <= 0) {
					if (random.nextInt(6) == 0) {
						level.setBlock(new BlockPos(floor.getX() + dx, top + 1, floor.getZ() + dz),
								(random.nextBoolean() ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState(), SILENT);
					}
				}
			}
		}
		if (terrain == Terrain.FOREST) {
			for (int dx = -BOX_HALF + 2; dx <= BOX_HALF - 2; dx += 4) {
				for (int dz = -BOX_HALF + 2; dz <= BOX_HALF - 2; dz += 4) {
					if (random.nextInt(3) == 0) {
						continue;
					}
					BlockPos trunk = floor.offset(dx + random.nextInt(3) - 1, RAISE + 1, dz + random.nextInt(3) - 1);
					int height = 5 + random.nextInt(3);
					for (int h = 0; h < height; h++) {
						level.setBlock(trunk.above(h), Blocks.OAK_LOG.defaultBlockState(), SILENT);
					}
					for (int lx = -2; lx <= 2; lx++) {
						for (int lz = -2; lz <= 2; lz++) {
							for (int ly = height - 2; ly <= height; ly++) {
								BlockPos leaf = trunk.offset(lx, ly, lz);
								if (level.getBlockState(leaf).isAir() && Math.abs(lx) + Math.abs(lz) + (ly - height + 2) < 5) {
									level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), SILENT);
								}
							}
						}
					}
				}
			}
		}
	}

	/** Force-loads every chunk a camp there can touch and returns the ground block at the site. */
	private static BlockPos prepareSite(ServerLevel level, int x, int z) {
		int reach = BOX_HALF + 16;
		for (int cx = x - reach >> 4; cx <= x + reach >> 4; cx++) {
			for (int cz = z - reach >> 4; cz <= z + reach >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
		return new BlockPos(x, Ground.surface(level, x, z), z);
	}

	private static void restore(ServerLevel level, StructureTemplate snapshot, BlockPos min) {
		snapshot.placeInWorld(level, min, min, new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), SILENT);
		level.removeAttached(ModAttachments.CAMPS);
	}

	private static List<String> buildAndCheck(ServerLevel level, BlockPos floor, Combo combo, boolean strict) {
		List<String> problems = new ArrayList<>();
		UUID stranger = UUID.randomUUID();
		CampLayout layout = combo.layout();
		int half = layout.half();
		Camps.build(level, Camp.chosen(stranger, floor, combo.tier(), floor, combo.rotation(), layout.id(), Threat.NORMAL));
		Camp camp = CampRegistry.all(level).stream().filter(c -> c.owner().equals(stranger)).findFirst().orElse(null);
		if (camp == null || !camp.built()) {
			problems.add("did not build");
			return problems;
		}
		BlockPos center = camp.center();
		BlockPos standard = Camps.standardPos(camp);

		if (!level.getBlockState(standard).is(ModBlocks.CAMP_STANDARD)) {
			problems.add("no standard at " + standard.toShortString());
		}
		if (!passable(level, standard.above(2))) {
			problems.add("standard covered: " + level.getBlockState(standard.above(2)).getBlock().getName().getString() + " over the banner");
		}
		if (strict && !reachesStandard(level, center, half, layout.height(), layout.sink(), combo.rotation(), standard)) {
			problems.add("standard not reachable on foot from outside the entrance");
		}
		for (UUID id : camp.guards()) {
			Entity guard = level.getEntity(id);
			if (guard == null) {
				problems.add("a guard is missing (" + camp.guards().indexOf(id) + " of " + camp.guards().size() + ")");
			} else if (guard instanceof LivingEntity living && (!living.isAlive() || !"?".equals(blocking(level, guard)) )) {
				problems.add("guard " + guard.getType().toShortString() + " stuck in " + blocking(level, guard) + " at " + guard.blockPosition().toShortString());
			}
			if (guard != null) {
				guard.discard();
			}
		}
		List<BlockPos> leaves = SiteCleanup.orphanLeaves(level, center, half + SiteCleanup.TREE_MARGIN, center.getY() - 2,
				center.getY() + layout.height() + SiteCleanup.OVERHANG);
		if (!leaves.isEmpty()) {
			problems.add(leaves.size() + " leaves would decay and drop items, e.g. at " + leaves.get(0).toShortString());
		}
		int overhang = 0;
		int unsupported = 0;
		int unfounded = 0;
		BlockPos firstUnsupported = null;
		String firstUnfounded = null;
		int reach = half + 3;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				boolean inside = Math.abs(dx) <= half && Math.abs(dz) <= half;
				for (int dy = 1; dy < layout.height() + SiteCleanup.OVERHANG; dy++) {
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (inside && dy >= layout.height() && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
						overhang++;
					}
					if (dy < layout.height() && state.canBeReplaced() && state.getFluidState().isEmpty() && !state.canSurvive(level, pos)) {
						unsupported++;
						firstUnsupported = pos;
					}
				}
				if (inside) {
					BlockState below = level.getBlockState(center.offset(dx, -1, dz));
					if (below.isAir() || below.canBeReplaced()) {
						unfounded++;
						if (firstUnfounded == null) {
							firstUnfounded = center.offset(dx, -1, dz) + " (center " + center + ", d=" + dx + "," + dz + ", half=" + half + ") column -3..+1: ";
							for (int k = -3; k <= 1; k++) {
								firstUnfounded += level.getBlockState(center.offset(dx, k, dz)).getBlock().getDescriptionId().replace("block.minecraft.", "") + " ";
							}
						}
					}
				}
			}
		}
		if (overhang > 0) {
			problems.add(overhang + " tree or leaf blocks hang over the camp");
		}
		if (unsupported > 0) {
			problems.add(unsupported + " plants with no soil that would pop off as items, e.g. at " + firstUnsupported.toShortString());
		}
		if (strict && unfounded > 0) { // on ground the game would never pick (cliff edges) the foundation stops at its depth cap
			problems.add(unfounded + " floor columns with nothing under them, e.g. " + firstUnfounded);
		}
		return problems;
	}

	private static String blocking(ServerLevel level, Entity entity) {
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(entity.getBoundingBox().minX, entity.getBoundingBox().minY, entity.getBoundingBox().minZ),
				BlockPos.containing(entity.getBoundingBox().maxX, entity.getBoundingBox().maxY, entity.getBoundingBox().maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (thick(level, pos)) {
				return state.getBlock().getName().getString() + " " + pos.toShortString();
			}
		}
		return "?";
	}

	/** A collision shape more than a carpet or snow layer thick (mobs stand in those). */
	private static boolean thick(ServerLevel level, BlockPos pos) {
		var shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.25;
	}

	private static boolean low(ServerLevel level, BlockPos pos) {
		var shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.5;
	}

	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return true;
		}
		Block block = state.getBlock();
		return block instanceof FenceGateBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock
				|| state.is(ModBlocks.PORTCULLIS) || state.is(ModBlocks.REINFORCED_GATE);
	}

	/** Feet cell free or holding a step-high block (slab, carpet, low stair half), room to stand, a floor. */
	private static boolean walkable(ServerLevel level, BlockPos pos) {
		boolean step = low(level, pos);
		if (!(passable(level, pos) || step) || !passable(level, pos.above()) || step && !passable(level, pos.above(2))) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		return (step || !below.getCollisionShape(level, pos.below()).isEmpty()) && below.getFluidState().isEmpty();
	}

	/** Walks from a spot in front of the entrance (one block steps up, up to three down) to a cell beside the standard. */
	private static boolean reachesStandard(ServerLevel level, BlockPos center, int half, int height, int sink, Rotation rotation, BlockPos standard) {
		Direction out = rotation.rotate(Direction.SOUTH);
		BlockPos start = null;
		// the lane out of the gate is cleared five wide: stand anywhere in it, three to five blocks out
		search:
		for (int dist = half + 3; dist <= half + 5; dist++) {
			for (int across : new int[] {0, 1, -1, 2, -2}) {
				BlockPos front = center.relative(out, dist).relative(out.getClockWise(), across);
				for (int y = center.getY() + sink + 4; y >= center.getY() + sink - 4; y--) {
					BlockPos candidate = new BlockPos(front.getX(), y, front.getZ());
					if (walkable(level, candidate)) {
						start = candidate;
						break search;
					}
				}
			}
		}
		if (start == null) {
			Warbands.LOGGER.error("[gametest] FUZZDIAG no walkable start out of the gate at {} rot {} sink {}", center, rotation, sink);
			return false;
		}
		int reach = half + 6;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int step : new int[] {0, 1, -1, -2, -3}) {
					BlockPos next = pos.relative(dir).above(step);
					if (Math.abs(next.getX() - center.getX()) > reach || Math.abs(next.getZ() - center.getZ()) > reach
							|| next.getY() > center.getY() + height + 2 || next.getY() < center.getY() - 6 || seen.contains(next)) {
						continue;
					}
					if (step == 1 && !passable(level, pos.above(2))) {
						continue;
					}
					if (step < 0 && (!passable(level, pos.relative(dir)) || !passable(level, pos.relative(dir).above()))) {
						continue;
					}
					if (walkable(level, next)) {
						seen.add(next);
						queue.add(next);
						if (next.getY() == standard.getY() && next.distManhattan(standard) == 1) {
							return true;
						}
					}
				}
			}
		}
		BlockPos best = null;
		for (BlockPos p : seen) {
			if (best == null || p.distManhattan(standard) < best.distManhattan(standard)) {
				best = p;
			}
		}
		com.mojang.logging.LogUtils.getLogger().error("[gametest] FUZZDIAG reached {} cells, closest {} (standard {}, start {})", seen.size(), best, standard, start);
		StringBuilder lane = new StringBuilder();
		for (int step = half - 2; step <= half + 6; step++) {
			BlockPos column = center.relative(rotation.rotate(Direction.SOUTH), step);
			String found = "none";
			for (int y = center.getY() + sink + 6; y >= center.getY() + sink - 6; y--) {
				BlockPos at = new BlockPos(column.getX(), y, column.getZ());
				if (walkable(level, at)) {
					found = (y - center.getY()) + ":" + level.getBlockState(at.below()).getBlock().getName().getString();
					break;
				}
			}
			lane.append(step - half).append('=').append(found).append(' ');
		}
		Warbands.LOGGER.error("[gametest] FUZZDIAG reached {} cells; lane (out from wall: walkable y above center:floor) {}", seen.size(), lane);
		return false;
	}
}
