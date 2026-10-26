package dev.felipe.warbands.camp;

import dev.felipe.warbands.town.StructureBlocks;
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
		detonate(server, pos);
		for (Direction direction : Direction.values()) {
			// the blast also sets off a keg right beside it
			BlockPos next = pos.relative(direction);
			if (level.getBlockState(next).getBlock() instanceof PowderKegBlock && Camps.armedAt(server, next)) {
				detonate(server, next);
			}
		}
	}

	/** A keg the camp placed spares that camp's guards. Any other blast, including TNT, does not. */
	private static void detonate(ServerLevel server, BlockPos pos) {
		server.removeBlock(pos, false);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		Runnable blast = () -> server.explode(null, x, y, z, 3.0F, Level.ExplosionInteraction.NONE);
		if (StructureBlocks.contains(server, pos)) {
			Camps.campAt(server, pos).ifPresentOrElse(camp -> Traps.blastCampKeg(camp.center(), blast), blast);
		} else {
			blast.run();
		}
	}
}
