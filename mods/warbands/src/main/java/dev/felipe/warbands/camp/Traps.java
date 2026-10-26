package dev.felipe.warbands.camp;

import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.registry.LandPathTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.network.chat.Component;

/**
 * Camp traps (Phase 4D): the wiring that makes a taken campsite fight back while it stands. Alarm plates ring and
 * rouse the guards, tripwires fire stocked dispensers or light powder kegs, caltrops hurt underfoot. Everything stops
 * once the camp is claimed, and the dispensers are sealed shut (no free arrows) while the camp exists.
 */
public final class Traps {
	/** Camp center → game time of the last alarm, so one runner doesn't machine-gun the bell. */
	private static final Map<String, Long> ALARMED = new HashMap<>();
	private static final long ALARM_COOLDOWN = 60;

	/** Forgets the alarms of a world that has closed (statics outlive a singleplayer world within one game session). */
	public static void clearState() {
		ALARMED.clear();
	}

	private Traps() {
	}

	/** An alarm plate stepped on: bell over the camp, then every unposted guard sallies out after the intruder. */
	public static void ring(ServerLevel level, BlockPos plate, LivingEntity intruder) {
		Camp camp = Camps.campAt(level, plate).orElse(null);
		if (camp == null || camp.conquered() || camp.abandoned() || camp.razed()) {
			return;
		}
		long now = level.getGameTime();
		String key = level.dimension() + "@" + camp.center().asLong();
		Long last = ALARMED.get(key);
		if (last != null && now - last < ALARM_COOLDOWN) {
			return;
		}
		ALARMED.put(key, now);
		level.playSound(null, plate, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 0.8F);
		level.playSound(null, plate, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 0.7F);
		Camps.alarm(level, camp, intruder);
	}

	/** Trap arrows alive at once, server-wide, and how long each lasts (in flight or stuck). */
	private static final int MAX_TRAP_ARROWS = 40;
	private static final int TRAP_ARROW_TICKS = 5 * 20;
	private static final ArrayDeque<AbstractArrow> TRAP_ARROWS = new ArrayDeque<>();

	/**
	 * Dispenser-in-camp seal (open and break), unrecoverable trap arrows and the caltrops path-type. Hoppers pull
	 * without opening the block; that path is {@code HopperDispenserMixin}.
	 */
	public static void initialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server)
					|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof DispenserBlock)) {
				return InteractionResult.PASS;
			}
			// Sealed as long as the camp stands, burning included: the guns' ammo isn't loot.
			if (Camps.campAt(server, hit.getBlockPos()).isEmpty()) {
				return InteractionResult.PASS;
			}
			player.sendSystemMessage(Component.translatable("message.warbands.dispenser_sealed"));
			return InteractionResult.FAIL;
		});
		// Breaking a camp's dispenser spills nothing: its ammo is emptied first (registered before the structure-block
		// handler, which would otherwise drop the contents like a filled barrel's).
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && blockEntity instanceof DispenserBlockEntity dispenser
					&& Camps.campAt(server, pos).isPresent()) {
				dispenser.clearContent();
			}
			return true;
		});
		LandPathTypeRegistry.register(ModBlocks.CALTROPS, PathType.DAMAGING, PathType.DAMAGING);
		// A tripwire's arrows are the camp's, not loot: vanilla lets dispenser arrows be picked up, so they're taken
		// back on spawn (ownerless arrows only, i.e. never one a player or soldier shot).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() == null
					&& arrow.pickup == AbstractArrow.Pickup.ALLOWED && Camps.armedAt(level, arrow.blockPosition())) {
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				TRAP_ARROWS.add(arrow);
			}
		});
		// Guns can fire for minutes: stuck arrows would pile up (lag), so trap arrows are short-lived and capped.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			while (!TRAP_ARROWS.isEmpty()) {
				AbstractArrow oldest = TRAP_ARROWS.peekFirst();
				if (oldest.isRemoved()) {
					TRAP_ARROWS.pollFirst();
				} else if (TRAP_ARROWS.size() > MAX_TRAP_ARROWS || oldest.tickCount > TRAP_ARROW_TICKS) {
					TRAP_ARROWS.pollFirst().discard();
				} else {
					break;
				}
			}
		});
	}

	/** A camp's own defenders don't trip or feel its traps (vanilla guards by tag, raider soldiers by flag). */
	public static boolean campOwn(Entity entity) {
		return entity instanceof Mob mob && (mob.entityTags().contains(Camps.GUARD_TAG)
				|| mob instanceof SoldierEntity soldier && soldier.isRaider());
	}
}
