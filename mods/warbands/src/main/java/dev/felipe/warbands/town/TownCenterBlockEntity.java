package dev.felipe.warbands.town;

import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.entity.SoldierSpawner;
import dev.felipe.warbands.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Holds the stockpile and the training queue, and spawns finished units. */
public class TownCenterBlockEntity extends BlockEntity implements MenuProvider {
	/** Menu data is synced as shorts, so stock is capped below Short.MAX_VALUE. */
	public static final int MAX_STOCK = 30_000;
	public static final int MAX_QUEUE = 5;
	/** A Repair puts this many blocks back per tick. */
	private static final int REPAIR_PER_TICK = 3;
	public static final int BASE_POPULATION = 5;
	public static final int POPULATION_PER_HOUSE = 5;
	public static final int MAX_POPULATION = 50;
	public static final int HOUSE_RADIUS = 64;
	/** Two of your Town Centers must be this far apart, so no house is in range of both. */
	public static final int TOWN_SPACING = 2 * HOUSE_RADIUS;

	// ContainerData layout shared with TownCenterMenu.
	public static final int DATA_STOCK = 0; // 4 slots, one per Resource
	public static final int DATA_POPULATION = 4;
	public static final int DATA_CAP = 5;
	public static final int DATA_PROGRESS = 6;
	public static final int DATA_PROGRESS_TOTAL = 7;
	public static final int DATA_QUEUE = 8; // MAX_QUEUE slots: UnitType ordinal + 1, 0 = empty
	public static final int DATA_BLOCKED = DATA_QUEUE + MAX_QUEUE;
	public static final int DATA_AGE = DATA_BLOCKED + 1;
	public static final int DATA_RESEARCH = DATA_AGE + 1;
	public static final int DATA_RESEARCH_TOTAL = DATA_RESEARCH + 1;
	/** Units already queued at nearby training buildings. They share this town's population cap. */
	public static final int DATA_OTHER_QUEUES = DATA_RESEARCH_TOTAL + 1;
	public static final int DATA_HOUSES = DATA_OTHER_QUEUES + 1;
	/** Blocks missing from the owner's buildings around this town (for the Repair button). */
	public static final int DATA_REPAIR = DATA_HOUSES + 1;
	/** 1 while a raid on this town is in progress. */
	public static final int DATA_RAID = DATA_REPAIR + 1;
	/** 1 while the owner has a camp to conquer (one War Map at a time). */
	public static final int DATA_CAMP = DATA_RAID + 1;
	/** Days until this town's next raid, plus one; 0 when it has none planned (before Feudal, or raids off). */
	public static final int DATA_NEXT_RAID = DATA_CAMP + 1;
	public static final int DATA_COUNT = DATA_NEXT_RAID + 1;

	private @Nullable UUID owner;
	private String ownerName = "";
	private final int[] stock = new int[Resource.values().length];
	private final List<UnitType> queue = new ArrayList<>();
	private int progress;
	private Age age = Age.VILLAGE;
	private int researchProgress;
	private int researchTotal;
	private int otherQueues;
	private int missingBlocks;
	/** Missing blocks are only recounted while the menu asks: each count replays every building's structure file. */
	private long missingBlocksAt = -1000;
	/** Blocks a Repair still has to put back (not saved: a restart just stops it, unpaid). */
	private @Nullable ArrayDeque<Repairs.Missing> repairJob;
	private int repairPlaced;
	private @Nullable UUID repairPlayer;
	private boolean raided;
	private int nextRaidDays;
	private boolean campActive;

