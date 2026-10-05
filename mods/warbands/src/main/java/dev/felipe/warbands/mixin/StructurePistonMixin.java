package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.town.StructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pistons can't move blueprint blocks: a pushed block would lose its protection and drop normally. Camp contraption
 * parts ({@link Camps#movable}) are the exception: they drop nothing anyway.
 */
@Mixin(PistonBaseBlock.class)
public abstract class StructurePistonMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void warbands$structureImmovable(BlockState state, Level level, BlockPos pos, Direction direction,
			boolean allowDestroyable, Direction connectionDirection, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel serverLevel && !Camps.movable(state) && StructureBlocks.contains(serverLevel, pos)) {
			cir.setReturnValue(false);
		}
	}
}
