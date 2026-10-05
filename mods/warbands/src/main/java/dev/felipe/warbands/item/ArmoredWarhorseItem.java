package dev.felipe.warbands.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The Cavalry Encampment's rare prize: a whistle that calls a warhorse, tamed to the caller, saddled, in diamond
 * barding and with the best stats a vanilla horse can roll. One use; the barding comes off like any horse armor.
 */
public class ArmoredWarhorseItem extends Item {
	public ArmoredWarhorseItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		// A plain vanilla horse, not a WarhorseEntity: that one is a knight's mount, refuses riders and vanishes
		// without its knight.
		Horse horse = EntityType.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return InteractionResult.FAIL;
		}
		horse.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getRotation(), 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
		horse.setTamed(true);
		horse.setPersistenceRequired();
		if (context.getPlayer() != null) {
			horse.setOwner(context.getPlayer());
		}
		// The best a vanilla horse can roll: fast, a high jumper, hard to kill.
		setBase(horse, Attributes.MAX_HEALTH, 30.0);
		setBase(horse, Attributes.MOVEMENT_SPEED, 0.3375);
		setBase(horse, Attributes.JUMP_STRENGTH, 1.0);
		horse.setHealth(horse.getMaxHealth());
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
		// Like gear a player put on: it comes back if the horse dies.
		horse.setGuaranteedDrop(EquipmentSlot.SADDLE);
		horse.setGuaranteedDrop(EquipmentSlot.BODY);
		horse.setCustomName(Component.translatable("entity.warbands.warhorse"));
		level.addFreshEntity(horse);
		level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
		arrive(level, horse, context.getPlayer());
		context.getItemInHand().consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}

	/**
	 * The call and the arrival: a two-note whistle from the player, then the horse bursts out of a dust cloud with a
	 * neigh, clanking barding and a short burst of speed and regeneration so it arrives ready to ride.
	 */
	private static void arrive(ServerLevel level, Horse horse, Player player) {
		if (player != null) {
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.5F, 1.6F);
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.5F, 2.0F);
			level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getEyeY() + 0.5, player.getZ(), 3, 0.3, 0.2, 0.3, 1.0);
		}
		BlockPos pos = horse.blockPosition();
		level.playSound(null, pos, SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.5F, 1.0F);
		level.playSound(null, pos, SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1.0F, 0.9F);
		level.playSound(null, pos, SoundEvents.HORSE_ARMOR.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			level.sendParticles(ParticleTypes.POOF, horse.getX() + Math.cos(angle) * 1.2, horse.getY() + 0.2, horse.getZ() + Math.sin(angle) * 1.2,
					1, 0.0, 0.0, 0.0, 0.05);
		}
		level.sendParticles(ParticleTypes.CLOUD, horse.getX(), horse.getY() + 0.8, horse.getZ(), 20, 0.6, 0.5, 0.6, 0.02);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, horse.getX(), horse.getY() + 1.6, horse.getZ(), 12, 0.6, 0.4, 0.6, 0.0);
		level.sendParticles(ParticleTypes.HEART, horse.getX(), horse.getY() + 2.0, horse.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		horse.addEffect(new MobEffectInstance(MobEffects.SPEED, 30 * 20, 1));
		horse.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 0));
	}

	private static void setBase(Horse horse, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = horse.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.warbands.warhorse_whistle").withStyle(ChatFormatting.GRAY));
	}
}
