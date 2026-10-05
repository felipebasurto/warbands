package dev.felipe.warbands.town;

import com.mojang.serialization.MapCodec;
import dev.felipe.warbands.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The heart of a town. FACING points out of the building's door; trained units walk out that way.
 * Breaking it ends the town (stockpile, age, queue), so only its owner can, and only while sneaking:
 * a stray click with a pickaxe, or an instant creative click, must not wipe out a mid-game investment.
 */
public class TownCenterBlock extends BaseEntityBlock {
	public static final MapCodec<TownCenterBlock> CODEC = simpleCodec(TownCenterBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public TownCenterBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TownCenterBlockEntity townCenter) {
			if (townCenter.owner() == null) {
				townCenter.setOwner(player);
			}
			if (townCenter.isOwner(player)) {
				player.openMenu(townCenter);
			} else {
				player.sendOverlayMessage(Component.translatable("message.warbands.not_your_town", townCenter.ownerName()));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Mining makes no progress unless sneaking, so nobody spends ten seconds on a break that gets refused. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return player.isShiftKeyDown() ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
	}

	/** Left-click in survival: say what will happen before the player spends time mining. */
	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (!(level instanceof ServerLevel) || !(level.getBlockEntity(pos) instanceof TownCenterBlockEntity townCenter)) {
			return;
		}
		if (townCenter.owner() != null && !townCenter.isOwner(player)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.not_your_town", townCenter.ownerName()));
		} else if (player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.town_center_breaking")
					.withStyle(ChatFormatting.RED));
		} else {
			player.sendOverlayMessage(Component.translatable("message.warbands.town_center_break_sneak"));
		}
	}

	/**
	 * Fabric PlayerBlockBreakEvents.BEFORE handler, registered before the structure one. Covers creative
	 * (instant break, no attack call) and other players: only the owner, sneaking, may tear it down.
	 */
	public static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel) || !(blockEntity instanceof TownCenterBlockEntity townCenter)) {
			return true;
		}
		if (townCenter.owner() != null && !townCenter.isOwner(player)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.not_your_town", townCenter.ownerName()));
			return false;
		}
		if (!player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.town_center_break_sneak"));
			return false;
		}
		// In chat, not the overlay, so it stays readable after the fact (creative breaks instantly).
		player.sendSystemMessage(Component.translatable("message.warbands.town_center_destroyed").withStyle(ChatFormatting.RED));
		return true;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		TownRegistry.remove(level, pos);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TownCenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel serverLevel
				? createTickerHelper(type, ModBlockEntities.TOWN_CENTER,
						(l, pos, s, townCenter) -> townCenter.serverTick(serverLevel, pos, s))
				: null;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
