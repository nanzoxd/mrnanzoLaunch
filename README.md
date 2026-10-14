# GlideLaunch

Safe aerial launch for new / respawning players on **Paper 1.21.11** (Java 21, Maven).
Custom velocity-based glide (no Elytra needed), directional firework-style particles,
double-sneak landing, optional persistent spawn-grace, and a full admin GUI.

## Build
Requirements: JDK 21, Maven 3.8+, internet access (to fetch paper-api from repo.papermc.io).

    mvn clean package

Output: `target/GlideLaunch-1.0.0.jar`

## Install
1. Copy the jar to `plugins/` on a Paper 1.21.11 server.
2. Start the server (generates `config.yml`; `grace-data.yml` appears when grace is used).
3. Stand where you want the spawn centre, run `/setspawn` (or use the GUI).
4. Open the GUI with `/glidelaunch`.

## Commands
| Command | Permission | Description |
|---|---|---|
| `/glidelaunch` (`/gl`, `/glaunch`) | glidelaunch.admin | Open the admin GUI |
| `/setspawn` | glidelaunch.admin | Save your location as the spawn centre |
| `/glidetest` | glidelaunch.admin | Test glide in place (nothing persisted) |
| `/launch test [player]` (alias `/lauch`) | glidelaunch.admin | Teleport to a validated safe launch zone and glide; tester is returned afterwards. `/launch` alone opens the player-selection GUI |

If another plugin owns `/setspawn` (e.g. Essentials) use `/glidelaunch:setspawn`.

## Permissions
- `glidelaunch.admin` (op) – everything administrative.
- `glidelaunch.bypass.grace` (false) – ignore grace PvP rules, only if `grace.pvp.bypass-permission-enabled: true`.
- `glidelaunch.use` (true) – required for automatic glides only if `glide.require-use-permission: true`.

## How it works (summary)
- **New players**: `PlayerJoinEvent` with `!hasPlayedBefore()` -> async chunk load of random candidates between min/max radius (uniform over the ring area, horizontal distance only) -> main-thread safety check -> `teleportAsync` high above the verified ground -> glide.
- **Safety checks**: world border, void, liquids, lava/fire/cactus/magma/etc., leaves, sky exposure (heightmap), minimum launch altitude, configurable fallback (`CENTER_AIR` / `NO_LAUNCH`).
- **Death**: valid bed/anchor respawns (`isBedSpawn`/`isAnchorSpawn`) are never touched. No saved point -> glide. Saved but unusable -> `post-death.unusable-saved-respawn` (GLIDE or VANILLA). End-portal "respawns" are ignored. A 5 s cooldown plus pending/scheduled sets stop join/death/respawn overlaps.
- **Physics**: one shared 1-tick task; velocity eases toward a target built from camera direction, speed (dive = faster, climb = slower), stall-dependent sink, A/D strafing, W/S speed. Vertical/horizontal speed caps. Never teleports. Collisions detected by expected-vs-actual movement; ground/water/lava/vehicle end the glide.
- **Sounds**: configurable per event under `sounds:` (first join, glide start, double-sneak, landing), toggle in Glide Settings.
- **Landing**: double sneak within `window-ms` -> `SLOW_FALL` (plugin-owned Slow Falling, refreshed until touchdown, removed only if we added it) or `NORMAL_LANDING`.
- **Grace**: disabled by default; wall-clock expiry timestamps in `grace-data.yml` (atomic async writes, flushed periodically and on shutdown). Pre-existing Saturation/Resistance are never removed and are restored (with remaining time) if we overrode a weaker one.

## Notes / limitations
- Movement is velocity-driven, so strict anti-cheat plugins may flag it; exempt glide sessions if needed.
- Players are not rendered in the elytra pose (no real Elytra is used by design).
- On quit/shutdown mid-air the player keeps a short un-owned Slow Falling so they do not die on rejoin.

## Testing checklist
1. [ ] Fresh player joins -> launched within min..max radius of centre, gliding.
2. [ ] Rejoin -> no launch.
3. [ ] Die with no bed -> launch after respawn.
4. [ ] Die with bed -> normal bed respawn, no launch.
5. [ ] Break bed then die -> follows `unusable-saved-respawn`.
6. [ ] Double-sneak -> glide ends, Slow Falling, removed on landing; pre-existing Slow Falling untouched.
7. [ ] Fly into wall / mountain -> glide ends, no damage.
8. [ ] Particles trail behind movement direction.
9. [ ] Change settings in GUI, restart -> persisted.
10. [ ] Fresh install -> grace disabled; enable in GUI -> effects + action bar.
11. [ ] Relog / restart during grace -> timer continues, not reset.
12. [ ] Protected player cannot hit others (melee, arrows, TNT); others cannot hit them.
13. [ ] Non-admin cannot open GUI or use commands.
14. [ ] Water/lava/void-only worlds -> fallback applied, no unsafe teleport.
15. [ ] Quit/death/world change mid-glide -> no leftover tasks or effects.
16. [ ] GUI: items cannot be taken, shift-clicked or dragged.
