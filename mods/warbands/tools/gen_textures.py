"""Generates Warbands pixel-art textures (no dependencies).

Run from this folder:  python3 gen_textures.py
Writes into ../src/main/resources/assets/warbands/textures/. Replace any PNG with hand-made
art at any time; this script is only a starting point.
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'warbands', 'textures')
T = (0, 0, 0, 0)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def noise(x, y, seed=0):
    """Deterministic 0..1 value for texture variation."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF


def jitter(c, x, y, seed=0, amount=0.08):
    return shade(c, 1 - amount + 2 * amount * noise(x, y, seed))


class Img:
    def __init__(self, w, h, fill=T):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c(x, y) if callable(c) else c)

    def save(self, rel):
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = b''.join(b'\x00' + bytes(v for p in row for v in p) for row in self.px)

        def chunk(t, d):
            return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)

        with open(path, 'wb') as f:
            f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
        print('wrote', rel)


# ---------------------------------------------------------------- skins (64x64 player layout)

# Texture offsets (u, v, w, h, d) of each body part: base layer and overlay layer.
PARTS = {
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'right_arm': ((40, 16), (40, 32), (4, 12, 4)),
    'left_arm': ((32, 48), (48, 48), (4, 12, 4)),
    'right_leg': ((0, 16), (0, 32), (4, 12, 4)),
    'left_leg': ((16, 48), (0, 48), (4, 12, 4)),
}


def paint(img, part, fn, overlay=False):
    """fn(face, u, v, w, h) -> color or None (leave transparent)."""
    base, over, (w, h, d) = PARTS[part]
    ox, oy = over if overlay else base
    faces = {
        'top': (ox + d, oy, w, d), 'bottom': (ox + d + w, oy, w, d),
        'right': (ox, oy + d, d, h), 'front': (ox + d, oy + d, w, h),
        'left': (ox + d + w, oy + d, d, h), 'back': (ox + 2 * d + w, oy + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for v in range(fh):
            for u in range(fw):
                c = fn(face, u, v, fw, fh)
                if c is not None:
                    img.set(fx + u, fy + v, c)


def shaded(c, face, u, v, seed, amount=0.07):
    """Base color with noise, lighter on top and darker on the back and sides."""
    f = {'top': 1.1, 'front': 1.0, 'left': 0.9, 'right': 0.9, 'back': 0.85, 'bottom': 0.75}[face]
    return jitter(shade(c, f), u, v, seed, amount)


def face_front(u, v, skin, hair, eyes, beard=None, brows=None):
    brows = brows or shade(hair, 0.8)
    if v <= 1 or (v == 2 and u in (0, 7)):
        return jitter(hair, u, v, 6)
    if v == 3 and u in (1, 2, 5, 6):
        return brows
    if v == 4:
        if u in (1, 6):
            return hexc('f4f4f4')
        if u in (2, 5):
            return eyes
    if v == 5 and u in (3, 4):
        return shade(skin, 0.82 if u == 4 else 0.9)  # nose
    if beard is not None:
        if v == 6 and u in (3, 4):
            return hexc('6b3a2e')
        if v >= 6 or (v == 5 and u in (0, 1, 6, 7)):
            return jitter(beard, u, v, 7, 0.1)
    elif v == 6 and u in (3, 4):
        return hexc('9c5a4a')  # mouth
    if v == 5 and u in (1, 6):
        return shade(skin, 0.95)
    return jitter(skin, u, v, 8, 0.03)


# ---------------------------------------------------------------- 16x16 items

EGG_ROWS = [(6, 9), (5, 10), (4, 11), (4, 11), (3, 12), (3, 12), (3, 12), (2, 13), (2, 13), (2, 13), (2, 13), (3, 12), (3, 12), (4, 11)]


def spawn_egg(name, base, spot, spots):
    """Vanilla-style egg: outline, soft 3D shading, highlight and two-tone spots."""
    img = Img(16, 16)
    top = 1
    last = len(EGG_ROWS) - 1
    for i, (x0, x1) in enumerate(EGG_ROWS):
        y = top + i
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or i in (0, last):
                c = shade(base, 0.45)
            else:
                cx = (x - 7.5) / 6.0
                cy = (i - 6.5) / 7.0
                light = 1.15 - 0.35 * max(0.0, cx + cy)  # lit from the top-left
                c = shade(base, light)
                if x - x0 == 1 and 1 <= i <= 5:
                    c = shade(base, 1.45)
            img.set(x, y, c)
    for (x, y) in spots:
        img.set(x, y, spot)
        img.set(x + 1, y, shade(spot, 0.8))
        img.set(x, y + 1, shade(spot, 0.8))
    img.save(f'item/{name}.png')


def blueprint(name, paper, icon, seal=None):
    """Blueprint scroll: blue paper with grid lines, curled wooden rods, white drawing, optional wax seal."""
    img = Img(16, 16)
    dark, light, grid, line = shade(paper, 0.55), shade(paper, 1.3), shade(paper, 1.12), hexc('e8f1ff')
    rod, rod_dark = hexc('a0763f'), hexc('6b4a2b')
    for y in range(3, 13):
        for x in range(2, 14):
            c = jitter(paper, x, y, 20, 0.04)
            if x % 3 == 0 or y % 3 == 0:
                c = grid
            if x == 13:
                c = shade(paper, 0.8)
            img.set(x, y, c)
    for x in range(1, 15):  # rolled top and bottom with wooden rods
        img.set(x, 2, rod if 1 < x < 14 else rod_dark)
        img.set(x, 13, rod if 1 < x < 14 else rod_dark)
        if 1 < x < 14:
            img.set(x, 3, dark)
            img.set(x, 12, light)
    for (x, y, ch) in icon:
        img.set(x, y, hexc('d33a2c') if ch == 'r' else line)
    if seal:
        for (x, y) in ((11, 11), (12, 11), (11, 12), (12, 12)):
            img.set(x, y, seal if (x + y) % 2 else shade(seal, 0.75))
    img.save(f'item/{name}.png')


def war_horn():
    """A big curved goat horn: iron mouthpiece bottom-left, widening bone body with growth rings, two gold bands,
    a red cord with a tassel, a dark bell opening top-right and a highlight along the top edge."""
    import math
    img = Img(16, 16)
    bone, bone_hi, bone_lo = hexc('efdcae'), hexc('fff4d6'), hexc('a9824f')
    ring_c, gold, gold_lo = hexc('c7a877'), hexc('f2c236'), hexc('a8741a')
    iron, iron_lo, bell = hexc('dfe3e7'), hexc('8c939a'), hexc('3a2a1c')
    cord, cord_lo = hexc('c8262c'), hexc('7a1216')
    # quadratic bezier from the mouthpiece (2,13) through (4,4) to the bell (13,4)
    p0, p1, p2 = (2.5, 13.0), (3.5, 3.5), (13.0, 4.0)
    steps = 60
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        dx = 2 * (1 - t) * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0])
        dy = 2 * (1 - t) * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1])
        n = math.hypot(dx, dy)
        nx, ny = -dy / n, dx / n  # normal: +side is the lower/outer edge
        width = 0.7 + 2.1 * t
        for k in range(-8, 9):
            o = k / 8 * width
            px, py = int(round(x + nx * o)), int(round(y + ny * o))
            if t < 0.1:
                c = iron if o < 0.3 else iron_lo
            elif 0.38 < t < 0.44 or 0.66 < t < 0.72:
                c = gold if o < 0.4 else gold_lo
            elif o < -width * 0.55:
                c = bone_hi
            elif o > width * 0.55:
                c = bone_lo
            else:
                c = ring_c if int(t * 30) % 3 == 0 else bone
            img.set(px, py, c)
    # bell opening
    for (x, y) in ((14, 3), (14, 4), (14, 5), (13, 2), (14, 6)):
        img.set(x, y, bone_lo)
    for (x, y) in ((13, 3), (13, 4), (13, 5)):
        img.set(x, y, bell)
    # red cord hanging from the first gold band, with a tassel
    for (x, y) in ((5, 8), (6, 9), (6, 10), (7, 11), (7, 12)):
        img.set(x, y, cord)
    for (x, y) in ((6, 13), (7, 13), (8, 13), (6, 14), (7, 14), (8, 14), (7, 15)):
        img.set(x, y, cord if (x + y) % 2 else cord_lo)
    img.save('item/war_horn.png')


