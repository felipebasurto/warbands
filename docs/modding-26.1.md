# Modding Minecraft 26.1.2 with Fabric: reference

Everything here was checked against the real 26.1.2 jars while building `starter-kit` and
`warbands` (September 2026). When something isn't covered, **verify it**
([Verifying an API](#verifying-an-api)) instead of trusting memory: most AI knowledge predates 26.1.

## Contents

1. [Toolchain](#toolchain)
2. [What changed in 26.1](#what-changed-in-261)
3. [Verifying an API](#verifying-an-api)
4. [Registration](#registration)
5. [Items](#items)
6. [Blocks and block entities](#blocks-and-block-entities)
7. [Menus and screens (GUI)](#menus-and-screens-gui)
8. [Entities and AI](#entities-and-ai)
9. [Entity rendering](#entity-rendering)
10. [World-saved data](#world-saved-data)
11. [Structures (NBT templates)](#structures-nbt-templates)
12. [Block states cheat sheet](#block-states-cheat-sheet)
13. [Tags](#tags)
14. [Advancements, recipes and guidance](#advancements-recipes-and-guidance)
15. [Events](#events)
16. [Resource file paths](#resource-file-paths)
17. [Gradle and local environment gotchas](#gradle-and-local-environment-gotchas)

---

## Toolchain

| Thing | Value |
|---|---|
| Minecraft | 26.1.2 (26.2 and 26.3 exist; we stay on 26.1.x on purpose) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| Loom plugin | `net.fabricmc.fabric-loom` 1.18.2 |
| Gradle | 9.7.0 (Loom 1.18 fails on 9.5 with a "No matching variant" error) |
| Java | 25 (mixin `compatibilityLevel: JAVA_25`) |
| DataVersion (`SharedConstants.WORLD_VERSION`) | 4790 |

Latest versions: `https://meta.fabricmc.net/v2/versions/game`, `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`.
The official example mod has a branch per version: `github.com/FabricMC/fabric-example-mod` (branch `26.1`).

## What changed in 26.1

- **Unobfuscated.** No Yarn mappings, no remapping. Code uses **Mojang names**: `Identifier`
  (not `ResourceLocation`), `ResourceKey`, `BuiltInRegistries`, `Registries`, `Level`, `ServerLevel`,
  `Player`, `Component`, `InteractionResult`, `CreativeModeTab`, `AbstractContainerMenu`.
  Yarn names (`World`, `PlayerEntity`, `Text`, `ItemGroup`, `ScreenHandler`, `ActionResult`) don't exist.
- **Gradle:** plugin `net.fabricmc.fabric-loom`, plain `implementation`/`compileOnly` (not `modImplementation`), `jar` (not `remapJar`).
- **Fabric API renames** follow Mojang names: `ItemGroupEvents` → `CreativeModeTabEvents`,
  `KeyBindingHelper` → `KeyMappingHelper`, `ExtendedScreenHandlerType` → `ExtendedMenuType`,
  `WorldRenderEvents` → `LevelRenderEvents`, `BlockRenderLayerMap` is gone. Check
  `docs/fabric-api-classes-26.1.2.txt` before using any Fabric class.
- **docs.fabricmc.net targets 26.2** now. Some examples use 26.2-only API (e.g. `BlockItemId`).
- **GUI rendering** uses `GuiGraphicsExtractor` and `extract*` methods (see below).

## Verifying an API

Fastest options, in order:

1. **Decompiled sources** (best): `./gradlew :<mod>:genSources` once, then extract and grep:
   ```bash
   L=.gradle/loom-cache/minecraftMaven/net/minecraft
   mkdir -p /tmp/mc && cd /tmp/mc
   unzip -qo $L/minecraft-common-*/26.1.2/*-sources.jar
   unzip -qo $L/minecraft-clientOnly-*/26.1.2/*-sources.jar
   grep -rn "class FollowOwnerGoal" net/minecraft
   ```
   Read vanilla classes that do what you want (a wolf for tamed mobs, a skeleton for bows,
   a furnace screen for progress bars, a barrel for block entities with menus).
2. **javap** on the deobfuscated jar for signatures:
   ```bash
   J=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.1.2/minecraft-common-deobf-26.1.2.jar
   .tooling/jdk-25/Contents/Home/bin/javap -cp $J 'net.minecraft.world.item.Item$Properties'
   ```
3. **Fabric API**: grep `docs/fabric-api-classes-26.1.2.txt`, then `javap` the module jar from
   `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/<module>/<version>/`. Fabric source
   jars usually aren't downloaded. The Gradle cache also holds **old** versions from other projects,
   so filter by the versions in the `fabric-api-0.155.3+26.1.2.pom`.
4. **Vanilla data files** as examples: `unzip -p ~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar data/minecraft/recipe/diamond_sword.json`.

## Registration

```java
// Items: setId(key) is mandatory BEFORE constructing, or the game crashes at startup.
ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));

// Blocks + BlockItem
ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));
new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));

// Entity types
ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
EntityType.Builder.of(MyMob::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build(key);
FabricDefaultAttributeRegistry.register(TYPE, MyMob.createAttributes());

// Block entity types (the vanilla constructor is private)
FabricBlockEntityTypeBuilder.create(MyBlockEntity::new, MY_BLOCK).build();

// Menus
new MenuType<>(MyMenu::new, FeatureFlags.VANILLA_SET);

// Spawn eggs
new SpawnEggItem(new Item.Properties().spawnEgg(TYPE).setId(key));   // needs its own item model + texture
```

Creative tabs: `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tab -> tab.accept(ITEM))`.

## Items

- `use(Level, Player, InteractionHand) → InteractionResult` (`SUCCESS`, `SUCCESS_SERVER`, `PASS`, `FAIL`).
- `useOn(UseOnContext)`: `getClickedPos()`, `getClickedFace()`, `getPlayer()`, `getItemInHand()`.
- `appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`.
- `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)`, `hurtEnemy(ItemStack, LivingEntity, LivingEntity)`.
- `stack.consume(1, player)` (respects creative), `stack.hurtAndBreak(1, player, hand)`, `player.getCooldowns().addCooldown(stack, ticks)`.
- Food: `stack.get(DataComponents.FOOD)` → `FoodProperties.nutrition()`.
- Weapon stats: `new Item.Properties().attributes(ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, amount, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build())`. A one-hit bonus can be `AttributeInstance.addOrUpdateTransientModifier` just before `doHurtTarget`, then `removeModifier(id)` in a `finally`.
- Dyed leather: `stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb))`.
- Messages: `player.sendOverlayMessage(Component)` (action bar). `displayClientMessage` is gone.
- `level.isClientSide()` is a method. Use `if (level instanceof ServerLevel serverLevel)` for server logic.
- After setting a player's velocity on the server, set `player.hurtMarked = true` to sync it.
- Permanent attribute changes on a spawned entity: `inst.addOrReplacePermanentModifier(new AttributeModifier(modId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE))` (or `ADD_VALUE`); read with `inst.hasModifier(id)`. `getAttribute(Attributes.X)` is `@Nullable` — it returns null when the attribute isn't registered for that entity type.
- `Util.shuffle(list, randomSource)` shuffles in place (`net.minecraft.util.Util`, not `net.minecraft.Util`).
- Deep night (the darkest few hours, ~13670–22330): `level.getSkyDarken() >= 11` — `skyDarken` is `15 - SKY_LIGHT_LEVEL` (0 at noon, 15 full dark). For a whole-night gate use `level.isDarkOutside()` instead.
- Custom model on a **vanilla item stack** (named boss weapons): `stack.set(DataComponents.ITEM_MODEL, Identifier)` →
  the model is looked up in `assets/<ns>/items/<name>.json` (the item-model definition file), which points at a normal
  `models/item/<name>.json` + texture. Enchant it with `stack.enchant(holder, level)` where
  `holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)`.
- Velocity on any entity: after `setDeltaMovement(...)` server-side set `entity.hurtMarked = true` to sync it to
  clients (`hasImpulse` is renamed in 26.1).

## Blocks and block entities

- **Steppable triggers** (alarm plates, caltrops): 26.1 signature is
  `entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean)` —
  `InsideBlockEffectApplier` lives in `net.minecraft.world.entity` (not `world.level`). For a 1-2px-high block
  to detect entities it must have `getCollisionShape() → Shapes.empty()` so feet sink into it (pressure-plate
  pattern). `entity.makeStuckInBlock(state, new Vec3(0.7, 0.8, 0.7))` slows; `entity.hurtServer(serverLevel, source, f)` hurts.
- `Entity.isIgnoringBlockTriggers()` is the vanilla "doesn't trip wires/plates" flag — a mixin on `Entity` that
  returns true covers tripwires AND pressure plates at once (used so camp guards don't set off their own traps).
- `neighborChanged(state, level, pos, block, @Nullable Orientation, boolean movedByPiston)` — 26.1 adds the
  `Orientation` param. `level.hasNeighborSignal(pos)` covers weak power.
- Tripwire hooks: `getSignal` emits 15 toward **all** directions while powered (so a mechanism directly adjacent
  to a hook fires), `getDirectSignal` only toward `FACING`. A dispenser/keg beside the west hook of a wire works.
- `level.explode(null, x, y, z, radius, Level.ExplosionInteraction.NONE)` damages entities but breaks no blocks.
- `AbstractArrow.pickup` defaults to `DISALLOWED`; `setOwner` only promotes it to `ALLOWED` when the owner is a
  player — dispenser-fired (ownerless) arrows can never be picked up, no code needed.
- `Player.sendSystemMessage(Component)` is chat; the actionbar overlay is `sendOverlayMessage(Component)`
  (or `ServerPlayer.sendSystemMessage(component, true)`). There is no `displayClientMessage`.
- `LandPathTypeRegistry.register(block, PathType.DAMAGING, PathType.DAMAGING)` makes mobs path around a block
  (fabric-content-registries-v0).


- `BaseEntityBlock` requires `codec()`: `public static final MapCodec<X> CODEC = simpleCodec(X::new);`.
  Override `getRenderShape` → `RenderShape.MODEL`.
- Use: `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)`, then `player.openMenu(blockEntity)`.
- Ticking: `getTicker(...)` → `createTickerHelper(type, MY_TYPE, (l, pos, s, be) -> be.serverTick(...))`, server only.
- Lifecycle: `onPlace(state, level, pos, oldState, movedByPiston)`; removal is
  `affectNeighborsAfterRemoval(state, ServerLevel, pos, movedByPiston)` (there's no `onRemove`).
  Container contents drop via `BlockEntity.preRemoveSideEffects` during `setBlock`.
- Save and load: `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`. `ValueOutput`: `putInt`,
  `putString`, `putIntArray`, `store(key, codec, value)`, `storeNullable`. `ValueInput`: `getIntOr`,
  `getStringOr`, `getIntArray(key)` → Optional, `read(key, codec)` → Optional. There's no NbtCompound juggling.
- Rotation-aware blocks: implement `rotate`/`mirror` so structure templates rotate them.
- `PushReaction.BLOCK` makes a block unmovable by pistons.
- A parameterized block (one class, several blocks) needs a `RecordCodecBuilder.mapCodec` with its parameter plus
  `propertiesCodec()` (see `ButtonBlock`, or `warbands/town/TrainingBlock`). One block entity class can back
  several `BlockEntityType`s: `FabricBlockEntityTypeBuilder.create((pos, state) -> new X(param, pos, state), block)`.
- **Every way a block can drop items** goes through `BlockState.getDrops(LootParams.Builder)` with
  `LootContextParams.ORIGIN` set: player breaks, explosions (`onExplosionHit`), lost support, `/loot`.
  A mixin at its head is the one place to stop drops for specific positions (`warbands/mixin/StructureDropsMixin`).
  Pistons ask the static `PistonBaseBlock.isPushable(state, level, pos, dir, allowDestroyable, connectionDir)`;
  falling blocks (anvils, sand) start falling in `FallingBlock.tick`.
- **A loot chest placed from code** (loot rolls once, on first open):
  `ChestBlockEntity.setLootTable(ResourceKey<LootTable>, seed)` (the `RandomizableContainer` default; call
  `setChanged()` after — the setter doesn't). Equivalent static helper:
  `RandomizableContainer.setBlockEntityLootTable(BlockGetter, RandomSource, BlockPos, key)`.
  A chest's custom name has no setter: `chest.applyComponents(DataComponentMap.EMPTY,
  DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, component).build())`
  (`DataComponentMap.builder()` is package-private). Facing: `ChestBlock.FACING`
  (`HorizontalDirectionalBlock.FACING`); a chest placed with `setBlock` stays `TYPE.SINGLE`.
  Working code: `warbands/camp/Spoils`.

## Menus and screens (GUI)

- **Menu:** client constructor `(int id, Inventory inv)` with dummy containers, plus a server constructor with the
  real data. `addStandardInventorySlots(inventory, 8, y)` adds the 27+9 player slots (the hotbar lands at y+58).
  `addDataSlots(ContainerData)` syncs ints, but **as shorts** (max 32767), so clamp or split big values.
- **Buttons without custom packets:** override `clickMenuButton(Player, int id)` in the menu. On the client, call
  `minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)`.
- **`SimpleContainer` has no listeners** in 26.1: subclass it and override `setChanged()` to react to slot changes.
  Guard against recursion when you modify the container from inside `setChanged()`.
- Return leftover items on close: `removed(Player)` → `clearContainer(player, container)`.
- `stillValid(access, player, block)` closes the menu when the player is out of reach. That matters in tests too.
- **Screen:** extend `AbstractContainerScreen<M>(menu, inv, title, width, height)`. Override:
  - `extractBackground(GuiGraphicsExtractor g, mx, my, pt)`: `g.blit(RenderPipelines.GUI_TEXTURED, TEX, x, y, 0, 0, w, h, 256, 256)`, `g.fill`, `g.item(stack, x, y)`.
  - `extractLabels(g, mx, my)`: coordinates are **relative** to the panel. `g.text(font, text, x, y, 0xFF404040, false)`. Colors are **ARGB**.
  - `extractRenderState(g, mx, my, pt)`: call super, then `g.setTooltipForNextFrame(font, List<Component>, Optional.empty(), mx, my)`.
  - `mouseClicked(MouseButtonEvent event, boolean doubleClick)`: `event.x()`, `event.y()`.
  - Widgets: `Button.builder(text, onPress).bounds(x, y, w, h).tooltip(Tooltip.create(c)).build()` → `addRenderableWidget`. Set `button.active` in `containerTick()`.
- Register: `MenuScreens.register(TYPE, MyScreen::new)` in the client initializer.
- UI rules learned the hard way: compact big numbers (`1.2k`, `10k`) with exact values in tooltips; items drawn
  in 18×18 slot frames go at frame+1 (the frame's inner origin); disabled buttons must say **why** (in the tooltip),
  and limits like "population full" need a visible red state, not just a greyed button.
- Book screen: `new BookViewScreen(new BookViewScreen.BookAccess(List<Component> pages))`. A page fits about
  **14 lines × 19 characters**.

## Entities and AI

- **Reuse vanilla goals.** For "follows its owner and fights for them", extend `TamableAnimal` (like `Wolf`):
  - `FollowOwnerGoal(this, speed, startDist, stopDist)` also handles teleporting, via `shouldTryTeleportToOwner()`
    (default is 12 blocks). **Override `shouldTryTeleportToOwner`** to change the distance.
  - `OwnerHurtByTargetGoal` (defend the owner), `OwnerHurtTargetGoal` (attack what the owner hits), `HurtByTargetGoal`.
  - `wantsToAttack(target, owner)` filters targets (friends, pets, PvP rules).
  - Required overrides: `isFood` → false, `getBreedOffspring` → null. Call `setPersistenceRequired()`.
    Call `setAge(0)` after `finalizeSpawn`.
  - Owner: `setTame(true, false)` + `setOwnerReference(EntityReference.of(uuid))`, which works while the owner is offline.
    Read it with `getOwnerReference().getUUID()`.
- **Spawning world-placed mobs** (`Camps.build`): `EntityType.create(level, EntitySpawnReason.STRUCTURE)` →
  `snapTo` → `finalizeSpawn` → `setPersistenceRequired()` + `setHomeTo(pos, radius)` + `addFreshEntity`.
  `addFreshEntity` adds to the section manager but the entity is only *tracked* (visible to `level.getEntity(uuid)`)
  once its chunk's ticket is entity-ticking — that applies on a later tick, so assert guard counts a few ticks after
  building, not in the same server call. Force a footprint to tick with `level.setChunkForced(cx, cz, true)` +
  `level.getChunk(cx, cz, ChunkStatus.FULL, true)` (ChunkStatus moved to `net.minecraft.world.level.chunk.status`).
- `Entity.getTags()` is now `entityTags()`; `Mob.setHomeTo(BlockPos, int)` limits wandering; tag a mob and mixin-guard
  `LivingEntity.dropAllDeathLoot` to keep it drop-free. XP: `Mob` overrides `getBaseExperienceReward`, so a mixin on
  `LivingEntity` never reaches a mob's XP; target `Mob.getBaseExperienceReward` instead.
- `MeleeAttackGoal(PathfinderMob, speed, followEvenIfNotSeen)` works for any `PathfinderMob`.
- `RangedBowAttackGoal<T extends Monster & RangedAttackMob>` rejects non-monsters: **copy it** into your own
  goal (see `warbands/entity/ai/SoldierBowAttackGoal`). Arrows: `ProjectileUtil.getMobArrow(...)` and
  `Projectile.spawnProjectileUsingShoot(...)`, with `arrow.pickup = AbstractArrow.Pickup.DISALLOWED` so they can't be farmed.
  `getMobArrow` calls `setBaseDamageFromMob` (power × 2, plus difficulty variance). Overwrite with
  `setBaseDamage` afterwards if the bolt should deal a fixed amount. **Damage on hit is
  `ceil(baseDamage × speed)`** (`AbstractArrow.onHitEntity`), so a bolt shot at velocity 1.9 with base 5
  hits for about 10, not 5. Divide the damage you want by the launch speed. A crossbow should not call
  `CrossbowItem.use` (it looks for ammo and can consume it); fire on a timer while holding
  `Items.CROSSBOW`. `SoundEvents.CROSSBOW_SHOOT` is a `SoundEvent`, not a `Holder`.
- Attributes: `Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, ..).add(Attributes.ARMOR, ..)...`
  The builder is an `ImmutableMap.Builder`: adding the same attribute twice fails at startup, so pass
  values (e.g. a unit's base attack) as parameters instead of adding again.
- **Vanilla spears exist** (`Items.STONE_SPEAR` ... `NETHERITE_SPEAR`, tag `ItemTags.SPEARS`). Their
  `DataComponents.ATTACK_RANGE` has a **minimum** reach, and `Mob.isWithinMeleeAttackRange` honors it, so a mob
  with `MeleeAttackGoal` walks up close and then can't hit. `stack.remove(DataComponents.ATTACK_RANGE)` before
  equipping. Their flat damage is low (stone +1 ... diamond +3); vanilla zombies use `SpearUseGoal`
  (charge), which requires a `Monster`.
- No loot farming: `setDropChance(slot, 0f)` for every `EquipmentSlot`.
- Damage hooks: `hurtServer(ServerLevel, DamageSource, float)`, `doHurtTarget(ServerLevel, Entity)`,
  `remove(RemovalReason)` (`reason.shouldDestroy()` = the entity really died or was discarded, not just unloaded).
- Entity save uses the same codecs as block entities: `addAdditionalSaveData(ValueOutput)` and
  `readAdditionalSaveData(ValueInput)`. Synced fields still go through `SynchedEntityData`.
- Guarding a spot: `setHomeTo(pos, radius)` plus `MoveTowardsRestrictionGoal`. `TargetGoal` will not
  acquire a target outside that home. `clearHome()` removes it. `Mob` already saves the home.
- Friendly fire across a whole side: Fabric `ServerLivingEntityEvents.ALLOW_DAMAGE`.
- Spawning from code: `type.create(level, EntitySpawnReason.MOB_SUMMONED)`, `snapTo(x, y, z, yaw, pitch)`,
  `finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, null)`, `level.addFreshEntity(e)`.
  Find space with `level.noCollision(entity, type.getSpawnAABB(x, y, z))`.
- **Riders:** a humanoid mob riding a horse needs `EntityType.Builder.ridingOffset(-0.7F)` (the zombie's value).
  Without it the rider's feet sit at the horse's passenger attachment (1.44) and it floats above the saddle.
  The builder stores it as a `VEHICLE` attachment of `-ridingOffset`, which is subtracted from the seat.
- **Bigger or smaller mobs:** give `Attributes.SCALE` a base value in the attribute builder; rendering follows.
  But `Entity`'s constructor sizes the hitbox before attributes exist, so also call `refreshDimensions()` in your
  constructor, or the hitbox keeps the unscaled size. Scaled mobs saved earlier keep their stored base value:
  reset it in `readAdditionalSaveData`. A mob wider than 1 block needs `floor(width + 1)` blocks of clearance to path.
  To draw a mob bigger than its hitbox, override `LivingEntityRenderer.scale(state, poseStack)` and call
  `poseStack.scale(...)` after `super` (and scale `getShadowRadius` to match).
- **Scenery mobs** (horses in a stall, a smith): `setInvulnerable(true)` (creative and `/kill` still work),
  `MobCategory.MISC` (no spawn-cap effect), override `isPushable`, `canBeLeashed`, `shouldDropLoot(ServerLevel)`,
  `shouldDropExperience`, `mobInteract` (horses: `PASS` blocks riding and taming), `removeWhenFarAway`. Tie them
  to their building by saving its core position and discarding when the block is gone.
- **Don't subclass `Villager` for looks.** A `Villager` claims job-site and bed POIs, trades, gossips, spawns iron
  golems, joins raids and turns into a zombie villager. Zombies and illagers target `AbstractVillager`; golems target
  `Enemy`. A plain `PathfinderMob` avoids all of it, and the renderer can still borrow the villager look (below).

- **Server ticks:** `ServerTickEvents.END_LEVEL_TICK` / `START_LEVEL_TICK` (per `ServerLevel`) and `END_SERVER_TICK`.
  The older `END_WORLD_TICK` name doesn't exist in our Fabric API.
- **Game rules:** `GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS).buildAndRegister(id)` returns a
  `GameRule<Boolean>`; read it with `level.getGameRules().get(rule)`; players set it with `/gamerule <namespace>:<path>`.
- **Terrain height without loading chunks:** `level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
  level, level.getChunkSource().randomState())`; compare with `OCEAN_FLOOR_WG` (equal = no water) and `getSeaLevel()`.
  Flat worlds: sea level -63, surface -60.
- **Boss bars:** `new ServerBossEvent(UUID, name, BossBarColor, BossBarOverlay)`, `addPlayer`/`removePlayer`, `setProgress`;
  keep them in memory and rebuild from saved state. The raid horn is `SoundEvents.RAID_HORN.value()` (a holder).
  A bar owned by an entity: update it in `aiStep` (players within range), clear it in `onRemoval` —
  `setRemoved` is `final` and can't be overridden.
- **Commands:** `CommandRegistrationCallback.EVENT` with `Commands.literal(...).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`.
- `Inventory.contains(ItemStack)` compares components too (a renamed item doesn't match); use `contains(stack -> stack.is(item))`.

- **Changing vanilla mobs:** `ServerEntityEvents.ENTITY_LOAD` `(entity, level)` fires on every spawn *and* chunk load, so
  keep changes idempotent. `getAttribute(Attributes.X).setBaseValue(v)` (base values are saved with the mob). `Mob.goalSelector`
  and `targetSelector` are **protected**: use an `@Accessor` mixin; goals aren't saved, so re-add them on every load.
  Drops: `LootTableEvents.MODIFY` `(key, builder, source, registries)` with `EntityType.X.getDefaultLootTable()` (an Optional key).
- **Natural spawns:** `BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type, weight, min, max)`;
  a custom mob also needs `SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules)`
  (public thanks to Fabric's transitive access wideners).
- **HUD:** `HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id, (GuiGraphicsExtractor g, DeltaTracker dt) -> ...)`
  (also `addFirst/addLast/replaceElement/removeElement`). The looked-at entity on the client is `Minecraft.getInstance().crosshairPickEntity`.

- **Swing animation for non-Monster mobs:** `LivingEntity.updateSwingTime()` (which advances `attackAnim`) is only
  called from `Monster.aiStep` and `Player`. A `TamableAnimal`/`PathfinderMob` that swings a sword shows no animation,
  and `swinging` stays stuck at true, until you call `this.updateSwingTime()` in your own `aiStep`.
- **Target goals call `getFollowDistance()` from their constructor** (`NearestAttackableTargetGoal` builds its
  `TargetingConditions` there). Overriding it with a field of your subclass NPEs: read `this.mob` instead.
- **Not shoving a player:** pushing happens from both sides (`LivingEntity.pushEntities` -> `doPush(other)` ->
  `other.push(this)`, and `Entity.push(Entity)` moves *both*). Override `push(Entity)` and `doPush(Entity)` and skip
  the player there (then nudge yourself away with `push(dx, 0, dz)`).
- **Doors in paths:** `getNavigation().setCanOpenDoors(true)` turns closed wooden doors into walkable nodes (the mob
  stops at the door: good for something that then breaks it). `AbstractArrow` has no base-damage getter; a mob arrow
  from `ProjectileUtil.getMobArrow` is about `power * 2`. Stuck arrows live 1200 ticks (`tickDespawn`, `inGroundTime`).
- **A non-null path may stop at a wall:** `MeleeAttackGoal.canUse()` accepts any non-null path; use
  `Path.canReach()` in navigation tests to prove it reaches the target. Check alarmed layouts too: an open
  portcullis in a calm camp does not prove the guards can sally out after it closes.
- **Frozen navigation probes:** `setNoAi(true)` also skips movement and gravity (`LivingEntity.aiStep`), so a
  newly spawned frozen mob never lands. `GroundPathNavigation.canUpdatePath()` requires `onGround()`, liquid or a
  passenger; for a fixed test probe placed on a verified floor, call `setOnGround(true)` before checking its path.
- **Explorer-style maps:** `MapItem.create(level, x, z, scale, true, true)` + `MapItem.renderBiomePreviewMap(level, stack)`
  + `MapItemSavedData.addTargetDecoration(stack, pos, key, MapDecorationTypes.RED_X)` (as `ExplorationMapFunction`
  does). The centre snaps to a grid of `128 << scale` blocks (`MapItemSavedData.createFresh`), so check both points fit.
- **Titles:** `player.connection.send(new ClientboundSetSubtitleTextPacket(c))` then `new ClientboundSetTitleTextPacket(c)`.
- **Item use pose:** override `getUseAnimation` -> `ItemUseAnimation.TOOT_HORN` (or BOW, SPEAR...) and
  `getUseDuration`, and call `player.startUsingItem(hand)` in `use`, like `InstrumentItem`.

- Dispensed arrows are **pickable** (`ArrowItem` sets `Pickup.ALLOWED`) and ownerless. To make trap arrows
  unrecoverable, set `arrow.pickup = DISALLOWED` in `ServerEntityEvents.ENTITY_LOAD` (see `camp/Traps`).
- Mobs in `#minecraft:burn_in_daylight` (skeletons, strays, zombies...) burn in the sun unless they wear a helmet:
  a vanilla mob used as a permanent guard needs infinite Fire Resistance (see `Camps.build`).

## Entity rendering

- GUI: `graphics.blit(pipeline, tex, x, y, u, v, drawW, drawH, regionW, regionH, texW, texH)` draws a region scaled
  (e.g. a skin's 8x8 face at 24x24); `graphics.blitSprite(pipeline, Identifier.withDefaultNamespace("hud/heart/full"), x, y, 9, 9)`.
- Player-shaped mob: `HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>` with
  `new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER))` and
  `new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, ctx.getModelSet(), HumanoidModel::new), ctx.getEquipmentRenderer())`.
  Implement `createRenderState()` and `getTextureLocation(state)`.
  Register with `EntityRendererRegistry.register(TYPE, ctx -> new MyRenderer(ctx))`.
- The skin is a **64×64 player skin**, and the overlay layers (hat, jacket, sleeves, pants) render too.
  Use them for hoods, helmets and quivers.
- **Worn armor hides the skin.** To show an outfit, paint it in the skin and give the mob an `Attributes.ARMOR` stat instead.
- A mob that is not a player shape uses `MobRenderer` plus your own `EntityModel<LivingEntityRenderState>`.
  Register the layer with `ModelLayerRegistry.registerModelLayer(location, Model::createBodyLayer)` during client init,
  then `ctx.bakeLayer(location)`. `LivingEntityRenderState.walkAnimationPos` is the walk cycle. Model forward is -Z,
  and a root offset of y=24 puts the feet on the ground.
- **Villager look without a Villager:** `MobRenderer<MyMob, VillagerRenderState, VillagerModel>` with
  `new VillagerModel(ctx.bakeLayer(ModelLayers.VILLAGER))`, base texture `minecraft:textures/entity/villager/villager.png`,
  and `new VillagerProfessionLayer<>(this, ctx.getResourceManager(), "villager", noHat, noHatBaby)` (both
  `new VillagerModel(ctx.bakeLayer(ModelLayers.VILLAGER_NO_HAT))`). In `extractRenderState` set
  `state.villagerData = new VillagerData(typeHolder, professionHolder, level)`, with holders from
  `level.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE / VILLAGER_PROFESSION).getOrThrow(VillagerType.PLAINS / VillagerProfession.ARMORER)`.
- Arm poses: override `getArmPose(mob, arm)`. Return `BOW_AND_ARROW` while aggressive with a bow; for a
  combat stance use `ITEM` for the weapon arm and `BLOCK` for a shield arm. Swinging comes from `mob.swing()`
  (the melee goal calls it). Add a hit sound (`SoundEvents.PLAYER_ATTACK_STRONG`) so attacks read clearly.

## World-saved data

- Use **Fabric data attachments** on a `ServerLevel`:
  `AttachmentRegistry.create(id, b -> b.persistent(CODEC).initializer(() -> EMPTY))`, then
  `level.getAttachedOrCreate(TYPE)` / `level.setAttached(TYPE, value)`.
  Store immutable records and replace them. For big mutable sets, wrap the same set in a *new* holder
  object when calling `setAttached`, so the change is flagged.
- Global (per-server) data: put it on `server.overworld()`.
- Vanilla `SavedDataType` needs a `DataFixTypes`, and passing null crashes on load, so avoid it.
- **Map keys must encode as strings** (NBT compound keys): `Codec.unboundedMap(UUIDUtil.STRING_CODEC, ...)`, never
  `UUIDUtil.CODEC` (an int array). A wrong key codec makes the *whole attachment* fail to save ("Not a string") and it
  is silently gone after a reload. Game tests never reload, so round-trip the codec with
  `codec.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), value)` (`Run.roundTrip`).

## Structures (NBT templates)

- **Chunk loading before you build or read the ground:** `level.getHeight(Heightmap...)` on a chunk that isn't
  loaded returns `getMinY()` (teleports into the void). `level.isLoaded(pos)` is true for border chunks whose
  entities don't tick (mobs added there freeze or aren't added). Check `level.areEntitiesActuallyLoadedAndTicking(
  new ChunkPos(cx, cz))` for every chunk of the area before building and spawning guards; for a one-off (command,
  teleport) `level.getChunk(cx, cz)` loads it synchronously first. `MOTION_BLOCKING_NO_LEAVES` still counts logs:
  walk down past `BlockTags.LOGS` / `LEAVES` / replaceable plants for real ground. See `warbands/Ground.java`.
- **Container blocks with contents** in a template: give the `blocks` entry an `nbt` key, e.g.
  `{'id': 'minecraft:dispenser', 'Items': [{'Slot': 0, 'id': 'minecraft:arrow', 'count': 9}]}`
  (`ItemStackWithSlot` format: `Slot`, `id`, `count`).
- **Redstone in templates works** (verified by the `camps.relay` game test): `placeInWorld` with `UPDATE_ALL` calls
  each block's `onPlace` (so a block can `scheduleTick` itself), then runs `Block.updateFromNeighbourShapes` and
  `updateNeighborsAt` for every placed block. An **extended** sticky piston saved with its `piston_head`
  (`type=sticky`, `short=false`) stays out if something powers it (a lit `redstone_wall_torch`): placed before its
  torch it queues a retract, but `PistonBaseBlock.triggerEvent` re-checks the power and cancels. A redstone torch
  hanging on a block whose `getSignal` is on goes dark, so a signal-source block + torch is a free inverter.
  Avoid observers (they fire on placement). Dust connections are best avoided: put each piston or dispenser
  right next to its own signal block (`isSignalSource` + `getSignal` 15 powers every touching block).
  Dispensers also fire when the block **above** them is powered (quasi-connectivity), like pistons.
- **Container contents drop on `setBlock`**: `LevelChunk.setBlockState` runs `BlockEntity.preRemoveSideEffects`
  (which spills a dispenser's or chest's items) unless the flags include `Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS`
  (256). `UPDATE_SUPPRESS_DROPS` (32) only stops the block's own loot. A player break goes through
  `destroyBlock` → spills too: clear the container in a `PlayerBlockBreakEvents.BEFORE` listener first.
- **Chunk render layer is automatic**: `ChunkSectionLayer.byTransparency` picks solid/cutout/translucent from the
  texture's pixels, so a cube with see-through pixels (a grating) needs no render-layer registration, only
  `.noOcclusion()` on the block so its neighbours' faces still draw.
- **Air in a template** that your placer clears beforehand (the box above the floor) needn't be stored: dropping it
  took a 45x45x20 camp from 40k to 6k listed blocks.
- Files: `data/<id>/structure/<name>.nbt`, gzipped NBT: `size`, `palette` (`Name` + `Properties`),
  `blocks` (`pos`, `state`), `entities`, `DataVersion` 4790. `mods/warbands/tools/nbt.py` reads and writes
  them without dependencies. Blocks missing from the list = structure void (existing blocks stay).
  List `minecraft:air` explicitly to clear space.
- Place: `level.getStructureManager().get(id)` → `template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_ALL)`.
  Rotate in place: `new StructurePlaceSettings().setRotation(rot).setRotationPivot(new BlockPos(half, 0, half))`
  on an **odd square** footprint. The rotated local position is `StructureTemplate.calculateRelativePosition(settings, localPos)`.
- To restyle a building and keep its block entity, add a `StructureProcessor` and return null from `processBlock`
  for the core (and for containers). `getType()` can return `StructureProcessorType.NOP` when the processor is never saved.
  Origin of an existing building: `core.subtract(calculateRelativePosition(settings, localCore))`.
- Door facing the player: with the entrance on the south side, player facing NORTH → `NONE`, EAST → `CLOCKWISE_90`,
  SOUTH → `CLOCKWISE_180`, WEST → `COUNTERCLOCKWISE_90`.
- Placement UX that works: treat the **clicked block as the spot in front of the entrance** and build away from
  the player (centering on the click puts the player inside the area almost every time). Clear natural terrain
  (a block tag) and fill gaps under the floor. Show a particle outline (green fits, red blocked, white entrance),
  and name the blocking block in the error message.
- Anti-exploit: record every placed position and make those blocks drop nothing when broken
  (`PlayerBlockBreakEvents.BEFORE` → `level.destroyBlock(pos, false, player, 512)` → return false).
  Otherwise players mine buildings for more than the blueprint costs.
- A player-held "main" block (like the Town Center) can refuse a break: return 0 from `getDestroyProgress` unless
  `player.isShiftKeyDown()`, explain in `attack(...)` (called on left-click in survival), and add a
  `PlayerBlockBreakEvents.BEFORE` listener for creative's instant break and other players. Register it before
  the structure-drop listener, which destroys the block itself.

- **Natural generation** (data only): `worldgen/structure/<n>.json` (`"type": "minecraft:jigsaw"`, required: `biomes`,
  `spawn_overrides`, `step`, `start_pool`, `size` 0-20, `start_height`, `use_expansion_hack`, `max_distance_from_center`),
  `worldgen/template_pool/<n>/start.json` (`element_type` `minecraft:single_pool_element`), `worldgen/structure_set/<n>.json`
  (`random_spread`: `spacing`, `separation`, `salt`) and `tags/worldgen/biome/has_structure/<n>.json`. Copy vanilla
  `pillager_outpost`. Only new chunks generate it; find it with `/locate structure`.

## Block states cheat sheet

| Block | Property meaning |
|---|---|
| Stairs `facing` | Direction of the **tall back side**. A roof rising toward +z on its north edge uses `facing=south`. |
| Stairs `half` | `bottom` / `top` (upside down, e.g. arches) |
| Glass pane | Set `north/south/east/west` explicitly (true along the wall line), since templates don't recompute shapes. |
| Log `axis` | `y` for posts, `x` or `z` for beams along that axis |
| Standing banner `rotation` | 0 = faces south, 4 = west, 8 = north, 12 = east. Make it face **into** the room. |
| Wall banner `facing` | The direction it faces, away from its supporting wall. |
| Lantern `hanging` | true needs a block above it |
| Bed | Two entries, `part=head/foot`. `facing` points from foot to head. |
| Campfire | `lit=true` smokes. Put it on top of a chimney column. |
| Eternal fire | `minecraft:fire` (age 0, all sides false) on `magma_block` or `netherrack` (tag `infiniburn_overworld`) never burns out. Keep flammable blocks more than 1 block to the side and 4 above. Furnaces placed `lit=true` go out at once (no fuel). |
| Hanging lantern | `hanging=true` needs a block (or a chain) above it. Template placement doesn't check, so an unsupported one looks fine until the next block update drops it. |
| Iron chain | Renamed: `minecraft:iron_chain` (`axis`, `waterlogged`), not `chain`. |

## Tags

- 26.1 split some vanilla tags: `#minecraft:dirt` is only dirt, coarse dirt and rooted dirt. Grass, podzol and
  mycelium are `#minecraft:grass_blocks`; `#minecraft:substrate_overworld` covers dirt, mud, moss and grass.
  **Check the real tag file** before relying on it:
  `unzip -p .../minecraft-client.jar data/minecraft/tags/block/<tag>.json`.
- A tag entry that doesn't exist makes the **whole tag fail to load**, silently in practice. Test tags in-game.
- Folders are singular: `tags/block`, `tags/item`.

## Advancements, recipes and guidance

- A custom advancement tab is a root advancement with `display.background`
  (`minecraft:gui/advancements/backgrounds/stone`) plus children with `parent`.
- Steps that vanilla triggers can't detect: criterion `{"trigger": "minecraft:impossible"}` named `done`, awarded from code:
  `player.getAdvancements().award(server.getAdvancements().get(id), "done")`.
- Recipe book unlocks: `rewards.recipes`, or a `recipes/...` advancement with `inventory_changed` + `recipe_unlocked`.
- Translation strings take only `%s` and `%1$s`. A `%d` throws `TranslatableFormatException` and the line doesn't
  format: pass numbers as `%s`.
- Two `sendOverlayMessage` calls in a row: only the last shows (one action-bar line). Use chat for the second.
- The first-join gift pattern: `ServerPlayConnectionEvents.JOIN` + `player.addTag("mod.got_x")`, which returns false
  if the tag was already there.

## Events

`event.lifecycle.v1.ServerTickEvents`, `ServerLifecycleEvents`; `event.player.UseItemCallback`,
`UseBlockCallback`, `AttackEntityCallback`, `PlayerBlockBreakEvents`; `entity.event.v1.ServerLivingEntityEvents`;
`command.v2.CommandRegistrationCallback`; `loot.v3.LootTableEvents`; `biome.v1.BiomeModifications`;
`networking.v1.ServerPlayConnectionEvents`, `PayloadTypeRegistry`, `ServerPlayNetworking`;
client: `client.event.lifecycle.v1.ClientTickEvents`, `client.keymapping.v1.KeyMappingHelper`,
`client.rendering.v1.hud.HudElementRegistry`, `client.rendering.v1.EntityRendererRegistry`.

## Resource file paths

| What | Path |
|---|---|
| Item model definition (**every item needs one**) | `assets/<id>/items/<name>.json` → `{"model":{"type":"minecraft:model","model":"<id>:item/<name>"}}` |
| Item model | `assets/<id>/models/item/<name>.json` (`minecraft:item/generated` or `item/handheld`) |
| Block model / blockstate | `assets/<id>/models/block/`, `assets/<id>/blockstates/` (`orientable`, `cube_all`, `cube_column` parents) |
| Textures | `assets/<id>/textures/{item,block,entity,gui}/*.png` |
| Lang | `assets/<id>/lang/en_us.json`, `es_es.json` (always both) |
| Recipes | `data/<id>/recipe/*.json`. Ingredients are `"minecraft:x"` or `"#minecraft:tag"`; the result is `{"id", "count"}`. |
| Loot / tags / advancements / structures | `data/<id>/loot_table/`, `tags/`, `advancement/`, `structure/` (all singular) |
| Mod icon | `assets/<id>/icon.png` (referenced in fabric.mod.json) |

## Gradle and local environment gotchas

- The local JDK lives at `.tooling/jdk-25` (gitignored), and `./gradlew` uses it when `JAVA_HOME` isn't set.
- macOS has no `timeout` command. Long game runs can exceed a 10-minute tool call; run them in the background and poll the log.
- `runServer` needs the Minecraft EULA accepted in `run/eula.txt`. That's the user's decision, never write it yourself.
  Use `runDatagen` (headless boot) or client game tests instead.
- The "missing files ... build/resources/client", "Untranslated Item Tags", Realms and shader `UV1/UV2`
  log lines are harmless development noise.
- GitHub Actions on this account may be blocked by billing. A failed run in ~3s with "payments have failed" isn't a code problem.

## Audit pitfalls: loaded entities and item ownership

Verified against the local 26.1.2 sources during the 2026-09-30 bug audit:

- `Level.getEntity(UUID)` reads the visible entity lookup. A chunk-unloaded entity is removed from that lookup
  without dying (`PersistentEntitySectionManager.stopTracking/unloadEntity`). A null result is not proof
  of death; persist confirmed survivor/death state when conquest or population depends on it.
- `ItemEntity.getOwner()` resolves its **thrower**. It does not return the UUID set by `setTarget(UUID)`,
  which controls player pickup in `playerTouch`. A target-protected item may still have a null owner.
- `HopperBlockEntity.addItem(Container, ItemEntity)` transfers the stack without checking the item's pickup
  target or invulnerability. `setTarget`, `setUnlimitedLifetime` and `setInvulnerable` do not prevent hopper
  theft or direct `discard()`. Owner-bound pending rewards need a stronger delivery mechanism.
- `StructureTemplate.placeInWorld` writes the processed template cells with `level.setBlock`; it does
  not apply blueprint obstruction/ownership rules. Check the complete written footprint, including Y=0.

## Client-test saves and optional development bridges

- Verified with `javap` on Fabric client-gametest-api-v1 5.1.0: `TestSingleplayerContext.getWorldSave()` returns `TestWorldSave`; `getSaveDirectory()` identifies the saved world and `open()` returns a new `TestSingleplayerContext`. Close the first context before opening the saved one. The owning runner must keep later cleanup separate from the already-closed context. `release.reload` exercises the actual close/reopen path.
- The client game-test launcher deletes its run directory on startup, including any bridge jar/config put there beforehand. Keep opt-in jars outside it and load with `-Dfabric.addMods=<absolute jar path>`; verified in a live mcpfabric run. Loom 1.18.2 `RunConfigSettings.vmArg(String)` was checked by `javap`. Warbands enables this only with `-Pmcpfabric`; normal builds/package dependencies are unchanged.
- For MCP tests, read the token from the freshly generated game-test config; the ordinary dev client config uses a different run directory. Never log or put the token in project MCP JSON. Handbook body-height checks do not catch horizontally overflowing illustration captions; rendered screenshot bounds need a separate check.

### Testing notification clutter (26.1.2, verified 2026-10-01)

- Vanilla `DisplayInfo.CODEC` defines `show_toast` and `announce_to_chat` independently (both default true). Setting either false preserves advancement credit, display in the tree and rewards. Verified in the generated common sources.
- The ordinary Warbands `Run.cleanShot` clears chat, titles and toasts. Notification regressions must call `context.takeScreenshot` directly after delivery; otherwise their screenshots hide the failure.
- For test-only observation, verified client sources contain `ChatComponent.allMessages`, `Gui.title`, `ToastManager.visibleToasts` and `ToastManager.queued`. `GuiMessage.content()` is public (verified with javap). The feedback scenarios reflect those private fields only in the gametest source set; no access changes or mixins ship with the mod.

### Terrain cleanup and unrelated item drops (26.1.2, verified 2026-10-02)

- `Level.setBlock` runs neighbour-shape updates unless `Block.UPDATE_KNOWN_SHAPE` (16) is set. Its propagated flags strip `UPDATE_SUPPRESS_DROPS` (32), so adding that flag alone does not stop an unsupported neighbouring crop from dropping loot. Clear known terrain with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, then explicitly remove unsupported plants/leaves. Normal template placement still uses normal neighbour updates. Verified in the local `Level` and `Block.updateOrDestroy` sources.
- `Containers.dropContents` creates throwerless item entities for ordinary container contents. Apples, sticks, seeds, saplings, flowers, leaves and snowballs can be player property; neither item type nor entity age identifies cleanup provenance. Never discard nearby item entities to clean up construction debris. `release.cleanup` spills every former debris category using the vanilla container path; `release.terrain` checks actual House and camp-approach clearing for plant loot.
