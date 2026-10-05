package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlockEntities;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModMenus;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Where a unit is trained. The Town Center has its own menu; every other site is a
 * {@link TrainingBlock}: one class for all of them, configured here, so a new training building
 * is a new constant plus its registrations, textures and strings.
 */
public enum TrainingSite implements StringRepresentable {
	TOWN_CENTER("town_center", "town", Age.VILLAGE),
	BARRACKS("barracks", "barracks", Age.FEUDAL),
	ARCHERY_RANGE("archery_range", "archery", Age.FEUDAL),
	STABLE("stable", "stable", Age.CASTLE),
	SIEGE_WORKSHOP("siege_workshop", "siege", Age.CASTLE);

	public static final Codec<TrainingSite> CODEC = StringRepresentable.fromEnum(TrainingSite::values);

	private final String name;
	/** Short name used by the older message keys ({@code not_your_archery}, {@code archery_needs_town}). */
	private final String messageName;
	private final Age requiredAge;

	TrainingSite(String name, String messageName, Age requiredAge) {
		this.name = name;
		this.messageName = messageName;
		this.requiredAge = requiredAge;
	}

	/** The training buildings, everything except the Town Center. */
	public static List<TrainingSite> buildings() {
		return List.of(BARRACKS, ARCHERY_RANGE, STABLE, SIEGE_WORKSHOP);
	}

	public Age requiredAge() {
		return requiredAge;
	}

	public Component displayName() {
		return Component.translatable("container.warbands." + name);
	}

	public Component notYours(String owner) {
		return Component.translatable("message.warbands.not_your_" + messageName, owner);
	}

	/** "Must be built within 64 blocks of your Town Center", worded for this building. */
	public Component needsTown() {
		return Component.translatable("message.warbands." + messageName + "_needs_town");
	}

	public Identifier guiTexture() {
		return Warbands.id("textures/gui/" + name + ".png");
	}

	public Block block() {
		return switch (this) {
			case BARRACKS -> ModBlocks.BARRACKS;
			case ARCHERY_RANGE -> ModBlocks.ARCHERY_RANGE;
			case STABLE -> ModBlocks.STABLE;
			case SIEGE_WORKSHOP -> ModBlocks.SIEGE_WORKSHOP;
			case TOWN_CENTER -> ModBlocks.TOWN_CENTER;
		};
	}

	public BlockEntityType<TrainingBlockEntity> blockEntityType() {
		return switch (this) {
			case BARRACKS -> ModBlockEntities.BARRACKS;
			case ARCHERY_RANGE -> ModBlockEntities.ARCHERY_RANGE;
			case STABLE -> ModBlockEntities.STABLE;
			case SIEGE_WORKSHOP -> ModBlockEntities.SIEGE_WORKSHOP;
			case TOWN_CENTER -> throw new IllegalStateException("The Town Center is not a training building");
		};
	}

	public MenuType<TrainingMenu> menuType() {
		return switch (this) {
			case BARRACKS -> ModMenus.BARRACKS;
			case ARCHERY_RANGE -> ModMenus.ARCHERY_RANGE;
			case STABLE -> ModMenus.STABLE;
			case SIEGE_WORKSHOP -> ModMenus.SIEGE_WORKSHOP;
			case TOWN_CENTER -> throw new IllegalStateException("The Town Center is not a training building");
		};
	}

	/** Positions and owners of this kind of building, per dimension. */
	public AttachmentType<TrainingRegistry.Data> attachment() {
		return switch (this) {
			case BARRACKS -> ModAttachments.BARRACKS;
			case ARCHERY_RANGE -> ModAttachments.ARCHERY_RANGES;
			case STABLE -> ModAttachments.STABLES;
			case SIEGE_WORKSHOP -> ModAttachments.SIEGE_WORKSHOPS;
			case TOWN_CENTER -> throw new IllegalStateException("Town Centers use TownRegistry");
		};
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
