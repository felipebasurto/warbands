package dev.felipe.warbands.town;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Training building menu. Stock and population are copies of the linked Town Center.
 * Button id is the {@link UnitType} ordinal; cancel is {@link #CANCEL_BUTTON_OFFSET} + slot.
 */
public class TrainingMenu extends AbstractContainerMenu {
	public static final int CANCEL_BUTTON_OFFSET = 100;
	public static final int INVENTORY_Y = 122;

	private final TrainingSite site;
	private final ContainerLevelAccess access;
	private final ContainerData data;

	/** Client constructor. */
	public TrainingMenu(TrainingSite site, int containerId, Inventory inventory) {
		this(site, containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(TrainingBlockEntity.DATA_COUNT));
	}

	public TrainingMenu(TrainingSite site, int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
		super(site.menuType(), containerId);
		this.site = site;
		this.access = access;
		this.data = data;
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	public TrainingSite site() {
		return site;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return this.access.evaluate((level, pos) -> {
			if (!(level.getBlockEntity(pos) instanceof TrainingBlockEntity building)) {
				return false;
			}
			if (id >= CANCEL_BUTTON_OFFSET) {
				return building.cancel(player, id - CANCEL_BUTTON_OFFSET);
			}
			UnitType type = UnitType.byId(id);
			return type != null && building.train(player, type);
		}, false);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, site.block());
	}

	public int stock(Resource resource) {
		return data.get(TrainingBlockEntity.DATA_STOCK + resource.ordinal());
	}

	public int population() {
		return data.get(TrainingBlockEntity.DATA_POPULATION);
	}

	public int populationCap() {
		return data.get(TrainingBlockEntity.DATA_CAP);
	}

	public int reserved() {
		return data.get(TrainingBlockEntity.DATA_RESERVED);
	}

	public int link() {
		return data.get(TrainingBlockEntity.DATA_LINK);
	}

	public float progress() {
		int total = data.get(TrainingBlockEntity.DATA_PROGRESS_TOTAL);
		return total <= 0 ? 0.0F : Math.min(1.0F, data.get(TrainingBlockEntity.DATA_PROGRESS) / (float) total);
	}

	public @Nullable UnitType queued(int slot) {
		return UnitType.byId(data.get(TrainingBlockEntity.DATA_QUEUE + slot) - 1);
	}

	public int queueSize() {
		int size = 0;
		while (size < TownCenterBlockEntity.MAX_QUEUE && queued(size) != null) {
			size++;
		}
		return size;
	}

	public boolean isBlocked() {
		return data.get(TrainingBlockEntity.DATA_BLOCKED) != 0;
	}

	/** Raiders broke more than half of it: it trains again once repaired. */
	public boolean isWrecked() {
		return data.get(TrainingBlockEntity.DATA_BLOCKED) == 2;
	}

	public boolean canAfford(UnitType type) {
		for (Resource resource : Resource.values()) {
			if (stock(resource) < type.cost(resource)) {
				return false;
			}
		}
		return true;
	}

	public boolean canQueue(UnitType type) {
		return link() == TrainingBlockEntity.LINK_OK && canAfford(type) && queueSize() < TownCenterBlockEntity.MAX_QUEUE
				&& population() + reserved() < populationCap();
	}
}