def trophy(name, cloth, trim, emblem, pole_top, emblem_color=None):
    """A captured camp standard: a crossbar on a pole with a swallow-tailed banner, a trim border and an emblem."""
    img = Img(16, 16)
    wood, wood_lo = hexc('6b4a2b'), hexc('3e2a17')
    for y in range(2, 16):  # pole
        img.set(7, y, wood)
        img.set(8, y, wood_lo)
    for x in range(3, 13):  # crossbar
        img.set(x, 2, wood)
        img.set(x, 3, wood_lo)
    img.set(7, 1, pole_top)
    img.set(8, 1, pole_top)
    img.set(7, 0, shade(pole_top, 1.2))
    for y in range(4, 13):  # banner, swallow tail at the bottom
        for x in range(4, 12):
            if y >= 11 and 6 < x < 9 + (y - 11):
                continue
            if y == 12 and x in (6, 9):
                continue
            edge = x in (4, 11) or y == 4
            img.set(x, y, trim if edge else jitter(cloth, x, y, 3, 0.1))
    for (x, y) in emblem:
        img.set(x, y, emblem_color or trim)
    img.save(f'item/{name}.png')


def ram_icon():
    """Battering Ram unit icon (menus, advancement, guide): side view of our ram, same palette as its model.
    Hide A-frame roof with stitching, an iron-capped log poking out the front, a wooden deck on two wheels."""
    img = Img(16, 16)
    pal = {
        'h': hexc('8a5a34'), 'H': hexc('5c3a20'), 's': hexc('c9b08a'),   # hide, hide shadow, stitch
        'p': hexc('4f3319'),                                             # posts
        'l': hexc('7a5634'), 'L': hexc('4a3320'), 'r': hexc('b89160'),   # log bark, bark shadow, cut ring
        'i': hexc('9aa1a9'), 'I': hexc('5f666e'), 'v': hexc('d4d9de'),   # iron cap, iron shadow, rivet
        'd': hexc('a8773f'), 'D': hexc('5e3d1f'),                        # deck, deck shadow
        'w': hexc('3b2918'), 'x': hexc('8a6a40'),                        # wheel rim, hub
    }
    rows = [
        '................',
        '................',
        '.......hs.......',
        '......hhhH......',
        '.....hhshhH.....',
        '....hhhhhshH....',
        '...hshhhhhhhH...',
        '..HHHHHHHHHHHH..',
        '...p........p...',
        'rllllllllllllivv',
        'LLLLLLLLLLLLLIIi',
        '...p........p...',
        '.dddddddddddddD.',
        '.DDwwwDDDDDwwwD.',
        '..wwxww...wwxww.',
        '...www.....www..',
    ]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.set(x, y, pal[ch])
    img.save('item/battering_ram.png')


def camp_blocks():
    """Camp Standard (an enemy camp's core: black war banner on a dark post, red skull) and the Keep's
    Reinforced Gate (dark oak planks under riveted iron bands)."""
    black, black_dark, red, bone = hexc('1c1a1e'), hexc('0e0d10'), hexc('9e1b1f'), hexc('d9cfb4')
    wood, wood_dark = hexc('4a3220'), hexc('2c1d12')
    side = Img(16, 16)
    for y in range(16):
        for x in range(16):
            post = x in (0, 1, 14, 15) or y in (0, 15)
            side.set(x, y, jitter(wood if post else black, x, y, 90, 0.08) if post or (x + y) % 5 else jitter(black_dark, x, y, 91))
    skull = [
        '..####..',
        '.######.',
        '##.##.##',
        '########',
        '.##..##.',
        '..####..',
        '..#..#..',
    ]
    for dy, row in enumerate(skull):
        for dx, ch in enumerate(row):
            if ch == '#':
                side.set(4 + dx, 4 + dy, bone)
    for x in range(3, 13):
        side.set(x, 2, red)
        side.set(x, 13, red)
    side.save('block/camp_standard_side.png')
    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            top.set(x, y, jitter(wood_dark if int(ring) % 3 == 0 else wood, x, y, 92, 0.06))
    top.save('block/camp_standard_top.png')

    oak, oak_dark, iron, iron_dark, rivet = hexc('3d2a19'), hexc('271a0f'), hexc('6d7379'), hexc('41464b'), hexc('b4bac0')
    gate = Img(16, 16)
    for y in range(16):
        for x in range(16):
            c = oak_dark if x % 4 == 0 else jitter(oak, x, y, 93, 0.07)
            if y in (2, 3, 12, 13):
                c = iron_dark if y in (3, 13) else iron
            gate.set(x, y, c)
    for x in (1, 6, 10, 14):
        gate.set(x, 2, rivet)
        gate.set(x, 12, rivet)
    gate.save('block/reinforced_gate.png')


def props_blocks():
    """Camp props (also craftable for towns): a weapon rack with three blades on dark boards, a hide rack with a
    stretched pelt in a wooden frame, and a banded supply crate (side and top)."""
    wood, wood_dark, wood_light = hexc('6a4a2c'), hexc('3f2b19'), hexc('8a6440')
    steel, steel_dark, grip, gold = hexc('c5ccd1'), hexc('7d858c'), hexc('5a3a20'), hexc('d9b13b')
    rack = Img(16, 16)
    board, board_dark = hexc('5a4430'), hexc('3a2a1b')
    for y in range(16):
        for x in range(16):
            rack.set(x, y, jitter(board_dark if y % 5 == 4 else board, x, y, 71, 0.07))
    for x in range(16):  # a heavy frame round the boards, so the rack reads as a piece of furniture
        for y in (0, 15):
            rack.set(x, y, wood_dark)
        rack.set(x, 1, wood)
    for y in range(16):
        for x in (0, 15):
            rack.set(x, y, wood_dark)
        for x in (1, 14):
            rack.set(x, y, wood)
    blade_hi, edge, shadow = hexc('f2f6f8'), hexc('aab3ba'), hexc('5d646b')
    for x in range(2, 14):  # the two rails the weapons hang on
        for y, c in ((5, wood_light), (6, wood), (11, wood_light), (12, wood)):
            rack.set(x, y, c)
    gear = Img(16, 16)   # weapons on their own layer, then outlined so they stand out from the boards
    for y in range(2, 9):    # sword: long blade, gold guard, wrapped grip, pommel
        gear.set(4, y, blade_hi)
        gear.set(5, y, edge)
    gear.set(4, 1, blade_hi)
    for x in (2, 3, 4, 5):
        gear.set(x, 9, gold)
    for y in (10, 11, 12):
        gear.set(4, y, grip)
    gear.set(4, 13, gold)
    for y in range(4, 14):   # axe: haft with a wide double-bit head
        gear.set(8, y, grip)
    for y in (2, 3, 4, 5, 6):
        gear.set(7, y, blade_hi if y in (3, 4, 5) else edge)
        gear.set(9, y, edge if y in (3, 4, 5) else shadow)
    for y in range(4, 14):   # spear: long shaft with a leaf head and a red tassel
        gear.set(13, y, grip)
    for y in (1, 2, 3):
        gear.set(13, y, blade_hi)
    gear.set(12, 3, edge)
    gear.set(14, 3, shadow)
    gear.set(13, 5, hexc('b3262a'))
    _outline(gear, hexc('2a1f14'))
    for y in range(16):
        for x in range(16):
            if gear.get(x, y)[3]:
                rack.set(x, y, gear.get(x, y))
    rack.save('block/weapon_rack.png')

    hide, hide_dark, hide_light, frame = hexc('b88a5a'), hexc('86603a'), hexc('d2a674'), hexc('4a3220')
    pelt = Img(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            n = noise(x, y, 73)
            c = hide_dark if n > 0.72 else hide_light if n < 0.18 else hide
            pelt.set(x, y, jitter(frame, x, y, 72, 0.08) if edge else jitter(c, x, y, 74, 0.05))
    for y in range(3, 13):   # a pale belly stripe down the middle and a dark spine
        pelt.set(8, y, hide_dark)
        pelt.set(7, y, hide_light)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12), (3, 7), (12, 7)):   # stretched out on lashings and pegs
        pelt.set(x, y, hide_dark)
    for x in range(2, 14, 3):
        for y in (2, 13):
            pelt.set(x, y, hexc('e0d2ac'))
            pelt.set(x, y + (1 if y == 2 else -1), hexc('a89a74'))
    for y in range(2, 14, 3):
        for x in (2, 13):
            pelt.set(x, y, hexc('e0d2ac'))
    pelt.save('block/hide_rack.png')
    for name in ('weapon_rack', 'hide_rack'):   # the inventory icons are the flat faces: a block this thin looks tiny in a slot
        icon = Img(16, 16)
        src = pelt if name == 'hide_rack' else rack
        for y in range(16):
            for x in range(16):
                icon.set(x, y, src.get(x, y))
        icon.save(f'item/{name}.png')

    plank_a, plank_b, frame_c, frame_d = hexc('a9794a'), hexc('93663c'), hexc('6e4a29'), hexc('4d3219')
    brace, brace_d, nail = hexc('c08f5a'), hexc('7a532f'), hexc('9aa1a6')
    side = Img(16, 16)
    for y in range(16):
        for x in range(16):
            # horizontal planks with a dark seam every 4 rows and a little grain
            base = plank_b if y % 4 == 3 else plank_a
            side.set(x, y, jitter(base, x, y, 75, 0.06))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            if edge:
                inner = x in (1, 14) or y in (1, 14)
                side.set(x, y, jitter(frame_d if inner else frame_c, x, y, 77, 0.05))
    for k in range(2, 14):  # X brace: light wood strip with a dark edge on its lower side
        for x, y in ((k, k), (k, 15 - k)):
            side.set(x, y, jitter(brace, x, y, 78, 0.04))
            if y + 1 < 14:
                side.set(x, y + 1, jitter(brace_d, x, y, 79, 0.04))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 7), (8, 8)):
        side.set(x, y, nail)
    side.save('block/supply_crate_side.png')
    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            inner = x in (1, 14) or y in (1, 14)
            plank = plank_b if x % 5 == 4 else plank_a
            top.set(x, y, jitter((frame_d if inner else frame_c) if edge else plank, x, y, 76, 0.05))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        top.set(x, y, nail)
    top.save('block/supply_crate_top.png')


