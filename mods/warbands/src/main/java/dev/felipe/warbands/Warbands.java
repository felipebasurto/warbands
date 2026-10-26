package dev.felipe.warbands;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlockEntities;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModGameRules;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Traps;
import dev.felipe.warbands.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownCenterBlock;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Warbands implements ModInitializer {
	public static final String MOD_ID = "warbands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModAttachments.initialize();
		ModEntities.initialize();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModMenus.initialize();
		ModItems.initialize();
		ModGameRules.initialize();
		Traps.initialize();
		WarbandsCommands.initialize();
		ServerTickEvents.END_LEVEL_TICK.register(Raids::tick);
		ServerTickEvents.END_LEVEL_TICK.register(Camps::tick);
		ServerTickEvents.END_LEVEL_TICK.register(Relics::tick);

		// No friendly fire between a player, their soldiers and their pets. This camp's own guns and
		// powder kegs also spare its guards; a player's dispenser or TNT still hits them.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (SoldierEntity.isFriendlyFire(victim, source.getEntity())) {
				return false;
			}
			return !Traps.sparesGuard(victim, source);
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> Camps.noteFight(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Camps.guardDied(entity, source));

		// Buildings can't be mined for materials.
		PlayerBlockBreakEvents.BEFORE.register(TownCenterBlock::beforeBreak);
		PlayerBlockBreakEvents.BEFORE.register(StructureBlocks::beforeBreak);

		// Every player gets the handbook once, the first time they join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.player.addTag("warbands.got_handbook")) {
				handler.player.getInventory().add(new ItemStack(ModItems.HANDBOOK));
			}
		});

		// Statics outlive a singleplayer world inside one game session: stale game times would mute the next world's alarms.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Camps.clearState();
			Traps.clearState();
			Raids.clearState();
			dev.felipe.warbands.item.WarHornItem.clearState();
		});

		LOGGER.info("Warbands loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
