package dev.felipe.warbands.mixin;

import dev.felipe.warbands.town.StructureBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blueprint blocks drop nothing however they break: explosions, lost support (hanging lanterns),
 * fluids. The player-break path is handled earlier by {@code StructureBlocks.beforeBreak}.
 * Container contents are dropped elsewhere, so a filled barrel still gives its items back.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class StructureDropsMixin {
	@Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
	private void warbands$noStructureDrops(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && StructureBlocks.release(params.getLevel(), BlockPos.containing(origin))) {
			cir.setReturnValue(List.of());
		}
	}
}
