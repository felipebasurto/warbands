"""Village Age camp layouts (the second design of each kind). Run through gen_camps.py."""
import gen_structures as g
from gen_camps import (Camp, fill, ring, circle, disc, wall, tower, log, fence, lantern, campfire, fence_gate, ladder,
                       slab, walkway, tent, gallery, sculk, alarm, pitfall, rigged_floor, NONE4)


def bandit_stockade():
    """19x19 round log fort. A ragged ring of stakes with a lintel gate; a roofed lookout tower over the south-west
    arc watches the gate (ladder inside, archer on top); a timber longhouse at the back (bunks, table, hearth with a
    chimney) with the standard in the yard in front of it; a pen of stolen livestock feed, a loot heap, a bonfire, a
    firing step by the gate, and two berry pits flanking the path in. Core (9, 1, 10). Height 13."""
    n = 19
    b = Camp('BANDIT_CAMP', 'bandit_stockade', n, 13)
    b.ground('minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:gravel')
    wall_cells = circle(9, 9, 8)
    for x in range(n):
        for z in range(n):
            if ((x - 9) ** 2 + (z - 9) ** 2) ** 0.5 >= 8.5 and not (8 <= x <= 10 and z == 18):
                b.set(x, 0, z, g.varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'))
                if (x * 5 + z * 3) % 4 == 0:
                    b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')
    for z in range(9, 19):
        b.set(9, 0, z, 'minecraft:dirt_path')
        if z >= 15:
            b.set(8, 0, z, 'minecraft:dirt_path')
            b.set(10, 0, z, 'minecraft:dirt_path')

    # palisade: an even ring of spruce logs on a cobble footing, dark oak posts every fourth cell rising above it and
    # a stripped log now and then; lower where the firing step and the longhouse stand
    gate = [(x, 17) for x in range(7, 12)]
    for x, z in wall_cells:
        if (x, z) in gate:
            continue
        low = z <= 3 and 5 <= x <= 13 or x >= 12 and z >= 13
        post = (x * 3 + z * 2) % 4 == 0
        h = 3 if low else 6 if post else 4
        b.set(x, 0, z, g.varied(x, 0, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:gravel'))
        b.set(x, 1, z, g.varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone_bricks'))
        for y in range(2, h + 1):
            wood = ('minecraft:dark_oak_log' if post else
                    'minecraft:stripped_spruce_log' if (x + z) % 5 == 0 and y == 3 else 'minecraft:spruce_log')
            b.set(x, y, z, wood, axis='y')
        if not low:
            b.set(x, h + 1, z, 'minecraft:spruce_fence', **g.BANDIT_NO) if not post else lantern(b, x, h + 1, z)
    for x, z in ((0, 9), (18, 9), (9, 0), (2, 4), (16, 4), (16, 14)):  # outside braces break the arc
        log(b, x, 1, z)
        log(b, x, 2, z, 'minecraft:stripped_spruce_log')
        b.set(x, 0, z, 'minecraft:mossy_cobblestone')
    for x in (7, 11):
        for y in range(1, 7):
            log(b, x, y, 17)
        lantern(b, x, 7, 17)
    for x in (8, 9, 10):
        fence_gate(b, x, 1, 17)
        log(b, x, 4, 17, 'minecraft:stripped_spruce_log', 'x')
    b.set(9, 5, 17, 'minecraft:skeleton_skull', rotation=0)
    alarm(b, 9, 16)
    pitfall(b, 7, 14, 'sweet_berry_bush')
    pitfall(b, 11, 14, 'sweet_berry_bush')

    # lookout tower, south-west: a plank base with a door on the yard side, a ladder up, a railed platform that
    # hangs over the wall, a hipped roof on corner posts
    for x in range(4, 7):
        for z in range(11, 14):
            corner = x in (4, 6) and z in (11, 13)
            for y in range(1, 6):
                if corner:
                    log(b, x, y, z)
                elif (x, z) != (5, 12):
                    b.set(x, y, z, 'minecraft:mossy_cobblestone' if y == 1 else 'minecraft:spruce_planks')
    for y in (1, 2):
        b.set(6, y, 12, 'minecraft:air')
    b.set(6, 3, 12, 'minecraft:stripped_spruce_log', axis='z')
    ladder(b, 5, 12, 1, 6, 'east')
    for x in range(3, 8):
        for z in range(10, 15):
            if (x, z) != (5, 12):
                b.set(x, 6, z, 'minecraft:spruce_planks')
    for x, z in g.rect_ring(3, 10, 7, 14):
        if x in (3, 7) and z in (10, 14):
            for y in (7, 8, 9):
                fence(b, x, y, z)
        else:
            fence(b, x, 7, z)
    g.bandit_hip_roof(b, 2, 9, 8, 15, 9, 'minecraft:dark_oak_stairs', 'minecraft:dark_oak_planks')
    lantern(b, 3, 9, 12, hanging=True)
    for z in (11, 13):
        b.set(4, 3, z, 'minecraft:spruce_planks')
    b.set(5, 3, 13, 'minecraft:glass_pane', **g.pane(False))
    b.set(5, 4, 11, 'minecraft:spruce_trapdoor', facing='south', half='top', open=True, powered=False, waterlogged=False)
    b.set(4, 1, 14, 'minecraft:barrel', facing='up', open=False)

    # longhouse, north: cobble footing, plank walls in a log frame, a beam band, gabled roof along x with overhang
    x0, x1, z0, z1 = 6, 12, 3, 7
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                b.set(x, 0, z, 'minecraft:spruce_planks' if (x + z) % 2 else 'minecraft:dark_oak_planks')
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            b.set(x, 1, z, g.varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone'))
            for y in (2, 3):
                if corner or (x == 9 and z == z0) or (z == 5 and x in (x0, x1)):
                    log(b, x, y, z)
                else:
                    b.set(x, y, z, 'minecraft:spruce_planks')
            log(b, x, 4, z, 'minecraft:spruce_log' if corner else 'minecraft:stripped_spruce_log',
                'y' if corner else ('x' if z in (z0, z1) else 'z'))
    for x in (x0, x1):  # gable ends
        for z in (4, 5, 6):
            b.set(x, 5, z, 'minecraft:spruce_planks')
        b.set(x, 6, 5, 'minecraft:stripped_spruce_log', axis='x')
    for x in range(x0 - 1, x1 + 2):
        for z, y, face in ((2, 4, 'south'), (3, 5, 'south'), (4, 6, 'south'), (8, 4, 'north'), (7, 5, 'north'), (6, 6, 'north')):
            b.set(x, y, z, 'minecraft:spruce_stairs', **g.stairs(face))
        slab(b, x, 7, 5, 'minecraft:dark_oak_slab')
    for x in (x0 - 1, x1 + 1):  # barge ends: the ridge beam pokes out
        b.set(x, 7, 5, 'minecraft:dark_oak_planks')
    for x in (8, 10):  # tie beams for the lanterns
        for z in (4, 5, 6):
            log(b, x, 4, z, 'minecraft:spruce_log', 'z')
    b.set(9, 1, z1, 'minecraft:spruce_door', facing='south', half='lower', hinge='left', open=False, powered=False)
    b.set(9, 2, z1, 'minecraft:spruce_door', facing='south', half='upper', hinge='left', open=False, powered=False)
    for x in (7, 11):
        b.set(x, 2, z1, 'minecraft:glass_pane', **g.pane(False))
        b.set(x, 3, z1, 'minecraft:spruce_trapdoor', facing='south', half='top', open=True, powered=False, waterlogged=False)
    for z in (4, 6):
        b.set(x0, 2, z, 'minecraft:glass_pane', **g.pane(True))
    # inside: two bunks, a table with the loot being split, a smoker at the hearth, a chest
    for x in (7, 8):
        b.set(x, 1, 4, 'minecraft:brown_bed', facing='north', part='head', occupied=False)
        b.set(x, 1, 5, 'minecraft:brown_bed', facing='north', part='foot', occupied=False)
    fence(b, 10, 1, 5)
    b.set(10, 2, 5, 'minecraft:spruce_pressure_plate', powered=False)
    b.set(10, 1, 4, 'minecraft:spruce_stairs', **g.stairs('south'))
    b.set(11, 1, 5, 'minecraft:smoker', facing='west', lit=True)
    b.set(11, 1, 4, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(11, 1, 6, 'minecraft:barrel', facing='up', open=False)
    b.set(7, 1, 6, 'minecraft:crafting_table')
    lantern(b, 8, 3, 5, hanging=True)
    lantern(b, 10, 3, 6, hanging=True)
    # chimney through the east gable, fire on top
    for y in range(1, 9):
        b.set(13, y, 5, 'minecraft:mossy_cobblestone' if y % 3 == 0 else 'minecraft:cobblestone')
        if y < 4:
            b.set(13, y, 4, 'minecraft:cobblestone')
            b.set(13, y, 6, 'minecraft:stone_bricks')
    for z in (4, 5, 6):
        slab(b, 13, 9, z, 'minecraft:cobblestone_slab') if z != 5 else None
    campfire(b, 13, 5, y=9)
    # porch step
    for x in (8, 9, 10):
        slab(b, x, 0, 8, 'minecraft:spruce_slab', 'top')

    # the yard: the standard in front of the longhouse between lantern posts
    b.standard(9, 10)
    for x in (7, 11):
        fence(b, x, 1, 10)
        lantern(b, x, 2, 10)
    # stolen feed pen, west
    for x, z in g.rect_ring(2, 5, 5, 9):
        if (x - 9) ** 2 + (z - 9) ** 2 < 7.5 ** 2 and (x, z) != (5, 7):
            fence(b, x, 1, z)
    fence_gate(b, 5, 1, 7, facing='east')
    b.set(3, 1, 7, 'minecraft:hay_block', axis='y')
    b.set(4, 1, 6, 'minecraft:hay_block', axis='x')
    b.set(3, 2, 7, 'minecraft:hay_block', axis='z')
    b.set(4, 1, 8, 'minecraft:cauldron')
    b.set(3, 1, 8, 'minecraft:composter', level=3)
    # loot heap, east
    for x, z, y in ((14, 8, 1), (14, 8, 2), (15, 8, 1), (14, 9, 1)):
        b.set(x, y, z, 'minecraft:barrel', facing='up', open=False)
    b.set(15, 1, 9, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(13, 1, 8, 'minecraft:yellow_carpet')
    # bonfire in a ring of stones, log benches two blocks out
    campfire(b, 14, 11)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        slab(b, 14 + dx, 1, 11 + dz, 'minecraft:cobblestone_slab')
    log(b, 16, 1, 11, 'minecraft:spruce_log', 'z')
    log(b, 12, 1, 11, 'minecraft:spruce_log', 'z')
    # firing step by the gate, east: 2-high planks, a stair up
    fill(b, 12, 1, 14, 13, 2, 15, 'minecraft:spruce_planks')
    b.set(12, 1, 13, 'minecraft:spruce_stairs', **g.stairs('south'))

    b.post('archer', 4, 7, 11)
    b.unit('archer', 13, 15, y=3)
    for x, z in ((8, 12), (10, 12), (12, 10), (6, 9)):
        b.unit('militia', x, z)
    b.unit('archer', 9, 8)
    b.spare((7, 12), (11, 12), (9, 13), (13, 9))
    b.report('A round log fort. A roofed lookout over the gate holds an archer; berry pits flank the path in. The '
             'standard stands before the longhouse at the back.',
             'Un fuerte redondo de troncos. Una atalaya con tejado vigila la puerta con un arquero; fosos de bayas a '
             'ambos lados del camino. El estandarte está ante la casa larga del fondo.')
    b.done()


def _leaf(b, x, y, z):
    if 0 <= x < 21 and 0 <= z < 21 and b.block(x, y, z) in (None, 'minecraft:air'):
        b.set(x, y, z, 'minecraft:oak_leaves', distance=1, persistent=True, waterlogged=False)


def _treehouse_crown(b, tx, tz, y0, top, r):
    """A thin crown: trunk to {@code top}, leaf tufts every second layer, holes left so light and the deck show."""
    for y in range(y0, top + 1):
        for x in range(tx - r, tx + r + 1):
            for z in range(tz - r, tz + r + 1):
                d = abs(x - tx) + abs(z - tz)
                if (x, z) == (tx, tz) or d > r + 1 - 2 * (y - y0) or (y == y0 and d == r + 1):
                    continue
                _leaf(b, x, y, z)
    _leaf(b, tx, top + 1, tz)


def poachers_treehouse():
    """21x21 hunters' camp in four trees: two open platforms and two tree cabins (the trunk grows through the room,
    hipped roof, door onto the rope bridges), archers on all four, rope bridges whose middle planks drop when the
    alarm sounds, ladders up each trunk, a fenced yard below with a path, stumps and a woodpile."""
    b = Camp('POACHERS_LODGE', 'poachers_treehouse', 21, 13)
    b.ground('minecraft:podzol', 'minecraft:coarse_dirt', 'minecraft:moss_block')
    for x in range(21):     # ragged grass edge and a beaten path from the gate
        for z in range(21):
            if x in (0, 20) or z in (0, 20):
                b.set(x, 0, z, g.varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'))
                if (x * 5 + z * 3) % 4 == 0:
                    b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')
    for z in range(11, 21):
        b.set(10, 0, z, 'minecraft:dirt_path')
        if z >= 17:
            b.set(9, 0, z, 'minecraft:dirt_path')
            b.set(11, 0, z, 'minecraft:dirt_path')
    for x, z in ring(1, 19):
        if (x + z) % 4 == 0:
            for y in (1, 2):
                log(b, x, y, z, 'minecraft:stripped_oak_log')
        else:
            fence(b, x, 1, z, 'minecraft:oak_fence' if (x * 3 + z) % 5 else 'minecraft:spruce_fence')
    for x in (9, 10, 11):
        fence_gate(b, x, 1, 19, 'oak')
    posts = []
    for tx, tz in ((4, 4), (16, 4), (4, 16), (16, 16)):
        cabin = (tx, tz) in ((16, 4), (4, 16))
        r = 3 if cabin else 2
        for y in range(1, 11 if not cabin else 12):
            log(b, tx, y, tz, 'minecraft:oak_log')
        for x in range(tx - r, tx + r + 1):
            for z in range(tz - r, tz + r + 1):
                if (x, z) != (tx, tz) and 0 <= x < 21 and 0 <= z < 21:
                    b.set(x, 5, z, g.varied(x, 5, z, 'minecraft:spruce_planks', 'minecraft:oak_planks'))
        for x, z in ((tx - r, tz - r), (tx + r, tz - r), (tx - r, tz + r), (tx + r, tz + r)):   # legs
            if 0 <= x < 21 and 0 <= z < 21:
                for y in range(1, 5):
                    log(b, x, y, z, 'minecraft:dark_oak_log')
        for x in range(tx - r, tx + r + 1):     # beam frame under the deck edge
            for z in (tz - r, tz + r):
                if 0 <= x < 21 and 0 <= z < 21:
                    log(b, x, 4, z, 'minecraft:dark_oak_log', 'x')
        for z in range(tz - r, tz + r + 1):
            for x in (tx - r, tx + r):
                if 0 <= x < 21 and 0 <= z < 21:
                    log(b, x, 4, z, 'minecraft:dark_oak_log', 'z')
        # ladder up the trunk's inner face (toward the yard), through the platform
        lz = tz + 1 if tz < 10 else tz - 1
        ladder(b, tx, lz, 1, 5, 'south' if tz < 10 else 'north')
        if not cabin:
            posts.append((tx + (1 if tx < 10 else -1), 6, tz + (-1 if tz < 10 else 1)))
            # the bridge openings stay free in the rail
            gap = {(tx + r, tz), (tx, tz + r if tz < 10 else tz - r)}
            for x, z in g.rect_ring(tx - r, tz - r, tx + r, tz + r):
                if (x, z) not in gap and 0 <= x < 21 and 0 <= z < 21:
                    fence(b, x, 6, z, 'minecraft:spruce_fence')
            for x, z in ((tx - r, tz - r), (tx + r, tz - r), (tx - r, tz + r), (tx + r, tz + r)):
                if 0 <= x < 21 and 0 <= z < 21:
                    fence(b, x, 7, z, 'minecraft:spruce_fence')
            _treehouse_crown(b, tx, tz, 9, 10, 3)
        else:
            x0, x1, z0, z1 = tx - 2, tx + 2, tz - 2, tz + 2
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        corner = x in (x0, x1) and z in (z0, z1)
                        for y in (6, 7):
                            b.set(x, y, z, 'minecraft:oak_log' if corner else 'minecraft:spruce_planks', **({'axis': 'y'} if corner else {}))
                        log(b, x, 8, z, 'minecraft:stripped_spruce_log' if not corner else 'minecraft:oak_log',
                            'y' if corner else ('x' if z in (z0, z1) else 'z'))
            # doors face the bridges; windows on the two outer sides
            if tx == 16:      # north-east: door west (x0, tz) and south (tx, z1)
                doors = ((x0, tz, 'west'), (tx, z1, 'south'))
                outer = [(x, z0) for x in (tx - 1, tx + 1)] + [(x1, z) for z in (tz - 1, tz + 1)]
            else:             # south-west: door east and north
                doors = ((x1, tz, 'east'), (tx, z0, 'north'))
                outer = [(x, z1) for x in (tx - 1, tx + 1)] + [(x0, z) for z in (tz - 1, tz + 1)]
            for dx, dz, face in doors:
                for y, h in ((6, 'lower'), (7, 'upper')):
                    b.set(dx, y, dz, 'minecraft:spruce_door', facing=face, half=h, hinge='left', open=False, powered=False)
            for wx, wz in outer:
                b.set(wx, 7, wz, 'minecraft:glass_pane', **g.pane(wz in (tz - 2, tz + 2) is False and wx in (x0, x1)))
            # deck rail round the cabin, open where the bridges come in
            gap = {(x0 - 1, tz), (x1 + 1, tz), (tx, z0 - 1), (tx, z1 + 1)}
            for x, z in g.rect_ring(tx - r, tz - r, tx + r, tz + r):
                if (x, z) not in gap and 0 <= x < 21 and 0 <= z < 21:
                    fence(b, x, 6, z, 'minecraft:spruce_fence')
            g.bandit_hip_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 8, 'minecraft:dark_oak_stairs', 'minecraft:dark_oak_planks')
            for y in range(6, 12):    # the trunk grows through the room and the roof
                log(b, tx, y, tz, 'minecraft:oak_log')
            b.set(tx, 12, tz, 'minecraft:oak_log')
            _treehouse_crown(b, tx, tz, 11, 11, 1)
            b.post('archer', tx + (-1 if tx == 16 else 1), 6, tz + (-1 if tz == 4 else 1))
    # rope bridges between the platforms (y5), fence rails, a rigged plank in the middle of the long ones
    for z in (4, 16):
        walkway(b, [(x, z) for x in range(6, 15)], 5)
        for x in range(6, 15):
            rz = z + (-1 if z == 4 else 1)
            fence(b, x, 6, rz, 'minecraft:spruce_fence')
            if x % 3 == 0:      # rope posts standing on the bridge edge hold the rail up
                fence(b, x, 5, rz, 'minecraft:spruce_fence')
    for x in (4, 16):
        walkway(b, [(x, z) for z in range(6, 15)], 5)
        rx = x + (-1 if x == 4 else 1)
        for z in range(6, 15):
            fence(b, rx, 6, z, 'minecraft:spruce_fence')
            if z % 3 == 0:
                fence(b, rx, 5, z, 'minecraft:spruce_fence')
    rigged_floor(b, 10, 5, 4, 'south')      # piston machinery hangs over the yard, relay at (10, 5, 8)
    rigged_floor(b, 10, 5, 16, 'north')     # relay at (10, 5, 12)
    for z in (8, 12):  # posts holding up the winches
        for y in range(1, 5):
            log(b, 10, y, z, 'minecraft:stripped_oak_log')
    b.standard(10, 10)
    campfire(b, 7, 13)
    for x, z in ((7, 12), (6, 13), (8, 13)):    # stump seats round the fire
        log(b, x, 1, z, 'minecraft:oak_log', 'y') if (x, z) != (7, 12) else None
    for x in (13, 14):
        b.set(x, 1, 13, 'minecraft:hay_block', axis='y')
    for x, z in ((6, 8), (7, 8)):
        fence(b, x, 1, z, 'minecraft:oak_fence')
        b.set(x, 2, z, 'minecraft:brown_wool')
    b.set(14, 1, 8, 'minecraft:target')
    # woodpile with a chopping block and axe stand
    for x, z, y in ((18, 10, 1), (18, 11, 1), (18, 12, 1), (18, 10, 2), (18, 11, 2)):
        log(b, x, y, z, 'minecraft:oak_log', 'z')
    log(b, 17, 1, 12, 'minecraft:stripped_oak_log', 'y')
    alarm(b, 10, 18)
    pitfall(b, 8, 16, 'sweet_berry_bush')
    pitfall(b, 12, 16, 'sweet_berry_bush')
    sculk(b, 7, 6)
    sculk(b, 13, 6)
    for p in posts:
        b.post('archer', *p)
    for x, z in ((8, 12), (12, 12), (10, 15)):
        b.unit('militia', x, z)
    b.spare((8, 14), (12, 14), (6, 11), (14, 11))
    b.report('Four treehouse platforms with archers, joined by rope bridges whose middle planks drop away when the '
             'alarm sounds. Ladders up each trunk; sculk sensors listen in the yard.',
             'Cuatro plataformas en los árboles con arqueros, unidas por puentes de cuerda cuyas tablas centrales '
             'caen cuando suena la alarma. Escaleras en cada tronco; sensores de sculk escuchan en el patio.')
    b.done()


def smugglers_warehouse():
    """23x23 smugglers' compound: two long gabled warehouses (stone footing, log frame, shuttered windows, eaves
    with lanterns, cargo inside and stacked outside) with a dark alley between them (sculk sensors), a hip-roofed
    counting house at the end with two guns covering the alley, and crow's nests on the back corners."""
    n = 23
    b = Camp('SMUGGLERS_DOCK', 'smugglers_warehouse', n, 10)
    b.ground('minecraft:sand', 'minecraft:gravel', 'minecraft:coarse_dirt')
    for x, z in ring(1, 21):
        if (x + z) % 4 == 0:
            for y in (1, 2, 3):
                log(b, x, y, z, 'minecraft:stripped_dark_oak_log', 'y')
            lantern(b, x, 4, z)
        else:
            b.set(x, 1, z, g.varied(x, 1, z, 'minecraft:stripped_dark_oak_log', 'minecraft:cobblestone'),
                  **({'axis': 'y'} if (x + z) % 4 else {}))
            fence(b, x, 2, z, 'minecraft:dark_oak_fence')
    for x in (10, 11, 12):  # open gateway
        b.set(x, 1, 21, 'minecraft:air')
        b.set(x, 2, 21, 'minecraft:air')
        b.set(x, 3, 21, 'minecraft:air')
        b.set(x, 4, 21, 'minecraft:air')
    left = tower(b, 1, 1, 6, 'minecraft:dark_oak_planks', 'minecraft:dark_oak_slab')
    right = tower(b, 19, 1, 6, 'minecraft:dark_oak_planks', 'minecraft:dark_oak_slab')
    for t in (1, 19):  # corner posts and a hanging lantern make the crow's nests read as nests
        for x, z in ((t, 1), (t + 2, 1), (t, 3), (t + 2, 3)):
            log(b, x, 7, z, 'minecraft:dark_oak_log')
    # crow's nest roofs: a plank cap on the four posts
    for t in (1, 19):
        for x in range(t, t + 3):
            for z in range(1, 4):
                if (x, z) != (t + 1, 2):
                    slab(b, x, 8, z, 'minecraft:dark_oak_slab')

    def warehouse(x0, x1, door_x, wall_block, roof_stair, roof_slab, footing):
        c = (x0 + x1) // 2
        west = door_x == x1          # the alley door faces east for the left house, west for the right one
        for x in range(x0, x1 + 1):
            for z in range(9, 19):
                edge = x in (x0, x1) or z in (9, 18)
                if not edge:
                    b.set(x, 0, z, 'minecraft:spruce_planks' if (x + z) % 3 else 'minecraft:dark_oak_planks')
                    slab(b, x, 5, z, 'minecraft:spruce_slab')
                    continue
                corner = x in (x0, x1) and z in (9, 18)
                post = corner or (z in (9, 18) and x in (x0 + 3,)) or (x in (x0, x1) and z in (12, 18))
                b.set(x, 1, z, g.varied(x, 1, z, footing, 'minecraft:mossy_cobblestone', 'minecraft:cobblestone'))
                for y in (2, 3):
                    if post:
                        log(b, x, y, z, 'minecraft:dark_oak_log')
                    else:
                        b.set(x, y, z, wall_block)
                log(b, x, 4, z, 'minecraft:dark_oak_log' if corner else 'minecraft:stripped_dark_oak_log',
                    'y' if corner else ('x' if z in (9, 18) else 'z'))
                b.set(x, 5, z, wall_block if not corner else 'minecraft:dark_oak_planks')
        # windows with open shutters on both long walls and the end walls
        for x in (x0, x1):
            for z in (10, 11, 13, 17):
                if z in (10, 17):
                    b.set(x, 3, z, 'minecraft:glass_pane', **g.pane(True))
                    b.set(x, 2, z, wall_block)
        for z in (9, 18):
            for x in (x0 + 1, x1 - 1):
                b.set(x, 3, z, 'minecraft:glass_pane', **g.pane(False))
                face = 'north' if z == 9 else 'south'
                b.set(x, 2, z, 'minecraft:spruce_trapdoor', facing=face, half='top', open=True, powered=False, waterlogged=False)
        # doors: alley door on the long side (real spruce door), big double door on the south face
        side = 'east' if not west else 'west'
        for h in ('lower', 'upper'):
            y = 1 if h == 'lower' else 2
            b.set(door_x, y, 15, 'minecraft:spruce_door', facing=side, half=h, hinge='left', open=False, powered=False)
        b.set(door_x, 3, 15, 'minecraft:dark_oak_planks')
        b.set(door_x, 4, 15, 'minecraft:stripped_dark_oak_log', axis='z')
        for x in (c, c + 1 if c + 1 < x1 else c - 1):
            for h in ('lower', 'upper'):
                y = 1 if h == 'lower' else 2
                b.set(x, y, 18, 'minecraft:dark_oak_door', facing='south', half=h,
                      hinge='left' if x == min(c, c + 1 if c + 1 < x1 else c - 1) else 'right', open=False, powered=False)
            b.set(x, 3, 18, 'minecraft:stripped_dark_oak_log', axis='x')
        # gable roof along z: ridge over the centre, eaves overhang by one, gables filled
        for d in range(0, 5):
            y = 9 - d
            for z in range(8, 20):
                if d == 0:
                    slab(b, c, 9, z, roof_slab)
                else:
                    b.set(c - d, y, z, roof_stair, **g.stairs('east'))
                    b.set(c + d, y, z, roof_stair, **g.stairs('west'))
        for z in (9, 18):
            for y in range(6, 9):
                span = 8 - y
                for x in range(c - span + 1, c + span):
                    b.set(x, y, z, wall_block)
        for xx in (x0 - 1, x1 + 1):   # eave lanterns
            for z in (10, 17):
                lantern(b, xx, 4, z, hanging=True)
        # cargo: barrel and crate rows that make the inside a small maze
        for z in (11, 14):
            for x in range(x0 + 1, x1):
                if (x + z) % 3:
                    b.set(x, 1, z, 'minecraft:barrel', facing='up', open=False)
                    if (x * z) % 4 == 0:
                        b.set(x, 2, z, 'minecraft:barrel', facing='up', open=False)
        for x in range(x0 + 1, x1):
            if (x + 17) % 4 == 0 and x != c and x != c + 1:
                b.set(x, 1, 17, 'minecraft:chest', facing='north', type='single', waterlogged=False)
        lantern(b, c, 4, 12, hanging=True)
        lantern(b, c, 4, 16, hanging=True)

    warehouse(3, 9, 9, 'minecraft:spruce_planks', 'minecraft:dark_oak_stairs', 'minecraft:dark_oak_slab',
              'minecraft:stone_bricks')
    warehouse(13, 19, 13, 'minecraft:dark_oak_planks', 'minecraft:spruce_stairs', 'minecraft:spruce_slab',
              'minecraft:cobblestone')
    # counting house at the end of the alley (north), door on its south face, guns either side of the door
    for x in range(7, 16):
        for z in range(2, 8):
            edge = x in (7, 15) or z in (2, 7)
            if edge:
                corner = x in (7, 15) and z in (2, 7)
                for y in range(1, 5):
                    if corner:
                        log(b, x, y, z, 'minecraft:dark_oak_log')
                    else:
                        b.set(x, y, z, g.varied(x, y, z, 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks',
                                                 'minecraft:cracked_stone_bricks') if y <= 2 else 'minecraft:spruce_planks')
                if not corner:
                    log(b, x, 4, z, 'minecraft:stripped_dark_oak_log', 'x' if z in (2, 7) else 'z')
            b.set(x, 5, z, 'minecraft:dark_oak_planks')
            b.set(x, 0, z, 'minecraft:spruce_planks')
    for z in (4, 5):
        for x in (7, 15):
            b.set(x, 3, z, 'minecraft:glass_pane', **g.pane(True))
    for x in (8, 10, 12, 14):
        if x not in (9, 13):
            b.set(x, 3, 2, 'minecraft:glass_pane', **g.pane(False))
    g.bandit_hip_roof(b, 6, 1, 16, 8, 5, 'minecraft:dark_oak_stairs', 'minecraft:dark_oak_planks')
    # cobble chimney in the roof's north-east corner
    for y in range(5, 10):
        b.set(15, y, 2, 'minecraft:cobblestone' if y % 2 else 'minecraft:mossy_cobblestone')
    b.set(16, 5, 2, 'minecraft:cobblestone')
    for y in (1, 2, 3):
        b.set(11, y, 7, 'minecraft:air')
    gallery(b, 9, 2, 7, 'south')
    gallery(b, 13, 2, 7, 'south')
    b.standard(11, 4)
    for x in (8, 14):
        b.set(x, 1, 3, 'minecraft:chest', facing='south', type='single', waterlogged=False)
    lantern(b, 11, 4, 5, hanging=True)
    # outside stacks: crates and barrels in groups, a hay bale, a cauldron of tar
    for x, z, ys in ((2, 15, 2), (2, 16, 1), (2, 12, 1), (20, 14, 2), (20, 13, 1), (8, 20, 2), (14, 20, 1), (2, 18, 1)):
        for y in range(1, ys + 1):
            b.set(x, y, z, 'minecraft:barrel', facing='up', open=False)
    sculk(b, 11, 10)
    sculk(b, 11, 16)
    alarm(b, 11, 20)
    campfire(b, 5, 20)
    campfire(b, 17, 20)
    for x, z in ((6, 10), (6, 16), (16, 10), (16, 16), (9, 4), (13, 4)):
        b.unit('militia', x, z)
    b.unit('archer', 11, 8)
    b.post('archer', *left)
    b.post('archer', *right)
    b.spare((11, 12), (11, 14), (4, 20), (18, 20))
    b.report('Two warehouses and a dark alley to a counting house. Sculk sensors in the alley: sneak. Two guns in the '
             'counting house wall cover the alley; the red relays under them silence them.',
             'Dos almacenes y un callejón oscuro hasta la casa de cuentas. Sensores de sculk en el callejón: ve '
             'agachado. Dos cañones en el muro de la casa cubren el callejón; los relés rojos de debajo los callan.')
    b.done()


def build():
    bandit_stockade()
    poachers_treehouse()
    smugglers_warehouse()
