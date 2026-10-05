package dev.felipe.warbands.entity;

import dev.felipe.warbands.entity.ai.DefendPostGoal;
import dev.felipe.warbands.entity.ai.SoldierBowAttackGoal;
import dev.felipe.warbands.town.Upgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Ranged unit in a green hood with a quiver: keeps its distance and shoots. Its arrows can't be picked up. */
public class ArcherEntity extends SoldierEntity implements RangedAttackMob {
	public ArcherEntity(EntityType<? extends ArcherEntity> type, Level level) {
		super(type, level);
	}

	private static final double MAX_HEALTH = 16.0;
	private static final double ARMOR = 3.0;

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

	/** Ticks between shots: faster in later ages. */
	public int shotIntervalTicks() {
		return gearAge().archerShotTicks();
	}

	@Override
	public boolean canGarrison() {
		return true;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(2, new SoldierBowAttackGoal(this, 1.0));
		this.targetSelector.addGoal(3, new DefendPostGoal(this));
	}

	@Override
	protected void equipLoadout() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
	}

	@Override
	public float attackRadius() {
		return 15.0F + (Gear.has(this, Upgrade.FLETCHING) ? Gear.FLETCHING_RANGE : 0.0F);
	}

	public float shotInaccuracy() {
		return Gear.has(this, Upgrade.FLETCHING) ? 2.0F : 4.0F;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		ItemStack bow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.addTag(Gear.ARROW_TAG);
		if (Gear.has(this, Upgrade.BODKIN_ARROWS)) {
			// Vanilla's mob arrow is power * 2 plus a little difficulty noise (there's no getter to add to it).
			arrow.setBaseDamage(power * 2.0 + Gear.BODKIN_DAMAGE);
		}
		double dx = target.getX() - this.getX();
		double dy = target.getY(1.0 / 3.0) - arrow.getY();
		double dz = target.getZ() - this.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (this.level() instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileUsingShoot(arrow, serverLevel, ammo, dx, dy + horizontal * 0.2, dz, 1.6F, shotInaccuracy());
		}
		this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
	}
}
