package dev.felipe.warbands.entity;

import dev.felipe.warbands.raid.RaidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The knight's mount. It is not its own soldier: damage lands on the rider, it drops nothing,
 * and it is discarded once the knight is gone.
 */
public class WarhorseEntity extends Horse {
	/** Starts after its commander past this distance and stops inside the second: the gap keeps it off their heels. */
	private static final double FOLLOW_START = 8.0;
	private static final double FOLLOW_STOP = 5.0;
	/** After the rider hit a ceiling, the horse waits this long before following again (the player is somewhere low). */
	private static final int LOW_CEILING_WAIT = 100;
	private int waitTicks;
	public WarhorseEntity(EntityType<? extends WarhorseEntity> type, Level level) {
		super(type, level);
	}

	/** Called after {@link #finalizeSpawn} so the random horse stats do not stick. */
	public void prepareFor(KnightEntity knight) {
		this.setTamed(true);
		this.setPersistenceRequired();
		LivingEntity owner = knight.getOwner();
		if (owner != null) {
			this.setOwner(owner);
		}
		this.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR));
		this.setDropChance(EquipmentSlot.BODY, 0.0F);
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(0.34);
			// A Cavalry raid's mounts are bred faster (the rider's boost is on the rider).
			if (knight.raidKind() == RaidKind.CAVALRY) {
				speed.addOrReplacePermanentModifier(
						new AttributeModifier(RaidKind.CAVALRY_HORSE_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}
		if (!(this.getFirstPassenger() instanceof KnightEntity knight) || !knight.isAlive()) {
			if (this.tickCount > 10) {
				this.discard();
			}
			return;
		}
		if (this.tickCount % 20 == 0) {
			Gear.applyHorse(knight, this);
		}
		if (this.waitTicks > 0) {
			this.waitTicks--;
		}
		if (this.tickCount % 5 == 0 && (knight.isInWall() || this.isInWall())) {
			unstick(knight);
		}
		LivingEntity target = knight.getTarget();
		if (target != null && target.isAlive()) {
			this.getNavigation().moveTo(target, 1.35);
			return;
		}
		// A raider knight's horse carries it along the raid's march.
		if (knight.isRaider()) {
			BlockPos march = knight.marchTarget();
			if (march != null && this.blockPosition().distSqr(march) > 9.0 && this.getNavigation().isDone()) {
				this.getNavigation().moveTo(march.getX() + 0.5, march.getY(), march.getZ() + 0.5, 1.0);
			} else if (march == null && knight.stance() == Stance.HOLD) {
				this.getNavigation().stop();
			}
			return;
		}
		// Follow and Charge both return to the commander once there's nothing to fight; Hold stays put.
		if (knight.stance() != Stance.HOLD) {
			LivingEntity owner = knight.getOwner();
			if (owner == null) {
				return;
			}
			double distSqr = this.distanceToSqr(owner);
			if (this.waitTicks > 0 || knight.isMustering()) {
				this.getNavigation().stop();
			} else if (distSqr > 24.0 * 24.0) {
				// Only where horse and rider fit: in a 2-high tunnel the knight's head would end up in the ceiling.
				Vec3 spot = fitNear(knight, behind(owner), owner.position());
				if (spot != null) {
					this.teleportTo(spot.x, spot.y, spot.z);
				}
			} else if (distSqr > FOLLOW_START * FOLLOW_START) {
				// Aim beside and behind the commander, never at them: a fast horse on their block shoves them around.
				Vec3 behind = behind(owner);
				this.getNavigation().moveTo(behind.x, owner.getY(), behind.z, 1.2);
			} else if (distSqr < FOLLOW_STOP * FOLLOW_STOP) {
				this.getNavigation().stop();
			}
			return;
		}
		this.getNavigation().stop();
	}

	/**
	 * The first spot near {@code preferred}, then in rings around {@code around}, where the horse with its rider fits
	 * (no blocks in the way up to the rider's head) on solid ground. Null if there is none.
	 */
	private @Nullable Vec3 fitNear(KnightEntity knight, Vec3 preferred, Vec3 around) {
		double height = Math.max(this.getBbHeight(), knight.getBoundingBox().maxY - this.getY()) + 0.1;
		double half = this.getBbWidth() / 2.0;
		java.util.List<Vec3> candidates = new java.util.ArrayList<>();
		candidates.add(preferred);
		for (int radius = 2; radius <= 4; radius++) {
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI / 4 * i;
				candidates.add(around.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
			}
		}
		for (Vec3 candidate : candidates) {
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos feet = BlockPos.containing(candidate.x, candidate.y + dy, candidate.z);
				Vec3 spot = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				BlockPos below = feet.below();
				if (this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
					continue;
				}
				AABB box = new AABB(spot.x - half, spot.y, spot.z - half, spot.x + half, spot.y + height, spot.z + half);
				if (this.level().noCollision(this, box)) {
					return spot;
				}
			}
		}
		return null;
	}

	/**
	 * The rider (or the horse) is inside a block, usually because the horse walked under a low ceiling after its
	 * commander. Back out to the nearest spot where both fit, and wait a while before following again.
	 */
	private void unstick(KnightEntity knight) {
		this.getNavigation().stop();
		this.waitTicks = LOW_CEILING_WAIT;
		Vec3 spot = fitNear(knight, this.position(), this.position());
		if (spot != null) {
			this.teleportTo(spot.x, spot.y, spot.z);
		}
	}

	/** A spot four blocks behind the commander and a little to the side. */
	private Vec3 behind(LivingEntity owner) {
		Vec3 look = Vec3.directionFromRotation(0.0F, owner.getYRot());
		return owner.position().subtract(look.scale(4.0)).add(look.z * 1.5, 0.0, -look.x * 1.5);
	}

	/** Never shove the commander: step aside instead (see {@link SoldierEntity#push(Entity)}). */
	@Override
	public void push(Entity entity) {
		if (this.getFirstPassenger() instanceof KnightEntity knight && entity == knight.getOwner()) {
			SoldierEntity.stepAside(this, entity);
			return;
		}
		super.push(entity);
	}

	@Override
	protected void doPush(Entity entity) {
		if (this.getFirstPassenger() instanceof KnightEntity knight && entity == knight.getOwner()) {
			SoldierEntity.stepAside(this, entity);
			return;
		}
		super.doPush(entity);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.getFirstPassenger() instanceof KnightEntity knight && knight.isAlive()) {
			if (SoldierEntity.isFriendlyFire(knight, source.getEntity())) {
				return false;
			}
			return knight.hurtServer(level, source, amount);
		}
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	protected void dropEquipment(ServerLevel level) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
