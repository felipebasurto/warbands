package dev.felipe.warbands;

import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.raid.RaidKind;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.TownCenterBlock;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.UnitType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tester's shortcut ({@code /warbands playtest}): a Castle-age town with every building, a full stockpile, an army
 * and the rare items, built in front of the player in a second, plus a clickable chat menu that starts every raid kind,
 * sends you to every camp kind and flips day and night. Playing it by hand stays the real test; this skips the grind.
 */
public final class Playtest {
	/** Where each building goes, in blocks right of and ahead of the player (buildings face back at the player). */
	private record Spot(Blueprint blueprint, int right, int ahead) {
	}

	private static final List<Spot> LAYOUT = List.of(
			new Spot(Blueprint.BARRACKS, -14, 8), new Spot(Blueprint.ARCHERY_RANGE, -14, 19), new Spot(Blueprint.STABLE, -14, 30),
			new Spot(Blueprint.BLACKSMITH, 14, 8), new Spot(Blueprint.SIEGE_WORKSHOP, 14, 19), new Spot(Blueprint.HOUSE, 14, 29),
			new Spot(Blueprint.HOUSE, 14, 37), new Spot(Blueprint.WATCH_TOWER, 0, 30));
	private static final int TOWN_AHEAD = 16;

	private Playtest() {
	}

