"""Textures for the boss signature drops: Brakk's Cleaver, Arha's Warbow (the vanilla bow's four frames, recoloured)
and Mos's Gravebane. Needs Pillow and the Loom client jar (for the bow frames). Run from tools/."""
import io
import pathlib
import zipfile

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/assets/warbands/textures/item'
JAR = pathlib.Path.home() / '.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar'


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def outline(img, color):
    px = img.load()
    solid = {(x, y) for y in range(16) for x in range(16) if px[x, y][3]}
    for x, y in solid:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid:
                    px[nx, ny] = color


def cleaver():
    """A broad dark-steel axe head with a glowing orange edge, on a leather-wrapped haft."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = img.load()
    for t in range(0, 9):
        p[2 + t, 14 - t] = rgb('5a3a22') if t % 3 else rgb('3a2414')
    p[1, 15] = rgb('a8741a')
    steel, hi, lo, edge, glow = rgb('4a505c'), rgb('9aa3b0'), rgb('2b2e35'), rgb('e8602a'), rgb('ffb347')
    head = {1: (9, 12), 2: (8, 13), 3: (8, 13), 4: (8, 13), 5: (9, 12), 6: (10, 11)}
    for y, (a, b) in head.items():
        for x in range(a, b + 1):
            p[x, y] = steel
    for y in range(1, 6):
        p[head[y][1], y] = edge
        p[head[y][0], y] = lo
    p[9, 2] = hi
    p[10, 3] = hi
    p[13, 3] = glow
    p[11, 7] = rgb('3a2414')
    outline(img, rgb('15151a'))
    img.save(OUT / 'brakk_cleaver.png')


def gravebane():
    """A pale bone-white blade with a soul-blue core and a skull-wrapped hilt."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = img.load()
    bone, bone_hi, bone_lo, soul = rgb('d8d2b8'), rgb('f5f1de'), rgb('8f8a74'), rgb('3de0d0')
    for t in range(5, 15):
        p[t, 14 - t] = bone_hi
        p[t + 1, 15 - t] = bone_lo
        p[t, 15 - t] = soul if 7 <= t <= 12 else bone
    p[14, 0] = rgb('ffffff')
    for k, c in enumerate(['5d5a4c', '8f8a74', 'd8d2b8', '8f8a74', '5d5a4c']):
        p[2 + k, 9 + k] = rgb(c)
    p[4, 12] = rgb('3a2f24')
    p[3, 13] = rgb('2a2018')
    p[2, 13] = rgb('3a2f24')
    p[3, 12] = rgb('2a2018')
    p[1, 14] = soul
    p[2, 15] = rgb('5d5a4c')
    p[0, 14] = rgb('5d5a4c')
    p[1, 15] = rgb('1c8f86')
    outline(img, rgb('15151a'))
    img.save(OUT / 'mos_gravebane.png')


def warbow():
    """The four vanilla bow frames with the wood recoloured to blackened horn with crimson and gold."""
    zf = zipfile.ZipFile(JAR)
    for name in ('bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2'):
        img = Image.open(io.BytesIO(zf.read(f'assets/minecraft/textures/item/{name}.png'))).convert('RGBA')
        p = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = p[x, y]
                if a and r > b + 12 and r >= g:       # the brown wood (the string and the arrow tip are grey/white)
                    v = (r + g + b) / 3 / 255
                    p[x, y] = (int(40 + 150 * v), int(14 + 30 * v), int(18 + 30 * v), a) if v < 0.45 else (
                        int(200 + 55 * v), int(150 + 80 * v - 40), int(40 + 30 * v), a)
        img.save(OUT / f'arha_{name}.png')


if __name__ == '__main__':
    cleaver()
    gravebane()
    warbow()
