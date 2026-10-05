package dev.felipe.warbands.town;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The core of a House. Houses are registered by the blueprint (with their owner and size), not here,
 * so a hearth placed any other way doesn't count. Breaking it removes the house.
 */
public class HearthBlock extends Block {
	public HearthBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		HouseRegistry.remove(level, pos);
	}
}
