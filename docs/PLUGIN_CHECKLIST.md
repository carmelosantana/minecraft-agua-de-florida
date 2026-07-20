# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `Agua de Florida`
- Slug: `agua-de-florida`
- Repository: `carmelosantana/minecraft-agua-de-florida`
- Owner: `Carmelo Santana`
- Target version: `2.0.0` (preceded by `1.1.2`, a bug-fix-only patch — see Release plan)
- Paper version: `26.1.2 build 74`
- Java version: `25`
- Updater destination: `agua-de-florida.jar`
- External services: `none`
- Status: `active`
- Autonomy: `autonomous`

Maven `artifactId`: `agua-de-florida`. `plugin.yml` name: `AguaDeFlorida`. Releasable JAR: `agua-de-florida-<version>.jar`.
Current released version at time of planning: `v1.1.1` (deployed, updater-enrolled, matrix PASS).

## Release plan

This gate covers two sequential releases. They are planned together because the bug backlog was
found during the same review, but they ship separately.

**`1.1.2` — bug fixes only, no behaviour change.** Low risk, independently valuable, no migration.
Lands first specifically so `/agua give` is correct *before* it is needed to re-issue items after
the 2.0.0 hard break.

**`2.0.0` — the item change.** Breaking: legacy `WATER_BUCKET` items stop being recognised.
Major version is required by semver.

## 1. Scope

- [x] Status is explicitly recorded as active, experimental, or excluded.
- [x] Purpose, commands, events, permissions, configuration, persistence, and acceptance checks are defined.
- [x] Known limitations and any intentionally withheld gates are recorded.

### Player-facing purpose

Agua de Florida is a rare, spiritually-themed item that saves a player from death exactly like a
Totem of Undying: hold it in either hand and, instead of dying, you are restored with a burst of
effects and a shattering-glass sound. It is obtained as a rare mob drop.

### What changes in 2.0.0, and why

The item was built on `Material.WATER_BUCKET`. Right-clicking a bucket places a water source block
and converts the item into an empty `BUCKET` — so the player silently loses their save by using it
the way any player naturally would. This was the motivating defect.

`Material.POTION` replaces it. Plain `POTION` has exactly one built-in right-click behaviour —
drinking — and that behaviour is gated on the `minecraft:consumable` data component. Removing the
component makes right-click completely inert: no drink, no pour, no place, no throw, and no event
handling required. `SPLASH_POTION` and `LINGERING_POTION` were rejected because throwing is
hardcoded in the item class and survives component removal; `GLASS_BOTTLE` was rejected because it
fills from water sources, reproducing the bucket's failure mode.

The item is intentionally **droppable and tradeable**. The original requirement of
"not droppable" was a description of the bucket's *spilling*, not of item dropping. No
`PlayerDropItemEvent` or `InventoryClickEvent` handling is needed or wanted.

### Architecture change: vanilla performs the resurrection

Since Minecraft 1.21.2, death protection is driven by the `minecraft:death_protection` data
component rather than by the Totem of Undying item type. Applying
`DataComponentTypes.DEATH_PROTECTION` to a custom item puts it on vanilla's real resurrection path.

This deletes, rather than fixes, the entire emulation layer:

- `PlayerDeathListener` — the cancel-the-death-and-reimplement-it path, including a totem-item-swap
  animation hack that was a duplication exploit (a player quitting inside its 9-tick window logged
  out holding a genuine fabricated `TOTEM_OF_UNDYING`).
- `CrossPlatformUtils` — ~190 lines of Bedrock offhand handling that exists only to support that
  listener. Vanilla's death-protection check scans both hands itself.

All effects are declared on the component rather than applied by plugin code, via
`DeathProtection.deathProtection().addEffects(List<ConsumeEffect>)`:

| Behaviour | Declared as |
| --- | --- |
| Glass-breaking sound | `ConsumeEffect.playSoundConsumeEffect(Key)` — `minecraft:block.glass.break` |
| Regeneration / Absorption / Fire Resistance | `ConsumeEffect.applyStatusEffects(List<PotionEffect>, float)` |
| Vanilla totem-style effect wipe | `ConsumeEffect.clearAllStatusEffects()` |

### Verified API constraints

Confirmed by disassembling `paper-api-26.1.2.build.74-stable.jar` during planning. Recorded here so
gate 4 does not re-derive them or trust contradictory secondary sources.

- `EntityDeathEvent` **does** implement `Cancellable`; `PlayerDeathEvent extends EntityDeathEvent`,
  so `PlayerDeathEvent` **is** cancellable.
