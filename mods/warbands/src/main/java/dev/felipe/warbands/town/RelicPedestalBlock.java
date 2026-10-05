package dev.felipe.warbands.town;

import dev.felipe.warbands.camp.CampTier;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a town keeps its relics. It only works inside the reach of your Town Center: right-click with a relic to
 * enshrine it, right-click empty-handed to read the collection. A relic kind already enshrined is turned away, so the
 * collection is "4 of 4" and effects don't stack. Each enshrined relic lights a gem on the pedestal in its camp's
 * colour, gives off its camp's particles, and the pedestal glows brighter with every relic.
 */
public class RelicPedestalBlock extends Block {
	public static final BooleanProperty HAG = BooleanProperty.create("hag");
	public static final BooleanProperty FROST = BooleanProperty.create("frost");
	public static final BooleanProperty GRAVE = BooleanProperty.create("grave");
	public static final BooleanProperty OMEN = BooleanProperty.create("omen");
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 11, 12),
			Block.box(2, 11, 2, 14, 14, 14));

	public RelicPedestalBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HAG, false).setValue(FROST, false).setValue(GRAVE, false)
				.setValue(OMEN, false));
	}

	/** Light by relic count: dark when empty, 15 with the full set. */
	public static int light(BlockState state) {
		int count = 0;
		for (BooleanProperty relic : List.of(HAG, FROST, GRAVE, OMEN)) {
			count += state.getValue(relic) ? 1 : 0;
		}
		return count == 0 ? 0 : 3 + count * 3;
	}

	private static BooleanProperty property(CampTier kind) {
		return switch (kind) {
			case WITCH_COVEN -> HAG;
			case FROZEN_OUTPOST -> FROST;
			case SUNKEN_CRYPT -> GRAVE;
			default -> OMEN;
		};
	}

	/** Shows the town's collection: one lit gem per enshrined relic. */
	private static void show(ServerLevel level, BlockPos pos, BlockState state, List<CampTier> kinds) {
		BlockState shown = state.setValue(HAG, false).setValue(FROST, false).setValue(GRAVE, false).setValue(OMEN, false);
		for (CampTier kind : kinds) {
			shown = shown.setValue(property(kind), true);
		}
		if (shown != state) {
			level.setBlock(pos, shown, Block.UPDATE_ALL);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HAG, FROST, GRAVE, OMEN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** A pedestal placed in a town that already holds relics shows them at once. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
		if (level instanceof ServerLevel server && by instanceof Player player) {
			TownRegistry.Town town = nearestTown(server, pos, player.getUUID());
			if (town != null) {
				show(server, pos, state, Relics.enshrined(server, town.pos()));
			}
		}
	}

	/** Each relic's camp breathes over its gem: hexes, snow, souls and the raid omen. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		spark(state, HAG, ParticleTypes.WITCH, level, pos, 0.25, 0.25, random);
		spark(state, FROST, ParticleTypes.SNOWFLAKE, level, pos, 0.75, 0.25, random);
		spark(state, GRAVE, ParticleTypes.SOUL_FIRE_FLAME, level, pos, 0.75, 0.75, random);
		spark(state, OMEN, ParticleTypes.RAID_OMEN, level, pos, 0.25, 0.75, random);
		if (light(state) == 15 && random.nextInt(3) == 0) {
			// The full collection: a slow column of light over the pedestal.
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.1,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.04, 0.0);
		}
	}

	private static void spark(BlockState state, BooleanProperty relic, ParticleOptions particle, Level level, BlockPos pos,
			double x, double z, RandomSource random) {
		if (state.getValue(relic) && random.nextInt(2) == 0) {
			level.addParticle(particle, pos.getX() + x + (random.nextDouble() - 0.5) * 0.2, pos.getY() + 1.15,
					pos.getZ() + z + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || Relics.kindOf(stack.getItem()) == null) {
			return InteractionResult.PASS;
		}
		CampTier kind = Relics.kindOf(stack.getItem());
		TownRegistry.Town town = town(server, pos, player);
		if (town == null) {
			return InteractionResult.CONSUME;
		}
		if (!Relics.enshrine(server, town.pos(), kind)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.relic_already").withStyle(ChatFormatting.RED));
			return InteractionResult.CONSUME;
		}
		Component relic = stack.getHoverName();
		stack.consume(1, player);
		show(server, pos, state, Relics.enshrined(server, town.pos()));
		server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.3, 0.4, 0.3, 0.3);
		server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		// Chat, not the action bar: two overlay lines in a row would hide the first.
		player.sendSystemMessage(Component.translatable("message.warbands.relic_enshrined", relic,
				Relics.enshrined(server, town.pos()).size(), Relics.total()).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("handbook.warbands.relics." + kind.serializedName() + ".effect")
				.withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.PASS;
		}
		TownRegistry.Town town = town(server, pos, player);
		if (town == null) {
			return InteractionResult.CONSUME;
		}
		List<CampTier> kinds = Relics.enshrined(server, town.pos());
		show(server, pos, state, kinds);
		if (kinds.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.relics_none").withStyle(ChatFormatting.GRAY));
		} else {
			player.sendOverlayMessage(Component.translatable("message.warbands.relics_count", kinds.size(),
					Relics.total()).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	/** The player's town whose center is in reach of this pedestal, or null (with a refusal message) outside one. */
	private static TownRegistry.Town town(ServerLevel level, BlockPos pos, Player player) {
		TownRegistry.Town found = nearestTown(level, pos, player.getUUID());
		if (found == null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.relic_no_town").withStyle(ChatFormatting.RED));
		}
		return found;
	}

	private static TownRegistry.Town nearestTown(ServerLevel level, BlockPos pos, UUID owner) {
		TownRegistry.Town found = null;
		double best = Double.MAX_VALUE;
		for (TownRegistry.Town t : TownRegistry.towns(level)) {
			if (!t.owner().equals(owner)) {
				continue;
			}
			double dist = t.pos().distSqr(pos);
			if (dist < best && dist <= Relics.TOWN_RANGE * Relics.TOWN_RANGE) {
				best = dist;
				found = t;
			}
		}
		return found;
	}
}
