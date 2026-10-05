"""Generates the Warbands logo (procedural, no generative AI): gold block letters with a red war banner
behind them, Minecraft-title style (voxel face, extruded side, dark outline) in the layout of Age of Empires.

Run from this folder:  python3 gen_logo.py      (needs Pillow)
Writes ../publish/logo.png (transparent), ../publish/icon.png (400x400) and ../publish/banner.png (850x205).
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageChops

VAN = os.path.join(os.path.dirname(__file__), 'vanilla')
OUT = os.path.join(os.path.dirname(__file__), '..', 'publish')
os.makedirs(OUT, exist_ok=True)

GLYPHS = {
    'W': ["XX......XX", "XX......XX", "XX......XX", "XX......XX", "XX......XX", "XX......XX", "XX..XX..XX", "XX..XX..XX", "XXX.XX.XXX", "XXXXXXXXXX", "XXXX..XXXX", "XXX....XXX", ".XX....XX."],
    'A': ["..XXXX..", ".XXXXXX.", "XXX..XXX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX"],
    'R': ["XXXXXXX.", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXX.", "XX.XXX..", "XX..XXX.", "XX...XXX", "XX....XX", "XX....XX", "XX....XX"],
    'B': ["XXXXXXX.", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXX.", "XXXXXXX.", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXX."],
    'N': ["XXX...XX", "XXXX..XX", "XXXXX.XX", "XXXXX.XX", "XX.XXXXX", "XX.XXXXX", "XX..XXXX", "XX..XXXX", "XX...XXX", "XX...XXX", "XX....XX", "XX....XX", "XX....XX"],
    'D': ["XXXXXXX.", "XXXXXXXX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XX....XX", "XXXXXXXX", "XXXXXXX."],
    'S': [".XXXXXX.", "XXXXXXXX", "XX....XX", "XX......", "XXXX....", ".XXXXXX.", "..XXXXXX", ".....XXX", "......XX", "XX....XX", "XX....XX", "XXXXXXXX", ".XXXXXX."],
}
ICON_W = ["XX......XX", "XX......XX", "XX......XX", "XX..XX..XX", "XX..XX..XX", "XXX.XX.XXX", "XXXXXXXXXX", "XXXX..XXXX", ".XXX..XXX."]  # the square icon's W (Felipe: perfect)
LOGO_W = GLYPHS['W']
WORD = "WARBANDS"
C = 12           # px per voxel
GAP = 2          # voxels between letters
DEPTH = 12       # extrusion in px (down-right)
PAD = 40


def tex(name):
    return Image.open(os.path.join(VAN, name + '.png')).convert('RGBA').load()


GOLD, WOOL = tex('gold_block'), tex('red_wool')
STYLE = 'classic'   # 'classic' = speckled gold (Felipe's pick); 'vanilla' = tiled gold_block texture


def sample(t, x, y, cell, k=1.0):
    """Pixel of a vanilla 16x16 block texture, stretched over a voxel of `cell` px."""
    r, g, b, _ = t[(x % cell) * 16 // cell, (y % cell) * 16 // cell]
    return (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)


def noise(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF


def word_mask():
    """Voxel grid -> list of (x, y) cells, plus size in cells."""
    cells, cx = [], 0
    for ch in WORD:
        g = GLYPHS[ch]
        for y, row in enumerate(g):
            for x, v in enumerate(row):
                if v == 'X':
                    cells.append((cx + x, y))
        cx += len(g[0]) + GAP
    return cells, cx - GAP, 13


def face_vanilla(cells, w, h, base=None, hi=None, lo=None, seed=1):
    """Letter face: the vanilla gold block texture on every voxel, with a light/dark bevel on the shape's edges."""
    m = mask_of(cells, w, h)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px, mp = img.load(), m.load()
    tl, br = ImageChops.offset(m, 3, 3).load(), ImageChops.offset(m, -3, -3).load()
    for y in range(h):
        for x in range(w):
            if not mp[x, y]:
                continue
            k = 1.18 if not tl[x, y] else (0.8 if not br[x, y] else 1.0)
            px[x, y] = sample(GOLD, x - PAD, y - PAD, C, k)
    return img


