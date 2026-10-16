package dev.felipe.warbands.town;

import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModBlockEntities;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Posts archers and crossbowmen on the roof. They hold that spot and shoot monsters
 * that walk into range. A War Horn order takes them off the tower.
 */
public class WatchTowerBlockEntity extends BlockEntity implements MenuProvider {
	public static final int CAP = 4;
	public static final int CALL_RADIUS = 24;
	public static final int LINK_OK = 0;
	public static final int LINK_NO_TOWN = 1;
	public static final int LINK_NOT_CASTLE = 2;

	public static final int DATA_POSTED = 0;
	public static final int DATA_NEARBY = 1;
	public static final int DATA_LINK = 2;
	public static final int DATA_COUNT = 3;

	/** Roof spots relative to the core. The platform is 7 blocks above the core. */
	private static final BlockPos[] SPOTS = {
			new BlockPos(1, 7, 0),
			new BlockPos(-1, 7, 0),
			new BlockPos(0, 7, 1),
			new BlockPos(0, 7, -1)
	};

	private @Nullable UUID owner;
	private String ownerName = "";
	/** Menu numbers, recounted at most every 10 ticks: each count is an entity search. */
	private final int[] menuValues = new int[DATA_COUNT];
	private long menuValuesTime = -1000L;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < 0 || index >= DATA_COUNT) {
				return 0;
			}
			long now = level == null ? 0 : level.getGameTime();
			if (now - menuValuesTime >= 10 || now < menuValuesTime) {
				menuValues[DATA_POSTED] = posted().size();
				menuValues[DATA_NEARBY] = waiting().size();
				menuValues[DATA_LINK] = link();
				menuValuesTime = now;
			}
			return menuValues[index];
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public WatchTowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WATCH_TOWER, pos, state);
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

	public boolean post(Player player) {
		if (!isOwner(player) || !(this.level instanceof ServerLevel level)) {
			return false;
		}
		int link = link();
		if (link == LINK_NO_TOWN) {
			player.sendOverlayMessage(Component.translatable("message.warbands.tower_needs_town"));
			return false;
		}
		if (BuildingRegistry.isWrecked(level, this.worldPosition)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.building_wrecked"));
			return false;
		}
		if (link == LINK_NOT_CASTLE) {
			player.sendOverlayMessage(Component.translatable("message.warbands.need_castle"));
			return false;
		}
		if (posted().size() >= CAP) {
			player.sendOverlayMessage(Component.translatable("message.warbands.tower_full"));
			return false;
		}
		BlockPos spot = freeSpot(level);
		SoldierEntity soldier = waiting().stream()
				.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
				.orElse(null);
		if (spot == null || soldier == null) {
			player.sendOverlayMessage(Component.translatable(spot == null ? "message.warbands.tower_full" : "message.warbands.no_ranged"));
			return false;
		}
		soldier.postAt(spot, this.worldPosition);
		menuValuesTime = -1000L;
		if (player instanceof ServerPlayer serverPlayer) {
			WarbandsAdvancements.award(serverPlayer, WarbandsAdvancements.GARRISON);
		}
		return true;
	}

	public boolean recall(Player player) {
		if (!isOwner(player) || !(this.level instanceof ServerLevel)) {
			return false;
		}
		List<SoldierEntity> posted = posted();
		if (posted.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.none_posted"));
			return false;
		}
		for (SoldierEntity soldier : posted) {
			soldier.releaseFromPost();
		}
		menuValuesTime = -1000L;
		return true;
	}

	/** Sends posted soldiers back to Follow when the tower is removed. */
	public void releasePosted() {
		for (SoldierEntity soldier : posted()) {
			soldier.releaseFromPost();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		releasePosted();
	}

	private int link() {
		if (!(this.level instanceof ServerLevel level) || owner == null) {
			return LINK_NO_TOWN;
		}
		TownCenterBlockEntity town = TownRegistry.nearestOwned(level, worldPosition, owner, TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			return LINK_NO_TOWN;
		}
		return town.age().atLeast(Age.CASTLE) ? LINK_OK : LINK_NOT_CASTLE;
	}

	private List<SoldierEntity> garrisonSoldiers() {
		if (!(this.level instanceof ServerLevel level) || owner == null) {
			return List.of();
		}
		AABB box = new AABB(worldPosition).inflate(CALL_RADIUS);
		return level.getEntitiesOfClass(SoldierEntity.class, box,
				soldier -> owner.equals(soldier.ownerId()) && soldier.canGarrison());
	}

	private List<SoldierEntity> posted() {
		return garrisonSoldiers().stream().filter(soldier -> soldier.isPostedTo(this.worldPosition)).toList();
	}

	private List<SoldierEntity> waiting() {
		return garrisonSoldiers().stream().filter(soldier -> !soldier.isPosted()).toList();
	}

	private @Nullable BlockPos freeSpot(ServerLevel level) {
		List<SoldierEntity> posted = posted();
		for (BlockPos offset : SPOTS) {
			BlockPos spot = worldPosition.offset(offset);
			boolean taken = posted.stream().anyMatch(soldier -> spot.equals(soldier.postSpot()));
			boolean open = level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir();
			if (!taken && open) {
				return spot;
			}
		}
		return null;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.warbands.watch_tower");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new WatchTowerMenu(containerId, inventory, ContainerLevelAccess.create(this.level, this.worldPosition), data);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
	}
}
