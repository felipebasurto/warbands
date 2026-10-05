package dev.felipe.warbands.camp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.Age;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * How hard a camp (or raid) hits: game difficulty, how many players came along, and how many camps of this age the
 * owner has already taken. Worked out once when a camp is built and saved with it, so reloading the world never
 * rerolls it. Health and damage go on as permanent attribute modifiers (they're saved with each guard); extra guards
 * come from the layout's spare spots, Easy removes a few. {@code stars} (1-5) is what the War Map shows.
 */
public record Threat(float health, float damage, int extraGuards, float bossHealth, int stars, boolean hard) {
	public static final Threat NORMAL = new Threat(1.0F, 1.0F, 0, 1.0F, 2, false);
	public static final Codec<Threat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("health").forGetter(Threat::health),
			Codec.FLOAT.fieldOf("damage").forGetter(Threat::damage),
			Codec.INT.fieldOf("extra_guards").forGetter(Threat::extraGuards),
			Codec.FLOAT.fieldOf("boss_health").forGetter(Threat::bossHealth),
			Codec.INT.fieldOf("stars").forGetter(Threat::stars),
			Codec.BOOL.fieldOf("hard").forGetter(Threat::hard)).apply(i, Threat::new));

	/** Players this close to the camp when it's built count as the attacking party. */
	public static final int PARTY_RANGE = 64;
	private static final Identifier HEALTH_ID = Warbands.id("camp_threat_health");
	private static final Identifier DAMAGE_ID = Warbands.id("camp_threat_damage");

	/**
	 * The threat for a camp of {@code age} whose garrison is {@code guards} strong, built for {@code owner} with
	 * {@code party} players around. Peaceful counts as Easy (soldiers still fight there).
	 */
	public static Threat of(ServerLevel level, UUID owner, Age age, int guards, int party) {
		Difficulty difficulty = level.getDifficulty();
		int extraPlayers = Math.max(0, party - 1);
		int streak = wins(level, owner, age);
		float health;
		float damage;
		int extra;
		int stars;
		switch (difficulty) {
			case PEACEFUL, EASY -> {
				health = 0.75F;
				damage = 0.8F;
				extra = -(guards / 4);
				stars = 1;
			}
			case HARD -> {
				health = 1.25F;
				damage = 1.15F;
				extra = (guards + 3) / 4;
				stars = 3;
			}
			default -> {
				health = 1.0F;
				damage = 1.0F;
				extra = 0;
				stars = 2;
			}
		}
		extra += Math.min(extraPlayers, 4) + Math.min(streak / 2, 3);
		stars += (extraPlayers > 0 ? 1 : 0) + (streak >= 2 ? 1 : 0);
		float boss = health * (1.0F + 0.35F * extraPlayers);
		return new Threat(health, damage, extra, boss, Math.min(stars, 5), difficulty == Difficulty.HARD);
	}

	/** Estimate for the War Map at purchase: the buyer's party now, around the town. */
	public static Threat estimate(ServerLevel level, UUID owner, Age age, int guards, BlockPos town) {
		return of(level, owner, age, guards, party(level, town));
	}

	/** Players (not spectators) within {@link #PARTY_RANGE} of {@code pos}, at least 1. */
	public static int party(ServerLevel level, BlockPos pos) {
		int count = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.blockPosition().distSqr(pos) <= (double) PARTY_RANGE * PARTY_RANGE) {
				count++;
			}
		}
		return Math.max(1, count);
	}

	/** Camps of this age the owner has taken (kept in the camp registry). */
	private static int wins(ServerLevel level, UUID owner, Age age) {
		return (int) CampRegistry.all(level).stream()
				.filter(camp -> camp.owner().equals(owner) && camp.conquered() && camp.tier().age() == age).count();
	}

	/** A raid's raiders: the difficulty's health and damage (Easy weaker, Hard tougher), no guard changes. */
	public static Threat raid(ServerLevel level) {
		return switch (level.getDifficulty()) {
			case PEACEFUL, EASY -> new Threat(0.75F, 0.8F, 0, 0.75F, 1, false);
			case HARD -> new Threat(1.25F, 1.15F, 0, 1.25F, 3, true);
			default -> NORMAL;
		};
	}

	/** Raids: Easy −2, Hard +2, and one more raider per extra player at home (up to 4). */
	public static int raidBonus(ServerLevel level, BlockPos town) {
		int bonus = switch (level.getDifficulty()) {
			case PEACEFUL, EASY -> -2;
			case HARD -> 2;
			default -> 0;
		};
		return bonus + Math.min(party(level, town) - 1, 4);
	}

	/** Puts the health and damage multipliers on a freshly spawned guard (the boss gets {@link #bossHealth}). */
	public void apply(Mob mob, boolean boss) {
		float healthScale = boss ? bossHealth : health;
		AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null && healthScale != 1.0F) {
			maxHealth.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, healthScale - 1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(mob.getMaxHealth());
		}
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null && damage != 1.0F) {
			attack.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE_ID, damage - 1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** "★★★☆☆". */
	public String starText() {
		return "★".repeat(stars) + "☆".repeat(5 - stars);
	}
}
