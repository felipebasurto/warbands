package dev.felipe.warbands.entity;

import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.camp.Traps;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The named head of a Castle camp. Mini-boss in the other Castle kinds (~3x a knight's health, one move: the charge);
 * the Keep's is a real boss with three health-gated phases and three moves (war cry, charge, ground slam). His boss
 * bar follows players near the camp, the Camp Standard counts him as a guard (no claim until he falls), and he drops
 * his named weapon once. Camp traps never target him ({@link Traps#campOwn}).
 */
public class WarlordEntity extends SoldierEntity {
	/** ~3x a knight's 28 health: the mini-bosses. */
	private static final double MINI_HEALTH = 84.0;
	private static final double MINI_ARMOR = 9.0;
	private static final double ATTACK = 5.0;
	/** The Keep's warlord is the real fight. */
	private static final double BOSS_HEALTH = 150.0;
	private static final double BOSS_ARMOR = 14.0;
	private static final double BOSS_ATTACK = 7.0;
	private static final double BAR_RANGE = 64.0;
	private static final double CRY_RANGE = 24.0;
	private static final int CRY_TICKS = 16 * 20;
	private static final int CRY_TICKS_ENRAGED = 10 * 20;
	private static final int CHARGE_TICKS = 8 * 20;
	private static final int SLAM_TICKS = 15 * 20;
	private static final double SLAM_RADIUS = 4.5;
	private static final float SLAM_DAMAGE = 10.0F;

	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(),
			Component.translatable("entity.warbands.warlord"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
	private boolean boss;
	private int phase = 1;
	private int cryCooldown = CRY_TICKS;
	private int chargeCooldown = CHARGE_TICKS;
	private int slamCooldown = SLAM_TICKS;
	private boolean slamming;
	/** Ticks since the slam's take-off: he must leave the ground before the landing counts. */
	private int slamAirTicks;

	public WarlordEntity(EntityType<? extends WarlordEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createSoldierAttributes(MINI_HEALTH, MINI_ARMOR, ATTACK);
	}

	/** Castle camps call this right after {@code makeRaider}: the Keep's warlord becomes the true boss. */
	public void setBoss(boolean boss) {
		this.boss = boss;
		if (boss) {
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BOSS_HEALTH);
			this.getAttribute(Attributes.ARMOR).setBaseValue(BOSS_ARMOR);
			this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BOSS_ATTACK);
			this.setHealth(this.getMaxHealth());
		}
		this.bar.setName(this.getName());
	}

	public boolean isBoss() {
		return this.boss;
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bar.setName(name != null ? name : Component.translatable("entity.warbands.warlord"));
		if (name != null) {
			this.setCustomNameVisible(true);
		}
	}

	@Override
	protected double baseMaxHealth() {
		return this.boss ? BOSS_HEALTH : MINI_HEALTH;
	}

	@Override
	protected double baseArmor() {
		return this.boss ? BOSS_ARMOR : MINI_ARMOR;
	}

	@Override
	protected void registerCombatGoals() {
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
	}

	@Override
	protected void equipLoadout() {
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return this.boss ? 50 : 20;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel server)) {
			return;
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		for (ServerPlayer player : server.players()) {
			if (player.distanceToSqr(this) < BAR_RANGE * BAR_RANGE) {
				this.bar.addPlayer(player);
			} else {
				this.bar.removePlayer(player);
			}
		}
		if (this.getTarget() == null || !this.getTarget().isAlive()) {
			return;
		}
		int newPhase = this.getHealth() / this.getMaxHealth() > 0.66F ? 1 : this.getHealth() / this.getMaxHealth() > 0.33F ? 2 : 3;
		if (this.boss && newPhase != this.phase) {
			this.phase = newPhase;
			warCry(server); // a phase turns on a roar: the camp answers
			if (this.phase == 2) {
				this.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, false));
			}
		}
		if (--this.cryCooldown <= 0 && this.boss) {
			warCry(server);
			this.cryCooldown = this.phase >= 2 ? CRY_TICKS_ENRAGED : CRY_TICKS;
		}
		if (--this.chargeCooldown <= 0) {
			charge(server);
		}
		if (this.boss && this.phase == 3 && --this.slamCooldown <= 0) {
			startSlam(server);
		}
		if (this.slamming && ++this.slamAirTicks > 5 && this.onGround()) {
			// Not on the take-off tick (still on the ground then): the leap is the player's warning to back off.
			landSlam(server);
		}
	}

	/** The war cry: nearby defenders gain strength and speed for a few seconds. */
	private void warCry(ServerLevel level) {
		for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(CRY_RANGE),
				e -> e instanceof Mob mob && Traps.campOwn(mob) && mob.isAlive())) {
			Mob ally = (Mob) entity;
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 8 * 20, 0));
			ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 8 * 20, 0));
		}
		level.playSound(null, this.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 0.7F);
	}

	/** The charge: a leap at a target a few blocks out. */
	private void charge(ServerLevel level) {
		LivingEntity target = this.getTarget();
		if (target == null) {
			return;
		}
		double distance = this.distanceTo(target);
		if (distance < 5.0 || distance > 16.0) {
			return;
		}
		Vec3 toward = target.position().subtract(this.position()).normalize();
		this.setDeltaMovement(toward.x * 0.85, 0.38, toward.z * 0.85);
		this.hurtMarked = true;
		this.chargeCooldown = CHARGE_TICKS;
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
	}

	/** The slam's take-off: the landing is handled in {@link #landSlam} once he's down. */
	private void startSlam(ServerLevel level) {
		LivingEntity target = this.getTarget();
		if (target == null || this.distanceTo(target) > 7.0 || !this.onGround()) {
			return;
		}
		this.slamming = true;
		this.slamAirTicks = 0;
		this.slamCooldown = SLAM_TICKS;
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.8F);
		this.setDeltaMovement(0.0, 0.6, 0.0);
		this.hurtMarked = true;
	}

	private void landSlam(ServerLevel level) {
		this.slamming = false;
		level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.6F);
		for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(SLAM_RADIUS),
				e -> e instanceof LivingEntity living && living.isAlive() && !SoldierEntity.isRaiderSide(living))) {
			LivingEntity victim = (LivingEntity) entity;
			victim.hurtServer(level, level.damageSources().mobAttack(this), SLAM_DAMAGE);
			Vec3 away = victim.position().subtract(this.position()).normalize().scale(1.1);
			victim.setDeltaMovement(victim.getDeltaMovement().add(away.x, 0.4, away.z));
			victim.hurtMarked = true;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.8, 0.1, 0.8, 0.02);
	}

	/** His named blade, once: a netherite sword wearing the Warlord's Brand model. */
	public ItemStack trophyWeapon(ServerLevel level) {
		ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("warbands", "warlord_blade"));
		stack.set(DataComponents.CUSTOM_NAME,
				Component.translatable("item.warbands.warlord_blade").withStyle(style -> style.withItalic(false).withColor(0xE8B84B)));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5);
		stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
		stack.enchant(enchantments.getOrThrow(Enchantments.FIRE_ASPECT), 2);
		return stack;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		String kind = this.campKind();
		this.spawnAtLocation(level, WarlordSpoils.signature(level, kind, trophyWeapon(level)));
		if (this.boss) {
			this.spawnAtLocation(level, WarlordSpoils.platePiece(level, this.getRandom()));
		}
		WarlordSpoils.bonus(this, level, this.boss);
		if (source.getEntity() instanceof ServerPlayer player) {
			WarbandsAdvancements.award(player, WarbandsAdvancements.WARLORD_SLAIN);
			String own = WarlordSpoils.advancement(kind);
			if (own != null) {
				WarbandsAdvancements.award(player, own);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// He's armored head to toe: arrows sting but never bring him down fast.
		if (source.is(DamageTypeTags.IS_PROJECTILE) && this.boss) {
			amount *= 0.6F;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		this.bar.removeAllPlayers();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Boss", this.boss);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		// Only the flag: the boss attributes and his current health were saved with him, and setBoss would heal him
		// to full every time his chunk reloads or the player relogs mid-fight.
		this.boss = input.getBooleanOr("Boss", false);
		this.bar.setName(this.getName());
	}
}
