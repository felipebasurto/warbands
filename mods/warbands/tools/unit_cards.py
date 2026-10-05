"""Collectible-style unit cards: the 3D portrait, the unit's real costs and where it is trained, in Minecraft's pixel font.

Run from this folder:  python3 unit_cards.py      (needs Pillow, the portraits from tools/portraits and trailer_titles.py's font)
Writes ../publish/cards/<unit>.png (700x1000, transparent corners). Numbers come from UnitType.java; the role lines paraphrase
the in-game handbook. Hearts and armor are left out on purpose: they change with the age and with research.
"""
import os
from PIL import Image, ImageDraw
import trailer_titles as T

HERE = os.path.dirname(os.path.abspath(__file__))
PUB = os.path.join(HERE, '..', 'publish')
TEX = os.path.join(PUB, 'render3d', 'tex')
OUT = os.path.join(PUB, 'cards')
os.makedirs(OUT, exist_ok=True)

# name, portrait file, (food, wood, gold), seconds, age, building, role
UNITS = [
	('Militia', 'militia-guard', (60, 0, 20), 25, 'Village', 'Town Center', 'Sword and shield. Fights up close.'),
	('Archer', 'archer-aim', (0, 25, 45), 30, 'Village', 'Town Center', 'Shoots from range.'),
	('Spearman', 'spearman-guard', (40, 20, 15), 22, 'Feudal', 'Barracks', 'Hits like a Militia, plus 4 against horses and their riders.'),
	('Crossbowman', 'crossbowman-aim', (0, 40, 60), 35, 'Feudal', 'Archery Range', 'Farther shots and harder bolts, but a slow reload.'),
	('Knight', 'knight-mounted-idle', (60, 0, 80), 40, 'Castle', 'Stable', 'Your strongest unit, on an armored warhorse.'),
	('Battering Ram', 'ram-front', (0, 100, 50), 50, 'Castle', 'Siege Workshop', 'Breaks doors, gates and fences when ordered to attack.'),
]
AGE_COLOR = {'Village': (120, 200, 90), 'Feudal': (110, 160, 240), 'Castle': (240, 170, 60)}
ICONS = {i: Image.open(os.path.join(TEX, f)).convert('RGBA') for i, f in enumerate(['bread.png', 'oak_planks.png', 'gold_ingot.png'])}


def wrap(text, scale, max_width):
	words, lines, line = text.split(' '), [], ''
	for w in words:
		trial = (line + ' ' + w).strip()
		if T.mask(trial).width * scale <= max_width or not line:
			line = trial
		else:
			lines.append(line)
			line = w
	lines.append(line)
	return lines


def card(unit):
	name, portrait, cost, seconds, age, building, role = unit
	W, H = 700, 1000
	im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
	d = ImageDraw.Draw(im)
	# stone-coloured frame with a bevel, then the inner panel
	d.rounded_rectangle([0, 0, W - 1, H - 1], radius=26, fill=(28, 14, 2, 255))
	d.rounded_rectangle([8, 8, W - 9, H - 9], radius=20, fill=(222, 150, 20, 255))
	d.rounded_rectangle([16, 16, W - 17, H - 17], radius=16, fill=(40, 46, 64, 255))
	for y in range(24, 600):
		t = (y - 24) / 576
		d.line([(24, y), (W - 25, y)], fill=(int(46 + 30 * t), int(60 + 36 * t), int(92 + 40 * t), 255))
	# title
	T.draw(im, name.upper(), W // 2, 42, 8)
	# portrait
	p = Image.open(os.path.join(PUB, 'portraits', portrait + '.png')).convert('RGBA')
	p = p.crop(p.getbbox())
	s = min(455 / p.height, 600 / p.width)
	p = p.resize((int(p.width * s), int(p.height * s)), Image.Resampling.LANCZOS)
	shadow = Image.new('RGBA', (int(p.width * 0.8), 36), (0, 0, 0, 0))
	ImageDraw.Draw(shadow).ellipse([0, 0, shadow.width - 1, 35], fill=(0, 0, 0, 120))
	im.alpha_composite(shadow, ((W - shadow.width) // 2, 590 - 26))
	im.alpha_composite(p, ((W - p.width) // 2, 590 - p.height))
	# age ribbon
	color = AGE_COLOR[age]
	d.rectangle([24, 606, W - 25, 650], fill=color + (255,))
	T.plain(im, f'{age} Age  -  {building}', W // 2, 616, 4)
	# role
	y = 676
	for line in wrap(role, 4, W - 90):
		T.plain(im, line, W // 2, y, 4)
		y += 44
	# cost row and time
	x = 70
	cy = 830
	for i, amount in enumerate(cost):
		if amount:
			icon = ICONS[i].resize((48, 48), Image.Resampling.NEAREST)
			im.alpha_composite(icon, (x, cy))
			T.plain(im, str(amount), x + 52 + T.mask(str(amount)).width * 2, cy + 8, 4)
			x += 70 + T.mask(str(amount)).width * 4 + 20
	T.plain(im, f'{seconds} s to train', W // 2, 920, 4)
	im.save(os.path.join(OUT, name.lower().replace(' ', '-') + '.png'))


if __name__ == '__main__':
	for u in UNITS:
		card(u)
	print('wrote', len(UNITS), 'cards to', OUT)
