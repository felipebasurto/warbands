"""Isometric preview of the gen_structures.py buildings and gen_camps.py camp layouts, drawn with the real vanilla block textures.

Usage (from this folder):  python3 render_structures.py out.png barracks blacksmith town_center ...
Each building is drawn twice, from the south-east and the south-west (the entrance is south).
Shapes are approximate: stairs, slabs, panes, fences, banners, lanterns and doors are sub-boxes;
anything unknown is a full cube, and a missing texture shows magenta. It's for judging massing and
materials before loading the game, not for final review (take an in-game screenshot for that).

Needs Pillow. Block textures are unpacked once from the Loom client jar into ../build/structure-preview/.
"""
import glob, os, sys, zipfile
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_structures as g

CACHE = os.path.join(HERE, '..', 'build', 'structure-preview')
VAN = os.path.join(CACHE, 'assets/minecraft/textures/block')
MOD = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'warbands', 'textures', 'block')


def unpack_textures():
    if os.path.isdir(VAN):
        return
    jars = glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar'))
    if not jars:
        sys.exit('minecraft-client.jar not found: run ./gradlew :warbands:build once so Loom downloads it')
    with zipfile.ZipFile(jars[0]) as jar:
        for name in jar.namelist():
            if name.startswith('assets/minecraft/textures/block/') and name.endswith('.png'):
                jar.extract(name, CACHE)
captured = {}
g.Builder.save = lambda self, fn: captured.__setitem__(fn[:-4], self)

_cache = {}


def tex(path):
    if path not in _cache:
        _cache[path] = Image.open(path).convert('RGBA').crop((0, 0, 16, 16)) if path and os.path.exists(path) else None
    return _cache[path]


def find(names):
    for n in names:
        for d in (VAN, MOD):
            p = os.path.join(d, n + '.png')
            if os.path.exists(p):
                return tex(p)
    return None


BASE = {'stone_brick': 'stone_bricks', 'mossy_stone_brick': 'mossy_stone_bricks', 'deepslate_brick': 'deepslate_bricks',
        'deepslate_tile': 'deepslate_tiles', 'tuff_brick': 'tuff_bricks', 'brick': 'bricks', 'nether_brick': 'nether_bricks',
        'mud_brick': 'mud_bricks', 'polished_blackstone_brick': 'polished_blackstone_bricks', 'cobblestone': 'cobblestone', 'snow_block': 'snow', 'soul_campfire': 'soul_campfire_log_lit',
        'chain': 'iron_chain', 'stone': 'stone', 'smooth_stone': 'smooth_stone', 'andesite': 'andesite', 'polished_andesite': 'polished_andesite',
        'dispenser': 'dispenser_front_vertical', 'sweet_berry_bush': 'sweet_berry_bush_stage3', 'tripwire_hook': 'tripwire_hook', 'tripwire': 'tripwire',
        'alarm_plate': 'alarm_plate', 'caltrops': 'caltrops', 'powder_keg': 'powder_keg'}
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'crimson', 'warped', 'bamboo']


def material(n):
    for suf in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button', '_trapdoor', '_door'):
        if n.endswith(suf):
            b = n[:-len(suf)]
            if b in WOODS:
                return b + '_planks'
            return BASE.get(b, b)
    if n.endswith('_wall_banner'):
        return n[:-12] + '_wool'
    if n.endswith('_banner') or n.endswith('_carpet') or n.endswith('_bed'):
        return n.rsplit('_', 1)[0] + '_wool'
    return BASE.get(n, n)


TINT = {'leaves': (72, 150, 58), 'grass_block': (110, 170, 70), 'fern': (100, 160, 60), 'vine': (90, 150, 60)}


def tinted(t, n):
    if t is None:
        return t
    for key, rgb in TINT.items():
        if key in n and 'azalea' not in n:
            r, g_, b, a = t.split()
            r, g_, b = [c.point(lambda v, m=m: int(v * m / 255)) for c, m in zip((r, g_, b), rgb)]
            return Image.merge('RGBA', (r, g_, b, a))
    return t


def textures(name):
    top, side, front = _textures(name)
    n = name.split(':')[1]
    return tinted(top, n), tinted(side, n), tinted(front, n)


def _textures(name):
    n = name.split(':')[1]
    if n in ('sticky_piston', 'piston', 'piston_head'):
        t = find(['piston_side', 'piston_top'])
        return t, t, t
    if n in ('redstone_wall_torch', 'redstone_torch'):
        t = find(['redstone_torch'])
        return t, t, t
    if n == 'campfire':
        t = find(['campfire_log_lit', 'campfire_log'])
        return t, t, t
    if n == 'grass_block':
        return find(['grass_block_top']), find(['grass_block_side']), find(['grass_block_side'])
    if n == 'podzol':
        return find(['podzol_top']), find(['podzol_side']), find(['podzol_side'])
    m = material(n)
    if n.endswith('_log') or n.endswith('_wood') or n in ('polished_basalt', 'basalt', 'hay_block'):
        side = find([n.replace('_wood', '_log') + '_side', n.replace('_wood', '_log'), n + '_side'])
        top = find([n + '_top']) or side
        return top, side, side
    if n.endswith('_trapdoor') or n.endswith('_door'):
        t = find([n, n + '_top', m])
        return t, t, t
    top = find([m + '_top', m])
    side = find([m + '_side', m + '_front', m])
    front = find([m + '_front', m + '_side', m])
    return top, side, front