def face_classic(cells, w, h, base=(232, 176, 40), hi=(255, 233, 140), lo=(150, 96, 14), seed=1):
    """Gold face: speckled texture in 5px blocks, bevel (light top-left, dark bottom-right) along the shape's edges."""
    m = mask_of(cells, w, h)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px, mp = img.load(), m.load()
    tl, br = ImageChops.offset(m, 3, 3).load(), ImageChops.offset(m, -3, -3).load()
    for y in range(h):
        for x in range(w):
            if not mp[x, y]:
                continue
            j = 0.9 + 0.2 * noise(x // 5, y // 5, seed)
            col = tuple(min(255, int(v * j)) for v in base)
            if noise(x // 5, y // 5, seed + 3) > .9:
                col = tuple(int(v * .8) for v in base)
            if not tl[x, y]:
                col = tuple(min(255, int(v * j)) for v in hi)
            elif not br[x, y]:
                col = tuple(int(v * j) for v in lo)
            px[x, y] = col + (255,)
    return img


def side_px(x, y):
    if STYLE == 'vanilla':
        return sample(GOLD, x - PAD, y - PAD, C, 0.55)
    t = 0.78 + 0.1 * noise(x // 3, y // 3, 5)
    return (int(150 * t), int(92 * t), int(16 * t), 255)


def banner_px(x, y, k, dark=False):
    if STYLE == 'vanilla':
        return sample(WOOL, x, y, 64, 0.5 if dark else k)
    if dark:
        return (96, 14, 14, 255)
    j = k * (0.9 + 0.14 * noise(x // 4, y // 4, 9))
    return (min(255, int(176 * j)), int(28 * j), int(28 * j), 255)


def face(*a, **k):
    return (face_vanilla if STYLE == 'vanilla' else face_classic)(*a, **k)


def mask_of(cells, w, h):
    m = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(m)
    for (cx, cy) in cells:
        d.rectangle([PAD + cx * C, PAD + cy * C, PAD + (cx + 1) * C - 1, PAD + (cy + 1) * C - 1], fill=255)
    return m


def extrude(m, depth):
    out = Image.new('L', m.size, 0)
    for i in range(1, depth + 1):
        out = ImageChops.lighter(out, ImageChops.offset(m, i, i))
    return out


def grow(m, r):
    return m.filter(ImageFilter.MaxFilter(r * 2 + 1))


def build_logo():
    cells, cw, ch = word_mask()
    w, h = cw * C + PAD * 2 + DEPTH, ch * C + PAD * 2 + DEPTH + 120
    H = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    oy = 60  # room above for the banner
    cells = [(x, y) for (x, y) in cells]

    def place(img):
        return ImageChops.offset(img, 0, oy)
    fm = place(mask_of(cells, w, h))
    gold = place(face(cells, w, h, (232, 176, 40), (255, 233, 140), (150, 96, 14)))
    side_m = ImageChops.subtract(extrude(fm, DEPTH), fm)
    side = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    sp = side.load()
    mp = side_m.load()
    for y in range(h):
        for x in range(w):
            if mp[x, y]:
                sp[x, y] = side_px(x, y)

    # red war banner behind the letters (AoE's red numeral, as a Minecraft banner)
    bw = 7 * C * 2 + 20
    bx = (w - bw) // 2 + 6
    ban = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(ban)
    top, bot = 22, oy + PAD + ch * C + 34
    pts = [(bx, top), (bx + bw, top), (bx + bw, bot), (bx + bw // 2, bot - 34), (bx, bot)]
    bd.polygon(pts, fill=(176, 28, 28, 255))
    bp = ban.load()
    for y in range(h):
        for x in range(w):
            if bp[x, y][3]:
                k = 1.18 if x - bx < 10 else (0.72 if bx + bw - x < 10 else 1.0)
                bp[x, y] = banner_px(x, y, k)
    ban_extr = ImageChops.offset(ban, DEPTH // 2, DEPTH // 2)
    dark_ban = ban_extr.copy()
    dp = dark_ban.load()
    for y in range(h):
        for x in range(w):
            if dp[x, y][3]:
                dp[x, y] = banner_px(x, y, 1, True)

    union = ImageChops.lighter(ImageChops.lighter(grow(fm, 0), side_m), dark_ban.split()[3])
    union = ImageChops.lighter(union, ban.split()[3])
    outline = grow(union, 4)
    H.paste((38, 20, 6, 255), mask=outline)
    H.alpha_composite(dark_ban)
    H.alpha_composite(ban)
    # banner shadow under the word so the gold pops
    sh = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    sh.paste((70, 8, 8, 255), mask=grow(ImageChops.offset(fm, 4, 4), 1).filter(ImageFilter.GaussianBlur(2)))
    cut = ImageChops.lighter(fm, side_m)
    H.alpha_composite(Image.composite(sh, Image.new('RGBA', (w, h), (0, 0, 0, 0)), ban.split()[3]))
    H.alpha_composite(side)
    H.alpha_composite(gold)
    # outline around the gold word itself, so it separates from the banner
    ring = ImageChops.subtract(grow(ImageChops.lighter(fm, side_m), 3), ImageChops.lighter(fm, side_m))
    H.paste((38, 20, 6, 255), mask=ring)
    H.alpha_composite(side)
    H.alpha_composite(gold)

    # AoE-style tagline rule + "of" -> small banner caption lines
    d = ImageDraw.Draw(H)
    ly = oy + PAD + ch * C + DEPTH + 22
    mid = w // 2
    for (a, b) in ((PAD + 10, mid - 12 * C), (mid + 12 * C, w - PAD - 10)):
        d.rectangle([a - 2, ly - 2, b + 2, ly + 5], fill=(38, 20, 6, 255))
        d.rectangle([a, ly, b, ly + 2], fill=(236, 186, 60, 255))
        d.rectangle([a, ly + 3, b, ly + 3], fill=(150, 96, 14, 255))
    return H.crop(H.getbbox())


def build_icon(size=400):
    """Red banner with the gold W on a dark stone-like square."""
    global WORD, C, DEPTH, PAD
    old = (WORD, C, DEPTH, PAD)
    WORD, C, DEPTH, PAD = "W", 22, 14, 20
    GLYPHS['W'] = ICON_W
    cells, cw, ch = word_mask()
    w = h = 400
    img = Image.new('RGBA', (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            k = 0.85 + 0.3 * noise(x // 8, y // 8, 21)
            px[x, y] = (int(52 * k), int(46 * k), int(44 * k), 255)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(30, 18, 6, 255), width=8)
    bx0, bx1, top, bot = 100, 300, 22, 380
    d.polygon([(bx0 + 8, top + 8), (bx1 + 8, top + 8), (bx1 + 8, bot + 8), (208, bot - 42), (bx0 + 8, bot + 8)], fill=(96, 14, 14, 255))
    ban = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(ban).polygon([(bx0, top), (bx1, top), (bx1, bot), (200, bot - 50), (bx0, bot)], fill=(176, 28, 28, 255), outline=(38, 20, 6, 255), width=6)
    img.alpha_composite(ban)
    ox = (w - cw * C) // 2 - PAD
    oy = 70
    fm = ImageChops.offset(mask_of(cells, w, h), ox, oy)
    gold = ImageChops.offset(face(cells, w, h, (232, 176, 40), (255, 233, 140), (150, 96, 14)), ox, oy)
    side_m = ImageChops.subtract(extrude(fm, DEPTH), fm)
    ring = ImageChops.subtract(grow(ImageChops.lighter(fm, side_m), 4), ImageChops.lighter(fm, side_m))
    img.paste((38, 20, 6, 255), mask=ring)
    img.paste((150, 92, 16, 255), mask=side_m)
    img.alpha_composite(gold)
    WORD, C, DEPTH, PAD = old
    GLYPHS['W'] = LOGO_W
    return img.resize((size, size), Image.NEAREST)


def build_banner(logo, shot, size=(850, 205)):
    """Wide header: a real in-game screenshot slightly darkened, logo centred on top."""
    bg = Image.open(shot).convert('RGBA')
    bw, bh = size
    bg = bg.resize((bw, int(bg.height * bw / bg.width)), Image.LANCZOS)
    top = max(0, (bg.height - bh) // 2)
    bg = bg.crop((0, top, bw, top + bh))
    bg.alpha_composite(Image.new('RGBA', size, (0, 0, 0, 70)))
    lg = logo.copy()
    lg.thumbnail((int(bw * 0.72), int(bh * 0.86)), Image.LANCZOS)
    bg.alpha_composite(lg, ((bw - lg.width) // 2, (bh - lg.height) // 2))
    return bg


if __name__ == '__main__':
    import sys
    logo = build_logo()
    logo.save(os.path.join(OUT, 'logo.png'))
    build_icon().save(os.path.join(OUT, 'icon.png'))
    if len(sys.argv) > 1:
        build_banner(logo, sys.argv[1]).save(os.path.join(OUT, 'banner.png'))
    print(logo.size)
