"""Generates the Warbands building structure files.

Run from this folder:  python3 gen_structures.py
Output: ../src/main/resources/data/warbands/structure/{town_center,town_center_feudal,town_center_castle,house,barracks,archery_range,blacksmith,stable,watch_tower,siege_workshop}.nbt

The door is always on the south side (z = max). The game rotates the building so the
door faces the player. Sizes and core positions must match Blueprint.java.
You can also edit the .nbt files in-game with a Structure Block instead of using this script.
"""
import math
import os
import nbt

DATA_VERSION = 4790  # Minecraft 26.1.2
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'warbands', 'structure')


class Builder:
    def __init__(self, size_x, size_y, size_z):
        self.size = [size_x, size_y, size_z]
        self.blocks = {}
        self.nbt = {}

    def set(self, x, y, z, name, nbt=None, **props):
        self.blocks[(x, y, z)] = (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))
        if nbt is not None:
            self.nbt[(x, y, z)] = nbt

    def save(self, filename):
        palette, index, blocks = [], {}, []
        for pos, state in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if state not in index:
                index[state] = len(palette)
                entry = {'Name': state[0]}
                if state[1]:
                    entry['Properties'] = dict(state[1])
                palette.append(entry)
            entry = {'pos': list(pos), 'state': index[state]}
            if pos in self.nbt:
                entry['nbt'] = self.nbt[pos]
            blocks.append(entry)
        os.makedirs(OUT, exist_ok=True)
        nbt.write_file(os.path.join(OUT, filename), {
            'size': self.size, 'entities': [], 'blocks': blocks, 'palette': palette, 'DataVersion': DATA_VERSION,
        })
        print(f'{filename}: {len(blocks)} blocks, {len(palette)} states')


def pane(ns):
    """Glass pane connecting along the wall: ns=True for walls running north-south."""
    return dict(north=ns, south=ns, east=not ns, west=not ns, waterlogged=False)


def stairs(facing, half='bottom'):
    return dict(facing=facing, half=half, shape='straight', waterlogged=False)


def post():
    return dict(up=True, north='none', south='none', east='none', west='none', waterlogged=False)


def varied(x, y, z, *blocks):
    """Deterministic mix of block variants (mostly the first one) for texture."""
    n = (x * 73856093 ^ y * 19349663 ^ z * 83492791) & 0xFFFF
    return blocks[1 + n % (len(blocks) - 1)] if len(blocks) > 1 and n % 7 == 0 else blocks[0]


def ring(lo, hi):
    """Cells on the border of the square lo..hi."""
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if x in (lo, hi) or z in (lo, hi):
                yield x, z


