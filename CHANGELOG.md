# Changelog

All notable changes to Agua de Florida are documented here.

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
