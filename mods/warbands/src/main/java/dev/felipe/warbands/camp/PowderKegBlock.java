package dev.felipe.warbands.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * A Castle-camp trap: a keg of powder wired to a tripwire. The blast hurts but breaks no blocks
 * ({@code ExplosionInteraction.NONE}). Inert once the camp is claimed, so a taken fort can't still blow up.
 */
public class PowderKegBlock extends Block {
	public PowderKegBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
			boolean movedByPiston) {
		if (!(level instanceof ServerLevel server) || !level.hasNeighborSignal(pos) || !Camps.armedAt(server, pos)) {
			return;
		}
		level.removeBlock(pos, false);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		server.explode(null, x, y, z, 3.0F, Level.ExplosionInteraction.NONE);
		for (Direction direction : Direction.values()) {
			// the blast also sets off a keg right beside it
			BlockPos next = pos.relative(direction);
			if (level.getBlockState(next).getBlock() instanceof PowderKegBlock && Camps.armedAt(server, next)) {
				level.removeBlock(next, false);
				server.explode(null, next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.NONE);
			}
		}
	}
}
