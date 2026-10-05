"""Soldier skins (64x64 player layout), drawn as pixel maps.

Each body part gets a small map per face: one character per pixel, looked up in the unit's palette.
A palette value is a color, or a function (u, v) -> color for textures like quilting or chainmail.
Every pixel gets a little deterministic noise so flat areas read like vanilla cloth and metal.
Missing faces fall back to a mirror (left <-> right) or a single fill character.
The overlay (hat / jacket / sleeve / pants) layer is used for things that stand out: helmets,
hoods, the gambeson skirt, the quiver.
"""
from gen_textures import Img, hexc, shade, jitter

T = None  # transparent in overlay maps

PARTS = {
    'head': ((0, 0), (32, 0), (8, 8, 8)),
    'body': ((16, 16), (16, 32), (8, 12, 4)),
    'right_arm': ((40, 16), (40, 32), (4, 12, 4)),
    'left_arm': ((32, 48), (48, 48), (4, 12, 4)),
    'right_leg': ((0, 16), (0, 32), (4, 12, 4)),
    'left_leg': ((16, 48), (0, 48), (4, 12, 4)),
}


def faces_of(part, overlay):
    base, over, (w, h, d) = PARTS[part]
    ox, oy = over if overlay else base
    return {
        'top': (ox + d, oy, w, d), 'bottom': (ox + d + w, oy, w, d),
        'right': (ox, oy + d, d, h), 'front': (ox + d, oy + d, w, h),
        'left': (ox + d + w, oy + d, d, h), 'back': (ox + 2 * d + w, oy + d, w, h),
    }


def mirror(rows):
    return [r[::-1] for r in rows]


def draw(img, part, maps, palette, seed, overlay=False, noise=0.05):
    """maps: face -> list of strings, or a single character to fill the face with."""
    maps = dict(maps)
    if 'left' not in maps and 'right' in maps and not isinstance(maps['right'], str):
        maps['left'] = mirror(maps['right'])
    if 'right' not in maps and 'left' in maps and not isinstance(maps['left'], str):
        maps['right'] = mirror(maps['left'])
    for face, (fx, fy, fw, fh) in faces_of(part, overlay).items():
        rows = maps.get(face, '.' if overlay else None)
        if rows is None:
            raise ValueError(f'{part} {face} has no map')
        if isinstance(rows, str):
            rows = [rows * fw] * fh
        if len(rows) != fh or any(len(r) != fw for r in rows):
            raise ValueError(f'{part} {face}: expected {fw}x{fh}, got {[len(r) for r in rows]}')
        for v, row in enumerate(rows):
            for u, ch in enumerate(row):
                if ch == '.':
                    continue
                c = palette[ch]
                if callable(c):
                    c = c(u, v)
                img.set(fx + u, fy + v, jitter(c, fx + u, fy + v, seed, noise))


# ---------------------------------------------------------------- shared materials

