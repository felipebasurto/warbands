package dev.felipe.warbands.camp;

import dev.felipe.warbands.Ground;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.town.StructureBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A test world for the camps ({@code /warbands testworld}): every layout of every kind is registered on a grid around
 * the player, {@link #SPACING} apart so only the camp you visit builds and fights. They are built by the normal tick when
 * you come near (or teleport there with {@code testworld tp <id>}), with their real guards, traps and rules. Meant for a
 * superflat creative world; {@code rebuild <id>} wipes one camp and registers it again after a code change.
 */
public final class CampTestWorld {
	private static final int SPACING = 150;
	private static final int COLUMNS = 6;

	private CampTestWorld() {
	}

	/** Every layout id with its kind, in a fixed order (the grid order). */
	public static List<CampLayout> layouts() {
		List<CampLayout> all = new ArrayList<>();
		for (CampTier tier : CampTier.values()) {
			all.addAll(tier.layouts());
		}
		return all;
	}

	private static CampTier tierOf(CampLayout layout) {
		for (CampTier tier : CampTier.values()) {
			if (tier.layouts().contains(layout)) {
				return tier;
			}
		}
		throw new IllegalStateException(layout.id());
	}

	private static BlockPos slot(BlockPos origin, int index) {
		return origin.offset((index % COLUMNS) * SPACING, 0, (index / COLUMNS + 1) * SPACING);
	}

	/** Registers every layout not yet registered; returns how many were added. */
	public static int setUp(ServerLevel level, ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		List<CampLayout> layouts = layouts();
		int added = 0;
		for (int i = 0; i < layouts.size(); i++) {
			if (register(level, player, origin, layouts.get(i), i)) {
				added++;
			}
		}
		player.sendSystemMessage(Component.literal(added + " camps registered (" + layouts.size() + " layouts), "
				+ SPACING + " blocks apart. /warbands testworld list, then click one.").withStyle(ChatFormatting.GOLD));
		return added;
	}

	private static boolean register(ServerLevel level, ServerPlayer player, BlockPos origin, CampLayout layout, int index) {
		BlockPos center = slot(origin, index);
		for (Camp camp : CampRegistry.all(level)) {
			if (camp.layoutId().equals(layout.id())) {
				return false;
			}
		}
		level.getChunkAt(center);
		int y = Ground.surface(level, center.getX(), center.getZ());
		BlockPos site = new BlockPos(center.getX(), y, center.getZ());
		CampTier tier = tierOf(layout);
		Threat estimate = Threat.estimate(level, player.getUUID(), tier.age(), layout.guards().size(), origin);
		// Turned like a camp bought at the origin: its entrance faces back toward it (north).
		CampRegistry.add(level, Camp.chosen(player.getUUID(), origin, tier, site, BlueprintPlacer.rotationFor(Direction.SOUTH),
				layout.id(), estimate));
		return true;
	}

	public static void list(ServerLevel level, ServerPlayer player) {
		for (CampLayout layout : layouts()) {
			Component line = Component.literal(String.format("%-22s %2d blocks  ", layout.id(), layout.size()))
					.append(Component.literal("[tp]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
							.withClickEvent(new ClickEvent.RunCommand("/warbands testworld tp " + layout.id()))))
					.append(Component.literal(" [rebuild]").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.RunCommand("/warbands testworld rebuild " + layout.id()))));
			player.sendSystemMessage(line);
		}
	}

	private static Camp find(ServerLevel level, String id) {
		for (Camp camp : CampRegistry.all(level)) {
			if (camp.layoutId().equals(id)) {
				return camp;
			}
		}
		return null;
	}

	/** Teleports to the camp's door (north of it, where the entrance faces), a few blocks out. */
	public static boolean tp(ServerLevel level, ServerPlayer player, String id) {
		Camp camp = find(level, id);
		if (camp == null) {
			player.sendSystemMessage(Component.literal("No test camp '" + id + "'. Run /warbands testworld first.").withStyle(ChatFormatting.RED));
			return false;
		}
		BlockPos spot = camp.center().offset(0, 0, -(camp.layout().half() + 14));
		level.getChunkAt(spot);
		level.getChunkAt(camp.center());
		int y = Ground.surface(level, spot.getX(), spot.getZ()) + 1;
		player.teleportTo(level, spot.getX() + 0.5, y, spot.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		player.resetFallDistance();
		return true;
	}

	/** Wipes the camp (structure, guards, registry entry) back to flat ground and registers it again to be rebuilt. */
	public static boolean rebuild(ServerLevel level, ServerPlayer player, String id) {
		Camp camp = find(level, id);
		if (camp == null) {
			player.sendSystemMessage(Component.literal("No test camp '" + id + "'.").withStyle(ChatFormatting.RED));
			return false;
		}
		CampLayout layout = camp.layout();
		for (java.util.UUID guard : camp.guards()) {
			Entity entity = level.getEntity(guard);
			if (entity != null) {
				entity.discard();
			}
		}
		int half = layout.half() + 12;
		BlockPos c = camp.center();
		int ground = c.getY();
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-half, -(layout.sink() + 10), -half), c.offset(half, layout.height() + 30, half))) {
			StructureBlocks.release(level, pos);
			level.setBlock(pos, pos.getY() < ground ? Blocks.DIRT.defaultBlockState()
					: pos.getY() == ground ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(),
					Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		}
		level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(c).inflate(half, 40, half)).forEach(Entity::discard);
		CampRegistry.remove(level, camp);
		register(level, player, camp.town(), layout, layouts().indexOf(layout));
		player.sendSystemMessage(Component.literal(id + " wiped; it builds again when you are near.").withStyle(ChatFormatting.GREEN));
		return true;
	}
}
