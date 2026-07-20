# Changelog

All notable changes to Agua de Florida are documented here.

## 2.0.0 - 2026-07-20

The item is no longer a `WATER_BUCKET`, and the plugin no longer performs the
resurrection itself. Both changes are the same change: vanilla owns death protection now.

### BREAKING

- **Existing Agua de Florida items stop working.** A 1.x item is a `WATER_BUCKET` and is
  no longer recognised — the genuine-item check now requires `Material.POTION` as well as
  the persistent-data tag. No conversion code ships. Re-issue replacements with
  `/aguadeflorida give <player> [amount]`. There is no in-place migration and no
  compatibility shim; a half-recognised legacy item would be worse than a plainly dead one.

### Changed

- The item is a `POTION` instead of a `WATER_BUCKET`. The bucket was not cosmetic: right-
  clicking it placed a water source and turned the item into an empty bucket, silently
  destroying the player's save. The material is deliberately no longer configurable,
  because making it configurable would let an admin reinstate exactly that defect.
- **Right-click is now completely inert.** The `minecraft:consumable` component is unset,
  and drinking is a plain potion's only built-in right-click behaviour.
- **Vanilla performs the resurrection.** Since MC 1.21.2 death protection is driven by the
  `minecraft:death_protection` data component rather than by the Totem of Undying item
  type, so the item is placed on vanilla's real resurrection path. The sound, the status
  effects and the vanilla-style effect wipe are all declared on the component rather than
  applied by plugin code.
- Mob drop sources are now `WITCH` 8%, `EVOKER` 25% and `DROWNED` 1%. `VINDICATOR` was
  dropped: it shares raid content with the Evoker, so listing both doubled up on one source.
- Per-mob drop rates replace the single global rate. `mob_drops.mob_types` is now a mapping
  of mob to rate; the 1.x list form is still read, with every listed mob using
  `mob_drops.default_rate`.
- `onEnable` now verifies that `DEATH_PROTECTION` actually applied to the built item and
  disables the plugin with an ERROR if it did not. The data component API is experimental
  and Paper promises no cross-version compatibility, so a silent degradation into an item
  that no longer saves anyone is worse than a plugin that refuses to start. There is
  deliberately no fallback resurrection path — two of those risk double-firing.
- Added `item.color`, an RGB hex tint for the potion liquid, falling back to `#1E90FF`.

### Fixed

- Looting is now read from the weapon that dealt the killing blow rather than from whatever
  the killer happens to be holding when the death event fires. For anything fired the
  shooter has usually swapped by the time the projectile lands, and for a dispenser or a
  mob-fired arrow there was no held item to read at all.

### Removed

- `PlayerDeathListener`, the cancel-the-death-and-reimplement-it emulation layer. Its
  totem-item-swap animation hack was a **duplication exploit**: a player quitting inside the
  9-tick window logged out holding a genuine fabricated `TOTEM_OF_UNDYING`. Deleted rather
  than patched — the whole path is redundant now that vanilla resurrects.
- `CrossPlatformUtils`, ~190 lines of Bedrock offhand handling that existed only to support
  that listener. Vanilla's death-protection check scans both hands itself. The
  `softdepend: [floodgate]` that would have fixed its broken API probe was not added, since
  the dependency would have had no remaining consumer.
- **Crafting, entirely.** It was disabled by default, the advertised `recipe.pattern` and
  `recipe.ingredients` keys were never read by any code, and the hardcoded recipe consumed
  a `WATER_BUCKET` to produce a `WATER_BUCKET`.
- Config keys: `item.material`, `item.unbreakable`, the whole `recipe:` block,
  `cross_platform.auto_move_to_main_hand`, and `totem.restore_health`,
  `totem.show_animation`, `totem.play_sound`, `totem.consume_on_use` — the last four are
  all vanilla-controlled by the component now.

### Added

- An `EntityResurrectEvent` listener that is **observation only**. It never performs and
  never cancels a resurrection; it exists so a save can be logged (`debug.log_saves`) and
  announced (`messages.life_saved`). It null-guards `getHand()`, which is nullable because
  of the event's one-argument constructor, and confirms the item is genuinely Agua de
  Florida so a vanilla Totem of Undying save is not reported as one. Nothing depends on it:
  if the event never fires for a custom death-protection item, the item still saves players
  and only the logging and messaging are absent.

## 1.1.3 - 2026-07-20

Bug-fix release. No behaviour redesign; the item is still `WATER_BUCKET`-based.
The potion rewrite lands in 2.0.0.

### Fixed

