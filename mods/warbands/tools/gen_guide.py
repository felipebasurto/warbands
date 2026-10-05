"""Generates the Warbands guide illustrations (no dependencies).

Run from this folder:  python3 gen_guide.py
Writes into ../src/main/resources/assets/warbands/textures/gui/guide/. Art is drawn at half size and
scaled 2x so it reads as chunky pixel art next to the item icons. Captions are drawn by the game
(GuideScreen) under each panel, so the pictures have no text and work in every language.
"""
from gen_textures import Img, T, hexc, jitter, shade

SCALE = 2

SKIN = hexc('d8a47f')
HAIR = hexc('5a3a22')
STEEL = hexc('9aa3ad')
RED = hexc('a3262a')
GREEN_HOOD = hexc('3f7a2e')
PLAYER = hexc('3b6fb6')
LEGS = hexc('3a3a4a')
BROWN = hexc('7a5230')
GRASS = hexc('5d9c3a')
DIRT = hexc('7a5a3a')
PLANK = hexc('a8804a')
ROOF = hexc('8a3b2a')
STONE = hexc('8e8e8e')
DARK = hexc('2b2b2b')
GLOW = hexc('f0a030')
OK = hexc('3fae3f')
BAD = hexc('d03a2a')
WHITE = hexc('f4f4f4')
YELLOW = hexc('f2d34a')
GLASS = hexc('9fd3e8')
ZOMBIE = hexc('5e9b52')


class Art(Img):
    def hline(self, x0, x1, y, c):
        for x in range(x0, x1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1):
            self.set(x, y, c)

    def box(self, x0, y0, x1, y1, c):
        """1px outline of the box from (x0, y0) to (x1, y1), exclusive."""
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1 - 1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1 - 1, y0, y1, c)

    def erase(self, x0, y0, x1, y1):
        self.rect(x0, y0, x1, y1, T)

    def save_scaled(self, rel):
        out = Img(self.w * SCALE, self.h * SCALE)
        for y in range(self.h):
            for x in range(self.w):
                out.rect(x * SCALE, y * SCALE, x * SCALE + SCALE, y * SCALE + SCALE, self.get(x, y))
        out.save(rel)


def ground(img, x0, x1, y, depth=3):
    for x in range(x0, x1):
        img.set(x, y, jitter(GRASS, x, y, 1))
        for d in range(1, depth):
            img.set(x, y + d, jitter(DIRT, x, y + d, 2))


def figure(img, x, feet, body, hat=None, weapon=None, skin=SKIN, facing=1):
    """A 3-wide, 8-tall person standing on row {@code feet} - 1, left edge at x."""
    top = feet - 8
    img.hline(x, x + 3, top, hat or HAIR)
    img.hline(x, x + 3, top + 1, skin)
    img.hline(x, x + 3, top + 2, skin)
    img.set(x + (2 if facing > 0 else 0), top + 1, DARK)  # eye
    img.rect(x - 1, top + 3, x + 4, top + 5, body)
    img.hline(x, x + 3, top + 5, shade(body, 0.8))
    img.vline(x, top + 6, feet, LEGS)
    img.vline(x + 2, top + 6, feet, LEGS)
    wx = x + 4 if facing > 0 else x - 2
    if weapon == 'sword':
        img.vline(wx, top, top + 4, STEEL)
        img.set(wx, top + 4, BROWN)
    elif weapon == 'bow':
        img.vline(wx + 1, top + 1, top + 6, BROWN)
        img.set(wx, top, BROWN)
        img.set(wx, top + 6, BROWN)


def check(img, x, y):
    for dx, dy in ((0, 3), (1, 4), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1)):
        img.set(x + dx, y + dy, OK)
        img.set(x + dx, y + dy + 1, shade(OK, 0.75))


def cross(img, x, y):
    for i in range(6):
        img.set(x + i, y + i, BAD)
        img.set(x + 5 - i, y + i, BAD)
        img.set(x + i + 1, y + i, shade(BAD, 0.75))


