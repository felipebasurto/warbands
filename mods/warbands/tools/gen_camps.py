"""Generates the Warbands enemy-camp layouts: the structure files, CampLayouts.java and the scouts' reports.

Run from this folder:  python3 gen_camps.py
Output:
  ../src/main/resources/data/warbands/structure/<layout>.nbt           one per layout
  ../src/main/java/dev/felipe/warbands/camp/CampLayouts.java           sizes, cores, guard spots (generated, don't edit)
  camp_layout.warbands.<layout> keys in the EN and ES lang files       what the War Map's scouts say

Every camp kind has a few hand-made layouts of different sizes and shapes; a War Map picks one at random. The first
layout of each kind is the original design (gen_structures.py), so camps from old saves still find their file.
Entrance on the south side (z = max); the game turns the camp so its entrance faces the town.

Redstone contraptions are vanilla redstone driven by warbands:war_relay blocks, which are powered while the camp is
alarmed (see WarRelayBlock). Keep these rules (done() checks some of them):
  * a relay powers only what touches it, so every piston or dispenser sits right next to its own relay;
  * never put an alarm horn next to a relay (it would ring on every pulse);
  * pistons may only move warbands:portcullis / warbands:rigged_floor (every other camp block is immovable);
  * guard spots need air at feet and head and something solid below.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_structures as g
from gen_structures import Builder, fill, ring, varied, hip_roof, stairs

JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'dev', 'felipe', 'warbands', 'camp', 'CampLayouts.java')
LANG = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'warbands', 'lang')

KINDS = ['BANDIT_CAMP', 'POACHERS_LODGE', 'SMUGGLERS_DOCK', 'FORT', 'QUARRY_STRONGHOLD', 'WITCH_COVEN', 'FROZEN_OUTPOST',
         'KEEP', 'CAVALRY_ENCAMPMENT', 'SUNKEN_CRYPT', 'ILLAGER_HALL']
UNITS = {'militia', 'archer', 'spearman', 'crossbowman', 'knight'}
MOBS = {'witch', 'stray', 'skeleton', 'zombie', 'vindicator', 'pillager', 'evoker', 'drowned', 'husk'}

LAYOUTS = []     # every layout, in KINDS order then layout order
REPORTS = {}     # layout id -> (english, spanish)

NONE4 = dict(north=False, south=False, east=False, west=False, waterlogged=False)
PASSABLE = ('minecraft:air', 'minecraft:red_carpet', 'minecraft:snow', 'warbands:alarm_plate', 'warbands:caltrops',
            'minecraft:stone_pressure_plate', 'minecraft:oak_pressure_plate', 'minecraft:light_gray_carpet',
            'minecraft:black_carpet', 'minecraft:brown_carpet', 'minecraft:moss_carpet')
NOT_FLOOR = ('minecraft:air', 'minecraft:water', 'minecraft:lava', 'minecraft:powder_snow', 'minecraft:sweet_berry_bush')


class Camp(Builder):
    """A layout being built: blocks plus its garrison, spare spots, core and the scouts' report."""

    def __init__(self, kind, lid, n, h, sink=0):
        super().__init__(n, h, n)
        self.kind, self.lid, self.n, self.h, self.sink = kind, lid, n, h, sink
        self.guards, self.spares, self.core = [], [], None
        fill(self, 0, 1, 0, n - 1, h - 1, n - 1, 'minecraft:air')

    def ground(self, *blocks):
        for x in range(self.n):
            for z in range(self.n):
                self.set(x, 0, z, varied(x, 0, z, *blocks))

    def unit(self, unit, x, z, y=1):
        assert unit in UNITS, unit
        self.guards.append(('unit', unit, x, y, z, False))

    def post(self, unit, x, y, z):
        """A guard posted on a tower or wall top: holds the spot, shoots from it, is put back if knocked off."""
        assert unit in UNITS, unit
        self.guards.append(('unit', unit, x, y, z, True))

    def mob(self, mob, x, z, y=1):
        assert mob in MOBS, mob
        self.guards.append(('mob', mob, x, y, z, False))

    def warlord(self, x, z, y=1):
        self.guards.append(('warlord', None, x, y, z, False))

    def spare(self, *cells, y=1):
        """Ground spots for the extra guards a harder threat brings (difficulty, party, win streak)."""
        for x, z in cells:
            self.spares.append((x, y, z))

    def standard(self, x, z, y=1):
        g.standard(self, x, y, z)
        self.core = (x, y, z)

    def report(self, en, es):
        REPORTS[self.lid] = (en, es)

    def block(self, x, y, z):
        return self.blocks.get((x, y, z), ('minecraft:air', ()))[0]

    def done(self):
        assert self.core, self.lid + ': no standard'
        assert self.lid in REPORTS, self.lid + ': no scouts report'
        problems = []
        for kind, what, x, y, z, _ in self.guards + [('spare', None, *s, False) for s in self.spares]:
            feet, head, below = self.block(x, y, z), self.block(x, y + 1, z), self.block(x, y - 1, z)
            if feet not in PASSABLE or head not in PASSABLE or below in NOT_FLOOR:
                problems.append(f'{kind} {what} at {x},{y},{z}: feet {feet}, head {head}, below {below}')
        for (x, y, z), (name, _) in self.blocks.items():
            if name == 'warbands:alarm_horn':
                for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    if self.block(x + dx, y + dy, z + dz) == 'warbands:war_relay':
                        problems.append(f'horn at {x},{y},{z} touches a relay')
        if problems:
            sys.exit(self.lid + ':\n  ' + '\n  '.join(problems))
        # BlueprintPlacer clears the whole box above the floor before placing, so air there needn't be stored
        # (a 45x45 camp is then ~10k blocks instead of 40k, and places that much faster).
        for pos in [p for p, (name, _) in self.blocks.items() if name == 'minecraft:air' and p[1] >= 1]:
            del self.blocks[pos]
        self.save(self.lid + '.nbt')
        LAYOUTS.append(dict(kind=self.kind, id=self.lid, size=self.n, height=self.h, core=self.core, sink=self.sink,
                            guards=self.guards, spares=self.spares))


