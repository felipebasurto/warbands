"""Turns the numbered frames from tools/portraits (?anim=dance|turn|gallop) into transparent loops.

Run from this folder:  python3 make_anims.py      (needs Pillow)
Reads ../publish/portraits/anim-<unit>-<mode>-NN.png, writes ../publish/animations/<unit>-<mode>.gif (1-bit transparency,
works everywhere) and .webp (full alpha, smoother edges). 20 fps, cropped to the loop's own bounding box.
"""
import glob
import os
import re
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'publish', 'portraits')
OUT = os.path.join(HERE, '..', 'publish', 'animations')
os.makedirs(OUT, exist_ok=True)
HEIGHT = 520

groups = {}
for f in sorted(glob.glob(os.path.join(SRC, 'anim-*.png'))):
	m = re.match(r'anim-(.+)-(dance|turn|gallop)-(\d+)\.png', os.path.basename(f))
	groups.setdefault((m.group(1), m.group(2)), []).append(f)

for (unit, mode), files in groups.items():
	frames = [Image.open(f).convert('RGBA') for f in files]
	left = min(f.getbbox()[0] for f in frames); top = min(f.getbbox()[1] for f in frames)
	right = max(f.getbbox()[2] for f in frames); bottom = max(f.getbbox()[3] for f in frames)
	pad = 16
	box = (max(0, left - pad), max(0, top - pad), min(frames[0].width, right + pad), min(frames[0].height, bottom + pad))
	scale = HEIGHT / (box[3] - box[1])
	size = (int((box[2] - box[0]) * scale), HEIGHT)
	cropped = [f.crop(box).resize(size, Image.Resampling.LANCZOS) for f in frames]
	gif_frames = []
	for f in cropped:
		alpha = f.getchannel('A').point(lambda a: 255 if a > 140 else 0)
		p = f.convert('RGB').quantize(255, dither=Image.Dither.NONE)
		p.paste(255, mask=alpha.point(lambda a: 255 - a))
		p.info['transparency'] = 255
		gif_frames.append(p)
	gif_frames[0].save(os.path.join(OUT, f'{unit}-{mode}.gif'), save_all=True, append_images=gif_frames[1:], duration=50, loop=0, disposal=2, transparency=255)
	cropped[0].save(os.path.join(OUT, f'{unit}-{mode}.webp'), save_all=True, append_images=cropped[1:], duration=50, loop=0, lossless=True, method=4)
	print(unit, mode, len(frames), 'frames', size)


# ---- the whole army dancing: one wide loop, on a dark gradient and transparent ----
def party():
	order = ['militia', 'spearman', 'archer', 'crossbowman', 'knight', 'warlord']
	per_unit = []
	for unit in order:
		frames = [Image.open(f).convert('RGBA') for f in groups[(unit, 'dance')]]
		box = (min(f.getbbox()[0] for f in frames), min(f.getbbox()[1] for f in frames), max(f.getbbox()[2] for f in frames), max(f.getbbox()[3] for f in frames))
		h = 430
		s = h / (box[3] - box[1])
		per_unit.append([f.crop(box).resize((int((box[2] - box[0]) * s), h), Image.Resampling.LANCZOS) for f in frames])
	width = sum(u[0].width for u in per_unit) - 60 * (len(per_unit) - 1)
	height = 470
	count = min(len(u) for u in per_unit)
	out_dark, out_alpha = [], []
	bg = Image.new('RGB', (width + 80, height))
	for y in range(height):
		for x in (0,):
			pass
	from PIL import ImageDraw
	d = ImageDraw.Draw(bg)
	for y in range(height):
		d.line([(0, y), (width + 80, y)], fill=(30 + int(y * 0.03), 38 + int(y * 0.04), 58 + int(y * 0.06)))
	for i in range(count):
		clear = Image.new('RGBA', (width + 80, height), (0, 0, 0, 0))
		x = 40
		for k, u in enumerate(per_unit):
			f = u[i]
			clear.alpha_composite(f, (x, height - f.height - 20))
			x += f.width - 60
		out_alpha.append(clear)
		dark = bg.convert('RGBA'); dark.alpha_composite(clear)
		out_dark.append(dark.convert('RGB'))
	out_dark[0].save(os.path.join(OUT, 'army-dance-bg.gif'), save_all=True, append_images=out_dark[1:], duration=50, loop=0, optimize=False)
	frames_p = []
	for f in out_alpha:
		alpha = f.getchannel('A').point(lambda a: 255 if a > 140 else 0)
		p = f.convert('RGB').quantize(255, dither=Image.Dither.NONE)
		p.paste(255, mask=alpha.point(lambda a: 255 - a)); p.info['transparency'] = 255
		frames_p.append(p)
	frames_p[0].save(os.path.join(OUT, 'army-dance.gif'), save_all=True, append_images=frames_p[1:], duration=50, loop=0, disposal=2, transparency=255)
	out_alpha[0].save(os.path.join(OUT, 'army-dance.webp'), save_all=True, append_images=out_alpha[1:], duration=50, loop=0, lossless=True, method=4)
	print('army dance', count, 'frames', out_alpha[0].size)


if all((u, 'dance') in groups for u in ['militia', 'spearman', 'archer', 'crossbowman', 'knight', 'warlord']):
	party()