- `/agua give <player>` now finds Bedrock players. Floodgate joins a Bedrock account
  under a prefixed Java-side username — `.acarm` for a player who calls themself
  `carm` — using the `username-prefix: "."` default from Floodgate's shipped config.
  `getPlayerExact` is an exact match and never saw the prefixed name, and
  `Server#getPlayer` prefix-matches the *name*, so `getPlayer("carm")` did not find
  `.acarm` either: that name starts with a dot. Lookups now go through
  `PlayerLookup.resolve`, which tries the bare name, then the `.`-prefixed form, then
  a case-insensitive sweep that also covers a server which reconfigured the prefix.
  Exact matches still win over everything, so the 1.1.2 fix against `/agua give Car`
  resolving to `Carmelo123` is preserved.
- The "player not found" message now lists who is actually online. Bedrock players get
  no tab completion at all — Geyser bakes the command tree into one login packet and
  never sends suggestion packets — so this message is the only channel through which a
  player discovers their own prefixed username.

## 1.1.2 - 2026-07-19

Bug-fix release. No behaviour redesign; the item is still `WATER_BUCKET`-based.
The potion rewrite lands in 2.0.0.

### Fixed

- `/agua give <player> <amount>` no longer silently deletes items. The return value
  of `addItem` was discarded, so anything that did not fit was destroyed; leftovers
  are now dropped at the player's feet. Amounts are handed out one stack at a time,
  so `/agua give someone 64` yields 64 items instead of 1 on a max-stack-1 material.
- `/agua give` now resolves players with `getPlayerExact`. `Server#getPlayer` does
  prefix matching, so `/agua give Car` could hand the item to `Carmelo123`.
- The life-saved message rendered literal `&e` colour codes. It converted `§` back to
  `&` and then passed the result to `Component.text`, which does no legacy parsing.
- `/agua give` messages rendered the configured item name as literal `§6§l` codes.
- Item name and lore rendered italic, overriding the configured formatting.
- An item whose persistent-data tag was explicitly `false` was accepted as genuine;
  the check tested key presence rather than the stored value.
- `/agua reload` did not refresh the cached item, so the give command and mob drops
  kept serving an item built from pre-reload configuration.
- `/agua reload` could not rebuild the crafting recipe. `unregisterRecipe` reset its
  registration flag inside the `try`, so a throw from `removeRecipe` left the flag
  stuck and pinned the recipe to a stale result item until restart.
- Mob-drop rates could go negative and are now clamped. Clamping the product alone
  was not enough: a negative `drop_rate` times a negative `looting_multiplier`
  produces a positive result, which would have meant a guaranteed drop on every kill.
  Inputs are clamped before multiplying, and out-of-range values are corrected and
  warned about at config load time.
- Invalid potion-effect configuration could abort the entire config load. A negative
  duration or amplifier makes `PotionEffect` throw; both are now clamped.
- The mob-drop handler no longer runs on player deaths. `PlayerDeathEvent` extends
  `EntityDeathEvent`, so it received every player death; it now also honours
  `ignoreCancelled`.
- `onEnable` no longer throws an unhelpful `NullPointerException` if the command
  block is missing from `plugin.yml`.

### Changed

- Build now emits a single Java 25 target. `maven.compiler.release` was silently
  overriding a contradictory `source`/`target` pair of 21.
- Removed `maven-shade-plugin`; every dependency is `provided` or `test` scope, so it
  shaded nothing on each build.
- Added the first unit tests (36), covering the stack-splitting arithmetic, drop-rate
  clamping, and configuration validation.

### Known issues

- Floodgate detection in `CrossPlatformUtils` does not work: `plugin.yml` declares no
  `softdepend`, so the API probe always fails and the code falls back to a username
  heuristic that depends on an operator-configurable prefix. Deliberately not fixed —
  the entire class is removed in 2.0.0, which would leave the dependency unused.
- The totem animation temporarily places a real `TOTEM_OF_UNDYING` in the player's
  hand, which can be duplicated by quitting inside the 9-tick window. Deliberately not
  patched: the guard that would narrow the window turns the race into a reliable dupe,
  and the listener is deleted in 2.0.0.

## 1.1.1 - 2026-07-19

### Fixed

- SHA256SUMS.txt now records bare JAR filenames instead of the build-time
  `target/` path, so `sha256sum --check` works against downloaded release assets.

## 1.1.0 - 2026-07-13

### Changed

- Updated the build baseline to Paper 26.1.2 and Java 25.
- Updated Maven compiler and shading plugins for Java 25 bytecode.
- Added GitHub Actions for tests, release JARs, SHA-256 checksums, and tagged releases.
- Verified plugin startup and command registration on the current server stack.

### Tested

- Paper 26.1.2 build 74
- Geyser 2.11.0
- Floodgate 2.2.5 build 138
- ViaVersion 5.11.0
