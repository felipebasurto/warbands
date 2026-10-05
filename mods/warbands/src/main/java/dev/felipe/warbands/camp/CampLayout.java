package dev.felipe.warbands.camp;

import dev.felipe.warbands.Warbands;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One hand-made design of a camp kind: its structure file (same name as {@code id}), footprint {@code size} (odd, the
 * camp is centered), {@code height}, the Camp Standard's spot {@code core}, how deep it's sunk into the ground, its
 * garrison, and {@code spares}: ground spots for the extra guards a higher {@link Threat} brings. All generated from
 * {@code tools/gen_camps.py} into {@link CampLayouts}, with the structure, so the numbers can't drift apart.
 */
public record CampLayout(String id, int size, int height, BlockPos core, int sink, List<CampTier.Guard> guards,
		List<BlockPos> spares) {
	public int half() {
		return size / 2;
	}

	public Identifier structureId() {
		return Warbands.id(id);
	}

	/** What the War Map's scouts say about this layout: its shape and a weak spot to try. */
	public Component scoutReport() {
		return Component.translatable("camp_layout.warbands." + id);
	}
}
