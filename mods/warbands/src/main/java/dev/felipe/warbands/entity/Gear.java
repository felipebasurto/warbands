package dev.felipe.warbands.entity;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.Upgrade;
import dev.felipe.warbands.town.UpgradeRegistry;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Blacksmith results. Damage and armor are modifiers, so they stack on the unit's own
 * stats and never replace them. Fletching is read when a ranged unit shoots.
 */
public final class Gear {
	public static final Identifier FORGING = Warbands.id("forging");
	public static final Identifier SCALE_ARMOR = Warbands.id("scale_armor");
	public static final Identifier BLAST_FORGING = Warbands.id("blast_forging");
	public static final Identifier CHAIN_MAIL = Warbands.id("chain_mail");
	public static final Identifier HUSBANDRY = Warbands.id("husbandry");
	/** Bodkin Arrows: extra base damage on arrows and bolts (vanilla scales it by speed: about +1 per hit). */
	public static final double BODKIN_DAMAGE = 0.5;
	public static final double HUSBANDRY_HEALTH = 4.0;
	/** Husbandry: warhorses run this much faster (multiplied). */
	public static final double HUSBANDRY_SPEED = 0.15;
	public static final double FORGING_DAMAGE = 1.0;
	public static final double SCALE_ARMOR_AMOUNT = 2.0;
	public static final float FLETCHING_RANGE = 5.0F;
	/** Entity tag on arrows and bolts shot by soldiers: they vanish {@link #ARROW_LIFE_TICKS} after sticking. */
	public static final String ARROW_TAG = "warbands_arrow";
	public static final int ARROW_LIFE_TICKS = 3 * 20;

	private Gear() {
	}

	public static boolean has(SoldierEntity soldier, Upgrade upgrade) {
		UUID owner = soldier.ownerId();
		if (owner == null || !(soldier.level() instanceof ServerLevel level)) {
			return false;
		}
		return UpgradeRegistry.has(level.getServer(), owner, upgrade);
	}

	/** Idempotent. The registry is the source of truth, so loading a soldier cannot stack the bonus. */
	public static void apply(SoldierEntity soldier) {
		if (!(soldier.level() instanceof ServerLevel level)) {
			return;
		}
		UUID owner = soldier.ownerId();
		set(soldier.getAttribute(Attributes.ATTACK_DAMAGE), FORGING, owned(level, owner, Upgrade.FORGING), FORGING_DAMAGE);
		set(soldier.getAttribute(Attributes.ATTACK_DAMAGE), BLAST_FORGING, owned(level, owner, Upgrade.BLAST_FORGING), FORGING_DAMAGE);
		set(soldier.getAttribute(Attributes.ARMOR), SCALE_ARMOR, owned(level, owner, Upgrade.SCALE_ARMOR), SCALE_ARMOR_AMOUNT);
		set(soldier.getAttribute(Attributes.ARMOR), CHAIN_MAIL, owned(level, owner, Upgrade.CHAIN_MAIL), SCALE_ARMOR_AMOUNT);
		if (soldier instanceof KnightEntity) {
			set(soldier.getAttribute(Attributes.MAX_HEALTH), HUSBANDRY, owned(level, owner, Upgrade.HUSBANDRY), HUSBANDRY_HEALTH);
		}
	}

	private static boolean owned(ServerLevel level, @Nullable UUID owner, Upgrade upgrade) {
		return owner != null && UpgradeRegistry.has(level.getServer(), owner, upgrade);
	}

	/** Husbandry's speed bonus on a knight's horse; idempotent like {@link #apply}. */
	public static void applyHorse(SoldierEntity knight, LivingEntity horse) {
		set(horse.getAttribute(Attributes.MOVEMENT_SPEED), HUSBANDRY, has(knight, Upgrade.HUSBANDRY), HUSBANDRY_SPEED,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void set(@Nullable AttributeInstance attribute, Identifier id, boolean present, double amount) {
		set(attribute, id, present, amount, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void set(@Nullable AttributeInstance attribute, Identifier id, boolean present, double amount,
			AttributeModifier.Operation operation) {
		if (attribute == null) {
			return;
		}
		if (present) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		} else {
			attribute.removeModifier(id);
		}
	}
}