# ---------------------------------------------------------------- building blocks

def log(b, x, y, z, name='minecraft:spruce_log', axis='y'):
    b.set(x, y, z, name, axis=axis)


def fence(b, x, y, z, name='minecraft:spruce_fence'):
    b.set(x, y, z, name, **NONE4)


def lantern(b, x, y, z, soul=False, hanging=False):
    b.set(x, y, z, 'minecraft:soul_lantern' if soul else 'minecraft:lantern', hanging=hanging, waterlogged=False)


def campfire(b, x, z, y=1, soul=False):
    b.set(x, y, z, 'minecraft:soul_campfire' if soul else 'minecraft:campfire', lit=True, signal_fire=False,
          facing='north', waterlogged=False)


def fence_gate(b, x, y, z, wood='spruce', facing='south'):
    b.set(x, y, z, f'minecraft:{wood}_fence_gate', facing=facing, open=False, in_wall=False, powered=False)


def ladder(b, x, z, y0, y1, facing):
    """Ladder on the face of the block behind it; {@code facing} is the way the climber looks out."""
    for y in range(y0, y1 + 1):
        b.set(x, y, z, 'minecraft:ladder', facing=facing, waterlogged=False)


def slab(b, x, y, z, name, kind='bottom'):
    b.set(x, y, z, name, type=kind, waterlogged=False)


def circle(cx, cz, r):
    """Cells of a one-thick ring of radius r (a digital circle, no gaps)."""
    cells = set()
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
            if r - 0.5 <= d < r + 0.5:
                cells.add((x, z))
    return sorted(cells)


