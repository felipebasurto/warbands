package dev.felipe.warbands.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A camp's war relay: the brain its redstone traps are wired to. While the camp is alarmed ({@link Camps#alarmedAt})
 * it gives a redstone signal, so the contraptions around it are plain vanilla redstone: a steady relay shuts piston
 * portcullises and pulls rigged floors, a {@code pulse} relay beats on and off so the dispenser galleries fire again
 * and again. Break it (it's protected like the camp, so it drops nothing) and everything wired to it goes dead: that
 * is the sabotage route into a camp. Dark once the camp is taken.
 */
public class WarRelayBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty PULSE = BooleanProperty.create("pulse");
	/** Ticks between checks; a pulse relay flips each step, so its galleries fire once a second. */
	private static final int STEP = 10;

	public WarRelayBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(PULSE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED, PULSE);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, STEP);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean inCamp = Camps.campAt(level, pos).isPresent();
		boolean alarmed = inCamp && Camps.alarmedAt(level, pos);
		boolean powered = alarmed && (!state.getValue(PULSE) || !state.getValue(POWERED));
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
		}
		if (powered) {
			level.sendParticles(new DustParticleOptions(0xFF2020, 1.2F), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					3, 0.25, 0.1, 0.25, 0.0);
		}
		// Outside a camp (not registered yet, or its camp gone) it idles on a slow check instead of stopping for good.
		level.scheduleTick(pos, this, inCamp ? STEP : STEP * 10);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}
}
