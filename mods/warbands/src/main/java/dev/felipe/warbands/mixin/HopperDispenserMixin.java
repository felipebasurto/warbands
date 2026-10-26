package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.DispenserSeal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camp dispensers are sealed, but a hopper never opens them: it pulls the block above it. Fabric's transfer hook
 * replaces the body of {@code suckInItems} once it finds that container, and the pull uses {@code setItem} rather
 * than {@code Container.canTakeItem} (the default is true, and a dispenser is not a worldly container). Refuse
 * before that hook. Hopper minecarts come through the same method. Firing is untouched: a dispenser shoots with
 * {@code getItem} and {@code setItem}.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperDispenserMixin {
	@Inject(method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
	private static void warbands$sealedCampDispensers(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
		// The block suckInItems reads, including a minecart that is not sitting on a grid point.
		BlockPos above = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
		boolean dispenser = level.getBlockState(above).getBlock() instanceof DispenserBlock;
		boolean camp = dispenser && level instanceof ServerLevel server && Camps.campAt(server, above).isPresent();
		if (!DispenserSeal.hopperMayPull(dispenser, camp)) {
			cir.setReturnValue(false);
		}
	}
}
