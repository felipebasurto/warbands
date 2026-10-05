"""The loop of the guide's second page as a picture: Build, Stock, Train, Grow, in Minecraft's pixel font.
Run from this folder:  python3 path_infographic.py     Writes ../publish/path.png (1920x560)."""
import os
from PIL import Image, ImageDraw
import trailer_titles as T

HERE = os.path.dirname(os.path.abspath(__file__))
PUB = os.path.join(HERE, '..', 'publish')
MOD_ITEMS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'warbands', 'textures', 'item')
VAN = os.path.join(PUB, 'render3d', 'tex')


def icon(path, size):
	return Image.open(path).convert('RGBA').resize((size, size), Image.Resampling.NEAREST)


steps = [
	('BUILD', ['town_center_blueprint.png'], 'Craft a Town Center blueprint and place it on clear ground.'),
	('STOCK', ['bread.png', 'oak_planks.png', 'stone.png', 'gold_ingot.png'], 'Deposit food, wood, stone and gold at the Town Center.'),
	('TRAIN', ['war_horn.png'], 'Recruit soldiers and lead them with the War Horn.'),
	('GROW', ['war_map.png'], 'Take enemy camps with War Maps to reach the next age.'),
]
W, H = 1920, 560
im = Image.new('RGBA', (W, H))
d = ImageDraw.Draw(im)
for y in range(H):
	d.line([(0, y), (W, y)], fill=(26 + int(y * 0.03), 32 + int(y * 0.04), 48 + int(y * 0.06), 255))
col = W // 4
for i, (title, icons, text) in enumerate(steps):
	cx = col * i + col // 2
	d.rounded_rectangle([cx - 200, 60, cx + 200, 500], radius=18, fill=(40, 46, 64, 255), outline=(222, 150, 20, 255), width=6)
	T.draw(im, f'{i + 1}. {title}', cx, 90, 6)
	size = 128 if len(icons) == 1 else 76
	total = len(icons) * size + (len(icons) - 1) * 12
	x = cx - total // 2
	for name in icons:
		path = os.path.join(MOD_ITEMS, name) if os.path.exists(os.path.join(MOD_ITEMS, name)) else os.path.join(VAN, name)
		im.alpha_composite(icon(path, size), (x, 190 if len(icons) == 1 else 215))
		x += size + 12
	y = 345
	words, line, lines = text.split(' '), '', []
	for w in words:
		trial = (line + ' ' + w).strip()
		if T.mask(trial).width * 3 <= 350 or not line:
			line = trial
		else:
			lines.append(line); line = w
	lines.append(line)
	for l in lines:
		T.plain(im, l, cx, y, 3)
		y += 32
	if i < 3:
		ax = cx + 215
		d.polygon([(ax, 270), (ax + 28, 270), (ax + 28, 250), (ax + 56, 280), (ax + 28, 310), (ax + 28, 290), (ax, 290)], fill=(222, 150, 20, 255), outline=(28, 14, 2, 255))
im.convert('RGB').save(os.path.join(PUB, 'path.png'))
print('wrote path.png')
