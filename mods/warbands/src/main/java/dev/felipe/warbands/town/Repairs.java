package dev.felipe.warbands.town;

import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.town.BuildingRegistry.Building;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Puts back the blocks raiders (or anyone) knocked out of a building, from its structure file, at 1 stone each.
 * Only empty or replaceable spots are filled: a player's own block is never overwritten, and the core is never touched.
 * A spot already holding the template's block counts as whole: the Blacksmith's fire and other "replaceable" blocks
 * used to count as missing forever, and every click on Repair charged a stone for them.
 * <p>
 * The Town Center's Repair button uses {@link #collectAround} and puts the blocks back a few per tick (see
 * {@code TownCenterBlockEntity.tickRepair}), so you watch the town rebuild; {@link #repair} does it at once.
 */
public final class Repairs {
	public static final int STONE_PER_BLOCK = 1;

	/** A block to put back, already rotated to the building. {@code tries}: times it couldn't stand yet. */
	public record Missing(BlockPos pos, BlockState state, int tries) {
		public Missing retry() {
			return new Missing(pos, state, tries + 1);
		}
	}

	private Repairs() {
	}

	/** Every missing block of the owner's buildings around this Town Center, bottom-up. */
	public static List<Missing> collectAround(ServerLevel level, TownCenterBlockEntity town) {
		List<Missing> out = new ArrayList<>();
		for (Building building : buildings(level, town)) {
			RepairProcessor processor = new RepairProcessor(level, building.core(), false);
			processor.collect = out;
			run(level, building, processor);
		}
		out.sort(java.util.Comparator.comparingInt(missing -> missing.pos().getY()));
		return out;
	}

	/** How many blocks {@code building} is missing. */
	public static int missing(ServerLevel level, Building building) {
		RepairProcessor processor = new RepairProcessor(level, building.core(), false);
		run(level, building, processor);
		return processor.count;
	}

	/**
	 * Fills the gaps and protects the new blocks. Returns how many stand afterwards. The template places bottom-up, so a
	 * hanging lantern or a wall banner can go in before the block it hangs from and pop off: up to three passes let those
	 * settle once their support is back.
	 */
	public static int repair(ServerLevel level, Building building) {
		int standing = 0;
		for (int pass = 0; pass < 3; pass++) {
			RepairProcessor processor = new RepairProcessor(level, building.core(), true);
			run(level, building, processor);
			List<BlockPos> kept = processor.placed.stream().filter(pos -> !level.getBlockState(pos).isAir()).toList();
			StructureBlocks.protect(level, kept);
			standing += kept.size();
			if (kept.size() == processor.placed.size()) {
				break;
			}
		}
		return standing;
	}

	/** What's missing and where: "stone_bricks at x, y, z in barracks". For diagnostics and the game test. */
	public static List<String> describeMissing(ServerLevel level, TownCenterBlockEntity town) {
		List<String> out = new ArrayList<>();
		for (Building building : buildings(level, town)) {
			RepairProcessor processor = new RepairProcessor(level, building.core(), false);
			processor.describe = out;
			processor.label = building.blueprint();
			run(level, building, processor);
		}
		return out;
	}

	/** Missing blocks across the owner's buildings around this Town Center. */
	public static int missingAround(ServerLevel level, TownCenterBlockEntity town) {
		int total = 0;
		for (Building building : buildings(level, town)) {
			total += missing(level, building);
		}
		return total;
	}

	/** Repairs every building of the owner around this Town Center. Returns the blocks placed. */
	public static int repairAround(ServerLevel level, TownCenterBlockEntity town) {
		int total = 0;
		for (Building building : buildings(level, town)) {
			total += repair(level, building);
		}
		return total;
	}

	private static List<Building> buildings(ServerLevel level, TownCenterBlockEntity town) {
		return town.owner() == null ? List.of()
				: BuildingRegistry.near(level, town.getBlockPos(), TownCenterBlockEntity.HOUSE_RADIUS, town.owner());
	}

	private static void run(ServerLevel level, Building building, RepairProcessor processor) {
		Identifier structure = structureOf(level, building);
		Optional<StructureTemplate> template = level.getStructureManager().get(structure);
		if (template.isEmpty() || !level.isLoaded(building.core())) {
			return;
		}
		int half = building.type().half();
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(building.rotation())
				.setRotationPivot(new BlockPos(half, 0, half))
				.setIgnoreEntities(true)
				.addProcessor(processor);
		template.get().placeInWorld(level, building.origin(), building.origin(), settings, level.getRandom(), Block.UPDATE_ALL);
	}

	/** The Town Center's look follows its age; every other building has one structure file. */
	private static Identifier structureOf(ServerLevel level, Building building) {
		if (building.type() == Blueprint.TOWN_CENTER && level.getBlockEntity(building.core()) instanceof TownCenterBlockEntity town) {
			return town.age().structureId();
		}
		return building.type().structureId();
	}

	/** Counts (dry run) or places the template's solid blocks that are missing from the world. */
	private static final class RepairProcessor extends StructureProcessor {
		private final ServerLevel level;
		private final BlockPos core;
		private final boolean place;
		private final List<BlockPos> placed = new ArrayList<>();
		private int count;
		private @Nullable List<String> describe;
		private @Nullable List<Missing> collect;
		private String label = "";

		private RepairProcessor(ServerLevel level, BlockPos core, boolean place) {
			this.level = level;
			this.core = core;
			this.place = place;
		}

		@Override
		public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader ignored, BlockPos targetPosition, BlockPos referencePos,
				StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings) {
			if (processed.state().isAir() || processed.pos().equals(this.core)) {
				return null;
			}
			BlockState current = this.level.getBlockState(processed.pos());
			if ((!current.isAir() && !current.canBeReplaced()) || current.is(processed.state().getBlock())) {
				return null;
			}
			this.count++;
			if (this.collect != null) {
				// Template states aren't rotated yet: placeInWorld rotates them after the processors run.
				this.collect.add(new Missing(processed.pos().immutable(), processed.state().rotate(settings.getRotation()), 0));
			}
			if (this.describe != null) {
				this.describe.add(processed.state() + " at " + processed.pos().toShortString() + " in " + this.label + " (now " + current + ")");
			}
			if (!this.place) {
				return null;
			}
			this.placed.add(processed.pos().immutable());
			return processed;
		}

		@Override
		protected StructureProcessorType<?> getType() {
			return StructureProcessorType.NOP;
		}
	}
}
