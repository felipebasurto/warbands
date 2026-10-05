package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A commander's waiting trophy or relic is picked up by them alone. Vanilla hoppers and hopper minecarts skip the
 * pickup-owner check, so they are told to leave these items where they lie.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperRewardMixin {
	@Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
	private static void warbands$leaveRewards(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
		if (item.entityTags().contains(Camps.REWARD_TAG)) {
			cir.setReturnValue(false);
		}
	}
}
