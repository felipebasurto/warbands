"""Static checks for every camp layout: the mistakes players saw (banner walled in, unreachable core, floating blocks,
blocks with nothing to stand on) fail generation instead of reaching the game.

Used by gen_camps.py (`python3 gen_camps.py --lint` checks without writing files). lint(builder, layout) returns a
list of problems, empty when the layout is clean.
"""
from collections import deque

AIR = 'minecraft:air'
PASSABLE_PARTS = ('carpet', 'banner', 'torch', 'lantern', 'pressure_plate', 'button', 'ladder', 'fence_gate', '_door',
                  'tripwire', 'sweet_berry_bush', 'fern', 'flower', 'vine', 'rail', 'sign', 'cobweb', 'caltrops',
                  'alarm_plate', 'candle', 'skull', 'lever', 'bush', 'sapling', 'mushroom', 'reinforced_gate',
                  'portcullis', 'short_grass', 'tall_grass', 'seagrass', 'poppy', 'dandelion')
PASSABLE_EXACT = ('air', 'snow', 'grass')
FLOOR_NOT = ('water', 'lava', 'powder_snow', 'sweet_berry_bush', 'cobweb')
SOIL = ('grass_block', 'dirt', 'podzol', 'coarse_dirt', 'moss_block', 'mud', 'rooted_dirt', 'farmland', 'mycelium')
PLANTS = ('fern', 'short_grass', 'tall_grass', 'poppy', 'dandelion', 'sweet_berry_bush')
STANDS = ('torch', 'banner', 'pressure_plate', 'carpet', 'campfire', 'cauldron', 'candle')


def name(b, pos):
    return b.blocks.get(pos, (AIR, ()))[0].split(':', 1)[-1]


def props(b, pos):
    return dict(b.blocks.get(pos, (AIR, ()))[1])


def passable(b, pos):
    n = name(b, pos)
    if n in PASSABLE_EXACT:
        return True
    if n.endswith('trapdoor'):
        return props(b, pos).get('open') == 'true'
    if n.endswith('_slab') and props(b, pos).get('type') == 'bottom':
        return True
    if n.endswith('fence_gate') or n.endswith('_door'):
        return True
    return any(part in n for part in PASSABLE_PARTS)


def floor(b, pos):
    """Solid enough to stand on: not passable, not a liquid or a trap."""
    n = name(b, pos)
    if n.endswith('_slab') or n.endswith('_stairs'):
        return True
    if passable(b, pos) and not n.endswith('trapdoor'):
        return 'ladder' in n
    return not any(part in n for part in FLOOR_NOT)


def walkable(b, x, y, z):
    return passable(b, (x, y, z)) and passable(b, (x, y + 1, z)) and floor(b, (x, y - 1, z))


def reachable(b, size, starts):
    seen = set(starts)
    queue = deque(starts)
    sx, sy, sz = size
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < sx and 0 <= nz < sz):
                continue
            for ny in (y, y + 1, y - 1, y - 2, y - 3):
                if not (1 <= ny < sy - 1) or (nx, ny, nz) in seen:
                    continue
                if ny == y + 1 and not passable(b, (x, y + 2, z)):
                    continue
                if ny < y and not all(passable(b, (nx, y + k, nz)) for k in range(0, 2)):
                    continue
                if walkable(b, nx, ny, nz):
                    seen.add((nx, ny, nz))
                    queue.append((nx, ny, nz))
        # ladders climb
        if 'ladder' in name(b, (x, y, z)) or 'ladder' in name(b, (x, y + 1, z)):
            for ny in (y + 1, y - 1):
                if 1 <= ny < sy - 1 and (x, ny, z) not in seen and passable(b, (x, ny, z)) and passable(b, (x, ny + 1, z)):
                    seen.add((x, ny, z))
                    queue.append((x, ny, z))
    return seen


def lint(b, layout):
    problems = []
    sx, sy, sz = b.size
    lid = layout['id']
    for (x, y, z) in b.blocks:
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            problems.append(f'block outside the {sx}x{sy}x{sz} box at {x},{y},{z}')
            break

    cx, cy, cz = layout['core']
    if name(b, (cx, cy, cz)) != 'camp_standard':
        problems.append(f'core {cx},{cy},{cz} is {name(b, (cx, cy, cz))}, not camp_standard')
    if 'banner' not in name(b, (cx, cy + 1, cz)):
        problems.append('no banner above the standard')
    if not passable(b, (cx, cy + 2, cz)):
        problems.append(f'standard covered: {name(b, (cx, cy + 2, cz))} right over the banner')
    free_sides = [d for d in ((1, 0), (-1, 0), (0, 1), (0, -1))
                  if passable(b, (cx + d[0], cy, cz + d[1])) and passable(b, (cx + d[0], cy + 1, cz + d[1]))]
    if not free_sides:
        problems.append('standard walled in: no free side to reach it from')

    # entrance: the south edge must have at least two open columns
    sunk = layout.get('sink', 0) > 0
    gate = [(x, y, sz - 1) for x in range(sx) for y in (1, 2, 3) if walkable(b, x, y, sz - 1)]
    if len(gate) < 2 and not sunk:
        problems.append(f'entrance: only {len(gate)} open columns on the south edge')
    starts = [g for g in gate]
    if not starts:
        starts = [(x, y, z) for x in range(sx) for y in (1, 2, 3) for z in range(sz - 3, sz) if walkable(b, x, y, z)]
    seen = reachable(b, b.size, starts)
    if not sunk and not any((cx + d[0], cy, cz + d[1]) in seen for d in free_sides):
        problems.append('standard not reachable from the entrance')
    for kind, what, x, y, z, on_tower in layout['guards'] + [('spare', None, *s, False) for s in layout['spares']]:
        if not walkable(b, x, y, z):
            problems.append(f'{kind} {what or ""} at {x},{y},{z}: feet {name(b, (x, y, z))}, head {name(b, (x, y + 1, z))}, below {name(b, (x, y - 1, z))}')
        elif not on_tower and not (kind == 'mob' and y > 1) and not sunk and (x, y, z) not in seen:
            problems.append(f'{kind} {what or ""} at {x},{y},{z} not reachable from the entrance')

    problems += common_problems(b)
    return problems


