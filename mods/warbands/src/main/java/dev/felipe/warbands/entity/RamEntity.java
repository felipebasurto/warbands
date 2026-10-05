package dev.felipe.warbands.entity;

import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.town.StructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Castle siege unit. It is slow and weak against soldiers. On Charge it breaks
 * doors, gates, wooden fences and wooden trapdoors, and those blocks drop nothing. A raider ram picks no fights and
 * isn't pulled off by hits: it marches straight at the Town Center, a little faster, smashing what's in its way.
 */
public class RamEntity extends SoldierEntity {
	private static final int SMASH_INTERVAL = 10;

	public RamEntity(EntityType<? extends RamEntity> type, Level level) {
		super(type, level);
		// The hitbox is sized in Entity's constructor, before attributes exist, so it misses SCALE.
		this.refreshDimensions();
	}

	private static final double MAX_HEALTH = 40.0;
	private static final double ARMOR = 5.0;
	/**
	 * 1.1x the model: reads as a siege engine next to a soldier (about 1.5 blocks wide and tall, 2.5 long) without
	 * dwarfing him. Playtesters found 1.68x (drawn) too big. The hitbox (1.54 wide) still paths through 2-wide gates.
	 */
	private static final double SCALE = 1.1;

	/** Built by hand rather than with the soldier helper: it is slower, heavier and climbs a block. */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.ARMOR, ARMOR)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	/** Rams saved at another size keep their old base scale in NBT; bring them to the current one. */
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		var scale = this.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(SCALE);
		}
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
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOD_HIT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.WOOD_BREAK;
	}

	/** A raider ram never turns to fight: it is the warband's siege engine and goes straight for the Town Center. */
	@Override
	protected boolean fightsBack(@Nullable LivingEntity attacker) {
		return false;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
	}

	@Override
	protected void equipLoadout() {
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && this.tickCount % SMASH_INTERVAL == 0) {
			boolean charging = this.stance() == Stance.CHARGE;
			// On Attack the path may run through wooden doors (it stops at one and breaks it), like a door-breaking zombie.
			this.getNavigation().setCanOpenDoors(charging);
			if (charging) {
				smash(serverLevel);
			}
		}
	}

	/**
	 * Breaks at most one whitelisted block: the one ahead of the ram, the next step of its path, or any it touches.
	 * Follow and Hold leave the town alone.
	 */
	private void smash(ServerLevel level) {
		Direction facing = this.getDirection();
		BlockPos ahead = this.blockPosition().relative(facing);
		if (trySmash(level, ahead) || trySmash(level, ahead.above())) {
			return;
		}
		Path path = this.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			BlockPos next = path.getNextNodePos();
			if (next.distSqr(this.blockPosition()) <= 4.0 && (trySmash(level, next) || trySmash(level, next.above()))) {
				return;
			}
		}
		AABB box = this.getBoundingBox().inflate(0.4, 0.0, 0.4);
		for (BlockPos pos : BlockPos.betweenClosed(
				Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
				Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			if (trySmash(level, pos)) {
				return;
			}
		}
	}

	/**
	 * A raider ram obeys {@code mobGriefing} like any mob; your own ram follows your order (the Keep's gate needs it).
	 * Building blocks drop nothing and are released for Repair; a player's own door or fence drops as an item.
	 */
	private boolean trySmash(ServerLevel level, BlockPos pos) {
		if (!breaks(level.getBlockState(pos))) {
			return false;
		}
		if (this.isRaider() && !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		boolean building = StructureBlocks.contains(level, pos);
		boolean destroyed = level.destroyBlock(pos, !building, this, Block.UPDATE_LIMIT);
		if (destroyed && building) {
			StructureBlocks.release(level, pos.immutable());
		}
		if (destroyed) {
			this.playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.0F, 0.8F + this.getRandom().nextFloat() * 0.2F);
		}
		return destroyed;
	}

	private static boolean breaks(BlockState state) {
		return state.is(ModBlocks.REINFORCED_GATE)
				|| state.is(ModBlocks.PORTCULLIS)
				|| state.is(BlockTags.DOORS)
				|| state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.WOODEN_FENCES)
				|| state.is(BlockTags.WOODEN_TRAPDOORS);
	}
}
