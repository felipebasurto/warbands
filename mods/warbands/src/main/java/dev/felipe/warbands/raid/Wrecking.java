package dev.felipe.warbands.raid;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.town.BuildingRegistry;
import dev.felipe.warbands.town.BuildingRegistry.Building;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Raiders marching on a town break it as they go: blocks of the raided owner's Warbands buildings, and doors, gates,
 * wooden fences and trapdoors anywhere in the town. Never a core block, never anything that holds items, never a
 * player's own building blocks. Broken blocks drop nothing; the Repair button puts them back.
 */
public final class Wrecking {
	public static final int INTERVAL = 3 * 20;
	private static final int REACH = 2;

	private Wrecking() {
	}

	/** One swing: breaks the first allowed block around {@code raider}. Returns true if something broke. */
	public static boolean wreck(ServerLevel level, SoldierEntity raider, BlockPos town, UUID owner) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false; // like zombies and doors: mobGriefing off keeps the town whole
		}
		List<Building> buildings = BuildingRegistry.near(level, town, TownCenterBlockEntity.HOUSE_RADIUS + 16, owner);
		BlockPos feet = raider.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-REACH, 0, -REACH), feet.offset(REACH, 2, REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || level.getBlockEntity(pos) != null || isCore(buildings, pos, town)) {
				continue;
			}
			boolean passage = state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)
					|| state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_TRAPDOORS);
			boolean building = StructureBlocks.contains(level, pos) && inside(buildings, pos);
			if (!passage && !building) {
				continue;
			}
			BlockPos target = pos.immutable();
			// A player's own door or fence drops, like one a zombie breaks: the Repair button only restores buildings.
			if (level.destroyBlock(target, !building, raider, Block.UPDATE_LIMIT)) {
				StructureBlocks.release(level, target);
				raider.swing(InteractionHand.MAIN_HAND);
				raider.playSound(blow(state), 0.6F, 0.8F + raider.getRandom().nextFloat() * 0.3F);
				return true;
			}
		}
		return false;
	}

	/** The thud of the blow, by material: a wooden door's bang only on wood, a clang on metal, else the block's own hit. */
	private static SoundEvent blow(BlockState state) {
		SoundType type = state.getSoundType();
		if (type == SoundType.WOOD || type == SoundType.NETHER_WOOD || type == SoundType.CHERRY_WOOD
				|| type == SoundType.BAMBOO_WOOD) {
			return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
		}
		if (type == SoundType.METAL || type == SoundType.COPPER) {
			return SoundEvents.ZOMBIE_ATTACK_IRON_DOOR;
		}
		return type.getHitSound();
	}

	private static boolean isCore(List<Building> buildings, BlockPos pos, BlockPos town) {
		if (pos.equals(town)) {
			return true;
		}
		for (Building building : buildings) {
			if (building.core().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inside(List<Building> buildings, BlockPos pos) {
		for (Building building : buildings) {
			BlockPos min = building.min();
			BlockPos max = building.max();
			if (pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
					&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
				return true;
			}
		}
		return false;
	}
}