def chainmail(dark, light):
    """Vanilla-like mail: rows of ring highlights, offset every other row, on a mid-grey weave."""
    mid = tuple((a + b) // 2 for a, b in zip(dark, light))

    def ring(u, v):
        if v % 2 == 0:
            return light if u % 2 == 0 else mid
        return mid if u % 2 == 0 else dark
    return ring


def quilted(cloth, seam):
    """Gambeson: vertical seams every two columns."""
    return lambda u, v: seam if u % 4 == 1 and v % 3 != 0 else cloth


def common(p):
    """Face colors every human shares; units override skin and hair."""
    base = {
        'W': hexc('eeeeee'),  # eye white
        'E': hexc('3b5a8c'),  # iris
        'm': hexc('8a4c3e'),  # mouth
    }
    base.update(p)
    s = base['s']
    base.setdefault('S', shade(s, 0.85))  # skin shadow
    base.setdefault('n', shade(s, 0.78))  # nose
    base.setdefault('b', shade(base['h'], 0.75))  # brows
    base.setdefault('H', shade(base['h'], 0.75))  # hair shadow
    return base


# Heads shared by the bare-faced units. Helmets and hoods go on the overlay.
BEARD_FRONT = [
    'hhhhhhhh',
    'hhhhhhhh',
    'hsssssSh',
    'sbbssbbs',
    'sWEssEWs',
    'SssnnssS',
    'BBsmmsBB',
    'BBBBBBBB',
]
BEARD_RIGHT = [
    'hhhhhhhh',
    'hhhhhhhh',
    'Hhhhhhss',
    'HhhhhsSs',
    'HhhSSsss',
    'HhhSSsBB',
    'HhhsBBBB',
    'HhhBBBBB',
]
SHAVEN_FRONT = [
    'hhhhhhhh',
    'hhhhhhhh',
    'hsssssSh',
    'sbbssbbs',
    'sWEssEWs',
    'SssnnssS',
    'sssmmsss',
    'SssssssS',
]
SHAVEN_RIGHT = [
    'hhhhhhhh',
    'hhhhhhhh',
    'Hhhhhhss',
    'HhhhhsSs',
    'HhhSSsss',
    'HhhSSsss',
    'Hhhsssss',
    'HhhSssss',
]
HAIR_BACK = ['hhhhhhhh'] * 6 + ['HhhhhhhH', 'HHHHHHHH']


def head(img, palette, seed, beard=True):
    draw(img, 'head', {
        'front': BEARD_FRONT if beard else SHAVEN_FRONT,
        'right': BEARD_RIGHT if beard else SHAVEN_RIGHT,
        'back': HAIR_BACK,
        'top': 'h',
        'bottom': 'B' if beard else 'S',
    }, palette, seed)


# ---------------------------------------------------------------- militia

def militia_skin():
    """Village levy: steel kettle hat, red quilted gambeson with a gold cross, wool hose, wrapped boots."""
    img = Img(64, 64)
    red, seam = hexc('a82b2b'), hexc('7e1f21')
    p = common({
        's': hexc('c68d67'), 'h': hexc('4b2e1a'), 'B': hexc('3d2415'),
        'r': quilted(red, seam), 'R': shade(red, 0.72), 'q': shade(red, 1.12),
        'g': hexc('e0b030'), 'G': hexc('9c7414'),
        'l': hexc('6b4a2b'), 'L': hexc('4a321d'), 'i': hexc('c9ced4'),
        'k': hexc('b8bec5'), 'K': hexc('8a9199'), 'x': hexc('e4e8ec'), 'y': hexc('676e76'),
        'o': hexc('463526'), 'O': hexc('33261b'), 'f': hexc('75654b'), 'F': hexc('5a4d3a'),
        'c': hexc('7a5a3a'),
    })
    head(img, p, 1)
    draw(img, 'head', {  # kettle hat: dome plus a brim that sits over the brows
        'front': ['xkkkkkkk', 'kkkkkkkK', 'yKKKKKKy'] + ['........'] * 5,
        'right': ['kkkkkkkk', 'kkkkkkkK', 'yKKKKKKy'] + ['........'] * 5,
        'back': ['kkkkkkkk', 'KkkkkkkK', 'yKKKKKKy'] + ['........'] * 5,
        'top': ['KkkkkkkK', 'kkxxkkkk', 'kxxkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkKk', 'kkkkkKkk', 'KkkkkkkK'],
        'bottom': '.',
    }, p, 2, overlay=True)
    draw(img, 'body', {
        'front': [
            'RrrssrrR',
            'RrrSSrrR',
            'RrrggrrR',
            'RrggggrR',
            'RrrggrrR',
            'RrrggrrR',
            'RrrGGrrR',
            'RrrrrrrR',
            'LlliilLL',
            'RrrrrrrR',
            'RrrrrrrR',
            'RRRRRRRR',
        ],
        'back': ['RrrrrrrR'] * 8 + ['LllllllL', 'RrrrrrrR', 'RrrrrrrR', 'RRRRRRRR'],
        'right': ['rrrr'] * 8 + ['LLLL', 'rrrr', 'rrrr', 'RRRR'],
        'top': 'q', 'bottom': 'R',
    }, p, 3)
    arm = {
        'front': ['qqqq', 'rrrr', 'rrrr', 'rrrr', 'rrrr', 'rrrr', 'RRRR', 'llll', 'llll', 'LllL', 'LLLL', 'LLLL'],
        'back': ['rrrr'] * 6 + ['RRRR', 'llll', 'llll', 'LllL', 'LLLL', 'LLLL'],
        'right': ['rrrr'] * 6 + ['RRRR', 'llll', 'llll', 'LllL', 'LLLL', 'LLLL'],
        'top': 'q', 'bottom': 'L',
    }
    draw(img, 'right_arm', arm, p, 4)
    draw(img, 'left_arm', arm, p, 5)
    leg = {
        'front': ['oooo', 'oooo', 'oOoo', 'oooo', 'oooo', 'ooOo', 'oooo', 'ffff', 'FfFf', 'ffff', 'FfFf', 'cccc'],
        'back': ['oooo', 'oooo', 'oooo', 'oooo', 'Oooo', 'oooo', 'oooo', 'ffff', 'fFfF', 'ffff', 'fFfF', 'cccc'],
        'right': ['Oooo', 'oooo', 'oooo', 'ooOo', 'oooo', 'oooo', 'oooo', 'ffff', 'FfFf', 'ffff', 'FfFf', 'cccc'],
        'top': 'o', 'bottom': 'O',
    }
    draw(img, 'right_leg', leg, p, 6)
    draw(img, 'left_leg', leg, p, 7)
    skirt = {  # gambeson skirt hanging over the thighs (pants layer)
        'front': ['rrrr', 'rrrr', 'RRRR'] + ['....'] * 9,
        'back': ['rrrr', 'rrrr', 'RRRR'] + ['....'] * 9,
        'right': ['rrrr', 'rrrr', 'RRRR'] + ['....'] * 9,
        'top': '.', 'bottom': '.',
    }
    draw(img, 'right_leg', skirt, p, 8, overlay=True)
    draw(img, 'left_leg', skirt, p, 9, overlay=True)
    return img


# ---------------------------------------------------------------- archer

def archer_skin():
    """Woodland bowman: green hood and short cape, linen shirt, baldric, quiver on the back, leather bracer."""
    img = Img(64, 64)
    green = hexc('3f7a2e')
    p = common({
        's': hexc('e0ac86'), 'h': hexc('b58a3e'), 'B': hexc('b58a3e'), 'E': hexc('3f6b2a'),
        'g': green, 'G': shade(green, 0.72), 'j': shade(green, 1.18), 'd': shade(green, 0.55),
        't': hexc('8f8a6c'), 'T': hexc('6f6b52'),
        'l': hexc('7a5634'), 'L': hexc('553a22'), 'a': hexc('a07a45'),
        'q': hexc('6b4a2b'), 'Q': hexc('4a321d'), 'f': hexc('f0f0f0'), 'F': hexc('c8352f'), 'z': hexc('d8d2c0'),
        'o': hexc('5c4a36'), 'O': hexc('3f3226'), 'c': hexc('3a2a1c'),
    })
    head(img, p, 11, beard=False)
    draw(img, 'head', {  # hood: open face, shadow under the rim, point at the back
        'front': ['jggggggg', 'gG....Gg', 'G......G', 'G......G', 'g......g', 'g......g', 'G......G', 'GG....GG'],
        'right': ['gggggggj', 'ggggggjg', 'gggggggG', 'ggggggG.', 'ggggggG.', 'gggggG..', 'GggggG..', 'GGGGGG..'],
        'back': ['gggggggg', 'gggggggg', 'ggGggggg', 'gggggGgg', 'gggggggg', 'gGgggggg', 'GggggggG', 'GGGGGGGG'],
        'top': ['gggggggg', 'gjjggggg', 'gjgggggg', 'gggggggg', 'gggggggg', 'ggggggGg', 'gggggGgg', 'GggggggG'],
        'bottom': '.',
    }, p, 12, overlay=True)
    draw(img, 'body', {
        'front': [
            'gGssssGg',
            'ggGSSGgg',
            'tttttlTt',
            'ttttltTt',
            'tttltttt',
            'ttlttttt',
            'tlTttttt',
            'lTtttttT',
            'LLLaaLLL',
            'tttttttt',
            'TttttttT',
            'TTTTTTTT',
        ],
        'back': [
            'gggggggg',
            'gGgqqfFg',
            'ttGqqzft',
            'ttlqqQtt',
            'tlTqqQtt',
            'lTtqqQtt',
            'ttlqqQtt',
            'tlTqqQtt',
            'LLLQQLLL',
            'tttttttt',
            'TttttttT',
            'TTTTTTTT',
        ],
        'right': ['gggg', 'Gggg', 'tttt', 'tttt', 'tttt', 'tttt', 'tttt', 'tttt', 'LLLL', 'tttt', 'TttT', 'TTTT'],
        'top': 'g', 'bottom': 'T',
    }, p, 13)
    draw(img, 'body', {  # the quiver's arrows stick out above the shoulders
        'front': '.', 'right': '.', 'left': '.', 'bottom': '.',
        'back': ['....fF..', '....zf..'] + ['........'] * 10,
        'top': ['........', '........', '........', '....fF..'],
    }, p, 14, overlay=True)
    right_arm = {
        'front': ['gggg', 'GggG', 'tttt', 'tttt', 'tttt', 'TttT', 'tttt', 'llll', 'LllL', 'llll', 'ssss', 'SssS'],
        'back': ['gggg', 'GggG', 'tttt', 'tttt', 'tttt', 'TttT', 'tttt', 'llll', 'LllL', 'llll', 'ssss', 'SssS'],
        'right': ['gggg', 'GggG', 'tttt', 'tttt', 'tttt', 'TttT', 'tttt', 'llll', 'LllL', 'llll', 'ssss', 'SssS'],
        'top': 'g', 'bottom': 's',
    }
    left_arm = dict(right_arm)
    left_arm['front'] = ['gggg', 'GggG', 'tttt', 'tttt', 'tttt', 'TttT', 'aaaa', 'llll', 'aaaa', 'llll', 'ssss', 'SssS']  # bow-hand bracer
    draw(img, 'right_arm', right_arm, p, 15)
    draw(img, 'left_arm', left_arm, p, 16)
    leg = {
        'front': ['oooo', 'oooo', 'oOoo', 'oooo', 'oooo', 'ooOo', 'oooo', 'llll', 'LllL', 'llll', 'LLLL', 'cccc'],
        'back': ['oooo', 'oooo', 'oooo', 'Oooo', 'oooo', 'oooo', 'oooo', 'llll', 'LllL', 'llll', 'LLLL', 'cccc'],
        'right': ['oooo', 'Oooo', 'oooo', 'oooo', 'ooOo', 'oooo', 'oooo', 'llll', 'LllL', 'llll', 'LLLL', 'cccc'],
        'top': 'o', 'bottom': 'c',
    }
    draw(img, 'right_leg', leg, p, 17)
    draw(img, 'left_leg', leg, p, 18)
    return img


# ---------------------------------------------------------------- spearman

def spearman_skin():
    """Feudal infantry: conical nasal helm over a mail coif, blue tabard over mail, iron-shod boots."""
    img = Img(64, 64)
    blue = hexc('2d5a9e')
    mail = chainmail(hexc('596068'), hexc('a0a7af'))
    p = common({
        's': hexc('b98260'), 'h': hexc('2e1f14'), 'B': hexc('2e1f14'), 'E': hexc('4a3020'),
        'M': mail, 'N': hexc('4c5259'),
        'u': blue, 'U': shade(blue, 0.7), 'v': shade(blue, 1.2), 'y': hexc('e8c547'), 'Y': hexc('b08f24'),
        'k': hexc('b3bac2'), 'K': hexc('7e868f'), 'x': hexc('e2e6ea'),
        'l': hexc('5e4128'), 'L': hexc('402c1b'), 'i': hexc('c9ced4'),
        'o': hexc('3b3530'), 'O': hexc('2a2622'), 'c': hexc('26211d'),
    })
    draw(img, 'head', {  # face framed by the mail coif
        'front': ['MMMMMMMM', 'MMMMMMMM', 'MsssssSM', 'MbbssbbM', 'MWEssEWM', 'MssnnssM', 'MSBmmBSM', 'MMBBBBMM'],
        'right': ['MMMMMMMM'] * 8,
        'back': ['MMMMMMMM'] * 7 + ['NNNNNNNN'],
        'top': 'M', 'bottom': 'N',
    }, p, 21)
    draw(img, 'head', {  # spangenhelm with a nasal bar down the nose
        'front': ['KkkxxkkK', 'kkkkkkkk', 'KKKkkKKK', '...kK...', '...kK...', '...kK...', '........', '........'],
        'right': ['kkkkkkkk', 'kkkkkkkk', 'KKKKKKKK'] + ['........'] * 5,
        'back': ['kkkKkkkk', 'kkkkkkkk', 'KKKKKKKK'] + ['........'] * 5,
        'top': ['KkkkkkkK', 'kkkxkkkk', 'kkxxxkkk', 'kkkxxkkk', 'KkkkkkkK', 'kKkkkkKk', 'kkKkkKkk', 'KkkKKkkK'],
        'bottom': '.',
    }, p, 22, overlay=True)
    draw(img, 'body', {
        'front': [
            'MMMMMMMM',
            'MuuuuuuM',
            'MuuyyuuM',
            'MuyyyyuM',
            'MuuyyuuM',
            'MuuyyuuM',
            'MuuuuuuM',
            'MuuuuuuM',
            'LlliilLL',
            'MUuuuuUM',
            'MUuuuuUM',
            'NUUUUUUN',
        ],
        'back': ['MMMMMMMM', 'MuuuuuuM', 'MuuuuuuM', 'MuuuuuuM', 'MuuuuuuM', 'MuuuuuuM', 'MuuuuuuM', 'MuuuuuuM',
                 'LLLLLLLL', 'MUuuuuUM', 'MUuuuuUM', 'NUUUUUUN'],
        'right': ['MMMM'] * 8 + ['LLLL', 'MMMM', 'MMMM', 'NNNN'],
        'top': 'M', 'bottom': 'N',
    }, p, 23)
    arm = {
        'front': ['MMMM'] * 8 + ['llll', 'LllL', 'llll', 'LLLL'],
        'back': ['MMMM'] * 8 + ['llll', 'LllL', 'llll', 'LLLL'],
        'right': ['MMMM'] * 8 + ['llll', 'LllL', 'llll', 'LLLL'],
        'top': 'M', 'bottom': 'L',
    }
    draw(img, 'right_arm', arm, p, 24)
    draw(img, 'left_arm', arm, p, 25)
    leg = {
        'front': ['oooo', 'oooo', 'oOoo', 'oooo', 'oooo', 'ooOo', 'LLLL', 'llll', 'lLll', 'llll', 'LLLL', 'cccc'],
        'back': ['oooo', 'oooo', 'oooo', 'Oooo', 'oooo', 'oooo', 'LLLL', 'llll', 'llLl', 'llll', 'LLLL', 'cccc'],
        'right': ['oooo', 'Oooo', 'oooo', 'oooo', 'ooOo', 'oooo', 'LLLL', 'llll', 'lLll', 'llll', 'LLLL', 'cccc'],
        'top': 'o', 'bottom': 'c',
    }
    draw(img, 'right_leg', leg, p, 26)
    draw(img, 'left_leg', leg, p, 27)
    tabard = {  # tabard skirt over the thighs
        'front': ['uuuu', 'uUuu', 'UUUU'] + ['....'] * 9,
        'back': ['uuuu', 'uuUu', 'UUUU'] + ['....'] * 9,
        'right': ['uuuu', 'uuuu', 'UUUU'] + ['....'] * 9,
        'top': '.', 'bottom': '.',
    }
    draw(img, 'right_leg', tabard, p, 28, overlay=True)
    draw(img, 'left_leg', tabard, p, 29, overlay=True)
    return img


# ---------------------------------------------------------------- crossbowman

def crossbowman_skin():
    """Feudal marksman: dark chapel hat, studded leather brigandine over olive sleeves, bolt case at the hip."""
    img = Img(64, 64)
    leather = hexc('7a5232')
    p = common({
        's': hexc('d19a74'), 'h': hexc('6b3b1f'), 'B': hexc('6b3b1f'), 'E': hexc('4c6b3a'),
        'l': leather, 'L': shade(leather, 0.7), 'j': shade(leather, 1.18), 'd': hexc('d9b54a'),
        'e': hexc('5a6b34'), 'r': hexc('3d4a22'),
        'k': hexc('7f868e'), 'K': hexc('565c63'), 'x': hexc('b7bdc4'),
        'a': hexc('4a321d'), 'A': hexc('33220f'), 'q': hexc('5b3d22'), 'f': hexc('d8d2c0'),
        'o': hexc('4d4a3a'), 'O': hexc('37352a'), 'c': hexc('2a2118'), 'i': hexc('b8bec5'),
    })
    head(img, p, 31, beard=False)
    draw(img, 'head', {  # chapel-de-fer: wide dark steel brim
        'front': ['xkkkkkkK', 'kkkkkkkK', 'KKKKKKKK', '........', '........', '........', '........', '........'],
        'right': ['kkkkkkkk', 'kkkkkkkK', 'KKKKKKKK'] + ['........'] * 5,
        'back': ['kkkkkkkk', 'KkkkkkkK', 'KKKKKKKK'] + ['........'] * 5,
        'top': ['KkkkkkkK', 'kxxkkkkk', 'kxkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkKk', 'kkkkkkkk', 'KkkkkkkK'],
        'bottom': '.',
    }, p, 32, overlay=True)
    draw(img, 'body', {
        'front': [
            'eessssee',
            'eLjSSjLe',
            'eljlljle',
            'eldlldle',
            'eljlljle',
            'eldlldle',
            'eljlljle',
            'eLllllLe',
            'aaAiiAaa',
            'LlllllLL',
            'LldlldlL',
            'LLLLLLLL',
        ],
        'back': ['eeeeeeee', 'eLllllLe', 'elllllle', 'eldlldle', 'elllllle', 'eldlldle', 'elllllle', 'eLllllLe',
                 'aaaaaaaa', 'LlllllLL', 'LldlldlL', 'LLLLLLLL'],
        'right': ['eeee', 'eeee', 'llll', 'llll', 'llll', 'llll', 'llll', 'LLLL', 'aaaa', 'llll', 'LllL', 'LLLL'],
        'top': 'e', 'bottom': 'L',
    }, p, 33)
    arm = {
        'front': ['llll', 'LllL', 'eeee', 'eeee', 'eeee', 'reer', 'eeee', 'aaaa', 'AaaA', 'aaaa', 'ssss', 'SssS'],
        'back': ['llll', 'LllL', 'eeee', 'eeee', 'eeee', 'reer', 'eeee', 'aaaa', 'AaaA', 'aaaa', 'ssss', 'SssS'],
        'right': ['llll', 'LllL', 'eeee', 'eeee', 'eeee', 'reer', 'eeee', 'aaaa', 'AaaA', 'aaaa', 'ssss', 'SssS'],
        'top': 'l', 'bottom': 's',
    }
    draw(img, 'right_arm', arm, p, 34)
    draw(img, 'left_arm', arm, p, 35)
    leg = {
        'front': ['oooo', 'oooo', 'oOoo', 'oooo', 'oooo', 'ooOo', 'oooo', 'aaaa', 'AaaA', 'aaaa', 'AAAA', 'cccc'],
        'back': ['oooo', 'oooo', 'oooo', 'Oooo', 'oooo', 'oooo', 'oooo', 'aaaa', 'AaaA', 'aaaa', 'AAAA', 'cccc'],
        'right': ['oooo', 'Oooo', 'oooo', 'oooo', 'ooOo', 'oooo', 'oooo', 'aaaa', 'AaaA', 'aaaa', 'AAAA', 'cccc'],
        'top': 'o', 'bottom': 'c',
    }
    draw(img, 'right_leg', leg, p, 36)
    draw(img, 'left_leg', leg, p, 37)
    draw(img, 'right_leg', {  # bolt case on the right hip, bolt fletchings showing
        'right': ['fqfq', 'qqqq', 'qQqq', 'qqqq', 'QqqQ', 'QQQQ'] + ['....'] * 6,
        'front': ['f...', 'q...', 'q...', 'q...', 'Q...', 'Q...'] + ['....'] * 6,
        'back': ['...f', '...q', '...q', '...q', '...Q', '...Q'] + ['....'] * 6,
        'left': '.', 'top': ['....', '....', '....', 'fqf.'], 'bottom': '.',
    }, dict(p, Q=hexc('3f2a17')), 38, overlay=True)
    return img


# ---------------------------------------------------------------- knight

def knight_skin():
    """Castle cavalry: great helm with a gold cross on the visor, white surcoat with a red cross, full mail and plate."""
    img = Img(64, 64)
    mail = chainmail(hexc('596068'), hexc('a6adb5'))
    p = common({
        's': hexc('c68d67'), 'h': hexc('3a2a1c'), 'B': hexc('3a2a1c'),
        'k': hexc('c3c9d0'), 'K': hexc('8d949c'), 'x': hexc('eef1f4'), 'z': hexc('1b1d20'), 'y': hexc('5b6168'),
        'g': hexc('e0b030'), 'G': hexc('a8801c'),
        'w': hexc('ecebe4'), 'V': hexc('c9c6ba'), 'r': hexc('b3262a'), 'R': hexc('7e1a1d'),
        'M': mail, 'N': hexc('4c5259'),
        'l': hexc('5e4128'), 'L': hexc('402c1b'), 'i': hexc('d6b04a'),
        'c': hexc('50565d'),
    })
    draw(img, 'head', {  # great helm: eye slit, breathing holes, brass cross on the visor
        'front': ['KkkkkkkK', 'kkxggkkk', 'kkkggkkk', 'zzzggzzz', 'kgggggGk', 'kkkggkkk', 'kzkGGkzk', 'KkkkkkkK'],
        'right': ['kkkkkkkK', 'kkkkkkxk', 'kkkkkkkk', 'kkkkkkzz', 'kkkyykkk', 'kkkyykkk', 'kkkkkkzk', 'KKKKKKKK'],
        'back': ['KkkkkkkK', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkKkkkk', 'kkkkkkkk', 'kkkkkkkk', 'KKKKKKKK'],
        'top': ['KkkkkkkK', 'kkxxkkkk', 'kxxkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkK', 'kkkkkkKk', 'KkkkkkkK'],
        'bottom': 'K',
    }, p, 41, noise=0.04)
    draw(img, 'body', {
        'front': [
            'MwwwwwwM',
            'wwwrrwww',
            'wwwrrwww',
            'wrrrrrrw',
            'wRRrrRRw',
            'wwwrrwww',
            'wwwrrwww',
            'wwwrrwww',
            'LllilllL',
            'wwwrrwww',
            'VwwrrwwV',
            'VVVRRVVV',
        ],
        'back': ['MwwwwwwM', 'wwwwwwww', 'wwwrrwww', 'wrrrrrrw', 'wRRrrRRw', 'wwwrrwww', 'wwwrrwww', 'wwwwwwww',
                 'LLLLLLLL', 'wwwwwwww', 'VwwwwwwV', 'VVVVVVVV'],
        'right': ['MMMM', 'wwww', 'wwww', 'wwww', 'wwww', 'wwww', 'wwww', 'wwww', 'LLLL', 'wwww', 'VwwV', 'VVVV'],
        'top': 'M', 'bottom': 'V',
    }, p, 42, noise=0.035)
    arm = {  # plate pauldron, mail sleeve, plate gauntlet
        'front': ['xkkk', 'kkkK', 'KKKK', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'kkkk', 'kkkK', 'KkkK', 'KKKK'],
        'back': ['kkkk', 'kkkK', 'KKKK', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'kkkk', 'kkkK', 'KkkK', 'KKKK'],
        'right': ['kkkk', 'kkkK', 'KKKK', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'MMMM', 'kkkk', 'kkkK', 'KkkK', 'KKKK'],
        'top': 'k', 'bottom': 'K',
    }
    draw(img, 'right_arm', arm, p, 43)
    draw(img, 'left_arm', arm, p, 44)
    leg = {  # mail chausses, plate knee, steel sabatons
        'front': ['MMMM', 'MMMM', 'MMMM', 'MMMM', 'xkkK', 'KkkK', 'MMMM', 'MMMM', 'MMMM', 'kkkk', 'kkkK', 'cccc'],
        'back': ['MMMM'] * 9 + ['kkkk', 'KkkK', 'cccc'],
        'right': ['MMMM', 'MMMM', 'MMMM', 'MMMM', 'kkkK', 'KkkK', 'MMMM', 'MMMM', 'MMMM', 'kkkk', 'kkkK', 'cccc'],
        'top': 'M', 'bottom': 'c',
    }
    draw(img, 'right_leg', leg, p, 45)
    draw(img, 'left_leg', leg, p, 46)
    surcoat = {  # surcoat skirt split at the front for riding
        'front': ['wwwr', 'wwww', 'VVVV'] + ['....'] * 9,
        'back': ['wwww', 'wwww', 'VVVV'] + ['....'] * 9,
        'right': ['wwww', 'wwww', 'VVVV'] + ['....'] * 9,
        'top': '.', 'bottom': '.',
    }
    draw(img, 'right_leg', surcoat, p, 47, overlay=True)
    surcoat_left = dict(surcoat, front=['rwww', 'wwww', 'VVVV'] + ['....'] * 9)
    draw(img, 'left_leg', surcoat_left, p, 48, overlay=True)
    return img


# ---------------------------------------------------------------- warlord

def warlord_skin():
    """Castle camps' chief: a horned dark-iron helm, a fur collar, a crimson war sash and bare scarred arms."""
    img = Img(64, 64)
    mail = chainmail(hexc('3a3f45'), hexc('6d747e'))
    p = common({
        's': hexc('b5764f'), 'h': hexc('241812'), 'B': hexc('241812'),
        'k': hexc('565d66'), 'K': hexc('2f333a'), 'z': hexc('101114'), 'o': hexc('d43a2e'),
        'x': hexc('e8e0cc'), 'X': hexc('b8ad92'), 'i': hexc('d8a833'), 'r': hexc('b3262a'), 'R': hexc('6e1417'),
        'f': hexc('6b4a2e'), 'F': hexc('4a3018'), 'M': mail, 'c': hexc('8a5f43'),
        'l': hexc('5e4128'), 'L': hexc('3d2a1a'),
    })
    draw(img, 'head', {  # horned helm: bone horns out the top corners, red eyes behind the visor slit
        'front': [
            'xxKKKKxx',
            'xkkkkkkx',
            'KkkkkkkK',
            'kzzzzzzk',
            'kzWWWWzk',
            'kkkkkkkk',
            'kfKffKfk',
            'KKKKKKKK',
        ],
        'right': [
            'xxkkkkkk',
            'Kkkkkkkk',
            'kkkkkkkk',
            'zzkkkkkk',
            'zzkkkkkk',
            'kkkkkkkk',
            'ffkkkkkk',
            'KKKKKKKK',
        ],
        'back': [
            'KKKKKKKK',
            'kkkkkkkk',
            'kkkkkkkk',
            'kkkkkkkk',
            'kkkkkkkk',
            'kkkkkkkk',
            'fKKffKKf',
            'KKKKKKKK',
        ],
        'top': ['xxkkkkxx', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'kkkkkkkk', 'xxkkkkxx'],
        'bottom': 'K',
    }, p, 61, noise=0.04)
    draw(img, 'body', {
        'front': [
            'ffffffff',
            'fKKKKKKf',
            'kkrkkkkk',
            'kkkrkkkk',
            'kkkkrrkk',
            'kkkkkrrk',
            'kikkkkkr',
            'KKiKKiKK',
            'kMMMMMMk',
            'kMMMMMMk',
            'kMMMMMMk',
            'KKKKKKKK',
        ],
        'back': [
            'ffffffff',
            'fKKKKKKf',
            'kKKKKKKk',
            'kKKKKKKk',
            'kKKKKKKk',
            'kKKKKKKk',
            'kKKKKKKk',
            'KKKKKKKK',
            'kMMMMMMk',
            'kMMMMMMk',
            'kMMMMMMk',
            'KKKKKKKK',
        ],
        'right': ['fKKf', 'kKKk', 'kKKk', 'kKKk', 'krKk', 'kKrk', 'kKKi', 'KKKK', 'kMMk', 'kMMk', 'kMMk', 'KKKK'],
        'top': 'f', 'bottom': 'K',
    }, p, 62, noise=0.04)
    arm = {  # bare, scarred arms ending in an iron bracer
        'front': ['ssss', 'scsc', 'ssss', 'ssss', 'ssss', 'scss', 'ssss', 'ssss', 'KKKK', 'kKKk', 'kKKk', 'KKKK'],
        'back': ['ssss', 'sscs', 'ssss', 'ssss', 'ssss', 'sscs', 'ssss', 'ssss', 'KKKK', 'kKKk', 'kKKk', 'KKKK'],
        'right': ['ssss', 'scsc', 'ssss', 'ssss', 'ssss', 'scss', 'ssss', 'ssss', 'KKKK', 'kKKk', 'kKKk', 'KKKK'],
        'top': 's', 'bottom': 'K',
    }
    draw(img, 'right_arm', arm, p, 63)
    draw(img, 'left_arm', arm, p, 64)
    leg = {  # dark leathers, fur top, iron boots
        'front': ['FFFF', 'lLLl', 'llll', 'llll', 'llll', 'llll', 'lKKl', 'KKKK', 'kKKk', 'kKKk', 'kKKk', 'KKKK'],
        'back': ['FFFF', 'lLLl', 'llll', 'llll', 'llll', 'llll', 'lKKl', 'KKKK', 'kKKk', 'kKKk', 'kKKk', 'KKKK'],
        'right': ['FFFF', 'lLLl', 'llll', 'llll', 'llll', 'llll', 'lKKl', 'KKKK', 'kKKk', 'kKKk', 'kKKk', 'KKKK'],
        'top': 'F', 'bottom': 'K',
    }
    draw(img, 'right_leg', leg, p, 65)
    draw(img, 'left_leg', leg, p, 66)
    mantle = {  # pelt cloak over the shoulders
        'front': ['ffffffff', 'FFfFFfFF', 'ff....ff'] + ['....' * 2] * 9,
        'back': ['ffffffff', 'FFfFFfFF', 'fF....Ff', 'ff....ff'] + ['....' * 2] * 8,
        'right': ['ffff', 'FFFF', 'ff..'] + ['....'] * 9,
        'top': 'f', 'bottom': '.',
    }
    draw(img, 'body', mantle, p, 67, overlay=True)
    return img


SKINS = {
    'militia': militia_skin,
    'archer': archer_skin,
    'spearman': spearman_skin,
    'crossbowman': crossbowman_skin,
    'knight': knight_skin,
    'warlord': warlord_skin,
}


def raider(img, white_cloth=False):
    """The raider faction's version of a skin: dyed cloth (tabards, hoods, capes) turns near-black with a crimson
    cast, bright emblems (crosses, badges) turn crimson, and skin, leather, wood and metal keep their colors, so the
    unit still reads as the same type. {@code white_cloth} also blackens white cloth (the knight's surcoat)."""
    import colorsys
    out = Img(img.w, img.h)
    for y in range(img.h):
        for x in range(img.w):
            p = img.get(x, y)
            if p is None or p[3] == 0:
                out.set(x, y, p)
                continue
            r, g, b, a = p
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            brown = 0.02 <= h <= 0.14 and s < 0.75  # skin, leather, wood
            # Gold crosses (more saturated than blond hair) and blue ones; red cloth is handled as cloth.
            emblem = v > 0.7 and ((0.1 < h < 0.17 and s > 0.75) or (0.5 < h < 0.75 and s > 0.55))
            cloth = (s > 0.3 and not brown) or (white_cloth and s < 0.1 and v > 0.88)
            if emblem:
                out.set(x, y, (178, 24, 30, a))
            elif cloth:
                dark = 0.09 + 0.2 * ((0.3 * r + 0.59 * g + 0.11 * b) / 255)
                out.set(x, y, (int(255 * dark * 1.45), int(255 * dark * 0.55), int(255 * dark * 0.55), a))
            else:
                out.set(x, y, p)
    raider_marks(out)
    return out


def raider_marks(img):
    """What tells a raider apart at a glance: a crimson headband tied round the head (base and hat layer) and a white
    skull on the chest (on the tabard overlay too, where there is one)."""
    band, knot = (190, 28, 34, 255), (120, 14, 18, 255)
    for x in range(0, 32):  # head: base layer, all four sides, rows 9-10
        for y in (9, 10):
            if img.get(x, y) is not None and img.get(x, y)[3] > 0:
                img.set(x, y, band if (x + y) % 5 else knot)
    for x in range(32, 64):  # hat layer: only where it is drawn (a hood or helmet brim)
        for y in (9, 10):
            p = img.get(x, y)
            if p is not None and p[3] > 0:
                img.set(x, y, band if (x + y) % 5 else knot)
    for x, y in ((25, 11), (26, 12), (25, 12)):  # the knot's tails hang at the back
        img.set(x, y, knot)
    bone, dark = (236, 232, 220, 255), (30, 22, 22, 255)
    skull = ['.XXX.', 'XoXoX', 'XXXXX', '.X.X.']
    for dy, row in enumerate(skull):
        for dx, ch in enumerate(row):
            if ch == '.':
                continue
            c = bone if ch == 'X' else dark
            for oy in (0, 16):  # body base (y 20..32) and jacket overlay (y 36..48)
                x, y = 21 + dx, 22 + dy + oy
                p = img.get(x, y)
                if oy == 0 or (p is not None and p[3] > 0):
                    img.set(x, y, c)


# Each camp kind's raiders wear the same dark kit in the kind's colors: (headband and emblem, cloth tint).
KIND_COLORS = {
    'poachers_lodge': ((70, 150, 60), (0.55, 0.95, 0.55)),
    'smugglers_dock': ((40, 150, 170), (0.5, 0.75, 1.1)),
    'fort': ((150, 160, 175), (0.85, 0.95, 1.1)),
    'quarry_stronghold': ((205, 120, 40), (1.25, 0.9, 0.55)),
    'witch_coven': ((150, 70, 190), (1.0, 0.55, 1.2)),
    'frozen_outpost': ((150, 205, 235), (0.95, 1.2, 1.5)),
    'keep': ((225, 180, 50), (1.3, 1.05, 0.45)),
    'cavalry_encampment': ((60, 90, 200), (0.55, 0.7, 1.4)),
    'sunken_crypt': ((140, 190, 130), (0.8, 1.15, 0.85)),
    'illager_hall': ((50, 170, 110), (0.6, 1.2, 0.85)),
}


def kind_raider(base, kind):
    """{@code base} (a raider skin) recolored for one camp kind: the crimson headband and emblems take the kind's color
    (keeping their shading) and the dark crimson-cast cloth takes its tint. Bandits keep the plain crimson kit."""
    band, tint = KIND_COLORS[kind]
    out = Img(base.w, base.h)
    for y in range(base.h):
        for x in range(base.w):
            p = base.get(x, y)
            if p is None or p[3] == 0:
                out.set(x, y, p)
                continue
            r, g, b, a = p
            if r > 1.7 * g and r > 1.7 * b:
                if r >= 100:
                    k = r / 190
                    out.set(x, y, (min(255, int(band[0] * k)), min(255, int(band[1] * k)), min(255, int(band[2] * k)), a))
                else:
                    dark = r / (255 * 1.45)
                    out.set(x, y, (min(255, int(255 * dark * tint[0])), min(255, int(255 * dark * tint[1])), min(255, int(255 * dark * tint[2])), a))
            else:
                out.set(x, y, p)
    return out


def save_all():
    for name, make in SKINS.items():
        img = make()
        img.save(f'entity/{name}.png')
        dark = raider(img, white_cloth=name == 'knight')
        dark.save(f'entity/raider_{name}.png')
        for kind in KIND_COLORS:
            kind_raider(dark, kind).save(f'entity/raider_{kind}_{name}.png')
