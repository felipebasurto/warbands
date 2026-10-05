package dev.felipe.warbands.town;

import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Watch Tower menu. Button 0 posts one ranged soldier. Button 1 recalls everyone posted here. */
public class WatchTowerMenu extends AbstractContainerMenu {
	public static final int POST_BUTTON = 0;
	public static final int RECALL_BUTTON = 1;
	public static final int INVENTORY_Y = 84;

	private final ContainerLevelAccess access;
	private final ContainerData data;

	public WatchTowerMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(WatchTowerBlockEntity.DATA_COUNT));
	}

	public WatchTowerMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
		super(ModMenus.WATCH_TOWER, containerId);
		this.access = access;
		this.data = data;
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return this.access.evaluate((level, pos) -> {
			if (!(level.getBlockEntity(pos) instanceof WatchTowerBlockEntity tower)) {
				return false;
			}
			if (id == POST_BUTTON) {
				return tower.post(player);
			}
			if (id == RECALL_BUTTON) {
				return tower.recall(player);
			}
			return false;
		}, false);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.WATCH_TOWER);
	}

	public int posted() {
		return data.get(WatchTowerBlockEntity.DATA_POSTED);
	}

	public int nearby() {
		return data.get(WatchTowerBlockEntity.DATA_NEARBY);
	}

	public int link() {
		return data.get(WatchTowerBlockEntity.DATA_LINK);
	}
}
