package dev.felipe.warbands.entity;

import dev.felipe.warbands.WarbandsAdvancements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * What a warlord leaves behind, by camp kind: the Keep's Vorak the Warlord's Brand and a piece of the Plate, each
 * mini-boss a lesser signature weapon of their own (never netherite), and all of them a small bonus pool priced like
 * a camp chest. Every drop is a vanilla item with a name, model and enchants, so nothing needs a new item class.
 */
final class WarlordSpoils {
	private static final String[][] PLATE = {
			{"netherite_helmet", "warlord_helm"}, {"netherite_chestplate", "warlord_cuirass"},
			{"netherite_leggings", "warlord_greaves"}, {"netherite_boots", "warlord_sabatons"}};

	private WarlordSpoils() {
	}

	/** The signature weapon for a camp kind ({@code SoldierEntity.campKind()}), or {@code brand} for the Keep. */
	static ItemStack signature(ServerLevel level, String kind, ItemStack brand) {
		return switch (kind) {
			case "illager_hall" -> named(level, Items.DIAMOND_AXE, "brakk_cleaver", 0xE8602A,
					Enchantments.SHARPNESS, 4, Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 2);
			case "cavalry_encampment" -> named(level, Items.BOW, "arha_warbow", 0xC8402A,
					Enchantments.POWER, 4, Enchantments.PUNCH, 2, Enchantments.UNBREAKING, 3);
			case "sunken_crypt" -> named(level, Items.DIAMOND_SWORD, "mos_gravebane", 0x3DE0D0,
					Enchantments.SMITE, 5, Enchantments.UNBREAKING, 2, null, 0);
			default -> brand;
		};
	}

	/** The advancement for a camp kind's own warlord, or null. */
	static @Nullable String advancement(String kind) {
		return switch (kind) {
			case "illager_hall" -> WarbandsAdvancements.WARLORD_BRAKK;
			case "cavalry_encampment" -> WarbandsAdvancements.WARLORD_ARHA;
			case "sunken_crypt" -> WarbandsAdvancements.WARLORD_MOS;
			default -> null;
		};
	}

	/** One random Warlord's Plate piece (the Keep boss only). */
	static ItemStack platePiece(ServerLevel level, RandomSource random) {
		String[] piece = PLATE[random.nextInt(PLATE.length)];
		Item base = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(piece[0]));
		ItemStack stack = new ItemStack(base);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("warbands", piece[1]));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.warbands." + piece[1])
				.withStyle(style -> style.withItalic(false).withColor(0xE8B84B)));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
		stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
		if (piece[0].endsWith("boots")) {
			stack.enchant(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), 4);
		}
		return stack;
	}

	/** Emeralds, XP bottles and a book for every warlord; the Keep's boss adds netherite scrap and more XP. */
	static void bonus(WarlordEntity warlord, ServerLevel level, boolean keep) {
		RandomSource random = warlord.getRandom();
		warlord.spawnAtLocation(level, new ItemStack(Items.EMERALD, 2 + random.nextInt(3)));
		warlord.spawnAtLocation(level, new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3)));
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		boolean mending = random.nextBoolean();
		book.enchant(enchantments.getOrThrow(mending ? Enchantments.MENDING : Enchantments.UNBREAKING), mending ? 1 : 3);
		warlord.spawnAtLocation(level, book);
		if (keep) {
			warlord.spawnAtLocation(level, new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2)));
		}
		ExperienceOrb.award(level, warlord.position(), keep ? 120 : 60);
	}

	private static ItemStack named(ServerLevel level, Item base, String id, int color, ResourceKey<Enchantment> a, int la,
			ResourceKey<Enchantment> b, int lb, @Nullable ResourceKey<Enchantment> c, int lc) {
		ItemStack stack = new ItemStack(base);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("warbands", id));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.warbands." + id)
				.withStyle(style -> style.withItalic(false).withColor(color)));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(enchantments.getOrThrow(a), la);
		stack.enchant(enchantments.getOrThrow(b), lb);
		if (c != null) {
			stack.enchant(enchantments.getOrThrow(c), lc);
		}
		return stack;
	}
}
