package dev.felipe.warbands.entity;

import dev.felipe.warbands.town.Age;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Cheap melee infantry: kettle helmet, red gambeson, stone sword and shield (the outfit is in the skin). */
public class MilitiaEntity extends SoldierEntity {
	public MilitiaEntity(EntityType<? extends MilitiaEntity> type, Level level) {
		super(type, level);
	}

	private static final double MAX_HEALTH = 20.0;
	private static final double ARMOR = 6.0;

	public static AttributeSupplier.Builder createAttributes() {
		return createSoldierAttributes(MAX_HEALTH, ARMOR);
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
			this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
		}
		return hit;
	}

	@Override
	protected void equipLoadout() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
		this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	/** Stone sword in the Village Age, iron in Feudal, diamond in Castle. */
	@Override
	protected void equipForAge(Age age) {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(age.militiaWeapon()));
	}
}