def rect_ring(x0, z0, x1, z1):
    """Cells on the border of the rectangle x0..x1, z0..z1 (ring() only does squares that start on the diagonal)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                yield x, z


def is_corner(x, z, lo, hi):
    return x in (lo, hi) and z in (lo, hi)


def fill(b, x0, y0, z0, x1, y1, z1, name, **props):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                b.set(x, y, z, name, **props)


def hip_roof(b, lo, hi, base_y, stairs_block, top_block):
    """Pyramid roof over lo..hi, one ring per layer, stepping inward. Corners use full blocks."""
    d = 0
    while lo + d <= hi - d:
        a, z_ = lo + d, hi - d
        y = base_y + d
        fill(b, a + 1, y, a + 1, z_ - 1, y, z_ - 1, 'minecraft:air')
        for x, z in ring(a, z_):
            if a == z_ or is_corner(x, z, a, z_):
                b.set(x, y, z, top_block)
            else:
                facing = 'south' if z == a else 'north' if z == z_ else 'east' if x == a else 'west'
                b.set(x, y, z, stairs_block, **stairs(facing))
        d += 1


# Same 11x11 footprint and core (5, 1, 5) for every age, so an upgrade can restyle the building
# without moving the Town Center block entity.
VILLAGE = dict(
    edge='minecraft:polished_andesite', shell='minecraft:stone_bricks', inner='minecraft:spruce_planks',
    wall='minecraft:stone_bricks', wall_b='minecraft:mossy_stone_bricks', wall_c='minecraft:cracked_stone_bricks',
    corner='minecraft:chiseled_stone_bricks', corner_alt='minecraft:stone_bricks',
    log='minecraft:dark_oak_log', plaster='minecraft:calcite', ceiling='minecraft:spruce_planks',
    roof_stairs='minecraft:dark_oak_stairs', roof_top='minecraft:dark_oak_planks',
    banner='minecraft:red_banner', wall_banner='minecraft:red_wall_banner', carpet='minecraft:red_carpet',
    window='minecraft:glass_pane', lantern='minecraft:lantern', flag='minecraft:red_banner',
)
FEUDAL = dict(
    edge='minecraft:stone_bricks', shell='minecraft:polished_andesite', inner='minecraft:oak_planks',
    wall='minecraft:tuff_bricks', wall_b='minecraft:chiseled_tuff_bricks', wall_c='minecraft:tuff_bricks',
    corner='minecraft:chiseled_tuff', corner_alt='minecraft:tuff_bricks',
    log='minecraft:spruce_log', plaster='minecraft:smooth_stone', ceiling='minecraft:oak_planks',
    roof_stairs='minecraft:stone_brick_stairs', roof_top='minecraft:stone_bricks',
    banner='minecraft:white_banner', wall_banner='minecraft:white_wall_banner', carpet='minecraft:white_carpet',
    window='minecraft:iron_bars', lantern='minecraft:lantern', flag='minecraft:orange_banner',
)
CASTLE = dict(
    edge='minecraft:polished_blackstone', shell='minecraft:deepslate_bricks', inner='minecraft:deepslate_tiles',
    wall='minecraft:deepslate_bricks', wall_b='minecraft:cracked_deepslate_bricks', wall_c='minecraft:deepslate_tiles',
    corner='minecraft:chiseled_deepslate', corner_alt='minecraft:polished_deepslate',
    log='minecraft:polished_basalt', plaster='minecraft:deepslate_tiles', ceiling='minecraft:polished_blackstone',
    roof_stairs='minecraft:deepslate_brick_stairs', roof_top='minecraft:polished_blackstone',
    banner='minecraft:blue_banner', wall_banner='minecraft:blue_wall_banner', carpet='minecraft:blue_carpet',
    window='minecraft:iron_bars', lantern='minecraft:soul_lantern', flag='minecraft:blue_banner',
)


def town_center(filename, m):
    """11x11: porch ring at 0/10, ground floor + timber upper story on the 1..9 shell, hip roof, flag."""
    n, lo, hi = 11, 1, 9
    b = Builder(n, 16, n)
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                b.set(x, 0, z, m['edge'])
            elif x in (lo, hi) or z in (lo, hi):
                b.set(x, 0, z, m['shell'])
            else:
                b.set(x, 0, z, m['inner'])
    fill(b, 0, 1, 0, 10, 15, 10, 'minecraft:air')  # clear everything; details below overwrite

    def beam(y):
        for x, z in ring(lo, hi):
            axis = 'y' if is_corner(x, z, lo, hi) else ('x' if z in (lo, hi) else 'z')
            b.set(x, y, z, m['log'], axis=axis)

    for y in range(1, 4):
        for x, z in ring(lo, hi):
            if is_corner(x, z, lo, hi):
                b.set(x, y, z, m['corner'] if y != 2 else m['corner_alt'])
            else:
                b.set(x, y, z, varied(x, y, z, m['wall'], m['wall_b'], m['wall_c']))
    fill(b, 4, 1, hi, 6, 3, hi, 'minecraft:air')  # grand entrance, 3 wide x 3 tall
    for z in (3, 7):
        b.set(lo, 2, z, m['window'], **pane(True))
        b.set(hi, 2, z, m['window'], **pane(True))
    for x in (3, 7):
        b.set(x, 2, lo, m['window'], **pane(False))
    beam(4)
    fill(b, lo + 1, 4, lo + 1, hi - 1, 4, hi - 1, m['ceiling'])

    for y in range(5, 8):
        for x, z in ring(lo, hi):
            if is_corner(x, z, lo, hi) or (z in (lo, hi) and x == 5) or (x in (lo, hi) and z == 5):
                b.set(x, y, z, m['log'], axis='y')
            else:
                b.set(x, y, z, m['plaster'])
    for x, z in ring(lo, hi):
        if not is_corner(x, z, lo, hi) and (x in (3, 7) or z in (3, 7)):
            b.set(x, 6, z, m['window'], **pane(x in (lo, hi)))
    hip_roof(b, 0, 10, 8, m['roof_stairs'], m['roof_top'])
    beam(8)
    b.set(5, 14, 5, 'minecraft:oak_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(5, 15, 5, m['flag'], rotation=0)

    b.set(2, 3, 10, m['wall_banner'], facing='south')
    b.set(8, 3, 10, m['wall_banner'], facing='south')
    b.set(0, 3, 5, m['wall_banner'], facing='west')
    b.set(10, 3, 5, m['wall_banner'], facing='east')
    b.set(5, 3, 0, m['wall_banner'], facing='north')
    for x in (3, 7):
        b.set(x, 1, 10, 'minecraft:stone_brick_wall', **post())
        b.set(x, 2, 10, m['lantern'], hanging=False, waterlogged=False)

    b.set(5, 1, 5, 'warbands:town_center', facing='south')
    for z in (6, 7, 8):
        b.set(5, 1, z, m['carpet'])
    for x in (4, 6):
        b.set(x, 1, 6, m['carpet'])
    for x in (2, 3, 7, 8):
        b.set(x, 1, 2, 'minecraft:bookshelf')
        b.set(x, 2, 2, 'minecraft:bookshelf')
    b.set(5, 1, 2, 'minecraft:lectern', facing='south', has_book=False, powered=False)
    b.set(2, 1, 8, 'minecraft:barrel', facing='up', open=False)
    b.set(2, 2, 8, 'minecraft:barrel', facing='up', open=False)
    b.set(2, 1, 7, 'minecraft:crafting_table')
    upper_floor(b, m)
    # no standing banners indoors: the room is too tight for them (the wall banners outside carry the colors)
    for x in (3, 6):  # not over the stairs (x 7-8): a hanging lantern there hits your head
        for z in (3, 7):
            b.set(x, 3, z, m['lantern'], hanging=True, waterlogged=False)
    b.save(filename)


def upper_floor(b, m):
    """A 2-wide staircase up the east wall (x 7-8, entered at z 8 from the door, climbing north) to the upper story:
    a storage room with chests and barrels along the north wall (every chest open from the front), a map table, a
    lectern and a bed to sleep in town. A fence rails the stairwell. The chests start empty; an age upgrade never
    overwrites containers (BlueprintPlacer.upgrade), so what you store there stays."""
    for y, z in ((1, 7), (2, 6), (3, 5), (4, 4)):
        for x in (7, 8):
            b.set(x, y, z, m['roof_stairs'], facing='north', half='bottom', shape='straight', waterlogged=False)
            for below in range(1, y):
                b.set(x, below, z, m['ceiling'])
    for x in (7, 8):
        for z in (5, 6, 7):
            b.set(x, 4, z, 'minecraft:air')  # stairwell through the ceiling
    rail = {(6, 5): 'south', (6, 6): 'north south', (6, 7): 'north south', (6, 8): 'north east', (7, 8): 'west east',
            (8, 8): 'west east'}
    for (x, z), sides in rail.items():
        b.set(x, 5, z, 'minecraft:spruce_fence', **{d: d in sides.split() for d in ('north', 'south', 'east', 'west')},
              waterlogged=False)
    for x in (2, 4, 6):
        b.set(x, 5, 2, 'minecraft:chest', facing='south', type='single', waterlogged=False)
    for x in (3, 5):
        b.set(x, 5, 2, 'minecraft:barrel', facing='up', open=False)
        b.set(x, 6, 2, m['lantern'], hanging=False, waterlogged=False)
    b.set(2, 5, 6, 'minecraft:cartography_table')
    b.set(2, 5, 7, 'minecraft:lectern', facing='east', has_book=False, powered=False)
    # bed along the south wall, clear of every older container (an upgrade never overwrites those)
    b.set(3, 5, 8, 'minecraft:red_bed', facing='west', part='head', occupied=False)
    b.set(4, 5, 8, 'minecraft:red_bed', facing='west', part='foot', occupied=False)
    b.set(2, 5, 8, 'minecraft:barrel', facing='up', open=False)  # bedside table
    b.set(2, 6, 8, m['lantern'], hanging=False, waterlogged=False)
    for x in range(3, 6):
        for z in range(4, 7):
            b.set(x, 5, z, m['carpet'])
    b.set(5, 6, 5, m['lantern'], hanging=False, waterlogged=False)
    b.set(5, 5, 5, 'minecraft:oak_fence', north=False, south=False, east=False, west=False, waterlogged=False)


def house():
    """7x7 cottage: 5x5 shell (1..5), gable roof along x with eaves, brick chimney on the east side."""
    n, lo, hi = 7, 1, 5
    b = Builder(n, 9, n)
    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            shell = x in (lo, hi) or z in (lo, hi)
            b.set(x, 0, z, 'minecraft:dirt_path' if edge else 'minecraft:cobblestone' if shell else 'minecraft:oak_planks')
    fill(b, 0, 1, 0, 6, 8, 6, 'minecraft:air')
    # walls: cobblestone base, plank walls, spruce corner posts
    for y in range(1, 4):
        for x, z in ring(lo, hi):
            if is_corner(x, z, lo, hi):
                b.set(x, y, z, 'minecraft:spruce_log', axis='y')
            elif y == 1:
                b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
            else:
                b.set(x, y, z, 'minecraft:oak_planks')
    b.set(3, 1, hi, 'minecraft:air'); b.set(3, 2, hi, 'minecraft:air')  # door
    b.set(lo, 2, 3, 'minecraft:glass_pane', **pane(True))
    b.set(3, 2, lo, 'minecraft:glass_pane', **pane(False))
    for y in (1, 2, 3):
        b.set(hi, y, 3, 'minecraft:bricks')  # fireplace back wall
    # gable roof: ridge runs east-west, eaves overhang on every side
    for step, (zn, zs) in enumerate(((0, 6), (1, 5), (2, 4))):
        y = 4 + step
        for x in range(n):
            b.set(x, y, zn, 'minecraft:spruce_stairs', **stairs('south'))
            b.set(x, y, zs, 'minecraft:spruce_stairs', **stairs('north'))
        for x in (lo, hi):  # gable ends
            for z in range(zn + 1, zs):
                b.set(x, y, z, 'minecraft:spruce_planks')
    for x in range(lo, hi + 1):  # close the gap above the long walls
        b.set(x, 4, lo, 'minecraft:spruce_planks'); b.set(x, 4, hi, 'minecraft:spruce_planks')
    for x in range(n):
        b.set(x, 7, 3, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    b.set(lo, 5, 3, 'minecraft:glass_pane', **pane(True))  # attic window, west gable
    # chimney outside the east wall, smoking campfire on top
    for y in range(0, 8):
        b.set(6, y, 3, 'minecraft:bricks')
    b.set(6, 8, 3, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    # porch: hanging lanterns under the eaves, flower pots
    b.set(2, 3, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(4, 3, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(1, 1, 6, 'minecraft:potted_poppy')
    b.set(5, 1, 6, 'minecraft:potted_blue_orchid')
    # inside: bed, hearth by the fireplace, barrel
    b.set(2, 1, 2, 'minecraft:red_bed', facing='north', part='head', occupied=False)
    b.set(2, 1, 3, 'minecraft:red_bed', facing='north', part='foot', occupied=False)
    b.set(4, 1, 3, 'warbands:hearth')
    b.set(2, 1, 4, 'minecraft:barrel', facing='up', open=False)
    b.save('house.nbt')


def barracks():
    """9x9 drill hall in the Town Center's village style: stone ground floor, dark oak beam, calcite
    upper story, gable roof with eaves. Inside it's one tall hall open to the rafters (a plank ceiling at the eaves
    used to make it look like a second floor you couldn't reach): low windows to see out, clerestory windows up high,
    lanterns on chains from the ridge. Core is the center (4, 1, 4); the open door is on the south side."""
    n, lo, hi = 9, 1, 7
    b = Builder(n, 11, n)
    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            shell = x in (lo, hi) or z in (lo, hi)
            floor = varied(x, 0, z, 'minecraft:dirt_path', 'minecraft:coarse_dirt') if edge \
                else 'minecraft:stone_bricks' if shell else 'minecraft:spruce_planks'
            b.set(x, 0, z, floor)
    fill(b, 0, 1, 0, 8, 10, 8, 'minecraft:air')
    for x, z in ring(lo, hi):
        along_x = z in (lo, hi)
        for y in range(1, 6):
            if is_corner(x, z, lo, hi):
                b.set(x, y, z, 'minecraft:spruce_log', axis='y')
            elif y <= 2:
                b.set(x, y, z, varied(x, y, z, 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks'))
            elif y == 3:
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='x' if along_x else 'z')
            elif (x if along_x else z) == 4:
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')  # middle post of the upper story
            else:
                b.set(x, y, z, 'minecraft:calcite')
    # door and windows
    b.set(4, 1, hi, 'minecraft:air'); b.set(4, 2, hi, 'minecraft:air')
    tr = dict(half='bottom', open=True, powered=False, waterlogged=False)
    for x in (2, 6):  # clerestory: glass with shutters hung on the outside
        for z, outer in ((lo, lo - 1), (hi, hi + 1)):
            b.set(x, 4, z, 'minecraft:glass_pane', **pane(False))
            for sx in (x - 1, x + 1):
                b.set(sx, 4, outer, 'minecraft:spruce_trapdoor', facing='north' if z == lo else 'south', **tr)
    for z in (2, 6):
        for x, outer in ((lo, lo - 1), (hi, hi + 1)):
            b.set(x, 4, z, 'minecraft:glass_pane', **pane(True))
            for sz in (z - 1, z + 1):
                b.set(outer, 4, sz, 'minecraft:spruce_trapdoor', facing='west' if x == lo else 'east', **tr)
    for x in (2, 6):  # low windows
        b.set(x, 2, lo, 'minecraft:iron_bars', **pane(False))
        b.set(x, 2, hi, 'minecraft:iron_bars', **pane(False))
    for z in (2, 6):
        b.set(lo, 2, z, 'minecraft:iron_bars', **pane(True))
        b.set(hi, 2, z, 'minecraft:iron_bars', **pane(True))
    # wall plate (no ceiling: the hall is open to the roof), then a gable roof: ridge runs east-west, eaves overhang
    for x, z in ring(lo, hi):
        b.set(x, 6, z, 'minecraft:dark_oak_log', axis='y' if is_corner(x, z, lo, hi) else ('x' if z in (lo, hi) else 'z'))
    for step, (zn, zs) in enumerate(((0, 8), (1, 7), (2, 6), (3, 5))):
        y = 6 + step
        for x in range(n):
            b.set(x, y, zn, 'minecraft:dark_oak_stairs', **stairs('south'))
            b.set(x, y, zs, 'minecraft:dark_oak_stairs', **stairs('north'))
        if step:
            for x in (lo, hi):  # gable ends
                for z in range(zn + 1, zs):
                    b.set(x, y, z, 'minecraft:spruce_planks')
    for x in range(n):
        b.set(x, 10, 4, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (lo, hi):
        b.set(x, 8, 4, 'minecraft:iron_bars', **pane(True))  # gable windows
    # front: red banner over the door, lantern posts at the yard corners
    b.set(4, 5, 8, 'minecraft:red_wall_banner', facing='south')
    fe = dict(north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(0, 1, 8, 'minecraft:spruce_fence', **fe)
    b.set(0, 2, 8, 'minecraft:lantern', hanging=False, waterlogged=False)
    for y in range(1, 6):  # banner post
        b.set(8, y, 8, 'minecraft:spruce_fence', **fe)
    b.set(8, 6, 8, 'minecraft:red_banner', rotation=0)
    for x in (1, 2):  # drill yard: straw dummies with targets
        b.set(x, 1, 8, 'minecraft:hay_block', axis='y')
    b.set(1, 2, 8, 'minecraft:target')
    b.set(7, 1, 8, 'minecraft:hay_block', axis='y')
    b.set(7, 2, 8, 'minecraft:target')
    # inside: weapon racks along the walls, a carpet runner to the core
    b.set(2, 1, 2, 'minecraft:grindstone', face='floor', facing='south')
    b.set(6, 1, 2, 'minecraft:smithing_table')
    b.set(2, 1, 6, 'minecraft:barrel', facing='up', open=False)
    b.set(6, 1, 6, 'minecraft:fletching_table')
    b.set(2, 1, 4, 'minecraft:red_banner', rotation=12)
    b.set(6, 1, 4, 'minecraft:red_banner', rotation=4)
    for z in (2, 6):  # lanterns on chains from the rafters
        b.set(4, 7, z, 'minecraft:iron_chain', axis='y', waterlogged=False)
        b.set(4, 6, z, 'minecraft:iron_chain', axis='y', waterlogged=False)
        b.set(4, 5, z, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(4, 1, 5, 'minecraft:red_carpet'); b.set(4, 1, 6, 'minecraft:red_carpet')
    b.set(4, 1, 4, 'warbands:barracks', facing='south')
    b.save('barracks.nbt')


def archery_range():
    """9x9 open fenced yard. Core is (4, 1, 4) and the gate is on the south side. Height 6."""
    n, lo, hi = 9, 1, 7
    b = Builder(n, 6, n)

    def on_fence(x, z):
        return lo <= x <= hi and lo <= z <= hi and (x in (lo, hi) or z in (lo, hi)) and not (x == 4 and z == hi)

    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            lane = 2 <= x <= 6 and 3 <= z <= 6
            if edge:
                b.set(x, 0, z, 'minecraft:cobblestone')
            elif lane:
                b.set(x, 0, z, 'minecraft:grass_block', snowy=False)
            else:
                b.set(x, 0, z, 'minecraft:coarse_dirt')
    fill(b, 0, 1, 0, 8, 5, 8, 'minecraft:air')
    for x, z in ring(lo, hi):
        if is_corner(x, z, lo, hi):
            b.set(x, 1, z, 'minecraft:spruce_log', axis='y')
            b.set(x, 2, z, 'minecraft:spruce_log', axis='y')
            if z == lo:
                b.set(x, 3, z, 'minecraft:spruce_log', axis='y')
        elif on_fence(x, z):
            b.set(x, 1, z, 'minecraft:oak_fence',
                  north=on_fence(x, z - 1) or (is_corner(x, z - 1, lo, hi)),
                  south=on_fence(x, z + 1) or (is_corner(x, z + 1, lo, hi)),
                  east=on_fence(x + 1, z) or (is_corner(x + 1, z, lo, hi)),
                  west=on_fence(x - 1, z) or (is_corner(x - 1, z, lo, hi)),
                  waterlogged=False)
    for x in range(2, 7):
        b.set(x, 3, 1, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
        b.set(x, 3, 2, 'minecraft:spruce_stairs', **stairs('south'))
    b.set(4, 2, 1, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(2, 1, 2, 'minecraft:target', power=0)
    b.set(6, 1, 2, 'minecraft:target', power=0)
    b.set(4, 1, 2, 'minecraft:hay_block', axis='y')
    b.set(4, 1, 5, 'minecraft:green_carpet')
    b.set(4, 1, 6, 'minecraft:green_carpet')
    b.set(4, 1, 7, 'minecraft:green_carpet')
    b.set(4, 1, 4, 'warbands:archery_range', facing='south')
    b.save('archery_range.nbt')


def blacksmith():
    """7x7 dark forge. Core is (3, 1, 3) and the wide open front is on the south side. Height 11.
    An eternal fire burns on magma in the back hearth, behind an iron grate. Every block within the
    fire's spread range is non-flammable (stone roof, no wood), so it can never burn the town."""
    n, hi = 7, 6
    b = Builder(n, 11, n)
    for x in range(n):
        for z in range(n):
            edge = x in (0, hi) or z in (0, hi)
            b.set(x, 0, z, 'minecraft:polished_blackstone_bricks' if edge
                  else varied(x, 0, z, 'minecraft:cobbled_deepslate', 'minecraft:deepslate_tiles', 'minecraft:polished_deepslate'))
    fill(b, 0, 1, 0, hi, 10, hi, 'minecraft:air')
    for y in range(1, 5):
        for x, z in ring(0, hi):
            if is_corner(x, z, 0, hi):
                b.set(x, y, z, 'minecraft:polished_basalt', axis='y')
            elif y == 1:
                b.set(x, y, z, varied(x, y, z, 'minecraft:polished_blackstone_bricks', 'minecraft:cracked_polished_blackstone_bricks'))
            elif y == 4:
                b.set(x, y, z, 'minecraft:polished_blackstone')
            else:
                b.set(x, y, z, varied(x, y, z, 'minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks'))
    # wide smithy front, windows on the sides
    fill(b, 2, 1, hi, 4, 2, hi, 'minecraft:air')
    fill(b, 2, 3, hi, 4, 3, hi, 'minecraft:polished_blackstone')
    b.set(1, 1, hi, 'minecraft:magma_block'); b.set(5, 1, hi, 'minecraft:magma_block')  # glowing coals by the door
    for z in (2, 4):
        b.set(0, 2, z, 'minecraft:iron_bars', **pane(True))
        b.set(hi, 2, z, 'minecraft:iron_bars', **pane(True))
    # ceiling, then a dark tile gable roof (ridge east-west)
    fill(b, 1, 5, 1, hi - 1, 5, hi - 1, 'minecraft:polished_blackstone')
    for step, (zn, zs) in enumerate(((0, 6), (1, 5), (2, 4))):
        y = 5 + step
        for x in range(n):
            b.set(x, y, zn, 'minecraft:deepslate_tile_stairs', **stairs('south'))
            b.set(x, y, zs, 'minecraft:deepslate_tile_stairs', **stairs('north'))
        for x in (0, hi):  # gable ends
            for z in range(zn + 1, zs):
                b.set(x, y, z, 'minecraft:deepslate_tiles')
    for x in range(n):
        b.set(x, 8, 3, 'minecraft:deepslate_tile_slab', type='bottom', waterlogged=False)
    # the forge: furnaces either side of an eternal fire, stone hood, chimney through the roof
    b.set(3, 0, 1, 'minecraft:magma_block')
    b.set(3, 1, 1, 'minecraft:fire', age=0, north=False, south=False, east=False, west=False, up=False)
    b.set(3, 1, 2, 'minecraft:iron_bars', **pane(False))
    b.set(2, 1, 1, 'minecraft:blast_furnace', facing='south', lit=False)
    b.set(4, 1, 1, 'minecraft:furnace', facing='south', lit=False)
    b.set(2, 2, 1, 'minecraft:polished_blackstone_bricks')
    b.set(4, 2, 1, 'minecraft:polished_blackstone_bricks')
    fill(b, 2, 3, 1, 4, 4, 1, 'minecraft:polished_blackstone_bricks')
    for y in range(1, 10):
        b.set(3, y, 0, 'minecraft:polished_blackstone_bricks')
    b.set(3, 10, 0, 'minecraft:campfire', lit=True, signal_fire=False, facing='south', waterlogged=False)
    # tools: anvil and quench tub on the left, smithing table and grindstone on the right
    b.set(1, 1, 3, 'minecraft:anvil', facing='north')
    b.set(1, 1, 1, 'minecraft:water_cauldron', level=3)
    b.set(5, 1, 3, 'minecraft:smithing_table')
    b.set(5, 1, 1, 'minecraft:grindstone', face='floor', facing='west')
    b.set(5, 1, 5, 'minecraft:barrel', facing='up', open=False)
    b.set(1, 4, 5, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(1, 3, 5, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(5, 4, 3, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(5, 3, 3, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(3, 1, 3, 'warbands:blacksmith', facing='south')
    b.save('blacksmith.nbt')


def stable():
    """9x9 red barn. Core is (4, 1, 4) in the central aisle, the door is on the south side. Height 10.
    Four 2x2 stalls either side of the aisle; BuildingDecor puts a Stable Horse in the (2..3, 2..3) and
    (5..6, 5..6) stalls, so keep those clear. The ridge runs north-south, so the front is a gable with a hayloft."""
    n, lo, hi = 9, 1, 7
    b = Builder(n, 10, n)
    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            stall = x in (2, 3, 5, 6) and lo < z < hi
            if edge:
                floor = varied(x, 0, z, 'minecraft:dirt_path', 'minecraft:coarse_dirt')
            elif x in (lo, hi) or z in (lo, hi):
                floor = 'minecraft:cobblestone'
            elif stall:
                floor = varied(x, 0, z, 'minecraft:coarse_dirt', 'minecraft:hay_block', 'minecraft:rooted_dirt')
            else:
                floor = 'minecraft:spruce_planks'
            b.set(x, 0, z, floor, **({'axis': 'y'} if floor == 'minecraft:hay_block' else {}))
    fill(b, 0, 1, 0, 8, 9, 8, 'minecraft:air')
    for x, z in ring(lo, hi):
        along_x = z in (lo, hi)
        for y in range(1, 5):
            if is_corner(x, z, lo, hi) or (not along_x and z == 4):
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
            elif y == 1:
                b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
            elif y == 4:
                b.set(x, y, z, 'minecraft:stripped_dark_oak_log', axis='x' if along_x else 'z')
            else:
                b.set(x, y, z, 'minecraft:mangrove_planks')
    # wide barn door, stall windows
    fill(b, 3, 1, hi, 5, 3, hi, 'minecraft:air')
    for z in (2, 6):
        for x in (lo, hi):
            b.set(x, 3, z, 'minecraft:spruce_fence', north=True, south=True, east=False, west=False, waterlogged=False)
    # loft floor, then a spruce gable roof; the gables face north and south
    fill(b, lo, 5, lo, hi, 5, hi, 'minecraft:spruce_planks')
    for step, (xw, xe) in enumerate(((0, 8), (1, 7), (2, 6), (3, 5))):
        y = 5 + step
        for z in range(n):
            b.set(xw, y, z, 'minecraft:spruce_stairs', **stairs('east'))
            b.set(xe, y, z, 'minecraft:spruce_stairs', **stairs('west'))
        if step:
            for z in (lo, hi):
                for x in range(xw + 1, xe):
                    b.set(x, y, z, 'minecraft:mangrove_planks')
    for z in range(n):
        b.set(4, 9, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    b.set(4, 6, hi, 'minecraft:air'); b.set(4, 7, hi, 'minecraft:air')  # hayloft door
    for x, z in ((3, 6), (4, 6), (5, 6), (4, 5), (3, 5)):
        b.set(x, 6, z, 'minecraft:hay_block', axis='y')
    b.set(4, 7, 6, 'minecraft:hay_block', axis='x')
    # stalls: fence dividers across the middle, hay and a barrel in the two empty ones
    for x in (2, 3, 5, 6):
        b.set(x, 1, 4, 'minecraft:spruce_fence', east=True, west=True, north=False, south=False, waterlogged=False)
    b.set(2, 1, 6, 'minecraft:hay_block', axis='y'); b.set(2, 1, 5, 'minecraft:hay_block', axis='z')
    b.set(2, 2, 6, 'minecraft:hay_block', axis='x')
    b.set(6, 1, 2, 'minecraft:barrel', facing='up', open=False)
    b.set(6, 1, 3, 'minecraft:hay_block', axis='y')
    b.set(4, 4, 2, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(4, 4, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    # yard: hay stack on the west, water trough on the east, lantern posts at the door
    for z, h in ((1, 2), (2, 2), (3, 1)):
        for y in range(1, h + 1):
            b.set(0, y, z, 'minecraft:hay_block', axis='z')
    b.set(8, 1, 5, 'minecraft:water_cauldron', level=3)
    b.set(8, 1, 6, 'minecraft:water_cauldron', level=3)
    for x in (0, 8):
        b.set(x, 1, 8, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
        b.set(x, 2, 8, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(4, 1, 4, 'warbands:stable', facing='south')
    b.save('stable.nbt')


def watch_tower():
    """5x5 stone tower. Core is (2, 1, 2). Door on the south side. Height 10.

    Roof spots the game posts soldiers on are (1, 8, 2), (3, 8, 2), (2, 8, 1) and (2, 8, 3):
    one block in from the core, 7 above it. Those cells stay air. The ladder runs up the north-west corner to a
    trapdoor at (1, 7, 1), so the player can climb onto the roof too. Arrow slits, corner posts with lanterns.
    """
    n = 5
    b = Builder(n, 10, n)
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, 'minecraft:cobblestone' if x in (0, n - 1) or z in (0, n - 1) else 'minecraft:stone_bricks')
    fill(b, 0, 1, 0, 4, 9, 4, 'minecraft:air')
    for y in range(1, 7):
        for x in range(n):
            for z in range(n):
                edge = x in (0, n - 1) or z in (0, n - 1)
                door = x == 2 and z == 4 and y <= 2
                if edge and not door:
                    corner = x in (0, n - 1) and z in (0, n - 1)
                    b.set(x, y, z, 'minecraft:mossy_stone_bricks' if corner and y < 3
                          else 'minecraft:stone_bricks' if (x + z + y) % 7 else 'minecraft:cracked_stone_bricks')
    for x in range(n):  # a dark oak band, then a corbelled course under the parapet
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                if not (x == 2 and z == 4):
                    b.set(x, 5, z, 'minecraft:stripped_dark_oak_log', axis='z' if x in (0, n - 1) else 'x')
                if not (x in (0, n - 1) and z in (0, n - 1)):
                    face = 'south' if z == n - 1 else 'north' if z == 0 else 'east' if x == n - 1 else 'west'
                    b.set(x, 6, z, 'minecraft:stone_brick_stairs', **stairs(face, 'top'))
    for x, z, east in ((0, 2, False), (4, 2, False), (2, 0, True)):  # arrow slits
        b.set(x, 4, z, 'minecraft:iron_bars', north=not east, south=not east, east=east, west=east, waterlogged=False)
    for y in range(1, 8):
        b.set(1, y, 1, 'minecraft:ladder', facing='south', waterlogged=False)
    for x in range(n):
        for z in range(n):
            if (x, z) != (1, 1):
                b.set(x, 7, z, 'minecraft:stone_bricks')
    b.set(1, 7, 1, 'minecraft:spruce_trapdoor', facing='south', half='top', open=False, powered=False, waterlogged=False)
    for x in range(n):
        for z in range(n):
            rim = x in (0, n - 1) or z in (0, n - 1)
            corner = x in (0, n - 1) and z in (0, n - 1)
            if corner:
                b.set(x, 8, z, 'minecraft:stone_brick_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False)
                b.set(x, 9, z, 'minecraft:lantern', hanging=False, waterlogged=False)
            elif rim and (x + z) % 2 == 0:
                b.set(x, 8, z, 'minecraft:spruce_planks')  # hoarding boards between the stone merlons
            elif rim:
                b.set(x, 8, z, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
    for x, z, rot in ((2, 4, 8),):  # banners flying from the parapet
        b.set(x, 9, z, 'minecraft:red_banner', rotation=rot)
    b.set(3, 6, 3, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(2, 1, 2, 'warbands:watch_tower', facing='south')
    b.save('watch_tower.nbt')


def siege_workshop():
    """9x9 timber workshop with open sides and a wide south bay, so the ram (scaled 1.4, under 2 blocks
    wide) rolls out. Core is (4, 1, 4); keep x 3..5, z 5..8 clear. A crane over the bay hoists a ram log.
    Height 10."""
    n, lo, hi = 9, 1, 7
    b = Builder(n, 10, n)
    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:coarse_dirt', 'minecraft:dirt_path', 'minecraft:gravel') if edge
                  else varied(x, 0, z, 'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:mossy_stone_bricks'))
    fill(b, 0, 1, 0, 8, 9, 8, 'minecraft:air')
    trap = dict(half='top', open=True, powered=False, waterlogged=False)
    for x, z in ring(lo, hi):
        along_x = z in (lo, hi)
        pos = x if along_x else z
        mid = pos == 4
        for y in range(1, 5):
            if is_corner(x, z, lo, hi) or (mid and z != hi):
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
            elif y == 4:
                b.set(x, y, z, 'minecraft:stripped_spruce_log', axis='x' if along_x else 'z')
            elif z == hi:
                continue  # the bay: open from post to post
            elif y == 1:
                b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone_bricks'))
            elif y == 3 and pos in (2, 6):  # shuttered window slot
                face = 'north' if z == lo else 'west' if x == lo else 'east'
                b.set(x, y, z, 'minecraft:spruce_trapdoor', facing=face, **trap)
            else:
                b.set(x, y, z, 'minecraft:spruce_planks' if y == 3 else 'minecraft:dark_oak_planks')
    for x, face in ((2, 'east'), (6, 'west')):  # arch braces under the bay header
        b.set(x, 3, hi, 'minecraft:dark_oak_stairs', **stairs(face, 'top'))
    # dark oak gable roof, ridge north-south; the front gable is open truss work over the bay
    for step, (xw, xe) in enumerate(((0, 8), (1, 7), (2, 6), (3, 5))):
        y = 5 + step
        for z in range(n):
            tile = 'minecraft:dark_oak_stairs' if step in (0, 3) or z in (0, 8) else 'minecraft:spruce_stairs'
            b.set(xw, y, z, tile, **stairs('east'))
            b.set(xe, y, z, tile, **stairs('west'))
        for x in range(xw + 1, xe):
            b.set(x, y, lo, 'minecraft:spruce_planks')
            if x in (xw + 1, xe - 1) or x == 4:
                b.set(x, y, hi, 'minecraft:dark_oak_log', axis='y' if x == 4 else 'x')
    for x in (3, 5):
        b.set(x, 5, hi, 'minecraft:dark_oak_log', axis='x')
    for z in range(n):
        b.set(4, 9, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (0, 8):  # eave shadow: upside-down stairs under the overhang at the front and back
        for z in (0, 8):
            b.set(x, 4, z, 'minecraft:dark_oak_stairs', **stairs('west' if x else 'east', 'top'))
    b.set(4, 8, 8, 'minecraft:dark_oak_log', axis='z')  # crane jib
    b.set(4, 7, 8, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(4, 6, 8, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(4, 5, 8, 'minecraft:stripped_oak_log', axis='x')  # the log being hoisted
    # inside: the ram slung under a tie beam, tools along the walls
    for x in range(2, 7):
        b.set(x, 4, 3, 'minecraft:stripped_spruce_log', axis='x')
    for x in (3, 5):
        b.set(x, 3, 3, 'minecraft:iron_chain', axis='y', waterlogged=False)
    for x in range(2, 6):
        b.set(x, 2, 3, 'minecraft:stripped_oak_log', axis='x')
    b.set(6, 2, 3, 'minecraft:iron_block')  # the ram's head
    for x in (2, 6):
        b.set(x, 1, 3, 'minecraft:oak_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(2, 1, 2, 'minecraft:crafting_table')
    b.set(6, 1, 2, 'minecraft:barrel', facing='up', open=False)
    b.set(3, 1, 2, 'minecraft:oak_log', axis='x')  # sawn timber and shavings
    b.set(5, 1, 2, 'minecraft:oak_slab', type='bottom', waterlogged=False)
    b.set(2, 1, 6, 'minecraft:grindstone', face='floor', facing='east')
    b.set(6, 1, 6, 'minecraft:smithing_table')
    b.set(4, 4, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    for y in range(5, 9):
        b.set(4, y, 6, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(4, 4, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    # yard: stepped timber stacks on both sides, a plank ramp up the east stack, lantern posts at the bay
    for z in range(1, 7):
        b.set(0, 1, z, 'minecraft:spruce_log', axis='z')
        if 2 <= z <= 5:
            b.set(0, 2, z, 'minecraft:spruce_log', axis='z')
        if 3 <= z <= 4:
            b.set(0, 3, z, 'minecraft:stripped_spruce_log', axis='z')
    for z in (1, 2):
        for y in (1, 2):
            b.set(8, y, z, 'minecraft:oak_log', axis='z')
    b.set(8, 1, 3, 'minecraft:oak_stairs', **stairs('north'))
    b.set(8, 1, 4, 'minecraft:oak_slab', type='bottom', waterlogged=False)
    b.set(8, 1, 6, 'minecraft:barrel', facing='up', open=False)
    b.set(8, 1, 5, 'minecraft:hay_block', axis='y')
    for x in (0, 8):
        b.set(x, 1, 8, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
        b.set(x, 2, 8, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(4, 1, 4, 'warbands:siege_workshop', facing='south')
    b.save('siege_workshop.nbt')


# ---------------------------------------------------------------- enemy camps (Phase 3)
# The original camp layouts. Their garrisons and every other layout live in gen_camps.py, which also writes
# these files (run that instead). Entrance on the south side (z = max) like blueprints;
# the game turns the camp so its entrance faces the town that sent the War Map.

def camp_floor(b, n):
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:coarse_dirt', 'minecraft:dirt_path', 'minecraft:podzol'))


def tent(b, x0, z0, length, cloth):
    """A-frame tent along z: cloth walls on x0 and x0+2, ridge on x0+1, fence poles at the ends."""
    for z in range(z0, z0 + length):
        b.set(x0, 1, z, cloth)
        b.set(x0 + 2, 1, z, cloth)
        b.set(x0 + 1, 2, z, cloth)
    b.set(x0 + 1, 1, z0, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)


def standard(b, x, y, z):
    """The camp's core (claimed on conquest) with a black banner flying above it."""
    b.set(x, y, z, 'warbands:camp_standard')
    b.set(x, y + 1, z, 'minecraft:black_banner', rotation=0)


# ---- Phase 4D traps: telegraphed (all visible if you look), dead once the camp is claimed. ----

ARROWS = {'id': 'minecraft:dispenser', 'Items': [{'Slot': 0, 'id': 'minecraft:arrow', 'count': 9}]}


def alarm(b, x, z):
    """A brass plate on the floor: anything but the camp's own guards rings the whole camp awake."""
    b.set(x, 1, z, 'warbands:alarm_plate')


def spikes(b, *cells):
    """Caltrops underfoot (Feudal+ camps): whoever walks on them is slowed and hurt."""
    for x, z in cells:
        b.set(x, 1, z, 'warbands:caltrops')


def pitfall(b, x, z, soft='powder_snow'):
    """An open trapdoor (the tell) over a one-block drop into powder snow or berry bushes."""
    props = {} if soft == 'powder_snow' else {'age': 3}
    b.set(x, 0, z, 'minecraft:' + soft, **props)
    b.set(x, 1, z, 'minecraft:spruce_trapdoor', facing='north', half='bottom', open=True, powered=False,
          waterlogged=False)


def _tripwire(b, x0, z, width, post):
    """Posts and facing hooks with live wire between, wired across cells x0+1..x0+width at (y=1, z)."""
    b.set(x0, 1, z, post)
    b.set(x0 + width + 1, 1, z, post)
    b.set(x0 + 1, 1, z, 'minecraft:tripwire_hook', facing='east', attached=True, powered=False)
    for x in range(x0 + 2, x0 + width + 1):
        b.set(x, 1, z, 'minecraft:tripwire', east=True, west=True, north=False, south=False,
              attached=True, powered=False, disarmed=False)
    b.set(x0 + width, 1, z, 'minecraft:tripwire_hook', facing='west', attached=True, powered=False)


def arrow_slit(b, x0, z, width, post='minecraft:stone_bricks'):
    """Tripwire whose west post is a stocked dispenser aimed down the wire: the arrow crosses the cells an intruder
    trips (a dispenser beside the hook shot along an empty column and missed). Sealed while the camp stands."""
    _tripwire(b, x0, z, width, post)
    b.set(x0, 1, z, 'minecraft:dispenser', facing='east', triggered=False, nbt=dict(ARROWS))


def keg_wire(b, x0, z, width, post='minecraft:cobblestone'):
    """Tripwire -> a powder keg beside it (Castle camps): the blast hurts but breaks no blocks."""
    _tripwire(b, x0, z, width, post)
    b.set(x0 + 1, 1, z - 1, 'warbands:powder_keg')


# ---- bandit camps (Village Age): rough spruce stakes, canvas, stolen goods. Shared with camps_village.py. ----

BANDIT_NO = dict(north=False, south=False, east=False, west=False, waterlogged=False)


def bandit_stakes(b, cells, cap=None):
    """A ragged palisade: stakes 3-5 high, mostly spruce with oak and stripped logs mixed in, some sharpened with a
    fence tip, on a cobble and gravel footing. {@code cap} pins chosen cells to a height (no tip)."""
    cap = cap or {}
    for x, z in cells:
        h = cap.get((x, z), (3, 4, 5, 4, 3, 4, 4)[(x * 5 + z * 3) % 7])
        wood = ('minecraft:stripped_spruce_log' if (x * 7 + z) % 5 == 0 else
                'minecraft:oak_log' if (x + z * 3) % 6 == 0 else 'minecraft:spruce_log')
        b.set(x, 0, z, varied(x, 0, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:gravel'))
        for y in range(1, h + 1):
            b.set(x, y, z, wood, axis='y')
        if h < 5 and (x, z) not in cap and (x * 3 + z) % 3 == 0:
            b.set(x, h + 1, z, 'minecraft:spruce_fence', **BANDIT_NO)


def bandit_hip_roof(b, x0, z0, x1, z1, y, stair, top):
    """Hipped roof over the rectangle x0..x1, z0..z1, one ring per layer stepping in; corners and the peak are full
    blocks. Unlike hip_roof it leaves the inside of each layer alone, so posts can run up into it."""
    d = 0
    while x0 + d <= x1 - d and z0 + d <= z1 - d:
        a0, a1, c0, c1 = x0 + d, x1 - d, z0 + d, z1 - d
        for x, z in rect_ring(a0, c0, a1, c1):
            if a0 == a1 or c0 == c1 or (x in (a0, a1) and z in (c0, c1)):
                b.set(x, y + d, z, top)
            else:
                face = 'south' if z == c0 else 'north' if z == c1 else 'east' if x == a0 else 'west'
                b.set(x, y + d, z, stair, **stairs(face))
        d += 1


def bandit_edge(b, n, keep_clear=()):
    """Grass, moss and podzol on the outer ring with ferns and tufts, so the camp doesn't sit on a square slab."""
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                if (x, z) in keep_clear:
                    continue
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'))
                if (x * 5 + z * 3) % 4 == 0:
                    b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')


def bandit_camp():
    """15x15 bandit hideout. A ragged stake palisade with a lintel gate, a collapsed stretch in the west wall (a
    second way in, with a berry pit just inside), the chief's red war tent at the back with the standard in front of
    it, canvas lean-tos against the side walls (bedrolls on the west, stolen goods on the east), a cookfire and two
    plank firing steps for the archers by the gate. Core (7, 1, 5). Height 8."""
    n, lo, hi = 15, 1, 13
    b = Builder(n, 8, n)
    camp_floor(b, n)
    fill(b, 0, 1, 0, n - 1, 7, n - 1, 'minecraft:air')
    gate_path = [(x, n - 1) for x in (6, 7, 8)]
    bandit_edge(b, n, keep_clear=gate_path + [(0, z) for z in (7, 8, 9)])
    for x, z in gate_path:
        b.set(x, 0, z, 'minecraft:dirt_path')
    for z in range(6, 14):
        b.set(7, 0, z, 'minecraft:dirt_path')
        if z > 10:
            b.set(6, 0, z, 'minecraft:dirt_path')
            b.set(8, 0, z, 'minecraft:dirt_path')
    for x, z in ((0, 7), (0, 8), (0, 9), (1, 8), (2, 7), (2, 9), (3, 8)):  # trodden path through the breach
        b.set(x, 0, z, 'minecraft:dirt_path')

    # palisade: one continuous wall (cobble footing, spruce logs, a stripped beam on top, sharpened tips), a breach
    # in the west
    walls = [c for c in ring(lo, hi) if not is_corner(c[0], c[1], lo, hi) and c not in
             [(x, hi) for x in range(4, 11)] + [(lo, z) for z in (7, 8, 9)]]
    for x, z in walls:
        b.set(x, 0, z, varied(x, 0, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:gravel'))
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
        for y in (2, 3):
            b.set(x, y, z, 'minecraft:spruce_log', axis='y')
        b.set(x, 4, z, 'minecraft:stripped_spruce_log', axis='z' if x in (lo, hi) else 'x')
        if (x + z) % 2 == 0:
            b.set(x, 5, z, 'minecraft:spruce_fence', **BANDIT_NO)
    for x, z in ((lo, lo), (hi, lo), (lo, hi), (hi, hi)):  # corner posts with lanterns
        b.set(x, 1, z, 'minecraft:mossy_cobblestone')
        for y in range(2, 7):
            b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
        b.set(x, 7, z, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x, z in ((0, 4), (14, 4), (14, 10), (4, 0), (10, 0)):  # outside braces break the runs
        b.set(x, 0, z, 'minecraft:mossy_cobblestone')
        b.set(x, 1, z, 'minecraft:spruce_log', axis='y')
        b.set(x, 2, z, 'minecraft:stripped_spruce_log', axis='y')
    for z in (7, 9):  # the breach: two stumps and a gap
        b.set(lo, 1, z, 'minecraft:cobblestone')
        b.set(lo, 2, z, 'minecraft:stripped_spruce_log', axis='y')
        if z == 9:
            b.set(lo, 3, z, 'minecraft:spruce_log', axis='y')
    b.set(0, 1, 11, 'minecraft:spruce_log', axis='z')  # a stake that fell outwards
    b.set(0, 1, 12, 'minecraft:spruce_log', axis='z')
    pitfall(b, 2, 8, 'sweet_berry_bush')

    # gate: two roofed towers joined by a bridge over the 3-wide gate
    for tx in (4, 9):
        for x in (tx, tx + 1):
            for z in (12, 13):
                b.set(x, 0, z, 'minecraft:cobblestone')
                b.set(x, 1, z, 'minecraft:mossy_cobblestone' if (x + z) % 2 else 'minecraft:cobblestone')
                b.set(x, 2, z, 'minecraft:cobblestone')
                for y in (3, 4, 5):
                    b.set(x, y, z, 'minecraft:dark_oak_log' if (x, z) in ((tx, 12), (tx + 1, 13)) else 'minecraft:spruce_log',
                          axis='y')
        for x in (tx, tx + 1):  # a slit in the outer face
            b.set(x, 4, 13, 'minecraft:spruce_trapdoor', facing='south', half='bottom', open=True, powered=False,
                  waterlogged=False)
        bandit_hip_roof(b, tx - 1, 11, tx + 2, 14, 6, 'minecraft:spruce_stairs', 'minecraft:spruce_planks')
    for x in (6, 7, 8):
        b.set(x, 1, hi, 'minecraft:spruce_fence_gate', facing='south', open=False, in_wall=False, powered=False)
        b.set(x, 4, hi, 'minecraft:stripped_spruce_log', axis='x')
        b.set(x, 5, hi, 'minecraft:spruce_planks' if x != 7 else 'minecraft:spruce_slab')
    b.set(7, 5, hi, 'minecraft:spruce_planks')
    b.set(7, 6, hi, 'minecraft:skeleton_skull', rotation=8)
    b.set(6, 6, hi, 'minecraft:spruce_fence', **BANDIT_NO)
    b.set(8, 6, hi, 'minecraft:spruce_fence', **BANDIT_NO)
    b.set(6, 7, hi, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(8, 7, hi, 'minecraft:lantern', hanging=False, waterlogged=False)
    alarm(b, 7, 12)

    # firing steps by the gate: 2-high plank blocks, a stair up; archers see over the low stakes
    for x0, step, face in ((2, 3, 'west'), (11, 11, 'east')):
        fill(b, x0, 1, 11, x0 + 1, 2, 12, 'minecraft:spruce_planks')
        for x in (x0, x0 + 1):
            b.set(x, 2, 11, 'minecraft:spruce_slab', type='top', waterlogged=False)
        b.set(step, 1, 10, 'minecraft:spruce_stairs', **stairs('south'))

    # the chief's war tent: red canvas, ridge along z, open to the standard
    for z in (2, 3, 4):
        for x in (5, 9):
            b.set(x, 1, z, 'minecraft:red_wool')
        for x in (6, 8):
            b.set(x, 2, z, 'minecraft:red_wool')
        b.set(7, 3, z, 'minecraft:white_wool' if z == 3 else 'minecraft:red_wool')
    for y in (4, 5, 6):
        b.set(7, y, 3, 'minecraft:spruce_fence', **BANDIT_NO)
    b.set(7, 7, 3, 'minecraft:red_banner', rotation=0)
    b.set(6, 1, 2, 'minecraft:red_bed', facing='north', part='head', occupied=False)
    b.set(6, 1, 3, 'minecraft:red_bed', facing='north', part='foot', occupied=False)
    b.set(8, 1, 2, 'minecraft:cartography_table')   # the raid plans
    b.set(8, 1, 3, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(7, 1, 2, 'minecraft:red_carpet')
    b.set(7, 2, 2, 'minecraft:lantern', hanging=True, waterlogged=False)
    standard(b, 7, 1, 5)
    for x in (6, 8):  # lantern posts either side of the approach
        b.set(x, 1, 6, 'minecraft:spruce_fence', **BANDIT_NO)
        b.set(x, 2, 6, 'minecraft:lantern', hanging=False, waterlogged=False)

    # lean-tos: canvas sloping from the wall down onto fence poles
    for xw, xm, cloth, patch in ((2, 3, 'minecraft:brown_wool', 'minecraft:white_wool'),
                                 (12, 11, 'minecraft:gray_wool', 'minecraft:black_wool')):
        for z in range(2, 7):
            b.set(xw, 3, z, patch if z == 3 else cloth)
            b.set(xm, 2, z, patch if z == 5 else cloth)
        for z in (2, 4, 6):
            b.set(xm, 1, z, 'minecraft:spruce_fence', **BANDIT_NO)
    for z in (3, 5):  # west: bedrolls
        b.set(3, 1, z, 'minecraft:brown_carpet')
    b.set(2, 1, 2, 'minecraft:barrel', facing='up', open=False)
    b.set(2, 1, 6, 'minecraft:lantern', hanging=False, waterlogged=False)
    for z, y in ((2, 1), (2, 2), (3, 1)):  # east: the stolen goods
        b.set(12, y, z, 'minecraft:barrel', facing='up', open=False)
    b.set(12, 1, 5, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(12, 1, 6, 'minecraft:hay_block', axis='y')
    b.set(11, 1, 3, 'minecraft:light_gray_carpet')
    b.set(11, 1, 5, 'minecraft:yellow_carpet')

    # cookfire in a ring of stones, log bench, a stolen cake half eaten
    b.set(4, 1, 8, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    for dx, dz in ((0, -1), (1, 0), (0, 1)):
        b.set(4 + dx, 1, 8 + dz, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
    for x in (4, 5):
        b.set(x, 1, 10, 'minecraft:spruce_log', axis='x')
    b.set(5, 2, 10, 'minecraft:cake', bites=3)
    b.set(4, 1, 6, 'minecraft:cauldron')
    for x, z in ((3, 7), (5, 7), (3, 9), (5, 9)):
        b.set(x, 0, z, 'minecraft:gravel')

    # a loot cart left in the yard: cover for whoever comes in by the gate
    for x in (9, 10):
        b.set(x, 1, 10, 'minecraft:spruce_planks')
        b.set(x, 2, 10, 'minecraft:barrel', facing='up', open=False) if x == 10 else b.set(x, 2, 10, 'minecraft:hay_block', axis='x')
    for x in (9, 10):  # wheels
        b.set(x, 1, 11, 'minecraft:spruce_trapdoor', facing='south', half='bottom', open=True, powered=False, waterlogged=False)
    b.set(8, 1, 10, 'minecraft:spruce_fence', **BANDIT_NO)  # the shaft
    # east yard: a hay butt with a target, a chopping stump
    b.set(12, 1, 8, 'minecraft:hay_block', axis='y')
    b.set(12, 1, 9, 'minecraft:hay_block', axis='y')
    b.set(12, 2, 8, 'minecraft:target')
    b.set(10, 1, 7, 'minecraft:oak_log', axis='y')
    b.set(11, 1, 7, 'minecraft:oak_log', axis='x')
    for x, z, plant in ((2, 10, 'minecraft:fern'), (12, 10, 'minecraft:short_grass'), (10, 4, 'minecraft:fern')):
        b.set(x, 0, z, 'minecraft:podzol')
        b.set(x, 1, z, plant)
    b.save('bandit_camp.nbt')


def battlements(b, lo, hi, y, block):
    for x, z in ring(lo, hi):
        if (x + z) % 2 == 0:
            b.set(x, y, z, block)


def guard_tower(b, x0, z0, top, block, slab):
    """3x3 tower, solid up to {@code top}; the archer stands on the middle block. The rim is bottom slabs, not
    walls: a wall at the archer's feet reaches almost to its eyes and hides everything below the tower. A guard
    knocked off is put back by SoldierEntity.returnToPost."""
    fill(b, x0, 1, z0, x0 + 2, top, z0 + 2, block)
    for x, z in rect_ring(x0, z0, x0 + 2, z0 + 2):
        b.set(x, top + 1, z, slab, type='bottom', waterlogged=False)
    b.set(x0 + 1, top + 1, z0 + 1, 'minecraft:air')
    b.set(x0 + 1, top + 2, z0 + 1, 'minecraft:air')


# ---- FORT kind helpers (fort here, fort_barbican and fort_twin in camps_feudal.py) ----
# Stone curtain walls with a cobble plinth, buttresses and a corbelled cornice; hollow towers with slits, a ladder and
# a crenellated or roofed top; an arched gatehouse; a stone-and-timber hall with a gabled roof; yard props.

FORT_STONE = ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks')
FORT_FOOT = ('minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:mossy_stone_bricks')
FORT_NO4 = dict(north=False, south=False, east=False, west=False, waterlogged=False)
FORT_POST = dict(up=True, north='none', south='none', east='none', west='none', waterlogged=False)
FORT_OUT = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
FORT_BACK = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def fort_inside(b, x, z):
    return 0 <= x < b.size[0] and 0 <= z < b.size[2]


def fort_mix(x, z, *blocks):
    """An even mix of ground blocks (varied() keeps mostly the first one)."""
    return blocks[((x * 7349 + z * 5821) ^ (x * z * 131)) % 97 % len(blocks)]


def fort_ground(b, lo, hi, yard=('minecraft:coarse_dirt', 'minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:dirt_path',
                                  'minecraft:packed_mud')):
    """Trodden yard inside the walls lo..hi; outside, a grassy verge with ferns and grass tufts at the wall's feet."""
    n = b.size[0]
    for x in range(n):
        for z in range(b.size[2]):
            if lo < x < hi and lo < z < hi:
                b.set(x, 0, z, fort_mix(x, z, *yard))
                continue
            soil = fort_mix(x, z, 'minecraft:grass_block', 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block',
                            'minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:gravel')
            b.set(x, 0, z, soil)
            if soil in ('minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block') and (x * 5 + z * 3) % 3 == 0:
                b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')


def fort_wall(b, x0, z0, x1, z1, top, out, buttress=5):
    """A straight curtain wall from (x0, z0) to (x1, z1): cobble plinth, stone body to {@code top}, merlons above,
    and on the {@code out} side a corbelled cornice under the merlons broken every {@code buttress} cells by a
    stepped buttress, so no face runs flat."""
    dx, dz = FORT_OUT[out]
    cells = [(x, z) for x in range(min(x0, x1), max(x0, x1) + 1) for z in range(min(z0, z1), max(z0, z1) + 1)]
    for i, (x, z) in enumerate(cells):
        b.set(x, 1, z, varied(x, 1, z, *FORT_FOOT))
        for y in range(2, top + 1):
            b.set(x, y, z, varied(x, y, z, *FORT_STONE))
        b.set(x, top + 1, z, 'minecraft:stone_bricks' if (x + z) % 2 == 0 else 'minecraft:air')
        ox, oz = x + dx, z + dz
        if not fort_inside(b, ox, oz):
            continue
        if buttress and i % buttress == buttress // 2:
            for y in range(1, top - 1):
                b.set(ox, y, oz, varied(ox, y, oz, *FORT_FOOT) if y < 3 else varied(ox, y, oz, *FORT_STONE))
            b.set(ox, top - 1, oz, 'minecraft:stone_brick_stairs', **stairs(FORT_BACK[out]))
        else:
            b.set(ox, top, oz, 'minecraft:stone_brick_stairs', **stairs(FORT_BACK[out], 'top'))


def fort_roof(b, x0, z0, x1, z1, y, block='minecraft:dark_oak', peak=None):
    """Hip roof over the rectangle x0..x1, z0..z1 (cells outside the box are left off), stepping in one per layer;
    {@code peak} goes on the top block (a flag)."""
    if ':' not in block:
        block = 'minecraft:' + block
    d = 0
    while x0 + d <= x1 - d and z0 + d <= z1 - d:
        a0, a1, c0, c1 = x0 + d, x1 - d, z0 + d, z1 - d
        for x, z in rect_ring(a0, c0, a1, c1):
            if not fort_inside(b, x, z):
                continue
            if a0 == a1 or c0 == c1 or (x in (a0, a1) and z in (c0, c1)):
                b.set(x, y + d, z, block + '_planks')
            else:
                facing = 'south' if z == c0 else 'north' if z == c1 else 'east' if x == a0 else 'west'
                b.set(x, y + d, z, block + '_stairs', **stairs(facing))
        d += 1
    if peak:
        b.set((x0 + x1) // 2, y + d, (z0 + z1) // 2, peak, rotation=0)


def fort_tower(b, x0, z0, top, size=5, door=None, ladder=None, roof=None, flag=None):
    """Hollow stone tower with slit windows, a plank floor at {@code top} and merlons, or, with {@code roof} (a wood
    name), four posts carrying a hip roof over the fighting top. {@code door} is a wall cell opened two high,
    {@code ladder} (x, z, facing) climbs to the top. Returns the guard post in the middle of the top."""
    x1, z1 = x0 + size - 1, z0 + size - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(1, top):
                if edge:
                    b.set(x, y, z, varied(x, y, z, *FORT_FOOT) if y == 1 else
                          'minecraft:stone_bricks' if corner else varied(x, y, z, *FORT_STONE))
                else:
                    b.set(x, y, z, 'minecraft:air')
            b.set(x, top, z, ('minecraft:chiseled_stone_bricks' if corner else 'minecraft:stone_bricks') if edge
                  else 'minecraft:spruce_planks')
            merlon = edge and ((x - x0) + (z - z0)) % 2 == 0
            b.set(x, top + 1, z, 'minecraft:stone_bricks' if merlon else 'minecraft:air')
            b.set(x, top + 2, z, 'minecraft:air')
    mx, mz = x0 + size // 2, z0 + size // 2
    for x, z, ns in ((mx, z0, False), (mx, z1, False), (x0, mz, True), (x1, mz, True)):
        for y in (top - 3, top - 2):
            b.set(x, y, z, 'minecraft:iron_bars', **pane(ns))
    if door:
        dx_, dz_ = door
        for y in (1, 2):
            b.set(dx_, y, dz_, 'minecraft:air')
    if ladder:
        lx, lz, facing = ladder
        for y in range(1, top + 1):
            b.set(lx, y, lz, 'minecraft:ladder', facing=facing, waterlogged=False)
    if roof:
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in (top + 1, top + 2):
                b.set(x, y, z, 'minecraft:stone_brick_wall', **FORT_POST)
        fort_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 3, roof, flag)
    elif flag:
        b.set(x0, top + 2, z0, flag, rotation=0)
    return mx, top + 1, mz


def fort_gatehouse(b, xc, z0, z1, top, banner='minecraft:red_wall_banner'):
    """A 7-wide stone gatehouse (xc-3..xc+3, z0..z1; z1 is the outer face) pierced by a 3-wide arched passage, with
    merlons, two turrets proud of the outer face and banners between them. The gate leaves are fence gates.
    Returns the post on its roof."""
    xa, xb = xc - 3, xc + 3
    for x in range(xa, xb + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 1, z, varied(x, 1, z, *FORT_FOOT))
            for y in range(2, top):
                b.set(x, y, z, varied(x, y, z, *FORT_STONE))
            b.set(x, top, z, 'minecraft:stone_bricks')
            edge = x in (xa, xb) or z in (z0, z1)
            b.set(x, top + 1, z, 'minecraft:stone_bricks' if edge and (x + z) % 2 == 0 else 'minecraft:air')
            b.set(x, top + 2, z, 'minecraft:air')
    for z in range(z0, z1 + 1):
        for x in (xc - 1, xc, xc + 1):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:cracked_stone_bricks'))
            b.set(x, 1, z, 'minecraft:air')
            b.set(x, 2, z, 'minecraft:air')
        b.set(xc - 1, 3, z, 'minecraft:stone_brick_stairs', **stairs('west', 'top'))
        b.set(xc, 3, z, 'minecraft:air')
        b.set(xc + 1, 3, z, 'minecraft:stone_brick_stairs', **stairs('east', 'top'))
        b.set(xc, 4, z, 'minecraft:chiseled_stone_bricks' if z in (z0, z1) else 'minecraft:stone_bricks')
    for x in (xc - 1, xc, xc + 1):
        for y in (1, 2):
            b.set(x, y, z1, 'minecraft:dark_oak_fence_gate', facing='south', open=False, in_wall=False, powered=False)
    b.set(xc, 3, z1, 'minecraft:iron_bars', **pane(False))  # the portcullis' teeth, raised
    for x in (xa, xb):  # turrets
        tz = z1 + 1
        if fort_inside(b, x, tz):
            for y in range(1, top + 2):
                b.set(x, y, tz, varied(x, y, tz, *FORT_FOOT) if y == 1 else 'minecraft:stone_bricks')
            b.set(x, top + 2, tz, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
            for y in (top + 1, top + 2):
                b.set(x, y, z1, 'minecraft:stone_bricks' if y == top + 1 else 'minecraft:stone_brick_wall', **({} if y == top + 1 else FORT_POST))
    for x in (xc - 2, xc + 2):
        if fort_inside(b, x, z1 + 1):
            b.set(x, top - 1, z1 + 1, banner, facing='south')
    return xc, top + 1, (z0 + z1) // 2


def fort_hall(b, x0, z0, x1, z1, eave, stone_to=3, floor_y=None, wood='spruce', roof='dark_oak'):
    """A hall with its ridge along x: cobble plinth, stone walls to {@code stone_to}, timber-framed planks above,
    a stripped-log band at {@code eave}, a gabled roof one block proud all round (clipped to the box), gable ends
    filled with planks. {@code floor_y} adds an upper floor. The inside is cleared; doors and furniture are the
    caller's."""
    rows = list(range(z0 - 1, z1 + 2))
    mid = (len(rows) - 1) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            frame = corner or (z in (z0, z1) and (x - x0) % 4 == 0) or (x in (x0, x1) and (z - z0) % 4 == 0)
            b.set(x, 0, z, varied(x, 0, z, f'minecraft:{wood}_planks', 'minecraft:dark_oak_planks') if not edge else
                  'minecraft:cobblestone')
            for y in range(1, eave + 8):
                b.set(x, y, z, 'minecraft:air')
            if not edge:
                if floor_y:
                    b.set(x, floor_y, z, f'minecraft:{wood}_planks')
                continue
            for y in range(1, eave):
                if y == 1:
                    blk = varied(x, y, z, *FORT_FOOT)
                elif y <= stone_to:
                    blk = varied(x, y, z, *FORT_STONE)
                elif frame:
                    blk = f'minecraft:stripped_{wood}_log'
                else:
                    blk = f'minecraft:{wood}_planks'
                b.set(x, y, z, blk, **({'axis': 'y'} if blk.endswith('_log') else {}))
            b.set(x, eave, z, f'minecraft:{wood}_log' if corner else f'minecraft:stripped_{wood}_log',
                  axis='y' if corner else ('x' if z in (z0, z1) else 'z'))
    for r, z in enumerate(rows):
        step = int(min(r, len(rows) - 1 - r))
        y = eave + step
        for x in range(x0 - 1, x1 + 2):
            if not fort_inside(b, x, z):
                continue
            if r == mid:
                b.set(x, y, z, f'minecraft:{roof}_slab', type='bottom', waterlogged=False)
            else:
                b.set(x, y, z, f'minecraft:{roof}_stairs', **stairs('south' if r < mid else 'north'))
        if z0 <= z <= z1:
            for x in (x0, x1):
                for yy in range(eave + 1, y):
                    b.set(x, yy, z, f'minecraft:{wood}_planks' if z != (z0 + z1) // 2 else f'minecraft:stripped_{wood}_log',
                          **({'axis': 'y'} if z == (z0 + z1) // 2 else {}))


def fort_door(b, x, z, facing='north', wood='spruce'):
    b.set(x, 1, z, f'minecraft:{wood}_door', facing=facing, half='lower', hinge='left', open=False, powered=False)
    b.set(x, 2, z, f'minecraft:{wood}_door', facing=facing, half='upper', hinge='left', open=False, powered=False)


def fort_bed(b, x, y, z, facing, color='red'):
    """A bed whose foot is at (x, y, z) and head one step toward {@code facing}."""
    dx, dz = FORT_OUT[facing]
    b.set(x, y, z, f'minecraft:{color}_bed', facing=facing, part='foot', occupied=False)
    b.set(x + dx, y, z + dz, f'minecraft:{color}_bed', facing=facing, part='head', occupied=False)


def fort_well(b, x, z):
    """A stone well: water in a ring of mossy stone, two posts, a beam with a chain, a little roof."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                b.set(x + dx, 1, z + dz, varied(x + dx, 1, z + dz, 'minecraft:mossy_stone_bricks', 'minecraft:cobblestone'))
    b.set(x, 0, z, 'minecraft:water', level=0)
    b.set(x, 1, z, 'minecraft:air')
    for dx in (-1, 1):
        for y in (2, 3):
            b.set(x + dx, y, z, 'minecraft:spruce_fence', **FORT_NO4)
        b.set(x + dx, 4, z, 'minecraft:spruce_log', axis='x')
    b.set(x, 4, z, 'minecraft:spruce_log', axis='x')
    b.set(x, 3, z, 'minecraft:iron_chain', axis='y', waterlogged=False)
    for dx in (-1, 0, 1):
        b.set(x + dx, 4, z - 1, 'minecraft:spruce_stairs', **stairs('south'))
        b.set(x + dx, 4, z + 1, 'minecraft:spruce_stairs', **stairs('north'))
        b.set(x + dx, 5, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)


def fort_lean_to(b, xw, z0, z1, side, wood='spruce'):
    """A shed roof against a wall at x = {@code xw}: it slopes away from the wall on {@code side} ('east' when the
    shed is east of the wall) over four cells, carried by log posts on its open edge. Returns the post x."""
    s = 1 if side == 'east' else -1
    back = 'west' if side == 'east' else 'east'
    profile = ((1, 5, 'stairs'), (2, 5, 'slab'), (3, 4, 'stairs'), (4, 4, 'slab'), (5, 3, 'stairs'))
    for z in range(z0, z1 + 1):
        for d, y, kind in profile:
            if kind == 'stairs':
                b.set(xw + s * d, y, z, f'minecraft:{wood}_stairs', **stairs(back))
            else:
                b.set(xw + s * d, y, z, f'minecraft:{wood}_slab', type='bottom', waterlogged=False)
    px = xw + 4 * s
    for z in sorted({z0, z1, (z0 + z1) // 2}):
        for y in (1, 2, 3):
            b.set(px, y, z, f'minecraft:{wood}_log', axis='y')
    return px


def fort_dummy(b, x, z, facing='south'):
    """A straw training dummy: hay body, pumpkin head, fence arms."""
    b.set(x, 1, z, 'minecraft:spruce_fence', **FORT_NO4)
    b.set(x, 2, z, 'minecraft:hay_block', axis='y')
    b.set(x, 3, z, 'minecraft:carved_pumpkin', facing=facing)


def fort():
    """21x21 raider fort. Buttressed stone curtain, an arched gatehouse with a tripwire volley in its passage, four
    corner towers (open fighting tops at the front, roofed at the back), and round the command yard a two-storey
    barracks hall built into the north wall, a stable and a forge under lean-tos, a well and training dummies. The
    standard stands in the open in front of the hall at the end of a red carpet. Core (10, 1, 6). Height 16."""
    n, lo, hi = 21, 1, 19
    b = Builder(n, 16, n)
    fort_ground(b, lo, hi)
    fort_wall(b, 0, lo, n - 1, lo, 5, 'north')
    fort_wall(b, 0, hi, n - 1, hi, 5, 'south')
    fort_wall(b, lo, 0, lo, n - 1, 5, 'west')
    fort_wall(b, hi, 0, hi, n - 1, 5, 'east')
    front = [fort_tower(b, 0, 16, 7, door=(2, 16), ladder=(1, 18, 'east'), flag='minecraft:red_banner'),
             fort_tower(b, 16, 16, 7, door=(18, 16), ladder=(19, 18, 'west'), flag='minecraft:black_banner')]
    back = [fort_tower(b, 0, 0, 8, door=(2, 4), ladder=(1, 2, 'east'), roof='dark_oak', flag='minecraft:red_banner'),
            fort_tower(b, 16, 0, 8, door=(18, 4), ladder=(19, 2, 'west'), roof='dark_oak', flag='minecraft:black_banner')]
    gate_post = fort_gatehouse(b, 10, 15, hi, 8)

    # the barracks hall: its back wall is the fort's north face; mess room below, bunks above
    fort_hall(b, 6, 0, 14, 4, 7, stone_to=3, floor_y=4)
    fort_door(b, 10, 4)
    for x in (9, 10, 11):
        b.set(x, 3, 5, 'minecraft:dark_oak_stairs', **stairs('north'))
    for x in (8, 12):
        b.set(x, 2, 4, 'minecraft:iron_bars', **pane(False))
        b.set(x, 5, 4, 'minecraft:glass_pane', **pane(False))
    b.set(10, 5, 4, 'minecraft:glass_pane', **pane(False))
    b.set(6, 5, 2, 'minecraft:glass_pane', **pane(True))
    for x in (7, 13):
        b.set(x, 6, 5, 'minecraft:red_wall_banner', facing='south')
    for y in range(1, 12):  # chimney up the east gable, fire on top
        b.set(14, y, 2, varied(14, y, 2, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
    b.set(14, 12, 2, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    b.set(13, 1, 2, 'minecraft:furnace', facing='west', lit=False)
    for x in (8, 9, 10, 11):  # mess table, benches, a half-eaten cake
        b.set(x, 1, 2, 'minecraft:spruce_slab', type='top', waterlogged=False)
    for x in (8, 9, 11):
        b.set(x, 1, 1, 'minecraft:spruce_stairs', **stairs('north'))
    for x in (8, 11):
        b.set(x, 1, 3, 'minecraft:spruce_stairs', **stairs('south'))
    b.set(9, 2, 2, 'minecraft:cake', bites=4)
    b.set(11, 2, 2, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x, y, z in ((13, 1, 1), (13, 1, 3), (13, 2, 1), (7, 1, 1)):
        b.set(x, y, z, 'minecraft:barrel', facing='up', open=False)
    for y in range(1, 5):
        b.set(7, y, 3, 'minecraft:ladder', facing='east', waterlogged=False)
    fort_bed(b, 8, 5, 1, 'east')
    fort_bed(b, 12, 5, 1, 'west', 'black')
    fort_bed(b, 12, 5, 3, 'west', 'black')
    b.set(10, 5, 1, 'minecraft:barrel', facing='up', open=False)
    b.set(10, 6, 1, 'minecraft:lantern', hanging=False, waterlogged=False)

    # command yard: the standard in the open between lantern posts, a red carpet from the gate
    for x in range(8, 13):
        for z in range(5, 9):
            b.set(x, 0, z, 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:stone_bricks')
    standard(b, 10, 1, 6)
    for x in (8, 12):
        for y in (1, 2):
            b.set(x, y, 7, 'minecraft:dark_oak_fence', **FORT_NO4)
        b.set(x, 3, 7, 'minecraft:lantern', hanging=False, waterlogged=False)
    for z in range(7, 17):
        b.set(10, 1, z, 'minecraft:red_carpet')
    fort_well(b, 4, 7)

    # stable (west) and forge (east) under lean-tos against the side walls
    fort_lean_to(b, lo, 9, 14, 'east')
    for x in (2, 3, 4):
        b.set(x, 1, 11, 'minecraft:spruce_fence', **FORT_NO4)
    for z in (9, 10, 12, 13, 14):
        b.set(2, 0, z, 'minecraft:packed_mud')
    b.set(2, 1, 9, 'minecraft:hay_block', axis='y')
    b.set(2, 2, 9, 'minecraft:hay_block', axis='x')
    b.set(2, 1, 10, 'minecraft:hay_block', axis='y')
    b.set(2, 1, 13, 'minecraft:water_cauldron', level=3)
    b.set(2, 1, 14, 'minecraft:hay_block', axis='y')
    b.set(3, 1, 14, 'minecraft:composter', level=4)
    fort_lean_to(b, hi, 9, 14, 'west')
    b.set(17, 1, 10, 'minecraft:anvil', facing='north')
    b.set(18, 1, 12, 'minecraft:blast_furnace', facing='west', lit=False)
    b.set(18, 1, 13, 'minecraft:smithing_table')
    b.set(17, 1, 13, 'minecraft:grindstone', face='floor', facing='north')
    for x, y, z in ((18, 1, 9), (18, 2, 9), (18, 1, 10), (18, 1, 14)):
        b.set(x, y, z, 'minecraft:barrel', facing='up', open=False)
    for z in range(9, 15):
        b.set(17, 0, z, 'minecraft:cobblestone')
        b.set(18, 0, z, 'minecraft:cobblestone')

    # training ground by the east tower, cookfires with stone benches in the court
    fort_dummy(b, 15, 6)
    fort_dummy(b, 17, 7)
    b.set(15, 1, 8, 'minecraft:target')
    for x in (8, 12):
        b.set(x, 1, 11, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
        b.set(x, 1, 12, 'minecraft:stone_brick_stairs', **stairs('south'))
    b.set(13, 1, 11, 'minecraft:cauldron')

    # the gate: arrows from a slit in the passage wall down a tripwire, an alarm plate, caltrops at the inner mouth
    arrow_slit(b, 8, 17, 3)
    alarm(b, 10, 18)
    spikes(b, (9, 14), (11, 14))
    for x, z in ((0, 13), (20, 7), (6, 20), (14, 20)):  # bushes at the wall's feet
        b.set(x, 1, z, 'minecraft:oak_leaves', distance=1, persistent=True, waterlogged=False)
    fort_strip_air(b)
    b.save('fort.nbt')


def fort_strip_air(b):
    """BlueprintPlacer clears the box before placing, so air above the floor needn't be stored."""
    for pos in [p for p, (name, _) in b.blocks.items() if name == 'minecraft:air' and p[1] >= 1]:
        del b.blocks[pos]


def keep():
    """25x25 castle: high deepslate walls with battlements, four corner towers with archer posts, a reinforced gate
    only a Battering Ram breaks, and a central keep holding the standard. Core (12, 1, 9). Height 16."""
    n, lo, hi = 25, 1, 23
    b = Builder(n, 16, n)
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:cobbled_deepslate', 'minecraft:gravel', 'minecraft:coarse_dirt'))
    fill(b, 0, 1, 0, n - 1, 15, n - 1, 'minecraft:air')
    for x, z in ring(lo, hi):
        for y in range(1, 8):
            b.set(x, y, z, varied(x, y, z, 'minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:deepslate_tiles'))
    battlements(b, lo, hi, 8, 'minecraft:deepslate_bricks')
    for x in (11, 12, 13):  # reinforced gate, 3 wide and 4 high
        for y in range(1, 5):
            b.set(x, y, hi, 'warbands:reinforced_gate')
    for x0, z0 in ((1, 1), (21, 1), (1, 21), (21, 21)):
        guard_tower(b, x0, z0, 9, 'minecraft:polished_blackstone_bricks', 'minecraft:polished_blackstone_brick_slab')
    # the central keep: 9x9, door on the south, flat roof with battlements
    klo, khi = 8, 16
    for x in range(klo, khi + 1):
        for z in range(6, 15):
            edge = x in (klo, khi) or z in (6, 14)
            for y in range(1, 9):
                if edge:
                    b.set(x, y, z, varied(x, y, z, 'minecraft:polished_blackstone_bricks', 'minecraft:cracked_polished_blackstone_bricks'))
            b.set(x, 9, z, 'minecraft:polished_blackstone')
    for x in range(klo, khi + 1):
        for z in range(6, 15):
            if (x in (klo, khi) or z in (6, 14)) and (x + z) % 2 == 0:
                b.set(x, 10, z, 'minecraft:polished_blackstone_bricks')
    # 3-wide, 3-high doorway lined with lanterns, and a red carpet from the gate to the standard: easy to find
    for x in (11, 12, 13):
        for y in (1, 2, 3):
            b.set(x, y, 14, 'minecraft:air')
    b.set(10, 3, 15, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(14, 3, 15, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x in (10, 14):
        for y in (1, 2):
            b.set(x, y, 15, 'minecraft:polished_blackstone_brick_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False)
    for z in range(10, 23):
        b.set(12, 1, z, 'minecraft:red_carpet')
    for z in (17, 20):
        for x in (10, 14):
            b.set(x, 1, z, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x in (10, 14):
        b.set(x, 5, 6, 'minecraft:iron_bars', north=False, south=False, east=True, west=True, waterlogged=False)
        b.set(x, 5, 14, 'minecraft:iron_bars', north=False, south=False, east=True, west=True, waterlogged=False)
    standard(b, 12, 1, 9)
    for x, z in ((10, 8), (14, 8), (10, 12), (14, 12)):
        b.set(x, 4, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
        for y in (1, 2, 3):
            b.set(x, y, z, 'minecraft:polished_blackstone_brick_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False)
    # courtyard: braziers and hay for the horses
    for x in (6, 18):
        b.set(x, 1, 18, 'minecraft:magma_block')
        b.set(x, 2, 18, 'minecraft:campfire', lit=True, signal_fire=False, facing='south', waterlogged=False)
    for z in (4, 5):
        b.set(4, 1, z, 'minecraft:hay_block', axis='y')
        b.set(20, 1, z, 'minecraft:hay_block', axis='y')
    # gate approach: an alarm under the gate, caltrops, then a tripwire — arrows on the left, powder on the right
    alarm(b, 12, 22)
    spikes(b, (10, 21), (12, 21), (14, 21))
    arrow_slit(b, 9, 20, 5, 'minecraft:polished_blackstone_bricks')
    b.set(14, 1, 19, 'warbands:powder_keg')
    b.save('keep.nbt')


def poachers_lodge():
    """19x19 hunters' camp in the woods (built on a 15x15 plan, then the yard is opened up). A palisade of uneven logs with a timber gate arch; inside, a spruce cabin
    (cobble footing, plank walls in a log frame, gabled roof with overhang, porch, chimney with a lit fire), a
    skinning rack, a cookfire with log seats, a woodpile, a hay-bale range and a small pen. The camp standard is a
    trophy stake in the yard, in front of the porch. Core (9, 1, 9). Height 9."""
    n, lo, hi = 15, 1, 13
    b = Builder(n, 9, n)
    NO = dict(north=False, south=False, east=False, west=False, waterlogged=False)

    def fence(x, y, z, name='minecraft:oak_fence'):
        b.set(x, y, z, name, **NO)

    # ground: mixed forest floor, a trodden path from the gate to the porch, grassy edge outside the palisade
    camp_floor(b, n)
    fill(b, 0, 1, 0, n - 1, 8, n - 1, 'minecraft:air')
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'))
                if (x * 5 + z * 3) % 4 == 0:
                    b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')
    for z in range(7, 14):
        for x in (7,) if z < 12 else (6, 7, 8):
            b.set(x, 0, z, 'minecraft:dirt_path')
        if z > 8:
            b.set(6 if z % 2 else 8, 0, z, 'minecraft:dirt_path')
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14), (0, 7), (14, 6)):  # a bush or two against the outer wall
        b.set(x, 1, z, 'minecraft:oak_leaves', distance=1, persistent=True, waterlogged=False)

    # palisade: uneven stakes, sharpened with fence tips, corner posts with lanterns
    for x, z in ring(lo, hi):
        if is_corner(x, z, lo, hi):
            for y in range(1, 5):
                b.set(x, y, z, 'minecraft:stripped_oak_log', axis='y')
            b.set(x, 5, z, 'minecraft:lantern', hanging=False, waterlogged=False)
            continue
        # tallest behind the cabin, lowest at the front so the yard shows over it; a taller stripped post every 4
        base = 2 if z == hi else 3
        h = base + (1 if (x * 3 + z * 5) % 4 == 0 else 0) - (1 if (x + z * 2) % 5 == 0 else 0)
        post_cell = (x if z in (lo, hi) else z) % 4 == 1 and z != lo
        if post_cell:
            h = base + 2
        for y in range(1, h + 1):
            b.set(x, y, z, 'minecraft:stripped_spruce_log' if post_cell
                  else 'minecraft:oak_log' if (x * 7 + z + y) % 9 else 'minecraft:spruce_log', axis='y')
        fence(x, h + 1, z)
    # gate: two tall posts, a lintel beam, gates that the archers can shoot over, hanging lanterns
    for x in (6, 7, 8):
        b.set(x, 1, hi, 'minecraft:oak_fence_gate', facing='south', open=False, in_wall=False, powered=False)
        for y in (2, 3, 4):
            b.set(x, y, hi, 'minecraft:air')
        b.set(x, 5, hi, 'minecraft:stripped_spruce_log', axis='x')
    for x in (5, 9):
        for y in range(1, 7):
            b.set(x, y, hi, 'minecraft:stripped_spruce_log', axis='y')
        b.set(x, 7, hi, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x in (6, 8):
        b.set(x, 4, hi, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(7, 4, hi,'minecraft:spruce_trapdoor', facing='south', half='top', open=False, powered=False, waterlogged=False)

    # ---- the cabin: x4..10, z2..6 ----
    x0, x1, z0, z1 = 4, 10, 2, 6
    # interior floor
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            b.set(x, 0, z, 'minecraft:dark_oak_planks' if (x + z) % 2 else 'minecraft:spruce_planks')
    # walls: cobble footing, plank body, log frame, beam band under the eaves
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            corner = is_corner(x, z, x0, x1) if False else (x in (x0, x1) and z in (z0, z1))
            b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone'))
            for y in (2, 3):
                b.set(x, y, z, 'minecraft:spruce_log' if corner else 'minecraft:spruce_planks', **({'axis': 'y'} if corner else {}))
            b.set(x, 4, z, 'minecraft:spruce_log' if corner else 'minecraft:stripped_spruce_log',
                  axis='y' if corner else ('x' if z in (z0, z1) else 'z'))
    # mid posts to break the wall planes
    for x, z in ((7, z0), (x0, 4), (x1, 4)):
        for y in (2, 3):
            b.set(x, y, z, 'minecraft:stripped_spruce_log', axis='y')
    # gable ends (x0 and x1), filled under the roof
    for x in (x0, x1):
        for z in (3, 4, 5):
            b.set(x, 5, z, 'minecraft:spruce_planks')
        b.set(x, 6, 4, 'minecraft:spruce_planks')
    # roof: gable along x, one block overhang all round
    for x in range(x0 - 1, x1 + 2):
        for z, y, face in ((1, 4, 'south'), (2, 5, 'south'), (3, 6, 'south'), (7, 4, 'north'), (6, 5, 'north'), (5, 6, 'north')):
            b.set(x, y, z, 'minecraft:dark_oak_stairs', **stairs(face))
        b.set(x, 7, 4, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
        # underside of the eaves: upside-down stairs give the shadow line
    for x in range(x0 - 1, x1 + 2):
        b.set(x, 4, 1, 'minecraft:dark_oak_stairs', **stairs('south'))
        b.set(x, 4, 7, 'minecraft:dark_oak_stairs', **stairs('north'))
    for x in (x0 - 1, x1 + 1):  # gable barge boards
        for z, y in ((2, 5), (6, 5), (3, 6), (5, 6)):
            b.set(x, y, z, 'minecraft:dark_oak_stairs', **stairs('south' if z < 4 else 'north'))
        b.set(x, 7, 4, 'minecraft:dark_oak_planks')
        fence(x, 8, 4)  # gable horns
    b.set(x0 - 1, 6, 4, 'minecraft:brown_wall_banner', facing='west')  # a bearskin nailed to each gable
    b.set(x1 + 1, 6, 4, 'minecraft:white_wall_banner', facing='east')
    # roof tie-beams inside
    for x in (5, 7, 9):
        for z in (3, 4, 5):
            b.set(x, 4, z, 'minecraft:spruce_log', axis='z')
    # door, windows
    b.set(7, 1, z1, 'minecraft:spruce_door', facing='south', half='lower', hinge='left', open=False, powered=False)
    b.set(7, 2, z1, 'minecraft:spruce_door', facing='south', half='upper', hinge='left', open=False, powered=False)
    b.set(7, 3, z1, 'minecraft:stripped_spruce_log', axis='x')
    for x in (5, 9):
        b.set(x, 2, z1, 'minecraft:glass_pane', **pane(False))
        b.set(x, 3, z1, 'minecraft:spruce_trapdoor', facing='south', half='top', open=True, powered=False, waterlogged=False)
    for z in (3, 5):
        b.set(x0, 2, z, 'minecraft:glass_pane', **pane(True))
        b.set(x1, 2, z, 'minecraft:glass_pane', **pane(True))
    b.set(5, 2, z0, 'minecraft:glass_pane', **pane(False))
    b.set(9, 2, z0, 'minecraft:glass_pane', **pane(False))
    # porch: posts, planks, steps
    for x in range(5, 10):
        b.set(x, 0, 7, 'minecraft:spruce_planks')
    for x in (4, 10):
        for y in (1, 2, 3):
            b.set(x, y, 7, 'minecraft:spruce_log', axis='y')
    b.set(6, 0, 8, 'minecraft:spruce_slab', type='top', waterlogged=False)
    b.set(8, 0, 8, 'minecraft:spruce_slab', type='top', waterlogged=False)
    b.set(5, 1, 7, 'minecraft:barrel', facing='up', open=False)
    b.set(5, 2, 7, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(9, 1, 7, 'minecraft:hay_block', axis='y')
    b.set(9, 2, 7, 'minecraft:brown_carpet')
    # chimney on the back-east corner of the roof, fire on top
    for y in range(1, 8):
        b.set(9, y, 3, 'minecraft:cobblestone' if y % 3 else 'minecraft:mossy_cobblestone')
    b.set(9, 8, 3, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    # inside: bunk, smoker at the hearth, table, hanging lanterns
    b.set(5, 1, 3, 'minecraft:red_bed', facing='north', part='head', occupied=False)
    b.set(5, 1, 4, 'minecraft:red_bed', facing='north', part='foot', occupied=False)
    b.set(8, 1, 3, 'minecraft:smoker', facing='south', lit=False)
    b.set(9, 1, 4, 'minecraft:barrel', facing='up', open=False)
    b.set(9, 1, 5, 'minecraft:barrel', facing='up', open=False)
    fence(7, 1, 4, 'minecraft:spruce_fence')
    b.set(7, 2, 4, 'minecraft:spruce_pressure_plate', powered=False)
    b.set(7, 3, 4, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(5, 3, 5, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(5, 1, 5, 'minecraft:spruce_trapdoor', facing='east', half='bottom', open=False, powered=False, waterlogged=False)
    b.set(6, 1, 3, 'minecraft:chest', facing='south', type='single', waterlogged=False)
    b.set(8, 1, 5, 'minecraft:fletching_table')
    for x, z in ((6, 4), (6, 5), (8, 4)):  # a bearskin rug
        b.set(x, 1, z, 'minecraft:brown_carpet')
    b.set(7, 2, 3, 'minecraft:brown_wall_banner', facing='south')  # a pelt on the back wall, inside

    # ---- the yard ----
    standard(b, 7, 1, 9)
    for x, z in ((7, 8), (7, 10), (6, 9), (8, 9)):  # the stake stands in a ring of rocks
        b.set(x, 1, z, 'minecraft:cobblestone_slab' if (x + z) % 3 else 'minecraft:mossy_cobblestone_slab',
              type='bottom', waterlogged=False)
    # skinning rack between the porch and the stake: two posts, a crossbar, stretched hides, scraps below
    for x in (3, 6):
        for y in (1, 2, 3):
            fence(x, y, 8)
    for x in (4, 5):
        fence(x, 3, 8)
        b.set(x, 2, 8, 'minecraft:brown_wool' if x == 4 else 'minecraft:white_wool')
        b.set(x, 1, 8, 'minecraft:brown_carpet' if x == 4 else 'minecraft:light_gray_carpet')
    b.set(3, 1, 9, 'minecraft:bone_block', axis='y')  # what the hides came off
    # cookfire: stone seats and a chain spit, nothing wooden near the flames; supper left on a stump beside it
    b.set(11, 1, 9, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    for x in (10, 12):
        for y in (1, 2):
            b.set(x, y, 9, 'minecraft:cobblestone_wall', **post())
    b.set(11, 2, 9, 'minecraft:iron_chain', axis='x', waterlogged=False)
    b.set(11, 1, 8, 'minecraft:mossy_cobblestone_stairs', **stairs('north'))
    b.set(11, 1, 10, 'minecraft:cobblestone_stairs', **stairs('south'))
    for x, z in ((10, 8), (12, 10), (10, 10), (12, 8)):
        b.set(x, 0, z, 'minecraft:gravel' if (x + z) % 4 else 'minecraft:coarse_dirt')
    b.set(12, 1, 6, 'minecraft:stripped_spruce_log', axis='y')
    b.set(12, 2, 6, 'minecraft:cake', bites=3)
    b.set(3, 1, 12, 'minecraft:cauldron')
    # woodpile and chopping stump, NW corner
    for z in (2, 3, 4):
        b.set(2, 1, z, 'minecraft:oak_log', axis='x')
        b.set(3, 1, z, 'minecraft:oak_log', axis='x') if z != 4 else None
    b.set(2, 2, 2, 'minecraft:oak_log', axis='x')
    b.set(2, 2, 3, 'minecraft:oak_log', axis='x')
    b.set(2, 3, 2, 'minecraft:stripped_oak_log', axis='x')
    b.set(2, 1, 6, 'minecraft:stripped_oak_log', axis='y')
    b.set(2, 2, 6, 'minecraft:spruce_trapdoor', facing='east', half='bottom', open=False, powered=False, waterlogged=False)
    # archery butts, NE: hay backstop, targets, a practice post
    for z in (2, 3):
        b.set(12, 1, z, 'minecraft:hay_block', axis='y')
    b.set(12, 2, 2, 'minecraft:hay_block', axis='x')
    b.set(12, 2, 3, 'minecraft:target')
    fence(12, 1, 4)
    b.set(12, 2, 4, 'minecraft:target')
    # high seat in the SW corner: a hunter's stand on one leg and the palisade, archer on top
    for x, z in ((2, 11), (3, 11), (2, 12), (3, 12)):
        b.set(x, 4, z, 'minecraft:spruce_planks')
        b.set(x, 7, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for y in (1, 2, 3):
        b.set(3, y, 11, 'minecraft:spruce_log', axis='y')
    for x, z in ((2, 11), (3, 12)):
        fence(x, 5, z)
        fence(x, 6, z)
    for y in (1, 2, 3, 4):
        b.set(4, y, 11, 'minecraft:ladder', facing='east', waterlogged=False)
    # the pen, SE: hay and a feed barrel
    b.set(12, 1, 12, 'minecraft:hay_block', axis='y')
    b.set(11, 1, 12, 'minecraft:barrel', facing='up', open=False)
    # pelts drying on the inner face of the palisade
    for x, y, z, f, c in ((12, 3, 5, 'west', 'white'), (2, 3, 9, 'east', 'brown'), (5, 3, 12, 'north', 'light_gray')):
        back = {'west': (1, 0), 'east': (-1, 0), 'north': (0, 1)}[f]
        if b.blocks.get((x + back[0], y, z + back[1]), ('minecraft:air',))[0] != 'minecraft:air':
            b.set(x, y, z, f'minecraft:{c}_wall_banner', facing=f)
    b.set(7, 6, hi, 'minecraft:skeleton_skull', rotation=0)  # a trophy on the gate beam
    # The lodge was crowded: open up the yard. Four rows are added in front of the cabin (copies of row 10: palisade
    # sides and ground) and two columns of forest floor on each side, so it grows from 15 to 19.
    def m(x, z):
        return x + 2, z + 4 if z >= 10 else z

    moved, moved_nbt = {}, {}
    for (x, y, z), state in b.blocks.items():
        moved[(*m(x, z)[:1], y, m(x, z)[1])] = state
        if (x, y, z) in b.nbt:
            moved_nbt[(m(x, z)[0], y, m(x, z)[1])] = b.nbt[(x, y, z)]
    for (x, y, z), state in list(b.blocks.items()):
        if z == 10 and (y == 0 or x in (lo, hi)):
            for k in range(4):
                moved[(x + 2, y, 10 + k)] = state
    b.blocks, b.nbt, b.size = moved, moved_nbt, [19, 9, 19]
    for x in (0, 1, 17, 18):
        for z in range(19):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:moss_block'))
            if (x * 5 + z * 3) % 5 == 0:
                b.set(x, 1, z, 'minecraft:fern' if (x + z) % 3 else 'minecraft:short_grass')
    for x in range(2, 17):  # the four new rows keep the forest floor of the yard
        for z in range(10, 14):
            if (x, 0, z) not in b.blocks:
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:dirt_path'))
    # plants only on soil
    for x, z, p in ((2, 7, 'fern'), (4, 10, 'fern'), (5, 12, 'short_grass'), (12, 7, 'fern'), (9, 12, 'short_grass'),
                    (6, 11, 'short_grass'), (10, 7, 'fern')):
        px, pz = m(x, z)
        b.set(px, 0, pz, 'minecraft:podzol' if p == 'fern' else 'minecraft:coarse_dirt')
        b.set(px, 1, pz, 'minecraft:' + p)
    # traps: the alarm plate inside the gate, a berry pit, and a snare line across the east yard that fires the
    # arrow trap in the palisade (the dispenser face and the string both show)
    alarm(b, *m(7, 12))
    pitfall(b, *m(9, 10), 'sweet_berry_bush')
    _tripwire(b, *m(8, 11), 4, 'minecraft:mossy_cobblestone')
    dx, dz = m(13, 11)
    b.set(dx, 1, dz, 'minecraft:dispenser', facing='west', triggered=False, nbt=dict(ARROWS))
    poachers_link_fences(b)
    b.save('poachers_lodge.nbt')


SMUGGLERS_NO = dict(north=False, south=False, east=False, west=False, waterlogged=False)


def smugglers_house(b, x0, z0, x1, z1, ridge, top=4, footing=('minecraft:cobblestone', 'minecraft:mossy_cobblestone',
                    'minecraft:stone'), body='minecraft:spruce_planks', frame='minecraft:dark_oak_log',
                    band='minecraft:stripped_spruce_log', roof='dark_oak', beams=()):
    """A timber shed: stone footing, plank walls in a log frame (corners plus mid posts), a stripped-log band under
    the eaves, and a gable roof whose ridge runs along {@code ridge} ('x' or 'z'), overhanging by one all round,
    gable ends filled. {@code beams} are tie beams (along-ridge coordinates) at the eave height to hang lanterns."""
    def put(al, ac, y, name, **p):
        if ridge == 'x':
            b.set(al, y, ac, name, **p)
        else:
            b.set(ac, y, al, name, **p)

    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            b.set(x, 0, z, 'minecraft:spruce_planks' if (x + z) % 3 else 'minecraft:dark_oak_planks')
    for x, z in rect_ring(x0, z0, x1, z1):
        along_x = z in (z0, z1)
        k, last = (x - x0, x1 - x0) if along_x else (z - z0, z1 - z0)
        mids = (last // 2,) if 5 <= last <= 6 else (last // 3, last - last // 3) if last >= 7 else ()
        corner = k in (0, last)
        for y in range(1, top + 1):
            if corner:
                b.set(x, y, z, frame, axis='y')
            elif y == top:
                b.set(x, y, z, band, axis='x' if along_x else 'z')
            elif k in mids and y > 1:
                b.set(x, y, z, frame, axis='y')
            elif y == 1:
                b.set(x, y, z, varied(x, y, z, *footing))
            else:
                b.set(x, y, z, body)
    a0, a1, c0, c1 = (x0, x1, z0, z1) if ridge == 'x' else (z0, z1, x0, x1)
    w = c1 - c0 + 1
    peak = (w + 1) // 2
    low, high = ('south', 'north') if ridge == 'x' else ('east', 'west')
    for i in range(-1, w + 1):
        d = min(i + 1, w - i)
        for al in range(a0 - 1, a1 + 2):
            if d == peak:
                put(al, c0 + i, top + d, f'minecraft:{roof}_slab', type='bottom', waterlogged=False)
            else:
                put(al, c0 + i, top + d, f'minecraft:{roof}_stairs', **stairs(low if i < w / 2 - 0.5 else high))
        if 0 <= i < w:
            for al in (a0, a1):
                for y in range(top + 1, top + d):
                    put(al, c0 + i, y, body)
    for al in beams:
        for ac in range(c0 + 1, c1):
            put(al, ac, top, band, axis='z' if ridge == 'x' else 'x')


def smugglers_window(b, x, z, facing):
    """A pane with an open shutter over it; {@code facing} is the way the wall looks out."""
    b.set(x, 2, z, 'minecraft:glass_pane', **pane(facing in ('east', 'west')))
    b.set(x, 3, z, 'minecraft:spruce_trapdoor', facing=facing, half='top', open=True, powered=False, waterlogged=False)


def smugglers_door(b, x, z, facing):
    for y, half in ((1, 'lower'), (2, 'upper')):
        b.set(x, y, z, 'minecraft:spruce_door', facing=facing, half=half, hinge='left', open=False, powered=False)


def smugglers_boat(b, a0, a1, c, axis, cargo=True):
    """A moored single-master built from blocks, sitting in water at y0: dark oak bottom, plank gunwale, a pointed
    bow at a1, a stern lantern at a0, a striped sail on a yard and a masthead lantern. Runs along {@code axis}."""
    def put(al, ac, y, name, **p):
        if axis == 'x':
            b.set(al, y, ac, name, **p)
        else:
            b.set(ac, y, al, name, **p)

    bow = 'east' if axis == 'x' else 'south'
    for al in range(a0 + 1, a1):
        for ac in (c - 1, c, c + 1):
            put(al, ac, 0, 'minecraft:dark_oak_planks')
        put(al, c - 1, 1, 'minecraft:spruce_planks')
        put(al, c + 1, 1, 'minecraft:spruce_planks')
        put(al, c, 1, 'minecraft:air')
    put(a1, c, 0, 'minecraft:dark_oak_planks')
    put(a1, c, 1, 'minecraft:dark_oak_stairs', **stairs(bow))
    put(a0, c, 0, 'minecraft:dark_oak_planks')
    put(a0, c, 1, 'minecraft:spruce_planks')
    put(a0, c, 2, 'minecraft:spruce_fence', **SMUGGLERS_NO)
    put(a0, c, 3, 'minecraft:lantern', hanging=False, waterlogged=False)
    m = (a0 + a1) // 2
    for y in (1, 2, 3, 8):
        put(m, c, y, 'minecraft:spruce_fence', **SMUGGLERS_NO)
    for y, cloth in ((4, 'white'), (5, 'black'), (6, 'white')):
        for ac in (c - 1, c, c + 1):
            put(m, ac, y, f'minecraft:{cloth}_wool')
    for ac in (c - 1, c, c + 1):
        put(m, ac, 7, 'minecraft:stripped_spruce_log', axis='z' if axis == 'x' else 'x')
    put(m, c, 9, 'minecraft:lantern', hanging=False, waterlogged=False)
    if cargo:
        put(m - 1, c, 1, 'minecraft:barrel', facing='up', open=False)
        put(m + 1, c, 1, 'minecraft:composter', level=0)


def poachers_link_fences(b):
    """Poachers: sets each fence's side flags to what it touches (fences, gates across them, full blocks), so rails
    and racks are placed already joined and read as rails in the preview."""
    full = ('_log', '_planks', '_wool', 'cobblestone', 'stone_bricks', 'hay_block', 'barrel', 'target', 'bone_block')
    for (x, y, z), (n, props) in list(b.blocks.items()):
        if not n.endswith('_fence'):
            continue
        sides = {}
        for d, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
            m, mp = b.blocks.get((x + dx, y, z + dz), ('minecraft:air', ()))
            if m.endswith('_fence_gate'):
                sides[d] = (dict(mp).get('facing') in ('north', 'south')) == (d in ('east', 'west'))
            else:
                sides[d] = m.endswith('_fence') or m.endswith(full)
        b.set(x, y, z, n, waterlogged=False, **sides)


def smugglers_dock():
    """15x15 smugglers' landing on an inlet: a breakwater rim round shallow water, a T-shaped jetty on log piles with
    a cargo stack, a net rack and lantern bollards, a single-master moored alongside, and on the beach a timber shack,
    a striped tarp over the tally table and a cookfire. No wall at all. One jetty plank is rigged: when the alarm
    sounds its piston pulls it and whoever stood on it drops into the water. Core (7, 1, 9). Height 11."""
    n, h = 15, 11
    b = Builder(n, h, n)
    NO = SMUGGLERS_NO

    def fence(x, y, z, **joins):
        b.set(x, y, z, 'minecraft:spruce_fence', **{**NO, **joins})

    def lamp_post(x, z, y=1):
        fence(x, y, z)
        fence(x, y + 1, z)
        b.set(x, y + 2, z, 'minecraft:lantern', hanging=False, waterlogged=False)

    # ground: beach sand turning to grass inland (south), a breakwater rim round the water (north)
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:sand', 'minecraft:gravel', 'minecraft:coarse_dirt'))
            inland = z >= 13 or (x in (0, n - 1) and z >= 9)
            if inland:
                b.set(x, 0, z, ('minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:grass_block',
                                'minecraft:sand')[(x * 5 + z * 3) % 4], snowy=False)
    fill(b, 0, 1, 0, n - 1, h - 1, n - 1, 'minecraft:air')
    for x in range(n):
        for z in range(n):
            if z <= 6 and (z == 0 or x in (0, n - 1)):
                b.set(x, 0, z, ('minecraft:mossy_cobblestone', 'minecraft:cobblestone', 'minecraft:gravel',
                                'minecraft:stone')[(x * 7 + z * 3) % 4])
            elif z <= 6:
                b.set(x, 0, z, 'minecraft:water', level=0)
    for x, z in ((0, 0), (1, 0), (6, 0), (12, 0), (14, 1), (0, 4), (14, 3)):
        b.set(x, 1, z, 'minecraft:mossy_cobblestone' if (x + z) % 2 else 'minecraft:cobblestone')
    b.set(0, 2, 0, 'minecraft:mossy_cobblestone_slab', type='bottom', waterlogged=False)
    for x in (6, 7, 8):  # a trodden path in from the south
        for z in (13, 14):
            b.set(x, 0, z, 'minecraft:dirt_path' if (x + z) % 3 else 'minecraft:gravel')
    for z in range(10, 13):
        b.set(7, 0, z, 'minecraft:dirt_path')
    for x, z in ((1, 13), (3, 14), (11, 14), (14, 12), (0, 11), (13, 14)):
        if b.blocks[(x, 0, z)][0] == 'minecraft:grass_block':
            b.set(x, 1, z, 'minecraft:short_grass' if x % 2 else 'minecraft:fern')
    for x in (1, 4):  # reeds at the water's edge
        b.set(x, 1, 7, 'minecraft:sugar_cane', age=0)
        b.set(x, 2, 7, 'minecraft:sugar_cane', age=0)

    # ---- the jetty: head x3..10 z2..3, stem x9..10 z4..6, deck at y1 on log piles ----
    for x in range(3, 11):
        for z in (2, 3):
            b.set(x, 1, z, 'minecraft:spruce_planks')
    for z in (4, 5, 6):
        for x in (9, 10):
            b.set(x, 1, z, 'minecraft:spruce_planks')
    for x, z in ((3, 2), (3, 3), (6, 3), (10, 2), (9, 4), (9, 6), (10, 6)):
        b.set(x, 0, z, 'minecraft:stripped_spruce_log', axis='y')
    for x in (9, 10):
        b.set(x, 1, 7, 'minecraft:spruce_stairs', **stairs('north'))
    # the rigged plank: x9 is a visible gap, x10 a plank held by a sticky piston; the relay sits on the breakwater
    b.set(9, 1, 5, 'minecraft:air')
    b.set(10, 1, 5, 'warbands:rigged_floor')
    b.set(11, 1, 5, 'minecraft:piston_head', facing='west', type='sticky', short=False)
    b.set(12, 1, 5, 'minecraft:sticky_piston', facing='west', extended=True)
    b.set(13, 1, 5, 'minecraft:redstone_wall_torch', facing='west', lit=True)
    b.set(14, 1, 5, 'warbands:war_relay', powered=False, pulse=False)
    # cargo on the jetty head, a net rack of chains between two posts, bollards with lanterns
    for x, y in ((3, 1), (3, 2), (4, 1)):
        b.set(x, y + 1, 2, 'minecraft:barrel', facing='up' if y == 1 else 'north', open=False)
    b.set(4, 3, 2, 'minecraft:composter', level=0)
    for x in (5, 9):
        for y in (2, 3, 4):
            fence(x, y, 2, **({'east': True} if x == 5 and y == 4 else {'west': True} if y == 4 else {}))
    for x in (6, 7, 8):
        fence(x, 4, 2, east=True, west=True)
        b.set(x, 3, 2, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(7, 2, 2, 'minecraft:iron_chain', axis='y', waterlogged=False)
    for x, z in ((3, 3), (10, 2)):
        b.set(x, 2, z, 'minecraft:stripped_spruce_log', axis='y')
        b.set(x, 3, z, 'minecraft:lantern', hanging=False, waterlogged=False)
    # the boat in the bay, its mooring line to the stem
    smugglers_boat(b, 2, 7, 5, 'x')
    for x in (7, 8):
        b.set(x, 1, 4, 'minecraft:iron_chain', axis='x', waterlogged=False)

    # ---- the beach ----
    smugglers_house(b, 1, 8, 5, 12, 'z', beams=(10,))
    smugglers_door(b, 5, 10, 'east')
    smugglers_window(b, 1, 10, 'west')
    smugglers_window(b, 3, 12, 'south')
    smugglers_window(b, 3, 8, 'north')
    b.set(2, 1, 9, 'minecraft:red_bed', facing='north', part='head', occupied=False)
    b.set(2, 1, 10, 'minecraft:red_bed', facing='north', part='foot', occupied=False)
    b.set(4, 1, 9, 'minecraft:barrel', facing='up', open=False)
    b.set(2, 1, 11, 'minecraft:chest', facing='east', type='single', waterlogged=False)
    fence(3, 1, 11)
    b.set(3, 2, 11, 'minecraft:spruce_pressure_plate', powered=False)
    b.set(3, 3, 10, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(1, 1, 13, 'minecraft:hay_block', axis='y')
    b.set(2, 1, 13, 'minecraft:barrel', facing='up', open=False)

    standard(b, 7, 1, 9)
    # striped tarp on four posts over the tally table
    for x, z in ((10, 9), (13, 9), (10, 12), (13, 12)):
        fence(x, 1, z)
        fence(x, 2, z)
    for x in range(10, 14):
        cloth = 'minecraft:white_wool' if x % 2 else 'minecraft:blue_wool'
        for z in range(9, 13):
            b.set(x, 3, z, cloth)
        for z in (10, 11):
            b.set(x, 4, z, cloth)
    b.set(11, 1, 10, 'minecraft:cartography_table')
    b.set(12, 1, 10, 'minecraft:lectern', facing='west', has_book=False, powered=False)
    b.set(12, 1, 11, 'minecraft:barrel', facing='up', open=False)
    # cookfire on the shore with stone seats, lantern posts either side of the jetty steps
    b.set(13, 1, 7, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    b.set(12, 1, 7, 'minecraft:mossy_cobblestone_slab', type='bottom', waterlogged=False)
    b.set(13, 1, 8, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
    lamp_post(8, 7)
    lamp_post(11, 7)
    lamp_post(5, 14)
    lamp_post(9, 14)
    # a crate stack waiting to be loaded
    for x, z, y in ((12, 13, 1), (13, 13, 1), (13, 13, 2)):
        b.set(x, y, z, 'minecraft:barrel', facing='up' if y == 1 else 'east', open=False)
    b.set(11, 1, 13, 'minecraft:composter', level=0)
    alarm(b, 7, 13)
    b.save('smugglers_dock.nbt')


# ---- quarry helpers: the QUARRY_STRONGHOLD layouts (quarry_stronghold here, quarry_* in camps_feudal.py) ----
# Palette: cobblestone, andesite and tuff carry the stone; spruce and dark oak the timber; ore specks are the accent.

QUARRY_ROCK = ('minecraft:andesite', 'minecraft:stone', 'minecraft:tuff', 'minecraft:andesite', 'minecraft:cobblestone',
               'minecraft:stone')
QUARRY_ORES = ('minecraft:iron_ore', 'minecraft:coal_ore', 'minecraft:copper_ore', 'minecraft:coal_ore')
NO_SIDES = dict(north=False, south=False, east=False, west=False, waterlogged=False)


def quarry_hash(x, y, z):
    return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) >> 3 & 0xFFFF


def quarry_pick(x, y, z, *blocks):
    """An even deterministic mix: varied() keeps the first block 6 times in 7, a cut rock face wants real variety."""
    return blocks[quarry_hash(x, y, z) % len(blocks)]


def quarry_rock(x, y, z, ore=11):
    """A quarried face: stone, andesite, tuff and cobble with an ore speck about one block in {@code ore}."""
    h = quarry_hash(x, y, z)
    if ore and h % ore == 0:
        return QUARRY_ORES[(h >> 4) % len(QUARRY_ORES)]
    return QUARRY_ROCK[(h >> 2) % len(QUARRY_ROCK)]


def quarry_ground(b, n, lo, hi):
    """Stony yard inside the wall ring lo..hi, a ragged grassy and gravel edge outside it."""
    for x in range(n):
        for z in range(n):
            inside = lo <= x <= hi and lo <= z <= hi
            b.set(x, 0, z, quarry_pick(x, 0, z, *(
                ('minecraft:gravel', 'minecraft:stone', 'minecraft:andesite', 'minecraft:gravel', 'minecraft:coarse_dirt',
                 'minecraft:cobblestone', 'minecraft:tuff') if inside else
                ('minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:moss_block',
                 'minecraft:gravel', 'minecraft:podzol', 'minecraft:grass_block'))))


def quarry_path(b, cells):
    for x, z in cells:
        b.set(x, 0, z, quarry_pick(x, 0, z, 'minecraft:dirt_path', 'minecraft:gravel', 'minecraft:dirt_path'))


def quarry_curtain(b, lo, hi, top, gate, buttress=None):
    """Square curtain wall on ring(lo, hi): a mossy footing, a cobble/andesite/tuff body, a stone-brick coping at
    {@code top} and merlons above. Outside, andesite buttresses every few blocks break the long planes (at the
    positions in {@code buttress}, default the quarter points). A gate {@code gate} = (x0, x1) in the south wall:
    fence gates two high under a dark-oak lintel, the wall closed above."""
    for x, z in ring(lo, hi):
        b.set(x, 1, z, quarry_pick(x, 1, z, 'minecraft:mossy_cobblestone', 'minecraft:andesite', 'minecraft:cobblestone'))
        for y in range(2, top):
            b.set(x, y, z, quarry_pick(x, y, z, 'minecraft:cobblestone', 'minecraft:cobblestone', 'minecraft:andesite',
                                       'minecraft:tuff', 'minecraft:mossy_cobblestone', 'minecraft:cobblestone'))
        b.set(x, top, z, quarry_pick(x, top, z, 'minecraft:stone_bricks', 'minecraft:stone_bricks',
                                     'minecraft:cracked_stone_bricks'))
        if (x + z) % 2 == 0:
            b.set(x, top + 1, z, quarry_pick(x, top + 1, z, 'minecraft:cobblestone', 'minecraft:andesite'))
    gx0, gx1 = gate
    for x in range(gx0, gx1 + 1):
        for y in (1, 2):
            b.set(x, y, hi, 'minecraft:spruce_fence_gate', facing='south', open=False, in_wall=False, powered=False)
        b.set(x, 3, hi, 'minecraft:dark_oak_log', axis='x')
    mid = (lo + hi) // 2
    spots = buttress or (lo + 4, mid, hi - 4)
    for p in spots:
        for x, z, face in ((p, lo - 1, 'south'), (p, hi + 1, 'north'), (lo - 1, p, 'east'), (hi + 1, p, 'west')):
            if z == hi + 1 and gx0 - 1 <= x <= gx1 + 1:
                continue
            for y in range(1, top - 1):
                b.set(x, y, z, 'minecraft:polished_andesite' if y == 1 else 'minecraft:andesite')
            b.set(x, top - 1, z, 'minecraft:andesite_stairs', **stairs(face))


def quarry_tower(b, x0, z0, top):
    """3x3 crossbow tower with a flared top: upside-down stair corbels round a 5x5 fighting deck, slab parapet,
    spruce corner posts and a pyramid roof two blocks over the guard's head. Returns the guard spot."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            for y in range(1, top + 1):
                corner = x in (x0, x0 + 2) and z in (z0, z0 + 2)
                b.set(x, y, z, ('minecraft:polished_andesite' if y % 2 else 'minecraft:andesite') if corner else
                      quarry_pick(x, y, z, 'minecraft:cobblestone', 'minecraft:cobblestone', 'minecraft:tuff',
                                  'minecraft:mossy_cobblestone'))
    a, c = x0 - 1, x0 + 3
    d, e = z0 - 1, z0 + 3
    for x, z in rect_ring(a, d, c, e):
        corner = x in (a, c) and z in (d, e)
        if corner:
            b.set(x, top, z, 'minecraft:stone_bricks')
            for y in (top + 1, top + 2):
                b.set(x, y, z, 'minecraft:spruce_log', axis='y')
        else:
            face = 'south' if z == d else 'north' if z == e else 'east' if x == a else 'west'
            b.set(x, top, z, 'minecraft:cobblestone_stairs', **stairs(face, 'top'))
            b.set(x, top + 1, z, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
            b.set(x, top + 2, z, 'minecraft:air')
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            b.set(x, top + 1, z, 'minecraft:air')
            b.set(x, top + 2, z, 'minecraft:air')
    quarry_pyramid(b, a, d, c, e, top + 3, 'minecraft:spruce_stairs', 'minecraft:spruce_planks')
    b.set(x0 + 1, top + 6, z0 + 1, 'minecraft:spruce_fence', **NO_SIDES)
    return x0 + 1, top + 1, z0 + 1


def quarry_pyramid(b, x0, z0, x1, z1, y, stair, corner):
    """Hip roof over any rectangle (hip_roof only does squares on the diagonal): a stair ring per layer, stepping in."""
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        a, c, d, e = x0 + k, x1 - k, z0 + k, z1 - k
        for x, z in rect_ring(a, d, c, e):
            if a == c or d == e:
                b.set(x, y + k, z, stair.replace('_stairs', '_slab'), type='bottom', waterlogged=False)
            elif x in (a, c) and z in (d, e):
                b.set(x, y + k, z, corner)
            else:
                face = 'south' if z == d else 'north' if z == e else 'east' if x == a else 'west'
                b.set(x, y + k, z, stair, **stairs(face))
        k += 1


def quarry_hut(b, x0, z0, x1, z1, along='x', wood='spruce', door=None, windows=()):
    """Timber hut on the rectangle x0..x1, z0..z1: cobble footing (floor ring and first course), plank walls with
    log posts at the corners and every third block, a stripped-log band at y 4 and a gable roof with eaves one out
    on every side (ridge along {@code along}). door = (x, z, facing); windows = [(x, z)] get a pane at y 2 with
    open trapdoor shutters either side outside. Clears the inside; furnish x0+1..x1-1, z0+1..z1-1 afterwards."""
    logs, planks = f'minecraft:{wood}_log', f'minecraft:{wood}_planks'
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            b.set(x, 0, z, 'minecraft:cobblestone' if edge else planks)
            if not edge:
                for y in range(1, 5):
                    b.set(x, y, z, 'minecraft:air')
                continue
            b.set(x, 1, z, quarry_pick(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone',
                                       'minecraft:andesite'))
            post = corner or ((x - x0) % 3 == 0 if z in (z0, z1) else (z - z0) % 3 == 0)
            for y in (2, 3):
                if post:
                    b.set(x, y, z, logs, axis='y')
                else:
                    b.set(x, y, z, planks)
            b.set(x, 4, z, f'minecraft:stripped_{wood}_log', axis='y' if corner else ('x' if z in (z0, z1) else 'z'))
    quarry_gable(b, x0, z0, x1, z1, 4, along, wood)
    for wx, wz in windows:
        ns = wx in (x0, x1)
        b.set(wx, 2, wz, 'minecraft:glass_pane', **pane(ns))
        if ns:
            ox = wx - 1 if wx == x0 else wx + 1
            shutters = ((ox, wz - 1, 'north'), (ox, wz + 1, 'south'))
        else:
            oz = wz - 1 if wz == z0 else wz + 1
            shutters = ((wx - 1, oz, 'west'), (wx + 1, oz, 'east'))
        for sx, sz, facing in shutters:
            b.set(sx, 2, sz, f'minecraft:{wood}_trapdoor', facing=facing, half='bottom', open=True, powered=False,
                  waterlogged=False)
    if door:
        dx, dz, facing = door
        b.set(dx, 1, dz, f'minecraft:{wood}_door', facing=facing, half='lower', hinge='left', open=False, powered=False)
        b.set(dx, 2, dz, f'minecraft:{wood}_door', facing=facing, half='upper', hinge='left', open=False, powered=False)
        b.set(dx, 3, dz, f'minecraft:stripped_{wood}_log', axis='z' if facing in ('east', 'west') else 'x')


def quarry_gable(b, x0, z0, x1, z1, y, along='x', wood='spruce'):
    """Gable roof over walls on x0..x1, z0..z1: eave stairs one out at height y (beside the wall-top band), one
    step up per row, a slab ridge, plank gable ends. {@code along} is the ridge direction."""
    st, sl, planks = f'minecraft:{wood}_stairs', f'minecraft:{wood}_slab', f'minecraft:{wood}_planks'
    if along == 'x':
        put = lambda u, yy, v, *a, **k: b.set(u, yy, v, *a, **k)
        u0, u1, v0, v1, lo_face, hi_face = x0, x1, z0, z1, 'south', 'north'
    else:
        put = lambda u, yy, v, *a, **k: b.set(v, yy, u, *a, **k)
        u0, u1, v0, v1, lo_face, hi_face = z0, z1, x0, x1, 'east', 'west'
    a, c = v0 - 1, v1 + 1
    d = 0
    while a + d <= c - d:
        va, vc, yy = a + d, c - d, y + d
        for u in range(u0 - 1, u1 + 2):
            if va == vc:
                put(u, yy, va, sl, type='bottom', waterlogged=False)
            else:
                put(u, yy, va, st, **stairs(lo_face))
                put(u, yy, vc, st, **stairs(hi_face))
                if vc == va + 1:
                    put(u, yy + 1, va, sl, type='bottom', waterlogged=False)
        if d > 0:
            for u in (u0, u1):
                for v in range(va + 1, vc):
                    put(u, yy, v, planks)
        d += 1


def quarry_heap(b, x, z, y=1, kind='ore'):
    """A pile in a plus shape: a two-high middle, one-high arms, slabs in the corners. 'ore' is broken ore and cobble,
    'spoil' gravel and andesite, 'cut' dressed stone on a spruce pallet."""
    if kind == 'cut':
        for dx in (0, 1):
            for dz in (0, 1):
                b.set(x + dx, y, z + dz, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
                b.set(x + dx, y + 1, z + dz, quarry_pick(x + dx, y, z + dz, 'minecraft:smooth_stone',
                                                         'minecraft:stone_bricks', 'minecraft:polished_andesite'))
        b.set(x, y + 2, z, 'minecraft:smooth_stone_slab', type='bottom', waterlogged=False)
        b.set(x + 1, y + 2, z + 1, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
        return
    mix = (('minecraft:cobblestone', 'minecraft:iron_ore', 'minecraft:coal_ore', 'minecraft:cobblestone',
            'minecraft:copper_ore', 'minecraft:gravel') if kind == 'ore' else
           ('minecraft:gravel', 'minecraft:andesite', 'minecraft:gravel', 'minecraft:cobblestone', 'minecraft:tuff'))
    slabs = ('minecraft:cobblestone_slab', 'minecraft:andesite_slab', 'minecraft:tuff_slab')
    b.set(x, y, z, quarry_pick(x, y, z, *mix))
    b.set(x, y + 1, z, quarry_pick(x, y + 1, z, *mix))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(x + dx, y, z + dz, quarry_pick(x + dx, y, z + dz, *mix))
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        b.set(x + dx, y, z + dz, quarry_pick(x + dx, y, z + dz, *slabs), type='bottom', waterlogged=False)
    b.set(x, y + 2, z, quarry_pick(x, y + 2, z, *slabs), type='bottom', waterlogged=False)


def quarry_rails(b, cells):
    """Rails along a path of (x, y, z) cells (4-connected, climbing at most one block per step); each rail's shape
    is worked out from its neighbours, climbs become ascending rails."""
    dirs = {(0, -1): 'north', (0, 1): 'south', (1, 0): 'east', (-1, 0): 'west'}
    for i, (x, y, z) in enumerate(cells):
        near = [cells[j] for j in (i - 1, i + 1) if 0 <= j < len(cells)]
        up = [dirs[(nx - x, nz - z)] for nx, ny, nz in near if ny == y + 1]
        sides = [dirs[(nx - x, nz - z)] for nx, ny, nz in near]
        if up:
            shape = 'ascending_' + up[0]
        elif len(sides) == 1 or {'north', 'south'} >= set(sides) or {'east', 'west'} >= set(sides):
            shape = 'north_south' if sides[0] in ('north', 'south') else 'east_west'
        else:
            ns = [s for s in sides if s in ('north', 'south')][0]
            ew = [s for s in sides if s in ('east', 'west')][0]
            shape = ns + '_' + ew
        b.set(x, y, z, 'minecraft:rail', shape=shape, waterlogged=False)


def quarry_scaffold(b, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        b.set(x, y, z, 'minecraft:scaffolding', distance=0, bottom=False, waterlogged=False)


def quarry_green(b, n, lo, hi):
    """Last pass: grass and ferns on the soil outside the wall ring, loose rubble slabs against the wall foot."""
    for x in range(n):
        for z in range(n):
            if lo <= x <= hi and lo <= z <= hi:
                continue
            if b.blocks.get((x, 1, z), ('minecraft:air',))[0] != 'minecraft:air':
                continue
            under = b.blocks.get((x, 0, z), ('minecraft:air',))[0]
            h = quarry_hash(x, 1, z) % 9
            near_wall = x in (lo - 1, hi + 1) or z in (lo - 1, hi + 1)
            if near_wall and h in (0, 4) and under not in ('minecraft:dirt_path',):
                b.set(x, 1, z, quarry_pick(x, 1, z, 'minecraft:cobblestone_slab', 'minecraft:mossy_cobblestone_slab',
                                           'minecraft:andesite_slab'), type='bottom', waterlogged=False)
            elif under in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol') and h in (1, 2, 5, 7):
                b.set(x, 1, z, 'minecraft:fern' if h == 5 else 'minecraft:short_grass')


def quarry_stronghold():
    """21x21 walled quarry: a stepped stone face cut into the north-west corner falls to a wet pit floor, where a
    treadwheel crane lifts blocks out and a cart track runs them to the ore heap. A mason's lodge in the north-east,
    a stone-cutting yard round the standard, crossbow towers either side of the gate and a tripwire gun behind it.
    Core (10, 1, 7). Height 14."""
    n, lo, hi = 21, 1, 19
    b = Builder(n, 14, n)
    fill(b, 0, 1, 0, n - 1, 13, n - 1, 'minecraft:air')
    quarry_ground(b, n, lo, hi)
    quarry_path(b, [(x, z) for x in (9, 10, 11) for z in (15, 16, 17, 18, 19, 20)] + [(10, 14), (10, 13)])
    quarry_curtain(b, lo, hi, 5, (9, 11))
    # the quarry face: benches three, two and one high step down from the north and west walls to the pit floor
    for x in range(2, 12):
        for z in range(2, 12):
            d = min(x - 2, z - 2)
            h = 3 if d <= 1 else 2 if d == 2 else 1 if d == 3 else 0
            h = min(h, max(0, 11 - x), max(0, 11 - z))
            if 0 < h and d > 0 and quarry_hash(x, 7, z) % 5 == 0:
                h -= 1  # a ragged bench edge
            for y in range(1, h + 1):
                b.set(x, y, z, quarry_rock(x, y, z) if y < h else
                      quarry_pick(x, y, z, 'minecraft:gravel', 'minecraft:cobblestone', 'minecraft:coarse_dirt',
                                  'minecraft:andesite'))
            if h == 0:
                b.set(x, 0, z, quarry_pick(x, 0, z, 'minecraft:gravel', 'minecraft:stone', 'minecraft:gravel',
                                           'minecraft:iron_ore', 'minecraft:andesite'))
    for x, z in ((6, 10), (7, 10), (6, 11)):  # seep water in the low corner of the pit
        b.set(x, 0, z, 'minecraft:water', level=0)
    # treadwheel crane on the west bench: a two-rim wheel, mast, jib over the pit, a block on the chain
    wheel = [(-2, -1), (-2, 0), (-2, 1), (2, -1), (2, 0), (2, 1), (-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2), (1, 2)]
    for x in (2, 3):
        for dz, dy in wheel:
            b.set(x, 6 + dy, 6 + dz, 'minecraft:spruce_planks')
    for dz, dy, axis in ((0, 1, 'y'), (0, -1, 'y'), (1, 0, 'z'), (-1, 0, 'z')):
        b.set(3, 6 + dy, 6 + dz, 'minecraft:stripped_dark_oak_log', axis=axis)
    b.set(2, 6, 6, 'minecraft:dark_oak_log', axis='x')
    b.set(3, 6, 6, 'minecraft:dark_oak_log', axis='x')
    for y in range(3, 11):
        b.set(4, y, 6, 'minecraft:dark_oak_log', axis='y')
    for x in range(5, 9):
        b.set(x, 10, 6, 'minecraft:dark_oak_log', axis='x')
    b.set(5, 9, 6, 'minecraft:dark_oak_stairs', **stairs('west', 'top'))
    for y in range(5, 10):
        b.set(8, y, 6, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(8, 4, 6, 'minecraft:chiseled_stone_bricks')
    b.set(6, 9, 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    quarry_scaffold(b, 9, 3, 2, 3)
    quarry_scaffold(b, 5, 7, 2, 2)
    # cart track: from the foot of the crane out of the pit to the ore heap, ending at a tipped ore tub
    quarry_rails(b, [(7, 1, z) for z in range(6, 12)] + [(7, 1, 12), (6, 1, 12), (5, 1, 12)])
    b.set(4, 1, 12, 'minecraft:cauldron')
    quarry_heap(b, 3, 14, kind='ore')
    b.set(2, 1, 11, 'minecraft:iron_ore')
    # mason's lodge, north-east: bed, ledger, stores, lantern under the ridge
    quarry_hut(b, 13, 3, 17, 7, along='z', door=(15, 7, 'south'), windows=((13, 5), (17, 5), (15, 3)))
    b.set(14, 1, 4, 'minecraft:red_bed', facing='north', part='head', occupied=False)
    b.set(14, 1, 5, 'minecraft:red_bed', facing='north', part='foot', occupied=False)
    b.set(16, 1, 4, 'minecraft:barrel', facing='up', open=False)
    b.set(16, 2, 4, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(16, 1, 5, 'minecraft:lectern', facing='west', has_book=False, powered=False)
    b.set(14, 1, 6, 'minecraft:chest', facing='east', type='single', waterlogged=False)
    b.set(15, 6, 5, 'minecraft:lantern', hanging=True, waterlogged=False)
    # the stone-cutting yard and the standard in the open
    for x in range(9, 12):
        for z in range(5, 10):
            b.set(x, 0, z, 'minecraft:polished_andesite')
    standard(b, 10, 1, 7)
    b.set(12, 1, 6, 'minecraft:stonecutter', facing='west')
    b.set(12, 1, 8, 'minecraft:stonecutter', facing='west')
    quarry_heap(b, 12, 10, kind='cut')
    b.set(9, 1, 5, 'minecraft:grindstone', face='floor', facing='south')
    for x in (8, 12):
        b.set(x, 1, 12, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    b.set(16, 1, 12, 'minecraft:barrel', facing='up', open=False)
    b.set(16, 1, 13, 'minecraft:barrel', facing='up', open=False)
    b.set(16, 2, 12, 'minecraft:barrel', facing='up', open=False)
    quarry_heap(b, 16, 15, kind='spoil')
    # gate: flared crossbow towers, lanterns on posts outside, the trap line behind
    quarry_tower(b, 1, 17, 7)
    quarry_tower(b, 17, 17, 7)
    for x in (8, 12):
        b.set(x, 1, 20, 'minecraft:spruce_fence', **NO_SIDES)
        b.set(x, 2, 20, 'minecraft:lantern', hanging=False, waterlogged=False)
    alarm(b, 10, 18)
    spikes(b, (9, 17), (11, 17))
    arrow_slit(b, 8, 16, 3, 'minecraft:cobblestone')
    quarry_green(b, n, lo, hi)
    b.save('quarry_stronghold.nbt')


def witch_coven():
    """17x17 bog hut: a timber-framed witch's hut on crooked mangrove stilts over a lily pond, a gabled dark roof
    with a smoking chimney, a porch with soul lanterns, a cauldron boiling on soul fire and an herb bed in the yard.
    The standard stands at the foot of the hut's stair. Core (8, 1, 13). Height 12."""
    n = 17
    b = Builder(n, 12, n)
    none4 = dict(north=False, south=False, east=False, west=False, waterlogged=False)
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                h = (x * 73856093 ^ z * 83492791) >> 3 & 15
                soil = 'minecraft:' + ('grass_block' if h < 8 else 'moss_block' if h < 11 else 'podzol' if h < 13
                                       else 'coarse_dirt' if h < 15 else 'mud')
                b.set(x, 0, z, soil)
            else:
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:mud', 'minecraft:muddy_mangrove_roots', 'minecraft:coarse_dirt'))
    fill(b, 0, 1, 0, n - 1, 11, n - 1, 'minecraft:air')
    for x, z in ring(0, n - 1):
        if b.blocks[(x, 0, z)][0] != 'minecraft:mud' and (x * 5 + z * 11) % 4 == 0:
            b.set(x, 1, z, 'minecraft:fern' if (x + z) % 2 else 'minecraft:short_grass')
    # the pond under the hut, lily pads where the hut doesn't shade it
    dry = {(5, 2), (11, 2), (5, 8), (11, 8), (8, 9), (8, 10), (10, 1)}
    for x in range(n):
        for z in range(2, 10):
            if ((x - 8) / 5.6) ** 2 + ((z - 5) / 4.6) ** 2 <= 1 and (x, z) not in dry:
                b.set(x, 0, z, 'minecraft:water', level=0)
                if not (4 <= x <= 12 and 1 <= z <= 8) and (x * 5 + z * 3) % 4 == 0:
                    b.set(x, 1, z, 'minecraft:lily_pad')
    # a hedge of mangrove roots, knee to head high, with log corner posts and bone stakes crowned with skulls
    for x, z in ring(1, 15):
        if z == 15 and 6 <= x <= 10:
            continue
        top = 1 + (x * 7 + z * 11) % 3 // 2 + (1 if (x + z) % 5 == 0 else 0)
        for y in range(1, top + 1):
            b.set(x, y, z, 'minecraft:mangrove_roots', waterlogged=False)
    for x, z in ((1, 1), (15, 1), (1, 15), (15, 15)):
        for y in (1, 2, 3):
            b.set(x, y, z, 'minecraft:mangrove_log', axis='y')
        b.set(x, 4, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for x, z in ((1, 8), (15, 8), (4, 15), (12, 15), (5, 1)):
        b.set(x, 1, z, 'minecraft:bone_block', axis='y')
        b.set(x, 2, z, 'minecraft:mangrove_fence', **none4)
        b.set(x, 3, z, 'minecraft:skeleton_skull', rotation=8 if z == 1 else 0)
    # gate: two crooked posts under a log lintel, 3 wide and 2 high
    for x in (6, 10):
        for y in (1, 2, 3):
            b.set(x, y, 15, 'minecraft:mangrove_log', axis='y')
    for x in (7, 8, 9):
        b.set(x, 3, 15, 'minecraft:stripped_mangrove_log', axis='x')
    b.set(8, 4, 15, 'minecraft:skeleton_skull', rotation=0)
    for x in (6, 10):
        b.set(x, 4, 15, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for z in range(11, 16):
        b.set(8, 0, z, 'minecraft:dirt_path')
    for x, z in ((3, 10), (6, 14), (12, 14), (13, 10), (2, 5), (14, 6)):
        b.set(x, 1, z, 'minecraft:moss_carpet')
    # the hut: stilts, a plank floor at y3 with a porch, timber-framed walls, a gabled roof
    x0, x1, z0, z1 = 5, 11, 2, 6
    for x, z in ((5, 2), (11, 2), (5, 8), (11, 8), (8, 2)):
        b.set(x, 0, z, 'minecraft:mud')
        for y in (1, 2):
            b.set(x, y, z, 'minecraft:mangrove_log', axis='y')
    for x, z in ((4, 2), (5, 3), (12, 8), (11, 9), (4, 8), (12, 1)):
        b.set(x, 1, z, 'minecraft:mangrove_roots', waterlogged=False)
    fill(b, x0, 3, z0, x1, 3, z1 + 2, 'minecraft:mangrove_planks')
    for x in range(x0, x1 + 1):
        b.set(x, 3, z1 + 2, 'minecraft:stripped_mangrove_log', axis='x')
    for x, z in ((6, 4), (9, 3), (7, 7), (10, 6), (6, 8)):
        b.set(x, 2, z, 'minecraft:hanging_roots', waterlogged=False)
    posts = {(x0, z0), (x1, z0), (x0, z1), (x1, z1), (8, z0), (7, z1), (9, z1), (x0, 4), (x1, 4)}
    for x, z in rect_ring(x0, z0, x1, z1):
        for y in (4, 5, 6, 7):
            if (x, z) in posts and y < 7:
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
            elif y == 6:
                b.set(x, y, z, 'minecraft:stripped_dark_oak_log', axis='x' if z in (z0, z1) else 'z')
            elif y < 6 or z in (z0, z1):
                b.set(x, y, z, 'minecraft:mud_bricks' if y < 6 else 'minecraft:spruce_planks')
    b.set(8, 4, z1, 'minecraft:spruce_door', facing='south', half='lower', hinge='left', open=False, powered=False)
    b.set(8, 5, z1, 'minecraft:spruce_door', facing='south', half='upper', hinge='left', open=False, powered=False)
    for x, z, ns, out in ((6, z0, False, (0, -1)), (10, z0, False, (0, -1)), (6, z1, False, None),
                          (10, z1, False, None), (x0, 3, True, (-1, 0)), (x1, 5, True, (1, 0))):
        b.set(x, 5, z, 'minecraft:glass_pane', **pane(ns))
        if out:  # a sill under the outside of the window (the south ones look onto the porch)
            b.set(x + out[0], 4, z + out[1], 'minecraft:spruce_trapdoor', facing='north', half='top', open=False,
                  powered=False, waterlogged=False)
    # gabled roof along x, one block of overhang all round; gable ends in planks; a cobble chimney on the north
    for d, y in enumerate(range(7, 10)):
        for x in range(x0 - 1, x1 + 2):
            b.set(x, y, z0 - 1 + d, 'minecraft:dark_oak_stairs', **stairs('south'))
            b.set(x, y, z1 + 1 - d, 'minecraft:dark_oak_stairs', **stairs('north'))
    for x in range(x0 - 1, x1 + 2):
        b.set(x, 10, 4, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (x0, x1):
        for y, zs in ((7, range(3, 6)), (8, range(3, 6)), (9, (4,))):
            for z in zs:
                if z == 4:
                    b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
                else:
                    b.set(x, y, z, 'minecraft:mud_bricks')
    for x in range(x0 + 1, x1):
        b.set(x, 9, 4, 'minecraft:dark_oak_log', axis='x')
    for x in range(x0 - 1, x1 + 2):  # eave shadow line: upside-down stairs under the overhang
        for z, f in ((z0 - 1, 'north'), (z1 + 1, 'south')):
            if (x, z) not in ((6, z1 + 1), (10, z1 + 1), (10, z0 - 1)):
                b.set(x, 6, z, 'minecraft:dark_oak_stairs', **stairs(f, 'top'))
    # a dormer over the door breaks the long south slope
    for x in (7, 9):
        b.set(x, 8, 6, 'minecraft:spruce_planks')
        for z in (5, 6):
            b.set(x, 9, z, 'minecraft:dark_oak_stairs', **stairs('east' if x == 7 else 'west'))
    b.set(8, 8, 6, 'minecraft:glass_pane', **pane(False))
    b.set(8, 9, 6, 'minecraft:spruce_planks')
    b.set(8, 9, 5, 'minecraft:spruce_planks')
    for z in (5, 6):
        b.set(8, 10, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for y in range(0, 12):
        if y == 11:
            b.set(10, y, 1, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
        else:
            b.set(10, y, 1, varied(10, y, 1, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:mud_bricks'))
    b.set(10, 10, 1, 'minecraft:mud_bricks')
    # inside: brewing corner, bed, shelf of candles, a rug, a hanging soul lantern from the ridge beam
    b.set(6, 4, 3, 'minecraft:brewing_stand', has_bottle_0=True, has_bottle_1=False, has_bottle_2=True)
    b.set(7, 4, 3, 'minecraft:water_cauldron', level=2)
    b.set(6, 4, 5, 'minecraft:barrel', facing='up', open=False)
    b.set(6, 5, 5, 'minecraft:potted_red_mushroom')
    b.set(10, 4, 3, 'minecraft:purple_bed', facing='north', part='head', occupied=False)
    b.set(10, 4, 4, 'minecraft:purple_bed', facing='north', part='foot', occupied=False)
    b.set(9, 4, 3, 'minecraft:bookshelf')
    b.set(9, 5, 3, 'minecraft:candle', candles=3, lit=True, waterlogged=False)
    b.set(10, 4, 5, 'minecraft:composter', level=5)
    for x, z in ((7, 4), (8, 4), (8, 5)):
        b.set(x, 4, z, 'minecraft:purple_carpet')
    b.set(8, 8, 4, 'minecraft:soul_lantern', hanging=True, waterlogged=False)
    b.set(10, 6, 5, 'minecraft:cobweb')
    # porch: rail with potted mushrooms, soul lanterns under the eave, a stair down to the yard
    for x in (5, 6, 10, 11):
        b.set(x, 4, 8, 'minecraft:mangrove_fence', **none4)
    b.set(6, 5, 8, 'minecraft:potted_brown_mushroom')
    b.set(10, 5, 8, 'minecraft:potted_red_mushroom')
    for x in (6, 10):
        b.set(x, 6, 7, 'minecraft:soul_lantern', hanging=True, waterlogged=False)
    for y, z in ((3, 9), (2, 10), (1, 11)):
        b.set(8, y, z, 'minecraft:mangrove_stairs', **stairs('north'))
    for y, z in ((1, 9), (2, 9), (1, 10)):
        b.set(8, y, z, 'minecraft:mangrove_log', axis='y')
    standard(b, 8, 1, 13)
    # the yard: a cauldron boiling on soul fire, a brewing stump, an herb bed in podzol
    b.set(4, 1, 12, 'minecraft:soul_campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    b.set(4, 2, 12, 'minecraft:water_cauldron', level=3)
    b.set(3, 1, 11, 'minecraft:mangrove_log', axis='y')
    b.set(3, 2, 11, 'minecraft:brewing_stand', has_bottle_0=True, has_bottle_1=True, has_bottle_2=False)
    b.set(5, 1, 11, 'minecraft:barrel', facing='up', open=False)
    b.set(5, 2, 11, 'minecraft:potted_brown_mushroom')
    b.set(3, 1, 13, 'minecraft:skeleton_skull', rotation=4)
    herbs = {(11, 11): 'fern', (12, 11): 'brown_mushroom', (13, 11): 'allium', (11, 12): 'red_mushroom',
             (12, 12): 'fern', (13, 12): 'brown_mushroom', (11, 13): 'allium', (13, 13): 'red_mushroom'}
    for (x, z), plant in herbs.items():
        b.set(x, 0, z, 'minecraft:podzol', snowy=False)
        b.set(x, 1, z, 'minecraft:' + plant)
    b.set(12, 0, 13, 'minecraft:podzol', snowy=False)
    b.set(12, 1, 13, 'minecraft:composter', level=3)
    for x, z in ((10, 11), (14, 11), (10, 14), (14, 14)):
        b.set(x, 1, z, 'minecraft:mangrove_fence', **none4)
    b.set(14, 2, 11, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    b.set(10, 2, 14, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    # traps: the plate by the gate, caltrops either side, a berry pit at the stair foot, a sculk ear by the wall
    alarm(b, 8, 14)
    spikes(b, (7, 14), (9, 13))
    pitfall(b, 8, 12, 'sweet_berry_bush')
    b.set(2, 0, 14, 'warbands:alarm_horn', powered=False)
    b.set(2, 1, 14, 'minecraft:sculk_sensor', power=0, sculk_sensor_phase='inactive', waterlogged=False)
    b.save('witch_coven.nbt')


# ---- FROZEN_OUTPOST helpers (also used by the frozen layouts in camps_feudal.py) ----

NONE4 = dict(north=False, south=False, east=False, west=False, waterlogged=False)
SNOW_GROUND = ('minecraft:snow_block', 'minecraft:packed_ice', 'minecraft:gravel')


def _frozen_at(b, x, y, z):
    return b.blocks.get((x, y, z), ('minecraft:air', ()))[0]


def frozen_icicle(b, x, z, support_y, length=1):
    """Pointed dripstone hanging under the full block at support_y: tip lowest, thicker pieces above it."""
    for i in range(length):
        y = support_y - length + i
        thick = 'tip' if i == 0 else 'base' if i == length - 1 and length > 2 else 'frustum'
        b.set(x, y, z, 'minecraft:pointed_dripstone', vertical_direction='down', thickness=thick, waterlogged=False)


def frozen_dome(b, cx, cz, r, blocks=('minecraft:snow_block', 'minecraft:snow_block', 'minecraft:packed_ice'), y0=1):
    """A one-block snow shell, hemisphere of radius r on y0. Each layer reaches in to where the next one starts,
    so every ring rests on the one below (a thin sphere leaves its cap floating). Clears the inside."""
    def rad(k):
        return math.sqrt(r * r - k * k) if k <= r else -1.0
    for k in range(r + 1):
        y = y0 + k
        inner = rad(k + 1) - 0.5
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                d = math.hypot(x - cx, z - cz)
                if d <= rad(k) + 0.3:
                    b.set(x, y, z, varied(x, y, z, *blocks) if d >= inner else 'minecraft:air')


def frozen_banner(b, x, z, y0=1, pole=3, color='light_blue', rotation=6):
    """A spruce pole with a banner on top. All frozen banners share one rotation: the wind blows one way."""
    for y in range(y0, y0 + pole):
        b.set(x, y, z, 'minecraft:spruce_fence', **NONE4)
    b.set(x, y0 + pole, z, f'minecraft:{color}_banner', rotation=rotation)


def frozen_fur_rack(b, x, z, axis='x', y=1, furs=('minecraft:white_wool', 'minecraft:light_gray_wool')):
    """A drying rack three long: fence legs, a stripped-log beam, pelts hanging from the beam."""
    dx, dz = (1, 0) if axis == 'x' else (0, 1)
    for i in (0, 2):
        for yy in (y, y + 1):
            b.set(x + i * dx, yy, z + i * dz, 'minecraft:spruce_fence', **NONE4)
    for i in range(3):
        b.set(x + i * dx, y + 2, z + i * dz, 'minecraft:stripped_spruce_log', axis=axis)
    b.set(x + dx, y + 1, z + dz, furs[0])
    if len(furs) > 1:
        b.set(x + dx, y, z + dz, 'minecraft:light_gray_carpet')


def frozen_bed(b, x, z, facing, color='white', y=1):
    """A fur bedroll: the head at (x, z), the foot one step against facing."""
    dx, dz = {'north': (0, 1), 'south': (0, -1), 'east': (-1, 0), 'west': (1, 0)}[facing]
    b.set(x, y, z, f'minecraft:{color}_bed', facing=facing, part='head', occupied=False)
    b.set(x + dx, y, z + dz, f'minecraft:{color}_bed', facing=facing, part='foot', occupied=False)


def frozen_drift(b, n, border=2, gate=()):
    """Breaks the square edge: snow drifts on the snow blocks near the rim, boulders and a stump here and there.
    Run it last, on empty cells only; the gate columns on the south edge stay clear of boulders."""
    for x in range(n):
        for z in range(n):
            if min(x, z, n - 1 - x, n - 1 - z) >= border or _frozen_at(b, x, 1, z) != 'minecraft:air':
                continue
            h = (x * 7349 + z * 3691) % 13
            if h in (0, 1) and not (z >= n - 3 and x in gate):
                b.set(x, 1, z, 'minecraft:mossy_cobblestone' if h else 'minecraft:cobblestone')
                b.set(x, 2, z, 'minecraft:snow', layers=2)
            elif h == 2 and not (z >= n - 3 and x in gate):
                b.set(x, 1, z, 'minecraft:spruce_log', axis='y')
                b.set(x, 2, z, 'minecraft:snow', layers=3)
            elif h < 9 and _frozen_at(b, x, 0, z) in ('minecraft:snow_block', 'minecraft:gravel'):
                b.set(x, 1, z, 'minecraft:snow', layers=1 + h % 3)


def frozen_outpost():
    """17x17 trappers' outpost: a snow wall on a cobble footing framed by spruce posts, icicles under its ledges, a
    roofed gate with the camp's blue banners. Inside, a spruce longhouse against the north wall (bunks, stores,
    smoker chimney), an igloo with an ice-blue fire, a timber watchtower with one archer, fur racks and a woodpile.
    Powder snow under open trapdoors on the path. Core (8, 1, 6) in front of the longhouse door. Height 12."""
    n, lo, hi = 17, 1, 15
    b = Builder(n, 12, n)
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, varied(x, 0, z, *SNOW_GROUND))
    for x in range(7, 10):  # the trodden path from the gate
        for z in range(8, n):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:gravel', 'minecraft:coarse_dirt', 'minecraft:snow_block'))
    for x in range(6, 11):  # the frozen yard in front of the longhouse
        for z in range(5, 8):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:packed_ice', 'minecraft:blue_ice'))

    # the wall: cobble footing, snow body, snow crenels; spruce posts at the corners and every few blocks
    for x, z in ring(lo, hi):
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone'))
        b.set(x, 2, z, 'minecraft:snow_block')
        b.set(x, 3, z, 'minecraft:packed_ice' if (x + z) % 3 else 'minecraft:blue_ice')
        b.set(x, 4, z, 'minecraft:stripped_spruce_log', axis='z' if x in (lo, hi) else 'x')
        if (x + z) % 2 == 0:
            b.set(x, 5, z, 'minecraft:snow_block')
        if (x in (lo, hi) and z % 5 == 1) or (z in (lo, hi) and x % 5 == 1) or is_corner(x, z, lo, hi):
            for y in range(1, 5):
                b.set(x, y, z, 'minecraft:spruce_log', axis='y')
            b.set(x, 5, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for x, z in ((lo, lo), (hi, lo), (lo, hi), (hi, hi)):
        b.set(x, 5, z, 'minecraft:spruce_log', axis='y')
        b.set(x, 6, z, 'minecraft:lantern', hanging=False, waterlogged=False)

    # gatehouse: log posts, a lintel, a little gabled roof, the camp's banners
    for x in (6, 10):
        for y in range(1, 7):
            b.set(x, y, hi, 'minecraft:spruce_log', axis='y')
    for x in (7, 8, 9):
        b.set(x, 1, hi, 'minecraft:spruce_fence_gate', facing='south', open=False, in_wall=False, powered=False)
        b.set(x, 2, hi, 'minecraft:spruce_fence_gate', facing='south', open=False, in_wall=False, powered=False)
        b.set(x, 3, hi, 'minecraft:stripped_spruce_log', axis='x')
        b.set(x, 4, hi, 'minecraft:spruce_planks')
    for x in range(5, 12):
        b.set(x, 5, hi + 1, 'minecraft:spruce_stairs', **stairs('north'))
        b.set(x, 5, hi - 1, 'minecraft:spruce_stairs', **stairs('south'))
        if 6 < x < 10:
            b.set(x, 5, hi, 'minecraft:spruce_planks')
        b.set(x, 6, hi, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for x in (6, 10):
        b.set(x, 7, hi, 'minecraft:light_blue_banner', rotation=6)
    for x in (7, 9):
        b.set(x, 4, hi + 1, 'minecraft:light_blue_wall_banner', facing='south')

    # the longhouse: x4..12, z1..4, its back is the camp wall
    for x in range(4, 13):
        for z in range(1, 5):
            for y in range(1, 5):
                b.set(x, y, z, 'minecraft:air')
    for x in range(5, 12):
        for z in (2, 3):
            b.set(x, 0, z, 'minecraft:spruce_planks')
            b.set(x, 4, z, 'minecraft:spruce_planks')   # loft ceiling
    for x, z in rect_ring(4, 1, 12, 4):
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
        b.set(x, 2, z, 'minecraft:spruce_planks')
        b.set(x, 3, z, 'minecraft:stripped_spruce_log', axis='x' if z in (1, 4) else 'z')
        b.set(x, 4, z, 'minecraft:spruce_planks')
    for x in (4, 7, 9, 12):
        for z in (1, 4):
            for y in (1, 2, 3):
                b.set(x, y, z, 'minecraft:spruce_log', axis='y')
    b.set(8, 1, 4, 'minecraft:spruce_door', facing='north', half='lower', hinge='left', open=False, powered=False)
    b.set(8, 2, 4, 'minecraft:spruce_door', facing='north', half='upper', hinge='left', open=False, powered=False)
    for x in (5, 11):
        b.set(x, 2, 4, 'minecraft:glass_pane', **pane(False))
        for sx in (x - 1, x + 1):
            if sx not in (4, 12):
                b.set(sx, 2, 5, 'minecraft:spruce_trapdoor', facing='south', half='bottom', open=True, powered=False,
                      waterlogged=False)
    # gable roof along x, eaves overhanging one block on every side, gable ends filled
    for x in range(3, 14):
        for y, zn, zs in ((4, 0, 5), (5, 1, 4), (6, 2, 3)):
            b.set(x, y, zn, 'minecraft:spruce_stairs', **stairs('south'))
            b.set(x, y, zs, 'minecraft:spruce_stairs', **stairs('north'))
        for z in (2, 3):
            b.set(x, 7, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for x in (4, 12):
        for z in (2, 3):
            b.set(x, 5, z, 'minecraft:stripped_spruce_log', axis='z')
    for y in range(1, 9):  # cobble chimney through the roof, a smoker at its foot
        b.set(11, y, 2, 'minecraft:cobblestone')
    b.set(11, 1, 2, 'minecraft:smoker', facing='south', lit=True)
    b.set(11, 9, 2, 'minecraft:cobblestone_wall', up=True, north='none', south='none', east='none', west='none',
          waterlogged=False)
    frozen_bed(b, 5, 2, 'north', 'white')
    frozen_bed(b, 6, 2, 'north', 'light_gray')
    b.set(10, 1, 2, 'minecraft:barrel', facing='up', open=False)
    b.set(10, 1, 3, 'minecraft:crafting_table')
    b.set(9, 1, 2, 'minecraft:chest', facing='south', type='single', waterlogged=False)
    for x in (7, 8):
        b.set(x, 1, 2, 'minecraft:light_gray_carpet')
    b.set(6, 3, 3, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(9, 3, 3, 'minecraft:lantern', hanging=True, waterlogged=False)
    for x in (4, 12):  # icicles off the eave ends
        frozen_icicle(b, x - 1 if x == 4 else x + 1, 0, 4)
        frozen_icicle(b, x - 1 if x == 4 else x + 1, 5, 4)

    standard(b, 8, 1, 6)
    frozen_banner(b, 6, 7)
    frozen_banner(b, 10, 7)

    # the watchtower: x2..4, z8..10, platform y5, archer on top, pyramid roof
    for x, z in ((2, 8), (4, 8), (2, 10), (4, 10)):
        for y in range(1, 9):
            b.set(x, y, z, 'minecraft:spruce_log', axis='y')
    for x in range(2, 5):
        for z in range(8, 11):
            if _frozen_at(b, x, 5, z) == 'minecraft:air':
                b.set(x, 5, z, 'minecraft:spruce_planks')
    for x, z in ((3, 8), (2, 9), (4, 9)):
        b.set(x, 6, z, 'minecraft:spruce_fence', **NONE4)
    for y in range(1, 5):
        b.set(3, y, 10, 'minecraft:stripped_spruce_log', axis='y')
    ladder_cells = [(3, y, 11) for y in range(1, 6)]
    for x, y, z in ladder_cells:
        b.set(x, y, z, 'minecraft:ladder', facing='south', waterlogged=False)
    for x, z in rect_ring(1, 7, 5, 11):
        facing = 'south' if z == 7 else 'north' if z == 11 else 'east' if x == 1 else 'west'
        if is_corner(x - 1, z - 7, 0, 4):
            b.set(x, 8, z, 'minecraft:spruce_planks')
        else:
            b.set(x, 8, z, 'minecraft:spruce_stairs', **stairs(facing))
    for x, z in rect_ring(2, 8, 4, 10):
        facing = 'south' if z == 8 else 'north' if z == 10 else 'east' if x == 2 else 'west'
        b.set(x, 9, z, 'minecraft:spruce_planks' if is_corner(x - 2, z - 8, 0, 2) else 'minecraft:spruce_stairs',
              **({} if is_corner(x - 2, z - 8, 0, 2) else stairs(facing)))
    b.set(3, 9, 9, 'minecraft:spruce_planks')
    b.set(3, 10, 9, 'minecraft:light_blue_banner', rotation=6)
    for x, z in ((1, 7), (5, 7), (1, 11), (5, 11)):
        frozen_icicle(b, x, z, 8)

    # the igloo: an ice-blue fire, a bedroll, stores; door to the west
    frozen_dome(b, 12, 10, 3)
    for x in (9, 10):
        for y in (1, 2):
            b.set(x, y, 10, 'minecraft:air')
    b.set(9, 3, 10, 'minecraft:packed_ice')
    b.set(12, 0, 10, 'minecraft:cobblestone')
    b.set(12, 1, 10, 'minecraft:soul_campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    frozen_bed(b, 13, 8, 'east', 'light_gray')
    b.set(14, 1, 10, 'minecraft:barrel', facing='up', open=False)
    b.set(12, 1, 12, 'minecraft:lantern', hanging=False, waterlogged=False)

    # trappers' corner: pelts on a rack, a woodpile with a chopping stump
    frozen_fur_rack(b, 2, 12, 'z')
    for x in (4, 5):
        for z in (13, 14):
            b.set(x, 1, z, 'minecraft:spruce_log', axis='x')
    b.set(4, 2, 14, 'minecraft:spruce_log', axis='x')
    b.set(6, 1, 13, 'minecraft:stripped_spruce_log', axis='y')
    b.set(13, 1, 14, 'minecraft:barrel', facing='up', open=False)
    b.set(14, 1, 14, 'minecraft:barrel', facing='up', open=False)
    b.set(14, 1, 13, 'minecraft:spruce_fence', **NONE4)
    b.set(14, 2, 13, 'minecraft:lantern', hanging=False, waterlogged=False)

    alarm(b, 8, 14)
    pitfall(b, 8, 10)          # powder snow against a snow fort: only the open trapdoor gives it away
    pitfall(b, 7, 8)
    spikes(b, (7, 13), (9, 13))
    frozen_drift(b, n, 1, gate=range(5, 12))
    b.save('frozen_outpost.nbt')


# ---- cavalry helpers (CAVALRY_ENCAMPMENT layouts here and in camps_castle.py): pavilions, lists, stables, props ----

CAVALRY_NOT_SOLID = ('air', 'water', '_slab', '_stairs', '_trapdoor', '_door', '_carpet', '_banner', 'lantern', 'torch',
                     'campfire', '_bed', '_button', '_pressure_plate', 'tripwire', 'end_rod', 'caltrops', 'alarm_plate',
                     'anvil', 'grindstone', 'bell', 'cake', 'lectern', 'chest', 'fern', 'grass', 'ladder', 'iron_chain',
                     'camp_standard', 'fence_gate', 'sweet_berry_bush', 'poppy', 'dandelion', 'rail', 'flower')


def cavalry_connect(b):
    """Joins every fence to the fences, gates and full blocks beside it, as the game does when it places them, so the
    previews show rails instead of loose posts."""
    sides = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    for (x, y, z), (full, _) in list(b.blocks.items()):
        if not full.endswith('_fence'):
            continue
        props = {}
        for side, (dx, dz) in sides.items():
            other, oprops = b.blocks.get((x + dx, y, z + dz), ('minecraft:air', ()))
            o = other.split(':', 1)[1]
            if o.endswith('_fence'):
                props[side] = True
            elif o.endswith('fence_gate'):
                props[side] = dict(oprops).get('facing') in (('east', 'west') if dz else ('north', 'south'))
            else:
                props[side] = not any(part in o for part in CAVALRY_NOT_SOLID)
        b.set(x, y, z, full, waterlogged=False, **props)


def cavalry_pavilion(b, cx, cz, r, stripes, door='south', pennant='minecraft:red_banner', clip=None, trim=None):
    """A round tournament pavilion: striped cloth walls two high on a circle of radius r, a conical striped roof that
    overhangs the walls by one, a centre pole running through the roof with a pennant on top, a plank floor and a
    two-high doorway on the {@code door} side. {@code clip(x, z)} leaves roof and wall cells out (box edges, the
    standard's air). Returns the y of the pennant."""
    import math
    clip = clip or (lambda x, z: False)
    n = b.size[0]

    trim = trim or stripes[1]

    def colour(x, z):
        return stripes[(x + z) % 2]

    cells = [(x, z) for x in range(cx - r - 2, cx + r + 3) for z in range(cz - r - 2, cz + r + 3)
             if 0 <= x < n and 0 <= z < b.size[2] and not clip(x, z)]
    d = {(x, z): math.hypot(x - cx, z - cz) for x, z in cells}
    for (x, z), dist in d.items():
        if dist < r - 0.5:
            b.set(x, 0, z, 'minecraft:spruce_planks')
            for y in (1, 2):
                b.set(x, y, z, 'minecraft:air')
        elif dist < r + 0.5:
            for y in (1, 2):
                b.set(x, y, z, colour(x, z))
    dx, dz = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[door]
    for y in (1, 2):
        b.set(cx + dx * r, y, cz + dz * r, 'minecraft:air')
    b.set(cx + dx * r, 0, cz + dz * r, 'minecraft:spruce_planks')
    y, lo, hi = 3, r - 0.5, r + 1.5
    while hi > 0:
        for (x, z), dist in d.items():
            if lo <= dist < hi or y > 3 and hi <= dist < hi + 1:   # overlap the band below so the cone holds
                b.set(x, y, z, trim if y == 3 and dist >= r + 0.5 else stripes[(y - 3) % 2])
            elif dist < lo:
                b.set(x, y, z, 'minecraft:air')
        y, hi, lo = y + 1, lo, lo - 1
    apex = y - 1
    for yy in range(1, apex):
        b.set(cx, yy, cz, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(cx, apex + 1, cz, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(cx, apex + 2, cz, pennant, rotation=0)
    return apex + 2


def cavalry_bed(b, x, z, facing, colour='red'):
    """A cot: foot at (x, z), head one step toward {@code facing}."""
    dx, dz = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[facing]
    b.set(x, 1, z, f'minecraft:{colour}_bed', facing=facing, part='foot', occupied=False)
    b.set(x + dx, 1, z + dz, f'minecraft:{colour}_bed', facing=facing, part='head', occupied=False)


def cavalry_banner_pole(b, x, z, banner, h=3, y=1, wood='minecraft:spruce_fence'):
    """A heraldic pole: fence shaft {@code h} high, a banner on top."""
    for yy in range(y, y + h):
        b.set(x, yy, z, wood, north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(x, y + h, z, banner, rotation=0)


def cavalry_dummy(b, x, z, facing='south'):
    """A straw training dummy: post, hay-bale body, carved-pumpkin head."""
    b.set(x, 1, z, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(x, 2, z, 'minecraft:hay_block', axis='y')
    b.set(x, 3, z, 'minecraft:carved_pumpkin', facing=facing)


def cavalry_quintain(b, x, z, axis='x'):
    """The jousting practice post: a crossbar on a post, a shield (target) on one arm and a sandbag (hay) on the
    other, high enough for a rider to pass under."""
    dx, dz = (1, 0) if axis == 'x' else (0, 1)
    for y in (1, 2):
        b.set(x, y, z, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(x, 3, z, 'minecraft:stripped_spruce_log', axis=axis)
    b.set(x + dx, 3, z + dz, 'minecraft:target')
    b.set(x - dx, 3, z - dz, 'minecraft:hay_block', axis=axis)


def cavalry_lances(b, cells, y=1):
    """Lances standing in a rack: tall shafts tipped with white (end rods)."""
    for x, z in cells:
        for yy in (y, y + 1):
            b.set(x, yy, z, 'minecraft:spruce_fence', north=False, south=False, east=False, west=False, waterlogged=False)
        b.set(x, y + 2, z, 'minecraft:end_rod', facing='up')


def cavalry_trough(b, x0, z0, length, axis='z', y=1):
    """A horse trough: a strip of water boxed in stripped logs."""
    along = (0, 1) if axis == 'z' else (1, 0)
    across = (1, 0) if axis == 'z' else (0, 1)
    for i in range(-1, length + 1):
        x, z = x0 + along[0] * i, z0 + along[1] * i
        if 0 <= i < length:
            b.set(x, y, z, 'minecraft:water', level=0)
            for s in (-1, 1):
                b.set(x + across[0] * s, y, z + across[1] * s, 'minecraft:stripped_spruce_log', axis=axis)
        else:
            b.set(x, y, z, 'minecraft:stripped_spruce_log', axis='x' if axis == 'z' else 'z')


def cavalry_scatter(b, n, cells, seed, ground=('minecraft:grass_block',)):
    """Tufts of grass and ferns on the soil cells given (edge blend)."""
    import random
    rnd = random.Random(seed)
    for x, z in cells:
        if b.blocks.get((x, 0, z), ('',))[0] in ground and b.blocks.get((x, 1, z), ('minecraft:air',))[0] == 'minecraft:air':
            roll = rnd.random()
            if roll < 0.18:
                b.set(x, 1, z, 'minecraft:short_grass')
            elif roll < 0.26:
                b.set(x, 1, z, 'minecraft:fern')


def cavalry_tent(b, cx, cz, half_len, axis, door, colours, pennant, wood='minecraft:spruce_fence'):
    """A ridge tent seen from the side as a stepped A: two-high cloth walls seven wide, then steps in to a ridge one
    block above. Striped along its length in {@code colours}, closed at the far end, open at the {@code door} end (+1
    or -1 along the axis), plank floor, a pole with a pennant over the ridge and pegged guy stakes. Interior is clear
    to y2 for |across| <= 2 and to y3 for |across| <= 1."""
    def at(i, j):  # i along the axis, j across it
        return (cx + i, cz + j) if axis == 'x' else (cx + j, cz + i)
    for i in range(-half_len, half_len + 1):
        for j in range(-3, 4):
            x, z = at(i, j)
            if abs(j) <= 2:
                b.set(x, 0, z, 'minecraft:spruce_planks')
                for y in range(1, 5):
                    b.set(x, y, z, 'minecraft:air')
        closed = i == -door * half_len
        for j in range(-3, 4):
            for y in range(1, 6):
                edge = {1: 3, 2: 3, 3: 2, 4: 1, 5: 0}[y]
                if abs(j) == edge or (closed and abs(j) < edge) or (y == 5 and j == 0):
                    tx, tz = at(i, j)
                    b.set(tx, y, tz, colours[0] if (y in (1, 5) or i % 3 == 0) else colours[1])
    for j in (-4, 4):  # guy stakes at the corners of the long sides
        for i in (-half_len, half_len):
            x, z = at(i, j)
            b.set(x, 1, z, wood, north=False, south=False, east=False, west=False, waterlogged=False)
    x, z = at(0, 0)
    b.set(x, 6, z, wood, north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(x, 7, z, wood, north=False, south=False, east=False, west=False, waterlogged=False)
    b.set(x, 8, z, pennant, rotation=0)


def cavalry_encampment():
    """23x23 knights' field camp: the lord's striped pavilion at the north end with the standard in front of it, two
    knights' pavilions either side, a lean-to stable with a trough on the east, a practice yard with a quintain,
    straw dummies and archery butts on the west, lances racked by the gate. Core (11, 1, 7). Height 10."""
    n, lo, hi = 23, 1, 21
    b = Builder(n, 10, n)
    fill(b, 0, 1, 0, n - 1, 9, n - 1, 'minecraft:air')
    for x in range(n):
        for z in range(n):
            inside = lo < x < hi and lo < z < hi
            if inside:
                name = varied(x, 0, z, 'minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:podzol')
                if (x * 7 + z * 3) % 5 == 0:
                    name = 'minecraft:grass_block'
            else:
                name = varied(x, 0, z, 'minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:moss_block')
            b.set(x, 0, z, name, **({'snowy': False} if name == 'minecraft:grass_block' else {}))
    for z in range(8, n):   # the trodden lane from the gate to the standard, and the cross lane
        for x in (10, 11, 12):
            b.set(x, 0, z, 'minecraft:dirt_path')
    for x in range(3, 20):
        b.set(x, 0, 10, 'minecraft:dirt_path')
    for x in range(9, 14):
        for z in range(7, 10):
            b.set(x, 0, z, 'minecraft:dirt_path' if (x + z) % 3 else 'minecraft:coarse_dirt')

    lord = (11, 3)

    def under_lord(x, z):
        return (x - lord[0]) ** 2 + (z - lord[1]) ** 2 <= 3.6 ** 2

    # perimeter rail on posts; taller corner posts with lanterns; the pavilion's back closes the north side
    for x, z in ring(lo, hi):
        if under_lord(x, z):
            continue
        b.set(x, 1, z, 'minecraft:oak_fence')
        if (x + z) % 4 == 0:
            b.set(x, 1, z, 'minecraft:stripped_oak_log', axis='y')
            b.set(x, 2, z, 'minecraft:oak_fence')
        elif not (z == hi and 9 <= x <= 13):  # a rope (chain) strung between the stakes
            b.set(x, 2, z, 'minecraft:iron_chain', axis='z' if x in (lo, hi) else 'x', waterlogged=False)
    for x, z in ((lo, lo), (hi, lo), (lo, hi), (hi, hi)):
        b.set(x, 1, z, 'minecraft:oak_log', axis='y')
        b.set(x, 2, z, 'minecraft:oak_log', axis='y')
        b.set(x, 3, z, 'minecraft:lantern', hanging=False, waterlogged=False)
    for x in (10, 11, 12):  # broad gate, open above
        b.set(x, 1, hi, 'minecraft:oak_fence_gate', facing='south', open=False, in_wall=False, powered=False)
    for x, banner in ((9, 'minecraft:red_banner'), (13, 'minecraft:white_banner')):
        b.set(x, 1, hi, 'minecraft:oak_log', axis='y')
        b.set(x, 2, hi, 'minecraft:oak_log', axis='y')
        cavalry_banner_pole(b, x, hi, banner, h=1, y=3)

    # the lord's pavilion, the standard in the open in front of it, flanked by banner poles
    cavalry_tent(b, 11, 3, 3, 'z', 1, ('minecraft:red_wool', 'minecraft:white_wool'), 'minecraft:red_banner')
    cavalry_bed(b, 9, 3, 'north', 'red')
    b.set(13, 1, 2, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(13, 1, 3, 'minecraft:cartography_table')
    b.set(13, 1, 4, 'minecraft:lectern', facing='west', has_book=False, powered=False)
    b.set(9, 1, 5, 'minecraft:barrel', facing='up', open=False)
    for z in (4, 5):
        b.set(11, 1, z, 'minecraft:red_carpet')
    b.set(12, 3, 3, 'minecraft:lantern', hanging=True, waterlogged=False)
    standard(b, 11, 1, 7)
    for x in (9, 13):
        cavalry_banner_pole(b, x, 8, 'minecraft:red_banner' if x == 9 else 'minecraft:white_banner')
        b.set(x, 0, 8, 'minecraft:coarse_dirt')

    # two knights' pavilions, doors on the yard
    cavalry_tent(b, 4, 6, 2, 'x', 1, ('minecraft:blue_wool', 'minecraft:white_wool'), 'minecraft:blue_banner')
    cavalry_bed(b, 3, 6, 'north', 'blue')
    b.set(3, 1, 7, 'minecraft:barrel', facing='up', open=False)
    b.set(5, 1, 7, 'minecraft:blue_carpet')
    b.set(5, 3, 7, 'minecraft:lantern', hanging=True, waterlogged=False)
    cavalry_tent(b, 18, 6, 2, 'x', -1, ('minecraft:blue_wool', 'minecraft:white_wool'), 'minecraft:blue_banner')
    cavalry_bed(b, 19, 6, 'north', 'blue')
    b.set(19, 1, 7, 'minecraft:chest', facing='west', type='single', waterlogged=False)
    b.set(17, 1, 7, 'minecraft:blue_carpet')
    b.set(17, 3, 7, 'minecraft:lantern', hanging=True, waterlogged=False)

    # lean-to stable on the east side: cobble footing, plank walls framed in logs, open to the west, three stalls
    # with hay, a shallow roof of stairs and slabs with rafter ends, a trough in front
    x_back, z0, z1 = 20, 12, 20
    for z in range(z0, z1 + 1):
        post = (z - z0) % 4 == 0
        b.set(x_back, 0, z, 'minecraft:coarse_dirt')
        b.set(x_back, 1, z, 'minecraft:spruce_log' if post else varied(x_back, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'), axis='y')
        for y in (2, 3, 4):
            b.set(x_back, y, z, 'minecraft:spruce_log' if post else 'minecraft:spruce_planks', axis='y')
        for x in range(16, x_back):
            b.set(x, 0, z, 'minecraft:coarse_dirt' if (x + z) % 3 else 'minecraft:rooted_dirt')
    for z in range(z0 - 1, z1 + 2):
        rafter = (z - z0) % 4 == 0
        b.set(15, 3, z, 'minecraft:stripped_spruce_log' if rafter else 'minecraft:dark_oak_stairs',
              **({'axis': 'x'} if rafter else stairs('east')))
        b.set(16, 3, z, 'minecraft:spruce_slab', type='top', waterlogged=False)
        b.set(17, 4, z, 'minecraft:spruce_stairs', **stairs('east'))
        b.set(18, 4, z, 'minecraft:spruce_slab', type='top', waterlogged=False)
        b.set(19, 5, z, 'minecraft:spruce_stairs', **stairs('east'))
        b.set(20, 5, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
        b.set(21, 5, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for z in (z0, z0 + 4, z1):
        for y in (1, 2):
            b.set(16, y, z, 'minecraft:spruce_log', axis='y')
        b.set(20, 4, z, 'minecraft:stripped_spruce_log', axis='x')
    for z in range(z0 + 1, z1):
        if (z - z0) % 4:
            b.set(16, 2, z, 'minecraft:stripped_spruce_log', axis='z')
    for z in (z0, z1):   # gable-side walls, planks up under the roof
        for x in (17, 18, 19):
            for y in range(1, 4 if x < 19 else 5):
                b.set(x, y, z, 'minecraft:spruce_planks' if y > 1 else 'minecraft:cobblestone')
    for x in (17, 18, 19):
        b.set(x, 1, z0 + 4, 'minecraft:spruce_fence')
    b.set(17, 1, z0 + 4, 'minecraft:spruce_fence_gate', facing='east', open=True, in_wall=False, powered=False)
    for z in (z0 + 1, z0 + 3, z0 + 5, z0 + 7):
        b.set(19, 1, z, 'minecraft:hay_block', axis='y')
    b.set(19, 2, z0 + 1, 'minecraft:hay_block', axis='x')
    b.set(19, 1, z0 + 2, 'minecraft:composter', level=0)
    b.set(19, 3, z0 + 6, 'minecraft:hay_block', axis='z')
    b.set(17, 3, z0 + 2, 'minecraft:lantern', hanging=True, waterlogged=False)
    b.set(17, 3, z0 + 6, 'minecraft:lantern', hanging=True, waterlogged=False)
    for z in (z0 + 2, z0 + 6):
        b.set(x_back - 1, 2, z, 'minecraft:spruce_trapdoor', facing='west', half='top', open=True, powered=False, waterlogged=False)
    cavalry_trough(b, 14, 13, 3, 'z')

    # practice yard on the west: quintain, straw dummies, archery butts, lances by the gate
    cavalry_quintain(b, 5, 14, 'z')
    cavalry_dummy(b, 3, 17, 'east')
    cavalry_dummy(b, 3, 12, 'east')
    for z in (15, 19):
        b.set(2, 1, z, 'minecraft:hay_block', axis='y')
        b.set(2, 2, z, 'minecraft:target')
    cavalry_lances(b, [(6, 20), (7, 20)])
    b.set(8, 1, 20, 'minecraft:stripped_spruce_log', axis='y')
    b.set(5, 1, 20, 'minecraft:anvil', facing='east')
    b.set(4, 1, 20, 'minecraft:grindstone', face='floor', facing='north', powered=False)
    b.set(2, 1, 20, 'minecraft:barrel', facing='up', open=False)
    b.set(3, 1, 20, 'minecraft:smithing_table')

    # the knights' fire, benches round it, a meal left on a barrel
    b.set(7, 1, 12, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    b.set(7, 1, 14, 'minecraft:spruce_stairs', **stairs('south'))
    b.set(8, 1, 14, 'minecraft:spruce_stairs', **stairs('south'))
    b.set(9, 1, 12, 'minecraft:barrel', facing='up', open=False)
    b.set(9, 2, 12, 'minecraft:cake', bites=3)

    # traps: alarm plate and caltrops inside the gate, a keg wire across the lane behind them
    alarm(b, 11, 20)
    spikes(b, (10, 20), (12, 20))
    keg_wire(b, 9, 18, 3)
    b.set(9, 1, 18, 'minecraft:oak_log', axis='y')
    b.set(13, 1, 18, 'minecraft:oak_log', axis='y')

    cavalry_scatter(b, n, [(x, z) for x in range(n) for z in range(n)], 7)
    cavalry_connect(b)
    b.save('cavalry_encampment.nbt')


# ---- SUNKEN_CRYPT helpers (the three crypt layouts; camps_castle.py uses them too) ----

CRYPT_STONE = ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks')
CRYPT_DEEP = ('minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:deepslate_tiles')
CRYPT_NONE4 = dict(north='none', south='none', east='none', west='none', waterlogged=False)


def crypt_surface(x, z):
    """Patchy topsoil for the ground round a sunk camp, so its edge fades into the terrain instead of a dirt square."""
    n = (x * 928371 + z * 1237 + x * z * 31) % 13
    return ('minecraft:grass_block' if n < 6 else 'minecraft:podzol' if n < 8 else 'minecraft:coarse_dirt' if n < 11
            else 'minecraft:moss_block')


def crypt_earth(b, n, sink):
    """Packed earth round the rim of a sunk camp up to the terrain (the box is cleared before placing: left out, it
    would cut a moat round the pit)."""
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                for y in range(1, sink):
                    b.set(x, y, z, 'minecraft:dirt')
                b.set(x, sink, z, crypt_surface(x, z))


def crypt_sarcophagus(b, x, y, z, along='x', length=2, candle=True, skull=False):
    """A stone coffin: undercut deepslate ends (upside-down stairs), polished deepslate between, a lit candle at the
    foot and maybe a skull at the head."""
    dx, dz = (1, 0) if along == 'x' else (0, 1)
    for i in range(length):
        cx, cz = x + dx * i, z + dz * i
        if i == 0:
            b.set(cx, y, cz, 'minecraft:deepslate_tile_stairs', **stairs('east' if along == 'x' else 'south', 'top'))
        elif i == length - 1:
            b.set(cx, y, cz, 'minecraft:deepslate_tile_stairs', **stairs('west' if along == 'x' else 'north', 'top'))
        else:
            b.set(cx, y, cz, 'minecraft:polished_deepslate')
    if candle:
        b.set(x + dx * (length - 1), y + 1, z + dz * (length - 1), 'minecraft:candle', candles=3, lit=True,
              waterlogged=False)
    if skull:
        b.set(x, y + 1, z, 'minecraft:skeleton_skull', rotation=4 if along == 'x' else 0)


def crypt_skull_post(b, x, y, z, h=1, wall='minecraft:deepslate_brick_wall', rotation=8):
    """A grave marker: a stone post with a skull on it (the approach to every crypt is lined with them)."""
    for k in range(h):
        b.set(x, y + k, z, wall, up=True, **CRYPT_NONE4)
    b.set(x, y + h, z, 'minecraft:skeleton_skull', rotation=rotation)


def crypt_rail(b, cells, y, *blocks):
    """A broken balustrade: wall blocks along the given cells, joined to their neighbours in the list."""
    cells = set(cells)
    for x, z in cells:
        conn = {d: 'low' if (x + dx, z + dz) in cells else 'none'
                for d, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0)))}
        straight = (conn['north'] == conn['south'] != 'none' and conn['east'] == conn['west'] == 'none') or \
                   (conn['east'] == conn['west'] != 'none' and conn['north'] == conn['south'] == 'none')
        b.set(x, y, z, varied(x, y, z, *blocks), up=not straight, waterlogged=False, **conn)


def crypt_hanging_light(b, x, y_top, z, drop):
    """A soul lantern on a chain hanging {@code drop} blocks below the solid block at y_top."""
    for y in range(y_top - drop + 1, y_top):
        b.set(x, y, z, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(x, y_top - drop, z, 'minecraft:soul_lantern', hanging=True, waterlogged=False)


def sunken_crypt():
    """19x19 burial court sunk 4 into the ground. A stair cut through the south rim, under an iron-barred arch with
    skull posts, drops into an open court: sarcophagi, two flooded ossuary cisterns for the drowned, skeleton archers
    in wall alcoves, and at the north end a deepslate mausoleum whose gabled roof stands above the grass. The
    standard is in the court in front of the mausoleum door; a keg wire spans the foot of the stair. Core (9, 1, 9).
    Height 14."""
    n, sink = 19, 4
    b = Builder(n, 14, n)
    for x in range(n):
        for z in range(n):
            b.set(x, 0, z, varied(x, 0, z, *CRYPT_DEEP))
    fill(b, 0, 1, 0, n - 1, 13, n - 1, 'minecraft:air')
    crypt_earth(b, n, sink)
    # pit walls, flush with the terrain
    for x, z in ring(1, 17):
        for y in range(1, sink + 1):
            b.set(x, y, z, varied(x, y, z, *CRYPT_STONE))
    # buttresses stand out of the long side walls; a chiseled cap at ground level
    for z in (4, 8, 12, 16):
        for x in (2, 16):
            for y in range(1, sink):
                b.set(x, y, z, 'minecraft:polished_deepslate')
            b.set(x, sink, z, 'minecraft:chiseled_deepslate')
    for x in (4, 14):
        for y in range(1, sink):
            b.set(x, y, 16, 'minecraft:polished_deepslate')
        b.set(x, sink, 16, 'minecraft:chiseled_deepslate')
    # skeleton archer alcoves in the side walls (feet at y 2, a skull shelf beside), bone ossuary niches further in
    for x in (1, 17):
        for y in (2, 3):
            b.set(x, y, 10, 'minecraft:air')
        b.set(x, 1, 10, 'minecraft:polished_deepslate')
        for z in (6, 14):
            b.set(x, 2, z, 'minecraft:bone_block', axis='z')
            b.set(x, 3, z, 'minecraft:skeleton_skull', rotation=4 if x == 1 else 12)
    for x, z in ((2, 2), (2, 3), (3, 2), (16, 2), (16, 3), (15, 2)):
        b.set(x, 1, z, 'minecraft:bone_block', axis='y' if (x + z) % 2 else 'x')
    for x, y, z in ((2, 2, 2), (16, 2, 2), (2, 3, 11), (16, 3, 9), (3, 3, 16), (15, 3, 16), (2, 1, 5), (16, 1, 7)):
        b.set(x, y, z, 'minecraft:cobweb')
    for x, z in ((3, 7), (15, 11), (10, 15), (5, 3), (12, 10)):
        b.set(x, 1, z, 'minecraft:moss_carpet')

    # the mausoleum: x 5..13, z 1..6, deepslate with polished corners, a gabled tile roof over the ground
    mx0, mx1, mz0, mz1 = 5, 13, 1, 6
    for x, z in rect_ring(mx0, mz0, mx1, mz1):
        for y in range(1, 10):
            corner = x in (mx0, mx1) and z in (mz0, mz1)
            b.set(x, y, z, 'minecraft:polished_deepslate' if corner else varied(x, y, z, *CRYPT_DEEP))
    for x in range(mx0 + 1, mx1):
        for z in range(mz0 + 1, mz1):
            b.set(x, 0, z, 'minecraft:polished_deepslate')

    def roof_y(z):
        return 9 + min(z, 7 - z)
    for z in range(0, 8):
        for x in range(mx0 - 1, mx1 + 2):
            b.set(x, roof_y(z), z, 'minecraft:deepslate_tile_stairs', **stairs('south' if z <= 3 else 'north'))
    for z in (3, 4):
        for x in range(mx0 - 1, mx1 + 2):
            b.set(x, 13, z, 'minecraft:deepslate_tile_slab', type='bottom', waterlogged=False)
    for x in (mx0, mx1):   # gable ends up to the roof, a round window in each
        for z in range(mz0, mz1 + 1):
            for y in range(1, roof_y(z)):
                b.set(x, y, z, varied(x, y, z, *CRYPT_DEEP))
        b.set(x, 10, 3, 'minecraft:chiseled_deepslate')
        b.set(x, 10, 4, 'minecraft:chiseled_deepslate')
    for x in (mx0, mx1):
        for z in (mz0, mz1):
            for y in range(1, 10):
                b.set(x, y, z, 'minecraft:polished_deepslate')
    # facade: an arched door, barred windows above the ground line, pilasters, a skull over the door
    for x in (8, 9, 10):
        for y in (1, 2):
            b.set(x, y, mz1, 'minecraft:air')
    b.set(9, 3, mz1, 'minecraft:air')
    b.set(8, 3, mz1, 'minecraft:deepslate_brick_stairs', **stairs('west', 'top'))
    b.set(10, 3, mz1, 'minecraft:deepslate_brick_stairs', **stairs('east', 'top'))
    b.set(9, 4, mz1 + 1, 'minecraft:skeleton_wall_skull', facing='south')
    for x in (7, 11):
        for y in (5, 6):
            b.set(x, y, mz1, 'minecraft:iron_bars', north=False, south=False, east=True, west=True, waterlogged=False)
    for x in (6, 12):
        for y in range(1, 9):
            b.set(x, y, mz1 + 1, 'minecraft:polished_deepslate')
        b.set(x, 5, mz1 + 1, 'minecraft:chiseled_deepslate')
    for x in (7, 11):
        b.set(x, 8, mz1 + 1, 'minecraft:deepslate_brick_stairs', **stairs('south', 'top'))
    for x in range(8, 11):
        b.set(x, 8, mz1 + 1, 'minecraft:deepslate_brick_slab', type='top', waterlogged=False)
    # inside: the founder's tomb, two lesser ones, skull shelves in the back wall, a lantern on a chain
    crypt_sarcophagus(b, 8, 1, 3, 'x', 3, skull=True)
    crypt_sarcophagus(b, 6, 1, 4, 'z', 2)
    crypt_sarcophagus(b, 12, 1, 4, 'z', 2)
    for x in (7, 9, 11):
        b.set(x, 2, mz0, 'minecraft:skeleton_skull', rotation=0)
        b.set(x, 3, mz0, 'minecraft:air')
    for x, z in ((6, 2), (12, 2)):
        b.set(x, 1, z, 'minecraft:candle', candles=4, lit=True, waterlogged=False)
    for x, y, z in ((6, 8, 2), (12, 8, 2), (6, 9, 5), (12, 7, 5), (11, 9, 2)):
        b.set(x, y, z, 'minecraft:cobweb')
    crypt_hanging_light(b, 9, 12, 3, 5)

    # the court: sarcophagi either side of the standard, lantern pillars, two flooded cisterns
    standard(b, 9, 1, 9)
    crypt_sarcophagus(b, 5, 1, 9, 'x', 2, skull=True)
    crypt_sarcophagus(b, 12, 1, 9, 'x', 2, skull=True)
    crypt_sarcophagus(b, 3, 1, 3, 'z', 2)
    crypt_sarcophagus(b, 15, 1, 3, 'z', 2)
    for x in (4, 14):
        for y in range(1, sink + 1):
            b.set(x, y, 11, 'minecraft:polished_deepslate')
        b.set(x, sink + 1, 11, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for x0 in (2, 12):
        for x, z in rect_ring(x0, 12, x0 + 4, 16):
            if b.blocks.get((x, 1, z), ('minecraft:air',))[0] == 'minecraft:air':
                b.set(x, 1, z, 'minecraft:deepslate_tile_slab', type='bottom', waterlogged=False)
        for x in range(x0 + 1, x0 + 4):
            for z in range(13, 16):
                b.set(x, 0, z, 'minecraft:water', level=0)
                b.set(x, 1, z, 'minecraft:water', level=0)
        b.set(x0 + 2, 0, 14, 'minecraft:bone_block', axis='y')
        b.set(x0 + 2, 1, 14, 'minecraft:bone_block', axis='y')
        b.set(x0 + 2, 2, 14, 'minecraft:skeleton_skull', rotation=8)
    # the stair down through the south rim, walled in, under an iron-barred arch with skulls
    for x in (8, 9, 10):
        for y, z in ((4, 17), (3, 16), (2, 15), (1, 14)):
            b.set(x, y, z, 'minecraft:stone_brick_stairs', **stairs('south'))
            for above in range(y + 1, 9):
                b.set(x, above, z, 'minecraft:air')
        b.set(x, sink, 18, 'minecraft:dirt_path')
    for x in (7, 11):
        for z in (14, 15, 16, 17):
            for y in range(1, sink + 1):
                b.set(x, y, z, varied(x, y, z, *CRYPT_STONE))
        for z in (14, 15, 16):
            b.set(x, sink + 1, z, 'minecraft:deepslate_brick_wall', up=z == 14, north='low' if z > 14 else 'none',
                  south='low', east='none', west='none', waterlogged=False)
        for y in (5, 6, 7):
            b.set(x, y, 17, 'minecraft:polished_deepslate')
    for x in range(7, 12):
        b.set(x, 8, 17, 'minecraft:deepslate_bricks')
    for x in (8, 9, 10):
        b.set(x, 7, 17, 'minecraft:iron_bars', north=False, south=False, east=x < 10, west=x > 8, waterlogged=False)
    for x in (7, 11):
        b.set(x, 9, 17, 'minecraft:skeleton_skull', rotation=0)
    b.set(9, 9, 17, 'minecraft:deepslate_brick_slab', type='bottom', waterlogged=False)
    keg_wire(b, 7, 13, 3, 'minecraft:polished_deepslate')

    # above ground: a broken balustrade round the pit, obelisks at the far corners, skull posts by the gate
    rail = [(x, z) for x, z in ring(1, 17)
            if not (mx0 <= x <= mx1 and z == mz0) and not (7 <= x <= 11 and z == 17) and (x * 3 + z) % 5 != 0]
    crypt_rail(b, rail, sink + 1, 'minecraft:mossy_stone_brick_wall', 'minecraft:stone_brick_wall',
               'minecraft:cobblestone_wall')
    for x in (1, 17):
        for y in range(sink + 1, sink + 4):
            b.set(x, y, 1, 'minecraft:polished_deepslate')
        b.set(x, sink + 4, 1, 'minecraft:chiseled_deepslate')
        b.set(x, sink + 5, 1, 'minecraft:skeleton_skull', rotation=8)
    for x in (3, 15):
        crypt_skull_post(b, x, sink + 1, 18, h=2)
    for x, z in ((0, 6), (18, 11), (5, 18), (13, 18), (0, 14), (18, 3)):
        b.set(x, sink + 1, z, 'minecraft:dead_bush')
    b.save('sunken_crypt.nbt')


def illager_hall():
    """21x21 raiding hall: a 9x9 great hall (cobble footing, spruce plaster, dark oak frame, stripped-log band, windows,
    gable roof with overhang, the gable end and the 3-wide door facing south) inside a palisade with four roofed corner
    towers and a gabled gate. Core (10, 1, 7), inside the hall. Height 14."""
    n = 21
    b = Builder(n, 14, n)
    fe = dict(north=False, south=False, east=False, west=False, waterlogged=False)
    for x in range(n):
        for z in range(n):
            edge = x in (0, n - 1) or z in (0, n - 1)
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:coarse_dirt') if edge
                  else varied(x, 0, z, 'minecraft:cobblestone', 'minecraft:gravel', 'minecraft:coarse_dirt'))
    fill(b, 0, 1, 0, n - 1, 13, n - 1, 'minecraft:air')
    for z in range(15, 20):
        b.set(10, 0, z, 'minecraft:dirt_path')
    b.set(10, 0, 20, 'minecraft:dirt_path')
    # palisade ring: cobble footing, dark oak logs, a stripped beam on top, tips
    towers = [(1, 1), (17, 1), (1, 17), (17, 17)]
    in_tower = lambda x, z: any(t0 <= x <= t0 + 2 and t1 <= z <= t1 + 2 for t0, t1 in towers)
    for x, z in ring(1, 19):
        if in_tower(x, z) or (z == 19 and 9 <= x <= 11):
            continue
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
        for y in (2, 3):
            b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
        b.set(x, 4, z, 'minecraft:stripped_dark_oak_log', axis='z' if x in (1, 19) else 'x')
        if (x + z) % 2 == 0:
            b.set(x, 5, z, 'minecraft:dark_oak_fence', **fe)
    # corner towers: stone footing, spruce body with log corners, a slit, a pyramid roof and a lantern
    for t0, t1 in towers:
        for x in range(t0, t0 + 3):
            for z in range(t1, t1 + 3):
                corner = x in (t0, t0 + 2) and z in (t1, t1 + 2)
                for y in range(1, 6):
                    if y <= 2:
                        b.set(x, y, z, varied(x, y, z, 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks'))
                    elif corner:
                        b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
                    else:
                        b.set(x, y, z, 'minecraft:spruce_planks')
        cx, cz = t0 + 1, t1 + 1
        ox = t0 if t0 == 1 else t0 + 2
        oz = t1 if t1 == 1 else t1 + 2
        b.set(cx, 4, oz, 'minecraft:iron_bars', **pane(False))
        b.set(ox, 4, cz, 'minecraft:iron_bars', **pane(True))
        fill(b, t0, 6, t1, t0 + 2, 6, t1 + 2, 'minecraft:dark_oak_planks')
        d = 0
        while d < 3:  # roof one wider than the tower on each side (t0-1 may be 0)
            lo_x, hi_x, lo_z, hi_z = t0 - 1 + d, t0 + 3 - d, t1 - 1 + d, t1 + 3 - d
            for x, z in rect_ring(lo_x, lo_z, hi_x, hi_z):
                if lo_x == hi_x or is_corner(x, z, lo_x, hi_x) and lo_z != hi_z or (x in (lo_x, hi_x) and z in (lo_z, hi_z)):
                    b.set(x, 6 + d, z, 'minecraft:dark_oak_planks')
                else:
                    face = 'south' if z == lo_z else 'north' if z == hi_z else 'east' if x == lo_x else 'west'
                    b.set(x, 6 + d, z, 'minecraft:dark_oak_stairs', **stairs(face))
            d += 1
        b.set(cx, 9, cz, 'minecraft:lantern', hanging=False, waterlogged=False)
    # gate: two log posts, a lintel, a small gable roof and lanterns
    for x in (8, 12):
        b.set(x, 1, 19, 'minecraft:mossy_cobblestone')
        for y in range(2, 6):
            b.set(x, y, 19, 'minecraft:dark_oak_log', axis='y')
    for x in range(8, 13):
        b.set(x, 5, 19, 'minecraft:stripped_dark_oak_log', axis='x')
    for x in (9, 10, 11):
        b.set(x, 4, 19, 'minecraft:stripped_dark_oak_log', axis='x')
    for x in range(7, 14):
        b.set(x, 6, 18, 'minecraft:dark_oak_stairs', **stairs('south'))
        b.set(x, 6, 20, 'minecraft:dark_oak_stairs', **stairs('north'))
        b.set(x, 6, 19, 'minecraft:dark_oak_planks')
        b.set(x, 7, 19, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (9, 11):
        b.set(x, 3, 19, 'minecraft:lantern', hanging=True, waterlogged=False)
    # the hall: cobble footing, spruce plaster, dark oak frame, stripped band under the eaves
    hlo, hhi = 6, 14
    for x in range(hlo, hhi + 1):
        for z in range(hlo, hhi + 1):
            edge = x in (hlo, hhi) or z in (hlo, hhi)
            b.set(x, 0, z, 'minecraft:dark_oak_planks' if not edge else 'minecraft:cobblestone')
            if edge:
                post = (x if z in (hlo, hhi) else z) in (6, 10, 14)
                for y in range(1, 5):
                    if y == 4:
                        b.set(x, y, z, 'minecraft:stripped_dark_oak_log', axis='x' if z in (hlo, hhi) else 'z')
                    elif post:
                        b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
                    elif y == 1:
                        b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone_bricks'))
                    else:
                        b.set(x, y, z, 'minecraft:spruce_planks')
    for x in (9, 10, 11):  # hall door on the south face, three wide
        for y in (1, 2, 3):
            b.set(x, y, hhi, 'minecraft:air')
    for x in (9, 11):
        b.set(x, 3, hhi, 'minecraft:air')
    for x, z, ns in ((hlo, 8, True), (hlo, 12, True), (hhi, 8, True), (hhi, 12, True), (8, hlo, False), (12, hlo, False)):
        b.set(x, 3, z, 'minecraft:iron_bars', **pane(ns))
        b.set(x, 2, z, 'minecraft:spruce_planks')
    # pillars inside and gray banners flanking the door
    for x in (8, 12):
        for z in (7, 10):
            for y in (1, 2, 3):
                b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
    b.set(8, 3, 15, 'minecraft:gray_wall_banner', facing='south')
    b.set(12, 3, 15, 'minecraft:gray_wall_banner', facing='south')
    # gable roof, ridge north-south so the gable end and the door face south; eaves overhang by one
    for step in range(6):
        y = 5 + step
        xw, xe = 5 + step, 15 - step
        for z in range(5, 16):
            if xw == xe:
                b.set(xw, y, z, 'minecraft:dark_oak_slab' if step == 5 else 'minecraft:dark_oak_planks', **(
                    dict(type='bottom', waterlogged=False) if step == 5 else {}))
            else:
                b.set(xw, y, z, 'minecraft:dark_oak_stairs', **stairs('east'))
                b.set(xe, y, z, 'minecraft:dark_oak_stairs', **stairs('west'))
        if step and xw < xe:
            for z in (hlo, hhi):
                for x in range(xw + 1, xe):
                    b.set(x, y, z, 'minecraft:spruce_planks')
    for z in (hlo, hhi):
        for y in range(5, 11):
            b.set(10, y, z, 'minecraft:dark_oak_log', axis='y')
    for y in (7, 8):  # gable windows either side of the king post
        for x in (9, 11) if y == 7 else ():
            b.set(x, y, hhi, 'minecraft:iron_bars', **pane(False))
    standard(b, 10, 1, 7)
    for x, z in ((6, 16), (14, 16)):
        b.set(x, 1, z, 'minecraft:campfire', lit=True, signal_fire=False, facing='north', waterlogged=False)
    for x, z in ((3, 16), (17, 16)):
        b.set(x, 1, z, 'minecraft:target')
    for x in (5, 15):
        b.set(x, 1, 17, 'minecraft:hay_block', axis='y')
    alarm(b, 10, 18)
    spikes(b, (9, 16), (12, 16))  # (11, 16) is the keg's cell
    arrow_slit(b, 8, 17, 3, 'minecraft:dark_oak_log')
    _tripwire(b, 8, 15, 3, 'minecraft:cobblestone')
    b.set(11, 1, 16, 'warbands:powder_keg')  # beside the inner wire's hook (the hall door is at z=14)
    b.save('illager_hall.nbt')


def lint_town_buildings():
    """Builds every town building in memory and lints it (python3 gen_structures.py --lint)."""
    import lint_structures
    caught = {}
    real = Builder.save
    Builder.save = lambda self, filename: caught.__setitem__(filename, self)
    try:
        import camp_props
        run_all()
        for filename, b in caught.items():
            camp_props.add(b, dict(kind=filename, id=filename, guards=[], spares=[], core=(0, 0, 0)))
    finally:
        Builder.save = real
    bad = 0
    for filename, b in caught.items():
        problems = lint_structures.lint_building(b, filename)
        for p in problems:
            print(f'LINT {filename}: {p}')
        bad += bool(problems)
    print(f'lint: {len(caught) - bad}/{len(caught)} town buildings clean')
    return bad


def run_all():
    town_center('town_center.nbt', VILLAGE)
    town_center('town_center_feudal.nbt', FEUDAL)
    town_center('town_center_castle.nbt', CASTLE)
    house()
    barracks()
    archery_range()
    blacksmith()
    stable()
    watch_tower()
    siege_workshop()


def run_all_with_props():
    """Town buildings, each with its props placed just before it is saved (see camp_props.py)."""
    import camp_props
    real = Builder.save

    def save(self, filename):
        camp_props.add(self, dict(kind=filename, id=filename, guards=[], spares=[], core=(0, 0, 0)))
        real(self, filename)

    Builder.save = save
    try:
        run_all()
    finally:
        Builder.save = real


if __name__ == "__main__":
    import sys
    if "--lint" in sys.argv:
        sys.exit(1 if lint_town_buildings() else 0)
    run_all_with_props()
