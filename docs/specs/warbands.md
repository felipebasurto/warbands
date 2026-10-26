# Warbands: design spec (v1)

> Working title. An Age of Empires–style mod: build a Town Center, stockpile food, wood, stone
> and gold, train soldiers, and lead them into battle.

Status: **v1, Phase 2 (Ages) and Phase 3 (Enemies) implemented** (see "Implementation notes" at the end); later ideas in "Roadmap: making it fun" · Target: Minecraft 26.1.2 / Fabric · Folder: `mods/warbands` · Mod id: `warbands`

## Core loop

1. Craft a **Town Center Blueprint** and right-click the ground. A small pre-made building appears.
2. Open the **Town Center** block in the middle of the building and deposit items into its **stockpile**.
3. **Train** Militia or Archers. You pay from the stockpile, the unit joins a timed queue, and it
   walks out of the Town Center when it's ready.
4. Soldiers **follow you** and fight what you fight.
5. Build **Houses** near the Town Center to raise the **population cap** and grow the army.

## Decisions (from the Q&A)

| # | Topic | Decision |
|---|---|---|
| 1 | Placement | **Blueprint item**. Right-click a spot to build a pre-made structure. The core block in it has the menu. |
| 2 | Resources | **Stockpile**. Deposited items turn into resource points stored in the Town Center. |
| 3 | Units | **Militia** (melee) and **Archer** (bow). |
| 4 | Look | **Humans in armor**: player-shaped, custom skins, real vanilla armor and weapons. |
| 5 | Control | **Always follow**. No command items in v1. |
| 6 | Targets | **What you attack** and **what attacks you** (or them). They don't attack hostile mobs on their own. |
| 7 | Population | **Houses raise the cap.** |
| 8 | Training | **Queue with a timer** and a progress bar. |
| 9 | Survival | **Wolf rules**: teleport to you when left behind, regenerate out of combat, permanent death. |
| 10 | Ages | **Not in v1.** The data is designed so ages and upgrades can be added later. |

## Content

### Blueprints (items)

| Item | Recipe idea | Builds |
|---|---|---|
| Town Center Blueprint | paper + map + gold ingot + logs | ~9×9 building: stone floor and walls, log pillars, a roof, and a **Town Center** block inside |
| House Blueprint | paper + logs + planks | ~5×5 hut with a **House** block (marker) inside |

- **Placing:** right-clicking the ground builds the structure facing the player. The blueprint is used up.
- **Validation:** building only works if the area is mostly clear (grass, flowers and snow get replaced).
  Otherwise you get a red message ("Not enough space") and nothing is used up.
- **Preview (v1):** while holding a blueprint, a particle outline shows the footprint, green if it
  fits and red if it doesn't. A full "ghost block" preview can come later.
- **Structure data:** stored as structure files (`data/warbands/structure/*.nbt`), so buildings can be
  redesigned in-game with a vanilla Structure Block without touching code.

### Town Center (block + menu)

- Only its **owner** (the player who placed it) can open it. Breaking it drops nothing special, and
  the stockpile is lost, so protect it.
