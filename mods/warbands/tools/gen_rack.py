"""Weapon rack texture: vanilla dark oak planks as the board (real grain and shading), then hand-placed weapons with
a dark outline and three tones each. Needs Pillow and the client jar (path as argv[1]); the output is committed, so this
only runs when the rack changes. Lesson from the flat generated textures: start from a vanilla texture, add outlines."""
import io, sys, zipfile
from PIL import Image, ImageEnhance

jar = sys.argv[1] if len(sys.argv) > 1 else '/Users/felipe/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar'
with zipfile.ZipFile(jar) as z:
    planks = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/dark_oak_planks.png'))).convert('RGBA')
board = ImageEnhance.Brightness(planks).enhance(0.8)
rail = ImageEnhance.Brightness(planks).enhance(1.55)
out = board.copy()
px = out.load()
rp = rail.load()
K = (30, 26, 32, 255)
C = {'K': K, 'L': (236, 240, 243, 255), 'M': (176, 185, 192, 255), 'D': (112, 121, 130, 255),
     'G': (238, 196, 70, 255), 'g': (150, 112, 28, 255), 'B': (122, 80, 44, 255), 'b': (74, 46, 26, 255),
     'R': (170, 60, 50, 255)}
for y in (5, 6, 11, 12):  # the rails the 3D model carries
    for x in range(16):
        px[x, y] = rp[x, y]
for x in range(16):
    px[x, 7] = tuple(int(c * 0.6) for c in px[x, 7][:3]) + (255,)
    px[x, 13] = tuple(int(c * 0.6) for c in px[x, 13][:3]) + (255,)
def put(grid, x, y, ch):
    grid[y][x] = ch


grid = [['.'] * 16 for _ in range(16)]
sword = [
    ".KLK..",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "KLMDK.",
    "GGGGgK",
    "KggKKK",
    ".KBbK.",
    ".KBbK.",
    ".KRbK.",
    ".KGgK.",
    "..KK..",
]
axe = [  # cols 6..11: head on the left of the haft
    "......",
    ".KKK..",
    "KLLDK.",
    "KLMDKK",
    "KLMDBb",
    "KMDDKK",
    ".KKK..",
    "...KBb",
    "....Bb",
    "....Bb",
    "....Bb",
    "....Bb",
    "....Bb",
    "....Bb",
    "....Bb",
    "....KK",
]
spear = [  # cols 12..15
    "..KK",
    ".KLK",
    "KLMK",
    "KMDK",
    ".KDK",
    "..KK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KBK",
    ".KbK",
    "..KK",
]
for x0, art_part in ((0, sword), (6, axe), (12, spear)):
    for y, row in enumerate(art_part):
        for x, ch in enumerate(row):
            if ch != '.':
                put(grid, x0 + x, y, ch)
art = [''.join(r) for r in grid]
for y, row in enumerate(art):
    for x, ch in enumerate(row):
        if ch in C:
            px[x, y] = C[ch]
out.save('../src/main/resources/assets/warbands/textures/block/weapon_rack.png')
out.resize((256, 256), Image.NEAREST).save(sys.argv[2] if len(sys.argv) > 2 else '/tmp/rack_preview.png')