	private int population;
	private int populationCap = BASE_POPULATION;
	/** Houses counted at the last refresh; -1 until the first one, so loading a world doesn't announce anything. */
	private int houses = -1;
	private boolean blocked;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < DATA_POPULATION) {
				return stock[index - DATA_STOCK];
			}
			if (index >= DATA_QUEUE && index < DATA_QUEUE + MAX_QUEUE) {
				int slot = index - DATA_QUEUE;
				return slot < queue.size() ? queue.get(slot).ordinal() + 1 : 0;
			}
			return switch (index) {
				case DATA_POPULATION -> populationCount();
				case DATA_CAP -> populationCap;
				case DATA_HOUSES -> Math.max(0, houses);
				case DATA_PROGRESS -> progress;
				case DATA_PROGRESS_TOTAL -> queue.isEmpty() ? 0 : queue.getFirst().trainTicks();
				case DATA_BLOCKED -> blocked ? 1 : 0;
				case DATA_AGE -> age.ordinal();
				case DATA_RESEARCH -> researchProgress;
				case DATA_RESEARCH_TOTAL -> researchTotal;
				case DATA_OTHER_QUEUES -> otherQueues;
				case DATA_REPAIR -> missingBlocks();
				case DATA_RAID -> raided ? 1 : 0;
				case DATA_CAMP -> campActive ? 1 : 0;
				case DATA_NEXT_RAID -> nextRaidDays;
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

	public TownCenterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TOWN_CENTER, pos, state);
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
		if (this.level instanceof ServerLevel serverLevel) {
			TownRegistry.add(serverLevel, this.worldPosition, this.owner);
		}
		setChanged();
	}

	// ---- ticking ----

	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getGameTime() % 20 == 0) {
			// Once a second: the other buildings' queues and the houses both scan registries.
			refreshOtherQueues(level);
			refreshPopulation(level);
			this.raided = RaidRegistry.isActive(level, pos);
			this.nextRaidDays = Raids.daysUntilNext(level, pos);
			Camps.payOut(level, pos);
			this.campActive = owner != null && CampRegistry.active(level, owner).isPresent();
		}
		tickQueue(level, pos, state);
		tickResearch(level, pos, state);
		tickRepair(level);
	}

	private void tickQueue(ServerLevel level, BlockPos pos, BlockState state) {
		if (queue.isEmpty()) {
			progress = 0;
			blocked = false;
			return;
		}
		blocked = ArmyCount.atCap(populationCount(), 0, populationCap);
		if (blocked) {
			return;
		}
		if (++progress >= queue.getFirst().trainTicks() && spawn(level, pos, state, queue.getFirst())) {
			queue.removeFirst();
			progress = 0;
			refreshPopulation(level);
		}
		setChanged();
	}

	private void tickResearch(ServerLevel level, BlockPos pos, BlockState state) {
		if (researchTotal <= 0) {
			return;
		}
		if (++researchProgress >= researchTotal) {
			completeResearch(level, pos, state);
		}
		setChanged();
	}

	/**
	 * Blocks missing from the owner's buildings, recounted at most every 5 s and only when a menu reads it
	 * ({@link #data} is asked while the Town Center screen is open). Nobody watching means nobody to see the number.
	 */
	private int missingBlocks() {
		if (this.level instanceof ServerLevel level && level.getGameTime() - this.missingBlocksAt >= 100) {
			this.missingBlocks = Repairs.missingAround(level, this);
			this.missingBlocksAt = level.getGameTime();
		}
		return this.missingBlocks;
	}

	public Age age() {
		return age;
	}

	public boolean researching() {
		return researchTotal > 0;
	}

	public void refreshPopulation(ServerLevel level) {
		population = owner == null ? 0 : ArmyRegistry.count(level.getServer(), owner);
		int counted = owner == null ? 0 : HouseRegistry.countNear(level, worldPosition, HOUSE_RADIUS, owner);
		populationCap = Math.min(MAX_POPULATION + age.populationBonus(),
				BASE_POPULATION + age.populationBonus() + counted * POPULATION_PER_HOUSE);
		if (houses >= 0 && counted != houses && owner != null) {
			// Tell the owner right away, so a broken house never silently changes the limit.
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(counted < houses ? "message.warbands.house_lost" : "message.warbands.house_counted",
						populationCap));
			}
		}
		houses = counted;
		if (owner != null) {
			// Towns that advanced before ages gave bonuses: make sure the owner's soldiers get them.
			AgeRegistry.raise(level.getServer(), owner, age);
		}
		if (owner != null && populationCap >= MAX_POPULATION) {
			WarbandsAdvancements.award(level.getServer(), owner, WarbandsAdvancements.METROPOLIS);
		}
	}

	private boolean spawn(ServerLevel level, BlockPos pos, BlockState state, UnitType type) {
		if (owner == null) {
			return false;
		}
		return SoldierSpawner.spawn(level, pos, state.getValue(TownCenterBlock.FACING), type, owner, ownerName, 5);
	}

	private void refreshOtherQueues(ServerLevel level) {
		otherQueues = owner == null ? 0 : TrainingRegistry.queuedNear(level, worldPosition, HOUSE_RADIUS, owner);
	}

	// ---- actions from the menu ----

	/** Converts as much of {@code stack} as fits into the stockpile. Returns what's left. */
	public ItemStack deposit(ItemStack stack) {
		ResourceValues.Value value = ResourceValues.of(stack);
		if (value == null) {
			return stack;
		}
		int index = value.resource().ordinal();
		int accepted = Math.min(stack.getCount(), (MAX_STOCK - stock[index]) / value.perItem());
		if (accepted > 0) {
			stock[index] += accepted * value.perItem();
			stack.shrink(accepted);
			setChanged();
		}
		return stack;
	}

	public int stock(Resource resource) {
		return stock[resource.ordinal()];
	}

	public int queueSize() {
		return queue.size();
	}

	/**
	 * Soldiers already in the army. The cache starts at 0 and is not saved; the first read copies the army registry
	 * ({@code houses} stays -1 until then), so a loaded town does not train as if nobody were enlisted.
	 */
	public int populationCount() {
		if (houses < 0 && this.level instanceof ServerLevel serverLevel) {
			int registered = owner == null ? 0 : ArmyRegistry.count(serverLevel.getServer(), owner);
			refreshPopulation(serverLevel);
			return ArmyCount.living(houses >= 0, population, registered);
		}
		return population;
	}

	public int populationCap() {
		return populationCap;
	}

	public void spend(UnitType type) {
		for (Resource resource : Resource.values()) {
			stock[resource.ordinal()] -= type.cost(resource);
		}
		setChanged();
	}

	public void refund(UnitType type) {
		for (Resource resource : Resource.values()) {
			int index = resource.ordinal();
			stock[index] = Math.min(MAX_STOCK, stock[index] + type.cost(resource));
		}
		setChanged();
	}

	public boolean canAfford(UnitType type) {
		return canPay(type::cost);
	}

	public boolean canAfford(Upgrade upgrade) {
		return canPay(upgrade::cost);
	}

	/**
	 * The Repair button: puts back every missing block of the owner's buildings around here, 1 stone each. The blocks
	 * go back a few per tick, bottom-up, with the block's sound and a sparkle ({@link #tickRepair}); stone is spent as
	 * each one is placed.
	 */
	public boolean repair(Player player) {
		if (!(this.level instanceof ServerLevel level) || !isOwner(player)) {
			return false;
		}
		if (this.repairJob != null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.repair_busy", this.repairJob.size()));
			return false;
		}
		if (RaidRegistry.isActive(level, this.worldPosition)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.repair_raid"));
			return false;
		}
		List<Repairs.Missing> missing = Repairs.collectAround(level, this);
		this.missingBlocks = missing.size();
		this.missingBlocksAt = level.getGameTime();
		if (missing.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.repair_nothing"));
			return false;
		}
		int cost = missing.size() * Repairs.STONE_PER_BLOCK;
		if (stock(Resource.STONE) < cost) {
			player.sendOverlayMessage(Component.translatable("message.warbands.repair_stone", cost));
			return false;
		}
		this.repairJob = new ArrayDeque<>(missing);
		this.repairPlaced = 0;
		this.repairPlayer = player.getUUID();
		player.sendOverlayMessage(Component.translatable("message.warbands.repair_started", missing.size()));
		return true;
	}

	/** Whether a Repair is still putting blocks back. */
	public boolean repairing() {
		return this.repairJob != null;
	}

	private void tickRepair(ServerLevel level) {
		if (this.repairJob == null) {
			return;
		}
		if (RaidRegistry.isActive(level, this.worldPosition)) {
			finishRepair(level, "message.warbands.repair_stopped_raid");
			return;
		}
		for (int i = 0; i < REPAIR_PER_TICK && !this.repairJob.isEmpty(); i++) {
			Repairs.Missing next = this.repairJob.poll();
			BlockPos pos = next.pos();
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState current = level.getBlockState(pos);
			if ((!current.isAir() && !current.canBeReplaced()) || current.is(next.state().getBlock())) {
				continue; // filled in the meantime
			}
			if (!next.state().canSurvive(level, pos)) {
				if (next.tries() < 3) {
					this.repairJob.add(next.retry()); // a lantern or banner whose support isn't back yet
				}
				continue;
			}
			if (stock(Resource.STONE) < Repairs.STONE_PER_BLOCK) {
				finishRepair(level, "message.warbands.repair_out_of_stone");
				return;
			}
			level.setBlock(pos, next.state(), Block.UPDATE_CLIENTS);
			StructureBlocks.protect(level, List.of(pos));
			spendStone(Repairs.STONE_PER_BLOCK);
			this.repairPlaced++;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
			if (i == 0) {
				SoundType sound = next.state().getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			}
		}
		if (this.repairJob.isEmpty()) {
			finishRepair(level, null);
		}
	}

	/** Ends the Repair: tells the player what went back and for how much (and why it stopped, if it did). */
	private void finishRepair(ServerLevel level, @Nullable String stopped) {
		int placed = this.repairPlaced;
		UUID who = this.repairPlayer;
		this.repairJob = null;
		this.repairPlaced = 0;
		this.repairPlayer = null;
		this.missingBlocks = Repairs.missingAround(level, this);
		this.missingBlocksAt = level.getGameTime();
		refreshPopulation(level);
		ServerPlayer player = who == null ? null : level.getServer().getPlayerList().getPlayer(who);
		if (player == null) {
			return;
		}
		player.sendSystemMessage(Component.translatable("message.warbands.repaired", placed, placed * Repairs.STONE_PER_BLOCK)
				.withStyle(ChatFormatting.GREEN));
		if (stopped != null) {
			player.sendSystemMessage(Component.translatable(stopped).withStyle(ChatFormatting.RED));
		}
		if (placed > 0) {
			level.playSound(null, this.worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
			WarbandsAdvancements.award(player, WarbandsAdvancements.REPAIR);
		}
	}

	/** Resources from outside the town (a won raid, a camp's plunder), up to the stock limit. */
	public void addStock(Resource resource, int amount) {
		int index = resource.ordinal();
		stock[index] = Math.min(MAX_STOCK, stock[index] + Math.max(0, amount));
		setChanged();
	}

	/** Pays {@code amount} of one resource (a War Map). The caller checked the stock. */
	public void take(Resource resource, int amount) {
		int index = resource.ordinal();
		stock[index] = Math.max(0, stock[index] - amount);
		setChanged();
	}

	/** Raiders carry off {@code share} of every resource. Returns what they took, indexed by {@link Resource#ordinal()}. */
	public int[] plunder(double share) {
		int[] taken = new int[stock.length];
		for (int i = 0; i < stock.length; i++) {
			taken[i] = (int) Math.floor(stock[i] * share);
			stock[i] -= taken[i];
		}
		setChanged();
		return taken;
	}

	/** Stone paid for a building placed near this town, or a repaired block. The caller checked the stock first. */
	public void spendStone(int amount) {
		stock[Resource.STONE.ordinal()] = Math.max(0, stock[Resource.STONE.ordinal()] - amount);
		setChanged();
	}

	public void spend(Upgrade upgrade) {
		pay(upgrade::cost, -1);
	}

	private boolean canPay(Function<Resource, Integer> cost) {
		for (Resource resource : Resource.values()) {
			if (stock[resource.ordinal()] < cost.apply(resource)) {
				return false;
			}
		}
		return true;
	}

	private void pay(Function<Resource, Integer> cost, int sign) {
		for (Resource resource : Resource.values()) {
			int index = resource.ordinal();
			stock[index] = Math.min(MAX_STOCK, stock[index] + sign * cost.apply(resource));
		}
		setChanged();
	}

	public boolean train(Player player, UnitType type) {
		if (!(this.level instanceof ServerLevel serverLevel) || !isOwner(player) || type == null || type.site() != TrainingSite.TOWN_CENTER) {
			return false;
		}
		refreshOtherQueues(serverLevel);
		if (queue.size() >= MAX_QUEUE || !canAfford(type) || !age.atLeast(type.requiredAge())) {
			return false;
		}
		if (ArmyCount.atCap(populationCount(), queue.size() + otherQueues, populationCap)) {
			player.sendOverlayMessage(Component.translatable("message.warbands.need_houses"));
			return false;
		}
		spend(type);
		queue.add(type);
		setChanged();
		return true;
	}

	/**
	 * Pays the next age's cost from the stockpile and one item in {@code deposit}, then starts the timer.
	 * The building changes when the timer finishes.
	 */
	public boolean tryAdvance(Player player, Container deposit) {
		if (!isOwner(player)) {
			return false;
		}
		Age next = age.next();
		if (next == null) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.age_max"));
			return false;
		}
		if (researchTotal > 0) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.age_busy"));
			return false;
		}
		if (!canPay(next::cost)) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.age_resources"));
			return false;
		}
		Item requirementItem = next.requirement();
		ItemStack requirement = deposit.getItem(0);
		if (requirementItem == null || !requirement.is(requirementItem)) {
			player.sendOverlayMessage(Component.translatable("gui.warbands.reason.age_item", Component.translatable(requirementItem == null ? "" : requirementItem.getDescriptionId())));
			return false;
		}
		pay(next::cost, -1);
		requirement.shrink(1);
		if (requirement.isEmpty()) {
			deposit.setItem(0, ItemStack.EMPTY);
		} else {
			deposit.setChanged();
		}
		researchProgress = 0;
		researchTotal = next.researchTicks();
		setChanged();
		return true;
	}

	private void completeResearch(ServerLevel level, BlockPos pos, BlockState state) {
		Age next = age.next();
		researchProgress = 0;
		researchTotal = 0;
		if (next == null) {
			return;
		}
		age = next;
		int skipped = BlueprintPlacer.upgrade(level, pos, state.getValue(TownCenterBlock.FACING), age.structureId());
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (owner != null) {
			WarbandsAdvancements.award(level.getServer(), owner,
					age == Age.CASTLE ? WarbandsAdvancements.CASTLE_AGE : WarbandsAdvancements.FEUDAL_AGE);
			AgeRegistry.raise(level.getServer(), owner, age);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				if (age.atLeast(Age.FEUDAL)) {
					unlockFeudalRecipes(player);
				}
				if (age.atLeast(Age.CASTLE)) {
					unlockCastleRecipes(player);
				}
				announceAge(player, age, skipped);
			}
		}
		setChanged();
	}

	/** Keep the milestone compact; benefits stay in the guide and the Advance tooltip. */
	private static void announceAge(ServerPlayer player, Age age, int skipped) {
		player.sendSystemMessage(Component.translatable("message.warbands.age_up", age.displayName())
				.withStyle(ChatFormatting.GOLD));
		if (skipped != 0) {
			player.sendSystemMessage(Component.translatable(skipped > 0
					? "message.warbands.age_up_partial" : "message.warbands.age_up_missing", age.displayName())
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	public boolean cancel(Player player, int slot) {
		if (!isOwner(player) || slot < 0 || slot >= queue.size()) {
			return false;
		}
		UnitType type = queue.remove(slot);
		refund(type);
		if (slot == 0) {
			progress = 0;
		}
		setChanged();
		return true;
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.warbands.town_center");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		if (this.level instanceof ServerLevel serverLevel) {
			refreshPopulation(serverLevel);
			refreshOtherQueues(serverLevel);
		}
		if (player instanceof ServerPlayer serverPlayer && isOwner(player)) {
			if (age.atLeast(Age.FEUDAL)) {
				unlockFeudalRecipes(serverPlayer);
			}
			if (age.atLeast(Age.CASTLE)) {
				unlockCastleRecipes(serverPlayer);
			}
		}
		return new TownCenterMenu(containerId, inventory, ContainerLevelAccess.create(this.level, this.worldPosition), data);
	}

	private static void unlockFeudalRecipes(ServerPlayer player) {
		player.awardRecipesByKey(List.of(
				ResourceKey.create(Registries.RECIPE, Warbands.id("barracks_blueprint")),
				ResourceKey.create(Registries.RECIPE, Warbands.id("archery_range_blueprint")),
				ResourceKey.create(Registries.RECIPE, Warbands.id("blacksmith_blueprint"))));
	}

	private static void unlockCastleRecipes(ServerPlayer player) {
		player.awardRecipesByKey(List.of(
				ResourceKey.create(Registries.RECIPE, Warbands.id("stable_blueprint")),
				ResourceKey.create(Registries.RECIPE, Warbands.id("watch_tower_blueprint")),
				ResourceKey.create(Registries.RECIPE, Warbands.id("siege_workshop_blueprint"))));
	}

	// ---- saving ----

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, owner);
		output.putString("OwnerName", ownerName);
		output.putIntArray("Stock", stock);
		output.store("Queue", UnitType.CODEC.listOf(), List.copyOf(queue));
		output.putInt("Progress", progress);
		output.putString("Age", age.serializedName());
		output.putInt("Research", researchProgress);
		output.putInt("ResearchTotal", researchTotal);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("OwnerName", "");
		input.getIntArray("Stock").ifPresent(saved -> System.arraycopy(saved, 0, stock, 0, Math.min(saved.length, stock.length)));
		queue.clear();
		input.read("Queue", UnitType.CODEC.listOf()).ifPresent(queue::addAll);
		progress = input.getIntOr("Progress", 0);
		age = Age.byName(input.getStringOr("Age", Age.VILLAGE.serializedName()));
		researchProgress = input.getIntOr("Research", 0);
		researchTotal = input.getIntOr("ResearchTotal", 0);
	}
}