def disc(cx, cz, r):
    return [(x, z) for x in range(cx - r, cx + r + 1) for z in range(cz - r, cz + r + 1)
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + r]


def wall(b, cells, y0, y1, *blocks):
    for x, z in cells:
        for y in range(y0, y1 + 1):
            b.set(x, y, z, varied(x, y, z, *blocks))


def crenels(b, cells, y, block):
    for x, z in cells:
        if (x + z) % 2 == 0:
            b.set(x, y, z, block)


def tower(b, x0, z0, top, block, rim, size=3):
    """Square tower, solid to {@code top}, a slab rim, the guard spot open in the middle: (x0+size//2, top+1, ...)."""
    fill(b, x0, 1, z0, x0 + size - 1, top, z0 + size - 1, block)
    for x, z in g.rect_ring(x0, z0, x0 + size - 1, z0 + size - 1):
        slab(b, x, top + 1, z, rim)
    c = size // 2
    for x in range(x0 + 1, x0 + size - 1):
        for z in range(z0 + 1, z0 + size - 1):
            b.set(x, top + 1, z, 'minecraft:air')
            b.set(x, top + 2, z, 'minecraft:air')
    return x0 + c, top + 1, z0 + c


def walkway(b, cells, y, block='minecraft:spruce_planks'):
    for x, z in cells:
        b.set(x, y, z, block)


def tent(b, x0, z0, length, cloth):
    g.tent(b, x0, z0, length, cloth)


# ---------------------------------------------------------------- traps and contraptions

ARROWS = 'minecraft:arrow'


def ammo(item, count=64, potion=None):
    stack = {'Slot': 0, 'id': item, 'count': count}
    if potion:
        stack['components'] = {'minecraft:potion_contents': {'potion': 'minecraft:' + potion}}
    return {'id': 'minecraft:dispenser', 'Items': [stack]}


def relay(b, x, y, z, pulse=False):
    b.set(x, y, z, 'warbands:war_relay', powered=False, pulse=pulse)


def gallery(b, x, y, z, facing, item=ARROWS, potion=None, count=64):
    """A dispenser gun over its own pulse relay: while the camp is alarmed it fires once a second. Smash the relay
    under it (red, glowing once the alarm sounds) and that gun goes quiet."""
    b.set(x, y, z, 'minecraft:dispenser', facing=facing, triggered=False, nbt=ammo(item, count, potion))
    relay(b, x, y - 1, z, pulse=True)


def piston_gate(b, x0, z, y=1):
    """A 2-wide, 2-high gateway at x0..x0+1 in a wall running along x: two portcullis leaves in the wall beside it,
    each on sticky pistons wired straight to steady relays. The alarm slams it shut; it opens again when the camp
    calms down or when its relays (the red blocks in the wall, four of them) are smashed. A Ram breaks the grating.
    Needs wall cells x0-3 .. x0+4."""
    for dy in (0, 1):
        yy = y + dy
        b.set(x0, yy, z, 'minecraft:air')
        b.set(x0 + 1, yy, z, 'minecraft:air')
        b.set(x0 - 1, yy, z, 'warbands:portcullis')
        b.set(x0 + 2, yy, z, 'warbands:portcullis')
        b.set(x0 - 2, yy, z, 'minecraft:sticky_piston', facing='east', extended=False)
        b.set(x0 + 3, yy, z, 'minecraft:sticky_piston', facing='west', extended=False)
        relay(b, x0 - 3, yy, z)
        relay(b, x0 + 4, yy, z)


def sculk(b, x, z, y=1):
    """A sculk sensor on an alarm horn: it hears footsteps, mining and landing arrows within 8 blocks and sounds the
    camp alarm. Sneak past it, or muffle it with wool."""
    b.set(x, y - 1, z, 'warbands:alarm_horn', powered=False)
    b.set(x, y, z, 'minecraft:sculk_sensor', power=0, sculk_sensor_phase='inactive', waterlogged=False)