- `PlayerDeathEvent` has **no** `getReviveHealth` / `setReviveHealth`. It does not exist in this
  jar. The class has 8 constructors. (A research pass asserted this method exists — it does not.
  Do not write code against it.)
- `DataComponentTypes.DEATH_PROTECTION`, `.CONSUMABLE`, `.FOOD`, `.MAX_STACK_SIZE`,
  `.POTION_CONTENTS`, `.TOOLTIP_DISPLAY` all exist.
- `ItemStack` has `setData(Valued<T>, T)`, `setData(Valued<T>, DataComponentBuilder<T>)`,
  `setData(NonValued)`, `unsetData(DataComponentType)`, `resetData(DataComponentType)`.
  Use `unsetData` for `CONSUMABLE` — `resetData` restores the item type's prototype value and would
  make the potion drinkable again.
- `ConsumeEffect` static factories: `playSoundConsumeEffect(Key)`,
  `applyStatusEffects(List<PotionEffect>, float)`, `clearAllStatusEffects()`,
  `removeEffects(RegistryKeySet<PotionEffectType>)`, `teleportRandomlyEffect(float)`.
- `EntityEffect.PROTECTED_FROM_DEATH` exists. `EntityEffect.TOTEM_RESURRECT` still exists but is
  deprecated-for-removal since 1.21.2 — use the former if an explicit visual is ever needed.
- `ItemFlag.HIDE_ADDITIONAL_TOOLTIP` exists; `HIDE_POTION_EFFECTS` is **absent** from this jar.
- `PotionMeta.setColor(Color)` exists.
- `org.bukkit.Sound` is an **interface** (`extends OldEnum<Sound>, Keyed, adventure Sound.Type`),
  not a Java enum. `Sound.X` field references compile, but `EnumSet<Sound>`, `EnumMap<Sound,…>`,
  and switch-on-enum do **not**. Prefer Adventure `Key` / registry lookups.
- The data component API is `@ApiStatus.Experimental`; Paper does not promise cross-version
  compatibility.

### Commands

Unchanged from 1.1.1.

| Command | Arguments | Who |
| --- | --- | --- |
| `/aguadeflorida give` | `<player> [amount]` | `aguadeflorida.give` |
| `/aguadeflorida reload` | — | `aguadeflorida.reload` |

### Events

| Event | Purpose | Release |
| --- | --- | --- |
| `EntityDeathEvent` | Mob drop rolls. Must guard against `PlayerDeathEvent` (a subclass) and use `ignoreCancelled = true`. | existing |
| `EntityResurrectEvent` | **Observation only** — logging saves and player messaging. Never used to perform the resurrection. | 2.0.0 |
| `PlayerDeathEvent` | **Removed in 2.0.0.** Vanilla handles resurrection. | removed |

### Permissions

Unchanged: `aguadeflorida.give` (op), `aguadeflorida.reload` (op).

### Configuration

Retained: `item.name`, `item.lore`, `item.enchanted`, `totem.effects.*`, `mob_drops.*`,
`messages.*`, `debug.*`.

Added in 2.0.0: `item.color` (RGB hex for the potion liquid tint).

Removed in 2.0.0:
- `item.material` — the material is now load-bearing, not configurable. `POTION` is what makes
  right-click inert; allowing an admin to set `WATER_BUCKET` would reintroduce the original defect.
- The entire `recipe:` block — crafting is being removed (see below).
- `cross_platform.auto_move_to_main_hand` — the layer it configured is deleted.
- `totem.restore_health`, `totem.show_animation`, `totem.play_sound`, `totem.consume_on_use` —
  all now vanilla-controlled by the component.

**Crafting is removed entirely.** It was disabled by default, the advertised `recipe.pattern` and
`recipe.ingredients` keys were never read by any code, and the hardcoded recipe consumed a
`WATER_BUCKET` to produce a `WATER_BUCKET`. Both the code and the dead config block go.

**Stack size stays at 1** (vanilla `POTION` default, matching Totem of Undying). `MAX_STACK_SIZE` is
deliberately not set. `/agua give <player> <n>` must therefore loop into separate inventory slots.

### Persistence

PDC key `agua_de_florida` (`PersistentDataType.BOOLEAN`) on the ItemStack. No files, no database.

`isAguaDeFloridaItem` must change from `has(key, BOOLEAN)` to
`Boolean.TRUE.equals(get(key, BOOLEAN))` — `has` only tests presence, so an item explicitly tagged
`false` currently passes. In 2.0.0 it must additionally require `Material.POTION`, which is what
makes the hard break work.

