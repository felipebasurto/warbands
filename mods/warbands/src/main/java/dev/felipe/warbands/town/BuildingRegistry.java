package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Every building placed from a blueprint: which one, where and how it was turned, and its core block. Raiders can
 * wreck walls, and this is what lets a Repair put the right blocks back, and lets a building count as wrecked (below
 * half its blocks) until then. Entries whose core block is gone are dropped the next time they're looked at.
 */
public final class BuildingRegistry {
	/** {@code blocks}: how many protected blocks it had when placed (the "half" in the wrecked rule). */
	public record Building(String blueprint, BlockPos origin, Rotation rotation, BlockPos core, Identifier coreBlock,
			UUID owner, int blocks) {
		public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("blueprint").forGetter(Building::blueprint),
				BlockPos.CODEC.fieldOf("origin").forGetter(Building::origin),
				Rotation.CODEC.fieldOf("rotation").forGetter(Building::rotation),
				BlockPos.CODEC.fieldOf("core").forGetter(Building::core),
				Identifier.CODEC.fieldOf("core_block").forGetter(Building::coreBlock),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Building::owner),
				Codec.INT.fieldOf("blocks").forGetter(Building::blocks)).apply(i, Building::new));

		public Blueprint type() {
			Blueprint type = Blueprint.byName(blueprint);
			return type == null ? Blueprint.HOUSE : type;
		}

		public BlockPos min() {
			return origin;
		}

		public BlockPos max() {
			Blueprint type = type();
			return origin.offset(type.size() - 1, type.height() - 1, type.size() - 1);
		}
	}

	public record Data(List<Building> buildings) {
		public static final Data EMPTY = new Data(List.of());
		public static final Codec<Data> CODEC = Building.CODEC.listOf().xmap(Data::new, Data::buildings);
	}

	private BuildingRegistry() {
	}

	public static void add(ServerLevel level, Blueprint blueprint, BlockPos origin, Rotation rotation, BlockPos core, UUID owner) {
		Identifier coreBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(core).getBlock());
		int half = blueprint.half();
		BlockPos center = origin.offset(half, 0, half);
		int blocks = StructureBlocks.countIn(level, center.offset(-half, 0, -half), center.offset(half, blueprint.height() - 1, half));
		List<Building> list = new ArrayList<>(all(level));
		list.removeIf(b -> b.core().equals(core));
		list.add(new Building(blueprint.getName(), origin.immutable(), rotation, core.immutable(), coreBlock, owner, blocks));
		level.setAttached(ModAttachments.BUILDINGS, new Data(List.copyOf(list)));
	}

	public static List<Building> all(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.BUILDINGS).buildings();
	}

	/** The owner's buildings whose core is within {@code radius} of {@code center}. Forgets buildings whose core is gone. */
	public static List<Building> near(ServerLevel level, BlockPos center, int radius, UUID owner) {
		List<Building> found = new ArrayList<>();
		List<Building> kept = new ArrayList<>();
		boolean pruned = false;
		for (Building building : all(level)) {
			if (level.isLoaded(building.core())
					&& !BuiltInRegistries.BLOCK.getKey(level.getBlockState(building.core()).getBlock()).equals(building.coreBlock())) {
				pruned = true;
				continue;
			}
			kept.add(building);
			if (building.owner().equals(owner) && building.core().distSqr(center) <= (double) radius * radius) {
				found.add(building);
			}
		}
		if (pruned) {
			level.setAttached(ModAttachments.BUILDINGS, new Data(List.copyOf(kept)));
		}
		return found;
	}

	public static Optional<Building> at(ServerLevel level, BlockPos core) {
		return all(level).stream().filter(b -> b.core().equals(core)).findFirst();
	}

	/**
	 * True when fewer than half of the building's blocks still stand (raiders broke it). A wrecked building stops
	 * working until it's repaired at the Town Center. Buildings from before the registry are never wrecked.
	 */
	public static boolean isWrecked(ServerLevel level, BlockPos core) {
		return at(level, core).map(b -> {
			int standing = (b.blocks() + 1) / 2;
			return StructureBlocks.countIn(level, b.min(), b.max(), standing) < standing;
		}).orElse(false);
	}
}
