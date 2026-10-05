package dev.felipe.warbands.item;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.ArmyRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Gives your whole army its next order: Follow, Hold, Attack. The order is the army's, not only of the soldiers
 * nearby: soldiers far away take it up when their chunk loads, and new recruits start with it (see
 * {@code ArmyOrders}). Soldiers posted on a Watch Tower stay there. The horn is blown like a goat horn, with a ring
 * of particles and a mark over every soldier that heard it.
 * <p>
 * Sounding <b>Follow</b> also rallies stragglers: soldiers more than {@link #RALLY_DISTANCE} blocks away (in loaded
 * land, same dimension) come to your side. It's meant for soldiers lost on the march, not for dropping an army into a
 * fight, so it doesn't work while you're in combat and has its own {@link #RALLY_COOLDOWN_TICKS} cooldown.
 */
public class WarHornItem extends Item {
	/** Short on purpose: a wrong order can be fixed straight away (Shift goes back one). */
	private static final int COOLDOWN_TICKS = 5;
	private static final int USE_TICKS = 8;
	/** After an order, the action bar keeps its message this long before the order picker shows again. */
	private static final int MESSAGE_TICKS = 60;
	private static final double RALLY_DISTANCE = 32.0;
	private static final int RALLY_COOLDOWN_TICKS = 2 * 60 * 20;
	/** You're in combat if something hurt you this recently, or a raider is this close. */
	private static final int COMBAT_TICKS = 10 * 20;
	private static final double COMBAT_RANGE = 24.0;
	/** Game time each commander last rallied. In memory: a restart just resets the cooldown. */
	private static final Map<UUID, Long> LAST_RALLY = new HashMap<>();
	/** Game time each commander last blew the horn, so the picker doesn't hide the order message. */
	private static final Map<UUID, Long> LAST_ORDER = new HashMap<>();

	/** Forgets the horn cooldowns of a world that has closed, or of a player who left. */
	public static void clearState() {
		LAST_RALLY.clear();
		LAST_ORDER.clear();
	}

	public WarHornItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.CONSUME;
		}
		UUID owner = player.getUUID();
		int army = ArmyRegistry.count(serverLevel.getServer(), owner);
		if (army == 0) {
			player.sendOverlayMessage(Component.translatable("message.warbands.no_soldiers"));
			player.stopUsingItem();
			return InteractionResult.FAIL;
		}
		Stance current = ArmyOrders.get(serverLevel.getServer(), owner);
		Stance next = player.isShiftKeyDown() ? current.previous() : current.next();
		LAST_ORDER.put(owner, serverLevel.getGameTime());
		ArmyOrders.set(serverLevel.getServer(), owner, next);
		List<SoldierEntity> heard = new ArrayList<>();
		for (UUID id : ArmyRegistry.ids(serverLevel.getServer(), owner)) {
			for (ServerLevel any : serverLevel.getServer().getAllLevels()) {
				Entity entity = any.getEntity(id);
				if (entity instanceof SoldierEntity soldier && soldier.isAlive() && !soldier.isPosted()) {
					soldier.endMuster();
					soldier.command(next);
					heard.add(soldier);
				}
			}
		}
		player.sendOverlayMessage(Component.translatable("message.warbands.order", next.displayName(), army)
				.withStyle(next == Stance.CHARGE ? ChatFormatting.RED : ChatFormatting.GOLD)
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("stance.warbands." + next.serializedName() + ".what").withStyle(ChatFormatting.GRAY)));
		if (next == Stance.FOLLOW) {
			rally(serverLevel, player, heard);
		}
		serverLevel.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(next == Stance.CHARGE ? 3 : 0).value(),
				SoundSource.PLAYERS, 2.0F, next == Stance.HOLD ? 0.8F : 1.0F);
		effects(serverLevel, player, next, heard);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.CONSUME;
	}

	/** While the horn is in hand: the three orders with the army's current one picked out, and how to go back. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!(owner instanceof Player player) || level.getGameTime() % 10 != 0
				|| (player.getMainHandItem() != stack && player.getOffhandItem() != stack)
				|| level.getGameTime() - LAST_ORDER.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < MESSAGE_TICKS
				|| ArmyRegistry.count(level.getServer(), player.getUUID()) == 0) {
			return;
		}
		Stance current = ArmyOrders.get(level.getServer(), player.getUUID());
		MutableComponent line = Component.empty();
		for (Stance stance : Stance.values()) {
			if (stance.ordinal() > 0) {
				line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(stance == current
					? Component.literal("[").append(stance.displayName()).append("]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
					: stance.displayName().copy().withStyle(ChatFormatting.GRAY));
		}
		line.append(Component.literal("   ").append(Component.translatable("message.warbands.horn_picker")).withStyle(ChatFormatting.DARK_GRAY));
		player.sendOverlayMessage(line);
	}

	/** Brings far-away soldiers to the player, unless they're in a fight or rallied recently. */
	private static void rally(ServerLevel level, Player player, List<SoldierEntity> heard) {
		List<SoldierEntity> far = heard.stream().filter(s -> s.level() == level && !s.isPassenger()
				&& s.distanceToSqr(player) > RALLY_DISTANCE * RALLY_DISTANCE).toList();
		if (far.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		boolean hurt = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < COMBAT_TICKS;
		boolean raiders = !level.getEntitiesOfClass(SoldierEntity.class, player.getBoundingBox().inflate(COMBAT_RANGE),
				SoldierEntity::isRaider).isEmpty();
		if (hurt || raiders) {
			player.sendSystemMessage(Component.translatable("message.warbands.rally_combat", far.size()).withStyle(ChatFormatting.GRAY));
			return;
		}
		long last = LAST_RALLY.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
		if (now - last < RALLY_COOLDOWN_TICKS) {
			player.sendSystemMessage(Component.translatable("message.warbands.rally_cooldown", far.size(),
					(RALLY_COOLDOWN_TICKS - (now - last) + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		LAST_RALLY.put(player.getUUID(), now);
		int came = 0;
		for (SoldierEntity soldier : far) {
			soldier.tryToTeleportToOwner();
			if (soldier.distanceToSqr(player) <= RALLY_DISTANCE * RALLY_DISTANCE) {
				level.sendParticles(ParticleTypes.POOF, soldier.getX(), soldier.getY() + 1.0, soldier.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
				came++;
			}
		}
		player.sendSystemMessage(Component.translatable("message.warbands.rally", came).withStyle(ChatFormatting.GOLD));
	}

	/** A ring spreading from the horn, and a mark over each soldier that heard it, in the order's colours. */
	private static void effects(ServerLevel level, Player player, Stance order, List<SoldierEntity> heard) {
		ParticleOptions ring = switch (order) {
			case FOLLOW -> ParticleTypes.HAPPY_VILLAGER;
			case HOLD -> ParticleTypes.END_ROD;
			case CHARGE -> ParticleTypes.FLAME;
		};
		for (int r = 1; r <= 4; r++) {
			int points = 12 * r;
			for (int i = 0; i < points; i++) {
				double angle = Math.PI * 2 * i / points;
				level.sendParticles(ring, player.getX() + Math.cos(angle) * r * 1.2, player.getY() + 0.3, player.getZ() + Math.sin(angle) * r * 1.2,
						1, 0.0, 0.05, 0.0, 0.0);
			}
		}
		ParticleOptions mark = switch (order) {
			case FOLLOW -> ParticleTypes.NOTE;
			case HOLD -> ParticleTypes.ENCHANTED_HIT;
			case CHARGE -> ParticleTypes.ANGRY_VILLAGER;
		};
		for (SoldierEntity soldier : heard) {
			if (soldier.level() == level) {
				level.sendParticles(mark, soldier.getX(), soldier.getEyeY() + 0.7, soldier.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.warbands.war_horn").withStyle(ChatFormatting.GRAY));
	}
}