def boxes(name, props):
    n = name.split(':')[1]
    P = dict(props)
    full = [(0, 0, 0, 1, 1, 1)]
    if n == 'air' or n.startswith('potted') or n in ('campfire', 'soul_campfire'):
        return [] if n == 'air' else [(0.3, 0, 0.3, 0.7, 0.4, 0.7)]
    if n.endswith('_stairs'):
        top = P.get('half') == 'top'
        slab = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        f = P.get('facing', 'north')
        back = {'north': (0, y0, 0, 1, y1, 0.5), 'south': (0, y0, 0.5, 1, y1, 1),
                'west': (0, y0, 0, 0.5, y1, 1), 'east': (0.5, y0, 0, 1, y1, 1)}[f]
        return [slab, back]
    if n.endswith('_slab'):
        t = P.get('type', 'bottom')
        return full if t == 'double' else [(0, 0.5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, 0.5, 1)]
    if n.endswith('_carpet'):
        return [(0, 0, 0, 1, 0.07, 1)]
    if n.endswith('glass_pane') or n == 'iron_bars':
        ns = P.get('north') == 'true'
        return [(0.44, 0, 0, 0.56, 1, 1)] if ns else [(0, 0, 0.44, 1, 1, 0.56)]
    if n.endswith('_fence') or n.endswith('_wall') and not n.endswith('_wall_banner') or n in ('chain', 'iron_chain'):
        out = [(0.37, 0, 0.37, 0.63, 1, 0.63)]
        for d, bx in (('north', (0.44, 0.4, 0, 0.56, 0.9, 0.5)), ('south', (0.44, 0.4, 0.5, 0.56, 0.9, 1)),
                      ('east', (0.5, 0.4, 0.44, 1, 0.9, 0.56)), ('west', (0, 0.4, 0.44, 0.5, 0.9, 0.56))):
            if P.get(d) in ('true', 'low', 'tall'):
                out.append(bx)
        return out
    if n.endswith('_wall_banner'):
        f = P.get('facing', 'north')
        return [{'north': (0.05, -0.8, 0.9, 0.95, 0.9, 1), 'south': (0.05, -0.8, 0, 0.95, 0.9, 0.1),
                 'east': (0, -0.8, 0.05, 0.1, 0.9, 0.95), 'west': (0.9, -0.8, 0.05, 1, 0.9, 0.95)}[f]]
    if n.endswith('_banner'):
        return [(0.45, 0, 0.45, 0.55, 1.9, 0.55), (0.05, 0.7, 0.45, 0.95, 1.85, 0.52)]
    if 'lantern' in n:
        y = 0.5 if P.get('hanging') == 'true' else 0
        return [(0.31, y, 0.31, 0.69, y + 0.45, 0.69)]
    if 'torch' in n:
        return [(0.44, 0, 0.44, 0.56, 0.62, 0.56)]
    if n.endswith('_bed'):
        return [(0, 0, 0, 1, 0.56, 1)]
    if n.endswith('_trapdoor'):
        return [(0, 0.8, 0, 1, 1, 1)] if P.get('half') == 'top' else [(0, 0, 0, 1, 0.19, 1)]
    if n.endswith('_door'):
        f = P.get('facing', 'north')
        return [{'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1),
                 'west': (0.81, 0, 0, 1, 1, 1)}[f]]
    if n in ('anvil', 'grindstone', 'bell', 'brewing_stand', 'target', 'scaffolding') or 'target' in n:
        return full if n == 'target' else [(0.15, 0, 0.15, 0.85, 0.9, 0.85)]
    return full


class Rot:
    """A builder's blocks turned k quarter-turns about the vertical axis (for the four preview angles)."""
    DIRS = ['north', 'east', 'south', 'west']

    def __init__(self, builder, k):
        sx, sy, sz = builder.size
        self.size = (sx, sy, sz) if k % 2 == 0 else (sz, sy, sx)
        self.blocks = {}
        for (x, y, z), (name, props) in builder.blocks.items():
            for _ in range(k):
                x, z = sz - 1 - z, x
                sx, sz = sz, sx
            props = dict(props)
            if 'facing' in props and props['facing'] in self.DIRS:
                props['facing'] = self.DIRS[(self.DIRS.index(props['facing']) + k) % 4]
            for _ in range(k):
                pass
            self.blocks[(x, y, z)] = (name, props)
            sx, sy, sz = builder.size