def alarm(b, x, z, y=1):
    b.set(x, y, z, 'warbands:alarm_plate')


def spikes(b, *cells, y=1):
    for x, z in cells:
        b.set(x, y, z, 'warbands:caltrops')


def pitfall(b, x, z, soft='powder_snow', y=1):
    """An open trapdoor over a one-deep drop (y-1) into powder snow or berry bushes."""
    props = {} if soft == 'powder_snow' else {'age': 3}
    b.set(x, y - 1, z, 'minecraft:' + soft, **props)
    b.set(x, y, z, 'minecraft:spruce_trapdoor', facing='north', half='bottom', open=True, powered=False, waterlogged=False)


def tripwire(b, x0, z, width, post, y=1):
    b.set(x0, y, z, post)
    b.set(x0 + width + 1, y, z, post)
    b.set(x0 + 1, y, z, 'minecraft:tripwire_hook', facing='east', attached=True, powered=False)
    for x in range(x0 + 2, x0 + width + 1):
        b.set(x, y, z, 'minecraft:tripwire', east=True, west=True, north=False, south=False,
              attached=True, powered=False, disarmed=False)
    b.set(x0 + width, y, z, 'minecraft:tripwire_hook', facing='west', attached=True, powered=False)


def keg_wire(b, x0, z, width, post, y=1):
    """Tripwire -> a powder keg beside its west hook (north side): the blast hurts, breaks nothing."""
    tripwire(b, x0, z, width, post, y)
    b.set(x0 + 1, y, z - 1, 'warbands:powder_keg')


def rigged_floor(b, x, y, z, piston_side, pit=None):
    """A trap plank at (x, y, z) in a walkway or bridge, held by an extended sticky piston on {@code piston_side}
    ('west' = the piston is west of it). A lit redstone torch hanging on a relay keeps the piston out; when the alarm
    powers the relay the torch goes dark, the piston pulls the plank away and whoever stood on it falls (into
    {@code pit} one below, if given). Needs 4 cells on the piston side: head, piston, torch, relay."""
    dx, dz = {'west': (-1, 0), 'east': (1, 0), 'north': (0, -1), 'south': (0, 1)}[piston_side]
    toward = _opposite(piston_side)
    if pit:
        b.set(x, y - 1, z, pit)
    b.set(x, y, z, 'warbands:rigged_floor')
    b.set(x + dx, y, z + dz, 'minecraft:piston_head', facing=toward, type='sticky', short=False)
    b.set(x + 2 * dx, y, z + 2 * dz, 'minecraft:sticky_piston', facing=toward, extended=True)
    b.set(x + 3 * dx, y, z + 3 * dz, 'minecraft:redstone_wall_torch', facing=toward, lit=True)
    relay(b, x + 4 * dx, y, z + 4 * dz)


