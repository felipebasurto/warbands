# Changelog

## 0.2.0

- Fixes: when the world refuses a new soldier, its paid unit stays in the queue instead of being lost; a Warhorse Whistle is kept when the horse can't appear.
- Fixes: a training building that is broken pays its pending refunds to the Town Center.
- Fixes: a Town Center that changes owner starts a clean raid calendar, and its old raid ends with the raiders going home.
- Fixes: a camp whose seller Town Center is gone frees its slot, so the owner can buy another War Map.
- Fixes: a camp guard that never spawned is no longer listed, so a camp can always be conquered.
- Stable horses explain why they can't be ridden when clicked, instead of doing nothing.
- Spanish: terms unified ("pueblo", "Centro Urbano"), raider names agree in gender, handbook pages match the English rules (Orders, Raids, Relics, Taking Camps), and the follow order reads naturally.
- Performance: fewer checks while no menu is open; repair counts are recomputed only while the Town Center screen is open; blueprint fit is rescanned only when the aim or stance moves; camp scans skip deaths that are not mobs; War Map tags are read once per stack.
- Clearing a site no longer deletes items that drop in the seconds after it, so unrelated drops survive.
- Handbook: Crossbowman, Knight and Battering Ram stats state their Castle values before research; Taking a Camp explains trophies.
- Source, issues and homepage links point to the public repository and the CurseForge page.
- Enemy camps no longer wipe out a player's chest, furnace or other container in their footprint: the camp is dropped and the War Map's cost returns to the Town Center.
- Withdrawing soldiers from a Watch Tower keeps them on Follow instead of the army's standing order.
- Barracks refunds for cancelled units wait for the Town Center when it is out of reach, and are paid when it returns.
- Camp guards give no experience (the mixin now targets Mob).
- The Blacksmith explains the Village Age in the right language; Spanish calls illagers "saqueadores"; the handbook's "Still to Come" page is removed.

## 0.1.0 (initial beta, published on CurseForge on 2026-10-08)

- Town Center, stockpile, three ages, training buildings and six soldier types.
- War Horn orders, Blacksmith research, mounted knights, siege rams and tower garrisons.
- Guarded camps, traps, warlords, town raids, repairs, War Chests and relics.
- Illustrated handbook and advancements in English and Spanish.
- Compact age/conquest notifications; corrected guide margins and Stable stalls.
- Construction preserves unrelated item drops, including food and seeds spilled from containers. Terrain clearing removes unsupported plants without spawning cleanup loot.
- Handbook unit stats include the appropriate age bonuses and distinguish progression trophies from Keep prizes.

Clean installation, normal survival progression, historical-save upgrades, dedicated-server/co-op behavior and large-battle performance remain release validation tasks. Download it from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/warbands).