- **Menu layout:** one deposit slot, four resource counters (🍖 food, 🪵 wood, 🪨 stone, 🪙 gold), a
  population counter (`3 / 10`), a training button per unit (showing its cost, greyed out if you
  can't afford it), and the queue (up to 5) with a progress bar.
- **Stockpile conversion** (defined in item tags / data, easy to tune):

| Resource | Items | Value |
|---|---|---|
| Food | anything edible | its hunger value (bread = 5, steak = 8, apple = 4) |
| Wood | logs / stems | 4 each (planks = 1) |
| Stone | cobblestone, stone, deepslate, blackstone… | 1 each |
| Gold | gold ingot / nugget / block | 9 / 1 / 81 |

### Houses

- Each House block within **32 blocks** of a Town Center adds **+5 population** to it.
- The Town Center gives **5** on its own, up to a **maximum of 50**.
- Breaking a house lowers the cap. Soldiers you already have stay, but you can't train more until
  you're back under the cap.

### Units

| Unit | Cost | Train time | Health | Gear | Behavior |
|---|---|---|---|---|---|
| Militia | 30 food, 9 gold | 15 s | 20 | stone sword, leather armor | melee |
| Archer | 20 wood, 18 gold | 20 s | 16 | bow, leather cap + tunic | keeps its distance and shoots; arrows can't be picked up |

- **Skins:** a simple soldier skin per unit type. Later, a team-color tint per owner.
- **Owner:** the player who trained them. Their name tag shows e.g. "Felipe's Militia".
- **No friendly fire:** they won't hurt their owner, the owner's other soldiers, or the owner's pets.
  Their arrows pass through friendly units.
- **Wolf rules:**
  - They teleport next to you when more than 16 blocks behind and not fighting.
  - They regenerate 1 HP every 3 s after 10 s without combat.
  - They don't despawn.
  - Death is permanent, and it frees their population slot.
- **They don't** pick up items, open doors, or ride things in v1.

## How we reuse vanilla AI (technical)

Soldiers are built the way wolves are, by combining vanilla **goals**:

| Need | Vanilla piece |
|---|---|
| Owner, follow and teleport | extend `TamableAnimal` (or `PathfinderMob` + `OwnableEntity`) and use `FollowOwnerGoal` |
| Defend owner | `OwnerHurtByTargetGoal` |
| Attack what the owner attacks | `OwnerHurtTargetGoal` |
| Retaliate | `HurtByTargetGoal` |
| Melee | `MeleeAttackGoal` |
| Bow | `RangedBowAttackGoal`, or a small copy of it if its generic bounds (it expects a `Monster`) don't fit |
| Rendering | `HumanoidMobRenderer` with the player model plus the vanilla armor layer, so we only draw skin textures |

Menu: a vanilla-style `AbstractContainerMenu` with `ContainerData` to sync counters and progress.
Train buttons use the vanilla `clickMenuButton` mechanism, so no custom network packets are needed.

**Risks to check first** (a quick prototype before building everything):
- Can a `TamableAnimal` subclass use the player model and armor rendering without trouble?
- Do the bow-attack generics accept our entity?
- Does placing a structure template from an item work cleanly in 26.1.2?

## Phase 2 plan: Ages

Phase 2 turns Warbands into a real progression: your town **advances through Ages**, and each Age
unlocks units, buildings and upgrades. The biggest cost is still gold and diamonds, which keeps it mid/late game.

| Age | Advance cost (from stockpile) | Unlocks |
|---|---|---|
| **Village Age** (start) | none | Militia, Archer, House |
| **Feudal Age** | 500 food, 200 gold, 1 diamond block (item) | **Barracks** (Spearman: counters mounted units), **Archery Range** (Crossbowman), **Blacksmith** (upgrades), iron gear for existing units |
| **Castle Age** | 800 food, 500 gold, 1 netherite ingot (item) | **Knight** (mounted, tamed-horse AI), **Stable**, **Watch Tower** (archers garrison it and defend the town), **Siege Workshop** (Battering Ram) |

- **Advancing** is a timed research step at the Town Center (like AoE). The building visually upgrades:
  a second structure file per Age swaps in a bigger Town Center.
- **Upgrades at the Blacksmith:** Forging (+1 damage), Fletching (+range and accuracy), Scale Armor
  (+2 armor). They apply to every soldier you own, stored per player like the army registry.
- **Commands:** a **War Horn** item. Right-click cycles *Follow / Hold here / Charge* for soldiers within
  24 blocks. Hold makes them guard a spot, which makes Watch Towers and defense work.
- **Villager Workers:** optional late unit that walks between a resource site and the Town Center,
  depositing wood or stone slowly (a gentle idle income, capped).
- **Team colors:** every owner gets a color, tinting soldiers' tabards and hoods and the Town Center banners.
- **Guidance:** each Age gets its own branch in the Warbands advancement tab, and the Handbook gets
  one page per new building and unit.
- **Data-driven units:** move `UnitType` into JSON (`data/warbands/unit/*.json`: cost, time, entity,
  loadout, required Age and building) so new units are mostly data.

**Suggested order:** War Horn (done) → Age research (done: the timer and the building, not the new units) → Barracks and Spearman (done) → Archery Range and Crossbowman (done) → Blacksmith upgrades (done) →
Castle Age units (Knight, done) → Watch Tower (done) → Siege Workshop and Battering Ram (done) → workers and team colors.

## Roadmap: making it fun (Phase 3 and beyond)

Status: ideas from the 2026-09-26 playtest talk. **#1 and #2 are designed** in "Phase 3 plan: Enemies" below
(interview done); the rest still get a `mod-design` interview before any code.

**The problem they solve:** Phase 2 made a strong build-and-train game, but the army has little to *do*. In
singleplayer, soldiers only fight monsters that wander by, and the Castle Age tools (Battering Ram, Knights,
Watch Towers, Spearmen as a counter) have no real target. Fun needs **enemies with something to lose, and a
reason to come for you**, so the loop becomes *grow → defend → conquer*.

| # | Idea | What it adds | Stage | Watch out for |
|---|---|---|---|---|
| 1 | **Rival warbands: enemy camps** | Fortified camps in the world (pillager palisade, bandit fort, Castle Age illager keep) with walls, gates, towers and guards. Rams break the gates, archers clear the walls, knights run down the defenders. | mid-late | Loot capped in value and camps stay empty once taken (no farming); structure blocks drop nothing like ours; a "nearest camp" hint and a conquest advancement branch |
| 2 | **Raids on your town** | Every few in-game days an enemy warband marches on your Town Center at night, bigger each age, announced by a horn. Towers, garrisons and walls matter. | mid-late | Peaceful-difficulty behavior, a clear warning and countdown, no griefing of player-built blocks beyond doors/gates, scale with age not with army size |
| 3 | **Villager workers** | Peasants that farm, chop and mine near the town and deliver to the stockpile. Houses become an economy, not just population; food vs. army is a real choice. | mid | Capped idle income; not a vanilla `Villager` (see the smith); pathing near the town only |
| 4 | **Veterans** | Soldiers earn ranks from kills (Recruit → Veteran → Elite): small stat boosts, a badge and a name. Permadeath makes retreating and healing matter. | mid | Keep boosts small; show rank in the name tag and the guide |
| 5 | **Better commands** | Rally Banner (plant it, the army holds there), squads (pick which unit types the horn orders), simple formations (archers behind, spearmen in front). | mid-late | No new keybinds needed; readable feedback for each order |
| 6 | **A late-game goal** | A Wonder (huge, expensive Castle Age build with a big reward) or relics found in camps that buff the whole town. | late | A real finale, priced above the Castle Age |
| 7 | **Team colors** (from Phase 2) | Each owner's color tints tabards, hoods and banners; enemy camps get their own. | any | Needed once enemies exist, so both sides read at a glance |

**Suggested order:** Phase 3 = **1 + 2 together ("Enemies")**, since they reuse the whole tech tree and give it a
purpose → Phase 4 = **3 (Workers)**, once a stronger economy is worth having → then 4, 5 and 7 as polish on the
army → 6 as the finale.

## Phase 3 plan: Enemies (camps and raids)

Status: **implemented** (2026-09-26, PR to `main`; see "Implementation notes (Phase 3)" at the end). Roadmap ideas #1 and #2.

**Core loop:** grow your town → a **War Map** sends you to an enemy **camp** matched to your age → take it with
your army (rams for the gates, archers for the walls, knights for the defenders) → **plunder** flows into your
stockpile → the enemy strikes back with a **raid** on your town → defend it, **repair** what they broke → bigger camps.

### Decisions (from the Q&A)

| Question | Answer |
|---|---|
| Who are the enemies? | A **rival warband**: our own unit types (militia, archers, spearmen, crossbowmen, knights, rams) as an ownerless raider faction with dark outfits. Reuses all our AI; they fight your soldiers properly. |
| How do camps appear? | **War Map** item bought at the Town Center: it chooses a site a few hundred blocks away, builds the camp when you get close, and points to it like a lodestone compass. No world generation. |
| Conquest reward | **Plunder + trophy**: resources straight into the stockpile (a bit more than the fight costs), a trophy banner, an advancement. Stays inside the mod's economy. |
| Camp kinds | **One per age**: Bandit Camp (Village), Fort (Feudal), Keep (Castle). |
| When do raids happen? | **Timer + retaliation**, from the Feudal Age: a raid every few in-game days at night, plus a revenge raid the night after you conquer a camp. The Village Age stays safe (no raids, not even retaliation). |
| Raid stakes | Felipe: *"they steal stock and break buildings. you might have to reconstruct them"*. Raiders who reach the Town Center plunder part of the stockpile; on the way they wreck buildings. |
| Raid scaling | **By age and raids won**: Feudal raids bring militia and archers, Castle raids add spearmen, crossbowmen, knights and a ram; each raid you beat makes the next one a little bigger (capped). |
| Opt-out | **Off on Peaceful**, plus a gamerule `warbandsRaids` (default on). |
| What raiders break | **Warbands buildings only** (blocks placed by blueprints, the owner's) plus doors, gates, fences and trapdoors in the town area. Hand-built blocks and the world are never touched. |
| Rebuilding | **Repair button** at the Town Center: every building remembers its blueprint, position and rotation; missing blocks come back for stone. |
| Destroyed completely? | **Walls yes, cores no.** Core blocks (Town Center, Barracks, Stable...) can't be broken by raiders. A building below half its blocks stops working (like houses already do) until repaired. |
| Defense reward | Raiders drop nothing (no farming), but a **won raid pays gold** into the stockpile, scaled to its size, plus an advancement. It helps pay for repairs. |

### Camps

| Camp | Age | War Map cost | Guards | Layout (structure) | Plunder on conquest |
|---|---|---|---|---|---|
| **Bandit Camp** | Village | 100 food, 50 gold | 4 militia, 2 archers | ~15x15 log palisade, fence-gate entrance, tents, a campfire, the **Camp Banner** core | 150 food, 100 wood, 80 gold |
| **Fort** | Feudal | 200 food, 100 wood, 100 gold | 4 militia, 3 archers, 3 spearmen, 2 crossbowmen | ~21x21 stone walls, wooden gate, two corner towers with archers posted | 400 food, 200 wood, 150 stone, 250 gold |
| **Keep** | Castle | 400 food, 200 wood, 300 gold | 6 militia, 4 crossbowmen, 4 spearmen, 2 knights | ~25x25 high walls, a **reinforced gate** only a Battering Ram breaks, a central keep | 800 food, 400 wood, 400 stone, 600 gold |

- **War Map:** bought at the Town Center (a new button, cost from the stockpile). One active camp per player at a time;
  the button says why when disabled (camp already active, wrong age, not enough resources). The map points at the camp
  (`LodestoneTracker`, no lodestone needed) and its tooltip shows the tier, distance and direction.
- **Site:** 250-400 blocks from your Town Center in a random direction, on dry, fairly flat land (heightmap check,
  no water or lava under the footprint; retry other directions). Stored as a pending camp; it's **built when a player
  gets within 128 blocks** (chunks loaded then), using the same clear-terrain + foundation + template code as blueprints.
- **Guards** spawn with the camp: raider-faction soldiers set to Hold around the camp (home = camp center), persistent.
  Fort and Keep archers are posted on their towers (reuse the Watch Tower posting).
- **Conquest:** when no guard is left alive, right-click the Camp Banner: plunder goes into the Town Center that sold the
  map (any distance), you get the trophy banner item and the advancement, and the camp is marked conquered for good
  (its banner turns into a plain block, no second payout). Camp blocks drop nothing, like our buildings.
- **Reinforced gate** (Keep): a block with the hardness of obsidian for players; a Battering Ram on Charge breaks it.
  Walls are ordinary (protected) blocks, so tunnelling is possible, just slow. TODO(Felipe): OK, or should walls resist too?

### Raids

- **Eligible town:** Feudal Age or later, difficulty not Peaceful, gamerule `warbandsRaids` on, and its owner online
  within 96 blocks of the Town Center (so chunks are loaded). A due raid waits until those are true.
- **Timer:** the first raid comes at least 2 in-game days after reaching Feudal; then every **4-6 in-game days**
  (random), starting after nightfall. **Retaliation:** conquering a camp schedules a raid for the next night (one size
  bigger), from the Feudal Age on.
- **Warning:** 60 s before, the vanilla raid horn (`SoundEvents.RAID_HORN`), a chat message with the direction
  ("A warband approaches from the north-east!") and a red boss bar (`ServerBossEvent`: "Raid: N raiders").
- **Size:** Feudal base 6 (4 militia, 2 archers); Castle base 10 (3 militia, 2 archers, 2 spearmen, 1 crossbowman,
  1 knight, 1 ram). +1 raider per raid won, capped at +6 (Feudal) / +8 (Castle).
- **Spawn and march:** 48-64 blocks from the Town Center, on the surface, all from one direction. They walk to the
  Town Center, fighting players and your soldiers within 16 blocks on the way.
- **Wrecking:** raiders break the town owner's Warbands structure blocks within reach (never cores, never the Town
  Center core) and doors, gates, fences and trapdoors, about one block per raider every 3 s, while not fighting.
  Rams keep their Charge behavior. Broken blocks drop nothing; the building records them as missing.
- **Reaching the Town Center:** a raider within 4 blocks of the core for 5 s **plunders 20% of each resource**; then the
  whole raid retreats and despawns out of sight. The raid is lost (message, no payout).
- **Victory:** every raider dead → **gold paid into the stockpile**: 15 per raider (Feudal), 25 (Castle), plus an
  advancement the first time. Raiders drop nothing and give no XP.
- **Timeout:** 10 minutes without a result (stuck raiders) → they retreat, no payout, no plunder.

### Repair

- A new **building registry** records every blueprint placement: blueprint, origin, rotation, owner, core. It generalizes
  `HouseRegistry` (houses migrate to it; the half-blocks rule applies to every building).
- **Town Center menu → Repair:** "Repair N buildings: X missing blocks, Y stone". **1 stone per missing block.** It puts
  back template blocks only where the world now has air or a replaceable block (never overwrites a player's block).
  Containers come back empty. Disabled with a reason: nothing to repair, a raid in progress, not enough stone.

### Game stage, prices and exploits

- **Stage:** camps are mid-late (a Bandit Camp is the Village Age's first taste; Fort and Keep need the Feudal/Castle
  armies). Raids start at Feudal, when the army can answer.
- **Prices:** plunder ≈ 1.5-2× the War Map's cost, before your losses (permadeath). A camp is a gamble that pays off
  with a good army, not free money.
- **Exploits closed:** guards and raiders drop nothing and give no XP; camp blocks drop nothing; one active camp per
  player and one payout per camp; raids can't be summoned on demand (only via a paid conquest); raiders only break Warbands
  buildings and doors/gates/fences, so they can't be aimed at the world; repair only replaces missing template blocks.
- **Multiplayer:** camps and raids belong to one owner; other players can help fight. Raiders ignore other players'
  towns unless attacked.

### Guidance

- Guide pages: Enemies (who they are), War Maps and camps (the three tiers), Raids (warning, stakes, reward), Repair.
- Advancements branch: buy a War Map → conquer a Bandit Camp → a Fort → a Keep; defend a raid; repair a building.
- Tooltips: War Map (tier, distance, direction), trophy banners. Messages with reasons for every refused action.
- **Still to Come** loses "Enemy camps" and "Raids" when this ships.

### How we reuse vanilla

| Need | Reuse |
|---|---|
| Enemy soldiers | Our `SoldierEntity` subclasses, untamed, with a `Faction` (PLAYER / RAIDER); raider skins are palette swaps in `skins.py` |
| Targeting | `NearestAttackableTargetGoal` (players, tamed soldiers) for raiders; our `ChargeHostilesGoal` / defend goals also pick raiders |
| Raid timing, horn, boss bar | Patterns from `net.minecraft.world.entity.raid.Raid`; `ServerBossEvent`; `SoundEvents.RAID_HORN` |
| Pointing at a camp | `DataComponents.LODESTONE_TRACKER` on the War Map (compass needle, no lodestone) |
| Camp buildings | `gen_structures.py` + `BlueprintPlacer` (clearing, foundation, rotation, `StructureBlocks` protection) |
| Posted archers on camp towers | Watch Tower posting code |
| Breaking blocks | The ram's `smash`/`trySmash` pattern, generalized |
| Gamerule | Fabric `GameRuleBuilder` (`net.fabricmc.fabric.api.gamerule.v1`) |

### Risks to prototype first

1. **Raiders' march:** 48-64 blocks of pathfinding to the Town Center through a town, without getting stuck (waypoints if needed).
2. **Faction fights** feel good: soldiers and raiders pick each other as targets, no friendly fire inside a faction.
3. **Building a camp 250-400 blocks away** lazily when a player approaches, on decent terrain.
4. **Repair with rotation**: rebuilding from a template at the saved origin and rotation, only into empty spots.

### Build order (smallest valuable step first)

1. Raider faction: `Faction` on soldiers, dark skins, targeting both ways, creative spawn eggs to test a brawl.
2. Raids v0: timer, warning, boss bar, march, fight, victory payout, gamerule and Peaceful off (no wrecking yet).
3. Building registry + Repair button (houses migrate).
4. Wrecking and plunder at the Town Center.
5. War Map + Bandit Camp + conquest + plunder + advancement.
6. Fort and Keep (+ reinforced gate), retaliation raids and scaling.
7. Guide pages, advancements, balance pass, game test (brawl, raid won/lost, repair, conquest), screenshots.

### Open questions

- Faction name? Working title "raiders"; the guide could call them *the Black Banner*.
- Numbers to taste after playtesting: raid interval (4-6 days), plunder share (20%), repair price (1 stone per block).
- Keep walls: ordinary (tunnelling possible) or reinforced like the gate?

## Phase 4 plan: Expeditions (loot, raid types, more camps, traps, warlords, relics)

Status: **implemented 2026-09-27/28 (4A-4F, PR to `main`)**; notes per phase at the end. From the brainstorm with Felipe.

**Frame (Felipe):** Warbands is a **mid/late-game adventure layer on top of Minecraft**, not a replacement. The player
gathers resources the normal way (no workers, no market, no new ages, no Wonder). Phase 4 gives the army more places to
go and better reasons to go there.

### Decisions

| Topic | Decision |
|---|---|
| Loot | **Mostly vanilla items**, priced by age, themed per camp and raid kind. Plus **something super cool and rarer** that's ours: Warlord weapons (4E) and Relics (4F). |
| Delivery | **Resources go straight into the stockpile** (as today); **item loot goes in a chest** the player opens in the world. |
| Camp themes | **Biome-themed** (forest, shore, mountain) and **fantasy-themed** (crypt, witch coven, illager hall). Fantasy camps use our raiders plus **vanilla mobs** as guards for now; new units may come later. |
| Traps | Camps get **traps** (telegraphed, explained in the handbook, never farmable). |
| Raid kinds | **Random without repeats**: a shuffled bag per town, never the same kind twice in a row. All four new kinds (Night Stalkers, Shield Wall, Siege, Cavalry) plus today's Warband. |
| Raid spoils | **Where the raiders came from** (a short trip after the fight, marked by signal smoke). |
| Camps | **All eight** proposed kinds: biome (Poachers' Lodge, Smugglers' Dock, Quarry Stronghold, Cavalry Encampment) and fantasy (Witch Coven, Frozen Outpost, Sunken Crypt, Illager Hall). The War Map picks **at random, no repeats** (a bag per player and age). |
| Traps | **All of them:** alarm plates, arrow tripwires, pitfalls, caltrops and powder kegs. |
| Warlords | **Castle camps only.** Strength scales with the camp: the Keep's Warlord is a real boss (phases, 2-3 moves), the other Castle camps' are mini-bosses (about 3x a soldier's health, one move). |
| Rare rewards | **All four, split by source:** Warlord weapons from Warlords; Relics as the first-clear reward of each fantasy camp; the Warlord's Plate set (4 pieces) in the rare pool of Castle War Chests; special mounts from the Cavalry Encampment. |
| Relic power | **Small buffs**, one town-wide effect each. Nice to have, never required. |
| Order | **Raids, then camps:** 4B raid kinds → 4C camps → 4D traps → 4E Warlords and weapons → 4F relics, gear set, mounts. |

### Loot rules (every phase)

- Loot is rolled **once**, only after a camp is claimed or a raid is won, from a vanilla loot table
  (`data/warbands/loot_table/chests/...`), so it's easy to tune in JSON.
- Each table has a normal pool (4-8 rolls of themed items) and a **rare pool** (one roll, a few % for a jackpot).
- Tiers: Village = rough gear, leather, iron, emeralds; Feudal = iron, gold, redstone/lapis, enchanted books, a few diamonds;
  Castle = diamonds, strong enchanted gear, experience bottles, netherite scrap and templates (rare).
- No farming: camps stay one at a time, raiders and guards drop nothing, and the chests only appear when there's a win.

### 4A: War Chests and Raid Spoils (smallest step, uses only what exists)

- **Camp War Chest:** when the standard is claimed, it becomes a **War Chest** (a vanilla chest with the tier's
  loot table) instead of black wool. Its position is released from the camp's protected blocks, so the burn-down leaves
  it standing on the dirt, and it's an ordinary chest afterwards. Resource plunder is unchanged.
- **Raid Spoils:** a won raid leaves the **raiders' war chest where they came from** (the spawn with the smoke signal),
  with the age's raid loot table. The victory message says in which direction; signal smoke rises there for a while.
- Tables: `chests/camp/bandit_camp`, `chests/camp/fort`, `chests/camp/keep`, `chests/raid/feudal`, `chests/raid/castle`.

### 4B: Raid kinds (random, no repeats)

The warning names the kind, so the player can prepare the right answer. Each kind has its own units and spoils table.

| Kind | From | Who comes | Best answer | Spoils theme |
|---|---|---|---|---|
| **Warband** (today's raid) | Feudal | mixed | anything | gold, iron, basic gear |
| **Night Stalkers** | Feudal | archers and crossbowmen, at full dark | melee rush, towers, light | arrows, tipped arrows, spyglass, bows |
| **Shield Wall** | Feudal | spearmen and militia, slow and tough | archers | iron, shields, armor pieces |
| **Siege** | Castle | rams and crossbowmen with an escort | knights sally out | iron blocks, TNT, redstone |
| **Cavalry** | Castle | knights, fast | spearmen | saddles, horse armor, golden carrots |

- A shuffled **bag** of the kinds the town's age allows, saved per town; drawn until empty, then refilled; the first draw
  after a refill can't be the last kind. Revenge raids draw from the bag too (one raider bigger, as today).
- Size and scaling rules stay as today (by age and raids won).

### 4C: More camps per age (biome and fantasy themes)

The War Map picks a **camp kind** for the town's age from a bag (like raid kinds), then looks for a site in a matching
biome (tries more directions and distances, then falls back to any dry site with a plain kind).

| Age | Kind | Theme / biome | Guards | Loot theme |
|---|---|---|---|---|
| Village | Bandit Camp (exists) | any | militia, archers | rough gear |
| Village | **Poachers' Lodge** | forest / taiga | archer-heavy | leather, leads, name tags, saddle |
| Village | **Smugglers' Dock** | shore / beach | militia, archers | fish, gold, emeralds, a buried treasure map |
| Feudal | Fort (exists) | any | mixed | iron, books |
| Feudal | **Quarry Stronghold** | hills / stony | spearmen, crossbowmen | iron, redstone, lapis, some diamonds |
| Feudal | **Witch Coven** (fantasy) | swamp | raiders + vanilla witches | potions, brewing ingredients, a rare potion book |
| Feudal | **Frozen Outpost** (fantasy) | snowy biomes | raiders + vanilla strays | blue ice, tipped slowness arrows, a rare trident |
| Castle | Keep (exists) | any | everything, ram gate | diamonds, templates |
| Castle | **Cavalry Encampment** | plains / savanna | knights | horse armor, saddles, books |
| Castle | **Sunken Crypt** (fantasy) | any, dug into the ground | raiders + vanilla skeletons and zombies (persistent) | enchanted gear, XP bottles, rare netherite scrap |
| Castle | **Illager Hall** (fantasy) | dark forest | vindicators, pillagers, one evoker (vanilla) | totem of undying (rare), emeralds, crossbows |

- Technically: `CampTier` becomes a list of kinds per age; each kind is a structure plus guard list plus loot table.
  Vanilla-mob guards are counted the same way (by UUID), made persistent, and drop nothing.
- Trophies stay per age (bandit, fort, keep), so the age requirements don't change.

### 4D: Traps in camps

Telegraphed (visible if you look), explained on a handbook page, placed in structures, protected like other camp blocks.

- **Alarm plates:** stepping on one rings a bell and rouses the whole camp (reuses `Camps.rouse`).
- **Arrow slits:** tripwire → dispensers that shoot. Dispensers in camps can't be opened (no free arrows) and their
  arrows can't be picked up.
- **Pitfalls:** trapdoor over a short drop into powder snow or berry bushes (slows and hurts, doesn't kill).
- **Caltrops** (our block, Feudal+ camps): slows and hurts whoever walks on it; your soldiers path around it.
- **Powder kegs** (Castle camps): a tripwire sets off a blast that hurts but **breaks no blocks**.
- Traps turn off when the camp is claimed.

### 4E: Warlords (the rare item begins)

- **Castle camps only** get a **named Warlord** with a boss bar. The Keep's Warlord is a real boss (phases, 2-3 moves:
  a war cry that buffs nearby guards, a charge, a ground slam); in the other Castle camps he's a mini-boss (about 3x a
  soldier's health, one move). The standard can only be claimed once he falls.
- He drops his **Warlord weapon**: a named, enchanted vanilla weapon with a unique look (a custom model on the same
  item). Once per camp.

### 4F: Rare rewards (relics, gear set, mounts)

- **Relics:** the first time you take each fantasy camp, it gives its Relic: a unique item you place on a **Relic
  Pedestal** in the Town Center for one **small** town-wide effect (soldiers regenerate faster, raids warn earlier,
  horses faster...). A collection goal ("2 of 4 relics"); the same relic can't be stacked.
- **Warlord's Plate:** 4 named armor pieces (a unique look, strong enchants) in the rare pool of Castle War Chests.
  Collect the set.
- **Special mounts:** the Cavalry Encampment can give a rare armored warhorse (spawn egg-like item, one per camp).

### Later (not planned yet)

- A Nether warband camp (blackstone war camp, netherite loot) once the army can follow through portals.
- New units for fantasy camps (necromancer, witch-doctor).

### Open questions

- Warlords "scale by age" + "Castle only" were both picked: read as "scale by camp within the Castle Age" (above).
- Raid kind timing: Night Stalkers only at full dark, others as today.
- Numbers to tune after play: loot weights, Warlord health, trap damage.

## Build order

1. Warrior entity (Militia) that spawns from an egg, follows and fights. This is the core fun; test it first.
2. Archer.
3. Town Center block, stockpile menu and training queue (spawning the entities from step 1).
4. Houses and population.
5. Blueprints and structure placement (with the particle preview).
6. Recipes, textures, translations (EN + ES), balance pass.

## Implementation notes (v1)

- **Code map:** `entity/` (soldiers + bow goal), `town/` (Town Center, Hearth, stockpile, army/house registries),
  `blueprint/` (items + placement), `client/` (renderer, menu screen, particle preview).
- **Buildings:** `tools/gen_structures.py` generates `data/warbands/structure/*.nbt`. `tools/gen_textures.py`
  generates all textures. You can re-run either one or replace their output with hand-made files.
- **Houses:** the house core block is called a **Hearth**. It's a glowing brazier, and population counts hearths within 32 blocks.
- **Progression (mid/late game):** Handbook = book + gold nugget. Every player also gets one on first join.
  Town Center Blueprint = `EGE / SMS / SDS` (2 emeralds, gold block, 4 stone bricks, map, diamond).
  House Blueprint = `LPL / BIB` (2 logs, paper, 2 bricks, iron ingot), only placeable within 32 blocks of **your** Town Center.
- **Unit prices:** Militia 60 food + 20 gold (25 s), Archer 25 wood + 45 gold (30 s), close to Age of Empires values.
- **Placement:** the clicked block is the spot in front of the entrance, and the building extends away from the player.
  Natural terrain (`warbands:clearable` block tag: dirt, grass, stone, sand, leaves, snow...) is cleared.
  Gaps under the floor are filled with cobblestone (up to 8 deep). Built blocks, containers and liquids still block placement.
- **No looting:** every block a blueprint places is recorded (`StructureBlocks`). Breaking one drops nothing, by any
  means: mining, explosions, lost support and fluids (a mixin on `BlockState.getDrops`), pistons can't push them, and
  the Blacksmith's anvil doesn't fall. Container contents still drop, and blocks you place there later drop normally.
- **Guidance:** a Warbands Handbook and a Warbands advancement tab that walks the whole path,
  with recipes unlocking as you progress.
- **Soldiers' look:** their outfits are in the skins (kettle helmet and gambeson; hood and quiver), and armor is a stat
  (militia 6, archer 3, spearman 5, crossbowman 4, knight 7). Militia and knights also carry a shield.
- **Teleport distance:** 24 blocks (vanilla pets use 12).
- **Spawn eggs:** the soldiers they spawn have no owner. The first player to right-click one becomes its commander.
- **Feeding:** right-clicking your own injured soldier with food heals it.
- **Friendly fire:** you can't hurt your own soldiers, and they can't hurt you, each other, or your pets.
- **Stockpile limit:** each resource is capped at 30,000, because menu numbers sync as 16-bit values.
- **Test:** `./gradlew :warbands:runClientGameTest` (~50 s) plays the whole tech tree in a real world: Village loop,
  both age-ups, every building (stone costs checked), one of each unit, a posted Watch Tower, then a picture of every
  menu and every guide page, saved to `build/run/clientGameTest/screenshots`. Timers are fast-forwarded by calling the
  block entities' own `serverTick` in a loop (`/tick sprint` doesn't speed up the client harness). Only new or changed
  shots are copied into the gitignored blog archive `screenshots/`, by hand (`--only`).
- **Barracks (phase 2):** Feudal only, within 32 blocks of your Town Center. Recipe `S S / IPI / S S` (4 stone bricks, 2 iron ingots, paper), unlocked when the town reaches Feudal Age. The Barracks trains Spearmen (40 food, 20 wood, 15 gold, 22 s) and pays the nearest Town Center. That queue counts toward the same population cap, including a full queue if the Barracks chunk is not loaded. A spearman has the same attack as a militia (5) and +4 more against a horse or its rider. The spear is not a recipe and is not dropped. Breaking the Barracks refunds its queue to the Town Center.
- **Archery Range (phase 2):** Feudal only, same 32-block gate as the Barracks. Recipe `S S / IBI / S S` (4 stone bricks, 2 iron ingots, a bow), unlocked with the Barracks when the town reaches Feudal Age. It trains Crossbowmen (40 wood, 60 gold, 35 s): 20 health, 4 armor, range 20, a shot every 30 ticks for 5 damage. Bolts cannot be picked up, and the soldier holds a vanilla crossbow so nothing new is farmable. The queue shares the Town Center cap with the Barracks and the town's own queue. An unloaded range still reserves a full queue. Breaking it refunds the queue.
- **Blacksmith (phase 2):** Feudal only, within 32 blocks. Recipe `S S / ITI / S S` (4 stone bricks, 2 iron ingots, a smithing table), unlocked with the other Feudal blueprints. Three one-time upgrades, paid from the Town Center, stored per commander: Forging (200 food, 120 gold, +1 attack), Fletching (150 wood, 120 gold, +5 range and half the inaccuracy for archers and crossbowmen), Scale Armor (200 food, 150 gold, +2 armor). They apply to every soldier that commander owns, including ones trained later. Soldiers are not given iron items, so nothing new can be farmed. Breaking the Blacksmith does not refund a purchase.
- **Stable and Knight (phase 2):** Castle Age only, within 32 blocks. Recipe `S S / GLG / S S` (4 stone bricks, 2 gold ingots, leather), unlocked when the town reaches Castle Age. The Stable trains Knights (60 food, 80 gold, 40 s): 28 health, 7 armor, an iron sword and shield that are not dropped. Each knight rides a tamed warhorse in iron barding. The horse is not a soldier, damage hits the rider, and neither the horse nor its armor drops. Spearmen deal their mounted bonus to the knight and to the horse. The queue shares the Town Center cap. Breaking the Stable refunds its queue.
- **Watch Tower (phase 2):** Castle Age only, within 32 blocks. Recipe `S S / IAI / S S` (4 stone bricks, 2 iron ingots, an arrow), unlocked with the Stable. Post sends one owned archer or crossbowman within 24 blocks onto the roof (4 spots). They hold that spot and shoot monsters in their normal range, without walking off. Recall, breaking the tower, or a War Horn order sends them back to Follow. Militia, spearmen and knights cannot be posted.
  Posted soldiers' home covers their shooting range (`SoldierEntity.POST_RANGE`, 24): vanilla target goals refuse
  anything outside a mob's home, so a 1-block home never shot. The ranged goals keep them from walking, and a
  soldier knocked off the platform climbs back within a second (or gives up the post if the spot is blocked or the
  tower is gone). The tower targets any hostile mob (`MobCategory.MONSTER`), creepers included.
- **Review pass (phase 2 buildings):** merged with the age bonuses and the illustrated guide. Every new unit declares
  its base health/armor so age bonuses apply. Training buildings, the Blacksmith and the Town Center sync once a
  second instead of every tick, and every refused purchase says why. Forging adds +1 to melee and crossbow bolts
  (its text used to promise "every soldier"). The Warhorse returns to the commander after a Charge, like foot
  soldiers. The guide has a page per building and unit, and the age-up announcement and Advance tooltip list the
  blueprints each age unlocks. Known debt: Barracks, Archery Range and Stable are copies of one class each (block,
  block entity, menu, screen, registry); merge them into one parameterized training building once compiling works.
- **Ages (phase 2, second slice):** the Town Center stores Village / Feudal / Castle. Advance is a button in its menu.
  Feudal costs 500 food, 200 stone, 200 gold and a diamond block in Deposit (60 s). Castle costs 800 food, 400 stone,
  500 gold and a netherite ingot (90 s). Those items are not stockpile resources, so they wait in the slot. Training keeps
  running during research. On completion the same 11×11 footprint is restyled from `town_center_feudal.nbt` or
  `town_center_castle.nbt`. The Town Center block entity is not replaced. Anything that holds items (`Clearable`:
  barrels, the lectern and its book, campfires) is left alone, even where the new file has air. A player-placed
  block in the footprint (block entities like beacons, beds and banners included) is left alone and the player is told. New units come from their own buildings (Barracks, and later ones).
- **Age bonuses (phase 2, third slice):** the highest age any of a player's Town Centers reached is stored per player
  (`AgeRegistry`, never goes down) and applies to every soldier they own, old ones included. Soldiers check it
  every second, so far-away ones catch up when their chunk loads. Feudal: +2 hearts, +2 armor, Militia iron sword,
  Archers wait 12 ticks between shots instead of 20 (25% faster counting the 20-tick draw), +5 population and max 55.
  Castle: +4 hearts, +4 armor, diamond sword, 5-tick wait (60% faster), +10 population and max 60. Armor and health are
  attribute base values, not worn armor, so the skins stay visible. Numbers live in `Age`.
  **Feedback:** the Advance tooltip lists what the next age gives (and, at Castle, what you have); on completion the
  owner gets a title ("Feudal Age" / "Your army grows stronger") plus the list in chat; Handbook pages 12 and 13.
- **Houses count only while standing:** the blueprint registers each house with its owner, box and block count
  (`HouseRegistry.House`). A Town Center counts only its owner's houses whose hearth stands and that keep at least
  half of their blueprint blocks, so a broken or stripped house stops raising the limit. A hearth placed any other way
  doesn't count. Houses saved by older versions (hearth position only) count for anyone while the hearth stands.
  The Town Center's "Pop" label has a tooltip (base + standing houses + age bonus, and the house rule), and the owner
  gets a message whenever a house starts or stops counting.
- **Illustrated guide:** the Handbook opens `GuideScreen` instead of the vanilla book: 14 parchment pages, each with a
  picture or an item diagram above wrapped text, turned with arrow buttons. Pages and illustrations are defined in
  `client/Handbook`; text stays in `handbook.warbands.<page>.title/body`. Pictures (welcome, placement, houses, ages,
  War Horn orders) come from `tools/gen_guide.py`, drawn at half size and scaled 2x, with no text in them:
  captions are translated keys drawn under each panel. Item diagrams (the path, recipes, resources, units,
  sword upgrades) use real item icons. The panel is 240×236 GUI pixels, so each page fits in GUI scale 3 at 720p.
- **War Horn (phase 2, first slice):** gold ingot, goat horn, iron ingot, shaped in a column. Right-click cycles
  Follow → Hold → Charge for your soldiers within 24 blocks. Hold sets a vanilla home (radius 12) so they guard
  that block and won't pick targets outside it; rallies from other soldiers only pull them to attackers inside it.
  Charge targets nearby hostile mobs (`MobCategory.MONSTER`, never creepers), then follows you like Follow does,
  so charging soldiers can't be stranded out of horn range. Only Hold stops the teleport to you.
  Stance is saved on the soldier (`Stance`).
- **Stone (review pass 2):** stone used to have no use. Now every building placed near a Town Center pays stone from
  its stockpile: House 30, Barracks 100, Archery Range 100, Blacksmith 150, Stable 150, Watch Tower 200, and ages cost
  stone (above). The blueprint tooltip shows the cost; placement is refused with the amount if the stockpile is short.
  Numbers live in `Blueprint` and `Age`.
- **Unit balance (review pass 2):** crossbow bolts hit for about 6 (7 with Forging): vanilla multiplies an arrow's base
  damage by its speed, so the old base 5 hit for about 10. Spearmen carry vanilla spears that follow the age like the
  Militia's swords (stone / iron / diamond, 5 / 6 / 7 damage, +4 against horses and riders). The spear's reach
  component is removed, because its minimum reach stops `MeleeAttackGoal` from ever hitting. The mod's own spear item is
  gone; old spearmen are re-armed on load. Knights hit for 8 (base 3 + iron sword), the most in the army.
- **One training building (review pass 2):** Barracks, Archery Range and Stable are now one class set
  (`TrainingBlock`, `TrainingBlockEntity`, `TrainingMenu`, `TrainingScreen`, `TrainingRegistry`) configured by
  `TrainingSite` (age, GUI texture, message keys). Block, block-entity and attachment ids are unchanged, so worlds load.
  A new training building (the Siege Workshop from PR #11) is a `TrainingSite` constant plus its registrations.
- **Small UI fixes (review pass 2):** the Blacksmith marks bought upgrades in green with a check, instead of greying them out
  like unaffordable ones. Guide icon rows spread out to fit their captions ("Archer" and "Crossbowman" used to touch).
- **Skins (review pass 2):** `tools/skins.py` draws each soldier as per-face pixel maps: vanilla-style chainmail,
  a real face per unit, helmets and hoods on the hat layer, and skirts/tabards on the pants layer.
- **Siege Workshop and Battering Ram (PR #11, finished in review pass 2):** Castle Age, within 32 blocks, 150 stone.
  Recipe `S S / ILI / S S` (4 stone bricks, 2 iron ingots, any log), unlocked with the Stable. It is a `TrainingSite`
  (no new building classes). The Ram costs 100 wood and 50 gold (50 s): 40 health, 5 armor, speed 0.2, attack 3,
  knockback resistant, climbs one block. On **Charge** it breaks doors, fence gates, wooden fences and wooden trapdoors in
  its way every half second, and they drop nothing; Follow and Hold never break anything. It gets age and Blacksmith
  bonuses like any soldier. Model: `RamModel` (wheeled deck, posts, hide-covered A-frame roof, iron-capped log), texture
  from `ram_texture()` (128x128, painted per box UV). Its use is mostly multiplayer and structures with doors; in
  singleplayer it's a late-game curiosity.

- **Playtest fixes (2026-09-26):** the Town Center core block only breaks for its owner while sneaking
  (`TownCenterBlock.getDestroyProgress` is 0 otherwise, `attack` explains why, and a `PlayerBlockBreakEvents.BEFORE`
  guard covers creative's instant break and other players). Breaking it still ends the town (stockpile, age, queue),
  but no longer by accident. The guide now reads in play order (War Horn right after Your Army; each age's bonus page
  before the buildings it unlocks), icon rows are clamped to the page width (the 6-icon Town Center recipe spilled off
  both edges), and the Town Center and Units pages say how to open and use it. Every blueprint tooltip starts with what
  the building is for (gold line, `tooltip.warbands.blueprint.<name>.purpose`).
- **Barracks and Blacksmith rebuilt (2026-09-26):** the Barracks (9x9, height 11) now uses the Town Center's village
  style: stone ground floor, dark oak beam, calcite upper story with iron-bar windows, dark oak gable roof with eaves,
  a red banner over the door and lantern posts. The Blacksmith (7x7, height 11) is a dark forge: blackstone and
  deepslate walls, basalt corners, deepslate tile gable roof, a wide open front with magma coals, and a back hearth
  where an eternal fire burns on magma behind an iron grate, between a blast furnace and a furnace, under a chimney
  with a smoking campfire. Nothing within the fire's spread range is flammable. Heights in `Blueprint` match.
- **Town range 32 → 64 blocks (2026-09-26):** with seven building types plus houses, 32 blocks was too cramped.
  `TownCenterBlockEntity.HOUSE_RADIUS` (placement, house population, training/blacksmith/tower payments) is now 64,
  and every text says 64. Older notes above that say 32 predate this. Known gap, not new: a house counts for every
  Town Center of yours in range, and nothing stopped two Town Centers close together (closed the same day by
  the 128-block Town Center spacing below).
- **Stable, Siege Workshop and decor mobs (2026-09-26):** the Stable (height 10) is a red mangrove barn with a
  north-south gable, hayloft door, four 2x2 stalls, a hay stack and a water trough. The Siege Workshop (height 10) is an
  open-sided timber workshop with a 5-wide south bay, a ram frame inside, log piles, and a crane over the bay hoisting
  a log. `BuildingDecor` spawns scenery mobs right after placement: two `StableHorseEntity` (a `Horse` subclass) in the
  Stable's stalls and a `SmithEntity` at the Blacksmith's anvil. Both are MISC (no spawn-cap effect), invulnerable except
  to creative and `/kill`, not pushable, leashable, rideable, breedable or feedable, drop no loot or XP, and discard
  themselves when their building's core block is gone (`Anchor` in NBT). The smith is a `PathfinderMob`, not a
  `Villager`: `SmithRenderer` only borrows the villager model and the vanilla profession layer (plains armorer), so no
  job sites, beds, trades, gossip, golems, raids or zombie conversion. Right-clicking it says where upgrades are bought.
  Buildings placed before this have no decor mobs.
- **Knights sit in the saddle (2026-09-26):** the humanoid entity builder now uses the zombie's `ridingOffset(-0.7F)`;
  without it the knight was drawn standing at the horse's passenger attachment, floating above it.
- **Bigger Battering Ram (2026-09-26):** base `Attributes.SCALE` 1.4 (hitbox 1.96 x 1.82, still fits 2-wide gates and
  2-high openings); `readAdditionalSaveData` resets the base value so rams saved earlier grow too.
- **Game test additions (2026-09-26):** the client game test now also checks the Town Center guard (no mining
  progress unless sneaking, a plain break is refused, sneaking allows it), the 64-block range, every structure
  file's size against its `Blueprint`, the Stable horses (two, in free stalls, adult, invulnerable, not pushable,
  leashable, rideable or tameable), the smith (one, not an `AbstractVillager`, free, invulnerable), both leaving
  when their building breaks, the knight's seat height (0.74), the ram's scale and hitbox, and that `en_us` and
  `es_es` have the same keys and every blueprint has a purpose tooltip. It photographs the four rebuilt
  buildings, the horses and the smith. The "no instant teleport" check now ignores soldiers that started more
  than 20 blocks away (after a fight they can legitimately be past 24 and teleport), and prints distances.
- **Ram hitbox fix (found by the test):** `Entity`'s constructor sizes the hitbox before attributes exist, so a
  default `SCALE` only changed the drawing. `RamEntity` calls `refreshDimensions()` in its constructor; the
  hitbox is now 1.96 wide.
- **Town Center break messages (2026-09-26):** starting to mine the core says why it won't break (not sneaking) or
  whose it is (another player's); mining it while sneaking shows a red warning that the town will be lost; the
  break itself posts a red chat line that the stockpile, age and queue are gone (creative breaks instantly).
- **One Town Center per area (2026-09-26):** `BlueprintPlacer.checkServer` refuses a Town Center within
  `TownCenterBlockEntity.TOWN_SPACING` (128 = 2 × the 64-block range) of one of yours (`Problem.TOWN_TOO_CLOSE`), so no
  house can count for two towns. The blueprint tooltip and guide page 3 say so.
- **Battering Ram icon (2026-09-26):** `warbands:battering_ram`, a display-only item (no recipe, no creative tab),
  painted by `ram_icon()` in `gen_textures.py` from the ram model's palette. It replaces the oak door as the unit
  icon (`UnitType.RAM`), the advancement icon and the guide's unit icon.
- **Ram drawn bigger (2026-09-26):** `RamRenderer` scales the model a further 1.2x (1.68x in all) and its shadow; the
  hitbox stays 1.96 x 1.82, the most that still paths through 2-wide gates and 2-high doorways.

## Implementation notes (Phase 3: Enemies)

- **Raider faction:** a synced `Raider` flag on `SoldierEntity` (`makeRaider(age, raidTown)`). Raiders are untamed,
  keep the age they were raised with, can't be claimed (`mobInteract` → PASS), give no XP, and never hurt each other.
  Goals added to every soldier but gated by side: `RaiderTargetGoal` (players and tamed soldiers), `FightRaidersGoal`
  (your soldiers attack raiders on sight, any stance; Hold still limits them to their home), `MarchGoal` (walk to
  `marchTarget`, hopping 16 blocks at a time when there's no full path). Raider knights' warhorses follow the march.
  `ChargeHostilesGoal` is off for raiders. Skins: `raider_<unit>.png`, a palette swap in `tools/skins.py`
  (`raider()`: dyed cloth → near-black with a crimson cast, emblems → crimson, skin/leather/metal unchanged);
  `SoldierRenderer.State.raider` picks the texture.
- **Raids** (`raid/Raids`, `RaidRegistry`, `RaidComposition`): ticked per level (`ServerTickEvents.END_LEVEL_TICK`), checked
  once a second. Numbers as designed above: first raid ≥ 2 days after Feudal, then 4-6 days, after nightfall (or a day
  late); warning 60 s (horn at the town and the spawn, chat with direction, red boss bar for players within 96 blocks);
  spawn 48-64 blocks out on dry ground; plunder after 5 s within 4 blocks of the core (20% of each resource, then
  retreat); timeout 10 min; victory pays 15/25 gold per raider. Raiders are counted by their deaths
  (`SoldierEntity.die` → `Raids.raiderDied`), never by whether they can be found, so raiders in unloaded chunks can't
  hand out a free win. Paused while the owner is away: the 10-minute timeout counts `elapsed` attack time only
  while the owner is within 96 blocks (so it also stands still while the town is unloaded). Stale raiders (raid over) remove themselves. `warbands:raids` gamerule (Fabric `GameRuleBuilder`).
  `/warbands raid` (gamemaster) starts one on your nearest town.
- **Wrecking** (`raid/Wrecking`): a raider marching on the town with no target breaks one block within 2 blocks every
  3 s: the raided owner's building blocks (inside a registered building's box, protected, not a core, no block
  entity) or doors, gates, wooden fences and trapdoors. Building blocks drop nothing (the position is released so counts stay
  right); a player's own door or fence drops as an item, since Repair only restores buildings. With the vanilla
  `mobGriefing` gamerule off, raiders and raider rams break nothing (like zombies and doors); your own ram on
  Charge still breaks what it's told to (the Keep's gate needs it), and it also drops your blocks instead of
  deleting them.
- **Buildings and repair** (`town/BuildingRegistry`, `town/Repairs`): every blueprint placement is recorded (blueprint,
  origin, rotation, core, core block id, protected block count). `isWrecked` = fewer than half its blocks stand; training
  pauses (status "Wrecked: repair"), the Blacksmith and Watch Tower refuse with a message. Repair = the structure
  template placed through a processor that only fills air/replaceable cells (never the core, never a player's block),
  up to 3 passes so hanging blocks settle once their support is back; 1 stone per block; not during a raid. The Town
  Center counts missing blocks every 5 s for its menu. Town Center menu: Advance is narrower, with two icon buttons
  (anvil = Repair, map = War Map), each with cost, reason and rule in the tooltip.
- **Camps** (`camp/CampTier`, `CampRegistry`, `Camps`, `CampStandardBlock`, `WarMapItem`): costs, guards and plunder as
  designed. The War Map site is chosen from `ChunkGenerator.getBaseHeight` (WORLD_SURFACE_WG above sea level and equal to
  OCEAN_FLOOR_WG = dry), so nothing is loaded; the camp is built when a player is within 128 blocks, through the shared
  `BlueprintPlacer.build` (clear, foundation, place, protect), turned so its entrance faces the town. Guards are raiders
  on Hold (`SoldierEntity.guard(spot, radius)`), tower guards with a 24-block home, walled in on their posts. Claiming:
  right-click the unbreakable Camp Standard once no guard lives; plunder is paid to the selling town when it's loaded
  (`Camps.payOut`, also called by the town's tick), the standard is replaced by the tier's War Chest, a named black banner trophy is
  given, and from Feudal a revenge raid is scheduled (and announced) only when raids are on (gamerule, not Peaceful).
  **Abandoning:** shift-click the War Map button (`TownCenterMenu.ABANDON_CAMP_BUTTON`, `Camps.abandon`) gives up
  the current camp with no refund, for a quiet spell or when a camp can't be finished. An unbuilt camp is forgotten
  (it never appears); a built one is flagged `abandoned` (stays standing with its guards, can still be taken, no
  longer blocks a new map). The button stays clickable while a camp is active; its tooltip says so in yellow. Held War Map: action bar with camp, distance and direction.
  Reinforced Gate: hardness 50 (obsidian), mineable with an axe, broken by rams on Charge.
- **Structures:** `bandit_camp` (15x15, h8), `fort` (21x21, h12), `keep` (25x25, h16) in `gen_structures.py`; guard spots
  and cores in `CampTier` must match. The Siege Workshop's hanging lantern now hangs on a chain (it had no support: every
  block update dropped it, and the repair test found it).
- **Guidance:** guide pages Raiders, War Maps, Raids, Repair (after Rules; 28 pages); The Path gains the camps step;
  Still to Come keeps workers, veterans, team colors. Advancements: Hold the Line (raid defended), Rebuild (repair),
  Marching Orders (War Map), Clear the Road / Storm the Walls / Break the Gate (camps).
- **Game test:** now a checklist of scenarios (`mods/warbands/testing/checklist.md`, `scripts/game-test.sh`): each
  runs on its own site and only the ticked ones run. Raids and battles have corner-case scenarios (unfound raiders,
  owner away, timeout, off switches, destroyed town, stale raider, wrecking and rams with mobGriefing, repair during
  a raid, friendly fire, targeting, Hold discipline, a full army-vs-raid fight, camp abandon, revenge off).
  Before the split: +63 checks (197, ~1m 40s): faction, a won raid (size, spawn distance, ram on Charge, payout, growth),
  plunder at the core, gamerule off ends a raid, wrecking a house, the half-wrecked Barracks and a full repair, the
  Bandit Camp's guards, a Keep bought, built on arrival (16 guards, 12 gate blocks), claimed only after its guards
  fall, trophy, advancement, revenge scheduled, plunder paid. Screenshots of raiders, a raid and the three camps.
- **Balance to watch in play:** camps pay more than the map costs (by design), and each Feudal/Castle conquest brings a
  revenge raid that also pays gold if won; the real costs are travel and permanent soldier deaths.


## Implementation notes (playtest round, 27 Sep 2026)

About 45 notes from an hour of play (Village to Castle, a Fort taken, raids), fixed on branch
`warbands-playtest-sep27` in six commits:

- **Bugs:** soldiers never advanced their swing (`updateSwingTime` only runs in `Monster`/`Player`), so Militia and
  Knights didn't animate; now `SoldierEntity.aiStep` calls it. Watch Tower filled 2 of 4 spots (a post counted any
  soldier within `distSqr <= 2`, which covers the diagonal neighbours): now exact cell. Camp tower rims were walls at the
  archer's feet, blocking every downward line of sight: now bottom slabs, and tower guards are *posted* (stand and shoot,
  climb back). `DefendPostGoal` targets raiders (your side) or players (raider side), only within `attackRadius()`.
  Soldier arrows vanish 3 s after sticking (`SoldierArrowMixin`, tag `warbands_arrow`). Rams on Attack path through
  wooden doors (`setCanOpenDoors`) and break what they touch. Barracks banners faced the walls.
- **War Map** is a vanilla filled map (`MapItem.create`, biome preview, `MapItemSavedData.addTargetDecoration`: red X on
  the camp, blue banner on the town), the smallest scale 2-4 whose snapped grid shows both; tagged with custom data
  `warbands_camp`. Held: action bar "Raider Fort · 58 blocks ↗", the arrow relative to where the player looks
  (`Directions`). The old `war_map` item stays for old saves and as the button icon.
- **Camps:** new sites keep 160 blocks from every camp and other Town Center (then try 400-500). Ground guards chase 20
  blocks. Last guard down: title, horn, a beam over the standard. Claim: title, the tier's trophy, maps for that camp
  crumble; the camp burns for 30 s (particles), then its structure blocks are cleared (`StructureBlocks`, coarse dirt
  and gravel on the floor). `Camp` gained `cleared`, `razeAt`, `razed` (camps taken before this stay standing).
- **Trophies** (`bandit_trophy`, `fort_trophy`, `keep_trophy`) replace the diamond block and netherite ingot as the age
  requirements. Only a claimed standard gives one, and camps are one at a time, so they can't be farmed.
- **Raids:** raiders loot only when nobody of yours is within 12 blocks of the core; victory counts even if you chased
  the last one away (title, fanfare at the player, fireworks). Warning title with an arrow. Raider skins get a red
  headband and a white chest skull (`skins.py raider_marks`), the raider ram a black roof. Town Center shows "Raid: in N
  days" / "Raid: tonight" (`DATA_NEXT_RAID`) with the rule on hover. Raids still count `gameTime` (so `/time set` does
  nothing) and need the owner near town: that's now explained in the texts instead of changed.
- **War Horn** orders the whole army (`ArmyOrders` attachment); soldiers take up the army order every second, so far
  ones and new recruits follow it; posted tower soldiers are left alone. Charge is shown as Attack (`charge` in saves).
  Goat-horn pose, particle ring, marks over soldiers, a new icon, the recipe in the tooltip.
- **Army control:** soldiers and warhorses never push their commander, they step aside (`push`/`doPush` overrides);
  horses follow from 8 blocks, stop at 5, aiming behind the player; idle soldiers don't stroll into buildings.
- **Balance:** Crossbowman 16 health (was 20), one shot every 2.5 s (was 1.5 s).
- **Buildings:** Town Center stairs up the east side to an upper storage room (empty chests; age upgrades never overwrite
  containers). Watch Tower: ladder to a trapdoor on the roof, arrow slits, lantern posts. Keep: 3-wide lit doorway.
- **Blacksmith:** research takes 40 s (Castle 60 s), one at a time per Blacksmith, one Blacksmith per town
  (`Problem.ONE_PER_TOWN`); a duplicate finished elsewhere is refunded. Castle upgrades: Blast Forging, Bodkin Arrows,
  Chain Mail (each needs its Feudal one), Husbandry (horses +15% speed, knights +4 health).
- **Handbook:** pages stack illustrations; `Faces` (skin face + hat layer), `Grid` (3x3 crafting grid), `Stats`
  (heart/armor sprites). No spawn eggs. A "Buildings" page carries the shared rules; the rest got shorter.
- **Game test:** new `raids.defended`, `battles.horn-orders`; the camp test checks map decorations, the trophy and the
  burn-down; the buildings test checks the research timer, one smith per town and a 4/4 tower. Army orders reset
  between scenarios.

### Implementation notes (playtest round five, 2026-09-27)

- **Camp burn-down** (`Camps.tickBurning`, every 5 ticks, 50 s): a transient `Burn` orders every structure block of the
  camp roughly top to bottom with per-block noise (roofs and towers fall first), so it collapses block by block with
  debris (`LevelEvent.PARTICLES_DESTROY_BLOCK`), flames and smoke along the fire front, tall `CAMPFIRE_SIGNAL_SMOKE`
  columns sent with `alwaysShow` (seen from far), ash, crackling and crashes. Blocks go with `UPDATE_CLIENTS |
  UPDATE_SUPPRESS_DROPS` so banners and lanterns don't pop off as loot. The floor and the cobblestone foundation turn to
  plain **dirt** (no more gravel and coarse dirt); grass grows back over it. After a restart the order is rebuilt from
  what still stands.
- **War Map after conquest** no longer crumbles: it gets `warbands_home` (the Town Center), is renamed "War Map: X
  (taken)", keeps the red X on the burnt camp, and held it shows "Home: Town Center · N blocks ↗" in the action bar
  (live arrow, updated every 10 ticks). `mapTarget` ignores taken maps.
- **Camp guards sally out** (`Camps.rouse`, while the owner is within 64 blocks): once a guard is hurt or has a target,
  every fence gate in the camp is opened (and reopened if shut), a horn sounds, and ground guards widen their home to
  48 blocks and go for the attacker. Closes the "open one gate a crack and shoot through it" exploit.
- **Fort standard** stands in the open on a polished andesite square between two lantern posts, at the end of a red
  carpet from the gate (the dais and awning hid it). Core unchanged, so forts already built still work.
- **Recruits muster** at the building that trained them (`SoldierEntity.muster`, saved as `MusterSpot`, home radius 5):
  no following or teleporting to the commander until they come within 16 blocks or blow the horn. Before, Follow's
  24-block teleport made them appear next to the player.
- **War Horn rally:** sounding Follow brings loaded soldiers more than 32 blocks away to the player
  (`tryToTeleportToOwner`), unless the player was hurt in the last 10 s or a raider is within 24 blocks, and at most
  every 2 minutes (in memory). Soldiers in unloaded chunks still only take the order.
- **Raid warning:** no arrows in chat or the title (they can't follow where you look); compass words, and a column of
  signal smoke with flames at the spawn point during the warning.
- **Town Center interior:** 2-wide stairs up the east wall (x 7-8), no hanging lanterns over them, crafting table
  moved to the west wall, the two standing banners flank the core from behind (one used to stick through the stairs),
  the corner chest upstairs is reachable, and a bed along the south wall upstairs. Age upgrades may now replace the
  building's own **empty** containers when the new layout puts something else there (`BlueprintPlacer.UpgradeProcessor`).

### Implementation notes (playtest round six, 2026-09-27)

- **Raider deaths** (`Raids.raiderDied`, now passed the entity): soul and smoke puff where it fell; the commander gets a
  bell whose pitch rises as the warband thins, and "Raider down · N left" in the action bar; the boss bar updates at once.
- **Repair:** a spot already holding the template's block is whole (`Repairs.RepairProcessor`). The Blacksmith's
  eternal `fire` is "replaceable", so it used to count as missing forever and every click charged 1 stone. The button
  now queues the missing blocks (`Repairs.collectAround`, states rotated, bottom-up) and `TownCenterBlockEntity.tickRepair`
  places 3 per tick with the block's place sound and a sparkle, paying stone per block. Blocks that can't stand yet
  retry up to 3 times. It stops if a raid starts or stone runs out. The job isn't saved. `Repairs.repair` (instant)
  stays for code that needs it at once.
- **War Horn:** Shift+right-click goes back one order (`Stance.previous`); cooldown 5 ticks, use 8 ticks. While held
  (and 3 s after an order), the action bar shows `Follow · [Hold] · Attack` and how to go back. Order messages carry
  a one-line meaning (`stance.warbands.<order>.what`).
- **Follow vs Attack:** Follow is an escort: `FightRaidersGoal` only takes raiders within 12 blocks of the commander
  and drops a chase past 20; vanilla owner goals still answer attacks. Attack hunts: raiders before monsters
  (`FightRaidersGoal` and `RaiderTargetGoal` at priority 4, `ChargeHostilesGoal` at 5) and +15% speed
  (`warbands:attack_order_speed`, transient modifier).
- **Raiders stay on the raid:** their `HurtByTargetGoal` only answers players and your soldiers (`fightsBack`), so
  mobs don't pull them away. **Raider rams** pick no targets and ignore hits (`RamEntity.fightsBack` false,
  `RaiderTargetGoal` skips rams), and march at 1.3x (`MarchGoal.RAM_SPEED`) straight for the Town Center.
- **Warhorse:** the 24-block catch-up teleport only goes where horse and rider fit (`fitNear`: behind the owner, then
  rings of 2-4 blocks, solid ground, `noCollision` up to the rider's head). If the rider or horse ends up in a wall, it
  backs out to the nearest fitting spot and waits 5 s before following (a 2-high tunnel).
- **Barracks:** one tall hall open to the rafters (no plank ceiling at the eaves), a wall-plate beam, low windows,
  and lanterns on chains from the roof. Existing Barracks keep the old ceiling (Repair only fills gaps).

## Implementation notes (Phase 4A: War Chests and Raid Spoils)

- **Spoils** (`camp/Spoils`): `chest` places a named vanilla chest whose loot rolls from a warbands table on first
  open (`ChestBlockEntity.setLootTable(key, seed)` + `setChanged`; the name goes through `applyComponents` with a
  `CUSTOM_NAME` patch — there's no setter). `site` finds a surface column (MOTION_BLOCKING_NO_LEAVES) in a ring,
  replaceable over a sturdy floor, with room beside it for the signal campfire (lit, `SIGNAL_FIRE` set directly: it
  only recalculates when the block below changes).
- **Camp War Chest:** `Camps.claim` releases the standard position from `StructureBlocks` (so the burn skips it and
  the chest drops like an ordinary chest) and places the chest facing the entrance (`rotation().rotate(SOUTH)`).
  Table: `CampTier.lootTable()` → `warbands:chests/camp/<tier>`.
- **Raid Spoils:** `Raids.victory` leaves the chest at `active.spawn()` (ring 0-4), falling back to a ring 3-6
  around the Town Center; the victory chat says the compass direction and distance. Table: `Spoils.raidLoot(age)` →
  `warbands:chests/raid/feudal|castle`.
- Tables in `data/warbands/loot_table/chests/{camp,raid}/*.json`, vanilla chest format: a main pool plus a 1-roll
  rare pool with a weighted `minecraft:empty` entry.

## Implementation notes (Phase 4B: raid kinds)

- **`raid/RaidKind`**: WARBAND, NIGHT_STALKERS, SHIELD_WALL, SIEGE, CAVALRY — each carries its display name, a
  flavour (Shield Wall marches slower and tougher; Night Stalkers only strike in full dark, so a town can sit on a
  drawn kind until deep night), the age floors it can appear at, and `lootTable()` → `warbands:chests/raid/<name>`.
- **The bag** lives on `RaidRegistry.TownRaids` as `bag` + `last`, both optional codec fields — pre-4B saves load
  with an empty bag that fills on the next draw. `Raids` refills by shuffling one of each kind the age allows;
  across a refill, if the shuffle puts `last` first it's swapped deeper into the bag, so a kind never repeats
  back-to-back.
- **Composition** is `RaidComposition`'s per-kind table (Feudal and Castle lists, base + extra waves); the warning
  message and boss bar name the kind (`raid_warning` gained the kind argument).
- Victory uses the drawn kind's table for the war chest (`chests/raid/{warband,night_stalkers,shield_wall,siege,
  cavalry}`), and `/warbands raid <kind>` forces a kind for testing while still consuming it from the bag.

## Implementation notes (Phase 4C: camp kinds)

- **`CampTier` is now kinds, not ages**: 11 constants (3 Village, 4 Feudal, 4 Castle), each `NAME(size, height, core,
  age, advancement, home, sink, guards)`. The plain three (bandit_camp, fort, keep) keep their names, so old saved
  camps still load. `forAge` returns the plain kind (the safe fallback); `kinds(age)` lists all kinds of an age.
- **Per-player bag** (`CampRegistry.Bag`): `CampRegistry.Data` gained `bags` (map of owner → `{age, bag, last}`);
  the codec is `Codec.either(oldListCodec, newRecordCodec)` so pre-4C saves (a bare camp list) load with empty bags.
  `Camps.drawKind` refills `kinds(age)` shuffled, swaps the first entry when it repeats `last` (never twice in a row,
  refill included), and writes the bag back each draw.
- **Biome site search:** `Home` (a `TagKey<Biome>`/`ResourceKey<Biome>` list) per kind; `findSite(town, tier)` tries
  48 angle/ring candidates against `BiomeSource.getNoiseBiome` (quart coords, `randomState().sampler()`) without
  loading chunks, then falls back to the age's plain kind on any dry site. Forced buys (command, tests) skip both.
- **Vanilla-mob guards:** `CampTier.Guard(EntityType<? extends Mob>, spot, onTower)` covers soldiers (`makeRaider` +
  `guard`) and vanilla mobs alike (`setPersistenceRequired`, `setHomeTo`, tag `warbands_camp_guard`). Drops and XP
  are cancelled in `CampGuardMixin` (`LivingEntity.dropAllDeathLoot` / `getBaseExperienceReward` for tagged mobs);
  counting stays by UUID in `Camps.guardsLeft`.
- **Structures** (`tools/gen_structures.py`): eight builders — palisaded lodge with hide racks; a dock on piles with
  barrels and a treasure map in the chest; a stone quarry fort with two towers and a chain hoist; a stilted mud hut
  in a mangrove ring; a packed-ice platform in snow walls; a fenced encampment with paddocks; a vault sunk 4 deep
  (`sink` subtracts from the build height) entered down a stair trench; a bannered illager hall behind a palisade.
- **`/warbands camp <kind>`** gives a free War Map to a specific kind (no stock spent, still one camp at a time).
- Gotcha worth remembering: `level.getEntity(uuid)` only finds entities in entity-ticking chunks, and
  `setChunkForced` tickets apply a tick later — the game test checks guard counts a few ticks after `Camps.build`.

## Implementation notes (Phase 4D: traps)

- **Three new blocks** (`registry/ModBlocks`): `alarm_plate` (a brass plate; an `entityInside` with an empty
  collision shape, like a pressure plate), `caltrops` (slows + 1 damage per tick to anything walking through while
  the camp stands; `PathType.DAMAGING` via `LandPathTypeRegistry` so mobs and soldiers path around), `powder_keg`
  (detonates on a neighbor signal: `ExplosionInteraction.NONE` — hurts, breaks no blocks, chains to adjacent kegs).
- **Wiring lives in `camp/Traps.java`**: `ring` (bell + `Camps.alarm`, 60-tick cooldown per camp), a
  `UseBlockCallback` that fails dispenser opens inside an unclaimed camp ("The camp's dispensers are sealed shut"),
  and `campOwn` — the camp's own defenders (vanilla `warbands_camp_guard` tag + raider soldiers) are exempt.
- `Camps.alarm(level, camp, intruder)` is the public sally: opens gates, widens home radius, sets the target.
  `Camps.campAt`/`armedAt` locate and arm-check traps; "armed" = built, not conquered, not abandoned, not razed.
- **`CampTriggerMixin` on `Entity.isIgnoringBlockTriggers`** makes a camp's own defenders ignore tripwires and
  pressure plates while inside a camp — without it the guards blew their own kegs pacing the gate lane.
- Arrows fired by camp dispensers need no code: ownerless arrows are `pickup = DISALLOWED` in 26.1.
- Structures (`tools/gen_structures.py`): every camp got `alarm()` just inside the gate; Feudal+ camps get
  `spikes()` on the gate lane; arrow slits are `_tripwire` + a stocked dispenser (`nbt` block data, 9 arrows);
  pitfalls are an open trapdoor over powder snow or a berry bush; Castle camps wire `keg_wire`/`keg` to a tripwire.
  `render_structures.py` learnt the new block materials; previews verified.
- Game test `camps.traps` (tickable): plate exists and sallies guards onto an intruder, dispensers refuse,
  caltrops hurt, a powered keg removes itself without breaking a control block, claiming disarms everything.
- Not verified in-game: the actual arrow flight (dispenser fires when a non-guard crosses the wire) and pitfall
  falls — both are pure vanilla wiring/geometry.


## Implementation notes (Phase 4E: Warlords)

- **`entity/WarlordEntity`** (a `SoldierEntity`, always a raider): registered as `ModEntities.WARLORD`, slightly
  bigger than a soldier, one per Castle camp via a `warlord(x, z)` entry in the kind's guard list — counting him as
  a guard means the standard can't be claimed while he stands, for free. `Camps.build` promotes the Keep's to the
  boss (`setBoss`) and gives every warlord a fixed name per kind (Warlord Vorak, Khaness Arha, Grave-King Mos,
  Chief Brakk).
- **Mini-bosses** (cavalry/crypt/hall): ~3x soldier health (84 + the Castle raider bonus), 9 armor, one move — the
  charge (8 s cooldown, leaps 5-16 blocks at its target). **The Keep's boss:** 150 health, 14 armor, 7 attack,
  three health-gated phases — phase 2 (under 2/3) speeds him up and starts war cries (Strength + Speed to camp
  defenders in 24 blocks, raid horn), phase 3 (under 1/3) adds the ground slam (leap, then 10 damage + knockback in
  a 4.5-block ring on landing). Arrows sting him at 60%.
- **Boss bar:** an entity-owned `ServerBossEvent` (red) shows his name to players within 64 blocks, updated in
  `aiStep`, cleared in `onRemoval`. Saved via the `Boss` NBT flag (name rides the vanilla CustomName).
- **The named weapon:** `dropCustomDeathLoot` spawns the Warlord's Brand once — a netherite sword carrying
  `ITEM_MODEL=warbands:warlord_blade` (custom texture), `CUSTOM_NAME`, `RARITY.EPIC`, Sharpness V / Unbreaking III /
  Fire Aspect II. Equipment drop chances stay 0 so his held sword never dupes it. Killing him awards
  `warlord_slain` (advancement + challenge frame, parented under camp_keep).
- Camp-own exemption (`Traps.campOwn`) already covers him: raider soldier → he never trips his own wires or
  caltrops. Not verified in-game: whether the boss's phases *feel* right — the moves are implemented and cooldowns
  tick, but only the gametest asserts stats/gating/drop (screenshot shows both bars rendering).

## Implementation notes (Phase 4F: relics and rares)

- **`town/Relics`**: a `warbands:relics` level attachment (`Data{enshrined: List<TownRelics{town,kinds}>,
  earned: Map<UUID,kinds>}`, both optional — old saves load empty). `Camps.claim` calls `grant`: the owner's first
  conquest of each fantasy kind hands its relic item (Hag's Charm / Frost Sigil / Grave Lantern / Omen Banner);
  the `earned` map stops repeats per player, so a second Witch Coven gives none.
- **Relic Pedestal** (`town/RelicPedestalBlock`, stateless): `useItemOn` resolves the player's nearest owned town
  within 64 blocks and `enshrine`s the relic under the Town Center's position; a duplicate kind answers with the
  red "already rests here" overlay, so the same relic never stacks. Craft: polished blackstone + gold + an emerald;
  the pedestal is an ordinary drop (not anti-loot — it's the player's furniture).
- **Effects** tick every 4 s per town (`EFFECT_TICKS`, the "once a second" rule relaxed since nothing here needs
  faster): Hag's Charm → Regeneration on the owner's soldiers within 48 of the Town Center; Grave Lantern →
  Weakness + Slowness on monsters and raiders (and Weakness on raider-side soldiers); Omen Banner → Speed on the
  owner's horses (matched by `AbstractHorse.getOwnerReference`). Frost Sigil isn't a tick: `Raids.warnTicks` grows
  the warning from 60 s to 90 s when the town has it — "raids call out sooner" as more warning, not more raids.
- All four enshrined in one town awards `relic_collection` (challenge frame, parented under `warlord_slain`).
- **Warlord's Plate**: the four Castle War Chest tables (keep/cavalry/crypt/hall) gained a rare-pool entry per
  piece — vanilla netherite armor renamed (`Warlord's Helm/Cuirass/Greaves/Sabatons`), `ITEM_MODEL=warbands:
  warlord_<piece>` + `RARITY.EPIC`, Protection IV + Unbreaking III (+ Feather Falling IV on the sabatons): same
  trick as the Warlord's Brand, so no armor-equip code was needed.
- **Warhorse Whistle** (`item/ArmoredWarhorseItem`, rare pool of `cavalry_encampment`): `useOn` spawns a
  `WarhorseEntity` tamed to the caller, wearing diamond horse armor with drop chance 0 (no dupe). Gametest
  `camps.relics` covers grant-once, pedestal enshrine, no-stack and the Grave Lantern's sap.

## Implementation notes (Phase 4 review polish, 2026-09-28)

- **Raid bag survives a raid:** `Raids.finish` rebuilt `TownRaids` with the 6-arg constructor, wiping `bag` and
  `last` after every raid, so each raid drew from a fresh bag and the same kind could come twice in a row. It now
  carries both. `raids.bag` no longer injects `last` by hand and checks the finished raid remembers its kind.
- **Night Stalkers can't be slept away:** full dark (`skyDarken` 11, verified: `15 - SKY_LIGHT_LEVEL`, 4 at
  midnight) is only reached after the bed is usable, so sleeping every night held them (and every raid behind them
  in the bag) off forever. Every kind now comes a day late regardless, like the plain raids always did.
- **Warlord reloads keep his wounds:** `readAdditionalSaveData` called `setBoss(true)`, which heals to full; a relog
  or chunk unload mid-fight reset the Keep boss to 150 HP. Load now sets only the flag (attributes and health are
  saved with him).
- **Undead camp guards don't burn at dawn:** strays (Frozen Outpost), skeletons and zombies (Sunken Crypt) are in
  `#burn_in_daylight`; in the open they burned at sunrise and the camp cleared itself. Guards of that tag get an
  infinite hidden Fire Resistance. `camps.kinds` checks it.
- **Trap arrows really can't be picked up** (the handbook said so; vanilla `ArrowItem` sets `Pickup.ALLOWED` on
  dispensed arrows): `Traps` sets ownerless arrows spawning inside an armed camp to `DISALLOWED` on
  `ServerEntityEvents.ENTITY_LOAD`.
- **Relic Pedestal text:** the messages used `%d` (Minecraft translations only take `%s`: the line failed to
  format), named the camp instead of the relic, and the effect line overwrote the count on the action bar. Both now
  go to chat with the relic's name. Relic tooltips show their effect.
- **Rare pools rescaled to the spec's "a few % jackpot":** the eight new camp tables had a rare pool with almost no
  `empty` weight (Illager Hall 67% rare, 44% chance of a Warlord's Plate piece per camp). Each now matches its age's
  plain camp: Village 12%, Feudal 15%, Castle 18% (plate piece ~2-3% per Castle chest, whistle ~4.5% per Cavalry
  Encampment). Contents unchanged.
- **Round two (deeper testing):**
  - **Camps save was lost after the first War Map** (worst bug): the 4C bag map was keyed with `UUIDUtil.CODEC`
    (an int array) and NBT map keys must be strings, so the whole `warbands:camps` attachment failed to encode
    ("Not a string") and every camp was gone on the next load. Now `UUIDUtil.STRING_CODEC`. New `Run.roundTrip`
    encodes/decodes an attachment like a save; `camps.bag`, `camps.relics` and `raids.bag` use it.
  - **World crash:** a Village town with a raid entry (it took a Feudal camp, e.g. via `/warbands camp`) built
    its kind bag from `RaidKind.forAge(VILLAGE)`, which is empty, then `get(0)`: "Exception ticking world". The tick
    now uses the same Feudal clamp as `start()`, and `forAge` is never empty. Scenario `raids.village-revenge`.
  - **Arrow slits missed:** the dispenser sat beside the hook facing along an empty column. It is now the wire's
    west post, facing east, so the arrow crosses the tripped cells (`gen_structures.arrow_slit`; fort, quarry
    stronghold, keep, illager hall regenerated). `camps.traps` now trips a wire with a cow and checks the hit.
  - **Warhorse Whistle did nothing:** it spawned a `WarhorseEntity`, a knight's mount that discards itself after 10
    ticks without a knight and refuses riders. It now spawns a vanilla horse: tamed, saddled, diamond barding
    (both guaranteed drops), 30 HP, speed 0.3375, jump 1.0. `camps.relics` uses the whistle and rides the horse.
  - **Keep boss slam had no wind-up:** the landing check ran on the take-off tick (still on the ground), so the
    10-damage slam hit instantly. It now needs 5 airborne ticks, with a roar on take-off.
  - **Camp infighting:** raider arrows could hurt the vanilla guards (and vice versa), turning witches, strays and
    illagers on the raiders. `isFriendlyFire` puts `warbands_camp_guard`-tagged mobs on the raider side.
  - **Guidance:** the Relic Pedestal recipe unlocks when a relic enters the inventory
    (`advancement/recipes/relic_pedestal.json`); the War Map button names the age and lists its camp kinds instead
    of promising the plain kind.

## Implementation notes (tester shortcut, 2026-09-28)

- Felipe, after a playtest: "remove the banners inside the TH they look bad bc there is no space" and "i'm tired of
  having to add all the materials by hand and build everything".
- **Town Center:** the two standing banners flanking the core indoors are gone from all three ages (the wall banners
  outside stay). Existing towns keep theirs until broken (they drop nothing).
- **`/warbands playtest`** (op, `Playtest`): places a Town Center 16 blocks ahead of the player (normal placement
  checks), fills the stockpile, pays and runs both age researches at once (`tryAdvance` + `serverTick` for the
  research length, like the game test's `advance`), places the eight buildings in a fixed layout facing the player
  (skipped, and counted in chat, when blocked), spawns two of every unit (one ram) through `assignOwner`, and gives a
  kit. `playtest kit` refills it; `playtest help` reprints the **clickable chat menu** (`ClickEvent.RunCommand`): each
  raid kind, `raid skip` (new: ends the warning), each camp kind, `camp tp` (new: teleports outside the active camp),
  `camp abandon` (new), day and midnight. Walkthrough in `mods/warbands/testing/playtest.md`. Scenario
  `core.playtest` builds it, checks age, stock, 9 buildings, army, kit, and the camp teleport.

## Implementation notes (playtest round seven, 2026-09-28)

- **Camp gates open:** a camp is cut into the terrain at one point, so on a hillside its gate could face a wall of
  stone. `Camps.clearApproach` (not for the sunk crypt) clears natural, clearable terrain above the floor in a
  3-block ring round the walls and a 5-wide, 10-long lane out of the gate, and gives the lane a coarse-dirt floor.
  Never a player's block (`BlueprintPlacer.isClearable`). Generator fixes: the Poacher's Lodge gate had a lintel at
  head height (now at y3), the Cavalry Encampment's lintel is gone, and the Sunken Crypt's outer ring is solid earth
  up to the ground so the moat no longer walls it off. A BFS over every camp NBT (scratchpad script) confirms each
  one reaches its standard from outside with the gates open; the Keep still needs the ram by design.
- **Burnt crypt:** a sunk camp fills its footprint back with dirt (coarse on top) when it finishes burning.
- **Ram smaller:** base `Attributes.SCALE` 1.1 (was 1.4), and `RamRenderer` no longer draws it a further 1.2x
  (1.68x in all was too big). Hitbox 1.54 x 1.43.
- **Relic Pedestal:** a real pedestal model (plinth, column, capital) with four boolean block state properties
  (`hag`, `frost`, `grave`, `omen`) drawn by a multipart blockstate as glowing gems (`light_emission` 15) on the
  capital's corners. Set when a relic is enshrined, when the pedestal is right-clicked and when it's placed in a
  town that already holds relics. Light 0/6/9/12/15 by count. `animateTick`: witch, snowflake, soul-fire and
  raid-omen particles over each gem, an end-rod column with all four; a totem burst when a relic goes in. The item
  shows the empty pedestal (it showed the gems, which read as "comes with the relics"; fixed in round eight). Item name fixed (`useBlockDescriptionPrefix`; it showed `item.warbands.relic_pedestal`).
- **Icons redrawn** with outlines, shading and the full 16x16: the four relics, the whistle, the Warlord's Brand,
  the pedestal's stone.
- **Handbook:** the Warlords page overflowed into the arrows; split into "Warlords" and "Warlord Spoils". The `lang`
  scenario now checks that every page fits, in English and Spanish (`GuideScreen.fits`).

## Implementation notes (playtest round eight, 2026-09-28)

- **Void teleports fixed (chunk loading):** a heightmap read on an unloaded chunk answers "the bottom of the world".
  `camp tp` read the camp's column before its chunk loaded and dropped the player below bedrock. New `Ground` helper:
  `surface` loads the chunk first and skips logs, leaves, plants and snow; `surfaceIfLoaded` never loads and says so;
  `footprint` is the median ground over a 5x5 sample of a building's footprint; `ready` is true only when every chunk
  in a square is loaded with its entities and ticking.
- **Camps build on real, ready ground:** `Camps.build` waits for `Ground.ready` over footprint + approach lane (was:
  nine points `isLoaded`, which is true for border chunks where entities don't tick, so guards could be dropped or
  frozen and the approach clearing loaded chunks synchronously) and floors the camp at the footprint median (was: the
  center column's MOTION_BLOCKING height, which counts tree trunks, so a camp could sit on a treetop).
- **Raids:** spawn search skips unloaded columns; each raider whose offset column is unloaded or a >4-block drop
  spawns on the checked spot instead of at the world's bottom.
- **Test town:** buildings floor at their footprint median (trees no longer lift or skip them).
- **Warhorse Whistle:** two flute notes and note particles from the player; the horse arrives in a ring of dust and a
  cloud with a neigh, gallop and barding clank, green sparks and hearts, and 30 s of Speed II + 10 s Regeneration.
- **Tester's Guide** (`playtest_guide`, not in a creative tab): `/warbands playtest` and `playtest kit` give one;
  right-click opens `PlaytestScreen`, a panel of buttons with tooltips (travel, raids, camps by age, you and your
  town, world) that send commands (`ClientPacketListener.sendCommand`), so cheats still gate them. New commands:
  `/warbands home` (in front of the door of your nearest Town Center in the dimension, any distance),
  `playtest stock`, `playtest heal`. Gamemode, weather and time are vanilla commands. The chat menu stays.

## Implementation notes (camps round: layouts, contraptions, threat, 2026-09-28)

Playtest request: "all camps are exactly the same size… make things cooler, bigger and more minecrafty, using
technical-minecraft dynamics… different attacking and approach strategies… mini bosses all look too similar… do we
scale with the difficulty?". Shipped in two rounds; this is **Round A** (camps). Round B (bosses) is planned below.

- **Burn and wreck sounds match the material:** the burn's crash plays the break sound of the most common
  `SoundType` that fell that step (was always `WOOD_BREAK`); raiders' blows use the wooden-door bang on wood, the iron
  door clang on metal, else the block's own hit sound.
- **Layouts:** every kind has 2 (Village) or 3 (Feudal, Castle) hand-made layouts, 30 in all, from 15 to 45 wide.
  A War Map picks one at random; its id is saved on the camp (`layout`, old saves → the first, original layout).
  `CampLayout` (size, height, core, sink, guards, spares) is **generated** with the structures by
  `tools/gen_camps.py` into `CampLayouts.java`, together with each layout's scouts' report
  (`camp_layout.warbands.<id>`, EN/ES) shown on the War Map. The generator checks every guard spot (air at feet and
  head, solid below) and that no alarm horn touches a relay. Layouts are in `tools/camps_village.py`,
  `camps_feudal.py`, `camps_castle.py`:

| Kind | Layouts (width) | Main idea |
|---|---|---|
| Bandit camp | original 15, stockade 19 | round palisade, berry pits, rear sculk |
| Poachers | original 15, treehouse 21 | archers on tree platforms, rope bridges with rigged planks |
| Smugglers | original 15, warehouse 23 | alley maze with sculk, two guns in the counting house |
| Fort | original 21, barbican 27, twin wards 31 | kill court + portcullis + breach; cross wall + postern |
| Quarry | original 21, terraces 29, mine head 25 | switchback terraces with wall guns; headframe + powder magazine |
| Witch coven | original 17, stilts 25, grove 23 | stilt huts + dropping walkways + Slowness guns; potion throwers in standing stones |
| Frozen outpost | original 17, star fort 27, glacier 23 | ice bastions + dome; cavern behind a portcullis, diggable ice |
| Keep | original 25, moat 39, concentric 45 | island + causeway + postern; trap corridor between two walls |
| Cavalry | original 23, ring 33, paddocks 35 | horse arena; gun-lined run between knight paddocks |
| Sunken crypt | original 19, catacomb 31 (sunk 4), flooded 27 (sunk 3) | underground maze with sculk rooms; islands with a rigged plank |
| Illager hall | original 21, watchtower 35, longhouse 41 | tower + rope bridge; manor rooms with a throne portcullis |

- **Contraptions (vanilla redstone):** new blocks, all protected, no loot, no items:
  - `war_relay` (the camp's brain) gives redstone 15 while `Camps.alarmedAt` (an alarm, a horn, or guards spotting /
    being hurt by an enemy in the last 60 s, guards still alive). `pulse=true` relays beat on and off every 10 ticks,
    for guns. Smash one and what touches it goes dead: the sabotage route.
  - `alarm_horn`: a redstone signal (a sculk sensor on top of it) rings the camp alarm against the nearest player
    within 12 who **isn't sneaking** (a sensor can't say who it heard; a guard's steps next to a sneaking player stay
    quiet). Sneak or wool.
  - `portcullis`: iron grating (hardness 25, a Ram breaks it) that sticky pistons slide across a 2-wide gate. The one
    camp block (with `rigged_floor`) pistons may move (`Camps.movable`, exempt in `StructurePistonMixin`); the burn
    sweeps both wherever they ended up.
  - `rigged_floor`: a plank held out by an extended sticky piston powered by a torch hanging on a relay; the alarm
    darkens the torch and the piston pulls the plank away.
  - Guns: a dispenser on its own pulse relay (64 arrows; tipped Slowness in the stilts; 9 splash potions in the grove).
- **Portcullises guard inner gates** (barbican, twin wards' cross wall, keep, tomb, throne room, glacier cavern), never
  the outer gate: the outer fence gates still open for the sally, so guards can reach you.
- **Anti-exploit:** camp dispensers stay sealed while the camp exists (burning included, was: until claimed); a player
  breaking one gets no ammo (emptied in a `PlayerBlockBreakEvents.BEFORE` listener registered before the
  structure-block one); the burn removes blocks with `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS`, so leftover ammo doesn't
  spill.
- **Threat** (`camp/Threat.java`), worked out when the camp is manned and saved on it: difficulty (Easy/Peaceful
  0.75× health, 0.8× damage, −1 guard per 4; Hard 1.25×, 1.15×, +25% guards), party (each extra player within 64:
  +1 guard up to 4, +35% warlord health), streak (+1 guard per 2 camps of that age taken, up to 3). Multipliers are
  permanent attribute modifiers; extra guards use the layout's spare spots (originals double up on ground spots),
  Easy removes non-tower, non-warlord guards from the end of the list. Stars 1-5: difficulty 1-3, +1 party, +1 streak ≥ 2.
  The War Map shows an estimate at purchase; the camp announces the real threat and garrison when it's manned.
- **Raids** scale too: `Raids.sizeFor` = roster + Easy −2 / Hard +2 + 1 per extra player at home (up to 4), never
  below the kind's base roster (a first Castle raid on Easy still brings its rams: the first game-test run caught Easy
  trimming them); raiders also get the difficulty's health and damage (`Threat.raid`).
- **Handbook:** new pages "Camp Contraptions" and "Threat".
- Fixed in passing: the original Illager Hall's keg overwrote a caltrop at (11, 1, 16) (caltrop moved to (12, 1, 16)).
- **Not verified in-game by eye:** every layout on real terrain (game tests run on superflat), how the guns feel, how
  well guards path round the bigger layouts, the treehouse archers staying on their platforms.

### Round B plan: distinct mini-bosses (decided autonomously; to build after the Round A playtest)

- A `WarlordKind` enum, saved on the warlord: stats, scale (the `SCALE` attribute plus `refreshDimensions`), skin,
  weapons, abilities and a stated counter. The renderer scales by the attribute; each kind gets its own skin.
- Castle: **Vorak** (Keep) as now, plus a shield bash on Hard; **Brakk** (Illager Hall) a 1.8× brute: slow, heavy
  knockback, throws a block after a wind-up (counter: keep moving, hit his back); **Arha** (Cavalry) a 0.9× duelist:
  Speed II, sidesteps, lance charge (counter: shield, corner her); **Mos** (Crypt) a necromancer: raises skeleton
  waves, a charged guardian-style soul beam broken by line of sight (counter: kill the adds, use pillars).
- Feudal lieutenants (~50 health): **siege engineer** (Quarry) with telegraphed firework rockets; **frost witch**
  (Frozen) who freezes the ground and slows; **hag** (Coven) who throws potions and can re-arm a smashed relay.
- Balance: every ranged or area attack warns ~1 s ahead (particles + sound) and has a cooldown; threat scales them.

### Implementation notes: camp redesign run (2026-09-29)
- All 30 camp layouts were redesigned in the generators (`tools/gen_structures.py`, `camps_*.py`); `tools/lint_structures.py` (frozen static lint, 30/30 camps and 10/10 town buildings) and `FuzzScenarios` (`camps.fuzz`) guard them.
- `SiteCleanup` removes orphan leaves/unsupported plants and item drops when a camp is built; `Camps.findSite` rejects steep or sea-level-adjacent sites (`tooSteep`).

### Implementation notes: round 2 (normal world, loot, props, skins, coop, 2026-09-29)
- **Two worlds.** The functional scenarios stay on superflat; `camps.fuzz`, `camps.findsite` and `camps.kinds` help themselves to a NORMAL world (seed `warbands`). `camps.fuzz` builds every layout x 4 rotations on 35 sites (24 real sampled by biome and slope with the generator's own heights, plus 11 synthetic worst cases): 3948 builds, 0 problems, no terrain exclusions except ground `Camps.findSite` would never pick (basic checks only there). Env filters: `WARBANDS_FUZZ_LAYOUTS`, `_MODE`, `_SITE`, `_PER_BUCKET`.
- **Bug found by real terrain:** tree crowns under the floor. The foundation stopped at a leaf block, then the orphan-leaf cleanup deleted it, leaving a pit under the floor (mangrove swamp, forest). `BlueprintPlacer.lay` now treats leaves as fill and lays a second pass under the finished floor.
- **Loot.** `Spoils.stock` sets `chests/cache/<kind>` loot tables on at most 2 chests per camp at build time (`MAX_CACHES`), value about a quarter of the War Chest; caches burn with the camp. Town buildings never hold container contents or loot (repairs re-place template NBT). First opener gets the roll (coop: shared by first come).
- **Props.** `weapon_rack`, `hide_rack`, `supply_crate` (decor, craftable, in camps and town buildings), EN+ES, recipes, handbook page "caches".
- **Skins.** `SoldierEntity.DATA_CAMP_KIND`; `SoldierRenderer` uses `raider_<kind>_<unit>.png` with fallback to the plain raider skin.
- **Coop.** Statics (`ALARMS`, `BURNS`, `Traps.ALARMED`, `Raids.BARS`, `WarHornItem.LAST_*`) clear on `SERVER_STOPPED`; claiming a camp awards its advancement to every player within 64 blocks. Claim stays owner-only; garrison scales with players in range (`Threat.party`).
- Not done: cache-opening advancement, war drum, per-player loot rolls.

### Implementation notes: camp review fixes (2026-09-30)
- **Stashes.** `Spoils.stock` fills the two chests closest to the Camp Standard (tie: lower packed position). A rotated layout no longer stocks whichever chests the corner scan hit first. The handbook says they are the ones nearest the banner.
- **Claim while the commander is away.** If the owner is offline, a non-spectator within 64 blocks of the banner who actually fought (hit a guard, was hit by one, or had their own soldier do either) may claim once the guards are dead. Standing nearby is not enough, abandoned camp or not. The owner can always claim, including alone, with no fighter recorded. Plunder still pays that commander's town. The trophy and relic are dropped at the banner, and the relic is marked earned for the owner, so the ally cannot pocket the age-up prize or earn the relic. The ally gets the conquest advancement; the absent owner does not, until they take a camp themselves while online. A player outside that range still cannot claim.

### Independent review scores (advisory, 2026-09-29, fresh Opus reviewer, renders + lint only)
Rubric 0-10 average over silhouette, materials, identity, interior, terrain blend, play value, traps, correctness. Nothing reached the 8 target; the common gaps are thin interiors in the big castles and single-material walls. Camps: keep_concentric 7.1, cavalry_encampment 7.3, crypt_catacomb 6.9, poachers_treehouse 6.8, fort 6.8, sunken_crypt 6.8, illager_watchtower 6.8, coven_stilts 6.6, cavalry_paddocks 6.6, poachers_lodge 6.5, witch_coven 6.4, quarry_stronghold 6.4, fort_twin 6.3, frozen_outpost 6.3, frozen_starfort 6.3, cavalry_ring 6.3, quarry_minehead 6.3, bandit_stockade 6.1, smugglers_dock 6.1, smugglers_warehouse 6.1, crypt_flooded 6.1, bandit_camp 6.0, fort_barbican 5.9, keep_moat 5.9, illager_hall 5.9, frozen_glacier 5.8, quarry_terraces 5.6, keep 5.6, coven_grove 5.5, illager_longhouse 5.5. Town buildings: town_center 7.3, town_center_feudal 7.1, town_center_castle 6.9, barracks 6.7, siege_workshop 6.7, stable 6.6, archery_range 6.3, blacksmith 6.3, house 6.0, watch_tower 6.0.
Backlog (worst five, one fix each): coven_grove (add a ruined hut or altar at the centre), illager_longhouse (done 2026-10-01: dormers, chimney, gable windows, porch, tents, banner poles; height 22), quarry_terraces (layer stone/andesite/tuff, miners' shack, rail carts), keep (tuff band, roofed towers, furnished ground floor), frozen_glacier (ice-cave mouths, lit hall inside). Also keep_moat (square moat on flat grass) and fort_barbican (too close to fort).

### Camp armies (2026-09-30)
Feudal and Castle camps hold an army, not a patrol: `Camps.addArmy` grows the garrison to max(16 | 30, 1.5x | 2x the layout's) guards, capped at 40, on the layout's spare spots (or its ground guards' spots), with extras standing on free cells near the post (`spotNear`). Village camps are unchanged. A War Map no longer picks the small original layout from Feudal on (`CampTier.drawable`); it stays loadable for old saves. Watch entity count/lag in playtests.

### Crypt hardening and grass ring (2026-09-30)
Sunken Crypt's vanilla undead (not the warlord) get twice the health, +8 armor and +30% speed (`Camps.hardenUndead`), so a bow can't kite them; a crypt's army extras are zombies, skeletons, husks and wither skeletons. Cleared margins get their grass back (`BlueprintPlacer.clearColumn`) instead of a brown dirt ring.

### Camp test world (2026-09-30)
`/warbands testworld` (op) registers every camp layout (all 30) on a grid 150 blocks apart south of you, so only the camp you visit builds. `testworld list` shows clickable [tp]/[rebuild] per layout, `testworld tp <id>` goes to a camp's door, `testworld rebuild <id>` wipes one camp back to flat ground (guards, blocks, items, registry entry) and registers it again after a code change. Use a superflat creative world (the wipe assumes flat ground); combine with `/warbands playtest` for a Castle town, army and kit.

## Phase 5: Boss loot and the Warbands creative tab (2026-10-01, built; not yet played)

Interview answers: signature item per boss; scale by camp tier; no extra anti-farm limit (bounded drops).

**Creative tab (done).** `warbands:warbands` registered in `ModItems.initialize()` (icon: War Horn), holds every item that used to
sit in Tools and Spawn Eggs. `itemGroup.warbands` in EN and ES.

**Today:** every `WarlordEntity` (Keep boss and the three mini-bosses) drops the same Warlord's Brand.

**Proposed drops** (all vanilla item bases with a custom name, model and rarity, like the Brand; equipment drop chance stays 0):

| Boss | Camp | Signature drop | Enchants |
|---|---|---|---|
| Warlord Vorak | Keep (150 HP boss) | Warlord's Brand (as now) + one Warlord's Plate piece | as now |
| Chief Brakk | Illager Hall | Brakk's Cleaver: iron-tier axe, red-orange name | Sharpness IV, Looting III |
| Khaness Arha | Cavalry Encampment | Arha's Warbow: bow | Power IV, Punch II, Unbreaking III |
| Grave-King Mos | Sunken Crypt | Mos's Gravebane: sword | Smite V, Unbreaking II |

Mini-boss signatures are diamond or iron tier, never netherite; the Keep boss alone drops netherite. Each boss also rolls a small
bonus pool (2-4 emeralds, an enchanted book, XP bottles; Keep boss adds 1-2 netherite scrap), priced like a camp chest.
Numbers to tune after play. Needs: textures per item (`minecraft-pixel-art`), EN/ES names and tooltips, handbook line, an
advancement per boss, test in `testworld`.

**Implementation notes.** `entity/WarlordSpoils` builds the drops from `SoldierEntity.campKind()` (`illager_hall`,
`cavalry_encampment`, `sunken_crypt`; anything else, including the Keep, keeps the Brand). Built as vanilla bases with
`ITEM_MODEL`: Brakk's Cleaver (diamond axe, Sharpness IV, Looting III, Unbreaking II), Arha's Warbow (bow, Power IV,
Punch II, Unbreaking III, own four pull frames), Mos's Gravebane (diamond sword, Smite V, Unbreaking II). The Keep boss
(`boss` flag) also drops one random Plate piece (same stats as the chest ones) and 1-2 netherite scrap. Every warlord drops
2-4 emeralds, 2-4 XP bottles, one book (Mending I or Unbreaking III) and an XP orb (60, Keep 120). Advancements
`warlord_brakk/arha/mos` (children of the camp advancements). Textures: `tools/gen_boss_loot_art.py` (the bow recolours the
vanilla frames from the Loom jar). Not verified in game: the drops, the bow pull animation and the new icons.

## Implementation notes (twin Fort sally routes, 2026-10-01)

- Playtest screenshot: friendly and enemy troops piled up at the twin-ward Fort's wall. The cross wall is intentional, but its central portcullis closes on alarm while the defenders are ordered to sally. Vanilla melee accepts partial paths, and soldiers have a 24-block follow range, so a distant exit is not enough.
- Felipe chose to keep the dividing wall and add side gates. `camps_feudal.fort_twin` now puts two-wide, two-high fence gates in both flanks of both wards (x=1/29, z=12..13/17..18). Existing `Camps.openGates` opens them on alarm. The tower posts, four divider guns and closing central portcullis remain. Scouts explain the side route in English and Spanish.
- Only `fort_twin.nbt` and its two scout strings were regenerated; unrelated longhouse and boss-loot work was preserved. Previously built camps keep their saved blocks: create side openings manually or test a newly generated twin Fort after restarting the client. No save migration.
- Added `camps.twin-sally`: assert the central portcullis closes, all side leaves open and friendly/enemy militia each get complete vanilla paths through all four exits. Navigation probes are frozen and explicitly grounded, since NoAI also skips gravity.
- Verification: `:warbands:build --offline` passed; generator lint passed 30/30; EN/ES matched 637 keys; focused client game test passed (6 s scenario, 21 s total), with its screenshot reviewed. This proves complete vanilla paths for militia in the default rotation; it does not simulate a full army battle or mounted troops.

## Implementation notes (release regressions and MCP, 2026-10-01)

- Added seven explicit release scenarios: real MCP observation/command/screenshot, cleanup property preservation, hopper and hopper-minecart reward protection, unavailable tracked guards, payout idempotency, all four boss loot branches, and actual saved-world close/reopen. The reload checks stockpile, paid queue, soldier entity/owner, camp, pending retaliation and once-only relic state. No gameplay rules changed.
- Installed mcpfabric 0.5.0 for Fabric 26.1.2 under ignored `.tooling/`; its optional launch flag loads the jar outside the client-test run directory, which the harness deletes on startup. It is absent from the Warbands distribution. Registered the token-reading launcher with Codex and project MCP configuration; authenticated loopback and stdio were verified through a real MCP client.
- `scripts/test-release.sh` runs the bounded selection and companion MCP client. `scripts/check-guide-margins.py` checks rendered text pixels against page margins independently of Java layout arithmetic. The existing body-height test alone passed overflowing caption pages.
- New tests confirm the cleanup bug (old items, fresh unrelated drops and protected rewards are discarded) and guide overflow on pages 15, 21 and 36. Hopper protection, tracked guards, repeat-payout protection, boss drops and saved-world reopen pass. The existing progression test also detects Stable scenery overlapping a horse stall. These findings are not gameplay fixes.
- Corrected the placement test's outdated expectation that the mound location becomes air: the current Town Center template puts a dark-oak stair there. The test now requires the original dirt/grass to be replaced while retaining the rest of its placement, cost, spacing and protection assertions.

## Implementation notes (compact notifications, 2026-10-01)

- Felipe's screenshot showed a full-screen age title, every stat/unlock in chat, and routine advancement toasts together. Age completion now sends one short line pointing to the guide; the advancement milestone toast remains. A partial/missing Town Center upgrade still adds its warning. Army stats, research costs and unlocks are unchanged; benefits remain in the handbook and Advance tooltip.
- Warbands advancements no longer broadcast completion in chat. Routine `task` advancements earn credit silently and stay visible in the advancement tree; `goal` and `challenge` milestones retain their toast. Vanilla recipe unlock notifications are unchanged.
- Camp clearing uses the action bar to direct the owner to the standard. Conquest sends one summary identifying the reward destination and the War Chest/burn warning, plus a separate red revenge warning when applicable. No conquest title/subtitle. Offline commander rewards, map retargeting, co-op credit and payout still run normally.
- `release.age-feedback` and `release.camp-feedback` inspect the actual client HUD after real research/conquest, count received messages and queued/visible toasts, and save screenshots without clearing notifications. Routine award credit is also checked. The release runner includes both scenarios.

## Implementation notes (publishing error fixes, 2026-10-02)

- Reproduced the residual cleanup bug with vanilla `Containers.dropContents`: the old filter deleted all four apples. Removed the item-entity sweep, watch list and per-level tick hook; clearing no longer guesses provenance from item type, thrower or age. Building-volume and camp-approach clearing use `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, preventing unsupported crops from breaking into loot before explicit terrain cleanup. Template placement keeps normal updates; orphan-tree/leaf cleanup remains.
- Expanded `release.cleanup` to cover apples, sticks, four seed types, snowballs, saplings, leaves, flowers, emeralds, older seed/diamond drops and protected relics through the full former watch duration. Added `release.terrain` for House construction over a mature crop and camp-approach clearing over natural dirt/grass. The release runner includes it.
- Corrected both languages: Feudal Crossbowman 10 hearts/6 armor; Castle Knight 18 hearts/11 armor; Castle Ram 24 hearts/9 armor, before research. Camp guidance distinguishes Bandit/Fort advancement trophies from Keep war prizes. No costs, progression or combat balance changed.
- Added source/homepage/issues metadata, a dedicated player README and initial unreleased changelog. Updated the collection status without rewriting yesterday's historical findings. Publishing, platform eligibility, clean installation, normal survival progression, old-world compatibility and multiplayer validation are separate tasks.

- **Verification:** original extended cleanup regression fails (apples gone); after the fix, cleanup, placement, gallery and EN/ES body fit pass. New terrain regression passes after correcting its approach fixture to natural dirt/grass, matching the intended protection of cultivated crops. Independently checked all 37 rendered guide margins (zero failures) and reviewed the four changed pages at full size. Build and refreshed collected jar pass; no registration changes, so no separate datagen run.

## Implementation notes (0.2.0 fixes, 2026-10-16)

- Camp site with a player's block entity in its volume (chest, furnace, sign...): the camp is dropped at build time and the map's cost goes back to its Town Center (loaded for the refund). Plain player walls without a block entity are still cleared (known limit).
- Watch Tower recall and tower removal call `SoldierEntity.releaseFromPost()`: Follow until the next horn order (`ReleasedFromPost`).
- Barracks cancel without a Town Center stores the refund (`PendingRefunds`) and pays it on the first tick the Town Center is in reach.
- Camp guard XP is blocked by `CampGuardExperienceMixin` (targets `Mob`, which overrides `getBaseExperienceReward`).
- Handbook page "Still to Come" removed. Game test `camps.keeps-player-builds` added.
