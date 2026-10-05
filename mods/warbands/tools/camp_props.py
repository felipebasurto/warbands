"""Puts the camp props (weapon rack, hide rack, supply crate) into finished layouts, so each kind's identity shows in
its yard and rooms. Runs after a layout is built: it finds free floor cells beside walls, keeps clear of the gate lane,
the standard and every guard spot, and lets lint prove nothing became unreachable."""
import random

import lint_structures as L

PROPS = {
    'BANDIT_CAMP': (('weapon_rack', 2), ('supply_crate', 3)),
    'POACHERS_LODGE': (('hide_rack', 3), ('weapon_rack', 1)),
    'SMUGGLERS_DOCK': (('supply_crate', 5),),
    'FORT': (('weapon_rack', 3), ('supply_crate', 1)),
    'QUARRY_STRONGHOLD': (('supply_crate', 4),),
    'WITCH_COVEN': (),
    'FROZEN_OUTPOST': (('hide_rack', 2), ('supply_crate', 1)),
    'KEEP': (('weapon_rack', 4),),
    'CAVALRY_ENCAMPMENT': (('weapon_rack', 2), ('supply_crate', 2), ('hide_rack', 1)),
    'SUNKEN_CRYPT': (('supply_crate', 2),),
    'ILLAGER_HALL': (('weapon_rack', 3), ('hide_rack', 1)),
    # the player's town buildings (the same props, craftable): what each building does shows in its rooms
    'town_center.nbt': (('weapon_rack', 1), ('supply_crate', 2)),
    'town_center_feudal.nbt': (('weapon_rack', 2), ('supply_crate', 2)),
    'town_center_castle.nbt': (('weapon_rack', 2), ('supply_crate', 3)),
    'house.nbt': (('hide_rack', 1), ('supply_crate', 1)),
    'barracks.nbt': (('weapon_rack', 2), ('supply_crate', 1)),
    'archery_range.nbt': (('weapon_rack', 2), ('supply_crate', 1)),
    'blacksmith.nbt': (('weapon_rack', 2), ('supply_crate', 1)),
    'stable.nbt': (('hide_rack', 2), ('supply_crate', 2)),
    'watch_tower.nbt': (('weapon_rack', 1),),
    'siege_workshop.nbt': (('supply_crate', 3), ('weapon_rack', 1)),
}
WALLS = ('_planks', '_log', '_wood', 'bricks', 'cobblestone', 'stone', 'deepslate', 'blackstone', 'mud_brick')
LOOSE = WALLS + ('wool', 'snow_block', 'ice', 'hay_block', 'sandstone', 'terracotta', 'spruce', 'dark_oak', 'oak')
BUSY = ('door', 'ladder', 'chest', 'barrel', 'bed', 'gate', 'trapdoor', 'warbands', 'furnace', 'anvil', 'table', 'campfire',
        'stairs', 'slab', 'cauldron', 'composter', 'lectern', 'hay', 'target', 'piston', 'dispenser', 'tripwire', 'plate')
FACE = {(0, -1): 'south', (0, 1): 'north', (1, 0): 'west', (-1, 0): 'east'}  # wall on this side -> the prop faces away


def _solid_wall(b, pos, walls=WALLS):
    n = L.name(b, pos)
    return any(w in n for w in walls) and not n.endswith('_stairs') and not n.endswith('_slab') and not n.endswith('_wall') \
        and not n.endswith('_fence') and 'trapdoor' not in n


def _clear(b, pos):
    return L.name(b, pos) == 'air'