def house(img, x, feet, state):
    """A 22-wide house on row feet - 1. state: 'full', 'broken' (less than half left) or 'stripped' (hearth only)."""
    wall_top = feet - 11
    img.rect(x + 2, wall_top, x + 20, feet, lambda px, py: jitter(PLANK, px, py, 7, 0.1))
    for py in range(wall_top, feet, 3):
        img.hline(x + 2, x + 20, py, shade(PLANK, 0.8))
    for i in range(11):  # roof
        img.hline(x + i, x + 22 - i, wall_top - 1 - i, jitter(ROOF, x + i, i, 8))
    img.rect(x + 9, feet - 6, x + 13, feet, DARK)         # door
    img.rect(x + 4, feet - 8, x + 7, feet - 5, GLASS)     # window
    img.rect(x + 15, feet - 8, x + 18, feet - 5, GLASS)
    img.rect(x + 17, wall_top - 9, x + 19, wall_top - 3, hexc('9a4a3a'))  # chimney
    hearth = (x + 14, feet - 3, x + 17, feet)
    if state == 'full':
        img.rect(*hearth, GLOW)
        return
    if state == 'broken':
        img.erase(x, wall_top - 12, x + 22, wall_top)     # roof gone
        img.erase(x + 1, wall_top, x + 9, feet - 3)       # left wall knocked down
        img.erase(x + 13, wall_top, x + 21, feet - 5)
        img.erase(x + 9, wall_top, x + 13, feet - 3)
        for (px, py) in ((x + 3, feet - 4), (x + 6, feet - 5), (x + 18, feet - 6)):
            img.erase(px, py, px + 1, feet - 3)
        img.rect(*hearth, GLOW)
        return
    img.erase(x, wall_top - 12, x + 22, feet)            # stripped to the hearth
    img.rect(x + 2, feet - 1, x + 4, feet, shade(PLANK, 0.8))
    img.rect(x + 19, feet - 2, x + 20, feet, shade(PLANK, 0.8))
    img.rect(*hearth, GLOW)


def houses():
    img = Art(102, 30)
    for i, state in enumerate(('full', 'broken', 'stripped')):
        x0 = i * 34
        ground(img, x0 + 1, x0 + 33, 27)
        house(img, x0 + 5, 27, state)
        (check if state == 'full' else cross)(img, x0 + 26, 1)
    img.save_scaled('gui/guide/houses.png')


def site(img, x0, blocked):
    """Top-down view of placing a blueprint: footprint, entrance, clicked block and the player."""
    img.rect(x0, 0, x0 + 48, 32, lambda px, py: jitter(GRASS, px, py, 3, 0.06))
    for gx in range(x0, x0 + 48, 4):
        img.vline(gx, 0, 32, shade(GRASS, 0.9))
    for gy in range(0, 32, 4):
        img.hline(x0, x0 + 48, gy, shade(GRASS, 0.9))
    color = BAD if blocked else OK
    fx, fy = x0 + 14, 1
    img.rect(fx, fy, fx + 20, fy + 20, lambda px, py: shade(color, 1.3) if (px + py) % 2 else jitter(GRASS, px, py, 3, 0.06))
    img.box(fx, fy, fx + 20, fy + 20, color)
    img.rect(fx + 8, fy + 16, fx + 12, fy + 20, WHITE)   # entrance
    img.box(fx + 8, fy + 21, fx + 12, fy + 25, YELLOW)   # clicked block
    if blocked:
        img.rect(fx + 3, fy + 3, fx + 8, fy + 8, jitter(STONE, 1, 1, 4))
        img.box(fx + 3, fy + 3, fx + 8, fy + 8, DARK)
    px, py = fx + 8, 27                                   # player, seen from above, facing the site
    img.rect(px, py, px + 4, py + 4, HAIR)
    img.rect(px + 1, py, px + 3, py + 1, SKIN)
    img.set(px + 1, py - 1, DARK)
    img.set(px + 2, py - 1, DARK)


def placement():
    img = Art(100, 32)
    site(img, 0, False)
    site(img, 52, True)
    img.save_scaled('gui/guide/placement.png')


def battlements(img, x0, x1, y, c):
    for x in range(x0, x1):
        if (x - x0) % 2 == 0:
            img.set(x, y, c)


def flag(img, x, top, c):
    img.vline(x, top, top + 7, DARK)
    img.rect(x + 1, top, x + 4, top + 3, c)


def ages():
    img = Art(102, 30)
    for i in range(3):
        ground(img, i * 34 + 1, i * 34 + 33, 27)
    # Village: small wooden hall
    img.rect(8, 17, 26, 27, lambda x, y: jitter(PLANK, x, y, 9, 0.1))
    for i in range(6):
        img.hline(6 + i, 28 - i, 16 - i, jitter(ROOF, i, 16 - i, 8))
    img.rect(15, 21, 19, 27, DARK)
    flag(img, 25, 5, RED)
    # Feudal: stone hall with a gatehouse
    stone = lambda x, y: jitter(STONE, x, y, 11, 0.12)
    img.rect(38, 13, 64, 27, stone)
    battlements(img, 38, 64, 12, STONE)
    img.rect(46, 8, 56, 13, stone)
    battlements(img, 46, 56, 7, STONE)
    img.rect(49, 20, 53, 27, DARK)
    img.rect(41, 17, 43, 20, DARK)
    img.rect(59, 17, 61, 20, DARK)
    flag(img, 50, 0, RED)
    # Castle: keep with two towers
    img.rect(72, 12, 96, 27, stone)
    battlements(img, 72, 96, 11, STONE)
    img.rect(69, 4, 75, 27, stone)
    battlements(img, 69, 75, 3, STONE)
    img.rect(93, 4, 99, 27, stone)
    battlements(img, 93, 99, 3, STONE)
    img.rect(81, 19, 87, 27, DARK)
    img.hline(81, 87, 18, DARK)
    for tx in (71, 95):
        img.rect(tx, 9, tx + 2, 12, DARK)
    flag(img, 83, 3, hexc('e0b030'))
    img.save_scaled('gui/guide/ages.png')


