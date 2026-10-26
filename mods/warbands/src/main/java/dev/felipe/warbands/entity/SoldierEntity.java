package dev.felipe.warbands.entity;

import dev.felipe.warbands.entity.ai.ChargeHostilesGoal;
import dev.felipe.warbands.entity.ai.FightRaidersGoal;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.entity.ai.MarchGoal;
import dev.felipe.warbands.entity.ai.RaiderTargetGoal;
import dev.felipe.warbands.entity.ai.FollowWhenOrderedGoal;
import dev.felipe.warbands.entity.ai.OrderedStrollGoal;
import dev.felipe.warbands.raid.RaidDeparture;
import dev.felipe.warbands.raid.RaidKind;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.raid.Wrecking;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.AgeRegistry;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.ArmyRegistry;
import dev.felipe.warbands.town.WatchTowerBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Base for all trainable units. Built like a tamed wolf: vanilla goals give us
 * follow-owner (with teleport when far away), defend-owner and attack-what-owner-attacks.
 * A War Horn can instead make them hold a spot or charge nearby monsters.
 * Their owner's age (the highest any of their Town Centers reached) adds health, armor and better
 * weapons; soldiers check it every second, so ones far away catch up when their chunk loads.
 * <p>
 * The same classes also make the enemy: a <b>raider</b> is an untamed soldier of the raider faction, with a dark
 * skin, a fixed age, its own target goals and an optional march target (a raid's Town Center). Raiders can't be
 * claimed, drop nothing and give no XP.
 */
public abstract class SoldierEntity extends TamableAnimal {
	private static final int REGEN_DELAY_TICKS = 10 * 20;
	private static final int REGEN_INTERVAL_TICKS = 3 * 20;
	private static final double RALLY_RADIUS = 16.0;
	private static final Identifier ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath("warbands", "attack_order_speed");
	/** A mustering recruit joins its commander once they come this close. */
	private static final double MUSTER_JOIN_DISTANCE = 16.0;
	/** A mustering recruit mills about this close to the building's door. */
	private static final int MUSTER_RADIUS = 5;
	/** Vanilla pets teleport at 12 blocks; soldiers walk further before catching up. */
	private static final double TELEPORT_DISTANCE = 24.0;
	/** Hold keeps them inside this radius. Target goals refuse anything outside it. */
	private static final int HOLD_RADIUS = 12;
	/**
	 * A posted soldier's home covers its shooting range: target goals refuse anything outside the
	 * home, so a 1-block home would never shoot. The ranged goals keep it from walking; if it gets
	 * knocked off the platform, {@link #returnToPost} puts it back.
	 */
	public static final int POST_RANGE = 24;
	private static final EntityDataAccessor<Integer> DATA_STANCE = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
	/** Which camp kind's colors a camp guard wears ("" = the plain crimson raider kit). Synced so the renderer can pick the skin. */
	private static final EntityDataAccessor<String> DATA_CAMP_KIND = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_RAIDER = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BOOLEAN);
	private @Nullable BlockPos marchTarget;
	private @Nullable BlockPos raidTown;
	private boolean posted;
	/** Sent back to Follow by its tower: the army's standing order waits for the next horn call. */
	private boolean releasedFromPost;
	private @Nullable BlockPos postedTower;
	private @Nullable BlockPos postSpot;
	/** A fresh recruit waits here, outside the building that trained it, until its commander comes or blows the horn. */
	private @Nullable BlockPos musterSpot;

	private int lastCombatTick;
	private Age gearAge = Age.VILLAGE;
	/** Which raid kind raised this raider (spawn-time only; camp guards and old saves read as a plain warband). */
	private RaidKind raidKind = RaidKind.WARBAND;

	protected SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	/** Armor is a stat rather than worn pieces, so the unit's own outfit (its skin) stays visible. */
	protected static AttributeSupplier.Builder createSoldierAttributes(double maxHealth, double armor) {
		return createSoldierAttributes(maxHealth, armor, 1.0);
	}

	/** {@code attack} is the bare-handed damage; the held weapon's bonus is added on top. */
	protected static AttributeSupplier.Builder createSoldierAttributes(double maxHealth, double armor, double attack) {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, maxHealth)
				.add(Attributes.ARMOR, armor)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, attack)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** Adds the unit-specific attack goal at priority 2. Called from the constructor: don't use fields. */
	protected abstract void registerCombatGoals();

	/** Puts the unit's weapons and armor on. */
	protected abstract void equipLoadout();

	/** Max health and armor before age bonuses, the same values passed to {@link #createSoldierAttributes}. */
	protected abstract double baseMaxHealth();

	protected abstract double baseArmor();

	/** Swaps weapons for the owner's age. Most units keep their loadout. */
	protected void equipForAge(Age age) {
	}

	/** The age whose bonuses this soldier currently has. */
	public Age gearAge() {
		return gearAge;
	}

	private void refreshAge(ServerLevel level) {
		if (isRaider()) {
			return; // a raider's age is fixed when it is raised
		}
		UUID owner = ownerId();
		Age age = owner == null ? Age.VILLAGE : AgeRegistry.get(level.getServer(), owner);
		if (age != this.gearAge) {
			applyAge(age);
		}
	}

	private void applyAge(Age age) {
		float oldMax = this.getMaxHealth();
		setBase(Attributes.MAX_HEALTH, baseMaxHealth() + age.soldierHealthBonus());
		setBase(Attributes.ARMOR, baseArmor() + age.soldierArmorBonus());
		float gained = this.getMaxHealth() - oldMax;
		if (gained > 0) {
			this.heal(gained);
		} else if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
		equipForAge(age);
		this.gearAge = age;
	}

	private void setBase(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		registerCombatGoals();
		this.goalSelector.addGoal(3, new FollowWhenOrderedGoal(this));
		this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.1));
		this.goalSelector.addGoal(7, new OrderedStrollGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		// A raider only answers the side it's at war with: a skeleton's stray arrow doesn't pull it off the raid.
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return (!isRaider() || fightsBack(getLastHurtByMob())) && super.canUse();
			}
		});
		// Faction goals: each only runs on its own side (checked every time, the flag is set after construction).
		// Raiders come before monsters: on Attack a raider in range takes a soldier off a zombie.
		this.goalSelector.addGoal(4, new MarchGoal(this));
		this.targetSelector.addGoal(4, new FightRaidersGoal(this));
		this.targetSelector.addGoal(4, new RaiderTargetGoal(this));
		this.targetSelector.addGoal(5, new ChargeHostilesGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STANCE, Stance.FOLLOW.ordinal());
		builder.define(DATA_RAIDER, false);
		builder.define(DATA_CAMP_KIND, "");
	}

	public boolean isRaider() {
		return this.entityData.get(DATA_RAIDER);
	}

	public String campKind() {
		return this.entityData.get(DATA_CAMP_KIND);
	}

	/** Dresses a camp guard in its kind's colors (the kind's lowercase id, e.g. {@code fort}). */
	public void setCampKind(String kind) {
		this.entityData.set(DATA_CAMP_KIND, kind);
	}

	/**
	 * Turns a freshly spawned soldier into a raider of {@code age}. Call after {@link #finalizeSpawn}.
	 * {@code raidTown} is the Town Center of the raid it belongs to (null for camp guards); a raider whose raid
	 * is over disappears.
	 */
	public void makeRaider(Age age, @Nullable BlockPos raidTown) {
		this.entityData.set(DATA_RAIDER, true);
		this.raidTown = raidTown == null ? null : raidTown.immutable();
		this.setCustomName(Component.translatable("entity.warbands.raider", this.getType().getDescription()));
		applyAge(age);
	}

	public RaidKind raidKind() {
		return raidKind;
	}

	public void setRaidKind(RaidKind raidKind) {
		this.raidKind = raidKind;
	}

	/** Where a raider walks when it has nobody to fight: the raid's Town Center, or back out after a raid. */
	public @Nullable BlockPos marchTarget() {
		return marchTarget;
	}

	public void setMarchTarget(@Nullable BlockPos target) {
		this.marchTarget = target == null ? null : target.immutable();
	}

	public @Nullable BlockPos raidTown() {
		return raidTown;
	}

	public Stance stance() {
		int id = this.entityData.get(DATA_STANCE);
		Stance[] values = Stance.values();
		return id >= 0 && id < values.length ? values[id] : Stance.FOLLOW;
	}

	public boolean isPosted() {
		return posted;
	}

	public boolean isPostedTo(BlockPos tower) {
		return posted && tower.equals(postedTower);
	}

	/** The tower cell this soldier stands on, or null when not posted. */
	public @Nullable BlockPos postSpot() {
		return posted ? postSpot : null;
	}

	/**
	 * Knocked off the tower: climb back to the spot, or give up the post if the spot is blocked or the tower is gone.
	 * A camp tower guard has a spot but no Watch Tower.
	 */
	private void returnToPost(ServerLevel level) {
		if (!this.posted || this.postSpot == null) {
			return;
		}
		if (this.postedTower != null) {
			if (!level.isLoaded(this.postedTower)) {
				return;
			}
			if (!(level.getBlockEntity(this.postedTower) instanceof WatchTowerBlockEntity)) {
				command(Stance.FOLLOW);
				return;
			}
		}
		if (this.blockPosition().distSqr(this.postSpot) <= 2.0) {
			return;
		}
		if (level.getBlockState(this.postSpot).isAir() && level.getBlockState(this.postSpot.above()).isAir()) {
			this.teleportTo(this.postSpot.getX() + 0.5, this.postSpot.getY(), this.postSpot.getZ() + 0.5);
			this.getNavigation().stop();
		} else {
			command(Stance.FOLLOW);
		}
	}

	/** How far this soldier can hit from where it stands. Ranged units override it. */
	public float attackRadius() {
		return 3.0F;
	}

	/** Archers and crossbowmen can stand a Watch Tower. Other soldiers cannot. */
	public boolean canGarrison() {
		return false;
	}

	/**
	 * Puts this soldier on a tower spot. It stays there through War Horn orders until the tower recalls it.
	 */
	public void postAt(BlockPos pos, BlockPos tower) {
		this.posted = true;
		this.postedTower = tower.immutable();
		this.postSpot = pos.immutable();
		this.entityData.set(DATA_STANCE, Stance.HOLD.ordinal());
		this.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		this.setHomeTo(pos, POST_RANGE);
		this.setTarget(null);
		this.getNavigation().stop();
	}

	/**
	 * Stands guard at {@code spot}: Hold, with a home of {@code radius}. A tower guard is posted there instead, so it
	 * stands and shoots rather than walking toward targets it can't reach, and climbs back if knocked off.
	 */
	public void guard(BlockPos spot, int radius, boolean onTower) {
		command(Stance.HOLD);
		this.setHomeTo(spot, radius);
		if (onTower) {
			this.posted = true;
			this.postSpot = spot.immutable();
		}
	}

	/**
	 * Waits at {@code spot} (outside the building that trained it) instead of appearing next to its commander: it joins
	 * when they come within {@link #MUSTER_JOIN_DISTANCE} blocks or blow the War Horn.
	 */
	public void muster(BlockPos spot) {
		this.musterSpot = spot.immutable();
		if (this.stance() != Stance.HOLD) {
			this.setHomeTo(this.musterSpot, MUSTER_RADIUS);
		}
	}

	public boolean isMustering() {
		return this.musterSpot != null;
	}

	/** Leaves the muster point and takes up the army's order. */
	public void endMuster() {
		if (this.musterSpot == null) {
			return;
		}
		this.musterSpot = null;
		if (!this.posted && this.stance() != Stance.HOLD) {
			this.clearHome();
		}
	}

	private void checkMuster() {
		if (this.musterSpot != null && this.getOwner() instanceof Player owner && owner.level() == this.level()
				&& !owner.isSpectator() && this.distanceToSqr(owner) <= MUSTER_JOIN_DISTANCE * MUSTER_JOIN_DISTANCE) {
			endMuster();
		}
	}

	/**
	 * Takes up the army's current War Horn order if it has a different one: a recruit trained after the horn sounded,
	 * or one that was far away. Soldiers posted on a tower stay there until recalled.
	 */
	public void followArmyOrder(ServerLevel level) {
		UUID owner = ownerId();
		if (isRaider() || this.posted || this.releasedFromPost || owner == null) {
			return;
		}
		Stance order = ArmyOrders.get(level.getServer(), owner);
		if (order != this.stance()) {
			command(order);
		}
	}

	/** Whether a raider hits back at {@code attacker}: players and their soldiers, never mobs. Rams never fight. */
	protected boolean fightsBack(@Nullable LivingEntity attacker) {
		return attacker instanceof Player || attacker instanceof SoldierEntity other && !other.isRaider();
	}

	/** On Attack your soldiers move faster: they're hunting, not escorting. */
	private void applyOrderSpeed() {
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(ATTACK_SPEED_ID);
		if (this.stance() == Stance.CHARGE && !isRaider()) {
			speed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_ID, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** A tower lets go of this soldier: it follows its commander until the next horn order. */
	public void releaseFromPost() {
		command(Stance.FOLLOW);
		this.releasedFromPost = true;
	}

	/** Applies an order. Hold guards the block the soldier is standing on. A horn order leaves the tower. */
	public void command(Stance stance) {
		this.posted = false;
		this.releasedFromPost = false;
		this.postedTower = null;
		this.postSpot = null;
		this.entityData.set(DATA_STANCE, stance.ordinal());
		applyOrderSpeed();
		if (stance == Stance.HOLD) {
			this.setHomeTo(this.blockPosition(), HOLD_RADIUS);
		} else if (this.musterSpot != null) {
			this.setHomeTo(this.musterSpot, MUSTER_RADIUS);
		} else {
			this.clearHome();
		}
		this.setTarget(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Stance", this.stance().serializedName());
		output.putBoolean("Posted", posted);
		output.putBoolean("ReleasedFromPost", releasedFromPost);
		output.storeNullable("PostedTower", BlockPos.CODEC, postedTower);
		output.storeNullable("PostSpot", BlockPos.CODEC, postSpot);
		output.putString("GearAge", this.gearAge.serializedName());
		output.putBoolean("Raider", isRaider());
		output.putString("CampKind", campKind());
		output.storeNullable("MarchTarget", BlockPos.CODEC, marchTarget);
		output.storeNullable("RaidTown", BlockPos.CODEC, raidTown);
		output.storeNullable("MusterSpot", BlockPos.CODEC, musterSpot);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.gearAge = Age.byName(input.getStringOr("GearAge", Age.VILLAGE.serializedName()));
		this.entityData.set(DATA_RAIDER, input.getBooleanOr("Raider", false));
		this.entityData.set(DATA_CAMP_KIND, input.getStringOr("CampKind", ""));
		this.marchTarget = input.read("MarchTarget", BlockPos.CODEC).orElse(null);
		this.raidTown = input.read("RaidTown", BlockPos.CODEC).orElse(null);
		Stance stance = Stance.byName(input.getStringOr("Stance", Stance.FOLLOW.serializedName()));
		this.posted = input.getBooleanOr("Posted", false);
		this.releasedFromPost = input.getBooleanOr("ReleasedFromPost", false);
		this.postedTower = input.read("PostedTower", BlockPos.CODEC).orElse(null);
		this.postSpot = input.read("PostSpot", BlockPos.CODEC).orElse(null);
		if (this.posted && this.postedTower == null && !isRaider()) {
			this.posted = false;
		}
		this.entityData.set(DATA_STANCE, stance.ordinal());
		applyOrderSpeed();
		if (this.posted) {
			if (this.postSpot == null) {
				this.postSpot = this.blockPosition();
			}
			this.setHomeTo(this.postSpot, POST_RANGE);
		} else if (stance == Stance.HOLD && !this.hasHome()) {
			this.setHomeTo(this.blockPosition(), HOLD_RADIUS);
		}
		this.musterSpot = input.read("MusterSpot", BlockPos.CODEC).orElse(null);
		if (!this.posted && stance != Stance.HOLD) {
			if (this.musterSpot != null) {
				this.setHomeTo(this.musterSpot, MUSTER_RADIUS);
			} else {
				this.clearHome();
			}
		}
		Gear.apply(this);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setAge(0);
		equipLoadout();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
		return data;
	}

	/** Makes {@code owner} this soldier's commander and counts it toward their population. */
	public void assignOwner(UUID owner, String ownerName) {
		this.setTame(true, false);
		this.setOwnerReference(EntityReference.of(owner));
		this.setCustomName(Component.translatable("entity.warbands.owned_by", ownerName, this.getType().getDescription()));
		if (this.level() instanceof ServerLevel serverLevel) {
			ArmyRegistry.add(serverLevel.getServer(), owner, this.getUUID());
			Gear.apply(this);
			refreshAge(serverLevel);
		}
	}

	public @Nullable UUID ownerId() {
		EntityReference<LivingEntity> owner = this.getOwnerReference();
		return owner == null ? null : owner.getUUID();
	}

	@Override
	public boolean shouldTryTeleportToOwner() {
		if (this.stance() == Stance.HOLD || this.isMustering()) {
			return false;
		}
		LivingEntity owner = this.getOwner();
		return owner != null && this.distanceToSqr(owner) >= TELEPORT_DISTANCE * TELEPORT_DISTANCE;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isRaider()) {
			return InteractionResult.PASS; // the enemy can't be claimed
		}
		if (!this.isTame()) {
			// Soldiers from spawn eggs have no commander: the first player to right-click gets them.
			if (this.level() instanceof ServerLevel) {
				assignOwner(player.getUUID(), player.getName().getString());
			}
			return InteractionResult.SUCCESS;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null && player.getUUID().equals(ownerId()) && this.getHealth() < this.getMaxHealth()) {
			this.feed(player, hand, stack, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		return !isFriendly(owner.getUUID(), target)
				&& !(target instanceof Player targetPlayer && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(targetPlayer));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			this.lastCombatTick = this.tickCount;
			UUID owner = ownerId();
			if (source.getEntity() instanceof LivingEntity attacker && owner != null && !isFriendly(owner, attacker)) {
				rallyAllies(level, owner, attacker);
			}
		}
		return hurt;
	}

	/** Nearby idle soldiers of the same owner join the fight. Held soldiers don't leave their post for it. */
	private void rallyAllies(ServerLevel level, UUID owner, LivingEntity attacker) {
		for (SoldierEntity ally : level.getEntitiesOfClass(SoldierEntity.class, this.getBoundingBox().inflate(RALLY_RADIUS),
				s -> s != this && s.isAlive() && owner.equals(s.ownerId()) && s.getTarget() == null
						&& (s.stance() != Stance.HOLD || s.isWithinHome(attacker.blockPosition())))) {
			ally.setTarget(attacker);
		}
	}

	/**
	 * Soldiers never shove their commander: in a doorway or a narrow path they step aside instead. Vanilla pushes both
	 * ways ({@code Entity.push}), from either side's collision check, so both entry points are covered.
	 */
	@Override
	public void push(Entity entity) {
		if (!isRaider() && entity == this.getOwner()) {
			stepAside(this, entity);
			return;
		}
		super.push(entity);
	}

	@Override
	protected void doPush(Entity entity) {
		if (!isRaider() && entity == this.getOwner()) {
			stepAside(this, entity);
			return;
		}
		super.doPush(entity);
	}

	/** Moves {@code self} a little away from {@code from}, leaving {@code from} where it is. */
	public static void stepAside(Entity self, Entity from) {
		double dx = self.getX() - from.getX();
		double dz = self.getZ() - from.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.01) {
			dx = self.getRandom().nextDouble() - 0.5;
			dz = self.getRandom().nextDouble() - 0.5;
			length = Math.sqrt(dx * dx + dz * dz);
		}
		self.push(dx / length * 0.1, 0.0, dz / length * 0.1);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.lastCombatTick = this.tickCount;
		return super.doHurtTarget(level, target);
	}

	@Override
	public void aiStep() {
		// Vanilla only advances the swing in Monster and Player: without this a sword swing never plays.
		this.updateSwingTime();
		super.aiStep();
		if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 20 == 0) {
			refreshAge(serverLevel);
			followArmyOrder(serverLevel);
			checkMuster();
			returnToPost(serverLevel);
			// Marching on the town with nobody to fight: break a bit of it.
			if (this.raidTown != null && this.raidTown.equals(this.marchTarget) && this.getTarget() == null
					&& this.tickCount % Wrecking.INTERVAL == 0) {
				RaidRegistry.get(serverLevel, this.raidTown).ifPresent(raids -> Wrecking.wreck(serverLevel, this, this.raidTown, raids.owner()));
			}
			// A raider whose raid is over (won, lost, or its town gone) leaves the world, horse and all.
			if (this.raidTown != null && this.tickCount % 100 == 0 && !RaidRegistry.isActive(serverLevel, this.raidTown)) {
				Entity vehicle = this.getVehicle();
				this.discard();
				if (vehicle != null) {
					vehicle.discard();
				}
			}
		}
		if (!this.level().isClientSide()
				&& this.tickCount % REGEN_INTERVAL_TICKS == 0
				&& this.tickCount - this.lastCombatTick > REGEN_DELAY_TICKS
				&& this.getHealth() < this.getMaxHealth()) {
			this.heal(1.0F);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		UUID owner = ownerId();
		if (reason.shouldDestroy() && owner != null && this.level() instanceof ServerLevel serverLevel) {
			ArmyRegistry.remove(serverLevel.getServer(), owner, this.getUUID());
		}
		super.remove(reason);
	}

	/** True when {@code entity} is on the same side as a player with id {@code owner}. */
	public static boolean isFriendly(UUID owner, Entity entity) {
		if (owner.equals(entity.getUUID())) {
			return true;
		}
		if (entity instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> ref = ownable.getOwnerReference();
			return ref != null && owner.equals(ref.getUUID());
		}
		return false;
	}

	/** Blocks damage a soldier deals to its own side, and damage a player deals to their own soldiers. */
	public static boolean isFriendlyFire(LivingEntity victim, @Nullable Entity attacker) {
		if (attacker instanceof SoldierEntity soldier && soldier.isRaider()) {
			return (isRaiderSide(victim) || isCampGuard(victim)) && victim != soldier;
		}
		// A camp's vanilla guards (witches, strays, illagers) fight beside its raiders: a stray shot either way
		// mustn't start a fight inside the camp.
		if (isCampGuard(attacker)) {
			return victim != attacker && (isRaiderSide(victim) || isCampGuard(victim));
		}
		if (attacker instanceof SoldierEntity soldier) {
			UUID owner = soldier.ownerId();
			return owner != null && victim != soldier && isFriendly(owner, victim);
		}
		if (attacker instanceof Player player && victim instanceof SoldierEntity soldier) {
			return player.getUUID().equals(soldier.ownerId());
		}
		return false;
	}

	/** A vanilla mob standing guard in a camp (tagged when the camp is built). */
	private static boolean isCampGuard(@Nullable Entity entity) {
		return entity != null && entity.entityTags().contains(Camps.GUARD_TAG);
	}

	/** A raider, or a raider's warhorse. */
	public static boolean isRaiderSide(Entity entity) {
		if (entity instanceof SoldierEntity soldier) {
			return soldier.isRaider();
		}
		return entity.getFirstPassenger() instanceof SoldierEntity rider && rider.isRaider();
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return isRaider() ? 0 : super.getBaseExperienceReward(level);
	}

	/**
	 * A portal copies the raider into the other dimension, then {@code setRemoved(CHANGED_DIMENSION)} on this one.
	 * That never calls {@link #remove} or {@link #die}, so the raid would keep counting a raider who has left.
	 */
	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (RaidDeparture.dropsFromCount(reason) && this.raidTown != null && this.level() instanceof ServerLevel level) {
			Raids.raiderDied(level, this.raidTown, this);
		}
	}

	/** A raid counts its raiders by their deaths, not by who it can find: unloaded raiders aren't dead. */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.raidTown != null && this.level() instanceof ServerLevel level) {
			Raids.raiderDied(level, this.raidTown, this);
		}
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
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}
}
