package dev.felipe.warbands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.felipe.warbands.camp.CampLayout;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.camp.CampTestWorld;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.raid.RaidKind;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /warbands raid [kind]}: an operator starts a raid on their nearest Town Center now (to test a defense),
 * or one of a specific kind ({@code /warbands raid night_stalkers}) if the town's age allows it.
 * {@code /warbands camp <kind>}: a free War Map to a camp of that kind (to visit it), any age. It still fails while
 * another camp is out. {@code camp tp} / {@code camp abandon}: go to it, give it up. {@code raid skip}: end the warning.
 * {@code /warbands testworld [list|tp <id>|rebuild <id>]}: every camp layout on a grid to visit ({@link CampTestWorld}).
 * {@code /warbands playtest [kit|help|stock|heal]}: a ready Castle town, army and kit (with the Tester's Guide button
 * panel), a clickable chat menu, a full stockpile, a heal ({@link Playtest}). {@code /warbands home}: back to your town.
 */
public final class WarbandsCommands {
	private WarbandsCommands() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("warbands")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("playtest")
								.executes(context -> Playtest.setUp(context.getSource().getLevel(),
										context.getSource().getPlayerOrException()) ? 1 : 0)
								.then(Commands.literal("kit").executes(context -> {
									Playtest.kit(context.getSource().getPlayerOrException());
									return 1;
								}))
								.then(Commands.literal("help").executes(context -> {
									Playtest.menu(context.getSource().getPlayerOrException());
									return 1;
								}))
								.then(Commands.literal("stock").executes(context -> Playtest.stock(context.getSource().getLevel(),
										context.getSource().getPlayerOrException()) ? 1 : 0))
								.then(Commands.literal("heal").executes(context -> {
									Playtest.heal(context.getSource().getPlayerOrException());
									return 1;
								})))
						.then(Commands.literal("testworld")
								.executes(context -> {
									ServerPlayer player = context.getSource().getPlayerOrException();
									CampTestWorld.setUp(player.level(), player);
									CampTestWorld.list(player.level(), player);
									return 1;
								})
								.then(Commands.literal("list").executes(context -> {
									ServerPlayer player = context.getSource().getPlayerOrException();
									CampTestWorld.list(player.level(), player);
									return 1;
								}))
								.then(Commands.literal("tp").then(Commands.argument("id", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												CampTestWorld.layouts().stream().map(CampLayout::id).toList(), builder))
										.executes(context -> CampTestWorld.tp(context.getSource().getLevel(),
												context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id")) ? 1 : 0)))
								.then(Commands.literal("rebuild").then(Commands.argument("id", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												CampTestWorld.layouts().stream().map(CampLayout::id).toList(), builder))
										.executes(context -> CampTestWorld.rebuild(context.getSource().getLevel(),
												context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id")) ? 1 : 0))))
						.then(Commands.literal("home").executes(context -> Playtest.home(context.getSource().getLevel(),
								context.getSource().getPlayerOrException()) ? 1 : 0))
						.then(Commands.literal("raid").executes(context -> raid(context, null))
								.then(Commands.literal("skip").executes(WarbandsCommands::skip))
								.then(Commands.argument("kind", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(RaidKind.values()).map(RaidKind::serializedName).toList(), builder))
										.executes(context -> raid(context, RaidKind.byName(
												StringArgumentType.getString(context, "kind"))))))
						.then(Commands.literal("camp")
								.then(Commands.literal("tp").executes(context -> Playtest.toCamp(context.getSource().getLevel(),
										context.getSource().getPlayerOrException()) ? 1 : 0))
								.then(Commands.literal("abandon").executes(context -> Camps.abandon(context.getSource().getLevel(),
										context.getSource().getPlayerOrException()) ? 1 : 0))
								.then(Commands.argument("kind", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(CampTier.values()).map(CampTier::serializedName).toList(), builder))
										.executes(context -> camp(context, CampTier.byName(
												StringArgumentType.getString(context, "kind"))))))));
	}

	/** Ends the nearest raid's warning now, so the raiders march at once. */
	private static int skip(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TownCenterBlockEntity town = TownRegistry.nearestOwned(player.level(), player.blockPosition(),
				player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			context.getSource().sendFailure(Component.translatable("message.warbands.raid_command_no_town"));
			return 0;
		}
		Raids.skipWarning(player.level(), town.getBlockPos());
		return 1;
	}

	private static int raid(CommandContext<CommandSourceStack> context, @Nullable RaidKind kind)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TownCenterBlockEntity town = TownRegistry.nearestOwned(player.level(), player.blockPosition(),
				player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			context.getSource().sendFailure(Component.translatable("message.warbands.raid_command_no_town"));
			return 0;
		}
		if (!Raids.startNow(player.level(), town.getBlockPos(), kind)) {
			context.getSource().sendFailure(kind != null && !RaidKind.forAge(town.age()).contains(kind)
					? Component.translatable("message.warbands.raid_command_age", kind.displayName(),
							kind.minAge().displayName())
					: Component.translatable("message.warbands.raid_command_busy"));
			return 0;
		}
		return 1;
	}

	private static int camp(CommandContext<CommandSourceStack> context, @Nullable CampTier kind)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TownCenterBlockEntity town = TownRegistry.nearestOwned(player.level(), player.blockPosition(),
				player.getUUID(), TownCenterBlockEntity.HOUSE_RADIUS);
		if (town == null) {
			context.getSource().sendFailure(Component.translatable("message.warbands.raid_command_no_town"));
			return 0;
		}
		if (kind == null) {
			context.getSource().sendFailure(Component.translatable("message.warbands.camp_command_kind"));
			return 0;
		}
		if (!Camps.buy(player.level(), town, player, kind, false)) {
			context.getSource().sendFailure(Component.translatable("message.warbands.war_map_active"));
			return 0;
		}
		return 1;
	}
}
