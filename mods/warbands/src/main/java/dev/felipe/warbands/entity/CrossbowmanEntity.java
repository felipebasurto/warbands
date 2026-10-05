package dev.felipe.warbands.entity;

import dev.felipe.warbands.entity.ai.DefendPostGoal;
import dev.felipe.warbands.entity.ai.SoldierCrossbowAttackGoal;
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

/**
 * Feudal ranged unit. Farther and harder-hitting than an archer, but it reloads slowly and has fewer hearts than
 * before: archers keep the damage over time, crossbowmen win at range and against single strong targets. The crossbow is the vanilla item,
 * so breaking the building or the soldier never yields a new weapon, and bolts cannot be picked up.
 */
public class CrossbowmanEntity extends SoldierEntity implements RangedAttackMob {
	public CrossbowmanEntity(EntityType<? extends CrossbowmanEntity> type, Level level) {
		super(type, level);
	}

	/** Fewer hearts than before (20): a slow, hard-hitting shot is its edge, not everything else too. */
	private static final double MAX_HEALTH = 16.0;
	/** A shot every 2.5 s (archers: 2 s in Village, faster each age): heavy bolts, slow reload. */
	private static final int SHOT_INTERVAL = 50;
	private static final double ARMOR = 4.0;
	private static final float BOLT_SPEED = 1.9F;

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
	public boolean canGarrison() {
		return true;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(2, new SoldierCrossbowAttackGoal(this, 1.0, SHOT_INTERVAL));
		this.targetSelector.addGoal(3, new DefendPostGoal(this));
	}

	@Override
	protected void equipLoadout() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
	}

	@Override
	public float attackRadius() {
		return 20.0F + (Gear.has(this, Upgrade.FLETCHING) ? Gear.FLETCHING_RANGE : 0.0F);
	}

	public float shotInaccuracy() {
		return Gear.has(this, Upgrade.FLETCHING) ? 0.75F : 1.5F;
	}

	/**
	 * Vanilla multiplies an arrow's base damage by its speed on hit, and bolts leave at {@link #BOLT_SPEED}.
	 * 3.0 lands as about 6 damage (7 with Forging); the old 5.0 hit for about 10.
	 */
	private double boltBaseDamage() {
		return (Gear.has(this, Upgrade.FORGING) ? 3.5 : 3.0) + (Gear.has(this, Upgrade.BODKIN_ARROWS) ? Gear.BODKIN_DAMAGE : 0.0);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		ItemStack crossbow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.CROSSBOW));
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow bolt = ProjectileUtil.getMobArrow(this, ammo, power, crossbow);
		bolt.setBaseDamage(boltBaseDamage());
		bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
		bolt.addTag(Gear.ARROW_TAG);
		double dx = target.getX() - this.getX();
		double dy = target.getY(1.0 / 3.0) - bolt.getY();
		double dz = target.getZ() - this.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (this.level() instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileUsingShoot(bolt, serverLevel, ammo, dx, dy + horizontal * 0.2, dz, BOLT_SPEED, shotInaccuracy());
		}
		this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
	}
}
