package dev.felipe.warbands.town;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import dev.felipe.warbands.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Town Center menu: one deposit slot (items are converted into the stockpile as soon as they
 * land there) plus synced stockpile, population and queue numbers.
 * Buttons use vanilla {@code clickMenuButton}: id = UnitType ordinal to train,
 * {@link #CANCEL_BUTTON_OFFSET} + slot to cancel a queued unit.
 */
public class TownCenterMenu extends AbstractContainerMenu {
	public static final int CANCEL_BUTTON_OFFSET = 100;
	public static final int ADVANCE_BUTTON = 200;
	public static final int REPAIR_BUTTON = 201;
	public static final int WAR_MAP_BUTTON = 202;
	/** Shift-click on the War Map button. */
	public static final int ABANDON_CAMP_BUTTON = 203;
	public static final int DEPOSIT_X = 8;
	public static final int DEPOSIT_Y = 56;
	public static final int INVENTORY_Y = 136;

	private final ContainerLevelAccess access;
	private final ContainerData data;
	private final SimpleContainer deposit = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			convertDeposit(this);
		}
	};
	private final Player player;
	private boolean converting;

	/** Client constructor. */
	public TownCenterMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(TownCenterBlockEntity.DATA_COUNT));
	}

	public TownCenterMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
		super(ModMenus.TOWN_CENTER, containerId);
		this.access = access;
		this.player = inventory.player;
		this.data = data;
		this.addSlot(new Slot(deposit, 0, DEPOSIT_X, DEPOSIT_Y));
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	private void convertDeposit(Container container) {
		if (converting) {
			return;
		}
		converting = true;
		this.access.execute((level, pos) -> {
			ItemStack stack = container.getItem(0);
			if (!stack.isEmpty() && level.getBlockEntity(pos) instanceof TownCenterBlockEntity townCenter) {
				ItemStack left = townCenter.deposit(stack.copy());
				if (this.player instanceof ServerPlayer serverPlayer) {
					if (left.getCount() < stack.getCount()) {
						WarbandsAdvancements.award(serverPlayer, WarbandsAdvancements.STOCKPILE);
					} else if (ResourceValues.of(stack) == null && !heldForAdvance(stack)) {
						serverPlayer.sendOverlayMessage(Component.translatable("message.warbands.deposit_rejected",
								Component.translatable(stack.getItem().getDescriptionId())));
					}
				}
				container.setItem(0, left);
			}
		});
		converting = false;
	}

	/** Age trophies sit in Deposit until Advance. They are not stockpile resources, and refusing them would be wrong. */
	private static boolean heldForAdvance(ItemStack stack) {
		for (Age age : Age.values()) {
			Item requirement = age.requirement();
			if (requirement != null && stack.is(requirement)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return this.access.evaluate((level, pos) -> {
			if (!(level.getBlockEntity(pos) instanceof TownCenterBlockEntity townCenter)) {
				return false;
			}
			if (id == ADVANCE_BUTTON) {
				return townCenter.tryAdvance(player, deposit);
			}
			if (id == REPAIR_BUTTON) {
				return townCenter.repair(player);
			}
			if (id == ABANDON_CAMP_BUTTON) {
				return townCenter.isOwner(player) && level instanceof ServerLevel serverLevel && Camps.abandon(serverLevel, player);
			}
			if (id == WAR_MAP_BUTTON) {
				return townCenter.isOwner(player) && level instanceof ServerLevel serverLevel && Camps.buy(serverLevel, townCenter, player);
			}
			if (id >= CANCEL_BUTTON_OFFSET) {
				return townCenter.cancel(player, id - CANCEL_BUTTON_OFFSET);
			}
			UnitType type = UnitType.byId(id);
			return type != null && townCenter.train(player, type);
		}, false);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index == 0
				? this.moveItemStackTo(stack, 1, this.slots.size(), true)
				: this.moveItemStackTo(stack, 0, 1, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// Anything the stockpile didn't accept goes back to the player.
		this.clearContainer(player, deposit);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.TOWN_CENTER);
	}

	// ---- read by the screen ----

	public int stock(Resource resource) {
		return data.get(TownCenterBlockEntity.DATA_STOCK + resource.ordinal());
	}

	public int population() {
		return data.get(TownCenterBlockEntity.DATA_POPULATION);
	}

	public int populationCap() {
		return data.get(TownCenterBlockEntity.DATA_CAP);
	}

	/** Standing houses of the owner near this Town Center. */
	public int houses() {
		return data.get(TownCenterBlockEntity.DATA_HOUSES);
	}

	public float progress() {
		int total = data.get(TownCenterBlockEntity.DATA_PROGRESS_TOTAL);
		return total <= 0 ? 0.0F : Math.min(1.0F, data.get(TownCenterBlockEntity.DATA_PROGRESS) / (float) total);
	}

	public @Nullable UnitType queued(int slot) {
		return UnitType.byId(data.get(TownCenterBlockEntity.DATA_QUEUE + slot) - 1);
	}

	public int queueSize() {
		int size = 0;
		while (size < TownCenterBlockEntity.MAX_QUEUE && queued(size) != null) {
			size++;
		}
		return size;
	}

	public boolean isBlocked() {
		return data.get(TownCenterBlockEntity.DATA_BLOCKED) != 0;
	}

	public boolean canAfford(UnitType type) {
		for (Resource resource : Resource.values()) {
			if (stock(resource) < type.cost(resource)) {
				return false;
			}
		}
		return true;
	}

	public Age age() {
		Age[] values = Age.values();
		int id = data.get(TownCenterBlockEntity.DATA_AGE);
		return id >= 0 && id < values.length ? values[id] : Age.VILLAGE;
	}

	public boolean researching() {
		return data.get(TownCenterBlockEntity.DATA_RESEARCH_TOTAL) > 0;
	}

	public float researchProgress() {
		int total = data.get(TownCenterBlockEntity.DATA_RESEARCH_TOTAL);
		return total <= 0 ? 0.0F : Math.min(1.0F, data.get(TownCenterBlockEntity.DATA_RESEARCH) / (float) total);
	}

	public boolean hasRequirement(Age age) {
		Item requirement = age.requirement();
		return requirement != null && deposit.getItem(0).is(requirement);
	}

	public boolean canAffordAge(Age age) {
		for (Resource resource : Resource.values()) {
			if (stock(resource) < age.cost(resource)) {
				return false;
			}
		}
		return true;
	}

	public boolean canAdvance() {
		Age next = age().next();
		return next != null && !researching() && canAffordAge(next) && hasRequirement(next);
	}

	public int missingBlocks() {
		return data.get(TownCenterBlockEntity.DATA_REPAIR);
	}

	public boolean campActive() {
		return data.get(TownCenterBlockEntity.DATA_CAMP) != 0;
	}

	public boolean raided() {
		return data.get(TownCenterBlockEntity.DATA_RAID) != 0;
	}

	public int otherQueues() {
		return data.get(TownCenterBlockEntity.DATA_OTHER_QUEUES);
	}

	/** Days until the next raid (0 = tonight), or -1 when none is planned. */
	public int nextRaidDays() {
		return data.get(TownCenterBlockEntity.DATA_NEXT_RAID) - 1;
	}

	/** This town's queue plus spearmen queued at nearby barracks. */
	public int committed() {
		return queueSize() + otherQueues();
	}

	public boolean canQueue(UnitType type) {
		int committed = committed();
		return canAfford(type) && queueSize() < TownCenterBlockEntity.MAX_QUEUE && population() + committed < populationCap();
	}
}