def common_problems(b):
    problems = []
    sx, sy, sz = b.size
    # floating blocks: everything solid must join the ground through neighbours
    solid = {p for p, (n, _) in b.blocks.items() if n != AIR}
    ground = deque(p for p in solid if p[1] == 0)
    joined = set(ground)
    while ground:
        x, y, z = ground.popleft()
        shaped = name(b, (x, y, z)).endswith(('_stairs', '_slab'))
        steps = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        if shaped:  # roof pieces step diagonally in a vertical plane
            steps += tuple((a, c, 0) for a in (-1, 1) for c in (-1, 1)) + tuple((0, c, a) for a in (-1, 1) for c in (-1, 1))
        for d in steps:
            q = (x + d[0], y + d[1], z + d[2])
            if q in solid and q not in joined:
                joined.add(q)
                ground.append(q)
    floating = sorted(p for p in solid if p not in joined and p[1] > 0)
    if floating:
        problems.append(f'{len(floating)} floating blocks, e.g. {floating[:3]} ({name(b, floating[0])})')

    # things that need something under, behind or above them
    for pos, (full, _) in list(b.blocks.items()):
        if ':' not in full:
            problems.append(f'block id without namespace {full!r} at {pos}')
            continue
        n = full.split(':', 1)[1]
        x, y, z = pos
        below = (x, y - 1, z)
        if n in PLANTS and y > 0 and not any(s in name(b, below) for s in SOIL):
            problems.append(f'{n} at {x},{y},{z} has {name(b, below)} under it')
        elif any(n.endswith(s) for s in STANDS) and 'wall' not in n and name(b, below) == 'air':
            problems.append(f'{n} at {x},{y},{z} floats over air')
        elif n.endswith('_door') and props(b, pos).get('half') == 'lower' and name(b, below) == 'air':
            problems.append(f'door at {x},{y},{z} has no floor')
        elif n == 'ladder':
            f = props(b, pos).get('facing')
            back = {'north': (0, 0, 1), 'south': (0, 0, -1), 'east': (-1, 0, 0), 'west': (1, 0, 0)}.get(f)
            if back and passable(b, (x + back[0], y, z + back[2])) and name(b, (x, y + 1, z)) != 'ladder':
                problems.append(f'ladder at {x},{y},{z} has nothing behind it')
        elif n == 'lantern' and props(b, pos).get('hanging') == 'true' and name(b, (x, y + 1, z)) == 'air':
            problems.append(f'hanging lantern at {x},{y},{z} hangs from nothing')
    return problems


CORE_BLOCKS = ('town_center', 'barracks', 'archery_range', 'stable', 'siege_workshop', 'watch_tower', 'blacksmith', 'hearth')


def lint_building(b, filename):
    """Player town buildings: same construction rules as camps, plus what only matters for towns: a core block, an open
    south door with a walkable way to it, and no container contents or loot tables baked into the template (Repairs
    re-places template NBT for a stone, so a stocked chest or dispenser would be a farm)."""
    problems = []
    sx, sy, sz = b.size
    for (x, y, z) in b.blocks:
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            problems.append(f'block outside the {sx}x{sy}x{sz} box at {x},{y},{z}')
            break
    for pos, data in b.nbt.items():
        if 'Items' in data or 'LootTable' in data:
            problems.append(f'container contents or loot table baked in at {pos} ({name(b, pos)}): town templates must be empty')
    cores = [p for p, (n, _) in b.blocks.items() if n.startswith('warbands:') and n.split(':')[1] in CORE_BLOCKS]
    if not cores:
        problems.append('no core block')
        return problems + common_problems(b)
    gate = [(x, y, sz - 1) for x in range(sx) for y in (1, 2, 3) if walkable(b, x, y, sz - 1)]
    if not gate:
        problems.append('no walkable opening on the south edge')
    else:
        seen = reachable(b, b.size, gate)
        cx, cy, cz = cores[0]
        beside = [(cx + d[0], cy, cz + d[1]) for d in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if not any(p in seen for p in beside):
            problems.append(f'core at {cores[0]} not reachable from the door')
    return problems + common_problems(b)