	/** Builds the whole test town ahead of {@code player}. Returns false (having said why) if the Town Center can't go there. */
	public static boolean setUp(ServerLevel level, ServerPlayer player) {
		Direction ahead = player.getDirection();
		BlockPos start = player.blockPosition();
		BlockPos center = site(level, at(start, ahead, 0, TOWN_AHEAD), Blueprint.TOWN_CENTER);
		BlueprintPlacer.Problem problem = BlueprintPlacer.checkServer(level, center, Blueprint.TOWN_CENTER, player);
		if (problem != BlueprintPlacer.Problem.NONE) {
			player.sendSystemMessage(Component.translatable("message.warbands.playtest_no_room", problem.message(Blueprint.TOWN_CENTER))
					.withStyle(ChatFormatting.RED));
			return false;
		}
		BlueprintPlacer.place(level, center, Blueprint.TOWN_CENTER, player);
		BlockPos core = center.above();
		fill(level, core);
		advance(level, core, player, Age.FEUDAL);
		advance(level, core, player, Age.CASTLE);
		fill(level, core);

		int skipped = 0;
		for (Spot spot : LAYOUT) {
			BlockPos site = site(level, at(start, ahead, spot.right(), spot.ahead()), spot.blueprint());
			if (BlueprintPlacer.checkServer(level, site, spot.blueprint(), player) == BlueprintPlacer.Problem.NONE) {
				BlueprintPlacer.place(level, site, spot.blueprint(), player);
			} else {
				skipped++;
			}
		}
		fill(level, core);
		army(level, player, at(start, ahead, 0, 5));
		kit(player);
		player.sendSystemMessage(Component.translatable("message.warbands.playtest_ready").withStyle(ChatFormatting.GREEN));
		if (skipped > 0) {
			player.sendSystemMessage(Component.translatable("message.warbands.playtest_skipped", skipped).withStyle(ChatFormatting.YELLOW));
		}
		player.sendSystemMessage(Component.translatable("message.warbands.playtest_guide").withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** The gear and rare items worth trying by hand. Safe to give again (only one Tester's Guide). */
	public static void kit(ServerPlayer player) {
		for (Item item : List.of(ModItems.PLAYTEST_GUIDE, ModItems.HANDBOOK, ModItems.WAR_HORN, ModItems.HAG_CHARM, ModItems.FROST_SIGIL,
				ModItems.GRAVE_LANTERN, ModItems.OMEN_BANNER, ModItems.RELIC_PEDESTAL, ModItems.WARHORSE_WHISTLE,
				Items.NETHERITE_SWORD, Items.BOW, Items.SHIELD)) {
			if (item != ModItems.PLAYTEST_GUIDE || !player.getInventory().contains(new ItemStack(item))) {
				give(player, new ItemStack(item));
			}
		}
		give(player, new ItemStack(Items.ARROW, 64));
		give(player, new ItemStack(Items.COOKED_BEEF, 64));
		give(player, new ItemStack(Items.GOLDEN_APPLE, 16));
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			if (player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, new ItemStack(switch (slot) {
					case HEAD -> Items.DIAMOND_HELMET;
					case CHEST -> Items.DIAMOND_CHESTPLATE;
					case LEGS -> Items.DIAMOND_LEGGINGS;
					default -> Items.DIAMOND_BOOTS;
				}));
			}
		}
	}

	/** The clickable test menu: every raid kind, every camp kind, and the shortcuts around them. */
	public static void menu(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("playtest.warbands.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		MutableComponent raids = Component.translatable("playtest.warbands.raids").withStyle(ChatFormatting.GRAY);
		for (RaidKind kind : RaidKind.values()) {
			raids.append(" ").append(button(kind.displayName(), "/warbands raid " + kind.serializedName(), ChatFormatting.RED,
					Component.translatable("playtest.warbands.raid_hover", kind.displayName())));
		}
		raids.append(" ").append(button(Component.translatable("playtest.warbands.skip"), "/warbands raid skip",
				ChatFormatting.YELLOW, Component.translatable("playtest.warbands.skip_hover")));
		player.sendSystemMessage(raids);

		for (Age age : List.of(Age.VILLAGE, Age.FEUDAL, Age.CASTLE)) {
			MutableComponent camps = Component.translatable("playtest.warbands.camps", age.displayName()).withStyle(ChatFormatting.GRAY);
			for (CampTier kind : CampTier.kinds(age)) {
				camps.append(" ").append(button(kind.displayName(), "/warbands camp " + kind.serializedName(), ChatFormatting.AQUA,
						Component.translatable("playtest.warbands.camp_hover", kind.displayName())));
			}
			player.sendSystemMessage(camps);
		}

		MutableComponent tools = Component.translatable("playtest.warbands.tools").withStyle(ChatFormatting.GRAY);
		tools.append(" ").append(button(Component.translatable("playtest.warbands.camp_tp"), "/warbands camp tp", ChatFormatting.GREEN,
				Component.translatable("playtest.warbands.camp_tp_hover")));
		tools.append(" ").append(button(Component.translatable("playtest.warbands.camp_abandon"), "/warbands camp abandon",
				ChatFormatting.GREEN, Component.translatable("playtest.warbands.camp_abandon_hover")));
		tools.append(" ").append(button(Component.translatable("playtest.warbands.day"), "/time set day", ChatFormatting.WHITE,
				Component.translatable("playtest.warbands.day")));
		tools.append(" ").append(button(Component.translatable("playtest.warbands.night"), "/time set midnight", ChatFormatting.WHITE,
				Component.translatable("playtest.warbands.night_hover")));
		tools.append(" ").append(button(Component.translatable("playtest.warbands.kit"), "/warbands playtest kit", ChatFormatting.WHITE,
				Component.translatable("playtest.warbands.kit_hover")));
		player.sendSystemMessage(tools);
		player.sendSystemMessage(Component.translatable("playtest.warbands.menu_again").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Teleports the player to a spot just outside their active camp (it builds as they arrive). */
	public static boolean toCamp(ServerLevel level, ServerPlayer player) {
		CampRegistry.Camp camp = CampRegistry.active(level, player.getUUID()).orElse(null);
		if (camp == null) {
			player.sendSystemMessage(Component.translatable("message.warbands.war_map_none").withStyle(ChatFormatting.RED));
			return false;
		}
		// South of the camp, on the ground of that chunk loaded first (an unloaded chunk reads as the world's bottom).
		teleport(level, player, camp.center().offset(0, 0, camp.layout().half() + 12), 180.0F);
		return true;
	}

	/** Teleports the player in front of the door of their nearest Town Center, however far away it is. */
	public static boolean home(ServerLevel level, ServerPlayer player) {
		TownRegistry.Town home = null;
		for (TownRegistry.Town town : TownRegistry.towns(level)) {
			if (town.owner().equals(player.getUUID())
					&& (home == null || town.pos().distSqr(player.blockPosition()) < home.pos().distSqr(player.blockPosition()))) {
				home = town;
			}
		}
		if (home == null) {
			player.sendSystemMessage(Component.translatable("message.warbands.playtest_no_home").withStyle(ChatFormatting.RED));
			return false;
		}
		level.getChunkAt(home.pos());
		BlockState core = level.getBlockState(home.pos());
		Direction door = core.hasProperty(TownCenterBlock.FACING) ? core.getValue(TownCenterBlock.FACING) : Direction.SOUTH;
		teleport(level, player, home.pos().relative(door, Blueprint.TOWN_CENTER.half() + 3), door.getOpposite().toYRot());
		return true;
	}

	private static void teleport(ServerLevel level, ServerPlayer player, BlockPos column, float yaw) {
		int y = Ground.surface(level, column.getX(), column.getZ()) + 1;
		player.teleportTo(level, column.getX() + 0.5, y, column.getZ() + 0.5, java.util.Set.of(), yaw, 0.0F, true);
		player.resetFallDistance();
	}

	/** Tops up the stockpile of the player's nearest Town Center. */
	public static boolean stock(ServerLevel level, ServerPlayer player) {
		TownCenterBlockEntity town = TownRegistry.nearestOwned(level, player.blockPosition(), player.getUUID(),
				TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			player.sendSystemMessage(Component.translatable("message.warbands.raid_command_no_town").withStyle(ChatFormatting.RED));
			return false;
		}
		fill(level, town.getBlockPos());
		return true;
	}

	/** Full health and hunger, no fire, no effects. */
	public static void heal(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20.0F);
		player.clearFire();
		player.removeAllEffects();
	}

	private static MutableComponent button(Component label, String command, ChatFormatting color, Component hover) {
		return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	/** Two of each soldier (one ram) in front of the player, in their army. */
	private static void army(ServerLevel level, ServerPlayer player, BlockPos where) {
		int i = 0;
		for (UnitType type : UnitType.values()) {
			for (int n = 0; n < (type == UnitType.RAM ? 1 : 2); n++, i++) {
				SoldierEntity soldier = type.entityType().create(level, EntitySpawnReason.MOB_SUMMONED);
				if (soldier == null) {
					continue;
				}
				BlockPos spot = ground(level, where.offset(i % 6 * 2 - 5, 0, i / 6 * 2)).above();
				soldier.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0.0F);
				soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.MOB_SUMMONED, null);
				soldier.assignOwner(player.getUUID(), player.getName().getString());
				level.addFreshEntity(soldier);
			}
		}
	}

	/** Pays for the age with its item and runs the research to the end at once. */
	private static void advance(ServerLevel level, BlockPos core, ServerPlayer player, Age age) {
		if (!(level.getBlockEntity(core) instanceof TownCenterBlockEntity town) || town.age().atLeast(age)
				|| !town.tryAdvance(player, new SimpleContainer(new ItemStack(age.requirement())))) {
			return;
		}
		for (int i = 0; i < age.researchTicks() && level.getBlockEntity(core) instanceof TownCenterBlockEntity current; i++) {
			current.serverTick(level, core, level.getBlockState(core));
		}
	}

	private static void fill(ServerLevel level, BlockPos core) {
		if (level.getBlockEntity(core) instanceof TownCenterBlockEntity town) {
			for (Resource resource : Resource.values()) {
				town.addStock(resource, TownCenterBlockEntity.MAX_STOCK);
			}
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static BlockPos at(BlockPos start, Direction ahead, int right, int forward) {
		return start.relative(ahead, forward).relative(ahead.getClockWise(), right);
	}

	/** Where a building's floor goes: the median real ground across its footprint (a tree or pit doesn't count). */
	private static BlockPos site(ServerLevel level, BlockPos pos, Blueprint blueprint) {
		int y = Ground.footprint(level, pos, blueprint.half());
		return new BlockPos(pos.getX(), y == Integer.MIN_VALUE ? Ground.surface(level, pos.getX(), pos.getZ()) : y, pos.getZ());
	}

	private static BlockPos ground(ServerLevel level, BlockPos pos) {
		return new BlockPos(pos.getX(), Ground.surface(level, pos.getX(), pos.getZ()), pos.getZ());
	}
}
