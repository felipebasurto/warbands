"""Feudal Age camp layouts (second and third designs of each kind). Run through gen_camps.py."""
import math

import gen_structures as g

from gen_camps import (Camp, fill, ring, circle, disc, wall, crenels, tower, log, fence, lantern, campfire, fence_gate,
                       ladder, slab, walkway, tent, gallery, piston_gate, sculk, alarm, spikes, pitfall, tripwire,
                       keg_wire, rigged_floor, ammo, hip_roof, stairs, varied)

MUD_ROOF = ('minecraft:mud_brick_stairs', 'minecraft:mud_brick_slab', 'minecraft:mangrove_planks')
SPRUCE_ROOF = ('minecraft:spruce_stairs', 'minecraft:spruce_slab', 'minecraft:spruce_planks')
DARK_ROOF = ('minecraft:dark_oak_stairs', 'minecraft:dark_oak_slab', 'minecraft:dark_oak_planks')
STONE = ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks')
COBBLE = ('minecraft:cobblestone', 'minecraft:stone_bricks', 'minecraft:mossy_cobblestone')


def square_walls(b, lo, hi, top, blocks, crenel):
    wall(b, list(ring(lo, hi)), 1, top, *blocks)
    crenels(b, list(ring(lo, hi)), top + 1, crenel)


def _age(b):
    """Moss and cracks on stone bricks and cobble: mossier near the ground, so the walls look lived in."""
    for (x, y, z), (name, props) in list(b.blocks.items()):
        n = (x * 73856093 ^ y * 19349663 ^ z * 83492791) & 0xFFFF
        if name == 'minecraft:stone_bricks':
            if n % (3 if y <= 2 else 6) == 0:
                b.blocks[(x, y, z)] = ('minecraft:mossy_stone_bricks', props)
            elif n % 7 == 1:
                b.blocks[(x, y, z)] = ('minecraft:cracked_stone_bricks', props)
        elif name == 'minecraft:cobblestone' and n % (3 if y <= 2 else 5) == 0:
            b.blocks[(x, y, z)] = ('minecraft:mossy_cobblestone', props)


def _hall(b, x0, z0, x1, z1, door, inside=(), body='minecraft:spruce_planks', frame='minecraft:dark_oak_log',
          stair='minecraft:dark_oak_stairs', top='minecraft:dark_oak_slab'):
    """A roofed timber hall on a stone footing: log posts at the corners, plank walls, a hipped roof that overhangs by
    one block, a door cell (x, z) and a few props inside. Needs one free block all round for the eaves."""
    for x, z in g.rect_ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        b.set(x, 1, z, 'minecraft:stone_bricks')
        for y in (2, 3):
            if corner:
                log(b, x, y, z, frame)
            else:
                b.set(x, y, z, body)
        if corner:
            log(b, x, 1, z, frame)
            log(b, x, 4, z, frame)
        else:
            b.set(x, 4, z, 'minecraft:stripped_dark_oak_log', axis='x' if z in (z0, z1) else 'z')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            b.set(x, 0, z, 'minecraft:spruce_planks')
            b.set(x, 4, z, body)
    for x, z in g.rect_ring(x0, z0, x1, z1):       # shuttered window slits on the long faces
        if (x + z) % 3 == 0 and not (x in (x0, x1) and z in (z0, z1)) and (x, z) != door:
            b.set(x, 2, z, 'minecraft:glass_pane', **g.pane(z not in (z0, z1)))
    for y in (1, 2):
        b.set(door[0], y, door[1], 'minecraft:air')
    g.bandit_hip_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 4, stair, top)
    for (x, z), what in inside:
        if what == 'lantern':
            lantern(b, x, 3, z, hanging=True)
        else:
            b.set(x, 1, z, what)


def _yard(b, n, lo, hi, keep=()):
    """Grass, moss and gravel patches over the bare dirt, ferns and tufts at the wall feet and outside the wall."""
    free = set(keep) | {(g_[2], g_[4]) for g_ in b.guards} | {(sx, sz) for sx, _, sz in b.spares}
    for (x, y, z) in b.blocks:
        if y == 1:
            free.add((x, z))
    for x in range(n):
        for z in range(n):
            h = (x * 73856093 ^ z * 83492791 ^ 5) & 0xFFFF
            outside = not (lo <= x <= hi and lo <= z <= hi)
            near = (lo < x < hi and lo < z < hi) and (x in (lo + 1, hi - 1) or z in (lo + 1, hi - 1))
            if b.block(x, 0, z) == 'minecraft:polished_andesite' or 'planks' in b.block(x, 0, z):
                continue
            if outside or near:
                b.set(x, 0, z, 'minecraft:moss_block' if h % 5 == 0 else 'minecraft:grass_block' if h % 5 < 4
                      else 'minecraft:coarse_dirt')
                if (x, z) not in free and h % 3 == 0:
                    b.set(x, 1, z, 'minecraft:fern' if h % 2 else 'minecraft:short_grass')
                    if b.block(x, 0, z) == 'minecraft:coarse_dirt':
                        b.set(x, 0, z, 'minecraft:grass_block')
            elif h % 9 == 0:
                b.set(x, 0, z, 'minecraft:grass_block' if h % 2 else 'minecraft:moss_block')
            elif h % 6 == 0:
                b.set(x, 0, z, 'minecraft:gravel')


def _gate_dress(b, x0, x1, z, bcolor='red'):
    """Timber jambs, hanging lanterns and two wall banners on the outside of a gate wall (gate cells x0..x1)."""
    for x in (x0 - 1, x1 + 1):
        for y in (1, 2, 3):
            log(b, x, y, z, 'minecraft:dark_oak_log')
        b.set(x, 5, z + 1, f'minecraft:{bcolor}_wall_banner', facing='south')
        b.set(x, 4, z + 1, f'minecraft:{bcolor}_wall_banner', facing='south')
    for x in range(x0 - 1, x1 + 2):
        log(b, x, 3, z, 'minecraft:dark_oak_log', 'x')