def render(builder, s=18, flip=False):
    sx, sy, sz = builder.size
    W = int((sx + sz) * s * 0.866) + 2 * s
    H = int((sx + sz) * s * 0.5 + (sy + 2) * s) + 2 * s
    img = Image.new('RGBA', (W, H), (150, 190, 230, 255))
    ox, oy = W / 2 + (sz - sx) * s * 0.866 / 2, (sy + 1) * s + s

    def P(x, y, z):
        if flip:
            x = sx - x
        return (ox + (x - z) * s * 0.866, oy + (x + z) * s * 0.5 - y * s)

    items = []
    for (x, y, z), (name, props) in builder.blocks.items():
        t = textures(name)
        for b in boxes(name, props):
            x0, y0, z0, x1, y1, z1 = b
            if flip:
                x0, x1 = 1 - x1, 1 - x0
            items.append(((x + x0 + x + x1) / 2 * (-1 if flip else 1) * 0 + (x + (x0 + x1) / 2) * (1 if not flip else 1),
                          x, y, z, b, t, name))
    # draw order: far first. Viewer at +x(+flip),+y,+z.
    def key(it):
        _, x, y, z, b, t, _ = it
        x0, y0, z0, x1, y1, z1 = b
        cx = x + (x0 + x1) / 2
        if flip:
            cx = sx - cx
        return cx + z + (z0 + z1) / 2 + (y + (y0 + y1) / 2) * 0.99
    items.sort(key=key)
    for _, x, y, z, b, (top, side, front), name in items:
        x0, y0, z0, x1, y1, z1 = b
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        visx = X1 if not flip else X0
        faces = [
            ([(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X0, Y1, Z1)], top, 1.0),
            ([(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y0, Z1), (X0, Y0, Z1)], front if z1 == 1 and name.split(':')[0] == 'warbands' else side, 0.82),
            ([(visx, Y1, Z1), (visx, Y1, Z0), (visx, Y0, Z0), (visx, Y0, Z1)], side, 0.64),
        ]
        for quad, t, shade in faces:
            pts = [P(*q) for q in quad]
            face(img, pts, t, shade)
    return img


def face(img, pts, t, shade):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    bx0, by0, bx1, by1 = int(min(xs)) - 1, int(min(ys)) - 1, int(max(xs)) + 2, int(max(ys)) + 2
    w, h = bx1 - bx0, by1 - by0
    if w <= 0 or h <= 0:
        return
    if t is None:
        t = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    # affine: texture (u,v) in 0..16 -> screen; p0=(0,0), p1=(16,0), p3=(0,16)
    (ax, ay), (bxx, byy), _, (dx, dy) = [(p[0] - bx0, p[1] - by0) for p in pts]
    a, b_, c = (bxx - ax) / 16, (dx - ax) / 16, ax
    d, e, f = (byy - ay) / 16, (dy - ay) / 16, ay
    det = a * e - b_ * d
    if abs(det) < 1e-9:
        return
    ia, ib, id_, ie = e / det, -b_ / det, -d / det, a / det
    inv = (ia, ib, -(ia * c + ib * f), id_, ie, -(id_ * c + ie * f))
    tt = t.resize((16, 16))
    warped = tt.transform((w, h), Image.AFFINE, inv, resample=Image.NEAREST)
    if shade < 1:
        r, g_, bb, al = warped.split()
        r, g_, bb = [ch.point(lambda v: int(v * shade)) for ch in (r, g_, bb)]
        warped = Image.merge('RGBA', (r, g_, bb, al))
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).polygon([(p[0] - bx0, p[1] - by0) for p in pts], fill=255)
    alpha = Image.composite(warped.split()[3], Image.new('L', (w, h), 0), mask)
    img.paste(warped, (bx0, by0), alpha)


if __name__ == '__main__':
    unpack_textures()
    out = sys.argv[1]
    names = [a for a in sys.argv[2:] if not a.startswith('--')]
    camp_modules = []
    for mod in ('camps_village', 'camps_feudal', 'camps_castle'):
        try:
            camp_modules.append(__import__(mod))
        except ImportError:
            pass
    for n in names:
        fn = getattr(g, n, None) or next(getattr(m, n) for m in camp_modules if hasattr(m, n))
        if n == 'town_center':
            fn('town_center.nbt', g.VILLAGE)
        else:
            fn()
    ims = []
    scale = 30 if '--big' in sys.argv else 18
    for n in names:
        b = captured[n]
        for k in ((0, 1, 2, 3) if '--big' in sys.argv else (0,)):
            r = Rot(b, k) if k else b
            ims.append(render(r, s=scale))
            if k == 0 and '--big' not in sys.argv:
                ims.append(render(b, s=scale, flip=True))
    W = sum(i.width for i in ims) + 10 * len(ims)
    H = max(i.height for i in ims)
    sheet = Image.new('RGBA', (W, H), (40, 40, 40, 255))
    x = 0
    for i in ims:
        sheet.paste(i, (x, 0)); x += i.width + 10
    sheet.save(out)
    print(out, sheet.size)
