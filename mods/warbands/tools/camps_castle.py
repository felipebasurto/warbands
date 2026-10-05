"""Castle Age camp layouts (second and third designs of each kind). Run through gen_camps.py."""
import random
import gen_structures as g

from gen_camps import (Camp, fill, ring, circle, wall, crenels, tower, log, fence, lantern, campfire, fence_gate, ladder,
                       slab, walkway, tent, gallery, piston_gate, sculk, alarm, spikes, tripwire, keg_wire, rigged_floor,
                       ammo, hip_roof, stairs, varied)

DEEP = ('minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:deepslate_tiles')
BLACK = ('minecraft:polished_blackstone_bricks', 'minecraft:cracked_polished_blackstone_bricks')
STONE = ('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks')


def reinforced_gate(b, x0, x1, z, y0, y1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            b.set(x, y, z, 'warbands:reinforced_gate')


def _gable_barn(b, x0, x1, z0, z1, wall_top=4, plank='minecraft:spruce_planks', post='minecraft:dark_oak_log',
                foot='minecraft:cobblestone', roof='minecraft:dark_oak_stairs', ridge='minecraft:dark_oak_slab'):
    """A pitched-roof barn (ridge along x): footing, plank walls, corner posts, overhanging stair roof, gable ends.
    Depth z0..z1 must be odd. Returns the ridge height."""
    zm = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(1, wall_top + 1):
                    if (x in (x0, x1)) and (z in (z0, z1)):
                        b.set(x, y, z, post, axis='y')
                    elif y == 1:
                        b.set(x, y, z, varied(x, y, z, foot, 'minecraft:mossy_cobblestone'))
                    elif y == wall_top:
                        b.set(x, y, z, 'minecraft:stripped_dark_oak_log', axis='x' if z in (z0, z1) else 'z')
                    elif (x - x0) % 3 == 0 and z in (z0, z1) or (z - z0) % 3 == 0 and x in (x0, x1):
                        b.set(x, y, z, post, axis='y')
                    else:
                        b.set(x, y, z, plank)
            else:
                b.set(x, 0, z, 'minecraft:coarse_dirt' if (x + z) % 3 else 'minecraft:dirt_path')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, wall_top + 1, z, plank)
    k = 0
    while z0 - 1 + k < zm:
        y = wall_top + 1 + k
        for x in range(x0 - 1, x1 + 2):
            b.set(x, y, z0 - 1 + k, roof, **stairs('south'))
            b.set(x, y, z1 + 1 - k, roof, **stairs('north'))
        for x in (x0, x1):
            for z in range(z0 + k, z1 - k + 1):
                if z0 - 1 + k < z < z1 + 1 - k:
                    b.set(x, y, z, plank)
        k += 1
    ry = wall_top + 1 + k
    for x in range(x0 - 1, x1 + 2):
        b.set(x, ry - 1, zm, 'minecraft:dark_oak_planks')
        b.set(x, ry, zm, ridge, type='bottom', waterlogged=False)
    return ry


def _moat_cap(b, x0, z0, size, top, slit_ys, base_y=2):
    """Bevel and cap a tower made by tower(): stairs corbel under an overhanging slab deck, merlons, arrow slits."""
    hi = size - 1
    for x, z in g.rect_ring(x0 - 1, z0 - 1, x0 + size, z0 + size):
        corner = x in (x0 - 1, x0 + size) and z in (z0 - 1, z0 + size)
        if corner:
            b.set(x, top, z, 'minecraft:polished_blackstone_bricks')
        else:
            f = 'south' if z == z0 - 1 else 'north' if z == z0 + size else 'east' if x == x0 - 1 else 'west'
            b.set(x, top, z, 'minecraft:polished_blackstone_brick_stairs', **stairs(f, 'top'))
        b.set(x, top + 1, z, 'minecraft:polished_blackstone_brick_slab', type='bottom', waterlogged=False)
        if corner or (x + z) % 2 == 0:
            b.set(x, top + 2, z, 'minecraft:polished_blackstone_bricks')
    c = size // 2
    for y in slit_ys:
        for x, z in ((x0 + c, z0), (x0 + c, z0 + hi), (x0, z0 + c), (x0 + hi, z0 + c)):
            b.set(x, y, z, 'minecraft:iron_bars', north=False, south=False, east=False, west=False, waterlogged=False)
    for x, z in ((x0, z0), (x0 + hi, z0), (x0, z0 + hi), (x0 + hi, z0 + hi)):   # a polished corner column
        for y in range(base_y, top):
            b.set(x, y, z, 'minecraft:polished_blackstone')