def _opposite(d):
    return {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[d]


# ---------------------------------------------------------------- the original layouts (gen_structures.py)

def original(kind, lid, fn, size, height, core, sink, guards, en, es):
    """Registers an original layout: its structure comes from gen_structures.py, its garrison from the old table."""
    fn()
    REPORTS[lid] = (en, es)
    LAYOUTS.append(dict(kind=kind, id=lid, size=size, height=height, core=core, sink=sink, guards=guards, spares=[]))


def G(unit, x, z, y=1):
    return ('unit', unit, x, y, z, False)


def T(unit, x, y, z):
    return ('unit', unit, x, y, z, True)


def M(mob, x, z, y=1):
    return ('mob', mob, x, y, z, False)


def W(x, z, y=1):
    return ('warlord', None, x, y, z, False)


def originals():
    original('BANDIT_CAMP', 'bandit_camp', g.bandit_camp, 15, 8, (7, 1, 5), 0, [
        G('militia', 6, 7), G('militia', 8, 7), G('militia', 3, 9), G('militia', 10, 9),
        G('archer', 2, 12, 3), G('archer', 12, 12, 3)],
        'A bandit hideout: the chief\'s red tent at the back, archers on steps by the gate. The west wall has '
        'fallen in, but a berry pit waits just inside the gap.',
        'Un escondite de bandidos: la tienda roja del jefe al fondo, arqueros en tarimas junto a la puerta. El muro '
        'oeste se ha caído, pero justo tras el hueco espera un foso de bayas.')
    original('POACHERS_LODGE', 'poachers_lodge', g.poachers_lodge, 19, 9, (9, 1, 9), 0, [
        G('militia', 7, 9), G('militia', 11, 9), G('archer', 5, 4), G('archer', 13, 4), T('archer', 4, 5, 16), G('archer', 12, 16)],
        'A hunters\' lodge behind a palisade: mostly archers, one on a high seat. A snare string by the cookfire '
        'fires an arrow trap in the wall.',
        'Un refugio de cazadores tras una empalizada: casi todo arqueros, uno en un puesto alto. Un lazo de cuerda '
        'junto a la hoguera dispara una trampa de flechas del muro.')
    original('SMUGGLERS_DOCK', 'smugglers_dock', g.smugglers_dock, 15, 11, (7, 1, 9), 0, [
        G('militia', 8, 10), G('militia', 6, 11), G('militia', 11, 12), G('archer', 4, 3, 2), G('archer', 7, 3, 2),
        G('archer', 12, 8)],
        'An open landing with no wall at all; archers on the jetty. Once the alarm sounds, the loose plank on the '
        'jetty drops you into the water.',
        'Un desembarco abierto, sin muralla; arqueros en el embarcadero. Cuando suena la alarma, la tabla suelta del '
        'embarcadero te tira al agua.')
    original('FORT', 'fort', g.fort, 21, 12, (10, 1, 6), 0, [
        G('militia', 10, 12), G('militia', 8, 14), G('militia', 12, 14), G('militia', 10, 16), G('spearman', 7, 10),
        G('spearman', 13, 10), G('spearman', 10, 9), G('crossbowman', 6, 7), G('crossbowman', 14, 8), G('archer', 10, 3),
        T('archer', 2, 8, 18), T('archer', 18, 8, 18)],
        'A square stone fort with two gate towers. Tripwire and caltrops at the gate.',
        'Un fuerte cuadrado de piedra con dos torres en la puerta. Cable trampa y abrojos en la entrada.')
    original('QUARRY_STRONGHOLD', 'quarry_stronghold', g.quarry_stronghold, 21, 12, (10, 1, 7), 0, [
        G('militia', 10, 16), G('militia', 10, 18), G('spearman', 10, 13), G('spearman', 8, 15), G('spearman', 12, 15),
        G('crossbowman', 6, 7), G('crossbowman', 14, 8), T('crossbowman', 2, 8, 18), T('crossbowman', 18, 8, 18)],
        'Cobblestone walls round a quarry pit; crossbows on the towers.',
        'Muros de adoquín alrededor de una cantera; ballestas en las torres.')
    original('WITCH_COVEN', 'witch_coven', g.witch_coven, 17, 12, (8, 1, 13), 0, [
        G('militia', 6, 13), G('militia', 10, 13), G('militia', 7, 11), G('archer', 2, 12), G('archer', 14, 12),
        M('witch', 6, 7, 4), M('witch', 10, 7, 4)],
        'A hut on stilts over a lily pond, inside a hedge of mangrove roots. Two witches throw potions from the '
        'porch, and a berry pit waits at the foot of the stair: bring milk.',
        'Una choza sobre pilotes en un estanque, dentro de un seto de raíces de mangle. Dos brujas lanzan pociones '
        'desde el porche, y un foso de bayas espera al pie de la escalera: lleva leche.')
    original('FROZEN_OUTPOST', 'frozen_outpost', g.frozen_outpost, 17, 12, (8, 1, 6), 0, [
        G('militia', 8, 8), G('militia', 6, 10), G('spearman', 6, 14), G('spearman', 10, 14),
        M('stray', 5, 5), M('stray', 11, 5), T('archer', 3, 6, 9)],
        'A trappers\' outpost behind a snow wall: a longhouse, an igloo, an archer on the watchtower. Powder snow '
        'under the open trapdoors on the path.',
        'Un puesto de tramperos tras un muro de nieve: una casa larga, un iglú y un arquero en la atalaya. Nieve '
        'polvo bajo las trampillas abiertas del camino.')
    original('KEEP', 'keep', g.keep, 25, 16, (12, 1, 9), 0, [
        G('militia', 12, 17), G('militia', 9, 18), G('militia', 15, 18), G('militia', 12, 20), G('militia', 5, 12),
        G('militia', 19, 12), G('spearman', 10, 16), G('spearman', 14, 16), G('spearman', 5, 8), G('spearman', 19, 8),
        G('knight', 8, 20), G('knight', 16, 20), T('crossbowman', 2, 10, 2), T('crossbowman', 22, 10, 2),
        T('crossbowman', 2, 10, 22), T('crossbowman', 22, 10, 22), W(12, 14)],
        'High walls and a reinforced gate only a Battering Ram breaks.',
        'Murallas altas y un portón reforzado que solo rompe un Ariete.')
    original('CAVALRY_ENCAMPMENT', 'cavalry_encampment', g.cavalry_encampment, 23, 10, (11, 1, 7), 0, [
        G('knight', 11, 12), G('knight', 9, 14), G('knight', 12, 15), G('knight', 5, 11), G('knight', 17, 11),
        G('crossbowman', 6, 6), G('crossbowman', 16, 6), G('militia', 11, 16), G('militia', 11, 18), W(11, 13)],
        'An open paddock full of knights. Spears and a narrow spot help.',
        'Un cercado abierto lleno de caballeros. Ayudan las lanzas y un paso estrecho.')
    original('SUNKEN_CRYPT', 'sunken_crypt', g.sunken_crypt, 19, 14, (9, 1, 9), 4, [
        G('militia', 9, 8), G('militia', 8, 11), G('militia', 10, 11), M('skeleton', 4, 6), M('skeleton', 14, 6),
        M('zombie', 5, 12), M('zombie', 13, 12), W(9, 10)],
        'A vault dug into the ground: one stair down, and a powder keg at its foot.',
        'Una cripta excavada: una sola escalera, y un barril de pólvora al pie.')
    original('ILLAGER_HALL', 'illager_hall', g.illager_hall, 21, 14, (10, 1, 7), 0, [
        M('vindicator', 8, 8), M('vindicator', 12, 8), M('vindicator', 8, 11), M('vindicator', 12, 11), M('vindicator', 10, 12),
        M('pillager', 7, 9), M('pillager', 13, 9), M('evoker', 10, 9), W(10, 12)],
        'A raiders\' hall in a dark-oak ring. Kegs wired at the hall door.',
        'Una sala de saqueadores en un anillo de roble oscuro. Barriles en la puerta de la sala.')


# ---------------------------------------------------------------- code and text output

def java_guard(entry):
    kind, what, x, y, z, posted = entry
    if kind == 'unit':
        fn = 'tower' if posted else 'guard'
        return f'CampTier.{fn}(UnitType.{what.upper()}, {x}, {y}, {z})'
    if kind == 'mob':
        return f'CampTier.mob(EntityType.{what.upper()}, {x}, {y}, {z})'
    return f'CampTier.mob(ModEntities.WARLORD, {x}, {y}, {z})'


def write_java():
    by_kind = {k: [l for l in LAYOUTS if l['kind'] == k] for k in KINDS}
    for k, ls in by_kind.items():
        assert ls, 'no layout for ' + k
    out = ['package dev.felipe.warbands.camp;', '',
           'import dev.felipe.warbands.registry.ModEntities;',
           'import dev.felipe.warbands.town.UnitType;',
           'import java.util.EnumMap;',
           'import java.util.List;',
           'import java.util.Map;',
           'import net.minecraft.core.BlockPos;',
           'import net.minecraft.world.entity.EntityType;', '',
           '/**',
           ' * Every camp layout: generated by {@code tools/gen_camps.py} together with the structure files. Don\'t edit',
           ' * by hand; change the generator and run it again.',
           ' */',
           'final class CampLayouts {',
           '\tprivate static final Map<CampTier, List<CampLayout>> ALL = new EnumMap<>(CampTier.class);', '',
           '\tstatic {']
    for k in KINDS:
        items = []
        for l in by_kind[k]:
            guards = ',\n\t\t\t\t\t\t'.join(java_guard(e) for e in l['guards'])
            spares = ', '.join(f'new BlockPos({x}, {y}, {z})' for x, y, z in l['spares'])
            cx, cy, cz = l['core']
            items.append(f'new CampLayout("{l["id"]}", {l["size"]}, {l["height"]}, new BlockPos({cx}, {cy}, {cz}), {l["sink"]},\n'
                         f'\t\t\t\t\t\tList.of({guards}),\n'
                         f'\t\t\t\t\t\tList.of({spares}))')
        out.append(f'\t\tALL.put(CampTier.{k}, List.of(\n\t\t\t\t' + ',\n\t\t\t\t'.join(items) + '));')
    out += ['\t}', '', '\tprivate CampLayouts() {', '\t}', '',
            '\tstatic List<CampLayout> of(CampTier tier) {', '\t\treturn ALL.get(tier);', '\t}', '}', '']
    with open(JAVA, 'w') as f:
        f.write('\n'.join(out))
    print(f'CampLayouts.java: {len(LAYOUTS)} layouts')


def write_lang():
    for fn, idx in (('en_us.json', 0), ('es_es.json', 1)):
        path = os.path.join(LANG, fn)
        with open(path, encoding='utf-8') as f:
            data = json.load(f)
        for key in [k for k in data if k.startswith('camp_layout.warbands.')]:
            del data[key]
        for lid, texts in REPORTS.items():
            data['camp_layout.warbands.' + lid] = texts[idx]
        with open(path, 'w', encoding='utf-8') as f:
            f.write(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def all_layouts():
    import camps_village, camps_feudal, camps_castle
    originals()
    camps_village.build()
    camps_feudal.build()
    camps_castle.build()
    order = {k: i for i, k in enumerate(KINDS)}
    LAYOUTS.sort(key=lambda l: order[l['kind']])  # stable: originals first within a kind


def lint_all(builders):
    import lint_structures
    failed = 0
    for lay in LAYOUTS:
        problems = lint_structures.lint(builders[lay['id']], lay)
        for p in problems:
            print(f"LINT {lay['id']}: {p}")
        failed += bool(problems)
    print(f'lint: {len(LAYOUTS) - failed}/{len(LAYOUTS)} layouts clean')
    return failed


if __name__ == '__main__':
    # Run through the module (not __main__) so the camps_* files and this script share one LAYOUTS list.
    import gen_camps
    lint_only = '--lint' in sys.argv
    captured = {}
    real_save = g.Builder.save
    g.Builder.save = lambda self, fn: (captured.__setitem__(fn[:-4], self), None if lint_only else real_save(self, fn))[1]
    gen_camps.all_layouts()
    import camp_props
    for lay in gen_camps.LAYOUTS:
        camp_props.add(captured[lay['id']], lay)
        camp_props.blend(captured[lay['id']], lay)
        if not lint_only:
            real_save(captured[lay['id']], lay['id'] + '.nbt')
    if not lint_only:
        gen_camps.write_java()
        gen_camps.write_lang()
    sys.exit(1 if gen_camps.lint_all(captured) else 0)
