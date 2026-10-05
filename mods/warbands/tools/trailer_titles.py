"""Draws the trailer's title cards and captions with Minecraft's own pixel font (ascii.png from the game jar), in the
gold-with-dark-outline style of the Warbands logo.

Run from this folder:  python3 trailer_titles.py      (needs Pillow; ascii.png in ../publish/render3d/tex/)
Writes cap-war.png, cap-melee.png, cap-assault.png (transparent overlays), card-open.png and card-end.png in ../publish/trailer/.
Then run make_trailer.sh to cut the video.
"""
import os
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLISH = os.path.join(HERE, '..', 'publish')
OUT = os.path.join(PUBLISH, 'trailer')
ATLAS = Image.open(os.path.join(PUBLISH, 'render3d', 'tex', 'ascii.png')).convert('RGBA')
os.makedirs(OUT, exist_ok=True)


def glyph(ch):
	code = ord(ch)
	if ch == ' ':
		return Image.new('L', (4, 8), 0)
	cell = ATLAS.crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8)).split()[3]
	box = cell.getbbox()
	width = box[2] if box else 4
	return cell.crop((0, 0, width, 8))


def mask(text):
	glyphs = [glyph(c) for c in text]
	width = sum(g.width + 1 for g in glyphs) - 1
	m = Image.new('L', (width, 8), 0)
	x = 0
	for g in glyphs:
		m.paste(g, (x, 0))
		x += g.width + 1
	return m


def draw(canvas, text, centre_x, y, scale, top=(255, 226, 120), bottom=(222, 150, 20), shadow=(74, 40, 4), outline=(28, 14, 2), bold=False):
	"""Pixel text: outline, hard drop shadow and a vertical gold gradient, all on the 1x pixel grid."""
	m = mask(text)
	if bold:  # the game's bold is the glyph drawn twice, one pixel apart
		wide = Image.new('L', (m.width + 1, 8), 0)
		wide.paste(m, (0, 0))
		wide.paste(m, (1, 0), m)
		m = wide
	big = m.resize((m.width * scale, m.height * scale), Image.Resampling.NEAREST)
	x = int(centre_x - big.width / 2)
	grow = max(1, scale // 2)
	edge = big.filter(ImageFilter.MaxFilter(grow * 2 + 1))
	canvas.paste(Image.new('RGBA', edge.size, outline + (255,)), (x, y), edge)
	canvas.paste(Image.new('RGBA', big.size, shadow + (255,)), (x + scale, y + scale), big)
	gradient = Image.new('RGBA', big.size)
	g = ImageDraw.Draw(gradient)
	for row in range(big.height):
		t = row / max(1, big.height - 1)
		g.line([(0, row), (big.width, row)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
	canvas.paste(gradient, (x, y), big)
	return big.width


def plain(canvas, text, centre_x, y, scale):
	draw(canvas, text, centre_x, y, scale, top=(255, 255, 255), bottom=(205, 205, 205), shadow=(40, 40, 40), outline=(0, 0, 0), bold=False)


def flourish(canvas, centre_x, y, half, scale=4):
	"""A gold rule with a diamond in the middle, as on the logo's banner."""
	d = ImageDraw.Draw(canvas)
	for side in (-1, 1):
		x0 = centre_x + side * (4 * scale)
		x1 = centre_x + side * half
		d.rectangle([min(x0, x1), y, max(x0, x1), y + scale - 1], fill=(222, 150, 20, 255))
		d.rectangle([min(x0, x1), y + scale, max(x0, x1), y + scale], fill=(74, 40, 4, 255))
	pts = [(centre_x, y - 2 * scale), (centre_x + 3 * scale, y + scale // 2), (centre_x, y + 3 * scale), (centre_x - 3 * scale, y + scale // 2)]
	d.polygon(pts, fill=(255, 226, 120, 255), outline=(28, 14, 2, 255))


def caption(title, sub, name):
	im = Image.new('RGBA', (1920, 1080), (0, 0, 0, 0))
	d = ImageDraw.Draw(im)
	for y in range(700, 1080):
		d.line([(0, y), (1920, y)], fill=(0, 0, 0, int(190 * ((y - 700) / 380))))
	draw(im, title, 960, 800, 14)
	flourish(im, 960, 944, 360)
	plain(im, sub, 960, 980, 4)
	im.save(os.path.join(OUT, name + '.png'))


def card(line, name):
	bg = Image.new('RGBA', (1920, 1080))
	d = ImageDraw.Draw(bg)
	for y in range(1080):
		d.line([(0, y), (1920, y)], fill=(26 + int(y * 0.02), 32 + int(y * 0.025), 48 + int(y * 0.04), 255))
	logo = Image.open(os.path.join(PUBLISH, 'logo.png')).convert('RGBA')
	logo.thumbnail((1500, 520), Image.Resampling.LANCZOS)
	bg.alpha_composite(logo, ((1920 - logo.width) // 2, 280))
	flourish(bg, 960, 800, 420)
	plain(bg, line, 960, 850, 6)
	bg.convert('RGB').save(os.path.join(OUT, name + '.png'))


if __name__ == '__main__':
	caption('RAISE AN ARMY', 'Train militia, archers, spearmen, crossbowmen and knights.', 'cap-war')
	caption('FIGHT', 'Real melee, with archers and battering rams.', 'cap-melee')
	caption('CONQUER', 'Take guarded camps to unlock the next age.', 'cap-assault')
	card('A medieval war mod for Minecraft', 'card-open')
	card('A Fabric mod for Minecraft Java Edition', 'card-end')
	print('wrote titles to', OUT)
