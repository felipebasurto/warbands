package dev.felipe.warbands.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Warbands guide, opened by the Handbook item. A parchment page with a picture or item diagram
 * on top and wrapped text below; arrows turn pages. Replaces the vanilla book, which can't show images.
 */
public class GuideScreen extends Screen {
	private static final int WIDTH = 240;
	private static final int HEIGHT = 236;
	private static final int MARGIN = 10;
	private static final int TEXT_WIDTH = WIDTH - 2 * MARGIN;
	private static final int LINE_HEIGHT = 9;
	private static final int ILLUSTRATION_Y = 26;
	private static final int ICON_SPACING = 48;

	private static final int BORDER = 0xFF5A3A22;
	private static final int PAPER = 0xFFE8DCC0;
	private static final int PAPER_EDGE = 0xFFD2C29E;
	private static final int TEXT = 0xFF3A2A1A;
	private static final int TITLE = 0xFF8B1A1A;
	private static final int ARROW = 0xFF6B5030;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_EDGE = 0xFF373737;
	private static final int STAT = 0xFF2E6B1E;

	private int page;
	private Button previous;
	private Button next;

	public GuideScreen() {
		super(Component.translatable("item.warbands.handbook"));
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	private int top() {
		return (this.height - HEIGHT) / 2;
	}

	@Override
	protected void init() {
		int buttonY = top() + HEIGHT - 24;
		this.previous = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
				.bounds(left() + MARGIN, buttonY, 20, 16).build());
		this.next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
				.bounds(left() + WIDTH - MARGIN - 20, buttonY, 20, 16).build());
		updateButtons();
	}

	/** Turns {@code delta} pages. Public so the game test can photograph every page. */
	public void turn(int delta) {
		this.page = Math.max(0, Math.min(Handbook.pages().size() - 1, this.page + delta));
		updateButtons();
	}

	private void updateButtons() {
		this.previous.active = this.page > 0;
		this.next.active = this.page < Handbook.pages().size() - 1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = left();
		int y = top();
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, BORDER);
		graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, PAPER_EDGE);
		graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, PAPER);

		Handbook.Page current = Handbook.pages().get(this.page);
		Component title = current.title().copy().withStyle(ChatFormatting.BOLD);
		graphics.text(this.font, title, x + (WIDTH - this.font.width(title)) / 2, y + 10, TITLE, false);

		int textY = y + ILLUSTRATION_Y;
		for (Handbook.Illustration illustration : current.illustrations()) {
			drawIllustration(graphics, illustration, x + WIDTH / 2, textY);
			textY += illustration.height() + 6;
		}
		textY += 2;
		for (Component line : wrap(this.font, current.body().getString())) {
			graphics.text(this.font, line, x + MARGIN, textY, TEXT, false);
			textY += LINE_HEIGHT;
		}

		Component counter = Component.translatable("guide.warbands.page", this.page + 1, Handbook.pages().size());
		graphics.text(this.font, counter, x + (WIDTH - this.font.width(counter)) / 2, y + HEIGHT - 20, TEXT, false);
	}

	private void drawIllustration(GuiGraphicsExtractor graphics, Handbook.Illustration illustration, int centerX, int y) {
		switch (illustration) {
			case Handbook.Picture picture -> {
				int x = centerX - picture.imageWidth() / 2;
				graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), x, y, 0.0F, 0.0F,
						picture.imageWidth(), picture.imageHeight(), picture.imageWidth(), picture.imageHeight());
				int panels = picture.captions().size();
				for (int i = 0; i < panels; i++) {
					int panelCenter = x + picture.imageWidth() * (2 * i + 1) / (2 * panels);
					drawCentered(graphics, picture.captions().get(i), panelCenter, y + picture.imageHeight() + 3);
				}
			}
			case Handbook.Faces faces -> {
				int size = Handbook.FACE_SIZE;
				int count = faces.skins().size();
				int spacing = size + 40;
				int x = centerX - ((count - 1) * spacing + size) / 2;
				for (int i = 0; i < count; i++) {
					int faceX = x + i * spacing;
					Identifier skin = faces.skins().get(i);
					graphics.fill(faceX - 1, y - 1, faceX + size + 1, y + size + 1, BORDER);
					// Face at (8, 8) and its hat layer at (40, 8) on a 64x64 player skin, each 8x8.
					graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
					graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
					if (i < faces.captions().size()) {
						drawCentered(graphics, faces.captions().get(i), faceX + size / 2, y + size + 3);
					}
				}
			}
			case Handbook.Grid grid -> {
				int gridX = centerX - 50;
				graphics.fill(gridX - 1, y - 1, gridX + 55, y + 55, BORDER);
				for (int i = 0; i < 9; i++) {
					int slotX = gridX + (i % 3) * 18;
					int slotY = y + (i / 3) * 18;
					graphics.fill(slotX, slotY, slotX + 18, slotY + 18, SLOT_EDGE);
					graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, SLOT);
					graphics.item(grid.slots().get(i), slotX + 1, slotY + 1);
				}
				drawArrow(graphics, gridX + 60, gridX + 80, y + 27);
				int resultX = gridX + 86;
				graphics.fill(resultX - 1, y + 17, resultX + 21, y + 39, BORDER);
				graphics.fill(resultX, y + 18, resultX + 20, y + 38, SLOT);
				graphics.item(grid.result(), resultX + 2, y + 20);
			}
			case Handbook.Stats stats -> {
				int columnWidth = TEXT_WIDTH / 2;
				int x = centerX - TEXT_WIDTH / 2 + 12;
				for (int i = 0; i < stats.lines().size(); i++) {
					Handbook.Stat stat = stats.lines().get(i);
					int lineX = x + (i % 2) * columnWidth;
					int lineY = y + (i / 2) * 18;
					if (stat.sprite() != null) {
						graphics.blitSprite(RenderPipelines.GUI_TEXTURED, stat.sprite(), lineX + 3, lineY + 3, 9, 9);
					} else {
						graphics.item(stat.item(), lineX, lineY);
					}
					graphics.text(this.font, stat.text(), lineX + 20, lineY + 4, STAT, false);
				}
			}
			case Handbook.Icons icons -> {
				int count = icons.items().size();
				// Spread icons apart when their captions are wide, so "Archer" and "Crossbowman" don't touch.
				int spacing = ICON_SPACING;
				for (Component caption : icons.captions()) {
					spacing = Math.max(spacing, this.font.width(caption) + 8);
				}
				// ...but never wider than the page: a 5-ingredient recipe spilled past both edges.
				if (count > 1) {
					spacing = Math.min(spacing, (TEXT_WIDTH - 16) / (count - 1));
				}
				int x = centerX - ((count - 1) * spacing + 16) / 2;
				for (int i = 0; i < count; i++) {
					int itemX = x + i * spacing;
					graphics.item(icons.items().get(i), itemX, y);
					boolean arrow = icons.layout() == Handbook.Layout.FLOW
							|| (icons.layout() == Handbook.Layout.RECIPE && i == count - 2);
					if (arrow && i < count - 1) {
						drawArrow(graphics, itemX + 20, itemX + spacing - 4, y + 8);
					}
					if (i < icons.captions().size()) {
						drawCentered(graphics, icons.captions().get(i), itemX + 8, y + 20 + (icons.captions().size() >= 4 && i % 2 == 1 ? 10 : 0));
					}
				}
			}
		}
	}

	private void drawArrow(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
		graphics.fill(x0, y - 1, x1 - 3, y + 1, ARROW);
		for (int i = 0; i < 4; i++) {
			graphics.fill(x1 - 4 + i, y - 4 + i, x1 - 3 + i, y + 4 - i, ARROW);
		}
	}

	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y) {
		int x = Math.max(left() + MARGIN, Math.min(left() + WIDTH - MARGIN - this.font.width(text), centerX - this.font.width(text) / 2));
		graphics.text(this.font, text, x, y, TEXT, false);
	}

	/**
	 * How many lines of {@code body} (in any language) spill past the page counter and the arrows on {@code page}; 0 or
	 * less when it fits. Public so the game test can check every page in English and Spanish.
	 */
	public static int overflow(Font font, Handbook.Page page, String body) {
		int textY = ILLUSTRATION_Y + 2;
		for (Handbook.Illustration illustration : page.illustrations()) {
			textY += illustration.height() + 6;
		}
		return (textY + wrap(font, body).size() * LINE_HEIGHT - (HEIGHT - 25) + LINE_HEIGHT - 1) / LINE_HEIGHT;
	}

	/** Splits text into lines that fit the page. Blank lines ("\n\n") are kept as paragraph gaps. */
	private static List<Component> wrap(Font font, String text) {
		List<Component> lines = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && font.width(Component.literal(candidate)) > TEXT_WIDTH) {
					lines.add(Component.literal(line.toString()));
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(candidate);
				}
			}
			lines.add(Component.literal(line.toString()));
		}
		return lines;
	}
}
