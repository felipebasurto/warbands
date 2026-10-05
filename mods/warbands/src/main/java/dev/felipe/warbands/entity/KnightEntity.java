package dev.felipe.warbands.entity;

import dev.felipe.warbands.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Castle cavalry. The soldier is the unit; the horse only carries them and drops nothing. */
public class KnightEntity extends SoldierEntity {
	private boolean mounted;

	public KnightEntity(EntityType<? extends KnightEntity> type, Level level) {
		super(type, level);
	}

	private static final double MAX_HEALTH = 28.0;
	private static final double ARMOR = 7.0;
	/** With the iron sword's +5 a knight hits for 8, the hardest in the army (a Castle Militia hits for 7). */
	private static final double ATTACK = 3.0;

	public static AttributeSupplier.Builder createAttributes() {
		return createSoldierAttributes(MAX_HEALTH, ARMOR, ATTACK);
	}

	@Override
	protected double baseMaxHealth() {
		return MAX_HEALTH;
	}

	@Override
	protected double baseArmor() {
		return ARMOR;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F + this.getRandom().nextFloat() * 0.2F);
		}
		return hit;
	}

	@Override
	protected void equipLoadout() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	@Override
	public boolean shouldTryTeleportToOwner() {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide() || this.mounted || !this.isAlive()) {
			return;
		}
		if (this.getVehicle() instanceof WarhorseEntity) {
			this.mounted = true;
			return;
		}
		this.mounted = true;
		if (this.level() instanceof ServerLevel serverLevel) {
			mountWarhorse(serverLevel);
		}
	}

	@Override
	public void stopRiding() {
		Entity vehicle = this.getVehicle();
		if (vehicle instanceof WarhorseEntity horse && horse.isAlive() && !horse.isRemoved() && this.isAlive()) {
			return;
		}
		super.stopRiding();
	}

	private void mountWarhorse(ServerLevel level) {
		WarhorseEntity horse = ModEntities.WARHORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return;
		}
		horse.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
		horse.prepareFor(this);
		level.addFreshEntity(horse);
		this.startRiding(horse, true, true);
	}
}