### Dependencies

Hard: none. Soft: none.

`softdepend: [floodgate]` is **not** being added. It was going to be, to fix `CrossPlatformUtils`
(the class can never see `FloodgateApi` without it, so the Floodgate probe always fails and the
code always falls through to a name heuristic). But since that whole class is being deleted, the
dependency has no remaining consumer.

### External integrations

`none`. No Ollama, no Umami, no outbound network calls.

### Migration

**Hard break with manual re-issue.** Legacy `WATER_BUCKET`-based items stop being recognised in
2.0.0; no conversion code ships. Replacements are issued with `/aguadeflorida give`.

This is why `1.1.2` must land first: `/agua give` currently discards the return value of
`addItem`, so overflow items are **silently deleted**, and its `containsAtLeast` branch assumes
stacking room on a max-stack-1 material. Re-issuing with the buggy command would destroy items.

### Acceptance checks

**1.1.2**

1. Death message renders with colours, not literal `&e` codes.
2. `/agua give` messages render the item name with colours, not literal `§6§l`.
3. `/agua give <player> 5` yields 5 items across 5 slots with none lost; a full inventory drops the
   remainder at the player's feet rather than deleting it.
4. `/agua give <partial-name>` does not resolve to a different player by prefix match.
5. `/agua reload` visibly changes the item produced by a subsequent `/agua give`.
6. An item tagged `false` on the PDC key is not treated as Agua de Florida.
7. Item name and lore render non-italic.
8. Mob drop rate is clamped to `[0.0, 1.0]` for any config value including negatives.
9. `EntityDeathEvent` handler does not run for player deaths.
10. `mvn clean verify` passes with tests present.

**2.0.0**

11. Right-clicking the item — ground, water, air, block face, main hand and offhand — does nothing:
    no drink animation, no water placed, no throw, no consumption.
12. Dying with the item in the **main hand** resurrects the player.
13. Dying with the item in the **offhand** resurrects the player.
14. The glass-break sound plays on resurrection.
15. Regeneration, Absorption, and Fire Resistance are applied on resurrection, matching config.
16. Exactly one item is consumed per resurrection; the player cannot avoid consumption by swapping
    hands or scrolling the hotbar.
17. No death message, no inventory drop, and no death statistic is recorded on a save.
18. A legacy `WATER_BUCKET` Agua item does **not** resurrect (hard break is real).
19. The item can be dropped, picked up, traded, and stored in a chest normally.
20. A Bedrock client via Geyser sees the custom name, lore, and potion colour, and is resurrected.
21. The plugin logs an ERROR and disables itself if `DEATH_PROTECTION` fails to apply at startup.

### Known limitations

- **Data component API is experimental.** Paper does not promise cross-version compatibility. The
  chosen posture is *component only, fail loudly*: verify at enable time that the component applied,
  and if it did not, log an ERROR and disable the plugin. No `EntityResurrectEvent` fallback path —
  two resurrection paths risk double-firing, and a silent degradation to "item no longer saves you"
  is worse than a plugin that refuses to start.
- **Resurrect animation shows the item's own model.** Vanilla renders the held death-protection
  item, so players see the potion spin rather than a totem. Intended, but a visible change.
- **Legacy items are abandoned.** Anyone holding a `WATER_BUCKET` Agua after 2.0.0 loses it with no
  in-game signal. Mitigated by announcement plus manual re-issue, not by code.
- **Mob drop looting source.** Looting is read from the killer's main hand when
  `EntityDeathEvent` fires, not from the damage source, so bow kills usually read the wrong item.
  To be corrected as part of the mob-drop rework.
- **Mob list and rates are being reworked but not yet specified** — see Open decisions.

### Mob drops rework — confirmed

Previous: `WITCH`, `EVOKER`, `VINDICATOR` at 5% base with a 0.5×/level looting multiplier.

Confirmed replacement, keeping the spiritual-cleansing theme and adding a water-themed source
now that the item is a potion rather than a bucket:

| Mob | Rate | Rationale |
| --- | --- | --- |
| `WITCH` | 8% | Thematic primary source; potion-brewing mob suits a potion item. |
| `EVOKER` | 25% | Rare, dangerous, already drops a Totem — a natural high-value source. |
| `DROWNED` | 1% | Common water-themed trickle so the item is obtainable without raid farming. |
| `VINDICATOR` | *drop* | Redundant alongside Evoker within the same raid content. |

