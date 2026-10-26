package dev.felipe.warbands.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/**
 * The warning boss bar is kept in memory and built again from the raid kind after a reload.
 * No world is started: the boss-bar packet and the HUD are not covered here.
 */
class RaidWarningBarTest {
	@Test
	void warningTitleFillsTheKindPlaceholder() throws IOException {
		for (String code : List.of("en_us", "es_es")) {
			Map<String, String> lang = lang(code);
			String template = lang.get("bossbar.warbands.raid_warning");
			Language previous = Language.getInstance();
			Language.inject(new MapLanguage(lang));
			try {
				// A title built without the kind keeps the format string, which is what a reloaded bar used to show.
				assertEquals(template, Component.translatable("bossbar.warbands.raid_warning").getString(), code);
				assertTrue(template.contains("%s"), code);
				for (RaidKind kind : RaidKind.values()) {
					String kindName = lang.get("raid.warbands." + kind.serializedName());
					String shown = Raids.warningBarName(kind).getString();
					assertEquals(template.replace("%s", kindName), shown, code + " " + kind);
					assertFalse(shown.contains("%s"), shown);
				}
			} finally {
				Language.inject(previous);
			}
		}
	}

	private static Map<String, String> lang(String code) throws IOException {
		Map<String, String> translations = new HashMap<>();
		try (InputStream in = RaidWarningBarTest.class.getClassLoader()
				.getResourceAsStream("assets/warbands/lang/" + code + ".json")) {
			Language.loadFromJson(in, translations::put);
		}
		return translations;
	}

	private static final class MapLanguage extends Language {
		private final Map<String, String> translations;

		private MapLanguage(Map<String, String> translations) {
			this.translations = translations;
		}

		@Override
		public String getOrDefault(String key, String fallback) {
			return translations.getOrDefault(key, fallback);
		}

		@Override
		public boolean has(String key) {
			return translations.containsKey(key);
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return false;
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			throw new UnsupportedOperationException();
		}
	}
}
