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
import org.jspecify.annotations.Nullable;

/** Blacksmith menu. Button id is the {@link Upgrade} ordinal. Stock is a copy of the linked Town Center. */
public class BlacksmithMenu extends AbstractContainerMenu {
	public static final int INVENTORY_Y = 152;

	private final ContainerLevelAccess access;
	private final ContainerData data;

	public BlacksmithMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(BlacksmithBlockEntity.DATA_COUNT));
	}

	public BlacksmithMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
		super(ModMenus.BLACKSMITH, containerId);
		this.access = access;
		this.data = data;
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return this.access.evaluate((level, pos) -> {
			if (!(level.getBlockEntity(pos) instanceof BlacksmithBlockEntity smithy)) {
				return false;
			}
			return smithy.research(player, Upgrade.byId(id));
		}, false);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.BLACKSMITH);
	}

	public int stock(Resource resource) {
		return data.get(BlacksmithBlockEntity.DATA_STOCK + resource.ordinal());
	}

	public int link() {
		return data.get(BlacksmithBlockEntity.DATA_LINK);
	}

	public boolean owns(Upgrade upgrade) {
		return (data.get(BlacksmithBlockEntity.DATA_OWNED) & (1 << upgrade.ordinal())) != 0;
	}

	public boolean canAfford(Upgrade upgrade) {
		for (Resource resource : Resource.values()) {
			if (stock(resource) < upgrade.cost(resource)) {
				return false;
			}
		}
		return true;
	}

	public @Nullable Upgrade researching() {
		return Upgrade.byId(data.get(BlacksmithBlockEntity.DATA_RESEARCH) - 1);
	}

	public float researchProgress() {
		int total = data.get(BlacksmithBlockEntity.DATA_TOTAL);
		return total <= 0 ? 0.0F : Math.min(1.0F, data.get(BlacksmithBlockEntity.DATA_PROGRESS) / (float) total);
	}

	public Age townAge() {
		Age[] ages = Age.values();
		int id = data.get(BlacksmithBlockEntity.DATA_AGE);
		return id >= 0 && id < ages.length ? ages[id] : Age.VILLAGE;
	}
}
