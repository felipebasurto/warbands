# Camp design brief (read fully before touching a layout)

You are improving enemy-camp layouts of the Warbands Minecraft mod (Fabric, MC 26.1.2). A camp is a generated
structure file plus a garrison table. Your job: make each assigned layout look like a place a player wants to raid,
with zero generation errors. Judge by pictures, not by code.

## Loop for every layout (repeat until it scores 8+ on every row of the rubric)
1. `cd mods/warbands/tools`
2. Render: `python3 render_structures.py /tmp/<you>-<layout>.png <function_name> --big` (4 angles, 30 px blocks; the
   function name is the python function that builds it, e.g. `poachers_lodge` or `poachers_treehouse`). Read the PNG
   with the Read tool and write down 5 concrete flaws before changing anything.
3. Lint: `python3 gen_camps.py --lint 2>&1 | grep "^LINT <layout_id>"`. Must print nothing for your layouts.
4. Change the generator, render again, compare. Keep or revert. Do at least 3 render-and-fix rounds per layout.
5. Also print a plan view of layer 1 and 2 as ASCII (see `lint_structures.py` helpers `name`, `props`) and check
   interiors, because roofs hide rooms in the render.

Do NOT run gradle, do NOT commit generated files (`*.nbt`, `CampLayouts.java`, lang json): only python sources.
Never write outside your assigned functions/tables except to add small shared helpers (put them in your own module
section, prefixed with your kind name, so merges stay clean).

## Where things live
- `gen_structures.py`: the original layout of each kind (e.g. `bandit_camp`, `poachers_lodge`, `fort`, ...), plus
  helpers (`Builder`, `fill`, `ring`, `rect_ring`, `varied`, `hip_roof`, `stairs`, `pane`, `standard`, `alarm`, `spikes`,
  `pitfall`, `camp_floor`, `tent`).
- `camps_village.py`, `camps_feudal.py`, `camps_castle.py`: the other layouts, using the `Camp` class from
  `gen_camps.py` (`b.ground`, `b.unit`, `b.post`, `b.mob`, `b.warlord`, `b.spare`, `b.standard`, `b.report`, `b.done`) and
  helpers `log, fence, lantern, campfire, fence_gate, ladder, slab, circle, disc, wall, crenels, tower, walkway,
  relay, gallery, piston_gate, sculk, alarm, spikes, pitfall, tripwire, keg_wire, rigged_floor`.
- `gen_camps.py` `originals()`: size, height, core, sink and garrison table of the original layout of each kind, and
  the scouts' report (EN and ES). Keep them in sync with the structure you build (core position!).
- Structure conventions: odd square footprint, entrance on the south side (z = max), y = 0 is the floor layer (ground
  blocks), the game turns the camp so the entrance faces the town. x east, z south, y up. Anything above `height`
  is cut, so set height to fit roofs and towers.

## Hard rules (lint enforces most; breaking them ships a bug the player sees)
- The camp standard (`b.standard(x, z)` / `standard(b, x, y, z)`) stands in the OPEN in front of the main building:
  a free side, 2 blocks of air above the banner, reachable on foot from the entrance. Never inside a wall, roof, tent.
- Guard and spare spots: air at feet and head, solid floor, reachable from the entrance (tower posts excepted).
- No floating blocks (rails and roof pieces must touch something that reaches the ground), no plant/torch/banner/
  door/ladder with nothing under or behind it, hanging lanterns hang from a block.
- The entrance: a gate at least 3 wide and 2 high on the south wall, not blocked by a trap.
- Fire only on campfires/soul campfires with no wood or wool within 1 block sideways and 4 above.
- Traps (see the docstring of `gen_camps.py`): a relay powers only what touches it; every piston/dispenser sits next
  to its own relay; pistons only move `warbands:portcullis` and `warbands:rigged_floor`; never an alarm horn next to
  a relay. Traps must be telegraphed (visible tell), fair, and cannot be farmed for items.
- Custom blocks you may use: `warbands:alarm_plate, caltrops, powder_keg, war_relay, portcullis, rigged_floor,
  alarm_horn, camp_standard`. Do not invent new block ids.
- Every name in the scouts' report is player-facing text: keep EN and ES, one or two short sentences.

## Rubric (score each 0-10 for every layout, report the table)
1. Silhouette: readable from 40 blocks away; not a box; varied heights; roofs with overhang; corner posts.
2. Depth and texture: no wall plane longer than 4 blocks without a break (posts, recesses, trapdoor shutters, stairs
   as trim, upside-down stairs as eaves, slabs as ledges); 3-block main palette plus one accent; mossy/cracked mix.
3. Identity: props tell what the camp does (hunters: racks, pelts, targets; smugglers: crates, barrels, nets; ...).
4. Interior: enterable buildings have furnished rooms (bed, table, storage, light), not empty shells.
5. Terrain blend: edges break up (grass, moss, ferns, gravel, path), no perfect square slab; feet of walls hidden.
6. Play: readable route from gate to standard, cover, sight lines for archers, at least two interesting fights spots.
7. Traps: at least one telegraphed trap that changes how you approach (see helpers), never unfair.
8. Correctness: lint clean, standard/guards free, no leftover debug, block count sane (a camp under ~9000 blocks).

## Design principles (from build guides and from what already went wrong)
- Roofs: overhang the walls by 1, use stairs plus a slab ridge, add gable ends filled with planks/logs, dormers or a
  chimney to break long slopes. Half-height slabs under eaves make shadow lines.
- Walls: stone or cobble footing (1 block), plank or log body, log frame at corners and every 3-4 blocks, a band of
  stripped log under the eaves. Windows are panes with trapdoor shutters, not holes.
- Palette: stone carries structure, wood carries warmth, one dark accent (dark oak, blackstone, deepslate) for trim.
- Detail clusters beat uniform detail: put props in groups (woodpile + chopping stump + axe stand; barrels + crates +
  rope) and leave open floor between them.
- Lighting: lanterns on posts and eaves; never torches on wood.
- Height variety: at least three height levels in the silhouette (fence, wall, roof, tower, chimney, mast).
- Mix natural edge blocks (grass_block, podzol, moss_block, coarse_dirt, dirt_path, gravel) and low plants.
- Every camp should tell one small story a player notices (a half-eaten meal by the fire, a map table with a red X).
- Do not add leaves near the camp edge except as bushes on top of solid ground: they decay and drop items.

## Deliverable
Commit on your branch (python sources only) with a message listing layouts changed. Final report (short): per layout
the rubric table before and after, the lint result, the 3 biggest flaws you fixed, what is still weak, and the file
paths of your final renders.
