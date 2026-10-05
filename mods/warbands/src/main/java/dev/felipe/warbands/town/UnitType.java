package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Trainable units. Costs are indexed by {@link Resource#ordinal()}: food, wood, stone, gold. */
public enum UnitType implements StringRepresentable {
	MILITIA("militia", new int[] {60, 0, 0, 20}, 25 * 20, () -> ModEntities.MILITIA, () -> Items.STONE_SWORD, Age.VILLAGE, TrainingSite.TOWN_CENTER, null),
	ARCHER("archer", new int[] {0, 25, 0, 45}, 30 * 20, () -> ModEntities.ARCHER, () -> Items.BOW, Age.VILLAGE, TrainingSite.TOWN_CENTER, null),
	SPEARMAN("spearman", new int[] {40, 20, 0, 15}, 22 * 20, () -> ModEntities.SPEARMAN, () -> Items.IRON_SPEAR, Age.FEUDAL, TrainingSite.BARRACKS,
			"tooltip.warbands.spear"),
	CROSSBOWMAN("crossbowman", new int[] {0, 40, 0, 60}, 35 * 20, () -> ModEntities.CROSSBOWMAN, () -> Items.CROSSBOW, Age.FEUDAL,
			TrainingSite.ARCHERY_RANGE, "tooltip.warbands.crossbow"),
	KNIGHT("knight", new int[] {60, 0, 0, 80}, 40 * 20, () -> ModEntities.KNIGHT, () -> Items.IRON_SWORD, Age.CASTLE, TrainingSite.STABLE,
			"tooltip.warbands.knight"),
	RAM("ram", new int[] {0, 100, 0, 50}, 50 * 20, () -> ModEntities.RAM, () -> ModItems.BATTERING_RAM, Age.CASTLE, TrainingSite.SIEGE_WORKSHOP,
			"tooltip.warbands.ram");

	public static final Codec<UnitType> CODEC = StringRepresentable.fromEnum(UnitType::values);

	private final String name;
	private final int[] cost;
	private final int trainTicks;
	private final Supplier<EntityType<? extends SoldierEntity>> entityType;
	private final Supplier<Item> icon;
	private final Age requiredAge;
	private final TrainingSite site;
	private final @Nullable String tooltip;

	UnitType(String name, int[] cost, int trainTicks, Supplier<EntityType<? extends SoldierEntity>> entityType, Supplier<Item> icon,
			Age requiredAge, TrainingSite site, @Nullable String tooltip) {
		this.name = name;
		this.cost = cost;
		this.trainTicks = trainTicks;
		this.entityType = entityType;
		this.icon = icon;
		this.requiredAge = requiredAge;
		this.site = site;
		this.tooltip = tooltip;
	}

	public int cost(Resource resource) {
		return cost[resource.ordinal()];
	}

	public int trainTicks() {
		return trainTicks;
	}

	public EntityType<? extends SoldierEntity> entityType() {
		return entityType.get();
	}

	public Item icon() {
		return icon.get();
	}

	public Age requiredAge() {
		return requiredAge;
	}

	public TrainingSite site() {
		return site;
	}

	/** One line on what the unit is for (its role or counter), or null. */
	public @Nullable Component tooltip() {
		return tooltip == null ? null : Component.translatable(tooltip);
	}

	public static List<UnitType> trainedAt(TrainingSite site) {
		List<UnitType> units = new ArrayList<>();
		for (UnitType type : values()) {
			if (type.site == site) {
				units.add(type);
			}
		}
		return units;
	}

	public Component displayName() {
		return Component.translatable("entity.warbands." + name);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public static UnitType byId(int id) {
		UnitType[] values = values();
		return id >= 0 && id < values.length ? values[id] : null;
	}
}