def add(b, lay, relax=False):
    wanted = PROPS.get(lay['kind'], ())
    if not wanted:
        return 0
    sx, _, sz = b.size
    rng = random.Random(lay['id'])
    keep_off = [(g[2], g[4]) for g in lay['guards']] + [tuple(lay['spares'][i][::1]) for i in range(0)] + [(lay['core'][0], lay['core'][2])]
    for s in lay['spares']:
        keep_off.append((s[0], s[1]))
    if lay['kind'] == 'stable.nbt':  # BuildingDecor stands horses in these 2x2 stalls: nothing may sit in them
        keep_off += [(x, z) for x in (2, 3, 5, 6) for z in (2, 3, 5, 6) if (x < 4) == (z < 4)]
    mid = sx // 2
    taken = []
    placed = 0

    def ok(x, z):
        if any(abs(x - kx) <= 1 and abs(z - kz) <= 1 for kx, kz in keep_off):
            return False
        if z >= sz - (3 if relax else 5) and abs(x - mid) <= (2 if relax else 3):  # the lane out of the gate
            return False
        if any(abs(x - tx) + abs(z - tz) < 3 for tx, tz in taken):
            return False
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if relax and dx and dz:
                    continue
                if any(w in L.name(b, (x + dx, 1, z + dz)) for w in BUSY):
                    return False
                if L.name(b, (x + dx, 2, z + dz)) != 'air' and (dx or dz) and not _solid_wall(b, (x + dx, 2, z + dz), LOOSE if relax else WALLS):
                    return False
        return True

    for prop, count in wanted:
        cells = []
        for x in range(1, sx - 1):
            for z in range(1, sz - 1):
                if not (_clear(b, (x, 1, z)) and _clear(b, (x, 2, z)) and _clear(b, (x, 3, z))):
                    continue
                if not L.floor(b, (x, 0, z)) or L.name(b, (x, 0, z)) in ('grass_block', 'dirt', 'podzol', 'coarse_dirt', 'gravel', 'moss_block') and False:
                    continue
                if L.name(b, (x, 0, z)).endswith(('water', 'lava')):
                    continue
                kinds = LOOSE if relax else WALLS
                walls = [(d, f) for d, f in FACE.items() if _solid_wall(b, (x + d[0], 1, z + d[1]), kinds) and (relax or _solid_wall(b, (x + d[0], 2, z + d[1]), kinds))]
                if prop == 'supply_crate':
                    if walls or any(_solid_wall(b, (x + d[0], 1, z + d[1]), kinds) for d in FACE):
                        cells.append((x, z, None))
                elif len(walls) == 1:
                    cells.append((x, z, walls[0][1]))
        rng.shuffle(cells)
        made = 0
        for x, z, facing in cells:
            if made >= count:
                break
            if not ok(x, z):
                continue
            if prop == 'supply_crate':
                b.set(x, 1, z, 'warbands:supply_crate')
                if rng.random() < 0.35 and _clear(b, (x, 3, z)):
                    b.set(x, 2, z, 'warbands:supply_crate')
            else:
                b.set(x, 1, z, 'warbands:' + prop, facing=facing)
            taken.append((x, z))
            made += 1
            placed += 1
    if placed < sum(c for _, c in wanted) // 2 and not relax:
        placed += add(b, lay, relax=True)
    return placed


def blend(b, lay):
    """Breaks the perfect square: floor cells along the outer edge of the footprint that nothing stands on are left out,
    so the natural ground shows through in an irregular fringe instead of a plinth. Camps only (a sunk crypt keeps its
    rim: the pit needs it)."""
    if lay.get('sink', 0) > 0:
        return 0
    sx, sy, sz = b.size
    rng = random.Random('edge' + lay['id'])
    dropped = 0
    for x in range(sx):
        for z in range(sz):
            edge = min(x, z, sx - 1 - x, sz - 1 - z)
            if edge > 1 or (0, 0, 0) == (x, 0, z):
                continue
            if z >= sz - 3 and abs(x - sx // 2) <= 3:  # keep the lane out of the gate paved
                continue
            if (x, 0, z) not in b.blocks or any((x, y, z) in b.blocks and b.blocks[(x, y, z)][0] != 'minecraft:air' for y in range(1, sy)):
                continue
            # keep a cell when a neighbour above ground rests on it diagonally (stairs/slabs), or an entity stands on it
            if any(b.blocks.get((x + dx, 1, z + dz), ('minecraft:air',))[0] != 'minecraft:air' for dx in (-1, 0, 1) for dz in (-1, 0, 1)) and edge == 1 and rng.random() < 0.6:
                continue
            if rng.random() < (0.55 if edge == 0 else 0.3):
                del b.blocks[(x, 0, z)]
                dropped += 1
    return dropped
