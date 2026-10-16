# Warbands in-game test checklist

`./gradlew :warbands:runClientGameTest` (or `scripts/game-test.sh warbands`) runs **only the ticked items**,
each on its own patch of one world, and reports every scenario (`PASS` / `FAIL`) instead of stopping at the
first failure.

How to use it:
- Before a run, tick `[x]` only what the change could have broken. Untick the rest. Everything else we already
  know works, so we don't pay for it again.
- Leave the ticks as they were for the last run and commit them; the next person sees what was verified.
- One run without editing the file: `scripts/game-test.sh warbands raids.* battles.army-vs-raid` (ids or
  prefixes with `*`, or `all`).
- Every scenario in the code must be listed here, and every id here must exist: the run fails fast otherwise.
- Times are rough, on top of ~40 s to start the game. Photos are kept per run in `build/game-test-shots/warbands-<time>/` (the harness wipes its own folder every run).

## Core loop
- [ ] `core.playtest` - `/warbands playtest` builds a Castle town with every building, army and kit (Tester's Guide); camp teleport on solid ground; go home (photo, ~10 s)
- [x] `core.blueprints` - placement, terrain clearing, stone costs, town spacing, protected blocks, Town Center break rules (~5 s)
- [ ] `core.training` - deposits, training three units, owner and gear, costs, first advancements (~5 s)
- [ ] `core.combat` - soldiers attack what you hit, walk behind, teleport past 24 blocks (~15 s)
- [ ] `core.guidance` - Town Center menu, handbook, advancement tab (photos, ~5 s)

## Ages and buildings
- [ ] `ages.progression` - Village -> Feudal -> Castle, gating, gear, Barracks/Range/Stable, later units, stable horses (~10 s)
- [ ] `ages.buildings` - Blacksmith + Forging, smith, Watch Tower posting, ram and its size (~10 s)

## Raids
- [ ] `raiders.faction` - raiders can't be claimed; 10 of your soldiers beat 5 on sight (photo, ~20 s)
- [ ] `raids.won` - one raid at a time, size, spawn distance, ram, deaths counted one by one, gold, war chest + signal fire, advancement (photo, ~10 s)
- [ ] `raids.kinds` - each raid kind brings its own roster; Shield Wall armor, Cavalry mounts, forAge gating (~40 s)
- [ ] `raids.bag` - the per-town bag never serves the same kind twice in a row, across refills (~40 s)
- [ ] `raids.village-revenge` - a Village town on the raid calendar (took a Feudal camp) is raided as Feudal instead of crashing the tick (~10 s)
- [ ] `raids.plunder` - 5 s at the core takes a fifth of everything; they retreat and vanish (~15 s)
- [ ] `raids.defended` - you within 12 blocks of the core: a raider there loots nothing (~10 s)
- [ ] `raids.unfound-raiders` - raiders the raid can't find (unloaded) don't count as dead: no free win (~8 s)
- [ ] `raids.owner-away` - owner away: no plunder, timeout clock stops; owner back: raid carries on (~20 s)
- [ ] `raids.timeout` - ten minutes without a result: they leave with nothing (~8 s)
- [ ] `raids.off-switches` - gamerule off and Peaceful each end a raid and remove the raiders (~10 s)
- [ ] `raids.town-destroyed` - breaking the Town Center mid-raid ends it (~6 s)
- [ ] `raids.stale-raider` - a raider whose raid is over removes itself; a camp guard stays (~8 s)
- [ ] `raids.wrecking` - building blocks break without drops, your fences drop, nothing with mobGriefing off (~3 s)
- [ ] `raids.rams` - raider rams obey mobGriefing, yours don't; the Reinforced Gate breaks; your blocks drop (~6 s)
- [ ] `repair.buildings` - a half-wrecked Barracks, no repair during a raid, block-by-block repair for 1 stone a block, a click with nothing to repair is free (~15 s)

## Battles
- [ ] `battles.friendly-fire` - who can hurt whom (~1 s)
- [ ] `battles.targets` - raiders go for you and your soldiers, never each other; your soldiers go for raiders (~8 s)
- [ ] `battles.horn-orders` - the horn orders the whole army (far soldiers too); a new recruit takes up the order; Shift goes back one (~5 s)
- [ ] `battles.hold` - Hold fights what comes close, doesn't chase what stands off (~15 s)
- [ ] `battles.army-vs-raid` - 8 soldiers at the Town Center beat a Feudal raid, and it pays (~60 s)

## Camps
- [ ] `camps.others` - another commander's camps: built, guarded, unbreakable standard; a player outside the party can't claim (photos, ~10 s)
- [ ] `camps.caches` - the two stash chests are the ones nearest the banner (~8 s)
- [ ] `camps.claim-away` - owner offline: a player beside the banner claims for them; trophy and relic left there (~15 s)
- [ ] `camps.keep` - Castle War Map: Keep built on arrival, gate, claim after guards, War Chest survives the burn, trophy, revenge, payout (photo, ~20 s)
- [ ] `camps.abandon` - abandon unbuilt (forgotten) and built (stays, still takeable); buy again, no refund (~20 s)
- [x] `camps.keeps-player-builds` - a player's chest in a camp's footprint: the camp is dropped on arrival, the map's cost comes back, the chest survives (~30 s)
- [ ] `camps.revenge-off` - taking a Fort with raids off schedules no revenge (~15 s)
- [ ] `camps.kinds` - all 30 camp layouts (11 kinds) build with standard + the garrison their threat calls for; vanilla guards persistent and tagged (~60 s, photos of the new layouts)
- [ ] `camps.findsite` - on the normal world every camp kind finds a home-biome site around towns (hit rate per kind logged)
- [ ] `camps.loot` - every camp kind has a cache loot table that resolves and rolls items
- [ ] `camps.fuzz` - every layout x 4 rotations on real terrain around the world (~120 builds, ~2-4 min): standard free and reachable, guards free, no leaves left to decay, no plants left to pop, no overhang, foundation filled
- [ ] `camps.bag` - War Map draws cycle every Castle kind before refilling, never the same kind twice in a row (~10 s)
- [ ] `camps.traps` - alarm plate sallies the guards, sealed dispensers, caltrops hurt, kegs break no blocks, disarmed on claim (~30 s)
- [ ] `camps.warlords` - one warlord per Castle kind; the Keep's is the boss (more HP); standard can't be claimed while he stands; he drops his named blade once (not yet asserted for the Cleaver, Warbow and Gravebane or the bonus pool) (~30 s)
- [ ] `camps.relay` - war relays: alarm shuts the barbican portcullis, fires its guns and drops the treehouse's rigged planks; smashing the gate relays reopens it; a broken camp gun spills no arrows (~15 s, photo)
- [ ] `camps.twin-sally` - twin Fort: side gates open on alarm; friendly and enemy militia find complete paths into and out of both wards while the central portcullis stays shut (~5 s, photo)
- [ ] `camps.threat` - Easy/Hard/party/streak threat numbers, raids +4 on Hard vs Easy, a Hard stockade has more guards with the health bonus (~10 s)
- [ ] `camps.relics` - first win at a fantasy camp gives its relic once; the Relic Pedestal enshrines it, won't stack a kind; the Grave Lantern's dead sap foes near the town (~40 s)

## Gallery and text (photos for review and the blog)
- [ ] `gallery.town` - Town Center and house, then the Town Center at each age
- [ ] `gallery.army` - one of each Castle unit, and the ram
- [ ] `gallery.buildings` - every building, stable horses, the smith, every building menu
- [x] `gallery.guide` - every guide page
- [x] `lang` - en_us and es_es have the same keys; every blueprint says what it's for (~1 s)

## Release regressions
- [ ] `release.mcp` - companion MCP client observes this disposable world, opens the handbook, screenshots it and sets the completion block (requires installed mcpfabric + companion script, up to 30 s)
- [x] `release.cleanup` - old drops, vanilla container spills (including every former debris type) and protected commander relics survive cleanup (~6 s)
- [x] `release.terrain` - House and camp approach clearing remove unsupported plants without terrain loot (~6 s)
- [ ] `release.hoppers` - hopper and hopper minecart collection cannot steal commander rewards; ordinary items still collect (~1 s)
- [ ] `release.guards` - saved tracked camps cannot be claimed with unavailable living guards (~1 s)
- [ ] `release.payout` - decoding paid camp data and paying again does not duplicate the reward (~2 s)
- [ ] `release.boss-loot` - all four signature weapons once, emeralds, XP bottles and book; Vorak also leaves armor (~1 s)
- [ ] `release.reload` - actual saved world is closed/reopened; stock, paid queue, soldier entity/owner, camp, pending revenge and earned relic persist (~15 s)

- [ ] `release.age-feedback` - received age-up chat, no title, at most milestone + recipe toasts; routine awards retain credit without popups (photos)
- [ ] `release.camp-feedback` - Bandit/Fort/Keep/offline commander conquest summaries, warning counts and toast bounds on the real HUD (photos)
- [ ] `release.warbow` - Arha's Warbow photos at rest and at three draw stages (look at the pull frames, ~5 s)

## Marketing shoot (never ticked: `scripts/shoot.sh warbands` runs it; spectator, no HUD, 1920x1080, real biomes)
- [ ] `shoot.army` - a Castle army in five ranks plus a ram on a meadow: aerial, low hero, wide and flank pictures (~40 s)
- [ ] `shoot.ui` - every building menu, every guide page, the advancement tab and the HUD at 1920x1080 with the interface on (~3 min)
- [ ] `shoot.howto` - blueprint outline, house built, recruiting menu and army with the horn at 1920x1080 with the HUD (~2 min)
- [ ] `shoot.ui-es` - the same interface pictures in Spanish (~3 min)
- [ ] `shoot.ages` - the same town at Village, Feudal and Castle Age from one camera (~2 min)
- [ ] `shoot.film-build` - video: a Town Center rising layer by layer while the camera circles it, 5 s
- [ ] `shoot.moods` - a small army at dawn, in the desert, at dusk, in rain and in a cherry grove, hero and wide pictures each (~3 min)
- [ ] `shoot.portraits` - every unit type close up plus a line-up of all of them (~1 min)
- [ ] `shoot.town` - the playtest Castle town on plains from four angles around it and one down the street (~40 s)
- [ ] `shoot.war` - staged war (no AI): defenders and attackers in melee, frozen arrows, fire, blasts and smoke at sunset, six pictures (~6-8 min)
- [ ] `shoot.battle` - a Feudal raid against 12 defenders at a Feudal town, seven pictures from the air while they fight (~1-2 min)
- [ ] `shoot.film-war` - video: the staged war from the air down to the clash, fires and blasts, 8 s at 20 fps (frames -> mp4/gif by shoot.sh)
- [ ] `shoot.film-melee` - video: the same line-up with the AI on, a real melee from the ground, 8 s
- [ ] `shoot.film-assault` - video: the army charges a Fort or Keep and fights its guards for real, 8 s
- [ ] `shoot.assault` your army storms enemy camps, posed, with fire and blasts
- [ ] `shoot.camps` - every camp kind built in its home biome with one aerial picture each (~1 min)