def keep_moat():
    """39x39 island castle: a two-deep moat all round, one causeway to a gate only a Ram breaks, guns in the
    gatehouse towers, a portcullis on the inner keep, and a back postern reached only by swimming."""
    n = 39
    b = Camp('KEEP', 'keep_moat', n, 18)
    # everything stands on a raised bank: ground blocks at y0..1, walk at y2; the moat is water at y0..1
    for x in range(n):
        for z in range(n):
            edge = min(x, z, n - 1 - x, n - 1 - z)
            if 2 <= edge <= 4:
                b.set(x, 0, z, 'minecraft:water', level=0)
                b.set(x, 1, z, 'minecraft:water', level=0)
            else:
                b.set(x, 0, z, 'minecraft:dirt')
                b.set(x, 1, z, varied(x, 1, z, 'minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:gravel')
                      if edge < 2 or edge > 5 else 'minecraft:cobblestone', **({'snowy': False} if edge < 2 else {}))
    for x in range(n):
        for z in range(n):
            if b.block(x, 1, z) == 'minecraft:grass_block':
                b.set(x, 1, z, 'minecraft:grass_block', snowy=False)
    for x in (18, 19, 20):  # causeway over the south moat
        for z in (34, 35, 36):
            b.set(x, 0, z, 'minecraft:cobblestone')
            b.set(x, 1, z, 'minecraft:cobblestone')
    lo, hi = 6, 32
    wall(b, list(ring(lo, hi)), 2, 9, *DEEP)
    crenels(b, list(ring(lo, hi)), 10, 'minecraft:deepslate_bricks')
    reinforced_gate(b, 18, 20, hi, 2, 5)
    corners = [tower(b, x0, z0, 12, 'minecraft:polished_blackstone_bricks', 'minecraft:polished_blackstone_brick_slab', 5)
               for x0, z0 in ((5, 5), (29, 5), (5, 29), (29, 29))]
    gates = [tower(b, x0, 31, 11, 'minecraft:polished_blackstone_bricks', 'minecraft:polished_blackstone_brick_slab')
             for x0 in (15, 21)]
    for x0, z0 in ((5, 5), (29, 5), (5, 29), (29, 29)):
        _moat_cap(b, x0, z0, 5, 12, (6, 9))
    for x0 in (15, 21):
        _moat_cap(b, x0, 31, 3, 11, (7,))
    for x in (16, 22):  # guns in the gatehouse towers' outer faces, over the causeway
        gallery(b, x, 4, 33, 'south', 'minecraft:arrow')
    b.set(16, 2, 33, 'minecraft:polished_blackstone_bricks')
    b.set(22, 2, 33, 'minecraft:polished_blackstone_bricks')
    # bailey floor
    for x in range(lo + 1, hi):
        for z in range(lo + 1, hi):
            b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobbled_deepslate', 'minecraft:gravel', 'minecraft:coarse_dirt'))
    # the inner keep: 11x11, walls to y12, a roof at y13 with crenels, portcullis gate on the south
    klo, khi = 14, 24
    for x in range(klo, khi + 1):
        for z in range(10, 21):
            edge = x in (klo, khi) or z in (10, 20)
            if edge:
                for y in range(2, 13):
                    b.set(x, y, z, varied(x, y, z, *BLACK))
            b.set(x, 13, z, 'minecraft:polished_blackstone')
            b.set(x, 1, z, 'minecraft:polished_deepslate')
    crenels(b, [(x, z) for x in range(klo, khi + 1) for z in (10, 20)] + [(x, z) for x in (klo, khi) for z in range(10, 21)],
            14, 'minecraft:polished_blackstone_bricks')
    piston_gate(b, 18, 20, 2)
    for x in (18, 19):
        b.set(x, 4, 20, 'minecraft:polished_blackstone_bricks')
    for x in (16, 22):
        for z in (13, 17):
            b.set(x, 5, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
            for y in (2, 3, 4):
                b.set(x, y, z, 'minecraft:polished_blackstone_brick_wall', up=True, north='none', south='none',
                      east='none', west='none', waterlogged=False)
    ladder(b, 23, 11, 2, 12, 'south')
    b.set(23, 13, 11, 'minecraft:air')
    b.standard(19, 13, 2)
    # the postern: one-wide door at the back (north wall), only reached by swimming, sculk sensors behind it
    for y in (2, 3):
        b.set(30, y, lo, 'minecraft:air')
    sculk(b, 28, 8, 2)
    sculk(b, 30, 9, 2)
    # bailey: stables, fires, the gate approach
    for x in range(8, 13):
        fence(b, x, 2, 24, 'minecraft:spruce_fence')
    for z in (25, 26):
        b.set(8, 2, z, 'minecraft:hay_block', axis='y')
    for x in (10, 28):
        b.set(x, 2, 20, 'minecraft:magma_block')
        campfire(b, x, 20, y=3)
    alarm(b, 19, 31, 2)
    spikes(b, (17, 29), (19, 28), (21, 29), y=2)
    keg_wire(b, 17, 26, 3, 'minecraft:polished_blackstone_bricks', y=2)
    for x, z in ((10, 26), (28, 24), (19, 27), (12, 12)):
        b.unit('militia', x, z, y=2)
    for x, z in ((16, 23), (22, 23), (10, 14), (28, 14)):
        b.unit('spearman', x, z, y=2)
    b.unit('knight', 11, 28, y=2)
    b.unit('knight', 27, 28, y=2)
    b.unit('crossbowman', 26, 9, y=2)
    for spot in corners + gates:
        b.post('crossbowman', *spot)
    b.post('crossbowman', 13, 10, 6)
    b.post('crossbowman', 25, 10, 6)
    b.post('crossbowman', 15, 14, 11)
    b.post('crossbowman', 23, 14, 19)
    b.warlord(19, 16, 2)
    b.unit('militia', 16, 15, y=2)
    b.unit('militia', 22, 15, y=2)
    b.spare((9, 18), (29, 18), (14, 26), (24, 26), (19, 23), y=2)
    b.report('An island castle: a moat all round, one causeway to a gate only a Ram breaks, guns in the gatehouse. The '
             'inner keep drops a portcullis when the alarm sounds. A postern at the back is only reached by swimming, '
             'and sculk sensors listen behind it.',
             'Un castillo en una isla: foso alrededor, una sola calzada hasta un portón que solo rompe un Ariete, '
             'cañones en el cuerpo de guardia. La torre del homenaje baja un rastrillo cuando suena la alarma. Un '
             'portillo trasero solo se alcanza nadando, y hay sensores de sculk detrás.')
    b.done()


def _concentric_crenels(b, n, lo, hi, y, block):
    """Crenels with a different rhythm on each side (wide merlons north, narrow south, paired east and west), so the
    wall doesn't read as one repeated fence."""
    for x, z in ring(lo, hi):
        if z == lo:
            on = x % 3 != 0
        elif z == hi:
            on = x % 2 == 0
        elif x == lo:
            on = z % 4 < 2
        else:
            on = z % 5 != 0
        if on:
            b.set(x, y, z, block)


def _bastion(b, x0, z0, top=10, size=5):
    """A corner bastion: a solid stone tower with a corbelled deck, merlons, arrow slits and a batter at the foot.
    Returns the guard spot, like tower()."""
    spot = tower(b, x0, z0, top, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab', size)
    hi = size - 1
    for x in range(x0, x0 + size):
        for z in range(z0, z0 + size):
            if x in (x0, x0 + hi) or z in (z0, z0 + hi):
                for y in range(1, top + 1):
                    b.set(x, y, z, varied(x, y, z, 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks',
                                          'minecraft:cracked_stone_bricks'))
    for x, z in g.rect_ring(x0 - 1, z0 - 1, x0 + size, z0 + size):   # corbel under the overhanging deck
        corner = x in (x0 - 1, x0 + size) and z in (z0 - 1, z0 + size)
        if corner:
            b.set(x, top, z, 'minecraft:stone_bricks')
        else:
            f = 'south' if z == z0 - 1 else 'north' if z == z0 + size else 'east' if x == x0 - 1 else 'west'
            b.set(x, top, z, 'minecraft:stone_brick_stairs', **stairs(f, 'top'))
        b.set(x, top + 1, z, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
        if (x + z) % 2 == 0 or corner:
            b.set(x, top + 2, z, 'minecraft:stone_bricks')
    for y in (4, 7):   # slits on all four faces
        for c in (size // 2,):
            for x, z in ((x0 + c, z0), (x0 + c, z0 + hi), (x0, z0 + c), (x0 + hi, z0 + c)):
                b.set(x, y, z, 'minecraft:iron_bars', north=False, south=False, east=False, west=False, waterlogged=False)
    for x in range(x0 - 1, x0 + size + 1):   # sloped foot: stairs round the base
        for z in range(z0 - 1, z0 + size + 1):
            if x in (x0 - 1, x0 + size) or z in (z0 - 1, z0 + size):
                if not (x in (x0 - 1, x0 + size) and z in (z0 - 1, z0 + size)):
                    f = 'south' if z == z0 - 1 else 'north' if z == z0 + size else 'east' if x == x0 - 1 else 'west'
                    if 0 <= x < 45 and 0 <= z < 45 and b.block(x, 1, z) == 'minecraft:air':
                        b.set(x, 1, z, 'minecraft:stone_brick_stairs', **stairs(f))
    return spot


def keep_concentric():
    """45x45 concentric castle: an outer wall, then a corridor of traps round an inner wall whose portcullis gate is
    on the far (north) side, guns in the inner wall sweeping the corridor, the warlord in the central keep."""
    n = 45
    b = Camp('KEEP', 'keep_concentric', n, 20)
    b.ground('minecraft:cobbled_deepslate', 'minecraft:gravel', 'minecraft:coarse_dirt')
    wall(b, list(ring(1, 43)), 1, 6, *STONE)
    _concentric_crenels(b, n, 1, 43, 7, 'minecraft:stone_bricks')
    for x in (21, 22, 23):
        fence_gate(b, x, 1, 43)
        fence_gate(b, x, 2, 43)
        log(b, x, 3, 43, 'minecraft:dark_oak_log', 'x')
    # pilasters buttress the wall on the corridor side, and outside on the bank
    for t in (8, 14, 30, 36):
        for x, z in ((2, t), (42, t), (t, 2), (t, 42)):
            for y in range(1, 6):
                b.set(x, y, z, 'minecraft:stone_bricks')
            b.set(x, 6, z, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
        for x, z in ((0, t), (44, t), (t, 0)):
            for y in range(1, 5):
                b.set(x, y, z, 'minecraft:stone_bricks')
            b.set(x, 5, z, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
    outer = [_bastion(b, x0, z0) for x0, z0 in ((1, 1), (39, 1), (1, 39), (39, 39))]
    for x0, z0 in ((21, 1), (1, 21), (41, 21)):
        tower(b, x0, z0, 9, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab')
    gatehouse = [tower(b, x0, 41, 10, 'minecraft:stone_bricks', 'minecraft:stone_brick_slab') for x0 in (17, 25)]
    for x in (17, 25):   # gate towers: lit slits and a dark band
        for z in (43,):
            for y in (5, 8):
                b.set(x + 1, y, z, 'minecraft:iron_bars', north=False, south=False, east=False, west=False, waterlogged=False)
    wall(b, list(ring(11, 33)), 1, 10, *DEEP)
    crenels(b, list(ring(11, 33)), 11, 'minecraft:deepslate_bricks')
    inner = [tower(b, x0, z0, 13, 'minecraft:polished_blackstone_bricks', 'minecraft:polished_blackstone_brick_slab', 5)
             for x0, z0 in ((10, 10), (30, 10), (10, 30), (30, 30))]
    piston_gate(b, 21, 11)   # the inner gate faces north: walk round the corridor to reach it
    for x in (21, 22):
        b.set(x, 3, 11, 'minecraft:deepslate_bricks')
    # guns in the inner wall facing out into the corridor, a relay under each
    for x in (16, 22, 28):
        gallery(b, x, 3, 33, 'south')
    for z in (16, 22, 28):
        gallery(b, 11, 3, z, 'west')
        gallery(b, 33, 3, z, 'east')
    for x in (16, 28):
        gallery(b, x, 3, 11, 'north')
    # the corridor: caltrops, keg wires and a sculk sensor at each turn
    for x, z in ((8, 38), (14, 37), (30, 37), (36, 38), (6, 30), (38, 30), (6, 14), (38, 14), (14, 6), (30, 6)):
        spikes(b, (x, z))
    keg_wire(b, 3, 20, 5, 'minecraft:stone_bricks')
    keg_wire(b, 35, 24, 5, 'minecraft:stone_bricks')
    for x, z in ((7, 7), (37, 7), (7, 37), (37, 37)):
        sculk(b, x, z)
    alarm(b, 22, 42)
    # inner bailey and the keep
    for x in range(17, 28):
        for z in range(17, 28):
            edge = x in (17, 27) or z in (17, 27)
            if edge:
                for y in range(1, 15):
                    b.set(x, y, z, varied(x, y, z, *BLACK))
            b.set(x, 15, z, 'minecraft:polished_blackstone')
    crenels(b, list(ring(17, 27)), 16, 'minecraft:polished_blackstone_bricks')
    for x in (21, 22, 23):
        for y in (1, 2, 3):
            b.set(x, y, 17, 'minecraft:air')
    b.standard(22, 22)
    for x, z in ((19, 19), (25, 19), (19, 25), (25, 25)):
        b.set(x, 1, z, 'minecraft:polished_blackstone_brick_wall', up=True, north='none', south='none', east='none',
              west='none', waterlogged=False)
        b.set(x, 2, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for x in (14, 30):
        campfire(b, x, 14)
    for x in (14, 30):
        b.set(x, 1, 30, 'minecraft:target')
    # garrison
    for x, z in ((22, 39), (8, 22), (36, 22)):
        b.unit('militia', x, z)
    for x, z in ((16, 38), (28, 38), (22, 6)):
        b.unit('spearman', x, z)
    b.unit('knight', 7, 34)
    b.unit('knight', 37, 34)
    for spot in outer + gatehouse:
        b.post('archer', *spot)
    for spot in inner:
        b.post('crossbowman', *spot)
    b.unit('spearman', 20, 14)
    b.unit('spearman', 24, 14)
    b.unit('crossbowman', 14, 22)
    b.unit('militia', 30, 22)
    b.unit('militia', 22, 19)
    b.warlord(22, 24)
    b.spare((14, 26), (30, 26), (22, 30), (6, 8), (38, 8))
    b.report('A concentric castle: an outer wall, then a corridor of traps round an inner wall whose portcullis gate is '
             'on the far (north) side. Guns in the inner wall sweep the corridor; each has its red relay under it. The '
             'warlord holds the central keep.',
             'Un castillo concéntrico: una muralla exterior y luego un pasillo de trampas alrededor de una muralla '
             'interior cuyo rastrillo está en el lado opuesto (norte). Cañones en la muralla interior barren el '
             'pasillo, cada uno con su relé rojo debajo. El señor de la guerra guarda la torre central.')
    b.done()


def _cav_tower(b, x0, z0, top=5):
    """Cavalry watch tower: a 3x3 log shaft with slits and a corbelled 5x5 deck, rails and a pyramid roof on four posts.
    The guard stands in the middle of the deck."""
    guard = tower(b, x0, z0, top, 'minecraft:stripped_spruce_log', 'minecraft:spruce_slab')
    for x, z in ((x0, z0), (x0 + 2, z0), (x0, z0 + 2), (x0 + 2, z0 + 2)):
        for y in range(1, top + 1):
            log(b, x, y, z, 'minecraft:spruce_log')
    for x, z, f in ((x0 + 1, z0 - 1, 'north'), (x0 + 1, z0 + 3, 'south'), (x0 - 1, z0 + 1, 'west'),
                    (x0 + 3, z0 + 1, 'east')):   # open shutters as slits
        b.set(x, 3, z, 'minecraft:spruce_trapdoor', facing=f, half='bottom', open=True, powered=False,
              waterlogged=False)
    lo, hi = x0 - 1, x0 + 3
    zlo, zhi = z0 - 1, z0 + 3
    for x, z in g.rect_ring(lo, zlo, hi, zhi):
        if not (x in (x0 - 1, x0 + 3) and z in (zlo, zhi)) and (lo < x < hi or zlo < z < zhi):
            f = 'south' if z == zlo else 'north' if z == zhi else 'east' if x == lo else 'west'
            b.set(x, 4, z, 'minecraft:spruce_stairs', **stairs(f, 'top'))
        else:
            b.set(x, 4, z, 'minecraft:dark_oak_log', axis='y')
    for x, z in g.rect_ring(lo, zlo, hi, zhi):
        b.set(x, 5, z, 'minecraft:spruce_planks')
    for x, z in g.rect_ring(lo, zlo, hi, zhi):
        corner = x in (lo, hi) and z in (zlo, zhi)
        if corner:
            for y in (6, 7, 8):
                log(b, x, y, z, 'minecraft:dark_oak_log')
        else:
            fence(b, x, 6, z, 'minecraft:spruce_fence')
    for x in range(lo, hi + 1):   # roof: a plank deck with an overhanging stair ring and a stepped cap
        for z in range(zlo, zhi + 1):
            b.set(x, 9, z, 'minecraft:dark_oak_planks')
    for x, z in g.rect_ring(lo - 1, zlo - 1, hi + 1, zhi + 1):
        f = 'south' if z == zlo - 1 else 'north' if z == zhi + 1 else 'east' if x == lo - 1 else 'west'
        if x in (lo - 1, hi + 1) and z in (zlo - 1, zhi + 1):
            b.set(x, 9, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
        else:
            b.set(x, 9, z, 'minecraft:dark_oak_stairs', **stairs(f))
    for x, z in g.rect_ring(lo, zlo, hi, zhi):
        f = 'south' if z == zlo else 'north' if z == zhi else 'east' if x == lo else 'west'
        corner = x in (lo, hi) and z in (zlo, zhi)
        b.set(x, 10, z, 'minecraft:dark_oak_planks' if corner else 'minecraft:dark_oak_stairs',
              **({} if corner else stairs(f)))
    for x in range(lo + 1, hi):
        for z in range(zlo + 1, zhi):
            b.set(x, 10, z, 'minecraft:dark_oak_planks')
    b.set(x0 + 1, 11, z0 + 1, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    return guard


def _cav_stand(b, x0, x1):
    """Raised spectators' stand in a corner: three tiers, back posts, a plank roof and lanterns."""
    xs = range(x0, x1 + 1)
    for x in xs:
        for i, z in enumerate((29, 30, 31)):
            for y in range(1, i + 2):
                b.set(x, y, z, 'minecraft:spruce_planks' if y == i + 1 else 'minecraft:stripped_spruce_log',
                      **({} if y == i + 1 else {'axis': 'y'}))
        b.set(x, 1, 28, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for x in (x0, x1):
        for y in range(4, 7):
            log(b, x, y, 31, 'minecraft:dark_oak_log')
        for y in range(2, 6):
            log(b, x, y, 28, 'minecraft:dark_oak_log')
    for x in range(x0 - 1, x1 + 2):
        for z in range(27, 32):
            b.set(x, 6, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (x0, x1):
        lantern(b, x, 5, 29, hanging=True)
    for x in xs:
        b.set(x, 5, 28, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')



def cavalry_ring():
    """33x33 round horse arena: six knights on the track, crossbows on two platforms, caltrops at the gate and guns
    at the command pavilion."""
    n, c = 33, 16
    b = Camp('CAVALRY_ENCAMPMENT', 'cavalry_ring', n, 13)
    b.ground('minecraft:coarse_dirt', 'minecraft:grass_block', 'minecraft:dirt_path')
    for x in range(n):
        for z in range(n):
            if b.block(x, 0, z) == 'minecraft:grass_block':
                b.set(x, 0, z, 'minecraft:grass_block', snowy=False)
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if 9.5 <= d <= 13.5:
                b.set(x, 0, z, 'minecraft:dirt_path')
    for x, z in circle(c, c, 15):
        if not (15 <= x <= 17 and z > c):
            fence(b, x, 1, z, 'minecraft:oak_fence')
            if (x + z) % 4 == 0:
                log(b, x, 1, z, 'minecraft:oak_log')
                log(b, x, 2, z, 'minecraft:oak_log')
    for x in (15, 16, 17):
        fence_gate(b, x, 1, 31, 'oak')
    lantern(b, 14, 2, 31)
    lantern(b, 18, 2, 31)
    # the command pavilion, north: striped wool on posts, the standard and the warlord inside
    for x in range(12, 21):
        for z in range(4, 10):
            if x in (12, 20) and z in (4, 9):
                for y in (1, 2, 3):
                    fence(b, x, y, z, 'minecraft:dark_oak_fence')
            b.set(x, 4, z, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')
            if x in (12, 20) or z == 4:
                b.set(x, 3, z, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')
    for x in (13, 19):
        log(b, x, 1, 10, 'minecraft:stripped_dark_oak_log')
        gallery(b, x, 2, 10, 'south')
    b.standard(16, 6)
    # tilting rail down the middle, stables either side
    for z in range(13, 24):
        fence(b, c, 1, z, 'minecraft:spruce_fence')
    for x0 in (5, 23):
        for x in range(x0, x0 + 5):
            for z in (13, 19):
                fence(b, x, 1, z, 'minecraft:spruce_fence')
        for z in (15, 17):
            b.set(x0 + 2, 1, z, 'minecraft:hay_block', axis='y')
    platforms = [_cav_tower(b, x0, 6) for x0 in (6, 24)]
    _cav_stand(b, 2, 7)
    _cav_stand(b, 25, 30)
    for x in range(14, 19):   # a raised dais under the pavilion
        for z in (4, 5):
            b.set(x, 1, z, 'minecraft:spruce_planks')
    for x in (13, 19):
        b.set(x, 1, 4, 'minecraft:spruce_stairs', **stairs('east' if x == 13 else 'west'))
        b.set(x, 1, 5, 'minecraft:spruce_stairs', **stairs('east' if x == 13 else 'west'))
    for x, z in ((14, 28), (18, 28), (16, 27), (13, 25), (19, 25)):
        spikes(b, (x, z))
    keg_wire(b, 5, 21, 3, 'minecraft:oak_log')
    keg_wire(b, 23, 21, 3, 'minecraft:oak_log')
    alarm(b, 16, 30)
    campfire(b, 10, 25)
    campfire(b, 22, 25)
    for x, z in ((6, 16), (26, 16), (11, 24), (21, 24), (11, 8), (21, 8)):
        b.unit('knight', x, z)
    for spot in platforms:
        b.post('crossbowman', *spot)
    b.unit('crossbowman', 13, 12)
    b.unit('crossbowman', 19, 12)
    b.unit('spearman', 14, 7)
    b.unit('spearman', 18, 7)
    b.unit('militia', 16, 26)
    b.warlord(16, 8)
    b.spare((9, 11), (23, 11), (12, 28), (20, 28), (16, 12))
    b.report('A round horse arena: six knights ride the track, crossbows on two platforms, caltrops at the gate and two '
             'guns at the pavilion. Spears and a narrow gate stop a charge.',
             'Una arena redonda para caballos: seis caballeros en la pista, ballestas en dos plataformas, abrojos en la '
             'puerta y dos cañones en el pabellón. Las lanzas y una puerta estrecha frenan la carga.')
    b.done()


def cavalry_paddocks():
    """35x35 horse camp: two paddocks of knights either side of a long fenced run lined with guns, a command yard at
    the end. The relays are the red blocks at the foot of each gun post."""
    n = 35
    b = Camp('CAVALRY_ENCAMPMENT', 'cavalry_paddocks', n, 11)
    b.ground('minecraft:coarse_dirt', 'minecraft:dirt_path', 'minecraft:podzol')
    for x, z in ring(1, 33):
        fence(b, x, 1, z, 'minecraft:oak_fence')
        if (x + z) % 4 == 0:
            log(b, x, 1, z, 'minecraft:oak_log')
            log(b, x, 2, z, 'minecraft:oak_log')
    for x in (16, 17, 18):
        fence_gate(b, x, 1, 33, 'oak')
    for x in (15, 19):   # a fence arch over the gate: log posts, a beam, a hanging lantern
        for y in (1, 2, 3, 4):
            log(b, x, y, 33, 'minecraft:dark_oak_log')
    for x in range(15, 20):
        log(b, x, 5, 33, 'minecraft:stripped_dark_oak_log', 'x')
    lantern(b, 15, 4, 32)
    lantern(b, 19, 4, 32)
    for x in (16, 18):
        lantern(b, x, 4, 33, hanging=True)
    # the run: x 16..18 from the gate to z 17, fenced, with gun posts every four blocks on both sides
    for z in range(17, 33):
        for x in (15, 19):
            fence(b, x, 1, z, 'minecraft:spruce_fence')
            fence(b, x, 2, z, 'minecraft:spruce_fence')
        for x in range(16, 19):
            b.set(x, 0, z, 'minecraft:dirt_path')
    for z in (20, 24, 28):
        for x, facing in ((15, 'east'), (19, 'west')):
            b.set(x, 1, z, 'minecraft:stripped_spruce_log', axis='y')
            gallery(b, x, 2, z, facing)
            b.set(x, 1, z, 'warbands:war_relay', powered=False, pulse=True)
            b.set(x, 3, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    spikes(b, (17, 22), (16, 26), (18, 30))
    alarm(b, 17, 32)
    # paddocks: fenced squares either side, gates into the run (the knights come out when the alarm sounds)
    for x0, x1 in ((3, 13), (21, 31)):
        for x, z in [(x, z) for x in range(x0, x1 + 1) for z in (17, 31)] + [(x, z) for x in (x0, x1) for z in range(17, 32)]:
            fence(b, x, 1, z, 'minecraft:spruce_fence')
        for x in range(x0 + 1, x1):   # grass inside the paddock, worn to dirt in the middle
            for z in range(18, 31):
                b.set(x, 0, z, varied(x, 0, z, 'minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:podzol'),
                      **({'snowy': False} if (x * 31 + z) % 7 else {}))
    # feeding stations, grouped: a hay stack, a trough of hay, a water cauldron and a barrel
    for sx, d in ((4, 1), (30, -1)):
        for i in range(2):
            b.set(sx + d * i, 1, 24, 'minecraft:hay_block', axis='x')
        b.set(sx, 2, 24, 'minecraft:hay_block', axis='x')
        b.set(sx, 1, 25, 'minecraft:barrel', facing='up', open=False)
        b.set(sx + d, 1, 25, 'minecraft:cauldron')
        b.set(sx, 1, 23, 'minecraft:hay_block', axis='z')
    for sx in (12, 22):
        for y in (1, 2):
            b.set(sx, y, 20, 'minecraft:hay_block', axis='y')
        b.set(sx, 1, 21, 'minecraft:hay_block', axis='y')
        b.set(sx, 1, 22, 'minecraft:barrel', facing='up', open=False)
    for x, z in ((7, 19), (25, 19), (7, 29), (25, 29)):   # low jump: two posts and a rail
        b.set(x, 1, z, 'minecraft:oak_log', axis='y')
        b.set(x + 2, 1, z, 'minecraft:oak_log', axis='y')
        b.set(x + 1, 1, z, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for z in (18, 30):   # paddock gates onto the run, and gaps in the run's fence
        fence_gate(b, 13, 1, z, 'spruce', 'east')
        fence_gate(b, 21, 1, z, 'spruce', 'east')
        for x in (13, 21):   # a fence arch over each paddock gate
            for zz in (z - 1, z + 1):
                for y in (1, 2, 3):
                    log(b, x, y, zz, 'minecraft:oak_log')
            for zz in (z - 1, z, z + 1):
                log(b, x, 4, zz, 'minecraft:stripped_oak_log', 'z')
        for x in (15, 19):
            b.set(x, 1, z, 'minecraft:air')
            b.set(x, 2, z, 'minecraft:air')
    # command yard, north
    for x in range(12, 23):
        for z in range(3, 12):
            b.set(x, 0, z, 'minecraft:dirt_path' if (x + z) % 5 else 'minecraft:coarse_dirt')
    # the stable barn, north-west: stalls, hay, barrels, hanging lanterns
    _gable_barn(b, 3, 11, 3, 9)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            b.set(x, y, 9, 'minecraft:air')
        b.set(x, 4, 9, 'minecraft:stripped_dark_oak_log', axis='x')
    for x in (4, 10):
        for y in (2, 3):
            b.set(x, y, 9, 'minecraft:glass_pane', **g.pane(False))
    for x in (5, 9):
        for z in range(4, 9):
            b.set(x, 4, z, 'minecraft:stripped_dark_oak_log', axis='z')
        lantern(b, x, 3, 6, hanging=True)
    for x in (6, 8):
        for z in (4, 5):
            fence(b, x, 1, z, 'minecraft:spruce_fence')
    for x, z in ((4, 4), (7, 4), (10, 4)):
        b.set(x, 1, z, 'minecraft:hay_block', axis='y')
    b.set(4, 2, 4, 'minecraft:hay_block', axis='y')
    b.set(4, 1, 8, 'minecraft:barrel', facing='up', open=False)
    b.set(4, 1, 7, 'minecraft:barrel', facing='up', open=False)
    b.set(10, 1, 8, 'minecraft:hay_block', axis='x')
    b.set(10, 1, 7, 'minecraft:hay_block', axis='x')
    b.set(10, 2, 8, 'minecraft:hay_block', axis='x')
    for x, z in ((5, 10), (9, 10)):   # lanterns and a water trough outside the barn door
        fence(b, x, 1, z, 'minecraft:dark_oak_fence')
        lantern(b, x, 2, z)
    for x in (6, 7, 8):
        b.set(x, 0, 10, 'minecraft:dirt_path')
    # the judges' stand, north-east: a raised platform, steps, benches and a striped awning
    for x in range(23, 32):
        for z in range(3, 6):
            for y in (1, 2):
                b.set(x, y, z, 'minecraft:spruce_planks' if y == 2 and z < 5 else varied(x, y, z, 'minecraft:stripped_spruce_log', 'minecraft:spruce_planks'))
    for x in (23, 31):
        for z in (3, 5):
            for y in (3, 4, 5):
                fence(b, x, y, z, 'minecraft:dark_oak_fence')
    for x in (27,):
        b.set(x, 2, 5, 'minecraft:spruce_stairs', **stairs('north'))
        b.set(x, 1, 6, 'minecraft:spruce_stairs', **stairs('north'))
    for x in range(23, 32):
        for z in range(3, 6):
            b.set(x, 6, z, 'minecraft:red_wool' if x % 2 else 'minecraft:white_wool')
    for x in (23, 31):
        b.set(x, 3, 4, 'minecraft:spruce_fence', **dict(north=False, south=False, east=False, west=False, waterlogged=False))
    for x in (25, 26, 28, 29):
        b.set(x, 3, 3, 'minecraft:spruce_stairs', **stairs('south'))
    for x in range(24, 31):
        if x != 27:
            fence(b, x, 3, 5, 'minecraft:spruce_fence')
    for x in (24, 30):
        lantern(b, x, 5, 5, hanging=True)
        fence(b, x, 3, 5, 'minecraft:dark_oak_fence')
        fence(b, x, 4, 5, 'minecraft:dark_oak_fence')
    fill(b, 24, 0, 6, 30, 0, 6, 'minecraft:dirt_path')
    b.standard(17, 6)
    for x in (15, 19):
        fence(b, x, 1, 6, 'minecraft:dark_oak_fence')
        lantern(b, x, 2, 6)
    platforms = [tower(b, x0, 11, 5, 'minecraft:spruce_log', 'minecraft:spruce_slab') for x0 in (10, 22)]
    campfire(b, 17, 13)
    for x, z in ((6, 21), (10, 25), (6, 29), (28, 21), (24, 25), (28, 29)):
        b.unit('knight', x, z)
    b.unit('militia', 17, 18)
    b.unit('militia', 17, 15)
    b.unit('spearman', 14, 8)
    b.unit('spearman', 20, 8)
    b.unit('crossbowman', 6, 12)
    b.unit('crossbowman', 28, 12)
    for spot in platforms:
        b.post('crossbowman', *spot)
    b.warlord(17, 9)
    b.spare((9, 14), (25, 14), (4, 15), (30, 15), (17, 11))
    b.report('Two paddocks of knights either side of a long fenced run lined with guns; their relays are the red blocks '
             'at the foot of each post. Or break through a paddock fence and fight the knights first.',
             'Dos cercados de caballeros a los lados de un largo pasillo vallado lleno de cañones; sus relés son los '
             'bloques rojos al pie de cada poste. O rompe la valla de un cercado y lucha antes con los caballeros.')
    b.done()


def _bars(b, x, y, z):
    b.set(x, y, z, 'minecraft:iron_bars', north=False, south=False, east=False, west=False, waterlogged=False)


def _stone_wall(b, x, y, z, name='minecraft:stone_brick_wall'):
    b.set(x, y, z, name, up=True, north='none', south='none', east='none', west='none', waterlogged=False)


def _crypt_surface(b):
    """The catacomb's surface: a chapel over the stair, a bell tower with a spire behind it, a graveyard. Ground is
    y4, so feet stand at y5."""
    wallb = ('minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:mossy_stone_bricks')
    x0, x1, z0, z1 = 11, 19, 23, 30
    for x in range(x0, x1 + 1):   # paved floor, except over the stair shaft
        for z in range(z0, z1 + 1):
            if not (14 <= x <= 16 and z >= 27):
                b.set(x, 4, z, 'minecraft:polished_deepslate')
    for x in range(x0, x1 + 1):   # walls y5..9, corner and pilaster posts
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                corner = x in (x0, x1) and z in (z0, z1)
                pil = (x in (x0, x1) and z in (26, 28)) or (z in (z0, z1) and x in (13, 17))
                for y in range(5, 10):
                    if corner or pil:
                        b.set(x, y, z, 'minecraft:polished_deepslate')
                    elif y == 5:
                        b.set(x, y, z, varied(x, y, z, 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks'))
                    else:
                        b.set(x, y, z, varied(x, y, z, *wallb))
    for x in (x0 - 1, x1 + 1):   # buttresses at the front and back corners
        for z in (z0, z1):
            for y in range(5, 8):
                b.set(x, y, z, 'minecraft:stone_bricks')
            b.set(x, 8, z, 'minecraft:stone_brick_stairs', **stairs('west' if x == x0 - 1 else 'east'))
    for z in (24, 25, 27, 29):   # arched windows: bars
        for x in (x0, x1):
            if z in (25, 27, 29) and z not in (26, 28):
                for y in (6, 7, 8):
                    _bars(b, x, y, z)
    for x in (14, 15, 16):   # the door: 3 wide, 3 high, a lintel; a round window in the gable
        for y in (5, 6, 7):
            b.set(x, y, z1, 'minecraft:air')
        b.set(x, 8, z1, 'minecraft:polished_deepslate')
    for y in (11, 12):
        _bars(b, 15, y, z1)
    for x in range(x0, x1 + 1):   # ceiling, then stair layers up to a slab ridge, gables filled
        for z in range(z0, z1 + 1):
            b.set(x, 10, z, 'minecraft:deepslate_bricks')
    for k in range(5):
        y = 10 + k
        for z in range(z0, z1 + 1):
            b.set(x0 - 1 + k, y, z, 'minecraft:deepslate_tile_stairs', **stairs('east'))
            b.set(x1 + 1 - k, y, z, 'minecraft:deepslate_tile_stairs', **stairs('west'))
        for x in range(x0 + k, x1 - k + 1):
            for z in (z0, z1):
                if not (x == 15 and z == z1 and y in (11, 12)):
                    b.set(x, y, z, 'minecraft:deepslate_bricks')
    for z in range(z0, z1 + 1):
        b.set(15, 14, z, 'minecraft:deepslate_tile_slab', type='bottom', waterlogged=False)
    for z in (25, 27, 29):   # benches and candles inside, soul lanterns from the ceiling
        b.set(12, 5, z, 'minecraft:stone_brick_stairs', **stairs('east'))
        b.set(18, 5, z, 'minecraft:stone_brick_stairs', **stairs('west'))
    for x, z in ((12, 26), (18, 26), (12, 28), (18, 28)):
        b.set(x, 5, z, 'minecraft:candle', candles=3, lit=True, waterlogged=False)
    for x, z in ((13, 25), (17, 25)):
        lantern(b, x, 9, z, soul=True, hanging=True)
    # the bell tower behind the chapel: 5x5 hollow, slits, a stepped spire and a lightning rod
    tx0, tx1, tz0, tz1 = 13, 17, 18, 22
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            if x in (tx0, tx1) or z in (tz0, tz1):
                corner = x in (tx0, tx1) and z in (tz0, tz1)
                for y in range(5, 18):
                    b.set(x, y, z, 'minecraft:polished_deepslate' if corner or y in (9, 13, 17)
                          else varied(x, y, z, *wallb))
    for y in (7, 11, 15):
        for x, z in ((15, tz0), (15, tz1), (tx0, 20), (tx1, 20)):
            _bars(b, x, y, z)
    for x, z in g.rect_ring(tx0, tz0, tx1, tz1):
        f = 'south' if z == tz0 else 'north' if z == tz1 else 'east' if x == tx0 else 'west'
        if x in (tx0, tx1) and z in (tz0, tz1):
            b.set(x, 18, z, 'minecraft:deepslate_tiles')
        else:
            b.set(x, 18, z, 'minecraft:deepslate_tile_stairs', **stairs(f))
    for x in range(tx0 + 1, tx1):
        for z in range(tz0 + 1, tz1):
            b.set(x, 18, z, 'minecraft:deepslate_tiles')
    for x, z in g.rect_ring(14, 19, 16, 21):
        f = 'south' if z == 19 else 'north' if z == 21 else 'east' if x == 14 else 'west'
        if x in (14, 16) and z in (19, 21):
            b.set(x, 19, z, 'minecraft:deepslate_tiles')
        else:
            b.set(x, 19, z, 'minecraft:deepslate_tile_stairs', **stairs(f))
    b.set(15, 19, 20, 'minecraft:deepslate_tiles')
    for y in (20, 21, 22):
        _stone_wall(b, 15, y, 20, 'minecraft:deepslate_tile_wall')
    b.set(15, 23, 20, 'minecraft:lightning_rod', facing='up', powered=False, waterlogged=False)
    # a path from the chapel north through the graveyard, gravel round the buildings
    for z in range(2, 23):
        for x in ((14, 15, 16) if z > 8 else (15,)):
            if not (tx0 <= x <= tx1 and tz0 <= z <= tz1):
                b.set(x, 4, z, 'minecraft:dirt_path' if (x + z) % 4 else 'minecraft:gravel')
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 1, z1 + 1):
            if not (x0 - 1 <= x <= x1 + 1) and abs(x - 15) <= 7:
                b.set(x, 4, z, 'minecraft:gravel' if (x + z) % 3 else 'minecraft:dirt_path')
    # graves: leaning stones of different shapes either side of the path, dead trees, a fence along the north edge
    for gx in range(3, 28, 4):
        for gz in range(4, 27, 5):
            if abs(gx - 15) <= 3 or (7 <= gx <= 23 and gz >= 17):
                continue
            kind = (gx * 7 + gz * 3) % 5
            if kind == 0:
                _stone_wall(b, gx, 5, gz, 'minecraft:mossy_stone_brick_wall')
                b.set(gx, 6, gz, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
            elif kind in (1, 2):
                _stone_wall(b, gx, 5, gz, 'minecraft:cobblestone_wall')
                b.set(gx, 4, gz + 1, 'minecraft:coarse_dirt')
            elif kind == 3:   # a cross
                _stone_wall(b, gx, 5, gz)
                _stone_wall(b, gx, 6, gz)
                b.set(gx + 1, 6, gz, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
                b.set(gx - 1, 6, gz, 'minecraft:stone_brick_slab', type='bottom', waterlogged=False)
            else:
                b.set(gx, 5, gz, 'minecraft:dead_bush')
    for x, z in ((5, 11), (25, 14), (4, 22)):   # dead trees: a bare trunk with stubs
        for y in range(5, 10):
            log(b, x, y, z, 'minecraft:dark_oak_log')
        b.set(x + 1, 8, z, 'minecraft:dark_oak_log', axis='x')
        b.set(x - 1, 7, z, 'minecraft:dark_oak_log', axis='x')
    for x in range(1, 30):
        if x % 4 == 1:
            _stone_wall(b, x, 5, 1)
        else:
            b.set(x, 5, 1, 'minecraft:iron_bars', north=False, south=False, east=True, west=True, waterlogged=False)


def crypt_catacomb():
    """31x31 catacomb sunk four into the ground: a maze of dark corridors under a stone ceiling, the dead in its rooms,
    sculk sensors at the turns, and a central tomb whose portcullis drops when the alarm sounds."""
    n, sink = 31, 4
    b = Camp('SUNKEN_CRYPT', 'crypt_catacomb', n, 24, sink)
    b.ground('minecraft:deepslate_tiles', 'minecraft:cracked_deepslate_tiles', 'minecraft:deepslate_bricks')
    for x in range(n):   # packed earth round the rim, up to the ground
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                for y in range(1, sink + 1):
                    b.set(x, y, z, 'minecraft:coarse_dirt' if y == sink else 'minecraft:dirt')
    # solid block 1..29, then carve a 7x7 maze of 3x3 rooms (room i spans 2+4i .. 4+4i)
    for x in range(1, 30):
        for y in range(1, 4):
            for z in range(1, 30):
                b.set(x, y, z, varied(x, y, z, *DEEP))
    for x in range(1, 30):
        for z in range(1, 30):
            b.set(x, 4, z, varied(x, 4, z, 'minecraft:coarse_dirt', 'minecraft:rooted_dirt', 'minecraft:podzol'))

    def room(i, j):
        for x in range(2 + 4 * i, 5 + 4 * i):
            for z in range(2 + 4 * j, 5 + 4 * j):
                for y in range(1, 4):
                    b.set(x, y, z, 'minecraft:air')

    def door(i, j, di, dj):
        if di:
            x = 5 + 4 * min(i, i + di)
            for z in range(2 + 4 * j, 5 + 4 * j):
                for y in range(1, 3):
                    b.set(x, y, z, 'minecraft:air')
        else:
            z = 5 + 4 * min(j, j + dj)
            for x in range(2 + 4 * i, 5 + 4 * i):
                for y in range(1, 3):
                    b.set(x, y, z, 'minecraft:air')

    tomb = {(i, j) for i in (2, 3, 4) for j in (2, 3, 4)}
    rng = random.Random(1717)
    seen = set(tomb)
    stack = [(3, 6)]
    seen.add((3, 6))
    while stack:
        i, j = stack[-1]
        options = [(di, dj) for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1))
                   if 0 <= i + di < 7 and 0 <= j + dj < 7 and (i + di, j + dj) not in seen]
        if not options:
            stack.pop()
            continue
        di, dj = rng.choice(options)
        door(i, j, di, dj)
        seen.add((i + di, j + dj))
        stack.append((i + di, j + dj))
    for i in range(7):
        for j in range(7):
            if (i, j) not in tomb:
                room(i, j)
    for extra in (((1, 1), (1, 0)), ((5, 1), (-1, 0)), ((1, 5), (0, 1)), ((5, 5), (0, 1)), ((0, 3), (0, 1))):
        (i, j), (di, dj) = extra
        door(i, j, di, dj)   # a few loops, so it's a maze with choices, not a single path
    # the tomb: rooms (2..4, 2..4) as one chamber 10..20, portcullis in its south wall into room (3, 5)
    for x in range(10, 21):
        for z in range(10, 21):
            for y in range(1, 4):
                b.set(x, y, z, 'minecraft:air')
    piston_gate(b, 14, 21)
    for x in (14, 15):
        b.set(x, 3, 21, 'minecraft:deepslate_bricks')
    room(3, 5)
    door(3, 5, 0, 1)
    for x, z in ((11, 11), (19, 11), (11, 19), (19, 19)):
        for y in range(1, 4):
            b.set(x, y, z, 'minecraft:polished_deepslate')
    for x in range(13, 18):
        b.set(x, 1, 12, 'minecraft:deepslate_tile_slab', type='bottom', waterlogged=False)
    b.standard(15, 13)
    lantern(b, 12, 1, 15, soul=True)
    lantern(b, 18, 1, 15, soul=True)
    # the stair from the rim down into room (3, 6), through the south wall
    for x in (14, 15, 16):
        for y, z in ((4, 30), (3, 29), (2, 28), (1, 27)):
            b.set(x, y, z, 'minecraft:stone_brick_stairs', **stairs('south'))
            for above in range(y + 1, 5):
                b.set(x, above, z, 'minecraft:air')
    # skylights and lights: iron grates in the ceiling over some rooms, soul lanterns, candles
    for i, j in ((0, 0), (6, 0), (0, 6), (6, 6), (3, 0), (0, 3), (6, 3)):
        b.set(3 + 4 * i, 4, 3 + 4 * j, 'minecraft:iron_bars', north=False, south=False, east=False, west=False,
              waterlogged=False)
        lantern(b, 3 + 4 * i, 3, 3 + 4 * j, soul=True, hanging=True)
    for i, j in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.set(2 + 4 * i, 1, 2 + 4 * j, 'minecraft:candle', candles=2, lit=True, waterlogged=False)
    _crypt_surface(b)
    # sculk sensors in corner rooms, spikes, a keg wire before the tomb
    for i, j in ((1, 2), (5, 2), (1, 4), (5, 4), (3, 6)):
        sculk(b, 4 + 4 * i, 4 + 4 * j)
    spikes(b, (14, 23), (16, 24))
    alarm(b, 15, 26)
    for x, z, mob in ((3, 3, 'skeleton'), (27, 3, 'skeleton'), (3, 27, 'zombie'), (27, 27, 'zombie'),
                      (11, 3, 'skeleton'), (19, 27, 'zombie'), (3, 15, 'husk'), (27, 15, 'husk')):
        b.mob(mob, x, z)
    b.warlord(15, 16)
    for x, z in ((12, 17), (18, 17), (15, 19)):
        b.unit('militia', x, z)
    b.unit('spearman', 15, 23)
    b.spare((7, 7), (23, 7), (7, 23), (23, 23), (15, 3))
    b.report('A catacomb under the ground: a maze of dark corridors with sculk sensors in some rooms and the dead in '
             'others. The tomb in the middle drops a portcullis; its relays sit in the tomb wall beside the gate.',
             'Unas catacumbas bajo tierra: un laberinto de pasillos oscuros con sensores de sculk en unas salas y '
             'muertos en otras. La tumba central baja un rastrillo; sus relés están en el muro junto a la puerta.')
    b.done()


def crypt_flooded():
    """27x27 flooded crypt sunk three into the ground: islands joined by planks that drop away when the alarm sounds,
    drowned in the channels, guns in the pillars, the standard on the central shrine."""
    n, sink = 27, 3
    b = Camp('SUNKEN_CRYPT', 'crypt_flooded', n, 12, sink)
    b.ground('minecraft:deepslate_bricks', 'minecraft:cracked_deepslate_bricks', 'minecraft:mossy_stone_bricks')
    for x in range(n):
        for z in range(n):
            if x in (0, n - 1) or z in (0, n - 1):
                for y in range(1, sink + 1):
                    b.set(x, y, z, 'minecraft:coarse_dirt' if y == sink else 'minecraft:dirt')
    wall(b, list(ring(1, 25)), 1, sink, *STONE)
    # channels: a ring of water two wide round the middle island and along the outer walls
    for x in range(2, 25):
        for z in range(2, 25):
            if x in (5, 6, 20, 21) or z in (5, 6, 20, 21):
                b.set(x, 0, z, 'minecraft:water', level=0)
    for x in range(9, 18):   # the shrine, raised one
        for z in range(9, 18):
            if (x - 13) ** 2 + (z - 13) ** 2 <= 12:
                b.set(x, 0, z, 'minecraft:polished_deepslate')
                b.set(x, 1, z, 'minecraft:deepslate_tiles')
    b.standard(13, 12, 2)
    lantern(b, 11, 2, 13, soul=True)
    lantern(b, 15, 2, 13, soul=True)
    # plank crossings flush with the floor; the south one is rigged (its machinery sits in the channel, east)
    for x, z in ((13, 20), (13, 5), (13, 6), (5, 13), (6, 13), (20, 13), (21, 13)):
        b.set(x, 0, z, 'minecraft:dark_oak_planks')
    rigged_floor(b, 13, 0, 21, 'east')
    # pillars with guns aimed along the channels
    for x, z, facing in ((9, 9, 'south'), (17, 9, 'south'), (9, 17, 'east'), (17, 17, 'west')):
        b.set(x, 1, z, 'minecraft:polished_deepslate')
        gallery(b, x, 3, z, facing)
        b.set(x, 4, z, 'minecraft:polished_deepslate')
        b.set(x, 5, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    # stair trench down the south rim
    for x in (12, 13, 14):
        for y, z in ((3, 25), (2, 24), (1, 23)):
            b.set(x, y, z, 'minecraft:stone_brick_stairs', **stairs('south'))
            for above in range(y + 1, 5):
                b.set(x, above, z, 'minecraft:air')
    alarm(b, 13, 22, 1)
    for x, z in ((3, 3), (23, 3), (3, 23), (23, 23)):
        b.set(x, 1, z, 'minecraft:candle', candles=3, lit=True, waterlogged=False)
    sculk(b, 9, 3)
    sculk(b, 17, 3)
    b.mob('drowned', 3, 9)
    b.mob('drowned', 23, 9)
    b.mob('drowned', 9, 23)
    b.mob('skeleton', 3, 17)
    b.mob('skeleton', 23, 17)
    b.warlord(13, 14, 2)
    for x, z in ((11, 11), (15, 11), (13, 16)):
        b.unit('militia', x, z, y=2)
    b.unit('spearman', 8, 22)
    b.unit('spearman', 18, 22)
    b.spare((3, 4), (23, 4), (8, 8), (18, 8))
    b.report('A flooded crypt sunk into the ground: islands joined by planks, one of which drops away when the alarm '
             'sounds; drowned in the channels and guns in the pillars.',
             'Una cripta inundada bajo tierra: islas unidas por tablones, uno de los cuales cae cuando suena la '
             'alarma; ahogados en los canales y cañones en los pilares.')
    b.done()


def illager_watchtower():
    """35x35 raiders' hall and a tall watchtower joined by a rope bridge from the hall roof; pillagers on the tower
    top, the bridge planks drop when the alarm sounds, ladders inside the tower."""
    n = 35
    b = Camp('ILLAGER_HALL', 'illager_watchtower', n, 26)
    b.ground('minecraft:cobblestone', 'minecraft:gravel', 'minecraft:coarse_dirt')
    for x, z in ring(1, 33):
        top = 3 if (x + z) % 2 else 4
        for y in range(1, top + 1):
            log(b, x, y, z, 'minecraft:dark_oak_log')
    for x in (16, 17, 18):
        for y in range(1, 4):
            b.set(x, y, 33, 'minecraft:air')
        log(b, x, 4, 33, 'minecraft:dark_oak_log', 'x')
    for x, z in ring(1, 33):   # a stone footing under the palisade
        b.set(x, 1, z, varied(x, 1, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
    for x, z in ((1, 1), (33, 1), (1, 33), (33, 33), (15, 33), (19, 33)):   # taller corner and gate posts, lit
        for y in range(1, 8):
            log(b, x, y, z, 'minecraft:dark_oak_log')
        b.set(x, 8, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
        lantern(b, x, 9, z, soul=(x, z) not in ((15, 33), (19, 33)))
    for x in (16, 18):
        lantern(b, x, 3, 33, hanging=True)
    for x in range(16, 19):   # a banner-like beam over the gate
        b.set(x, 5, 33, 'minecraft:red_wool' if x != 17 else 'minecraft:black_wool')
    # the hall: 13x11, stone footing, log posts, shuttered windows, a flat fighting deck at y8 with a cornice
    hx0, hx1, hz0, hz1 = 3, 15, 13, 23
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            edge = x in (hx0, hx1) or z in (hz0, hz1)
            if edge:
                post = (x in (hx0, hx1) and (z - hz0) % 5 == 0) or (z in (hz0, hz1) and (x - hx0) % 4 == 0)
                for y in range(1, 8):
                    if y <= 2:
                        b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
                    elif post:
                        b.set(x, y, z, 'minecraft:dark_oak_log', axis='y')
                    elif y == 7:
                        b.set(x, y, z, 'minecraft:stripped_dark_oak_log', axis='x' if z in (hz0, hz1) else 'z')
                    else:
                        b.set(x, y, z, 'minecraft:dark_oak_planks')
            b.set(x, 8, z, 'minecraft:dark_oak_planks')
            b.set(x, 0, z, 'minecraft:dark_oak_planks' if not edge else 'minecraft:cobblestone')
    for x, z in g.rect_ring(hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1):   # cornice: upside-down stairs under the deck edge
        if x in (hx0 - 1, hx1 + 1) and z in (hz0 - 1, hz1 + 1):
            b.set(x, 8, z, 'minecraft:dark_oak_slab', type='top', waterlogged=False)
            continue
        f = 'south' if z == hz0 - 1 else 'north' if z == hz1 + 1 else 'east' if x == hx0 - 1 else 'west'
        b.set(x, 8, z, 'minecraft:dark_oak_stairs', **stairs(f, 'top'))
    crenels(b, [(x, z) for x in range(hx0, hx1 + 1) for z in (hz0, hz1)] + [(x, z) for x in (hx0, hx1) for z in range(hz0, hz1 + 1)],
            9, 'minecraft:dark_oak_log')
    b.set(hx1, 9, 17, 'minecraft:air')   # keep the bridge lanes open at the hall edge
    for x in (8, 9, 10):
        for y in (1, 2, 3):
            b.set(x, y, hz1, 'minecraft:air')
    for x in (7, 11):   # lanterns on posts beside the door
        fence(b, x, 1, hz1 + 1, 'minecraft:dark_oak_fence')
        fence(b, x, 2, hz1 + 1, 'minecraft:dark_oak_fence')
        lantern(b, x, 3, hz1 + 1)
    for z in (15, 19):   # windows in the side walls
        for x in (hx0, hx1):
            for y in (4, 5):
                b.set(x, y, z, 'minecraft:glass_pane', **g.pane(True))
    for x in (5, 12):
        for y in (4, 5):
            b.set(x, y, hz1, 'minecraft:glass_pane', **g.pane(False))
            b.set(x, y, hz0, 'minecraft:glass_pane', **g.pane(False))
    for x in (6, 12):
        for z in (16, 20):
            for y in range(1, 8):
                log(b, x, y, z, 'minecraft:dark_oak_log')
    ladder(b, 14, 15, 1, 8, 'west')
    b.set(14, 8, 15, 'minecraft:air')
    # hall furnishing: two beds, a long table with benches, barrels, lanterns from the ceiling
    for z in (17, 21):
        b.set(4, 1, z, 'minecraft:red_bed', facing='south', part='foot', occupied=False)
        b.set(4, 1, z + 1, 'minecraft:red_bed', facing='south', part='head', occupied=False)
    for z in (14,):
        b.set(4, 1, z, 'minecraft:barrel', facing='east', open=False)
        b.set(4, 2, z, 'minecraft:barrel', facing='east', open=False)
    for x in (13, 14):
        b.set(x, 1, 21, 'minecraft:barrel', facing='north', open=False)
        b.set(x, 1, 22, 'minecraft:barrel', facing='north', open=False)
    for x in (5, 6, 7):
        b.set(x, 1, 22, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
        b.set(x, 1, 21, 'minecraft:spruce_stairs', **stairs('south'))
    for x in (11, 12):
        b.set(x, 1, 22, 'minecraft:spruce_slab', type='bottom', waterlogged=False)
    for x, z in ((7, 18), (11, 18), (9, 21)):
        lantern(b, x, 7, z, hanging=True)
    b.standard(9, 16)
    # the tower: 5x5 shaft on a stone footing to y17, slits, a corbelled 7x7 deck at y18 with crenels and a mast
    tx0, tz0 = 24, 16
    for x in range(tx0, tx0 + 5):
        for z in range(tz0, tz0 + 5):
            edge = x in (tx0, tx0 + 4) or z in (tz0, tz0 + 4)
            corner = x in (tx0, tx0 + 4) and z in (tz0, tz0 + 4)
            for y in range(1, 18):
                if edge:
                    if y < 4:
                        b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
                    elif corner or y in (9, 14, 17):
                        b.set(x, y, z, 'minecraft:dark_oak_log', axis='y' if corner else ('x' if z in (tz0, tz0 + 4) else 'z'))
                    else:
                        b.set(x, y, z, 'minecraft:dark_oak_planks')
            b.set(x, 18, z, 'minecraft:dark_oak_planks')
    for x in range(tx0 - 1, tx0 + 6):   # the deck overhangs by one on a ring of upside-down stairs
        for z in range(tz0 - 1, tz0 + 6):
            if x in (tx0 - 1, tx0 + 5) or z in (tz0 - 1, tz0 + 5):
                corner = x in (tx0 - 1, tx0 + 5) and z in (tz0 - 1, tz0 + 5)
                b.set(x, 18, z, 'minecraft:dark_oak_planks')
                if not corner:
                    f = 'south' if z == tz0 - 1 else 'north' if z == tz0 + 5 else 'east' if x == tx0 - 1 else 'west'
                    b.set(x, 17, z, 'minecraft:dark_oak_stairs', **stairs(f, 'top'))
    crenels(b, list(g.rect_ring(tx0 - 1, tz0 - 1, tx0 + 5, tz0 + 5)), 19, 'minecraft:dark_oak_log')
    for x, z in ((tx0 - 1, tz0 - 1), (tx0 + 5, tz0 - 1), (tx0 - 1, tz0 + 5), (tx0 + 5, tz0 + 5)):   # corner braziers
        b.set(x, 19, z, 'minecraft:dark_oak_log', axis='y')
        b.set(x, 20, z, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for y in range(19, 24):   # a mast with a lantern on top: the beacon you see from the town
        fence(b, tx0 + 2, y, tz0 + 2, 'minecraft:dark_oak_fence')
    lantern(b, tx0 + 2, 24, tz0 + 2)
    for y in (6, 11, 15):   # arrow slits on every face
        for x, z in ((tx0 + 2, tz0), (tx0 + 2, tz0 + 4), (tx0, tz0 + 2), (tx0 + 4, tz0 + 2)):
            if not (y == 6 and z == tz0 + 4):
                b.set(x, y, z, 'minecraft:iron_bars', north=False, south=False, east=False, west=False, waterlogged=False)
    for y in (1, 2):
        b.set(tx0 + 2, y, tz0 + 4, 'minecraft:air')
    ladder(b, tx0 + 2, tz0 + 1, 1, 18, 'south')
    b.set(tx0 + 2, 18, tz0 + 1, 'minecraft:air')
    for x in range(tx0 + 1, tx0 + 4):   # a floor at bridge height inside, so the door leads somewhere
        for z in range(tz0 + 1, tz0 + 4):
            if (x, z) != (tx0 + 2, tz0 + 1):
                b.set(x, 8, z, 'minecraft:dark_oak_planks')
    # bridge from the hall roof (y8, walk y9) to the tower's west door: 4 wide, rails on the outer lanes, a stone
    # pier under the middle, both ends resting on walls
    for x in range(hx1 + 1, tx0):
        for z in (16, 17, 18, 19):
            b.set(x, 8, z, 'minecraft:spruce_planks' if z in (17, 18) else 'minecraft:stripped_spruce_log')
            if z in (16, 19):
                b.set(x, 8, z, 'minecraft:stripped_spruce_log', axis='x')
                if x != 19:
                    fence(b, x, 9, z, 'minecraft:spruce_fence')
        if x in (17, 22):
            for z in (16, 19):
                log(b, x, 9, z, 'minecraft:dark_oak_log')
                lantern(b, x, 10, z)
    for y in (9, 10):
        for z in (17, 18):
            b.set(tx0, y, z, 'minecraft:air')
    b.set(tx0 + 1, 8, 18, 'minecraft:spruce_planks')
    for x in (20, 21):   # a stone pier under the bridge
        for z in (17, 18):
            for y in range(1, 8):
                b.set(x, y, z, varied(x, y, z, 'minecraft:cobblestone', 'minecraft:mossy_cobblestone'))
    # trap: the bridge's two centre planks at x19 are rigged from both sides (relays north and south)
    rigged_floor(b, 19, 8, 18, 'south')
    rigged_floor(b, 19, 8, 17, 'north')
    for y in range(1, 8):
        log(b, 19, y, 22, 'minecraft:dark_oak_log')
        log(b, 19, y, 13, 'minecraft:dark_oak_log')
    # courtyard traps
    keg_wire(b, 7, 25, 5, 'minecraft:cobblestone')
    keg_wire(b, 23, 23, 3, 'minecraft:cobblestone')
    for x in (8, 10):
        gallery(b, x, 5, hz1, 'south')
    spikes(b, (15, 29), (19, 29), (17, 27))
    alarm(b, 17, 32)
    for x, z in ((5, 29), (29, 29), (29, 6)):
        campfire(b, x, z)
    for x, z in ((20, 8), (24, 8)):
        b.set(x, 1, z, 'minecraft:target')
    b.mob('pillager', 25, 17, y=19)
    b.mob('pillager', 27, 19, y=19)
    b.mob('pillager', 5, 14, y=9)
    b.mob('pillager', 13, 22, y=9)
    b.mob('evoker', 9, 19)
    for x, z in ((5, 20), (13, 20), (9, 26), (17, 26), (22, 12), (28, 10)):
        b.mob('vindicator', x, z)
    b.warlord(9, 18)
    b.spare((20, 28), (14, 28), (6, 8), (12, 8), (28, 26))
    b.report('A raiders\' hall and a tall watchtower joined by a rope bridge; pillagers shoot from the tower top and '
             'the hall roof. A bridge plank drops when the alarm sounds. Climb the tower from inside.',
             'Una sala de saqueadores y una atalaya alta unidas por un puente de cuerda; los saqueadores disparan desde '
             'la atalaya y el tejado. Un tablón del puente cae cuando suena la alarma. La atalaya se sube por dentro.')
    b.done()


def _longhouse_roof(b, x0, x1, z0, z1):
    """The illager hall's roof: stepped dark oak gable (spruce flecks) with trimmed eaves, planked gable ends with a
    window, three dormers a side and a stone chimney on the east slope."""
    for d in range(0, 9):
        for z in range(z0 - 1, z1 + 2):
            for x, facing in ((x0 - 1 + d, 'east'), (x1 + 1 - d, 'west')):
                name = varied(x, 8 + d, z, 'minecraft:dark_oak_stairs', 'minecraft:spruce_stairs')
                b.set(x, 8 + d, z, name, **stairs(facing))
                if d:
                    b.set(x, 7 + d, z, 'minecraft:dark_oak_planks')   # solid under each step: no floating roof
    for z in range(z0 - 1, z1 + 2):
        b.set(20, 16, z, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
        for x, facing in ((x0 - 1, 'west'), (x1 + 1, 'east')):   # upside-down stair under the eave
            b.set(x, 7, z, 'minecraft:dark_oak_stairs', **stairs(facing, 'top'))
    pane_x = dict(north=False, south=False, east=True, west=True, waterlogged=False)
    for z in (z0, z1):   # gable ends: planks, two beams and a window
        for d in range(0, 9):
            lo, hi = x0 - 1 + d, x1 + 1 - d
            for x in range(lo + 1, hi):
                beam = x in (17, 23)
                b.set(x, 8 + d, z, 'minecraft:stripped_dark_oak_log' if beam else 'minecraft:spruce_planks',
                      **({'axis': 'y'} if beam else {}))
        for x in (19, 20, 21):
            for y in (10, 11):
                b.set(x, y, z, 'minecraft:glass_pane', **pane_x)
    pane_z = dict(north=True, south=True, east=False, west=False, waterlogged=False)
    for m in (0, 1):   # dormers: front wall on the wall line, a small gable above the slope
        def X(x):
            return 40 - x if m else x
        for zc in (8, 17, 26):
            for dz in (-1, 1):
                for y in (10, 11):
                    b.set(X(12), y, zc + dz, 'minecraft:dark_oak_log', axis='y')
                b.set(X(13), 11, zc + dz, 'minecraft:dark_oak_planks')
                for x in (12, 13, 14):
                    b.set(X(x), 12, zc + dz, 'minecraft:dark_oak_stairs',
                          **stairs('south' if dz < 0 else 'north'))
            for y in (10, 11):
                b.set(X(12), y, zc, 'minecraft:glass_pane', **pane_z)
            for x in (12, 13, 14):
                b.set(X(x), 12, zc, 'minecraft:dark_oak_planks')
                b.set(X(x), 13, zc, 'minecraft:dark_oak_slab', type='bottom', waterlogged=False)
    for x in (25, 26):   # chimney, with a campfire on top
        for z in (21, 22):
            for y in range(8, 20):
                b.set(x, y, z, 'minecraft:mossy_cobblestone' if y % 2 else 'minecraft:cobblestone')
    campfire(b, 25, 21, y=20)


def _longhouse_trim(b, x0, x1, z0, z1):
    """Eave beam, shutters, banners, lanterns and the covered porch of the illager hall."""
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if z % 4:
                b.set(x, 6, z, 'minecraft:stripped_dark_oak_log', axis='z')
    for x in range(x0 + 1, x1):
        for z in (z0, z1):
            b.set(x, 6, z, 'minecraft:stripped_dark_oak_log', axis='x')
    tr = dict(half='bottom', open=True, powered=False, waterlogged=False)
    for zw in (6, 10, 15, 20, 25, 30):
        for dz in (-1, 1):
            b.set(x0 - 1, 3, zw + dz, 'minecraft:spruce_trapdoor', facing='west', **tr)
            b.set(x1 + 1, 3, zw + dz, 'minecraft:spruce_trapdoor', facing='east', **tr)
    for z in (13, 23):
        for y in (4, 5):
            b.set(x0 - 1, y, z, 'minecraft:black_wall_banner', facing='west')
            b.set(x1 + 1, y, z, 'minecraft:black_wall_banner', facing='east')
    for z in (8, 18, 28):
        lantern(b, x0 - 1, 6, z, soul=True, hanging=True)
        lantern(b, x1 + 1, 6, z, soul=True, hanging=True)
    for x in (18, 22):   # porch: two posts and a lean-to roof over the front door
        for y in range(1, 5):
            log(b, x, y, 34, 'minecraft:dark_oak_log')
    for x in range(17, 24):
        b.set(x, 5, 34, 'minecraft:dark_oak_stairs', **stairs('north'))
        b.set(x, 6, 33, 'minecraft:dark_oak_stairs', **stairs('north'))
        b.set(x, 5, 33, 'minecraft:dark_oak_planks')
    lantern(b, 20, 4, 34, soul=True, hanging=True)


def _longhouse_loft(b):
    """The attic under the roof is a lit loft (hay, crates, collar beams and lanterns) reached by a ladder from the east
    room, so it is no dark hollow for monsters to spawn in."""
    for y in range(1, 9):
        b.set(27, y, 16, 'minecraft:ladder', facing='west', waterlogged=False)
    for z in (6, 12, 18, 24, 30):
        for x in range(16, 25):
            b.set(x, 12, z, 'minecraft:dark_oak_log', axis='x')
        lantern(b, 20, 11, z, soul=True, hanging=True)
    for x, z in ((13, 10), (13, 11), (14, 10), (27, 26), (27, 27), (26, 27)):
        b.set(x, 8, z, 'warbands:supply_crate')
    for x, z in ((13, 22), (13, 23), (14, 22), (27, 8), (27, 9), (26, 8)):
        b.set(x, 8, z, 'minecraft:hay_block', axis='y')
    for x, z in ((14, 8), (26, 22), (14, 29), (26, 14)):   # lamps on the loft floor, so the edges stay lit too
        lantern(b, x, 8, z, soul=True)


def illager_longhouse():
    """41x41 illager manor: a long building of rooms either side of a central hall, guns in the hall walls, and a
    portcullis before the throne room at the far end. Rooms have windows: you can break in from the side."""
    n = 41
    b = Camp('ILLAGER_HALL', 'illager_longhouse', n, 22)
    b.ground('minecraft:cobblestone', 'minecraft:gravel', 'minecraft:coarse_dirt')
    for x, z in ring(1, 39):
        top = 3 if (x + z) % 2 else 4
        for y in range(1, top + 1):
            log(b, x, y, z, 'minecraft:dark_oak_log')
    for x in (19, 20, 21):
        for y in range(1, 4):
            b.set(x, y, 39, 'minecraft:air')
        log(b, x, 4, 39, 'minecraft:dark_oak_log', 'x')
    for x in (18, 22):   # lintel rests on the wall posts
        log(b, x, 4, 39, 'minecraft:dark_oak_log', 'x')
    x0, x1, z0, z1 = 12, 28, 4, 32
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            b.set(x, 0, z, 'minecraft:dark_oak_planks' if not edge else 'minecraft:cobblestone')
            if edge:
                for y in range(1, 7):
                    b.set(x, y, z, 'minecraft:cobblestone' if y == 1 else
                          ('minecraft:dark_oak_log' if (x in (x0, x1) and z % 4 == 0) else 'minecraft:birch_planks'),
                          **({'axis': 'y'} if (y > 1 and x in (x0, x1) and z % 4 == 0) else {}))
            b.set(x, 7, z, 'minecraft:dark_oak_planks')
    _longhouse_roof(b, x0, x1, z0, z1)
    # central hall x 19..21; rooms either side behind walls x=18 and x=22, doors every room
    for z in range(z0 + 1, z1):
        for y in range(1, 7):
            for x in (18, 22):
                b.set(x, y, z, 'minecraft:spruce_planks')
    for zr in (13, 18, 23, 28):   # room dividers
        for x in list(range(x0 + 1, 18)) + list(range(23, x1)):
            for y in range(1, 7):
                b.set(x, y, zr, 'minecraft:spruce_planks')
    for zd in (15, 20, 25, 30):  # doors from the hall into each room
        for y in (1, 2):
            b.set(18, y, zd, 'minecraft:air')
            b.set(22, y, zd, 'minecraft:air')
    for z in (6, 10, 15, 20, 25, 30):  # windows on the outer walls
        for x in (x0, x1):
            b.set(x, 3, z, 'minecraft:glass_pane', **{'north': True, 'south': True, 'east': False, 'west': False,
                                                      'waterlogged': False})
    _longhouse_trim(b, x0, x1, z0, z1)
    for y in (1, 2, 3):   # the front door
        for x in (19, 20, 21):
            b.set(x, y, z1, 'minecraft:air')
    # throne room: z 5..10 across the hall, behind a cross wall at z=11 with the portcullis
    for x in range(x0 + 1, x1):
        for y in range(1, 7):
            b.set(x, y, 11, 'minecraft:spruce_planks')
        for z in range(z0 + 1, 11):
            for y in range(1, 7):
                if b.block(x, y, z) == 'minecraft:spruce_planks':
                    b.set(x, y, z, 'minecraft:air')
    piston_gate(b, 19, 11)
    for x in (19, 20):
        b.set(x, 3, 11, 'minecraft:spruce_planks')
    b.standard(20, 6)
    for x in (18, 22):
        b.set(x, 1, 6, 'minecraft:dark_oak_log', axis='y')
        b.set(x, 2, 6, 'minecraft:soul_lantern', hanging=False, waterlogged=False)
    for z in range(7, 11):
        b.set(20, 1, z, 'minecraft:red_carpet')
    # guns in the hall walls, facing across the hall; relay under each (in the wall, so also reachable from the rooms)
    for z in (14, 24):
        gallery(b, 18, 2, z, 'east')
        gallery(b, 22, 2, z, 'west')
    spikes(b, (20, 27), (19, 21), (21, 17))
    alarm(b, 20, 31)
    # room furnishing: bunks, a library, an armory
    for z in (19, 21):
        for y in (1, 2, 3):
            b.set(13, y, z, 'minecraft:bookshelf')
    b.set(26, 1, 21, 'minecraft:smithing_table')
    b.set(26, 1, 26, 'minecraft:anvil', facing='north')
    _longhouse_loft(b)
    # courtyard: pillager posts on two small towers, fires, targets
    posts = [tower(b, x0_, 34, 5, 'minecraft:cobblestone', 'minecraft:cobblestone_slab') for x0_ in (4, 34)]
    for x, z in ((8, 36), (32, 36), (6, 10), (34, 10)):
        campfire(b, x, z)
    for x, z in ((5, 20), (35, 20)):
        b.set(x, 1, z, 'minecraft:target')
    tent(b, 3, 26, 3, 'minecraft:gray_wool')
    tent(b, 35, 26, 3, 'minecraft:gray_wool')
    for x in (17, 23):   # banner poles flanking the gate
        for y in range(1, 5):
            fence(b, x, y, 36, 'minecraft:dark_oak_fence')
        b.set(x, 5, 36, 'minecraft:black_banner', rotation=0)
    sculk(b, 8, 18)
    sculk(b, 32, 18)
    for x, z in ((15, 10), (25, 10), (15, 15), (25, 20), (15, 25), (25, 30)):
        b.mob('vindicator', x, z)
    b.mob('evoker', 15, 20)
    b.mob('vindicator', 20, 29)
    for spot in posts:
        b.mob('pillager', *spot[::2], y=spot[1])
    b.mob('pillager', 8, 26)
    b.mob('pillager', 32, 26)
    b.warlord(20, 8)
    b.spare((20, 22), (20, 16), (8, 30), (32, 30), (25, 15))
    b.report('A long illager manor: rooms of vindicators either side of a central hall, guns in its walls, and a '
             'portcullis before the throne room. The rooms have windows: you can break in from the side.',
             'Una larga mansión illager: salas de vindicadores a ambos lados de un salón central, cañones en sus '
             'muros y un rastrillo ante la sala del trono. Las salas tienen ventanas: puedes entrar por un lado.')
    b.done()


def build():
    keep_moat()
    keep_concentric()
    cavalry_ring()
    cavalry_paddocks()
    crypt_catacomb()
    crypt_flooded()
    illager_watchtower()
    illager_longhouse()
