package dev.felipe.warbands.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Scattered spikes in Feudal+ camps: slow and hurt whoever walks through while the camp still stands. Mobs path
 * around it (registered with {@code LandPathTypeRegistry} in {@link Traps}); inert once the camp is claimed.
 */
public class CaltropsBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);

	public CaltropsBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity)
				|| !Camps.armedAt(server, pos) || Traps.campOwn(entity)) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.7, 0.8, 0.7));
		entity.hurtServer(server, level.damageSources().generic(), 1.0F);
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