def arrow_right(img, x, y, length, c):
    img.hline(x, x + length, y, c)
    img.set(x + length - 2, y - 1, c)
    img.set(x + length - 2, y + 1, c)


def stances():
    img = Art(102, 26)
    for i in range(3):
        ground(img, i * 34 + 1, i * 34 + 33, 23)
    # Follow: two soldiers walking after the player
    figure(img, 4, 23, RED, STEEL, 'sword')
    figure(img, 12, 23, GREEN_HOOD, GREEN_HOOD, 'bow')
    figure(img, 25, 23, PLAYER)
    arrow_right(img, 3, 11, 20, shade(PLAYER, 0.8))
    # Hold: a soldier guarding a flag inside a circle
    cx = 34 + 16
    for dx in range(-13, 14):
        dy = int(round(3.0 * (1 - (dx / 13.5) ** 2) ** 0.5))
        if dx % 2 == 0:
            img.set(cx + dx, 23 - dy, YELLOW)
            img.set(cx + dx, 23 + dy, YELLOW)
    figure(img, cx - 4, 23, RED, STEEL, 'sword')
    flag(img, cx + 5, 12, RED)
    # Charge: a soldier running at a zombie
    figure(img, 70, 23, RED, STEEL, 'sword')
    arrow_right(img, 76, 17, 9, BAD)
    figure(img, 91, 23, hexc('3b8fa8'), ZOMBIE, skin=ZOMBIE, facing=-1)
    img.save_scaled('gui/guide/stances.png')


def welcome():
    img = Art(104, 32)
    ground(img, 0, 104, 29)
    stone = lambda x, y: jitter(STONE, x, y, 11, 0.12)
    img.rect(36, 13, 68, 29, stone)
    battlements(img, 36, 68, 12, STONE)
    img.rect(46, 6, 58, 13, stone)
    battlements(img, 46, 58, 5, STONE)
    img.rect(49, 21, 55, 29, DARK)
    img.hline(49, 55, 20, DARK)
    img.rect(40, 16, 42, 19, DARK)
    img.rect(62, 16, 64, 19, DARK)
    flag(img, 51, 0, RED)
    for x in (8, 16, 24):
        figure(img, x, 29, RED, STEEL, 'sword')
    for x in (76, 84, 92):
        figure(img, x, 29, GREEN_HOOD, GREEN_HOOD, 'bow', facing=-1)
    img.save_scaled('gui/guide/welcome.png')


def tower():
    """Watch Tower: archers on the platform shoot a zombie that walks into range."""
    img = Art(102, 32)
    ground(img, 0, 102, 29)
    stone = lambda x, y: jitter(STONE, x, y, 13, 0.12)
    img.rect(30, 10, 42, 29, stone)                       # shaft
    img.rect(28, 8, 44, 10, stone)                        # platform
    battlements(img, 28, 44, 7, STONE)
    img.rect(34, 22, 38, 29, DARK)                        # door
    img.rect(35, 14, 37, 17, DARK)                        # arrow slit
    figure(img, 31, 7, GREEN_HOOD, GREEN_HOOD, 'bow')
    figure(img, 38, 7, hexc('6a4a2a'), STEEL)
    for i in range(14):                                   # arrow arc toward the zombie
        img.set(46 + i * 2, 4 + (i * i) // 12, shade(BROWN, 0.9))
    img.hline(72, 76, 21, BROWN)
    img.set(76, 21, STEEL)
    figure(img, 80, 29, hexc('3b8fa8'), ZOMBIE, skin=ZOMBIE, facing=-1)
    for dx in range(-24, 25, 3):                          # range marker on the ground
        img.set(36 + dx * 2 if 36 + dx * 2 < 102 else 101, 30, YELLOW)
    img.save_scaled('gui/guide/tower.png')


if __name__ == '__main__':
    welcome()
    placement()
    houses()
    ages()
    stances()
    tower()