def fort_barbican():
    """27x27 fort whose gate opens into a walled barbican: a kill court with two guns a side, and an inner portcullis
    that drops when the alarm sounds. A breach in the west wall is the quiet way in, past two sculk sensors."""
    n = 27
    b = Camp('FORT', 'fort_barbican', n, 13)
    b.ground('minecraft:coarse_dirt', 'minecraft:gravel', 'minecraft:dirt_path')
    square_walls(b, 1, 25, 6, STONE, 'minecraft:stone_bricks')
    for x in (12, 13, 14):  # outer gate
        fence_gate(b, x, 1, 25)
        fence_gate(b, x, 2, 25)
        log(b, x, 3, 25, 'minecraft:dark_oak_log', 'x')
    corners = [tower(b, x0, z0, 8, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab')
               for x0, z0 in ((1, 1), (23, 1), (1, 23), (23, 23))]
    # the barbican: side walls x=9 and x=17 from the gate wall to z=18, inner wall z=18 with the portcullis
    wall(b, [(9, z) for z in range(18, 25)] + [(17, z) for z in range(18, 25)] + [(x, 18) for x in range(9, 18)],
         1, 6, *STONE)
    crenels(b, [(9, z) for z in range(18, 25)] + [(17, z) for z in range(18, 25)], 7, 'minecraft:stone_bricks')
    piston_gate(b, 12, 18)
    for x in (12, 13):
        b.set(x, 3, 18, 'minecraft:stone_bricks')
    for z in (20, 23):
        gallery(b, 9, 2, z, 'east')
        gallery(b, 17, 2, z, 'west')
    alarm(b, 13, 24)
    spikes(b, (11, 22), (15, 22), (13, 21))
    # the breach: a slumped stretch of the west wall, one block high, with rubble and sculk sensors behind it
    for z in (11, 12, 13):
        for y in range(2, 8):
            b.set(1, y, z, 'minecraft:air')
        b.set(1, 1, z, 'minecraft:cobblestone')
        b.set(2, 1, z, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
    b.set(0, 1, 12, 'minecraft:mossy_cobblestone_slab', type='bottom', waterlogged=False)
    sculk(b, 4, 10)
    sculk(b, 4, 14)
    # main yard: command post, tents, dummies, fires
    for x in range(11, 16):
        for z in range(4, 9):
            b.set(x, 0, z, 'minecraft:polished_andesite')
    b.standard(13, 6)
    for x in (11, 15):
        fence(b, x, 1, 6, 'minecraft:dark_oak_fence')
        fence(b, x, 2, 6, 'minecraft:dark_oak_fence')
        lantern(b, x, 3, 6)
    _hall(b, 4, 3, 8, 7, (6, 7), [((5, 4), 'minecraft:barrel'), ((7, 4), 'minecraft:crafting_table'),
                                  ((6, 5), 'lantern')])
    _hall(b, 17, 3, 21, 7, (19, 7), [((18, 4), 'minecraft:barrel'), ((20, 4), 'minecraft:hay_block'),
                                     ((19, 5), 'lantern')])
    _hall(b, 19, 11, 23, 15, (19, 13), [((22, 12), 'minecraft:anvil'), ((22, 14), 'minecraft:grindstone'),
                                        ((21, 13), 'lantern')])
    mid = tower(b, 12, 1, 8, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab')
    _gate_dress(b, 12, 14, 25)
    for x, z in ((6, 17), (20, 17)):
        campfire(b, x, z)
    for x in (5, 21):
        b.set(x, 1, 20, 'minecraft:target')
    for z in (19, 20, 21):
        b.set(3, 1, z, 'minecraft:hay_block', axis='y')
    for x, z in ((13, 10), (10, 12), (16, 12), (13, 14)):
        b.unit('militia', x, z)
    for x, z in ((11, 16), (15, 16), (7, 8), (19, 8)):
        b.unit('spearman', x, z)
    for x, z in ((6, 14), (18, 14)):
        b.unit('crossbowman', x, z)
    for x, z in ((12, 21), (14, 21)):
        b.unit('spearman', x, z)
    for spot in corners:
        b.post('archer', *spot)
    b.post('archer', *mid)
    b.post('archer', 8, 7, 1)
    b.post('archer', 18, 7, 1)
    b.spare((9, 4), (17, 8), (4, 18), (22, 20))
    _yard(b, n, 1, 25)
    _age(b)
    b.report('The gate opens into a walled barbican: two guns a side and a portcullis that drops when the alarm '
             'sounds. Smash the red relays in its walls, or climb through the breach in the west wall, past two '
             'sculk sensors (sneak).',
             'La puerta da a una barbacana amurallada: dos cañones por lado y un rastrillo que cae cuando suena la '
             'alarma. Rompe los relés rojos de sus muros, o entra por la brecha del muro oeste, pasando dos sensores '
             'de sculk (agachado).')
    b.done()


def fort_twin():
    """31x31 fort of two wards: the gate leads into the outer (south) ward; a cross wall with guns and a portcullis
    guards the inner ward and the standard. A postern in the north wall leads straight into the inner ward."""
    n = 31
    b = Camp('FORT', 'fort_twin', n, 13)
    b.ground('minecraft:coarse_dirt', 'minecraft:gravel', 'minecraft:dirt_path')
    square_walls(b, 1, 29, 6, STONE, 'minecraft:stone_bricks')
    cross = [(x, 15) for x in range(1, 30)]
    wall(b, cross, 1, 6, *STONE)
    crenels(b, cross, 7, 'minecraft:stone_bricks')
    for x in (14, 15, 16):
        fence_gate(b, x, 1, 29)
        fence_gate(b, x, 2, 29)
        log(b, x, 3, 29, 'minecraft:dark_oak_log', 'x')
    piston_gate(b, 14, 15)
    for x in (14, 15):
        b.set(x, 3, 15, 'minecraft:stone_bricks')
    for x in (6, 9, 21, 24):
        gallery(b, x, 2, 15, 'south')
    corners = [tower(b, x0, z0, 8, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab')
               for x0, z0 in ((1, 1), (27, 1), (1, 27), (27, 27))]
    mids = [tower(b, x0, 14, 8, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab') for x0 in (1, 27)]
    # Both wards need a short way out when the alarm closes the central portcullis.
    for x in (1, 29):
        for z in (12, 13, 17, 18):
            for y in (1, 2):
                fence_gate(b, x, y, z, facing='east')
    # the postern: a one-wide door in the north wall, behind the inner ward's barracks, with sculk sensors
    for y in (1, 2):
        b.set(22, y, 1, 'minecraft:air')
    sculk(b, 20, 3)
    sculk(b, 24, 3)
    # outer ward
    # outer ward: the muster ground. Two barracks, a firing line of hay targets, cook fires
    _hall(b, 4, 21, 8, 25, (8, 23), [((5, 22), 'minecraft:barrel'), ((7, 22), 'minecraft:hay_block'),
                                     ((6, 24), 'lantern')])
    _hall(b, 22, 21, 26, 25, (22, 23), [((23, 22), 'minecraft:barrel'), ((25, 22), 'minecraft:hay_block'),
                                        ((24, 24), 'lantern')])
    for x in (6, 7, 8, 22, 23, 24):
        b.set(x, 1, 18, 'minecraft:hay_block', axis='y')
        b.set(x, 2, 18, 'minecraft:target')
    for x in (12, 18):
        campfire(b, x, 22)
    for x in (10, 20):
        fence(b, x, 1, 19, 'minecraft:dark_oak_fence')
        fence(b, x, 2, 19, 'minecraft:dark_oak_fence')
        lantern(b, x, 3, 19)
    for x in (11, 19):
        tower(b, x - 1, 27, 8, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab')
    _gate_dress(b, 14, 16, 29)
    alarm(b, 15, 28)
    spikes(b, (13, 27), (17, 27), (15, 25))
    tripwire(b, 11, 26, 7, 'minecraft:stone_bricks')
    b.set(11, 1, 26, 'minecraft:dispenser', facing='east', triggered=False, nbt=ammo('minecraft:arrow', 9))
    # inner ward
    for x in range(12, 19):
        for z in range(4, 9):
            b.set(x, 0, z, 'minecraft:polished_andesite')
    b.standard(15, 6)
    for x in (13, 17):
        fence(b, x, 1, 6, 'minecraft:dark_oak_fence')
        lantern(b, x, 2, 6)
    # inner ward: the keep. A great hall and an armory either side of the standard
    _hall(b, 5, 5, 9, 9, (9, 7), [((6, 6), 'minecraft:barrel'), ((8, 6), 'minecraft:crafting_table'),
                                  ((6, 8), 'minecraft:chest'), ((7, 7), 'lantern')])
    _hall(b, 21, 5, 25, 9, (21, 7), [((24, 6), 'minecraft:anvil'), ((24, 8), 'minecraft:grindstone'),
                                     ((22, 6), 'minecraft:smithing_table'), ((23, 7), 'lantern')])
    for x, z in ((4, 12), (26, 12)):
        b.set(x, 1, z, 'minecraft:hay_block', axis='y')
    for x in (8, 22):
        b.set(x, 1, 10, 'minecraft:target')
    for x, z in ((11, 19), (19, 19), (15, 22), (15, 19)):
        b.unit('militia', x, z)
    for x, z in ((12, 17), (18, 17), (12, 24), (18, 24)):
        b.unit('spearman', x, z)
    for x, z in ((10, 8), (20, 8)):
        b.unit('crossbowman', x, z)
    for x, z in ((13, 11), (17, 11)):
        b.unit('spearman', x, z)
    b.unit('militia', 15, 12)
    for spot in corners:
        b.post('archer', *spot)
    for spot in mids:
        b.post('crossbowman', *spot)
    b.post('crossbowman', 11, 9, 28)
    b.post('crossbowman', 19, 9, 28)
    b.spare((9, 19), (21, 19), (10, 12), (20, 12))
    _yard(b, n, 1, 29)
    _age(b)
    b.report('Two wards. A cross wall with four guns and a portcullis guards the inner one. Side gates in both wards '
             'open when the alarm sounds: lead your troops around the wall through them. A small north postern '
             'leads into the inner ward, past sculk sensors.',
             'Dos recintos. La puerta da al recinto exterior; un muro con cuatro cañones y un rastrillo guarda el '
             'interior. Las puertas laterales de ambos recintos se abren con la alarma: lleva a tus tropas por ellas '
             'para rodear el muro. Un portillo norte lleva al interior, pasando sensores de sculk.')
    b.done()


def quarry_terraces():
    """29x29 stepped quarry hill: an outer ring, a terrace four up and a top terrace seven up with the standard,
    switchback ramps, guns in the retaining walls and a keg wire on the upper walkway. Crossbows up top."""
    n = 29
    b = Camp('QUARRY_STRONGHOLD', 'quarry_terraces', n, 15)
    b.ground('minecraft:stone', 'minecraft:cobblestone', 'minecraft:gravel')
    square_walls(b, 1, 27, 4, COBBLE, 'minecraft:cobblestone')
    for x in (13, 14, 15):
        fence_gate(b, x, 1, 27)
        fence_gate(b, x, 2, 27)
        log(b, x, 3, 27, 'minecraft:dark_oak_log', 'x')
    front = [tower(b, x0, 24, 7, 'minecraft:cobblestone', 'minecraft:cobblestone_slab') for x0 in (1, 25)]
    # terrace 1: 6..22, top block y3 (walk at y4); terrace 2: 10..18, top y6 (walk at y7)
    for x in range(6, 23):
        for z in range(6, 23):
            edge = x in (6, 22) or z in (6, 22)
            for y in range(1, 4):
                b.set(x, y, z, varied(x, y, z, *STONE) if edge else 'minecraft:stone')
            b.set(x, 3, z, 'minecraft:stone_bricks' if edge else varied(x, 3, z, 'minecraft:gravel', 'minecraft:cobblestone'))
    for x in range(10, 19):
        for z in range(10, 19):
            edge = x in (10, 18) or z in (10, 18)
            for y in range(4, 7):
                b.set(x, y, z, varied(x, y, z, *STONE) if edge else 'minecraft:stone')
            b.set(x, 6, z, 'minecraft:polished_andesite' if not edge else 'minecraft:stone_bricks')
    # ramp 1: south face of terrace 1 (x 7..9), climbing north
    for x in (7, 8, 9):
        for i, (y, z) in enumerate(((1, 22), (2, 21), (3, 20))):
            b.set(x, y, z, 'minecraft:stone_brick_stairs', **stairs('north'))
            for above in range(y + 1, 4):
                b.set(x, above, z, 'minecraft:air')
    # ramp 2: north face of terrace 2 (x 16..18), climbing south
    for x in (16, 17, 18):
        for y, z in ((4, 10), (5, 11), (6, 12)):
            b.set(x, y, z, 'minecraft:stone_brick_stairs', **stairs('south'))
            for above in range(y + 1, 7):
                b.set(x, above, z, 'minecraft:air')
    # guns: terrace 1's south wall covers the outer ring; terrace 2's west wall covers the upper walkway
    for x in (13, 16, 19):
        gallery(b, x, 2, 22, 'south')
    for z in (13, 16):
        gallery(b, 10, 5, z, 'west')
    keg_wire(b, 11, 8, 3, 'minecraft:cobblestone', y=4)
    spikes(b, (19, 20), (20, 19), y=4)
    # top: crane, ore heaps, the standard
    b.standard(14, 15, 7)
    for x in (12, 16):
        for y in (7, 8, 9):
            fence(b, x, y, 13, 'minecraft:dark_oak_fence')
    for x in range(12, 17):
        log(b, x, 10, 13, 'minecraft:dark_oak_log', 'x')
    b.set(14, 9, 13, 'minecraft:iron_chain', axis='y', waterlogged=False)
    b.set(14, 8, 13, 'minecraft:iron_chain', axis='y', waterlogged=False)
    for x, z in ((3, 6), (4, 7), (24, 6), (23, 18)):
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:iron_ore', 'minecraft:coal_ore', 'minecraft:cobblestone'))
    # dark timber posts in the outer wall, a lookout tower, scaffolding, a mine-cart line into a timbered mouth
    for i in (5, 9, 17, 21):
        for y in (1, 2, 3, 4):
            log(b, i, y, 27, 'minecraft:dark_oak_log')
    for i in (9, 13, 17, 21):
        for y in (1, 2, 3, 4):
            log(b, 27, y, i, 'minecraft:dark_oak_log')
            log(b, 1, y, i, 'minecraft:dark_oak_log')
    for x in (9, 17, 21):
        for y in (1, 2, 3, 4):
            log(b, x, y, 1, 'minecraft:dark_oak_log')
    for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
        for y in range(1, 11):
            log(b, x, y, z, 'minecraft:dark_oak_log')
    for y in range(1, 8):
        b.set(3, y, 4, 'minecraft:spruce_planks')
    for x in (2, 3, 4):
        for z in (2, 3, 4):
            b.set(x, 7, z, 'minecraft:spruce_planks')
    ladder(b, 3, 5, 1, 7, 'south')
    for x, z in ((3, 2), (2, 3), (4, 3)):
        fence(b, x, 8, z, 'minecraft:dark_oak_fence')
    for x in range(1, 6):
        for z in range(1, 6):
            slab(b, x, 11, z, 'minecraft:dark_oak_slab')
    for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
        b.set(x, 11, z, 'minecraft:dark_oak_planks')
    lantern(b, 3, 10, 3, hanging=True)
    b.post('crossbowman', 3, 8, 3)
    for x in (17, 21):                      # scaffolding on the terrace's south face
        for y in (1, 2, 3):
            fence(b, x, y, 23, 'minecraft:spruce_fence')
    for x in (17, 18, 20, 21):
        b.set(x, 3, 23, 'minecraft:spruce_planks')
    for y in (1, 2, 3):
        b.set(19, y, 23, 'minecraft:ladder', facing='south', waterlogged=False)
    for z in (8, 13):                       # and its east face
        for y in (1, 2, 3):
            fence(b, 23, y, z, 'minecraft:spruce_fence')
    for z in (9, 10, 11, 12):
        b.set(23, 3, z, 'minecraft:spruce_planks')
    for z in range(3, 24):
        if True:
            b.set(25, 1, z, 'minecraft:rail', shape='north_south', waterlogged=False)
    for x in (24, 26):
        for y in (1, 2, 3):
            log(b, x, y, 2, 'minecraft:dark_oak_log')
    for x in (24, 25, 26):
        log(b, x, 4, 2, 'minecraft:dark_oak_log', 'x')
    for y in (1, 2, 3):
        b.set(25, y, 2, 'minecraft:blackstone')
    b.set(25, 1, 24, 'minecraft:stone_bricks')
    for x, y, z in ((26, 1, 9), (26, 1, 10), (26, 2, 9), (24, 1, 22)):
        b.set(x, y, z, varied(x, y, z, 'minecraft:iron_ore', 'minecraft:coal_ore'))
    for x, z in ((3, 24), (8, 25), (20, 25), (5, 26), (26, 12)):
        b.set(x, 1, z, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
    alarm(b, 14, 26)
    spikes(b, (12, 25), (16, 25))
    campfire(b, 4, 20)
    campfire(b, 24, 20)
    for x, z in ((14, 24), (4, 14), (24, 14)):
        b.unit('militia', x, z)
    b.unit('spearman', 10, 24)
    b.unit('spearman', 18, 24)
    for x, z in ((8, 15), (20, 15), (14, 20), (19, 7)):
        b.unit('crossbowman', x, z, y=4)
    b.unit('spearman', 12, 16, y=7)
    b.unit('spearman', 16, 16, y=7)
    b.post('crossbowman', 11, 7, 11)
    b.post('crossbowman', 17, 7, 17)
    for spot in front:
        b.post('crossbowman', *spot)
    b.spare((4, 10), (24, 10), y=1)
    b.spare((8, 8), (20, 20), y=4)
    b.report('A stepped quarry hill: two terraces, switchback ramps, guns in the retaining walls and a keg wire on the '
             'upper walkway. Crossbows up top, so keep low and keep moving.',
             'Una colina de cantera escalonada: dos terrazas, rampas en zigzag, cañones en los muros de contención y '
             'un cable con barril en el paseo superior. Ballestas arriba: mantente bajo y en movimiento.')
    _age(b)
    b.done()


def quarry_minehead():
    """25x25 mine head: a tall headframe in the middle with crossbows on top and four guns at its foot, a rail
    yard, and a powder magazine wired with tripwires you don't want to fight next to."""
    n = 25
    b = Camp('QUARRY_STRONGHOLD', 'quarry_minehead', n, 17)
    b.ground('minecraft:stone', 'minecraft:gravel', 'minecraft:cobblestone')
    square_walls(b, 1, 23, 5, COBBLE, 'minecraft:cobblestone')
    for x in (11, 12, 13):
        fence_gate(b, x, 1, 23)
        fence_gate(b, x, 2, 23)
        log(b, x, 3, 23, 'minecraft:dark_oak_log', 'x')
    front = [tower(b, x0, 20, 7, 'minecraft:cobblestone', 'minecraft:cobblestone_slab') for x0 in (1, 21)]
    # the headframe: 5x5 of stone and timber, 13 tall, a 3x3 fighting top
    fill(b, 10, 1, 10, 14, 12, 14, 'minecraft:stone_bricks')
    for y in range(1, 13):
        for x, z in ((10, 10), (14, 10), (10, 14), (14, 14)):
            log(b, x, y, z, 'minecraft:stripped_spruce_log')
    for x, z in ring(10, 14):
        slab(b, x, 13, z, 'minecraft:spruce_slab')
    for x in range(11, 14):
        for z in range(11, 14):
            b.set(x, 13, z, 'minecraft:air')
    for y in (14, 15):
        for x, z in ((10, 10), (14, 10), (10, 14), (14, 14)):
            fence(b, x, y, z, 'minecraft:spruce_fence')
    for x in range(10, 15):
        for z in range(10, 15):
            b.set(x, 16, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    gallery(b, 12, 2, 10, 'north')
    gallery(b, 12, 2, 14, 'south')
    gallery(b, 10, 2, 12, 'west')
    gallery(b, 14, 2, 12, 'east')
    # rails round the headframe
    for x, z in ring(8, 16):
        if not (x in (8, 16) and z in (8, 16)):
            b.set(x, 1, z, 'minecraft:rail', shape='north_south' if x in (8, 16) else 'east_west', waterlogged=False)
    for x, z, shape in ((8, 8, 'south_east'), (16, 8, 'south_west'), (8, 16, 'north_east'), (16, 16, 'north_west')):
        b.set(x, 1, z, 'minecraft:rail', shape=shape, waterlogged=False)
    # powder magazine, north-west: kegs in a stone hut, tripwire across its doorway
    for x in range(3, 8):
        for z in range(3, 8):
            edge = x in (3, 7) or z in (3, 7)
            if edge:
                for y in (1, 2, 3):
                    b.set(x, y, z, 'minecraft:cobblestone')
            b.set(x, 4, z, 'minecraft:cobblestone_slab', type='bottom', waterlogged=False)
    for x, z in ((4, 4), (5, 4), (6, 4), (4, 5)):
        b.set(x, 1, z, 'warbands:powder_keg')
    for y in (1, 2):
        b.set(5, y, 7, 'minecraft:air')
    keg_wire(b, 4, 9, 1, 'minecraft:cobblestone')
    b.set(5, 1, 8, 'warbands:powder_keg')
    # the standard at the mine mouth (north), ore heaps
    b.standard(18, 4)
    for x in range(16, 21):
        for y in (1, 2, 3):
            b.set(x, y, 2, 'minecraft:stone_bricks')
        b.set(x, 0, 3, 'minecraft:polished_andesite')
    for x in (17, 18, 19):
        for y in (1, 2):
            b.set(x, y, 2, 'minecraft:air')
    b.set(18, 1, 1, 'minecraft:coal_ore')
    for x, z in ((20, 8), (21, 9), (3, 14), (4, 16)):
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:iron_ore', 'minecraft:coal_ore', 'minecraft:gravel'))
    # dark timber posts in the wall, lantern posts at the gate
    for i in (5, 9, 15, 19):
        for y in (1, 2, 3, 4):
            log(b, 1, y, i, 'minecraft:dark_oak_log')
            log(b, 23, y, i, 'minecraft:dark_oak_log')
    for i in (7, 16):
        for y in (1, 2, 3, 4):
            log(b, i, y, 1, 'minecraft:dark_oak_log')
    for x in (9, 15):
        for y in (1, 2, 3, 4):
            log(b, x, y, 23, 'minecraft:dark_oak_log')
    for x in (10, 14):
        for y in (1, 2):
            fence(b, x, y, 22, 'minecraft:dark_oak_fence')
        lantern(b, x, 3, 22)
    # shaft cover: a planked lid with a hatch, fenced on two sides
    for x in range(19, 22):
        for z in range(11, 14):
            b.set(x, 1, z, 'minecraft:spruce_planks')
    b.set(20, 1, 12, 'minecraft:spruce_trapdoor', facing='north', half='bottom', open=False, powered=False,
          waterlogged=False)
    b.set(20, 1, 12, 'minecraft:barrel', facing='up', open=False)
    for x in (19, 21):
        fence(b, x, 2, 13, 'minecraft:dark_oak_fence')
    # spoil heaps and ore piles
    for (hx, hz, mat) in ((2, 15, 'minecraft:gravel'), (21, 7, 'minecraft:cobblestone')):
        for x in range(hx, hx + 3):
            for z in range(hz, hz + 3):
                b.set(x, 1, z, mat)
        for x, z in ((hx, hz), (hx + 1, hz), (hx, hz + 1), (hx + 1, hz + 1)):
            b.set(x, 2, z, varied(x, 2, z, mat, 'minecraft:coal_ore', 'minecraft:iron_ore'))
        b.set(hx, 3, hz, mat)
    for x, y, z in ((6, 1, 15), (7, 1, 15), (6, 1, 16), (6, 2, 15), (18, 1, 15), (18, 1, 16), (19, 1, 16)):
        b.set(x, y, z, varied(x, y, z, 'minecraft:raw_iron_block', 'minecraft:coal_block', 'minecraft:raw_copper_block'))
    alarm(b, 12, 22)
    spikes(b, (10, 20), (14, 20))
    campfire(b, 6, 19)
    campfire(b, 18, 19)
    for x, z in ((6, 17), (18, 17), (12, 19)):
        b.unit('militia', x, z)
    for x, z in ((6, 12), (18, 12), (16, 6), (20, 6)):
        b.unit('spearman', x, z)
    b.unit('crossbowman', 3, 12)
    b.unit('crossbowman', 21, 14)
    b.post('crossbowman', 11, 13, 11)
    b.post('crossbowman', 13, 13, 13)
    for spot in front:
        b.post('crossbowman', *spot)
    b.spare((9, 5), (15, 5), (4, 19), (20, 19))
    b.report('A mine head: a tall headframe in the middle with crossbows on top and four guns at its foot, and a '
             'powder magazine north-west that goes up if its tripwire is crossed.',
             'Una bocamina: un castillete alto en el centro con ballestas arriba y cuatro cañones al pie, y un polvorín '
             'al noroeste que salta si se cruza su cable trampa.')
    _age(b)
    b.done()


def _gable_roof(b, x0, z0, x1, z1, y, along, stair, slab, plank):
    """Gabled roof over x0..x1, z0..z1 with the ridge running {@code along} ('x' or 'z'), one block of eaves all round
    and the gable ends filled with planks. Walls should reach y-1."""
    if along == 'z':
        flip = True
        a0, a1, c0, c1 = x0, x1, z0, z1
    else:
        flip = False
        a0, a1, c0, c1 = z0, z1, x0, x1     # a = the span across the slope, c = along the ridge

    def put(c, a, yy, blk, **p):
        (b.set(a, yy, c, blk, **p) if flip else b.set(c, yy, a, blk, **p))
    d = 0
    while a0 - 1 + d <= a1 + 1 - d:
        lo, hi = a0 - 1 + d, a1 + 1 - d
        for c in range(c0 - 1, c1 + 2):
            if lo == hi:
                put(c, lo, y + d, slab, type='bottom', waterlogged=False)
            else:
                lo_face = ('east' if flip else 'south')
                hi_face = ('west' if flip else 'north')
                put(c, lo, y + d, stair, **stairs(lo_face))
                put(c, hi, y + d, stair, **stairs(hi_face))
        for c in (c0, c1):              # gable ends
            for a in range(lo + 1, hi):
                put(c, a, y + d, plank)
        d += 1


def coven_stilts():
    """25x25 stilt village over a bog: a big central hut and three smaller ones on stilts, joined by plank walkways
    whose planks drop when the alarm sounds, guns with Slowness arrows facing the stairs, three witches."""
    n = 25
    b = Camp('WITCH_COVEN', 'coven_stilts', n, 12)
    b.ground('minecraft:mud', 'minecraft:muddy_mangrove_roots', 'minecraft:coarse_dirt')
    for x, z in [(x, z) for x in range(2, 23) for z in range(2, 23)]:
        if (x * 7 + z * 13) % 11 == 0 and not (8 <= x <= 16 and 8 <= z <= 23) and z > 1:
            b.set(x, 0, z, 'minecraft:water', level=0)
    for x, z in ring(1, 23):
        if (x + z) % 3:
            b.set(x, 1, z, 'minecraft:mangrove_roots', waterlogged=False)
            b.set(x, 2, z, 'minecraft:mangrove_roots', waterlogged=False)
    for x in (11, 12, 13):
        for y in (1, 2):
            b.set(x, y, 23, 'minecraft:air')

    def hut(x0, z0, size, door, along='x', roof=MUD_ROOF, chimney=False):
        x1, z1 = x0 + size - 1, z0 + size - 1
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for y in (1, 2):
                log(b, x, y, z, 'minecraft:mangrove_log')
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                b.set(x, 3, z, 'minecraft:mangrove_planks')
                if x in (x0, x1) or z in (z0, z1):
                    for y in (4, 5):
                        b.set(x, y, z, 'minecraft:mangrove_planks')
        for x in range(x0 + 1, x1):     # a ceiling under the roof, so lanterns have something to hang from
            for z in range(z0 + 1, z1):
                if (x, z) != (12, 11):      # keep the standard's air column open
                    b.set(x, 6, z, 'minecraft:mangrove_planks')
        _gable_roof(b, x0, z0, x1, z1, 6, along, *roof)
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        lantern(b, cx, 2, cz, soul=True, hanging=True)
        for y in (1, 2, 3, 4):                  # a ladder up the corner stilt
            b.set(x0 - 1, y, z0, 'minecraft:ladder', facing='west', waterlogged=False)
        if chimney:
            for y in (7, 8, 9, 10):
                b.set(x0 + 1, y, z1 - 1, 'minecraft:stone_bricks' if y < 10 else 'minecraft:cobblestone_wall',
                      **({} if y < 10 else dict(up=True, north='none', south='none', east='none', west='none',
                                                waterlogged=False)))
        for x, z in door:
            for y in (4, 5):
                b.set(x, y, z, 'minecraft:air')

    hut(9, 9, 7, [(12, 15), (9, 12), (15, 12), (12, 9)])       # central
    hut(10, 2, 5, [(12, 6)], 'z', SPRUCE_ROOF)                 # north
    hut(2, 10, 5, [(6, 12)], 'x', DARK_ROOF, True)             # west
    hut(18, 10, 5, [(18, 12)], 'z', SPRUCE_ROOF, True)         # east
    for x in range(11, 14):                                    # boardwalk from the gate to the stair
        for z in range(19, 24):
            b.set(x, 0, z, 'minecraft:mangrove_planks' if z % 2 else 'minecraft:oak_planks')
    for z in (7, 8):
        b.set(12, 3, z, 'minecraft:mangrove_planks')
    for x in (7, 8):
        b.set(x, 3, 12, 'minecraft:mangrove_planks')
    for x in (16, 17):
        b.set(x, 3, 12, 'minecraft:mangrove_planks')
    rigged_floor(b, 8, 3, 12, 'south')     # machinery hangs under the walkway's side: relay at (8, 3, 16)
    rigged_floor(b, 16, 3, 12, 'south')
    for x in (8, 16):
        for y in (1, 2):
            log(b, x, y, 16, 'minecraft:mangrove_log')
    # stair up to the central hut's south door
    for y, z in ((1, 18), (2, 17), (3, 16)):
        b.set(12, y, z, 'minecraft:mangrove_stairs', **stairs('north'))
    # guns with Slowness arrows, in the central hut's south wall either side of the door
    gallery(b, 10, 5, 15, 'south', 'minecraft:tipped_arrow', 'slowness')
    gallery(b, 14, 5, 15, 'south', 'minecraft:tipped_arrow', 'slowness')
    b.standard(12, 11, 4)
    b.set(10, 4, 10, 'minecraft:brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
    b.set(14, 4, 10, 'minecraft:cauldron')
    lantern(b, 12, 5, 13, soul=True, hanging=True)
    campfire(b, 7, 19)
    campfire(b, 17, 19)
    sculk(b, 9, 20)
    sculk(b, 15, 20)
    b.mob('witch', 12, 4, y=4)
    b.mob('witch', 4, 12, y=4)
    b.mob('witch', 20, 12, y=4)
    b.post('archer', 11, 4, 13)
    b.post('archer', 13, 4, 13)
    for x, z in ((6, 20), (18, 20), (12, 21), (4, 6)):
        b.unit('militia', x, z)
    b.unit('archer', 20, 6)
    b.spare((10, 20), (14, 20), (5, 17), (19, 17))
    b.report('Huts on stilts over a bog, joined by plank walkways that drop away when the alarm sounds. Guns with '
             'Slowness arrows face the stairs. Three witches: bring milk.',
             'Chozas sobre pilotes en un pantano, unidas por pasarelas cuyas tablas caen cuando suena la alarma. '
             'Cañones con flechas de Lentitud apuntan a la escalera. Tres brujas: lleva leche.')
    b.done()


def coven_grove():
    """23x23 stone circle in a ring of azalea hedge: an altar with the standard, four standing stones that hide
    potion throwers aimed at it, three witches and their guard."""
    n = 23
    b = Camp('WITCH_COVEN', 'coven_grove', n, 11)
    b.ground('minecraft:moss_block', 'minecraft:podzol', 'minecraft:coarse_dirt')
    for x, z in circle(11, 11, 10):
        if not (10 <= x <= 12 and z > 15):
            for y in (1, 2):
                b.set(x, y, z, 'minecraft:azalea_leaves' if (x + z) % 4 else 'minecraft:flowering_azalea_leaves',
                      distance=1, persistent=True, waterlogged=False)
    for x, z in ((1, 11), (21, 11), (11, 1), (4, 4), (18, 4), (4, 18), (18, 18)):
        for y in range(1, 7):
            log(b, x, y, z, 'minecraft:dark_oak_log')
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if abs(dx) + abs(dz) <= 3 and 0 <= x + dx < n and 0 <= z + dz < n:
                    b.set(x + dx, 6, z + dz, 'minecraft:dark_oak_leaves', distance=1, persistent=True, waterlogged=False)
                    if abs(dx) + abs(dz) <= 1:
                        b.set(x + dx, 7, z + dz, 'minecraft:dark_oak_leaves', distance=1, persistent=True, waterlogged=False)
    # standing stones: eight pillars on a ring of 6; the four at the compass points hide a potion thrower
    points = {(11, 5): 'south', (11, 17): 'north', (5, 11): 'east', (17, 11): 'west'}
    potions = ['weakness', 'poison', 'slowness', 'weakness']
    for i, a in enumerate(range(0, 360, 45)):
        x = 11 + round(6 * math.cos(math.radians(a)))
        z = 11 + round(6 * math.sin(math.radians(a)))
        for y in range(1, 5):
            b.set(x, y, z, varied(x, y, z, 'minecraft:mossy_cobblestone', 'minecraft:cobblestone', 'minecraft:mossy_stone_bricks'))
        b.set(x, 5, z, 'minecraft:mossy_cobblestone_slab', type='bottom', waterlogged=False)
        if (x, z) in points:
            pot = potions[len([p for p in points if p < (x, z)]) % 4]
            b.set(x, 2, z, 'minecraft:dispenser', facing=points[(x, z)], triggered=False,
                  nbt={'id': 'minecraft:dispenser', 'Items': [
                      {'Slot': s, 'id': 'minecraft:splash_potion', 'count': 1,
                       'components': {'minecraft:potion_contents': {'potion': 'minecraft:' + pot}}} for s in range(9)]})
            b.set(x, 1, z, 'warbands:war_relay', powered=False, pulse=True)
    # the altar
    for x, z in [(x, z) for x in range(9, 14) for z in range(9, 14)]:
        b.set(x, 0, z, 'minecraft:polished_blackstone' if (x + z) % 2 else 'minecraft:blackstone')
    b.standard(11, 11)
    for x, z in ((9, 9), (13, 9), (9, 13), (13, 13)):
        b.set(x, 1, z, 'minecraft:cauldron')
    for x, z in ((10, 9), (12, 13)):
        b.set(x, 1, z, 'minecraft:candle', candles=3, lit=True, waterlogged=False)
    lantern(b, 11, 1, 14, soul=True)
    alarm(b, 11, 20)
    spikes(b, (10, 18), (12, 18))
    pitfall(b, 11, 16, 'sweet_berry_bush')
    sculk(b, 4, 11)
    sculk(b, 18, 11)
    for x, z in ((8, 11), (14, 11), (11, 8)):
        b.mob('witch', x, z)
    for x, z in ((9, 16), (13, 16), (6, 6)):
        b.unit('militia', x, z)
    b.unit('archer', 16, 6)
    b.unit('archer', 16, 16)
    b.unit('spearman', 6, 16)
    b.spare((5, 8), (17, 8), (8, 17), (14, 17))
    b.report('A stone circle inside an azalea hedge. The four standing stones at the compass points hide potion '
             'throwers aimed at the altar; smash the red stone at their feet. Sculk sensors listen at the hedge.',
             'Un círculo de piedras dentro de un seto de azalea. Las cuatro piedras de los puntos cardinales esconden '
             'lanzapociones que apuntan al altar; rompe la piedra roja de su base. Sensores de sculk escuchan en el seto.')
    b.done()


def frozen_starfort():
    """27x27 star fort of ice: a diamond wall of packed ice with icicle hoardings and spruce posts, archers under
    spruce spires on three bastions, a roofed south gate. Inside, a great igloo keep (bunks, stores, the map table,
    an ice-blue fire) with the standard in the open before its porch; fur tents in the side points. Powder-snow pits
    and two guns on the path; sculk sensors in the side points hear wall-climbers."""
    n, c = 27, 13
    b = Camp('FROZEN_OUTPOST', 'frozen_starfort', n, 13)
    b.ground(*g.SNOW_GROUND)
    dist = lambda x, z: abs(x - c) + abs(z - c)
    outer = [(x, z) for x in range(n) for z in range(n) if dist(x, z) == 12]
    inner = [(x, z) for x in range(n) for z in range(n) if dist(x, z) == 11]
    wall(b, outer + inner, 1, 4, 'minecraft:packed_ice', 'minecraft:blue_ice', 'minecraft:snow_block')
    for x, z in outer:
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone'))
        if (x + z) % 2 == 0:
            b.set(x, 5, z, 'minecraft:snow_block')
        if (x + 2 * z) % 9 == 0:
            for y in range(1, 6):
                log(b, x, y, z)
            lantern(b, x, 6, z)
    for x, z in inner:
        b.set(x, 5, z, 'minecraft:snow', layers=1)
    # hoardings: packed-ice ledges out of the wall top with icicles under them
    for x, z in [(x, z) for x in range(n) for z in range(n) if dist(x, z) == 13 and (x * 3 + z) % 4 == 0]:
        if z < 20:
            b.set(x, 4, z, 'minecraft:packed_ice')
            g.frozen_icicle(b, x, z, 4, 1 + (x + z) % 2)
    # the gate: a gap in the south point under a little spruce roof
    for x, z in outer + inner:
        if 12 <= x <= 14 and z >= 22:
            for y in range(1, 6):
                b.set(x, y, z, 'minecraft:air')
    for x in (11, 15):
        for y in range(1, 7):
            log(b, x, y, 23)
    for x in range(12, 15):
        for z in (22, 23):
            log(b, x, 4, z, 'minecraft:stripped_spruce_log', 'x')
    for x in range(10, 17):
        b.set(x, 5, 24, 'minecraft:spruce_stairs', **stairs('north'))
        b.set(x, 5, 21, 'minecraft:spruce_stairs', **stairs('south'))
        for z in (22, 23):
            b.set(x, 5, z, 'minecraft:spruce_planks')
            slab(b, x, 6, z, 'minecraft:spruce_slab')
    for x in (12, 14):
        b.set(x, 4, 24, 'minecraft:light_blue_wall_banner', facing='south')
    for x in (11, 15):
        b.set(x, 7, 23, 'minecraft:light_blue_banner', rotation=6)
    # bastions: packed ice with spruce corners, an archer under a spruce spire
    bastions = []
    for x0, z0 in ((12, 1), (23, 12), (1, 12)):
        spot = tower(b, x0, z0, 6, 'minecraft:packed_ice', 'minecraft:spruce_slab')
        bastions.append(spot)
        for x, z in ((x0, z0), (x0 + 2, z0), (x0, z0 + 2), (x0 + 2, z0 + 2)):
            for y in range(1, 10):      # up to the eave ring, so the spire rests on the posts
                log(b, x, y, z)
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                if b.block(x, 3, z) == 'minecraft:packed_ice':
                    b.set(x, 3, z, 'minecraft:blue_ice')
        for x, z in g.rect_ring(x0 - 1, z0 - 1, x0 + 3, z0 + 3):
            facing = 'south' if z == z0 - 1 else 'north' if z == z0 + 3 else 'east' if x == x0 - 1 else 'west'
            if x in (x0 - 1, x0 + 3) and z in (z0 - 1, z0 + 3):
                b.set(x, 9, z, 'minecraft:spruce_planks')
                g.frozen_icicle(b, x, z, 9)
            else:
                b.set(x, 9, z, 'minecraft:spruce_stairs', **stairs(facing))
        for x, z in g.rect_ring(x0, z0, x0 + 2, z0 + 2):
            facing = 'south' if z == z0 else 'north' if z == z0 + 2 else 'east' if x == x0 else 'west'
            if x in (x0, x0 + 2) and z in (z0, z0 + 2):
                b.set(x, 10, z, 'minecraft:spruce_planks')
            else:
                b.set(x, 10, z, 'minecraft:spruce_stairs', **stairs(facing))
        b.set(x0 + 1, 10, z0 + 1, 'minecraft:spruce_planks')
        b.set(x0 + 1, 11, z0 + 1, 'minecraft:light_blue_banner', rotation=6)

    # the keep: a great igloo, porch to the south, the standard in the open before it
    kx, kz = 13, 9
    g.frozen_dome(b, kx, kz, 5)
    for x in range(n):
        for z in range(n):
            if math.hypot(x - kx, z - kz) < 4.4:
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:spruce_planks', 'minecraft:stripped_spruce_log'))
    for y in (1, 2):
        b.set(kx, y, 14, 'minecraft:air')
    for x in (12, 14):
        for y in (1, 2, 3):
            log(b, x, y, 15)
    for x in range(12, 15):
        log(b, x, 3, 15, 'minecraft:stripped_spruce_log', 'x')
    for x in range(11, 16):
        b.set(x, 4, 15, 'minecraft:spruce_stairs', **stairs('north'))
    for x in range(12, 15):
        b.set(x, 4, 14, 'minecraft:spruce_planks')
    b.set(kx, 3, 16, 'minecraft:air')
    for x in (11, 15):
        g.frozen_banner(b, x, 17, pole=2)
    b.standard(kx, 17)
    b.set(kx, 0, kz, 'minecraft:cobblestone')
    campfire(b, kx, kz, soul=True)
    for x, z in ((11, 9), (15, 9), (13, 7)):
        b.set(x, 1, z, 'minecraft:light_gray_carpet')
    g.frozen_bed(b, 10, 7, 'north', 'white')
    g.frozen_bed(b, 16, 7, 'north', 'light_gray')
    g.frozen_bed(b, 10, 10, 'south', 'light_gray')
    b.set(12, 1, 5, 'minecraft:chest', facing='south', type='single', waterlogged=False)
    b.set(14, 1, 5, 'minecraft:barrel', facing='up', open=False)
    b.set(16, 1, 10, 'minecraft:cartography_table')
    b.set(16, 1, 11, 'minecraft:barrel', facing='up', open=False)
    b.set(16, 2, 11, 'minecraft:lantern', hanging=False, waterlogged=False)
    b.set(11, 1, 12, 'minecraft:cake', bites=3)
    for x in (11, 15):
        lantern(b, x, 4, kz, hanging=True)

    # fur tents and racks in the side points
    tent(b, 5, 12, 3, 'minecraft:white_wool')
    tent(b, 19, 12, 3, 'minecraft:light_gray_wool')
    g.frozen_fur_rack(b, 6, 8, 'x')
    g.frozen_fur_rack(b, 18, 8, 'x')
    for x, z in ((8, 16), (18, 16)):
        campfire(b, x, z, soul=True)
    sculk(b, 4, 13)
    sculk(b, 22, 13)

    # the path: powder-snow pits, two guns on spruce posts, caltrops and a plate in the gate
    for x, z in ((11, 20), (15, 20), (13, 21)):
        pitfall(b, x, z)
    for x in (10, 16):
        log(b, x, 1, 19)
        gallery(b, x, 3, 19, 'south')
        slab(b, x, 4, 19, 'minecraft:spruce_slab')
    alarm(b, 13, 23)
    spikes(b, (12, 22), (14, 22))
    g.frozen_drift(b, n, 6, gate=range(10, 17))

    for spot in bastions:
        b.post('archer', *spot)
    b.mob('stray', 7, 16)
    b.mob('stray', 19, 16)
    b.mob('stray', 13, 11)
    b.unit('militia', 13, 19)
    b.unit('militia', 9, 18)
    b.unit('militia', 17, 18)
    b.unit('militia', 13, 15)
    b.unit('spearman', 7, 10)
    b.unit('spearman', 19, 10)
    b.spare((6, 15), (20, 15), (12, 12), (14, 12))
    b.report('A star fort of ice: archers under the spires of three bastions, a great igloo keep in the middle. '
             'Powder snow under the open trapdoors on the path, two guns on spruce posts, sculk sensors in the side '
             'points.',
             'Un fuerte estrellado de hielo: arqueros bajo las agujas de tres baluartes y un gran iglú en el centro. '
             'Nieve polvo bajo las trampillas abiertas del camino, dos cañones en postes de abeto y sensores de sculk '
             'en las puntas laterales.')
    b.done()


def _ice_spire(b, x, z, base, height):
    """A tapering cluster of blue ice: one tall column with two shorter ones against it."""
    for dx, dz, h in ((0, 0, height), (1, 0, height // 2), (0, 1, max(2, height // 2 - 1))):
        for y in range(base, base + h):
            b.set(x + dx, y, z + dz, 'minecraft:blue_ice' if y % 3 else 'minecraft:packed_ice')
        b.set(x + dx, base + h, z + dz, 'minecraft:snow', layers=1)


def frozen_glacier():
    """23x23 glacier mound with a cavern inside: one tunnel, sealed by a portcullis when the alarm sounds (its relays
    are buried in the ice beside the gate), strays inside, archers on top. Ice digs fast: dig your own way in, but
    sculk sensors inside hear the mining."""
    n, c = 23, 11
    b = Camp('FROZEN_OUTPOST', 'frozen_glacier', n, 16)
    b.ground('minecraft:snow_block', 'minecraft:packed_ice', 'minecraft:gravel')
    heights = {}
    for x in range(n):
        for z in range(n):
            d = math.sqrt((x - c) ** 2 + (z - c) ** 2)
            ridge = 1.0 * math.sin(x * 0.5 + 1) * math.cos(z * 0.4) if d > 3.5 else 0
            h = int(10 - 0.9 * max(0.0, d - 3.5) + ridge)
            if h >= 1:
                heights[(x, z)] = h
                for y in range(1, h + 1):
                    if y <= 3 and d > 6:
                        blk = varied(x, y, z, 'minecraft:packed_ice', 'minecraft:blue_ice', 'minecraft:blue_ice')
                    elif y == h and (x + z) % 5 == 0:
                        blk = 'minecraft:packed_ice'
                    else:
                        blk = varied(x, y, z, 'minecraft:snow_block', 'minecraft:packed_ice', 'minecraft:blue_ice')
                    b.set(x, y, z, blk)
                b.set(x, h + 1, z, 'minecraft:snow', layers=1 + (x * z) % 3 % 2)
    # the cavern: a dome of radius 7 round the middle, 6 high
    for x in range(n):
        for z in range(n):
            for y in range(1, 7):
                if (x - c) ** 2 + (z - c) ** 2 + (y * 1.3) ** 2 <= 49:
                    b.set(x, y, z, 'minecraft:air')
    # tunnel from the south: a four-wide gate hall (z 19..22) narrowing to the two-wide portcullis mouth (z 17)
    for x in (10, 11):
        for z in range(17, n):
            for y in (1, 2):
                b.set(x, y, z, 'minecraft:air')
            b.set(x, 3, z, 'minecraft:packed_ice')
    for x in (9, 12):
        for z in range(19, n):
            for y in (1, 2, 3):
                b.set(x, y, z, 'minecraft:air')
            b.set(x, 4, z, 'minecraft:packed_ice')
    for x in range(9, 13):
        for z in range(19, n):
            b.set(x, 0, z, varied(x, 0, z, 'minecraft:gravel', 'minecraft:cobblestone'))
    piston_gate(b, 10, 17)
    for x in range(7, 15):
        for y in (1, 2, 3):
            if b.block(x, y, 16) == 'minecraft:air' and x not in (10, 11):
                b.set(x, y, 16, 'minecraft:packed_ice')
            if b.block(x, y, 18) == 'minecraft:air' and x not in (10, 11):
                b.set(x, y, 18, 'minecraft:packed_ice')
    # the gate arch: dark oak posts and lintel, soul lanterns, two banners on masts
    for x in (8, 13):
        for y in (1, 2, 3):
            log(b, x, y, 22, 'minecraft:dark_oak_log')
        for y in (5, 6, 7):
            fence(b, x, y, 22, 'minecraft:dark_oak_fence')
        b.set(x, 8, 22, 'minecraft:blue_banner', rotation=0)
    for x in range(8, 14):
        log(b, x, 4, 22, 'minecraft:dark_oak_log', 'x')
    for x in (9, 12):
        lantern(b, x, 3, 22, soul=True, hanging=True)
    for x in (7, 14):
        for y in (1, 2, 3, 4):
            b.set(x, y, 22, 'minecraft:blue_ice')
    # ice spires round the mound and beside the gate
    for x, z in ((7, 20), (14, 20), (5, 5), (17, 4), (3, 9), (19, 15), (9, 3), (3, 16)):
        _ice_spire(b, x, z, heights[(x, z)] + 1, 4 + (x * z) % 4)
    # a mast on the summit
    for y in range(11, 15):
        fence(b, 11, y, 11, 'minecraft:dark_oak_fence')
    b.set(11, 15, 11, 'minecraft:blue_banner', rotation=0)
    b.standard(c, 8)
    for x, z in ((8, 9), (14, 9)):
        campfire(b, x, z, soul=True)
    sculk(b, 6, 11)
    sculk(b, 16, 11)
    sculk(b, 11, 5)
    alarm(b, 10, 20)
    b.mob('stray', 8, 11)
    b.mob('stray', 14, 11)
    b.mob('stray', 11, 6)
    b.unit('militia', 9, 14)
    b.unit('militia', 13, 14)
    b.unit('spearman', 11, 12)
    b.unit('spearman', 12, 9)
    # archer ledges: a flat nook cut into each flank with a timber rail on the outside
    for x, side in ((5, 4), (17, 18)):
        top = heights[(x, 11)]
        for xx in (x - 1, x, x + 1):
            for z in (10, 11, 12):
                for y in range(1, top + 1):
                    if b.block(xx, y, z) == 'minecraft:air':
                        b.set(xx, y, z, 'minecraft:packed_ice')
                for y in range(top + 1, top + 4):
                    b.set(xx, y, z, 'minecraft:air')
        for z in (10, 11, 12):
            fence(b, side, top + 1, z, 'minecraft:dark_oak_fence')
        b.post('archer', x, top + 1, 11)
    b.spare((9, 11), (13, 11), (11, 15), (11, 13))
    b.report('A glacier with a cavern inside: one tunnel, sealed by a portcullis when the alarm sounds. Its relays are '
             'buried in the ice beside the gate, and ice digs fast: dig your own way in. Sculk sensors inside hear '
             'mining, so sneak while you dig.',
             'Un glaciar con una caverna dentro: un solo túnel, que un rastrillo sella cuando suena la alarma. Sus '
             'relés están enterrados en el hielo junto a la puerta, y el hielo se pica rápido: cava tu propia entrada. '
             'Sensores de sculk oyen picar dentro, así que cava agachado.')
    b.done()


def build():
    fort_barbican()
    fort_twin()
    quarry_terraces()
    quarry_minehead()
    coven_stilts()
    coven_grove()
    frozen_starfort()
    frozen_glacier()
