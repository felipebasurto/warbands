# Warbands

Build a medieval town, train an army, conquer guarded camps and defend your stockpile from raiders.

Warbands is built for mid-to-late survival. You start in the Village Age and earn trophies to reach Feudal and Castle. You train militia, archers, spearmen, crossbowmen, mounted knights and battering rams, and the War Horn tells them to follow, hold or attack. The Blacksmith researches upgrades, and ranged soldiers can stand guard on Watch Towers. Enemy camps have traps, warlords, loot and relics.

## Install

Version 0.2.0 is the second beta. The first one, 0.1.0, was published on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/warbands) on October 8, 2026. See the [changelog](mods/warbands/CHANGELOG.md) for what changed.

- Minecraft Java 26.1.2, Java 25 and Fabric Loader 0.19.5 or newer.
- Fabric API is required. The tested build is 0.155.3+26.1.2.
- Put `warbands-0.2.0.jar` and the matching Fabric API jar in your profile's `mods` folder. Fabric Loader is installed through the launcher/installer, not copied into that folder.
- Other Minecraft versions and loaders are not verified. Dedicated-server and two-player validation remain pending.

For a local build, run `./gradlew :warbands:build` from the repository root; the binary is `mods/warbands/build/libs/warbands-0.2.0.jar`. The `-sources.jar` is for developers.

## First steps

1. Read the illustrated Warbands Handbook, which you receive on your first join and can also craft cheaply. The Warbands advancement tab shows the next step.
2. Craft a Town Center Blueprint, find clear ground, and place it. The handbook shows the recipe and placement rules.
3. Deposit food, wood, stone and gold. Train soldiers and craft a War Horn to issue orders. New recruits wait at their training building until you muster them.
4. Buy a War Map at the Town Center. Defeat every camp guard and right-click the standard to claim supplies, a trophy and a War Chest.
5. Deposit a Bandit Trophy to unlock Feudal, then a Fort Trophy to unlock Castle, paying the displayed research costs. A Keep Trophy is a war prize; it unlocks no further age.

Raids begin in Feudal. Keep troops at home and repair damaged buildings. Building blocks and soldiers do not drop free materials or gear.

Try the beta in a disposable world and back up any world you care about. Upgrading older saves and performance in large battles have not been validated yet.

## Feedback

[Report a bug](https://github.com/felipebasurto/warbands/issues) with the mod version, Minecraft/Fabric versions, reproduction steps and relevant log or screenshot. [Source code](https://github.com/felipebasurto/warbands) · [Changelog](mods/warbands/CHANGELOG.md).

Warbands was built with AI assistance, as documented in the repository's making-of. The project is licensed CC0-1.0. It is an independent Minecraft mod.