Looting multiplier unchanged at 0.5×/level, clamped to `[0.0, 1.0]`.

### Runtime verification required at gate 7a

These cannot be settled from documentation and must be confirmed on a live server. Acceptance
checks 12, 13, 16, and 20 exist specifically to cover them.

1. Does `DEATH_PROTECTION` on a non-totem item trigger from **both** main hand and offhand?
   (Paper's `checkTotemDeathProtection` scans both in server source, but this is not in the javadoc.)
2. Does `EntityResurrectEvent` fire uncancelled with a non-null `getHand()` for the custom item?
   This is the observation hook for save logging and messaging — if it does not fire, that logging
   has no home and the limitation must be recorded.
3. Does vanilla **consume** the component-bearing item automatically, or must the plugin shrink the
   stack? (Vanilla's shrink is null-guarded on a real totem; behaviour for a custom item is
   unconfirmed.)
4. Do Geyser/Bedrock clients render the custom potion colour and the resurrect animation correctly?

## 2. Repository

- [x] Repository is `carmelosantana/minecraft-agua-de-florida` with an SSH `origin` and `main` branch.
- [x] Existing user-owned worktree changes were identified and preserved. Working tree was clean at planning time.
- [x] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation. Cleared in the 1.1.0 identity migration.

## 3. Metadata

- [x] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent.
- [x] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present.
- [x] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented.
- [x] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented.
- [x] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent.
- [x] No secrets committed in source, defaults, tests, logs, history, or documentation.

Gates 2 and 3 were satisfied by the existing released plugin; `minecraft-plugin-scaffold` is not
re-run. `pom.xml` cleanup is deferred to gate 6 (see below), as it affects the build rather than
identity metadata.

## 4. Compatibility

- [x] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '1.21'`. Verified for `1.1.2`: `mvn clean verify` green, embedded `plugin.yml` dumped from the JAR shows `version: '1.1.2'`, `api-version: '1.21'`, main class `org.xpfarm.aguadeflorida.AguaDeFloridaPlugin`.
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared. None; `softdepend: [floodgate]` considered and rejected — see Dependencies.
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior. For `1.1.2`: all four enable together on a live stack (evidence in §7). `1.1.2` introduces no new player-facing interaction — the fixes are message rendering, item distribution arithmetic, and config validation. **The Bedrock questions that matter are 2.0.0's** (custom potion colour, resurrect animation) and are recorded under Runtime verification in §1.

## 5. External services

- [x] External integrations are disabled by default or require explicit configuration and have bounded timeouts. No external integrations.
- [x] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable. Not applicable.
- [x] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets. Not applicable.

## 6. Tests and build

- [x] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable. **36 tests added — the repository had none.** See coverage note below.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds. `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`, run against the settled tree after all concurrent edits landed.
- [x] The releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded. Exactly one JAR (`agua-de-florida-1.1.2.jar`); class entries are `org/xpfarm/aguadeflorida/**` only — no bundled Paper/Bukkit, no leaked files. `original-*` cannot occur now that shading is removed.

`pom.xml` issues — **resolved in 1.1.2**:
- `maven.compiler.release=25` silently overrode `<source>21</source><target>21</target>`. The
  `source`/`target` block is removed; the property is the single source of truth.
- `maven-shade-plugin` removed — every dependency is `provided`/`test` scope, so it shaded nothing.
  Confirmed against `.github/workflows/build.yml` first: CI filters `original-*` but never requires
  shading, so removal is safe and eliminates the `original-*` risk entirely.

### Test coverage note — what is and is not covered, honestly

There is no MockBukkit dependency and none was added. `ItemStack` construction calls
`Bukkit.getItemFactory()` and event objects need a live server, so most of this codebase is not
unit-testable as written. Rather than fake a server, the pure logic was extracted and tested
directly:

- `AguaCommand.splitIntoStacks(amount, maxStackSize)` — 9 tests including an exhaustive 64×64 sweep
  asserting the split always sums back to the requested amount.
- `MobDeathListener.computeDropRate(base, looting, multiplier)` — 12 tests including negatives,
  NaN, and both infinities.
- `ConfigManager.clamp(...)` plus a `YamlConfiguration` round-trip — 12 tests.
- `AguaItemBuilder.isTagPresentAndTrue(Boolean)` — 3 tests.

**Not unit-tested, requiring runtime verification:** the inventory-overflow drop path,
`getPlayerExact` resolution, rendered colour of deserialized names, non-italic item rendering, the
reload → cached-item → recipe-result cycle, and the `PlayerDeathEvent` guard on the mob-drop
handler. These are recorded rather than papered over.

## 7. Matrix

### 7a — single-plugin runtime verification (`1.1.2`) — PARTIAL

- [x] Paper, Geyser, Floodgate, and ViaVersion start successfully together. Verified on a
      fresh-volume Legendary stack, slot 0, ports 25600/19200. `Done (20.528s)!`, then:
      `AguaDeFlorida (1.1.2), Geyser-Spigot (2.11.0-SNAPSHOT), ViaVersion (5.11.0), floodgate (2.2.5-SNAPSHOT b138)`.
      `Enabling AguaDeFlorida v1.1.2` → `Agua de Florida v1.1.2 enabled!`. **Zero exceptions,
      severes, or load errors in the full startup log.** Stack torn down with `down -v`; lease released.
- [ ] Java and Bedrock smoke tests cover joins plus affected commands, events, permissions,
      persistence, and reloads. **NOT DONE — no client and no console channel available.** The
      container exposes no RCON and no `screen`, and writing to the server process's stdin is
      permission-denied, so not even a console-only `/agua reload` could be issued. This is a
      limitation of the harness, not a finding about the plugin. See follow-up below.
- [ ] Public deployment smoke tests verify `play.xpfarm.org` reaches the intended Java and Bedrock entry points. Belongs to gate 11, not this gate.
- [x] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable. Not applicable — no external integrations.

**What 7a proves for `1.1.2`:** the JAR loads, enables, and coexists with the cross-play stack
without error. **What it does not prove:** any of the 10 behavioural acceptance checks in §1, all of
which need a player. Those remain unverified at release time and are accepted as such for a
bug-fix release whose changes are covered by 36 unit tests over the exact arithmetic that was wrong.

**Follow-up (blocking for 2.0.0, not for 1.1.2):** enable RCON in `docker-compose.yml` so gate 7a
can actually exercise commands. 2.0.0 has four open runtime questions that *cannot* be answered
without driving a live server — whether `DEATH_PROTECTION` fires from the offhand, whether
`EntityResurrectEvent` fires for a custom item, whether vanilla consumes the item itself, and how
Geyser renders it. Shipping 2.0.0 on a harness that cannot run a command would mean shipping those
four unanswered.

Also fixed while setting this up: `docker-compose.yml` mounted `./target/agua-de-florida-1.0.0.jar`,
a hardcoded version that had not existed since `1.1.0`. Docker silently creates a *directory* at a
missing bind-mount source, so the plugin would never have loaded while the stack looked healthy —
meaning any prior "runtime verified" claim made through this compose file after `1.0.0` was not
testing the plugin at all. Now mounts `${XPFARM_PLUGIN_JAR}` to the version-free updater destination
name.

### 7b — ten-plugin ecosystem matrix — NOT RUN

- [ ] Fresh-volume Legendary stack test covers all ten updater-managed plugins.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately.

Out-of-band and **not a prerequisite for this release**, per the lifecycle. `1.1.2` changes no
updater manifest entry and adds no dependency. Worth running after `2.0.0`, which changes the item
model.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior. Present from 1.1.1.
- [ ] Successful main Actions run is recorded before tagging. Per release.
- [x] Workflow permissions contain no broader access than the documented contract.

## 9. Release

- [ ] Semantic version matches the POM, plugin metadata, and `v<version>` tag. Two releases: `v1.1.2` then `v2.0.0`.
- [ ] Successful tag Actions run and GitHub release are recorded.
- [ ] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
- [ ] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.

## 10. Updater

- [x] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin. Already enrolled; `asset_regex` `^agua-de-florida-[0-9].*\.jar$` matches both `1.1.2` and `2.0.0`. **No manifest change required.**
- [ ] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass. Re-verify on the `2.0.0` upgrade.
- [ ] Updater dry-run uses a disposable directory and never a production plugin directory.
- [ ] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.

## 11. Deployment

- [ ] Dokploy redeployment notes identify the full recreation used to rerun the one-shot updater.
- [ ] Updater completion, Minecraft startup, destination JAR, and stack/plugin logs were inspected.
- [ ] No production plugin hot reload was used.

**2.0.0 rollback trigger:** if the data component fails to apply on the production Paper build, the
plugin disables itself by design. Roll back to `v1.1.2` — which still contains the working
emulation path — rather than leaving players without a functioning item.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.

`CURRENT_STATE.md` must be updated twice — once per release — and must record the hard-break
migration note so anyone fielding "my Agua stopped working" has the answer.
