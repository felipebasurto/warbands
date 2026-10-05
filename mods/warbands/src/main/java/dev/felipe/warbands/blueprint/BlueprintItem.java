package dev.felipe.warbands.blueprint;

import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.Age;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Right-click the top of the ground to build this blueprint's structure there. */
public class BlueprintItem extends Item {
	private final Blueprint blueprint;

	public BlueprintItem(Blueprint blueprint, Properties properties) {
		super(properties);
		this.blueprint = blueprint;
	}

	public Blueprint blueprint() {
		return blueprint;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (context.getClickedFace() != Direction.UP) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.warbands.click_ground"));
			}
			return InteractionResult.FAIL;
		}
		BlockPos center = BlueprintPlacer.centerFor(context.getClickedPos(), player.getDirection(), blueprint);
		BlueprintPlacer.Problem problem = level instanceof ServerLevel serverLevel
				? BlueprintPlacer.checkServer(serverLevel, center, blueprint, player)
				: BlueprintPlacer.check(level, center, blueprint, player);
		if (problem != BlueprintPlacer.Problem.NONE) {
			if (!level.isClientSide()) {
				BlockPos obstacle = BlueprintPlacer.findObstacle(level, center, blueprint);
				player.sendOverlayMessage(obstacle == null ? problem.message(blueprint)
						: Component.translatable("message.warbands.blocked_by", level.getBlockState(obstacle).getBlock().getName()));
			}
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			if (!BlueprintPlacer.place(serverLevel, center, blueprint, player)) {
				player.sendOverlayMessage(Component.translatable("message.warbands.missing_structure"));
				return InteractionResult.FAIL;
			}
			context.getItemInHand().consume(1, player);
			level.playSound(null, center, SoundEvents.VILLAGER_WORK_MASON, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.playSound(null, center, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		// What it's for first: "Builds a 7×7 building" alone never said a house raises population.
		tooltip.accept(Component.translatable("tooltip.warbands.blueprint." + blueprint.getName() + ".purpose")
				.withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("tooltip.warbands.blueprint.size", blueprint.size(), blueprint.size())
				.withStyle(ChatFormatting.GRAY));
		if (blueprint == Blueprint.TOWN_CENTER) {
			tooltip.accept(Component.translatable("tooltip.warbands.blueprint.town_spacing", TownCenterBlockEntity.TOWN_SPACING)
					.withStyle(ChatFormatting.GRAY));
		}
		if (blueprint.needsTown()) {
			tooltip.accept(Component.translatable("tooltip.warbands.blueprint.near_town").withStyle(ChatFormatting.GRAY));
		}
		if (blueprint.requiredAge() != Age.VILLAGE) {
			tooltip.accept(Component.translatable("tooltip.warbands.blueprint." + blueprint.requiredAge().serializedName())
					.withStyle(ChatFormatting.GRAY));
		}
		if (blueprint.stoneCost() > 0) {
			tooltip.accept(Component.translatable("tooltip.warbands.blueprint.stone", blueprint.stoneCost()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("tooltip.warbands.blueprint.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
