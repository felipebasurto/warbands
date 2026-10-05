# Warbands playtest guide

For testing by hand without gathering and building everything first. Needs cheats (operator).

## Set up (once per world)

1. Make a **Superflat** world with cheats on (creative or survival, either works). Normal worlds work too, on open
   flat ground: buildings that hit trees are skipped, and the chat says how many.
2. Stand where the town should start, face where it should go, and run:
   ```
   /warbands playtest
   ```
   In a second you get, in front of you: a **Castle Age Town Center** with a full stockpile, the Barracks, Archery
   Range, Stable (left), Blacksmith, Siege Workshop, two Houses (right) and a Watch Tower (behind), **two of every
   soldier and a ram** in your army, and a **kit**: the **Tester's Guide** (teal book), handbook, War Horn, the four relics, a Relic Pedestal, the Warhorse
   Whistle, diamond armor, a netherite sword, a bow, arrows and food.
3. **Right-click the Tester's Guide** for a panel of buttons (hover one to see what it does). The older chat menu
   is still there: `/warbands playtest help`.

## The Tester's Guide

| Section | Buttons | What they do |
|---|---|---|
| Travel | **Go home**, **Go to camp**, **Give up camp** | Back in front of your Town Center from anywhere; outside your camp (it builds when you arrive); abandon it to pick another |
| Raids | Warband, Night Stalkers, Shield Wall, Siege, Cavalry, **Skip warning** | Start that raid kind on your nearest town now; Skip makes them march at once |
| Camps (each age) | every camp kind | A free War Map to that kind (only one camp at a time) |
| You and your town | **Fill stockpile**, **Heal**, **Kit again**, Creative, Survival | Max resources, full health and food, refill the kit, switch game mode |
| World | Day, Night, Clear weather, Storm | Set the time and weather |

## A full pass (about 30 minutes)

- **Town:** open the Town Center, check the stockpile and buttons; train a unit in each building; research at the
  Blacksmith; put an archer on the Watch Tower.
- **Raids:** click each raid kind, then Skip warning. Fight with your army (War Horn to give orders). Win and follow the
  smoke to the raiders' war chest. Night Stalkers: try at Night and during the Day.
- **Camps:** click a camp, then Go to camp. Walk in slowly: alarm plate, tripwire arrows, pitfalls, caltrops, powder
  kegs (Castle). Kill the guards (and the warlord in Castle camps), right-click the banner, open the War Chest, watch
  it burn. Give up camp, pick the next kind.
- **Layouts and contraptions:** each kind has 2-3 layouts; click the same kind a few times (Give up camp in between)
  to see them. Read the War Map's Threat and Scouts lines. Try a sneak (sculk sensors), a sabotage (smash the red War
  Relays by a portcullis or under a gun), and a straight assault. Switch `/difficulty` between easy and hard before
  buying a map to compare the stars and the guard count.
- **Warlords:** the Keep's boss has a red bar, a war cry, a charge and (below a third of his health) a slam. Each warlord drops a named weapon (Vorak the Brand plus a Plate piece; Brakk's Cleaver, Arha's Warbow, Mos's Gravebane) and a bonus pool of emeralds, XP bottles and a book. Drawing the Warbow should show its pull frames.
- **Relics:** place the Relic Pedestal near the Town Center and right-click it with each relic. Check the chat line,
  the tooltips and the effects (regeneration, slower foes, faster horses, longer raid warning).
- **Whistle:** use it on the ground: a whistle, a dust burst, a neigh, and a fast horse (Speed II for 30 s). Ride it.
- **Saving:** Save and Quit with a camp out and a raid pending, reload, check both are still there.

## Also useful

- `/warbands home`, `/warbands playtest stock`, `/warbands playtest heal`
- `/warbands raid <kind>`, `/warbands raid skip`, `/warbands camp <kind>`, `/warbands camp tp`, `/warbands camp abandon`
- `/time set midnight` (Night Stalkers need full dark), `/gamerule warbands:raids false` to stop raids.
- Screenshots: F2 (they land in `run/screenshots/`).
