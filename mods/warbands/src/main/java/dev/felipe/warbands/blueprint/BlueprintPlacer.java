package dev.felipe.warbands.blueprint;

import dev.felipe.warbands.entity.BuildingDecor;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import net.minecraft.server.level.ServerPlayer;
import dev.felipe.warbands.town.BlacksmithBlockEntity;
import dev.felipe.warbands.town.BuildingRegistry;
import dev.felipe.warbands.town.WatchTowerBlockEntity;
import dev.felipe.warbands.town.HouseRegistry;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.TrainingBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Where a blueprint goes and how it's built. The clicked block becomes the spot right in front of
 * the entrance, and the building extends away from the player. Natural terrain in the way is
 * cleared, and gaps under the floor are filled. {@link #check} runs on both sides (the client uses
 * it for the preview).
 */
public final class BlueprintPlacer {
	/** Natural blocks a building may clear away (dirt, stone, sand, leaves...), editable via datapack. */
	public static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, Warbands.id("clearable"));
	private static final int MAX_FOUNDATION_DEPTH = 8;

	public enum Problem {
		NONE,
		BLOCKED,
		PLAYER_INSIDE,
		/** No Town Center of yours within 64 blocks. */
		NEEDS_TOWN,
		/** The nearby Town Center hasn't reached the blueprint's age. */
		TOO_EARLY,
		/** The nearby Town Center's stockpile doesn't have the stone. */
		NEED_STONE,
		/** A new Town Center within 128 blocks of one of yours: they would share (and double-count) houses. */
		TOWN_TOO_CLOSE,
		/** A second Blacksmith for the same Town Center: research is per commander, a second one adds nothing. */
		ONE_PER_TOWN;

		public Component message(Blueprint blueprint) {
			return switch (this) {
				case NONE -> Component.empty();
				case BLOCKED -> Component.translatable("message.warbands.blocked");
				case PLAYER_INSIDE -> Component.translatable("message.warbands.step_back");
				case NEEDS_TOWN -> blueprint.needsTownMessage();
				case TOO_EARLY -> blueprint.requiredAge().notReachedMessage();
				case NEED_STONE -> Component.translatable("message.warbands.need_stone", blueprint.stoneCost());
				case TOWN_TOO_CLOSE -> Component.translatable("message.warbands.town_too_close", TownCenterBlockEntity.TOWN_SPACING);
				case ONE_PER_TOWN -> Component.translatable("message.warbands.one_blacksmith");
			};
		}
	}

	private BlueprintPlacer() {
	}

	/** Center of the building (at ground level) when the player facing {@code facing} clicks {@code clicked}. */
	public static BlockPos centerFor(BlockPos clicked, Direction facing, Blueprint blueprint) {
		return clicked.relative(facing, blueprint.half() + 1);
	}

	public static Problem check(Level level, BlockPos center, Blueprint blueprint, @Nullable Player player) {
		if (findObstacle(level, center, blueprint) != null) {
			return Problem.BLOCKED;
		}
		if (player != null && player.getBoundingBox().intersects(bounds(center, blueprint))) {
			return Problem.PLAYER_INSIDE;
		}
		return Problem.NONE;
	}

	/** The first block in the building's space that can't be cleared, or null if the site is free. */
	public static @Nullable BlockPos findObstacle(Level level, BlockPos center, Blueprint blueprint) {
		int half = blueprint.half();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 1, -half), center.offset(half, blueprint.height() - 1, half))) {
			if (!isClearable(level, pos)) {
				return pos.immutable();
			}
		}
		// The template's floor replaces the ground layer too: only natural ground (or water, which the foundation fills) may be under it.
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 0, -half), center.offset(half, 0, half))) {
			BlockState state = level.getBlockState(pos);
			if (state.hasBlockEntity() || !(state.canBeReplaced() || state.is(CLEARABLE) || !state.getFluidState().isEmpty())) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** Air, plants, snow and natural terrain can be cleared. Anything built, valuable or liquid can't. */
	public static boolean isClearable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		return state.canBeReplaced() || state.is(CLEARABLE);
	}

	/** World-space box of the building above the ground. */
	public static AABB bounds(BlockPos center, Blueprint blueprint) {
		int half = blueprint.half();
		return new AABB(center.getX() - half, center.getY() + 1, center.getZ() - half,
				center.getX() + half + 1, center.getY() + blueprint.height(), center.getZ() + half + 1);
	}

	/** Inverse of {@link #rotationFor}: the Town Center block faces out the entrance. */
	public static Rotation rotationForEntrance(Direction entrance) {
		return rotationFor(entrance.getOpposite());
	}

	/**
	 * Restyles the Town Center with {@code structureId}, keeping the same footprint and the core
	 * block entity. Returns how many blocks were left alone because a player had built there,
	 * or -1 if the structure file is missing.
	 */
	public static int upgrade(ServerLevel level, BlockPos core, Direction entrance, Identifier structureId) {
		Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
		if (template.isEmpty()) {
			return -1;
		}
		int half = Blueprint.TOWN_CENTER.half();
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotationForEntrance(entrance))
				.setRotationPivot(new BlockPos(half, 0, half))
				.setIgnoreEntities(true);
		BlockPos origin = core.subtract(StructureTemplate.calculateRelativePosition(settings, Blueprint.TOWN_CENTER.core()));
		UpgradeProcessor processor = new UpgradeProcessor(level, core);
		settings.addProcessor(processor);
		template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);

		List<BlockPos> placed = new ArrayList<>();
		for (BlockPos pos : processor.affected) {
			if (level.getBlockState(pos).isAir()) {
				StructureBlocks.release(level, pos);
			} else {
				placed.add(pos);
			}
		}
		StructureBlocks.protect(level, placed);
		return processor.skipped;
	}

	/**
	 * Skips the Town Center itself and anything a player stored or built. Structure blocks and
	 * empty space are replaced. Anything that holds items ({@link Clearable}: barrels, the lectern's
	 * book, campfires) is never touched, because placing over it would wipe its contents, unless it's
	 * the building's own container, empty, and the new layout puts something else there.
	 * Returning null tells {@code placeInWorld} to leave that cell alone.
	 */
	private static final class UpgradeProcessor extends StructureProcessor {
		private final ServerLevel level;
		private final BlockPos core;
		private final List<BlockPos> affected = new ArrayList<>();
		private int skipped;

		private UpgradeProcessor(ServerLevel level, BlockPos core) {
			this.level = level;
			this.core = core;
		}

		@Override
		public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader ignored, BlockPos targetPosition, BlockPos referencePos,
				StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings) {
			BlockPos pos = processed.pos();
			if (pos.equals(this.core)) {
				return null;
			}
			BlockState current = this.level.getBlockState(pos);
			boolean structure = StructureBlocks.contains(this.level, pos);
			BlockEntity entity = this.level.getBlockEntity(pos);
			// An empty container of the old building that the new one moves (or drops) is replaced like any other block.
			boolean movedEmpty = structure && entity instanceof Container container && container.isEmpty()
					&& !processed.state().is(current.getBlock());
			if (entity instanceof Clearable && !movedEmpty) {
				if (!structure && !processed.state().isAir()) {
					this.skipped++;
				}
				return null;
			}
			if (processed.state().isAir()) {
				if (structure) {
					this.affected.add(pos.immutable());
					return processed;
				}
				if (!current.isAir() && !current.canBeReplaced()) {
					this.skipped++;
				}
				return null;
			}
			if (current.isAir() || current.canBeReplaced() || structure) {
				this.affected.add(pos.immutable());
				return processed;
			}
			this.skipped++;
			return null;
		}

		@Override
		protected StructureProcessorType<?> getType() {
			return StructureProcessorType.NOP;
		}
	}

	/** The building's entrance faces the player who places it. */
	public static Rotation rotationFor(Direction playerFacing) {
		return switch (playerFacing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/**
	 * Server-only rules on top of {@link #check}: everything but the Town Center goes near your own
	 * Town Center, which must have reached the blueprint's age and hold its stone.
	 */
	public static Problem checkServer(ServerLevel level, BlockPos center, Blueprint blueprint, Player player) {
		Problem problem = check(level, center, blueprint, player);
		if (problem == Problem.NONE && blueprint == Blueprint.TOWN_CENTER
				&& TownRegistry.hasOwnedWithin(level, center, player.getUUID(), TownCenterBlockEntity.TOWN_SPACING)) {
			return Problem.TOWN_TOO_CLOSE;
		}
		if (problem != Problem.NONE || !blueprint.needsTown()) {
			return problem;
		}
		TownCenterBlockEntity town = TownRegistry.nearestOwned(level, center, player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			return Problem.NEEDS_TOWN;
		}
		if (!town.age().atLeast(blueprint.requiredAge())) {
			return Problem.TOO_EARLY;
		}
		if (blueprint == Blueprint.BLACKSMITH && BuildingRegistry.near(level, town.getBlockPos(), TownCenterBlockEntity.HOUSE_RADIUS,
				player.getUUID()).stream().anyMatch(building -> building.type() == Blueprint.BLACKSMITH)) {
			return Problem.ONE_PER_TOWN;
		}
		if (town.stock(Resource.STONE) < blueprint.stoneCost()) {
			return Problem.NEED_STONE;
		}
		return Problem.NONE;
	}

	/**
	 * The shared building step (blueprints and enemy camps): clears the volume, fills a cobblestone foundation under
	 * the footprint, places the structure turned by {@code rotation} around its center, and protects every placed
	 * block (it drops nothing). Returns the settings used, or null if the structure file is missing.
	 */
	public static @Nullable StructurePlaceSettings build(ServerLevel level, Identifier structureId, BlockPos center, int half, int height,
			Rotation rotation) {
		Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
		if (template.isEmpty()) {
			return null;
		}
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 1, -half), center.offset(half, height - 1, half))) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		List<BlockPos> built = new ArrayList<>();
		lay(level, center, half, built, false);

		BlockPos origin = center.offset(-half, 0, -half);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation)
				.setRotationPivot(new BlockPos(half, 0, half))
				.setIgnoreEntities(true);
		template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);

		lay(level, center, half, built, true); // the template may have overwritten the first pass under its floor
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 0, -half), center.offset(half, height - 1, half))) {
			if (!level.getBlockState(pos).isAir()) {
				built.add(pos.immutable());
			}
		}
		StructureBlocks.protect(level, built);
		SiteCleanup.afterBuild(level, center, half, height);
		return settings;
	}

	/** Cobblestone from the floor down to solid ground (at most {@link #MAX_FOUNDATION_DEPTH}), under {@code onlyFloor} cells or all. */
	private static void lay(ServerLevel level, BlockPos center, int half, List<BlockPos> built, boolean onlyFloor) {
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				if (onlyFloor && level.getBlockState(center.offset(dx, 0, dz)).isAir()) {
					continue;
				}
				BlockPos pos = center.offset(dx, -1, dz);
				for (int depth = 0; depth < MAX_FOUNDATION_DEPTH; depth++, pos = pos.below()) {
					BlockState below = level.getBlockState(pos);
					if (!below.canBeReplaced() && !below.is(BlockTags.LEAVES) && below.getFluidState().isEmpty()) { // a crown under the floor would be cleaned away
						break;
					}
					level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
					built.add(pos.immutable());
				}
			}
		}
	}

	/**
	 * Clears natural terrain (never a player's block) above the floor in a ring of {@code margin} around the walls and in a
	 * 7-wide lane of {@code lane} blocks out of the entrance, and gives the lane a floor, so a building cut into a hillside
	 * can't have its door opening onto a wall of dirt.
	 */
	public static void clearApproach(ServerLevel level, BlockPos center, int half, int height, Direction entrance, int margin, int lane) {
		Direction side = entrance.getClockWise();
		for (int dx = -half - margin; dx <= half + margin; dx++) {
			for (int dz = -half - margin; dz <= half + margin; dz++) {
				if (Math.abs(dx) > half || Math.abs(dz) > half) {
					clearColumn(level, center.offset(dx, 0, dz), height, false);
				}
			}
		}
		for (int out = half + 1; out <= half + lane; out++) {
			for (int across = -3; across <= 3; across++) {
				clearColumn(level, center.relative(entrance, out).relative(side, across), height, true);
			}
		}
	}

	private static void clearColumn(ServerLevel level, BlockPos floor, int height, boolean pave) {
		BlockState topSoil = null;
		for (int dy = 1; dy <= height; dy++) {
			BlockPos pos = floor.above(dy);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && isClearable(level, pos)) {
				if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
					topSoil = state;
				}
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		// The soil left bare under a removed grass top would show as a brown ring: it gets the grass back.
		if (!pave && topSoil != null && level.getBlockState(floor).is(Blocks.DIRT) && level.getBlockState(floor.above()).isAir()) {
			level.setBlock(floor, topSoil.getBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		if (pave && level.getBlockState(floor).canBeReplaced()) {
			level.setBlock(floor, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
			// Never a bridge: where the ground falls away, the lane is carried down to it.
			for (int dy = 1; dy <= MAX_FOUNDATION_DEPTH; dy++) {
				BlockPos below = floor.below(dy);
				if (!level.getBlockState(below).canBeReplaced() || !level.getFluidState(below).isEmpty()) {
					break;
				}
				level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Clears the site, lays a foundation and builds the structure. Returns false if its file is missing. */
	public static boolean place(ServerLevel level, BlockPos center, Blueprint blueprint, Player player) {
		StructurePlaceSettings settings = build(level, blueprint.structureId(), center, blueprint.half(), blueprint.height(),
				rotationFor(player.getDirection()));
		if (settings == null) {
			return false;
		}
		int half = blueprint.half();
		BlockPos origin = center.offset(-half, 0, -half);
		clearApproach(level, center, half, blueprint.height(), settings.getRotation().rotate(Direction.SOUTH), 2, 8);

		if (blueprint.stoneCost() > 0) {
			TownCenterBlockEntity town = TownRegistry.nearestOwned(level, center, player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
			if (town != null) {
				town.spendStone(blueprint.stoneCost());
				player.sendOverlayMessage(Component.translatable("message.warbands.stone_paid", blueprint.stoneCost()));
			}
		}
		BlockPos core = origin.offset(StructureTemplate.calculateRelativePosition(settings, blueprint.core()));
		if (level.getBlockEntity(core) instanceof TownCenterBlockEntity townCenter) {
			townCenter.setOwner(player);
		}
		if (level.getBlockEntity(core) instanceof TrainingBlockEntity building) {
			building.setOwner(player);
		}
		if (level.getBlockEntity(core) instanceof BlacksmithBlockEntity smithy) {
			smithy.setOwner(player);
		}
		if (level.getBlockEntity(core) instanceof WatchTowerBlockEntity tower) {
			tower.setOwner(player);
		}
		BuildingDecor.spawn(level, blueprint, origin, settings, core);
		BuildingRegistry.add(level, blueprint, origin, settings.getRotation(), core, player.getUUID());
		if (blueprint == Blueprint.HOUSE) {
			BlockPos min = center.offset(-half, 0, -half);
			BlockPos max = center.offset(half, blueprint.height() - 1, half);
			HouseRegistry.add(level, core, player.getUUID(), min, max, StructureBlocks.countIn(level, min, max));
		}
		if (player instanceof ServerPlayer serverPlayer) {
			WarbandsAdvancements.award(serverPlayer, switch (blueprint) {
				case HOUSE -> WarbandsAdvancements.HOUSING;
				case BARRACKS -> WarbandsAdvancements.BARRACKS;
				case ARCHERY_RANGE -> WarbandsAdvancements.ARCHERY_RANGE;
				case BLACKSMITH -> WarbandsAdvancements.BLACKSMITH;
				case STABLE -> WarbandsAdvancements.STABLE;
				case WATCH_TOWER -> WarbandsAdvancements.WATCH_TOWER;
				case SIEGE_WORKSHOP -> WarbandsAdvancements.SIEGE_WORKSHOP;
				case TOWN_CENTER -> WarbandsAdvancements.FOUND_TOWN;
			});
		}
		return true;
	}
}
