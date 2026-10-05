package dev.felipe.warbands.entity;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.Age;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Feudal line infantry with a vanilla spear that improves with the owner's age (stone, iron, diamond),
 * hitting as hard as a Militia of the same age, and harder against horses and their riders.
 */
public class SpearmanEntity extends SoldierEntity {
	private static final Identifier MOUNTED_BONUS_ID = Warbands.id("spear_vs_mounted");
	/** Extra damage against a horse, a knight, or anyone riding a horse (zombie horsemen, camel husks). */
	public static final double MOUNTED_BONUS = 4.0;

	public SpearmanEntity(EntityType<? extends SpearmanEntity> type, Level level) {
		super(type, level);
	}

	private static final double MAX_HEALTH = 20.0;
	private static final double ARMOR = 5.0;
	/**
	 * Vanilla spears add little flat damage (stone +1, iron +2, diamond +3) because players charge with them.
	 * Soldiers only jab, so the base makes up the rest: 5 / 6 / 7 per age, like the Militia's swords.
	 */
	private static final double ATTACK = 4.0;

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
		boolean mounted = isMounted(target);
		AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (mounted && attack != null) {
			attack.addOrUpdateTransientModifier(
					new AttributeModifier(MOUNTED_BONUS_ID, MOUNTED_BONUS, AttributeModifier.Operation.ADD_VALUE));
		}
		boolean hit;
		try {
			hit = super.doHurtTarget(level, target);
		} finally {
			if (mounted && attack != null) {
				attack.removeModifier(MOUNTED_BONUS_ID);
			}
		}
		if (hit) {
			this.playSound(mounted ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, 1.0F,
					0.9F + this.getRandom().nextFloat() * 0.2F);
		}
		return hit;
	}

	/** A horse, a knight, or a rider whose vehicle is a horse. Boats and other mounts do not count. */
	private static boolean isMounted(Entity target) {
		if (target instanceof AbstractHorse || target instanceof KnightEntity) {
			return true;
		}
		Entity vehicle = target.getVehicle();
		return vehicle instanceof AbstractHorse;
	}

	@Override
	protected void equipLoadout() {
		equipForAge(gearAge());
	}

	@Override
	protected void equipForAge(Age age) {
		this.setItemSlot(EquipmentSlot.MAINHAND, spear(age));
	}

	/**
	 * A vanilla spear without its reach component: that component sets a minimum reach too, and a
	 * {@code MeleeAttackGoal} walks right up to its target, where it could never hit.
	 */
	private static ItemStack spear(Age age) {
		ItemStack spear = new ItemStack(age.spearmanWeapon());
		spear.remove(DataComponents.ATTACK_RANGE);
		return spear;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		// Spearmen saved by older versions held the mod's own spear item, which no longer exists.
		if (this.getMainHandItem().isEmpty() || !this.getMainHandItem().has(DataComponents.KINETIC_WEAPON)) {
			equipForAge(gearAge());
		}
	}
}
