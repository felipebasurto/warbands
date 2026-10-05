package dev.felipe.warbands.raid;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.town.Age;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * The kind of warband a raid brings. Each kind has its own roster (see {@link RaidComposition}) and its own
 * spoils table ({@code chests/raid/<name>}), plus a flavour on top: the Shield Wall marches slow and tough, the
 * Cavalry's horses are quick, and the Night Stalkers only come once the night is fully dark.
 */
public enum RaidKind {
	WARBAND(Age.FEUDAL),
	NIGHT_STALKERS(Age.FEUDAL),
	SHIELD_WALL(Age.FEUDAL),
	SIEGE(Age.CASTLE),
	CAVALRY(Age.CASTLE);

	public static final Codec<RaidKind> CODEC = Codec.STRING.xmap(RaidKind::byName, RaidKind::serializedName);
	/** skyDarken is 11 (sky light bottomed at 4) only deep into the night; dusk, dawn and storms don't reach it. */
	private static final int FULL_DARK = 11;
	private static final Identifier SHIELD_WALL_ID = Warbands.id("raid_shield_wall");
	private static final Identifier CAVALRY_ID = Warbands.id("raid_cavalry");
	/** The Cavalry speed boost for the horses under raider knights (they carry the knights' speed for them). */
	public static final Identifier CAVALRY_HORSE_ID = Warbands.id("raid_cavalry_horse");

	private final Age minAge;

	RaidKind(Age minAge) {
		this.minAge = minAge;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Unknown names (a kind removed in a later version) fall back to the plain warband. */
	public static RaidKind byName(String name) {
		for (RaidKind kind : values()) {
			if (kind.serializedName().equals(name)) {
				return kind;
			}
		}
		return WARBAND;
	}

	/** The town's age must be at least this for the kind to enter its bag. */
	public Age minAge() {
		return minAge;
	}

	public Component displayName() {
		return Component.translatable("raid.warbands." + serializedName());
	}

	/** The kinds a town of {@code age} can draw. Never empty: below Feudal it's the plain Warband. */
	public static List<RaidKind> forAge(Age age) {
		List<RaidKind> kinds = new ArrayList<>();
		for (RaidKind kind : values()) {
			if (age.atLeast(kind.minAge)) {
				kinds.add(kind);
			}
		}
		if (kinds.isEmpty()) {
			kinds.add(WARBAND);
		}
		return kinds;
	}

	/**
	 * Whether a raid due at {@code nextRaid} can begin now. Ordinary kinds come after nightfall, the Night Stalkers
	 * only once the sky is fully dark; either way a day late they come regardless, so sleeping through every night
	 * can't hold raids off forever.
	 */
	public boolean canStart(ServerLevel level, long now, long nextRaid) {
		if (now < nextRaid) {
			return false;
		}
		if (now >= nextRaid + Raids.DAY) {
			return true;
		}
		return this == NIGHT_STALKERS ? level.getSkyDarken() >= FULL_DARK : level.isDarkOutside();
	}

	/** The spoils table a beaten raid of this kind leaves behind. */
	public ResourceKey<LootTable> lootTable() {
		return ResourceKey.create(Registries.LOOT_TABLE, Warbands.id("chests/raid/" + serializedName()));
	}

	/**
	 * The kind's flavour on a freshly raised raider (call after {@code makeRaider}): the Shield Wall trades speed
	 * for toughness and the Cavalry's riders are quick. The modifiers are permanent, so a relog keeps them.
	 */
	public void dress(SoldierEntity soldier) {
		soldier.setRaidKind(this);
		switch (this) {
			case SHIELD_WALL -> {
				dress(soldier, Attributes.MOVEMENT_SPEED, SHIELD_WALL_ID, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				dress(soldier, Attributes.ARMOR, SHIELD_WALL_ID, 3.0, AttributeModifier.Operation.ADD_VALUE);
				dress(soldier, Attributes.KNOCKBACK_RESISTANCE, SHIELD_WALL_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
			}
			case CAVALRY -> dress(soldier, Attributes.MOVEMENT_SPEED, CAVALRY_ID, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			default -> {
			}
		}
	}

	private static void dress(SoldierEntity soldier, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = soldier.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}
}