def trap_blocks():
    """Camp trap blocks: a brass alarm plate, caltrops' dark spikes on dirt, the Castle powder keg."""
    gold, gold_dark, dark = hexc('c9a227'), hexc('8a6c1a'), hexc('2a2018')
    # alarm plate: a hammered brass square with a bell boss in the middle
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            c = dark if ring > 6 else jitter(gold, x, y, 60, 0.09)
            if ring <= 1:
                c = jitter(gold_dark, x, y, 61, 0.08)
            img.set(x, y, c)
    for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        img.set(7 + dx, 7 + dy, hexc('e8c95a'))
    img.set(7, 6, hexc('f2e08a'))
    img.save('block/alarm_plate.png')

    # caltrops: packed dirt with four iron spikes
    dirt, dirt_dark, iron, iron_dark = hexc('5b422b'), hexc('41301e'), hexc('9aa2ab'), hexc('4b5054')
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            img.set(x, y, jitter(dirt if (x * 3 + y * 5) % 7 else dirt_dark, x, y, 62, 0.08))
    for cx, cy in ((4, 4), (11, 3), (3, 11), (12, 12), (7, 8)):
        for dy in (-1, 0):
            for dx in (-1, 0):
                img.set(cx + dx, cy + dy, iron_dark)
        img.set(cx, cy - 2, iron)
        img.set(cx - 1, cy - 1, iron)
    img.save('block/caltrops.png')

    # powder keg: dark staves under tarred bands, a pale bung and fuse hole on top
    wood, wood_dark, band, fuse = hexc('4a3018'), hexc('33200f'), hexc('1e1c1a'), hexc('d9cfb4')
    side = Img(16, 16)
    for y in range(16):
        for x in range(16):
            c = wood_dark if x % 4 == 0 else jitter(wood, x, y, 63, 0.08)
            if y in (3, 12):
                c = band
            side.set(x, y, c)
    side.save('block/powder_keg_side.png')
    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            ring = int(max(abs(x - 7.5), abs(y - 7.5)))
            top.set(x, y, jitter(wood if ring % 3 else wood_dark, x, y, 64, 0.07))
    top.set(7, 7, fuse)
    top.set(8, 7, fuse)
    top.save('block/powder_keg_top.png')


