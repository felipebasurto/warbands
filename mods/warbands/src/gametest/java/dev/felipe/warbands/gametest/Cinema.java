package dev.felipe.warbands.gametest;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/**
 * What the marketing shoot needs on top of {@link Run}: spectator camera with the HUD hidden, a pretty flat spot in a
 * wanted biome, ranks of soldiers, and 1920x1080 pictures. Scenes live in {@link ShootScenarios}.
 */
final class Cinema {
	static final int WIDTH = 1920;
	static final int HEIGHT = 1080;
	/** Chunks drawn in every picture: far enough that the horizon is real terrain fading into fog, not a flat cut-off. */
	static final int FAR = 20;

	private Cinema() {
	}

	/** Spectator, no HUD, far render distance, clear weather, the light of {@code time} (1200 = morning, 12500 = sunset). */
	static void begin(Run run, int time) {
		run.command("gamemode spectator @a");
		run.command("time set " + time);
		run.command("weather clear");
		run.context.runOnClient(minecraft -> {
			minecraft.options.hideGui = true;
			minecraft.options.renderDistance().set(FAR);
			// Setting the option isn't enough: the server only sends as far as the distance the client last told it (5, the test's).
			minecraft.options.broadcastOptions();
		});
	}

	/** Gives the player and the options back; the runner only resets options between tests, not scenarios. */
	static void end(Run run) {
		run.context.runOnClient(minecraft -> {
			minecraft.options.hideGui = false;
			minecraft.options.renderDistance().set(5);
			minecraft.options.broadcastOptions();
		});
		run.command("gamemode survival @a");
		run.context.setScreen(() -> null);
	}

	/** What the client has of the view around the player: chunks still missing and whether every section is drawn. */
	private record View(int missing, int total, boolean drawn, int renderDistance) {
		boolean ready() {
			return missing == 0 && drawn;
		}

		@Override
		public String toString() {
			return "chunks missing " + missing + "/" + total + " (view " + renderDistance + "), all sections drawn: " + drawn;
		}
	}

	private static View view(net.minecraft.client.Minecraft minecraft) {
		var centre = minecraft.player.chunkPosition();
		int distance = minecraft.options.getEffectiveRenderDistance();
		int missing = 0;
		int total = 0;
		for (int dx = -distance; dx <= distance; dx++) {
			for (int dz = -distance; dz <= distance; dz++) {
				// The server sends a rounded view, not a square: Fabric's own wait asks for the corners too and never finishes.
				if (!net.minecraft.server.level.ChunkTrackingView.isInViewDistance(centre.x(), centre.z(), distance, centre.x() + dx, centre.z() + dz)) {
					continue;
				}
				total++;
				if (minecraft.level.getChunk(centre.x() + dx, centre.z() + dz, ChunkStatus.FULL, false) == null) {
					missing++;
				}
			}
		}
		return new View(missing, total, minecraft.levelRenderer.hasRenderedAllSections(), distance);
	}

	/**
	 * Waits until the world really is ready for a picture, and fails loudly if it never is (a picture over missing chunks
	 * or stale light is worse than no picture): every chunk of the view has reached the client, every section is drawn, and
	 * the server's light engine has had nothing queued for three checks in a row. It reports what it is waiting for every
	 * ten seconds, and when it gives up after {@code 3 minutes}, says which half was stuck.
	 */
	static void settle(Run run) {
		int steady = 0;
		View seen = null;
		boolean light = false;
		for (int waited = 0; waited <= 20 * 120; waited += 10) {
			seen = run.context.computeOnClient(Cinema::view);
			light = run.compute(server -> !server.overworld().getLightEngine().hasLightWork());
			if (seen.ready() && light) {
				if (++steady >= 3) {
					return;
				}
			} else {
				steady = 0;
			}
			if (waited % 200 == 0 && waited > 0) {
				dev.felipe.warbands.Warbands.LOGGER.info("[gametest] settling: {}, server light idle: {}", seen, light);
			}
			run.context.waitTicks(10);
		}
		map(run);
		throw new AssertionError("the world never settled: " + seen + ", server light idle: " + light);
	}

