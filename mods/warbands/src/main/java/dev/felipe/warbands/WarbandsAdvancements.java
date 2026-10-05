package dev.felipe.warbands;

import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the "Warbands" advancement tab steps that can't be detected by vanilla triggers.
 * Those advancements use a single "minecraft:impossible" criterion named "done".
 */
public final class WarbandsAdvancements {
	public static final String FOUND_TOWN = "found_town";
	public static final String STOCKPILE = "stockpile";
	public static final String FIRST_RECRUIT = "first_recruit";
	public static final String HOUSING = "housing";
	public static final String ARCHER = "archer";
	public static final String WARBAND = "warband";
	public static final String METROPOLIS = "metropolis";
	public static final String FEUDAL_AGE = "feudal_age";
	public static final String CASTLE_AGE = "castle_age";
	public static final String BARRACKS = "barracks";
	public static final String SPEARMAN = "spearman";
	public static final String ARCHERY_RANGE = "archery_range";
	public static final String CROSSBOWMAN = "crossbowman";
	public static final String BLACKSMITH = "blacksmith";
	public static final String FORGING = "forging";
	public static final String FLETCHING = "fletching";
	public static final String SCALE_ARMOR = "scale_armor";
	public static final String STABLE = "stable";
	public static final String KNIGHT = "knight";
	public static final String WATCH_TOWER = "watch_tower";
	public static final String GARRISON = "garrison";
	public static final String SIEGE_WORKSHOP = "siege_workshop";
	public static final String RAM = "ram";
	public static final String RAID_DEFENDED = "raid_defended";
	public static final String REPAIR = "repair";
	public static final String WAR_MAP = "war_map_bought";
	public static final String CAMP_BANDIT = "camp_bandit";
	public static final String CAMP_FORT = "camp_fort";
	public static final String CAMP_KEEP = "camp_keep";
	public static final String CAMP_POACHERS_LODGE = "camp_poachers_lodge";
	public static final String CAMP_SMUGGLERS_DOCK = "camp_smugglers_dock";
	public static final String CAMP_QUARRY_STRONGHOLD = "camp_quarry_stronghold";
	public static final String CAMP_WITCH_COVEN = "camp_witch_coven";
	public static final String CAMP_FROZEN_OUTPOST = "camp_frozen_outpost";
	public static final String CAMP_CAVALRY_ENCAMPMENT = "camp_cavalry_encampment";
	public static final String CAMP_SUNKEN_CRYPT = "camp_sunken_crypt";
	public static final String CAMP_ILLAGER_HALL = "camp_illager_hall";
	public static final String WARLORD_SLAIN = "warlord_slain";
	public static final String WARLORD_BRAKK = "warlord_brakk";
	public static final String WARLORD_ARHA = "warlord_arha";
	public static final String WARLORD_MOS = "warlord_mos";
	public static final String RELIC_COLLECTION = "relic_collection";

	private WarbandsAdvancements() {
	}

	public static void award(ServerPlayer player, String name) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(Warbands.id(name));
		if (advancement != null) {
			player.getAdvancements().award(advancement, "done");
		}
	}

	/** Awards {@code owner} if they're online. */
	public static void award(MinecraftServer server, UUID owner, String name) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			award(player, name);
		}
	}
}
