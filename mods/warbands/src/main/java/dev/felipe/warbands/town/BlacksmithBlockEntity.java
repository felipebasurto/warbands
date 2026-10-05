package dev.felipe.warbands.town;

import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Researches upgrades: three from the Feudal Age, four more from the Castle Age. The cost comes out of the nearest owned
 * Town Center when research starts; it then takes 40-60 s here, one at a time. The result is stored on the player, so
 * breaking this building does not undo it. Research in progress is lost with the building. One Blacksmith per town
 * (see {@code BlueprintPlacer}); if two towns' smiths finish the same upgrade, the second one is refunded.
 */
public class BlacksmithBlockEntity extends BlockEntity implements MenuProvider {
	public static final int LINK_OK = 0;
	public static final int LINK_NO_TOWN = 1;
	public static final int LINK_VILLAGE = 2;
	public static final int LINK_WRECKED = 3;

	public static final int DATA_STOCK = 0;
	public static final int DATA_LINK = 4;
	public static final int DATA_OWNED = 5;
	/** The upgrade being researched, ordinal + 1 (0 = idle). */
	public static final int DATA_RESEARCH = 6;
	public static final int DATA_PROGRESS = 7;
	public static final int DATA_TOTAL = 8;
	/** The linked Town Center's age ordinal. */
	public static final int DATA_AGE = 9;
	public static final int DATA_COUNT = 10;

	private @Nullable UUID owner;
	private String ownerName = "";
	private final int[] stock = new int[Resource.values().length];
	private int link = LINK_NO_TOWN;
	private int owned;
	private int townAge;
	private @Nullable Upgrade researching;
	private int progress;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < DATA_LINK) {
				return stock[index - DATA_STOCK];
			}
			return switch (index) {
				case DATA_LINK -> link;
				case DATA_OWNED -> owned;
				case DATA_RESEARCH -> researching == null ? 0 : researching.ordinal() + 1;
				case DATA_PROGRESS -> progress;
				case DATA_TOTAL -> researching == null ? 0 : researching.researchTicks();
				case DATA_AGE -> townAge;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	/** More than half its blocks are gone: research waits for a repair. Refreshed once a second. */
	private boolean wrecked;

	public BlacksmithBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BLACKSMITH, pos, state);
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public boolean isOwner(Player player) {
		return player.getUUID().equals(owner);
	}

	public void setOwner(Player player) {
		this.owner = player.getUUID();
		this.ownerName = player.getName().getString();
		setChanged();
	}

	/** Advances research every tick; feeds the open menu once a second. */
	public void serverTick(ServerLevel level) {
		if (researching != null && !wrecked && ++progress >= researching.researchTicks()) {
			finish(level);
		}
		if (level.getGameTime() % 20 == 0) {
			wrecked = BuildingRegistry.isWrecked(level, worldPosition);
			syncFrom(level, linkedTown(level));
			if (researching != null) {
				setChanged();
			}
		}
	}

	private void finish(ServerLevel level) {
		Upgrade done = researching;
		researching = null;
		progress = 0;
		setChanged();
		if (done == null || owner == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (UpgradeRegistry.has(level.getServer(), owner, done)) {
			// Another town's Blacksmith got there first: pay this one back.
			TownCenterBlockEntity town = linkedTown(level);
			if (town != null) {
				for (Resource resource : Resource.values()) {
					town.addStock(resource, done.cost(resource));
				}
			}
			return;
		}
		UpgradeRegistry.grant(level.getServer(), owner, done);
		if (player != null) {
			WarbandsAdvancements.award(player, done.advancement());
			player.sendSystemMessage(Component.translatable("message.warbands.upgrade_done", done.displayName(), done.effect())
					.withStyle(ChatFormatting.GREEN));
		}
		level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.1F);
		syncFrom(level, linkedTown(level));
	}

	private @Nullable TownCenterBlockEntity linkedTown(ServerLevel level) {
		return owner == null ? null : TownRegistry.nearestOwned(level, worldPosition, owner, TownCenterBlockEntity.HOUSE_RADIUS);
	}

	private void syncFrom(ServerLevel level, @Nullable TownCenterBlockEntity town) {
		owned = owner == null ? 0 : UpgradeRegistry.mask(level.getServer(), owner);
		if (owner == null || town == null) {
			Arrays.fill(stock, 0);
			link = LINK_NO_TOWN;
			townAge = 0;
			return;
		}
		townAge = town.age().ordinal();
		for (Resource resource : Resource.values()) {
			stock[resource.ordinal()] = town.stock(resource);
		}
		link = wrecked ? LINK_WRECKED : town.age().atLeast(Age.FEUDAL) ? LINK_OK : LINK_VILLAGE;
	}

	public boolean research(Player player, Upgrade upgrade) {
		if (!isOwner(player) || !(this.level instanceof ServerLevel level) || upgrade == null) {
			return false;
		}
		if (UpgradeRegistry.has(level.getServer(), player.getUUID(), upgrade)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.upgrade_owned"));
			return false;
		}
		if (BuildingRegistry.isWrecked(level, this.worldPosition)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.building_wrecked"));
			return false;
		}
		TownCenterBlockEntity town = linkedTown(level);
		syncFrom(level, town);
		if (link == LINK_NO_TOWN || town == null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.blacksmith_needs_town"));
			return false;
		}
		if (link == LINK_VILLAGE) {
			player.sendOverlayMessage(Component.translatable("message.warbands.need_feudal"));
			return false;
		}
		if (!town.age().atLeast(upgrade.age())) {
			player.sendOverlayMessage(Component.translatable("message.warbands.upgrade_needs_castle"));
			return false;
		}
		Upgrade first = upgrade.requires();
		if (first != null && !UpgradeRegistry.has(level.getServer(), player.getUUID(), first)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.upgrade_needs", first.displayName()));
			return false;
		}
		if (researching != null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.upgrade_busy", researching.displayName()));
			return false;
		}
		if (!town.canAfford(upgrade)) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.resources"));
			return false;
		}
		town.spend(upgrade);
		researching = upgrade;
		progress = 0;
		level.playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		syncFrom(level, town);
		setChanged();
		return true;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.warbands.blacksmith");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		if (this.level instanceof ServerLevel serverLevel) {
			syncFrom(serverLevel, linkedTown(serverLevel));
		}
		return new BlacksmithMenu(containerId, inventory, ContainerLevelAccess.create(this.level, this.worldPosition), data);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
		output.storeNullable("Researching", Upgrade.CODEC, researching);
		output.putInt("Progress", progress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
		researching = input.read("Researching", Upgrade.CODEC).orElse(null);
		progress = input.getIntOr("Progress", 0);
	}
}
