package dev.felipe.warbands.town;

import dev.felipe.warbands.entity.SoldierSpawner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * A training building's queue. It has no stockpile: costs come out of the nearest owned Town Center,
 * and the queue counts toward that town's population cap together with every other building's.
 */
public class TrainingBlockEntity extends BlockEntity implements MenuProvider {
	public static final int DOOR_DISTANCE = 4;
	public static final int LINK_OK = 0;
	public static final int LINK_NO_TOWN = 1;
	/** The linked Town Center hasn't reached the building's age. */
	public static final int LINK_TOO_EARLY = 2;

	public static final int DATA_STOCK = 0;
	public static final int DATA_POPULATION = 4;
	public static final int DATA_CAP = 5;
	public static final int DATA_PROGRESS = 6;
	public static final int DATA_PROGRESS_TOTAL = 7;
	public static final int DATA_QUEUE = 8;
	public static final int DATA_BLOCKED = DATA_QUEUE + TownCenterBlockEntity.MAX_QUEUE;
	public static final int DATA_LINK = DATA_BLOCKED + 1;
	public static final int DATA_RESERVED = DATA_LINK + 1;
	public static final int DATA_COUNT = DATA_RESERVED + 1;

	private final TrainingSite site;
	private @Nullable UUID owner;
	private String ownerName = "";
	private final int[] stock = new int[Resource.values().length];
	private final List<UnitType> queue = new ArrayList<>();
	private int progress;
	private int population;
	private int populationCap = TownCenterBlockEntity.BASE_POPULATION;
	private int reserved;
	private int link = LINK_NO_TOWN;
	private boolean blocked;
	/** Refunds for cancelled units while no Town Center was in reach: paid on the first tick one is. */
	private final List<UnitType> pendingRefunds = new ArrayList<>();
	/** Below half its blocks after a raid: training waits until the Town Center repairs it. */
	private boolean wrecked;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < DATA_POPULATION) {
				return stock[index - DATA_STOCK];
			}
			if (index >= DATA_QUEUE && index < DATA_QUEUE + TownCenterBlockEntity.MAX_QUEUE) {
				int slot = index - DATA_QUEUE;
				return slot < queue.size() ? queue.get(slot).ordinal() + 1 : 0;
			}
			return switch (index) {
				case DATA_POPULATION -> population;
				case DATA_CAP -> populationCap;
				case DATA_PROGRESS -> progress;
				case DATA_PROGRESS_TOTAL -> queue.isEmpty() ? 0 : queue.getFirst().trainTicks();
				case DATA_BLOCKED -> wrecked ? 2 : blocked ? 1 : 0;
				case DATA_LINK -> link;
				case DATA_RESERVED -> reserved;
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

	public TrainingBlockEntity(TrainingSite site, BlockPos pos, BlockState state) {
		super(site.blockEntityType(), pos, state);
		this.site = site;
	}

	public TrainingSite site() {
		return site;
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

	public int queueSize() {
		return queue.size();
	}

	public void setOwner(Player player) {
		this.owner = player.getUUID();
		this.ownerName = player.getName().getString();
		if (this.level instanceof ServerLevel serverLevel) {
			TrainingRegistry.add(serverLevel, site, this.worldPosition, this.owner);
		}
		setChanged();
	}

	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getGameTime() % 20 != 0) {
			// Between syncs only the timer moves; the town lookup and queue scan run once a second.
			if (queue.isEmpty()) {
				return;
			}
			if (progress + 1 < queue.getFirst().trainTicks()) {
				if (!blocked) {
					progress++;
					setChanged();
				}
				return;
			}
		}
		TownCenterBlockEntity town = linkedTown(level);
		if (town != null && !pendingRefunds.isEmpty()) {
			for (UnitType pending : pendingRefunds) {
				town.refund(pending);
			}
			pendingRefunds.clear();
			setChanged();
		}
		syncFrom(level, town);
		wrecked = BuildingRegistry.isWrecked(level, pos);
		if (queue.isEmpty()) {
			progress = 0;
			blocked = false;
			return;
		}
		blocked = wrecked || population >= populationCap;
		if (blocked) {
			return;
		}
		if (++progress >= queue.getFirst().trainTicks() && owner != null
				&& SoldierSpawner.spawn(level, pos, state.getValue(TrainingBlock.FACING), queue.getFirst(), owner, ownerName, DOOR_DISTANCE)) {
			queue.removeFirst();
			progress = 0;
			if (town != null) {
				town.refreshPopulation(level);
			}
			syncFrom(level, town);
		}
		setChanged();
	}

	private @Nullable TownCenterBlockEntity linkedTown(ServerLevel level) {
		return owner == null ? null : TownRegistry.nearestOwned(level, worldPosition, owner, TownCenterBlockEntity.HOUSE_RADIUS);
	}

	private void syncFrom(ServerLevel level, @Nullable TownCenterBlockEntity town) {
		if (owner == null || town == null) {
			Arrays.fill(stock, 0);
			population = owner == null ? 0 : ArmyRegistry.count(level.getServer(), owner);
			populationCap = TownCenterBlockEntity.BASE_POPULATION;
			reserved = 0;
			link = LINK_NO_TOWN;
			return;
		}
		for (Resource resource : Resource.values()) {
			stock[resource.ordinal()] = town.stock(resource);
		}
		population = town.populationCount();
		populationCap = town.populationCap();
		reserved = town.queueSize() + TrainingRegistry.queuedNear(level, town.getBlockPos(), TownCenterBlockEntity.HOUSE_RADIUS, owner);
		link = town.age().atLeast(site.requiredAge()) ? LINK_OK : LINK_TOO_EARLY;
	}

	public boolean train(Player player, UnitType type) {
		if (!isOwner(player) || !(this.level instanceof ServerLevel level)) {
			return false;
		}
		if (type == null || type.site() != site) {
			return false;
		}
		TownCenterBlockEntity town = linkedTown(level);
		syncFrom(level, town);
		if (link == LINK_NO_TOWN || town == null) {
			player.sendOverlayMessage(site.needsTown());
			return false;
		}
		if (link == LINK_TOO_EARLY) {
			player.sendOverlayMessage(site.requiredAge().notReachedMessage());
			return false;
		}
		if (queue.size() >= TownCenterBlockEntity.MAX_QUEUE) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.queue"));
			return false;
		}
		if (!town.canAfford(type)) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.resources"));
			return false;
		}
		if (population + reserved >= populationCap) {
			player.sendOverlayMessage(Component.translatable("message.warbands.need_houses"));
			return false;
		}
		town.spend(type);
		queue.add(type);
		syncFrom(level, town);
		setChanged();
		return true;
	}

	public boolean cancel(Player player, int slot) {
		if (!isOwner(player) || !(this.level instanceof ServerLevel level) || slot < 0 || slot >= queue.size()) {
			return false;
		}
		UnitType type = queue.remove(slot);
		TownCenterBlockEntity town = linkedTown(level);
		if (town != null) {
			town.refund(type);
		} else {
			pendingRefunds.add(type);
			player.sendOverlayMessage(Component.translatable("message.warbands.refund_pending"));
		}
		if (slot == 0) {
			progress = 0;
		}
		setChanged();
		return true;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (!(this.level instanceof ServerLevel level) || owner == null || queue.isEmpty()) {
			return;
		}
		TownCenterBlockEntity town = TownRegistry.nearestOwned(level, pos, owner, TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			return;
		}
		for (UnitType type : List.copyOf(queue)) {
			town.refund(type);
		}
		queue.clear();
		for (UnitType type : List.copyOf(pendingRefunds)) {
			town.refund(type);
		}
		pendingRefunds.clear();
	}

	@Override
	public Component getDisplayName() {
		return site.displayName();
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		if (this.level instanceof ServerLevel serverLevel) {
			syncFrom(serverLevel, linkedTown(serverLevel));
		}
		return new TrainingMenu(site, containerId, inventory, ContainerLevelAccess.create(this.level, this.worldPosition), data);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
		output.store("Queue", UnitType.CODEC.listOf(), List.copyOf(queue));
		output.putInt("Progress", progress);
		output.store("PendingRefunds", UnitType.CODEC.listOf(), List.copyOf(pendingRefunds));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
		queue.clear();
		input.read("Queue", UnitType.CODEC.listOf()).ifPresent(queue::addAll);
		progress = input.getIntOr("Progress", 0);
		pendingRefunds.clear();
		input.read("PendingRefunds", UnitType.CODEC.listOf()).ifPresent(pendingRefunds::addAll);
	}
}
