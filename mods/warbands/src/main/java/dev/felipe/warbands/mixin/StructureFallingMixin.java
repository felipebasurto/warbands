package dev.felipe.warbands.mixin;

import dev.felipe.warbands.town.StructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blueprint's anvil doesn't fall when its floor is broken, so it can't land somewhere unprotected and be mined. */
@Mixin(FallingBlock.class)
public abstract class StructureFallingMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void warbands$structureStays(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (StructureBlocks.contains(level, pos)) {
			ci.cancel();
		}
	}
}
