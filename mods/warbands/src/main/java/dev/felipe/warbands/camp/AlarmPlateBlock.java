package dev.felipe.warbands.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A camp's alarm plate: a brass plate on the ground that rings a bell and rouses the whole camp when anything that
 * isn't one of its guards steps on it. Silent once the camp is claimed.
 */
public class AlarmPlateBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0);

	public AlarmPlateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity)) {
			return;
		}
		if (Traps.campOwn(entity)) {
			return;
		}
		Traps.ring(server, pos, (LivingEntity) entity);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}
}
