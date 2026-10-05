package dev.felipe.warbands.client;

import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintItem;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** While holding a blueprint, outlines its footprint with particles: green = fits, red = blocked, white = entrance. */
public final class BlueprintPreview {
	private static final DustParticleOptions FITS = new DustParticleOptions(0x55FF55, 1.0F);
	private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xFF4444, 1.0F);
	private static final DustParticleOptions DOOR = new DustParticleOptions(0xFFFFFF, 1.2F);

	private BlueprintPreview() {
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (minecraft.player == null || level == null || minecraft.isPaused() || level.getGameTime() % 4 != 0) {
			return;
		}
		if (!(minecraft.player.getMainHandItem().getItem() instanceof BlueprintItem item)) {
			return;
		}
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| hit.getDirection() != Direction.UP) {
			return;
		}
		Blueprint blueprint = item.blueprint();
		Direction facing = minecraft.player.getDirection();
		BlockPos center = BlueprintPlacer.centerFor(hit.getBlockPos(), facing, blueprint);
		boolean fits = BlueprintPlacer.check(level, center, blueprint, minecraft.player) == BlueprintPlacer.Problem.NONE;
		DustParticleOptions dust = fits ? FITS : BLOCKED;

		int half = blueprint.half();
		double minX = center.getX() - half;
		double minZ = center.getZ() - half;
		double maxX = center.getX() + half + 1;
		double maxZ = center.getZ() + half + 1;
		double y = center.getY() + 1.1;
		for (double t = 0; t <= blueprint.size(); t += 0.5) {
			level.addParticle(dust, minX + t, y, minZ, 0, 0, 0);
			level.addParticle(dust, minX + t, y, maxZ, 0, 0, 0);
			level.addParticle(dust, minX, y, minZ + t, 0, 0, 0);
			level.addParticle(dust, maxX, y, minZ + t, 0, 0, 0);
		}

		// The entrance is right behind the clicked block, facing the player.
		BlockPos entrance = hit.getBlockPos().relative(facing);
		for (double dy = 0; dy < 2; dy += 0.5) {
			level.addParticle(DOOR, entrance.getX() + 0.5, y + dy, entrance.getZ() + 0.5, 0, 0, 0);
		}
	}
}
