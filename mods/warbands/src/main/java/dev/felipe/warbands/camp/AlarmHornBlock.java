package dev.felipe.warbands.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * A camp's alarm horn: any redstone signal into it (in camps, a sculk sensor beside it that heard footsteps, a
 * mined block or an arrow landing) rings the camp alarm against the nearest intruder. Sneaking makes no footstep
 * vibration and wool muffles a sensor, so it rewards a quiet approach. A sensor can't say who it heard, so the horn
 * blames the nearest player within reach who isn't sneaking: a guard's steps next to a sneaking player stay quiet.
 */
public class AlarmHornBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Who counts as the intruder: the nearest player this close to the horn (a sculk sensor hears 8 blocks). */
	private static final double REACH = 12.0;

	public AlarmHornBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
			boolean movedByPiston) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		boolean signal = level.hasNeighborSignal(pos);
		if (signal == state.getValue(POWERED)) {
			return;
		}
		level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
		if (!signal) {
			return;
		}
		ServerPlayer intruder = null;
		double best = REACH * REACH;
		for (ServerPlayer player : server.players()) {
			double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (!player.isSpectator() && !player.isCreative() && !player.isShiftKeyDown() && distance < best) {
				best = distance;
				intruder = player;
			}
		}
		if (intruder != null) {
			Traps.ring(server, pos, intruder);
		}
	}
}