def contraption_blocks():
    """Camp contraption blocks: the war relay (iron casing round a redstone core, dark and lit), the alarm horn
    (a brass bell mouth), the portcullis (iron grating with see-through holes) and rigged planks (spruce with a
    telltale saw cut and iron pins)."""
    iron, iron_dark, rivet = hexc('3b3f45'), hexc('24272b'), hexc('6d737b')
    for lit in (False, True):
        core, core_hi = (hexc('ff3a2a'), hexc('ffb09a')) if lit else (hexc('6a1510'), hexc('8e2a20'))
        for part in ('side', 'top'):
            img = Img(16, 16)
            for y in range(16):
                for x in range(16):
                    edge = x in (0, 15) or y in (0, 15)
                    c = iron_dark if edge else jitter(iron, x, y, 70, 0.07)
                    if x in (2, 13) and y in (2, 13):
                        c = rivet
                    if part == 'side' and 4 <= x <= 11 and 5 <= y <= 10:
                        c = iron_dark if x in (4, 11) or y in (5, 10) else jitter(core, x, y, 71, 0.12)
                    if part == 'top' and (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 12:
                        c = jitter(core, x, y, 72, 0.12)
                    img.set(x, y, c)
            if part == 'side':
                img.set(6, 6, core_hi)
                img.set(7, 6, core_hi)
            else:
                img.set(6, 6, core_hi)
                img.set(7, 6, core_hi)
                img.set(6, 7, core_hi)
            img.save(f'block/war_relay_{part}{"_on" if lit else ""}.png')

    brass, brass_dark, mouth = hexc('c9a227'), hexc('7a5d14'), hexc('2a1d0c')
    side = Img(16, 16)
    for y in range(16):
        for x in range(16):
            c = jitter(brass, x, y, 73, 0.09)
            if y in (0, 15) or x in (0, 15) or y in (4, 11):
                c = brass_dark
            side.set(x, y, c)
    side.save('block/alarm_horn_side.png')
    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            top.set(x, y, mouth if r < 4 else brass_dark if r < 5.2 else jitter(brass, x, y, 74, 0.09))
    top.save('block/alarm_horn_top.png')

    bar, bar_dark = hexc('5a5f66'), hexc('33373c')
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            if x % 5 in (0, 1) or y % 5 in (0, 1):
                img.set(x, y, bar_dark if (x % 5 == 1 or y % 5 == 1) else jitter(bar, x, y, 75, 0.08))
    for x in range(0, 16, 5):
        img.set(x, 15, hexc('8d949c'))
        img.set(x, 14, hexc('8d949c'))
    img.save('block/portcullis.png')

    base = hexc('6f4f2e')
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            img.set(x, y, planks(x, y, base))
    for y in range(16):
        img.set(8, y, shade(base, 0.45))
    for x, y in ((3, 1), (12, 1), (3, 13), (12, 13)):
        img.set(x, y, hexc('9aa2ab'))
    img.save('block/rigged_floor.png')


def war_map():
    """Rolled-out parchment with a dotted route to a red X: the War Map item."""
    img = Img(16, 16)
    paper, paper_dark, ink, red = hexc('e3d3a5'), hexc('b89d64'), hexc('5b4630'), hexc('b3242a')
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            img.set(x, y, paper_dark if edge else jitter(paper, x, y, 94, 0.05))
    for x, y in ((3, 11), (5, 10), (6, 8), (8, 7), (9, 5)):
        img.set(x, y, ink)
    for d in range(-1, 2):
        img.set(11 + d, 4 + d, red)
        img.set(11 + d, 4 - d, red)
    for x, y in ((4, 4), (5, 4), (4, 5)):  # a little tree
        img.set(x, y, hexc('4f7a2e'))
    img.save('item/war_map.png')


def _icon(rows, palette, rel, seed=40):
    img = Img(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.set(x, y, jitter(palette[ch], x, y, seed, 0.05))
    img.save(rel)


def _outline(img, color):
    """A 1px outline around every opaque pixel (8-neighbour), the way vanilla items read at hotbar size."""
    solid = {(x, y) for y in range(img.h) for x in range(img.w) if img.get(x, y)[3] > 0}
    for x, y in solid:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if (nx, ny) not in solid and 0 <= nx < img.w and 0 <= ny < img.h and (abs(dx) + abs(dy) == 1):
                    img.set(nx, ny, color)


def _art(rows, palette, seed, outline=None):
    img = Img(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                c = palette[ch]
                img.set(x, y, jitter(c, x, y, seed, 0.04) if ch.islower() else c)
    if outline:
        _outline(img, outline)
    return img


def warlord_blade():
    """The Warlord's Brand: a broad black falchion with a glowing crimson fuller, gold crossguard and a ruby pommel."""
    img = Img(16, 16)
    steel, steel_hi, steel_lo = hexc('4a505c'), hexc('c4ccd8'), hexc('23262c')
    for t in range(5, 15):
        img.set(t, 14 - t, steel_hi)      # the upper edge catches the light
        img.set(t + 1, 15 - t, steel_lo)  # the lower edge is in shadow
        img.set(t, 15 - t, hexc('e0303a') if 7 <= t <= 11 else steel)
    img.set(14, 0, hexc('ffffff'))
    img.set(15, 0, T)
    img.set(15, 1, T)
    img.set(12, 3, hexc('ff8080'))  # glint at the end of the fuller
    for k, c in enumerate(['fff0a0', 'f2c236', 'c8901e', 'f2c236', 'a8741a']):
        img.set(2 + k, 9 + k, hexc(c))  # crossguard, across the blade
    img.set(4, 12, hexc('5a3a22'))
    img.set(3, 13, hexc('3a2414'))
    img.set(2, 13, hexc('5a3a22'))
    img.set(3, 12, hexc('3a2414'))
    img.set(1, 14, hexc('ff4050'))  # ruby pommel
    img.set(2, 15, hexc('a8741a'))
    img.set(0, 14, hexc('a8741a'))
    img.set(1, 15, hexc('8a1a1e'))
    _outline(img, hexc('15151a'))
    img.save('item/warlord_blade.png')


def relics():
    """The four fantasy-camp relics, each lit by its camp's colour: a hex-skull charm, an ice sigil, a soul lantern
    and the illagers' omen banner."""
    _art([
        '................',
        '.......cc.......',
        '......c..c......',
        '.......cc.......',
        '.....BBBBBB.....',
        '....BBbbbbbb....',
        '...BBbbbbbbbs...',
        '...BKKKbbKKKs...',
        '...bKEKbbKEKs...',
        '...bbbbbsbbbs...',
        '....bbbkkbbs....',
        '.....bsbsbs.....',
        '.....sbsbsb.....',
        '.......pp.......',
        '......pGGp......',
        '.......pp.......',
    ], {'c': hexc('5a3a22'), 'B': hexc('f4eedc'), 'b': hexc('d6cbaa'), 's': hexc('9c8c68'),
        'E': hexc('f0b0ff'), 'K': hexc('2a1030'), 'k': hexc('8a2ad0'), 'p': hexc('6a2aa8'), 'G': hexc('e0a0ff')},
        41, hexc('231a12')).save('item/hag_charm.png')

    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 7.0 and d > 5.3:
                c = hexc('e6f4ff') if dx + dy < -4 else hexc('9ab8d0') if dx + dy < 4 else hexc('5a7890')
                img.set(x, y, c)
            elif d <= 5.3:
                img.set(x, y, shade(hexc('1f4f7a'), 1.25 - d / 10))
    for a in range(6):
        import math
        ang = a * math.pi / 3
        for r10 in range(0, 48):
            r = r10 / 10
            px, py = int(round(7.5 + r * math.cos(ang) - 0.0)), int(round(7.5 + r * math.sin(ang)))
            img.set(px, py, hexc('bff0ff'))
    for x, y in [(7, 7), (8, 8), (7, 8), (8, 7)]:
        img.set(x, y, hexc('ffffff'))
    for x, y in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        img.set(x, y, hexc('ffffff'))  # frost glints on the rim
    _outline(img, hexc('0e2438'))
    img.save('item/frost_sigil.png')

    _art([
        '.......oo.......',
        '......o..o......',
        '......o..o......',
        '....dIIIIIId....',
        '...dIiiiiiiid...',
        '....iggggggi....',
        '....igcFFcgi....',
        '....icFWWFci....',
        '....igFWWFgi....',
        '....icFWWFci....',
        '....igcFFcgi....',
        '....iggccggi....',
        '...dIiiiiiiid...',
        '....dddddddd....',
        '................',
        '................',
    ], {'o': hexc('3a3e46'), 'd': hexc('262a30'), 'I': hexc('9aa2ae'), 'i': hexc('4a4f58'),
        'g': hexc('1e4a50'), 'c': hexc('3fc9d9'), 'F': hexc('7ff0ff'), 'W': hexc('f0ffff')},
        43, hexc('121418')).save('item/grave_lantern.png')

    _art([
        '................',
        '.GyyyyyyyyyyyyG.',
        '..rRRRRRRRRRRr..',
        '..rRRRRRRRRRRr..',
        '..rRRwwwwwwRRr..',
        '..rRwwwwwwwwRr..',
        '..rRwkkwwkkwRr..',
        '..rRwwEwwEwwRr..',
        '..rRwwwkkwwwRr..',
        '..rRRwkkkkwRRr..',
        '..rRRRwwwwRRRr..',
        '..rRRRRRRRRRRr..',
        '..gggggggggggg..',
        '..rRR.rRRr.RRr..',
        '..rR...rr...Rr..',
        '................',
    ], {'G': hexc('ffe070'), 'y': hexc('a8741a'), 'r': hexc('5a1216'), 'R': hexc('9a1c22'),
        'w': hexc('dcd8cc'), 'k': hexc('2a2a30'), 'E': hexc('ff4040'), 'g': hexc('e0b030')},
        44, hexc('1a0a0a')).save('item/omen_banner.png')


def warhorse_whistle():
    """A gold cavalry whistle on a red cord loop."""
    _art([
        '................',
        '................',
        '..rrr...........',
        '.r...r..........',
        '.r...r..........',
        '..r.r...........',
        '...r......HHH...',
        '...r.....HGGGH..',
        '..dHHHHHHGGggGH.',
        '..dGGGGGGGgkkgg.',
        '..dgggggggGkkgd.',
        '..ddddddddgggd..',
        '..........ddd...',
        '................',
        '................',
        '................',
    ], {'r': hexc('c02a2e'), 'H': hexc('fff0a0'), 'G': hexc('f2c236'), 'g': hexc('c8901e'),
        'd': hexc('8a5a10'), 'k': hexc('3a2410')}, 45, hexc('2a1a08')).save('item/warhorse_whistle.png')


def relic_pedestal():
    """A blackstone reliquary with gold bands, and the four relic gems that light up on it."""
    stone, stone_hi, stone_lo = hexc('3c3a44'), hexc('5c5a66'), hexc('24232a')
    gold, gold_hi, gold_lo = hexc('e0b030'), hexc('fff0a0'), hexc('a8741a')
    side = Img(16, 16)
    side.rect(0, 0, 16, 16, lambda x, y: jitter(stone, x, y, 50, 0.08))
    for y in (4, 9):
        side.rect(0, y, 16, y + 1, stone_lo)  # courses of blackstone
    for x, y in [(5, 0), (11, 5), (3, 10)]:
        side.rect(x, y, x + 1, y + 4 if y else 4, stone_lo)
    side.rect(0, 0, 16, 1, gold_hi)
    side.rect(0, 1, 16, 2, gold)
    side.rect(0, 2, 16, 3, gold_lo)
    side.rect(0, 15, 16, 16, stone_lo)
    side.rect(7, 6, 9, 8, hexc('b3262a'))  # a crimson stone set in the face
    side.set(7, 6, hexc('ff6060'))
    side.save('block/relic_pedestal_side.png')
    top = Img(16, 16)
    top.rect(0, 0, 16, 16, lambda x, y: jitter(stone_hi, x, y, 51, 0.06))
    top.rect(0, 0, 16, 1, gold_hi)
    top.rect(0, 15, 16, 16, gold_lo)
    top.rect(0, 0, 1, 16, gold_hi)
    top.rect(15, 0, 16, 16, gold_lo)
    top.rect(1, 1, 15, 2, gold)
    top.rect(1, 14, 15, 15, gold)
    top.rect(1, 1, 2, 15, gold)
    top.rect(14, 1, 15, 15, gold)
    top.rect(6, 6, 10, 10, gold)
    top.rect(7, 7, 9, 9, hexc('b3262a'))
    top.save('block/relic_pedestal_end.png')
    # Each relic's gem: a 4x4 swatch per relic, shaded from a light top-left to a dark bottom-right.
    gems = Img(16, 16)
    for i, c in enumerate(['b060ff', '8fe8ff', '50f0e0', 'ff3a3a']):
        base = hexc(c)
        for y in range(4):
            for x in range(4):
                gems.set(i * 4 + x, y, shade(base, 1.25 - (x + y) * 0.1))
        gems.set(i * 4, 0, hexc('ffffff'))
        for y in range(4, 16):  # the rest: gold for the gem settings
            for x in range(4):
                gems.set(i * 4 + x, y, jitter(gold, x, y, 52 + i, 0.08))
    gems.save('block/relic_gems.png')


def warlord_plate():
    """The Warlord's Plate icons: blackened iron with gold trim and a crimson eye."""
    iron, iron_hi, iron_lo = hexc('3f444e'), hexc('7d8590'), hexc('23262c')
    gold, red = hexc('e0b030'), hexc('b3262a')
    p = {'i': iron, 'I': iron_hi, 'd': iron_lo, 'g': gold, 'r': red}
    _icon([
        '.....ggggg.....',
        '...iiiiiiiii...',
        '..iidiiiiidii..',
        '..iiirrrrriii..',
        '..iirrrrrriii..',
        '..iiiiiIiidii..',
        '..ddididididd..',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], p, 'item/warlord_helm.png', 46)
    _icon([
        '..gg.......gg..',
        '.iiiId...dIiii.',
        '.iiiiddddddiii.',
        '.riiiiiiiiiiir.',
        '..iiiggIggiiii.',
        '..iiiggggiiii..',
        '..iiiiiiiiiiii.',
        '..iiiiddddiiii.',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], p, 'item/warlord_cuirass.png', 47)
    _icon([
        '..iiiiiiiiii...',
        '..iggggggggi...',
        '..iiiiiIiiii...',
        '..iiii..iiii...',
        '..iiii..iiii...',
        '..iiii..iiii...',
        '..idii..iidi...',
        '..iiii..iiii...',
        '..iiii..iiii...',
        '..dddd..dddd...',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], p, 'item/warlord_greaves.png', 48)
    _icon([
        '................',
        '................',
        '..iiii..iiii....',
        '..iIii..iIii....',
        '..iiii..iiii....',
        '..iiii..iiii....',
        '..iigg..ggii....',
        '.iigg....ggii...',
        '.iggdd..ddggi...',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], p, 'item/warlord_sabatons.png', 49)


def playtest_guide():
    """The Tester's Guide: a teal book with a gold clasp and a white checkmark, so it never reads as the red Handbook."""
    _art([
        '................',
        '................',
        '...Sbbbbbbbbbp..',
        '...Sbbbbbbbbbp..',
        '...SbbbbbbbWbp..',
        '...SbbbbbbWWbp..',
        '...SbbbbbWWbbp..',
        '...SbWbbWWbbbp..',
        '...SbWWWWbbbGp..',
        '...SbbWWbbbbGp..',
        '...Sbbbbbbbbbp..',
        '...Sbbbbbbbbbp..',
        '...Sddddddddd...',
        '................',
        '................',
        '................',
    ], {'S': hexc('0f3a3a'), 'b': hexc('1f7a78'), 'd': hexc('135250'), 'p': hexc('efe6cf'),
        'W': hexc('f4f4f4'), 'G': hexc('e0b030')}, 52, hexc('08201f')).save('item/playtest_guide.png')


def handbook():
    """Red leather book with gold corners and a crossed-swords emblem."""
    img = Img(16, 16)
    cover, spine, pages, gold = hexc('9b2226'), hexc('5e1417'), hexc('efe6cf'), hexc('e0b030')
    for y in range(2, 15):
        for x in range(3, 14):
            if x == 3:
                c = spine
            elif y in (2, 14) or x == 13:
                c = shade(cover, 0.7)
            else:
                c = jitter(cover, x, y, 50, 0.06)
            img.set(x, y, c)
        img.set(14, y, pages if 3 <= y <= 13 else T)
    for (x, y) in ((4, 3), (12, 3), (4, 13), (12, 13)):
        img.set(x, y, gold)
    for i in range(5):  # crossed swords
        img.set(6 + i, 6 + i, hexc('e8e8e8'))
        img.set(10 - i, 6 + i, hexc('e8e8e8'))
    img.set(8, 8, gold)
    img.save('item/handbook.png')


def pixel_map(rows, x0, y0):
    """Turns a list of strings into icon points ('#' = line color, 'r' = red flag)."""
    pts = []
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != '.':
                pts.append((x0 + dx, y0 + dy, ch))
    return pts


TOWN_CENTER_ICON = [
    '....#r....',
    '...###....',
    '..#...#...',
    '.#.....#..',
    '#########.',
    '#.#.#.#.#.',
    '#...#...#.',
    '#########.',
]
HOUSE_ICON = [
    '...#....',
    '..#.#...',
    '.#...#..',
    '#######.',
    '#..#..#.',
    '#######.',
]
BARRACKS_ICON = [
    '....#....',
    '...###...',
    '..#...#..',
    '.#######.',
    '#.#...#.#',
    '#########',
]
SMITHY_ICON = [
    '#######',
    '.#####.',
    '..###..',
    '...#...',
    '..###..',
    '..#r#..',
]
TOWER_ICON = [
    '...#...',
    '..###..',
    '..#.#..',
    '..#.#..',
    '..#.#..',
    '.#####.',
    '#######',
]
STABLE_ICON = [
    '#######',
    '#.....#',
    '#.#.#.#',
    '#.#.#.#',
    '#.....#',
    '##.#.##',
    '..#.#..',
]
SIEGE_ICON = [
    '#######',
    '#.....#',
    '#..#..#',
    '#.###.#',
    '#.....#',
    '##...##',
    '..###..',
]
ARCHERY_ICON = [
    '#.....#',
    '#..#..#',
    '#.###.#',
    '#.#r#.#',
    '#.###.#',
    '#.....#',
    '#######',
]


# ---------------------------------------------------------------- blocks

def planks(x, y, base):
    c = jitter(base, x, y, 30, 0.06)
    if y % 4 == 3:
        return shade(base, 0.7)
    if (x + (y // 4) * 5) % 8 == 0:
        return shade(base, 0.8)
    return c


def town_center_blocks():
    wood, gold, red = hexc('6b4a2b'), hexc('d9a520'), hexc('8e2323')
    side = Img(16, 16)
    side.rect(0, 0, 16, 16, lambda x, y: planks(x, y, wood))
    for i in range(16):  # gold trim frame
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            side.set(x, y, shade(gold, 0.85 if (x + y) % 2 else 1.0))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        side.set(x, y, gold)
    side.save('block/town_center_side.png')

    front = Img(16, 16)
    front.px = [row[:] for row in side.px]
    front.rect(4, 2, 12, 13, lambda x, y: jitter(red, x, y, 31, 0.05))  # banner
    for x in range(4, 12):
        front.set(x, 2, gold)
    for (x, y) in ((4, 13), (6, 13), (8, 13), (10, 13), (5, 14), (9, 14)):
        front.set(x, y, shade(red, 0.8))  # banner tails
    for i in range(6):  # crossed swords
        front.set(5 + i, 4 + i, hexc('e0e0e0'))
        front.set(10 - i, 4 + i, hexc('e0e0e0'))
    front.set(7, 10, gold); front.set(8, 10, gold)
    front.save('block/town_center_front.png')

    top = Img(16, 16)
    parchment, ink = hexc('e7d3a1'), hexc('7a5a36')
    top.rect(0, 0, 16, 16, lambda x, y: jitter(parchment, x, y, 32, 0.05))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            top.set(x, y, shade(gold, 0.9))
    for (x, y) in ((3, 4), (4, 4), (5, 5), (6, 5), (7, 6), (8, 6), (9, 7), (10, 8), (11, 8), (12, 9)):
        top.set(x, y, ink)  # river
    for (x, y) in ((4, 10), (5, 11), (6, 10), (10, 3), (11, 4), (12, 3)):
        top.set(x, y, hexc('4f7a33'))  # trees
    for (x, y) in ((8, 10), (9, 10), (8, 11), (9, 11)):
        top.set(x, y, red)  # town marker
    top.save('block/town_center_top.png')


def hearth_blocks():
    stone, ember, glow = hexc('7d7d7d'), hexc('ff7a1a'), hexc('ffd35a')
    side = Img(16, 16)

    def bricks(x, y):
        off = 0 if (y // 4) % 2 == 0 else 4
        if y % 4 == 3 or (x + off) % 8 == 7:
            return shade(stone, 0.65)
        return jitter(stone, x, y, 40, 0.07)

    side.rect(0, 0, 16, 16, bricks)
    side.rect(4, 6, 12, 13, lambda x, y: hexc('2a1a12'))  # opening
    for x in range(5, 11):
        side.set(x, 12, ember if x % 2 else glow)
        side.set(x, 11, glow if x in (6, 9) else hexc('2a1a12'))
    side.set(7, 10, ember); side.set(8, 9, glow); side.set(8, 10, ember)
    for x in range(3, 13):
        side.set(x, 5, shade(stone, 0.5))
    side.save('block/hearth_side.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, bricks)
    top.rect(3, 3, 13, 13, lambda x, y: [hexc('3a2418'), ember, glow][int(noise(x, y, 41) * 3)])
    top.save('block/hearth_top.png')


def barracks_blocks():
    stone, wood, steel, red = hexc('7a7a7a'), hexc('6b4a2b'), hexc('d5dbe0'), hexc('a3262a')

    def bricks(x, y):
        off = 0 if (y // 4) % 2 == 0 else 4
        if y % 4 == 3 or (x + off) % 8 == 7:
            return shade(stone, 0.62)
        return jitter(stone, x, y, 60, 0.06)

    side = Img(16, 16)
    side.rect(0, 0, 16, 16, bricks)
    for y in range(16):
        side.set(0, y, shade(stone, 0.5))
        side.set(15, y, shade(stone, 0.8))
    side.save('block/barracks_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, bricks)
    front.rect(5, 5, 11, 15, lambda x, y: hexc('24160f'))
    for i in range(7):
        front.set(4 + i, 2 + i, steel)
        front.set(11 - i, 2 + i, steel)
    front.set(7, 1, red)
    front.set(8, 1, red)
    front.save('block/barracks_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, lambda x, y: planks(x, y, wood))
    top.save('block/barracks_top.png')


def archery_blocks():
    """Open yard: fence posts on the side, a target on the front, a shooting lane on top."""
    dirt, grass, wood = hexc('6b5438'), hexc('5d7a38'), hexc('6b4a2b')
    red, white, steel = hexc('a3262a'), hexc('f4f4f4'), hexc('d5dbe0')

    side = Img(16, 16)
    side.rect(0, 10, 16, 16, lambda x, y: jitter(dirt, x, y, 70, 0.06))
    side.rect(0, 0, 16, 10, lambda x, y: jitter(grass, x, y, 71, 0.05))
    for x in (2, 7, 12):
        for y in range(1, 15):
            side.set(x, y, shade(wood, 0.75) if y % 4 == 0 else wood)
            side.set(x + 1, y, shade(wood, 0.6))
    for y in (4, 9):
        for x in range(2, 14):
            side.set(x, y, shade(wood, 0.85))
    side.save('block/archery_range_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, lambda x, y: planks(x, y, wood))
    for y in range(16):
        for x in range(16):
            dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if dist <= 6.2:
                ring = int(dist / 1.25)
                front.set(x, y, (red, white, red, white, red, shade(wood, 0.75))[min(ring, 5)])
    front.set(7, 7, steel)
    front.set(8, 7, steel)
    front.set(7, 8, steel)
    front.set(8, 8, shade(steel, 0.75))
    front.save('block/archery_range_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, lambda x, y: jitter(grass, x, y, 73, 0.07))
    for y in range(1, 15):
        top.set(7, y, hexc('cbb98a'))
        top.set(8, y, hexc('a89870'))
    for x in range(3, 13):
        if x not in (7, 8):
            top.set(x, 3, shade(red, 0.9) if x % 2 else white)
    top.save('block/archery_range_top.png')


def blacksmith_blocks():
    """Stone forge: brick side, a lit hearth and anvil on the front, coals on top."""
    stone, brick, coal = hexc('6e6e6e'), hexc('8c4a32'), hexc('2a211c')
    ember, steel = hexc('ff7a1a'), hexc('c5ccd1')

    def bricks(x, y):
        off = 0 if (y // 4) % 2 == 0 else 4
        if y % 4 == 3 or (x + off) % 8 == 7:
            return shade(stone, 0.6)
        return jitter(stone, x, y, 90, 0.06)

    side = Img(16, 16)
    side.rect(0, 0, 16, 16, bricks)
    side.rect(4, 5, 12, 14, lambda x, y: coal)
    for x in range(5, 11):
        side.set(x, 12, ember if x % 2 else hexc('ffd35a'))
    side.save('block/blacksmith_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, lambda x, y: jitter(brick, x, y, 91, 0.05) if y > 8 else bricks(x, y))
    front.rect(5, 3, 11, 9, lambda x, y: coal)
    for x in range(6, 10):
        front.set(x, 8, ember if x % 2 else hexc('ffd35a'))
    for x in range(3, 13):
        front.set(x, 11, steel if 4 <= x <= 11 else shade(steel, 0.7))
        front.set(x, 12, shade(steel, 0.75))
    front.set(6, 13, shade(steel, 0.6))
    front.set(7, 13, shade(steel, 0.6))
    front.set(8, 14, shade(steel, 0.55))
    front.set(9, 13, shade(steel, 0.6))
    front.save('block/blacksmith_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, bricks)
    top.rect(4, 4, 12, 12, lambda x, y: [coal, ember, hexc('ffd35a')][int(noise(x, y, 92) * 3)])
    top.save('block/blacksmith_top.png')


def stable_blocks():
    """Timber stable: plank walls, a hayloft door, and a straw roof."""
    wood, wood_dark, hay, iron = hexc('b5834a'), hexc('6b4423'), hexc('d6b24a'), hexc('c5ccd1')

    def planks(x, y):
        if y % 4 == 3:
            return shade(wood_dark, 0.85)
        return jitter(wood, x, y, 60, 0.06)

    side = Img(16, 16)
    side.rect(0, 0, 16, 16, planks)
    side.rect(3, 4, 13, 13, lambda x, y: hay if y > 8 else shade(hay, 0.85))
    for x in range(3, 13):
        side.set(x, 4, shade(wood_dark, 0.7))
        side.set(x, 13, shade(wood_dark, 0.7))
    side.save('block/stable_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, planks)
    front.rect(4, 3, 12, 14, lambda x, y: hexc('2a2118') if y < 12 else hay)
    for y in range(3, 14):
        front.set(4, y, shade(wood_dark, 0.75))
        front.set(11, y, shade(wood_dark, 0.75))
    front.set(7, 8, iron)
    front.set(8, 8, shade(iron, 0.8))
    front.save('block/stable_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, lambda x, y: jitter(hay, x, y, 61, 0.08))
    for x in range(16):
        top.set(x, 0, shade(wood_dark, 0.8))
        top.set(x, 15, shade(wood_dark, 0.8))
    top.save('block/stable_top.png')


def stable_gui():
    img = Img(256, 256)
    w, h = 176, 206
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    hay = hexc('c4963a')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)
        img.set(x + size - 1, y, hexc('8b8b8b'))
        img.set(x, y + size - 1, hexc('8b8b8b'))

    for i in range(5):
        slot(7 + i * 20, 77)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 121 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 179)
    for x in range(7, w - 7):
        img.set(x, 36, dark)
        img.set(x, 37, white)
        img.set(x, 38, hay)
    img.save('gui/stable.png')


def watch_tower_blocks():
    """Stone tower: brick shaft, a dark door, and a crenellated top."""
    stone, dark, sky = hexc('8a8a8a'), hexc('2c2c2c'), hexc('7eb0d6')

    def bricks(x, y):
        off = 0 if (y // 4) % 2 == 0 else 4
        if y % 4 == 3 or (x + off) % 8 == 7:
            return shade(stone, 0.55)
        return jitter(stone, x, y, 70, 0.05)

    side = Img(16, 16)
    side.rect(0, 0, 16, 16, bricks)
    side.rect(6, 2, 10, 8, lambda x, y: sky if (x + y) % 2 == 0 else shade(sky, 0.75))
    side.save('block/watch_tower_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, bricks)
    front.rect(5, 3, 11, 14, lambda x, y: dark)
    front.set(9, 8, hexc('c9a227'))
    front.save('block/watch_tower_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, bricks)
    top.rect(6, 6, 10, 10, lambda x, y: dark)
    top.save('block/watch_tower_top.png')


def watch_tower_gui():
    img = Img(256, 256)
    w, h = 176, 168
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    stone = hexc('6e6e6e')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)

    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 83 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 141)
    for x in range(7, w - 7):
        img.set(x, 32, dark)
        img.set(x, 33, stone)
    img.save('gui/watch_tower.png')


def siege_blocks():
    """Timber shed: plank walls, a log ram on the front, and a slab roof."""
    wood, wood_dark, iron = hexc('b5834a'), hexc('6b4423'), hexc('c5ccd1')

    def planks(x, y):
        if y % 4 == 3:
            return shade(wood_dark, 0.85)
        return jitter(wood, x, y, 81, 0.06)

    side = Img(16, 16)
    side.rect(0, 0, 16, 16, planks)
    side.rect(2, 6, 14, 13, lambda x, y: shade(wood_dark, 0.7))
    for x in range(2, 14):
        side.set(x, 6, iron if x % 3 else shade(iron, 0.75))
    side.save('block/siege_workshop_side.png')

    front = Img(16, 16)
    front.rect(0, 0, 16, 16, planks)
    front.rect(6, 5, 10, 12, lambda x, y: jitter(shade(wood, 0.75), x, y, 82, 0.05))
    for y in range(5, 12):
        front.set(6, y, shade(wood_dark, 0.7))
        front.set(9, y, shade(wood_dark, 0.7))
    front.set(7, 8, iron)
    front.set(8, 8, shade(iron, 0.8))
    front.save('block/siege_workshop_front.png')

    top = Img(16, 16)
    top.rect(0, 0, 16, 16, planks)
    for x in range(16):
        top.set(x, 0, shade(wood_dark, 0.8))
        top.set(x, 15, shade(wood_dark, 0.8))
    top.save('block/siege_workshop_top.png')


def siege_gui():
    img = Img(256, 256)
    w, h = 176, 206
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    timber = hexc('8a5a2b')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)

    for i in range(5):
        slot(7 + i * 20, 77)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 121 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 179)
    for x in range(7, w - 7):
        img.set(x, 36, dark)
        img.set(x, 37, white)
        img.set(x, 38, timber)
    img.save('gui/siege_workshop.png')


def ram_texture():
    """128x128 texture for RamModel: every box's UV area painted by material.

    Box UV layout (vanilla): top (u+d, v) w x d, bottom (u+d+w, v), then at v+d: right d x h,
    front w x h, left d x h, back w x h. Offsets match RamModel.createBodyLayer.
    """
    img = Img(128, 128)
    oak, oak_dark = hexc('a8773f'), hexc('5e3d1f')
    post_wood = hexc('4f3319')
    hide, hide_dark, stitch = hexc('8a5a34'), hexc('5c3a20'), hexc('c9b08a')
    bark, bark_dark, ring, ring_dark = hexc('5a4028'), hexc('3b2918'), hexc('b89160'), hexc('8a6a40')
    iron, iron_dark, rivet = hexc('9aa1a9'), hexc('5f666e'), hexc('d4d9de')

    def faces(u, v, w, h, d):
        return {
            'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
        }

    def box(u, v, w, h, d, paint):
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    img.set(fx + x, fy + y, paint(face, x, y, fw, fh, fx + x, fy + y))

    def deck(face, x, y, fw, fh, ax, ay):
        # Planks run along the ram (z), so on top the seams are every 4 columns.
        if face in ('top', 'bottom'):
            c = oak_dark if x % 4 == 3 else oak
            if y % 9 == 4 and x % 4 == 1:
                c = iron_dark  # nail heads
            return jitter(c, ax, ay, 90, 0.08)
        return jitter(oak_dark if y == fh - 1 else shade(oak, 0.85), ax, ay, 91, 0.06)

    def roof(face, x, y, fw, fh, ax, ay):
        # Wet hides lashed over the frame: patches with pale stitching between them.
        if face in ('top', 'bottom'):
            if y % 7 == 0 or x % 6 == 0:
                return stitch if (x + y) % 2 == 0 else hide_dark
            patch = hide if ((x // 6) + (y // 7)) % 2 == 0 else shade(hide, 0.88)
            return jitter(patch, ax, ay, 92, 0.1)
        return jitter(hide_dark, ax, ay, 93, 0.06)

    def timber(face, x, y, fw, fh, ax, ay):
        return jitter(shade(post_wood, 1.1 if face == 'front' else 1.0), ax, ay, 94, 0.08)

    def log(face, x, y, fw, fh, ax, ay):
        if face in ('front', 'back'):  # end grain
            cx, cy = (fw - 1) / 2, (fh - 1) / 2
            r = max(abs(x - cx), abs(y - cy))
            return jitter(ring_dark if int(r) % 2 else ring, ax, ay, 95, 0.05)
        # Bark runs along the log.
        c = bark_dark if (x + (y % 3 == 0)) % 5 == 0 else bark
        return jitter(c, ax, ay, 96, 0.1)

    def head(face, x, y, fw, fh, ax, ay):
        if face == 'front':
            edge = x in (0, fw - 1) or y in (0, fh - 1)
            if (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                return rivet
            return jitter(iron_dark if edge else iron, ax, ay, 97, 0.05)
        return jitter(shade(iron, 0.85) if y % 3 else iron_dark, ax, ay, 98, 0.05)

    def wheel(face, x, y, fw, fh, ax, ay):
        if face in ('left', 'right'):  # the round face, 7x7
            cx = cy = 3
            dx, dy = abs(x - cx), abs(y - cy)
            if dx == 0 and dy == 0:
                return iron
            if (dx == 3 or dy == 3) and not (dx == 3 and dy == 3):
                return jitter(iron_dark, ax, ay, 99, 0.05)  # iron tyre
            if dx == 3 and dy == 3:
                return T
            if x == cx or y == cy:
                return post_wood  # spokes
            return jitter(oak, ax, ay, 100, 0.08)
        return jitter(iron_dark if y % 2 == 0 else shade(iron_dark, 0.8), ax, ay, 101, 0.05)

    box(0, 0, 16, 2, 26, deck)
    box(0, 28, 11, 1, 28, roof)
    box(0, 58, 4, 4, 32, log)
    box(84, 0, 6, 6, 3, head)
    box(84, 10, 2, 8, 2, timber)
    box(84, 26, 2, 7, 7, wheel)
    box(0, 96, 2, 2, 28, timber)
    img.save('entity/ram.png')
    # The raiders' ram: same cart, its hide roof dyed black with crimson stitching, like their tabards.
    hides = {hide: hexc('2a1416'), hide_dark: hexc('160a0b'), stitch: hexc('b2181e')}
    raider = Img(128, 128)
    for y in range(128):
        for x in range(128):
            p = img.get(x, y)
            roof = x < 78 and 28 <= y < 57  # the roof box's UV area: box(0, 28, 11, 1, 28)
            best = min(hides, key=lambda h: sum(abs(a - b) for a, b in zip(h[:3], p[:3]))) if roof and p and p[3] else None
            if best is not None and sum(abs(a - b) for a, b in zip(best[:3], p[:3])) < 36:
                raider.set(x, y, shade(hides[best], sum(p[:3]) / max(1, sum(best[:3]))))
            else:
                raider.set(x, y, p)
    raider.save('entity/raider_ram.png')

# ---------------------------------------------------------------- GUI

def gui():
    img = Img(256, 256)
    w, h = 176, 220
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black); img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black); img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white); img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white); img.set(w - 1 - i, y, dark)
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        img.set(x, y, T)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737')); img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white); img.set(x + size - 1, y + i, white)
        img.set(x + size - 1, y, hexc('8b8b8b')); img.set(x, y + size - 1, hexc('8b8b8b'))

    slot(7, 55)                        # deposit (TownCenterMenu.DEPOSIT_X/Y - 1)
    for i in range(5):
        slot(7 + i * 20, 77)           # training queue
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 135 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 193)
    for x in range(7, w - 7):          # separators
        img.set(x, 36, dark); img.set(x, 37, white)
    img.save('gui/town_center.png')


def barracks_gui():
    img = Img(256, 256)
    w, h = 176, 206
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        img.set(x, y, T)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)
        img.set(x + size - 1, y, hexc('8b8b8b'))
        img.set(x, y + size - 1, hexc('8b8b8b'))

    for i in range(5):
        slot(7 + i * 20, 77)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 121 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 179)
    for x in range(7, w - 7):
        img.set(x, 36, dark)
        img.set(x, 37, white)
    img.save('gui/barracks.png')


def archery_gui():
    img = Img(256, 256)
    w, h = 176, 206
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    olive = hexc('3d4a28')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        img.set(x, y, T)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)
        img.set(x + size - 1, y, hexc('8b8b8b'))
        img.set(x, y + size - 1, hexc('8b8b8b'))

    for i in range(5):
        slot(7 + i * 20, 77)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 121 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 179)
    for x in range(7, w - 7):
        img.set(x, 36, dark)
        img.set(x, 37, white)
        img.set(x, 38, olive)
    img.save('gui/archery_range.png')


def blacksmith_gui():
    img = Img(256, 256)
    w, h = 176, 236
    bg, white, dark, black = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('000000')
    iron = hexc('8d97a3')
    img.rect(0, 0, w, h, bg)
    for x in range(w):
        img.set(x, 0, black)
        img.set(x, h - 1, black)
    for y in range(h):
        img.set(0, y, black)
        img.set(w - 1, y, black)
    for i in (1, 2):
        for x in range(i, w - i):
            img.set(x, i, white)
            img.set(x, h - 1 - i, dark)
        for y in range(i, h - i):
            img.set(i, y, white)
            img.set(w - 1 - i, y, dark)
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        img.set(x, y, T)

    def slot(x, y, size=18):
        img.rect(x, y, x + size, y + size, hexc('8b8b8b'))
        for i in range(size):
            img.set(x + i, y, hexc('373737'))
            img.set(x, y + i, hexc('373737'))
            img.set(x + i, y + size - 1, white)
            img.set(x + size - 1, y + i, white)
        img.set(x + size - 1, y, hexc('8b8b8b'))
        img.set(x, y + size - 1, hexc('8b8b8b'))

    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 151 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 209)
    for x in range(7, w - 7):
        img.set(x, 36, dark)
        img.set(x, 37, white)
        img.set(x, 38, iron)
    img.save('gui/blacksmith.png')


def icon():
    """128x128 mod icon: the Town Center blueprint scaled up 8x."""
    import zlib as _z, struct as _s
    data = open(os.path.join(ROOT, 'item', 'town_center_blueprint.png'), 'rb').read()
    pos, idat = 8, b''
    while pos < len(data):
        n = _s.unpack('>I', data[pos:pos + 4])[0]
        if data[pos + 4:pos + 8] == b'IDAT':
            idat += data[pos + 8:pos + 8 + n]
        pos += 12 + n
    raw = _z.decompress(idat)
    out = Img(128, 128)
    for y in range(16):
        for x in range(16):
            i = y * 65 + 1 + x * 4
            c = tuple(raw[i:i + 4])
            if c[3]:
                out.rect(x * 8, y * 8, x * 8 + 8, y * 8 + 8, c)
    out.save('../icon.png')


if __name__ == '__main__':
    import skins
    skins.save_all()
    spawn_egg('militia_spawn_egg', hexc('a3262a'), hexc('c9ced4'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    spawn_egg('archer_spawn_egg', hexc('3f7a2e'), hexc('a07a45'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    spawn_egg('spearman_spawn_egg', hexc('1e4d8c'), hexc('c5ccd1'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    spawn_egg('crossbowman_spawn_egg', hexc('3d4a28'), hexc('c5ccd1'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    spawn_egg('knight_spawn_egg', hexc('f4f1ea'), hexc('a3262a'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    spawn_egg('ram_spawn_egg', hexc('8a5a2b'), hexc('c5ccd1'), [(6, 4), (9, 6), (5, 9), (10, 10), (7, 12)])
    blueprint('town_center_blueprint', hexc('2e5c9a'), pixel_map(TOWN_CENTER_ICON, 3, 4), seal=hexc('c9302c'))
    blueprint('house_blueprint', hexc('4f86c6'), pixel_map(HOUSE_ICON, 4, 5))
    blueprint('barracks_blueprint', hexc('3d6ea8'), pixel_map(BARRACKS_ICON, 4, 5), seal=hexc('c9a227'))
    blueprint('archery_range_blueprint', hexc('2f6b4f'), pixel_map(ARCHERY_ICON, 5, 4), seal=hexc('a3262a'))
    blueprint('blacksmith_blueprint', hexc('3a4450'), pixel_map(SMITHY_ICON, 5, 5), seal=hexc('c45a12'))
    blueprint('stable_blueprint', hexc('8a5a28'), pixel_map(STABLE_ICON, 4, 4), seal=hexc('a3262a'))
    blueprint('watch_tower_blueprint', hexc('5c6570'), pixel_map(TOWER_ICON, 4, 4), seal=hexc('c9a227'))
    blueprint('siege_workshop_blueprint', hexc('6b4423'), pixel_map(SIEGE_ICON, 4, 4), seal=hexc('c5ccd1'))
    handbook()
    playtest_guide()
    warlord_blade()
    relics()
    warhorse_whistle()
    warlord_plate()
    relic_pedestal()
    war_horn()
    trophy('bandit_trophy', hexc('5a3d24'), hexc('d9c9a3'), [(6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (9, 7), (7, 8), (8, 8), (6, 9), (9, 9)], hexc('8c939a'))
    trophy('fort_trophy', hexc('1d1d21'), hexc('b3202a'), [(6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (9, 7), (7, 8), (8, 8), (6, 9), (9, 9)], hexc('c5ccd1'))
    trophy('keep_trophy', hexc('2a1640'), hexc('7a4fb0'), [(6, 6), (8, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8)], hexc('f2c236'), hexc('f2c236'))
    ram_icon()
    war_map()
    camp_blocks()
    trap_blocks()
    props_blocks()
    contraption_blocks()
    town_center_blocks()
    hearth_blocks()
    barracks_blocks()
    archery_blocks()
    blacksmith_blocks()
    stable_blocks()
    watch_tower_blocks()
    siege_blocks()
    ram_texture()
    gui()
    barracks_gui()
    archery_gui()
    blacksmith_gui()
    stable_gui()
    watch_tower_gui()
    siege_gui()
    icon()