	/** Logs which chunks of the view the client lacks (# have, . missing, o missing but loaded on the server), centre in the middle. */
	private static void map(Run run) {
		java.util.List<Integer> centre = run.context.computeOnClient(minecraft -> java.util.List.of(minecraft.player.chunkPosition().x(),
				minecraft.player.chunkPosition().z(), minecraft.options.getEffectiveRenderDistance()));
		int cx = centre.get(0);
		int cz = centre.get(1);
		int d = centre.get(2);
		StringBuilder rows = new StringBuilder("\n");
		for (int dz = -d; dz <= d; dz++) {
			for (int dx = -d; dx <= d; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!net.minecraft.server.level.ChunkTrackingView.isInViewDistance(cx, cz, d, x, z)) {
					rows.append(' ');
					continue;
				}
				boolean client = run.context.computeOnClient(minecraft -> minecraft.level.getChunk(x, z, ChunkStatus.FULL, false) != null);
				boolean server = run.compute(srv -> srv.overworld().getChunkSource().getChunkNow(x, z) != null);
				rows.append(client ? '#' : server ? 'o' : '.');
			}
			rows.append('\n');
		}
		dev.felipe.warbands.Warbands.LOGGER.info("[gametest] chunk map around the camera:{}", rows);
	}

	static void shot(Run run, String name, Vec3 eye, Vec3 target) {
		shot(run, name, eye, target, () -> {
		});
	}

	/**
	 * Teleports the camera to {@code eye} looking at {@code target}, waits until the world is ready, runs {@code effect}
	 * (fire, blasts, swings: things that must be fresh in the frame) and takes the picture two ticks later.
	 */
	static void shot(Run run, String name, Vec3 eye0, Vec3 target, Runnable effect) {
		Vec3 eye = run.compute(server -> {
			ServerLevel level = server.overworld();
			BlockPos at = BlockPos.containing(eye0);
			if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				return eye0;
			}
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
			return new Vec3(eye0.x, Math.max(eye0.y, top + 3.0), eye0.z); // never film from inside a tree or a wall
		});
		look(run, eye, target);
		run.context.waitTicks(10);
		settle(run);
		sweepDrops(run, eye);
		effect.run();
		run.context.waitTicks(2);
		run.context.runOnClient(minecraft -> {
			minecraft.getToastManager().clear();
			minecraft.gui.getChat().clearMessages(false);
		});
		run.context.takeScreenshot(TestScreenshotOptions.of(name).withSize(WIDTH, HEIGHT));
	}

	/** Terraforming breaks flowers and grass into item drops that litter the picture; removes every item entity around the camera. */
	static void sweepDrops(Run run, Vec3 around) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(around, around).inflate(160))) {
				item.discard();
			}
		});
	}

	/** One camera pose of a {@link #record} path. */
	record Key(Vec3 eye, Vec3 target) {
	}

	/**
	 * Films {@code frames} frames as a numbered screenshot sequence ({@code <name>-f0000.png}...; {@code scripts/shoot.sh}
	 * turns each sequence into an mp4 and a gif). The server tick is frozen and stepped once per frame, so the video runs at
	 * exactly 20 frames per second of game time whatever the screenshots cost. The camera eases along {@code path};
	 * {@code perFrame} runs on the server thread's behalf before each step (swings, blasts).
	 */
	static void record(Run run, String name, int frames, List<Key> path, java.util.function.IntConsumer perFrame) {
		look(run, path.get(0).eye(), path.get(0).target());
		run.context.waitTicks(10);
		settle(run);
		sweepDrops(run, path.get(0).eye());
		run.command("tick freeze");
		try {
			for (int f = 0; f < frames; f++) {
				double t = frames == 1 ? 0.0 : f / (double) (frames - 1);
				t = t * t * (3.0 - 2.0 * t) * (path.size() - 1);
				int i = Math.min((int) t, path.size() - 2);
				double u = t - i;
				Key a = path.get(i);
				Key b = path.get(i + 1);
				look(run, a.eye().lerp(b.eye(), u), a.target().lerp(b.target(), u));
				if (perFrame != null) {
					perFrame.accept(f);
				}
				run.command("tick step 1");
				run.context.waitTicks(2);
				run.context.takeScreenshot(TestScreenshotOptions.of(String.format("%s-f%04d", name, f)).withSize(WIDTH, HEIGHT));
			}
		} finally {
			run.command("tick unfreeze");
		}
	}

	static void look(Run run, Vec3 eye, Vec3 target) {
		double dx = target.x - eye.x;
		double dy = target.y - (eye.y + 1.62);
		double dz = target.z - eye.z;
		float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
		float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		run.tp(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
	}

	static Vec3 at(BlockPos origin, double dx, double dy, double dz) {
		return new Vec3(origin.getX() + 0.5 + dx, origin.getY() + 1 + dy, origin.getZ() + 0.5 + dz);
	}

	/** A camera spot {@code height} blocks above the ground at (site + dx, site + dz), so hills never bury it. */
	static Vec3 eye(Run run, BlockPos site, double dx, double height, double dz) {
		int x = site.getX() + (int) Math.floor(dx);
		int z = site.getZ() + (int) Math.floor(dz);
		BlockPos ground = run.compute(server -> {
			loadAround(server.overworld(), new BlockPos(x, 0, z), 8);
			return Run.groundAt(server.overworld(), x, z);
		});
		return new Vec3(x + 0.5, ground.getY() + 1 + height, z + 0.5);
	}

	// ---- places ----

	/**
	 * A level pad of {@code half} blocks around a dry spot in the first of {@code biomes} the seed has near this scenario's
	 * site (meadows before plains before forest...), blended into the real hills around it so the scenery stays natural.
	 * Returns its ground. Chunks around it are loaded for good.
	 */
	@SafeVarargs
	static BlockPos flatSpot(Run run, int half, ResourceKey<Biome>... biomes) {
		return run.compute(server -> {
			ServerLevel level = server.overworld();
			for (ResourceKey<Biome> biome : biomes) {
				var found = level.findClosestBiome3d(holder -> holder.is(biome), run.ground, 3000, 32, 64);
				if (found == null) {
					continue;
				}
				BlockPos spot = dry(level, found.getFirst(), half);
				if (spot != null) {
					pad(level, spot, half, 24);
					return Run.groundAt(level, spot.getX(), spot.getZ());
				}
			}
			throw new AssertionError("no dry ground in any of " + List.of(biomes));
		});
	}

	/** Walks outwards from {@code from} in 24-block steps to a spot that is not water or a treetop. */
	private static BlockPos dry(ServerLevel level, BlockPos from, int half) {
		for (int ring = 0; ring <= 6; ring++) {
			for (int gx = -ring; gx <= ring; gx++) {
				for (int gz = -ring; gz <= ring; gz++) {
					if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) {
						continue;
					}
					BlockPos centre = from.offset(gx * 24, 0, gz * 24);
					loadAround(level, centre, half + 24);
					BlockPos ground = Run.groundAt(level, centre.getX(), centre.getZ());
					BlockState state = level.getBlockState(ground);
					if (state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && ground.getY() > level.getSeaLevel()) {
						return centre;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Levels a square of {@code half} blocks to the height at its centre (trees and bumps cut, hollows filled with dirt under
	 * grass) and blends the next {@code blend} blocks back into the natural terrain.
	 */
	private static void pad(ServerLevel level, BlockPos centre, int half, int blend) {
		int top = Run.groundAt(level, centre.getX(), centre.getZ()).getY();
		for (int dx = -half - blend; dx <= half + blend; dx++) {
			for (int dz = -half - blend; dz <= half + blend; dz++) {
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				int outside = Math.max(Math.max(Math.abs(dx) - half, 0), Math.max(Math.abs(dz) - half, 0));
				double t = Math.min(1.0, outside / (double) blend);
				int original = Run.groundAt(level, x, z).getY();
				int target = (int) Math.round(top + (original - top) * t);
				for (int y = target + 1; y <= Math.max(original, target) + 16; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				for (int y = Math.min(original, target) - 4; y <= target; y++) {
					level.setBlock(new BlockPos(x, y, z), y == target ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
	}

	/**
	 * Asks the light engine to recompute every surface column around {@code site}: terraforming and structure placement
	 * leave dark smears on hill faces otherwise.
	 */
	static void relight(Run run, BlockPos site, int reach) {
		run.server(server -> {
			ServerLevel level = server.overworld();
			var light = level.getChunkSource().getLightEngine();
			int dark = 0;
			StringBuilder where = new StringBuilder();
			for (int x = site.getX() - reach; x <= site.getX() + reach; x++) {
				for (int z = site.getZ() - reach; z <= site.getZ() + reach; z++) {
					int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
					if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(x, top, z)) < 10) {
						dark++;
						if (where.length() < 200) {
							where.append(x - site.getX()).append(',').append(top - site.getY()).append(',').append(z - site.getZ()).append(' ');
						}
					}
				}
			}
			dev.felipe.warbands.Warbands.LOGGER.info("[gametest] relight: {} surface columns with sky light < 10 before; e.g. {}", dark, where);
			for (int x = site.getX() - reach; x <= site.getX() + reach; x++) {
				for (int z = site.getZ() - reach; z <= site.getZ() + reach; z++) {
					int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
					for (int y = top - 2; y <= top + 1; y++) {
						light.checkBlock(new BlockPos(x, y, z));
					}
				}
			}
		});
		run.context.runOnClient(mc -> {
			var light = mc.level.getLightEngine();
			for (int x = site.getX() - reach; x <= site.getX() + reach; x++) {
				for (int z = site.getZ() - reach; z <= site.getZ() + reach; z++) {
					int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
					for (int y = top - 2; y <= top + 1; y++) {
						light.checkBlock(new BlockPos(x, y, z));
					}
				}
			}
		});
		run.context.waitTicks(20);
	}

	static void loadAround(ServerLevel level, BlockPos site, int reach) {
		for (int cx = site.getX() - reach >> 4; cx <= site.getX() + reach >> 4; cx++) {
			for (int cz = site.getZ() - reach >> 4; cz <= site.getZ() + reach >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
	}

	// ---- ranks ----

	/** Where a posed soldier stands and which way it faces (0 = south, 180 = north). */
	record Spot(Vec3 pos, float yaw) {
	}

	/** {@code count} soldiers of {@code type} side by side; row 0 is the front. */
	record Rank(UnitType type, int count) {
	}

	/**
	 * Spawns {@code ranks} in a block formation centred on {@code centre}, front rank at z+0 and the others behind
	 * (north), facing south. Returns each soldier with the place it belongs; call {@link #pose} once knights have
	 * mounted, 20 ticks later.
	 */
	static Map<SoldierEntity, Spot> spawnRanks(ServerLevel level, ServerPlayer owner, BlockPos centre, List<Rank> ranks) {
		Map<SoldierEntity, Spot> places = new IdentityHashMap<>();
		for (int row = 0; row < ranks.size(); row++) {
			Rank rank = ranks.get(row);
			for (int i = 0; i < rank.count(); i++) {
				double x = centre.getX() + 0.5 + (i - (rank.count() - 1) / 2.0) * 2.1;
				double z = centre.getZ() + 0.5 - row * 2.6;
				double y = Run.groundAt(level, (int) Math.floor(x), (int) Math.floor(z)).getY() + 1;
				SoldierEntity soldier = Run.soldier(level, rank.type(), new Vec3(x, y, z), owner);
				places.put(soldier, new Spot(new Vec3(x, y, z), 0.0F));
			}
		}
		return places;
	}

	/** Freezes every soldier (and its mount) in place, facing south. */
	static void pose(Map<SoldierEntity, Spot> places) {
		places.forEach((soldier, place) -> {
			soldier.setNoAi(true);
			Entity body = soldier.getVehicle() != null ? soldier.getVehicle() : soldier;
			if (body instanceof Mob mob) {
				mob.setNoAi(true);
			}
			soldier.setInvulnerable(true);
			body.setInvulnerable(true);
			body.snapTo(place.pos().x, place.pos().y, place.pos().z, place.yaw(), 0.0F);
			soldier.setYHeadRot(place.yaw());
			soldier.setYBodyRot(place.yaw());
		});
	}

	static Vec3 centroid(List<? extends Entity> entities) {
		List<Vec3> points = new ArrayList<>();
		entities.forEach(e -> points.add(e.position()));
		return points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / Math.max(1, points.size()));
	}
}
