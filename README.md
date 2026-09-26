# Treasure Run — Android game (continuation project)

Cute, colorful cartoon museum **stealth/heist** game: a masked boy sneaks through museum rooms, steals treasure, avoids guards and escapes through the exit. Portrait, package `com.treasurerun.game`, **native Java with a custom Canvas engine** (no Unity/Godot, no Gradle).

## Where things stand (2026-09-26)
- **v0.5 "gameplay & guard AI" is built** (`scripts/build.sh 7 "0.5"`, versionCode 7) and handed to the owner to test. **Stop here until they report back.**
- `reference/TreasureRun-v0_4-rooms.apk` is the original v0.4 baseline (different signing key; see below).
- Nothing has been run on a physical phone from this sandbox. Everything below is verified in the Robolectric harness (real Android framework + real Skia rendering on the JVM).

## What v0.5 does
- **Controls (no joystick):** drag/swipe anywhere to steer (the finger's direction is the boy's direction on screen, drag length = sneak/walk speed, direction changes smoothly while held); a **quick swipe** walks a short distance that grows with the swipe length; **tap** a spot or a treasure to walk there (A*) and grab it. Pause, sprint (hold) and hand (grab) buttons kept. `Game.touch*`.
- **Walking characters (`Rig`, `Walker`):** painted upper bodies (legs removed) over procedural 3D-lite legs projected with the room perspective: alternating legs, planted feet (gait phase driven by distance), heel-toe roll, knee IK, hip bob/sway, counter-swinging arm, lean, eased start/stop, smooth turning (heading rotates; body view cross-fades back → ¾ → front-sneak, mirror turns flip).
- **Guards (`Guard`):** patrol routes with stops and look-arounds, flashlight cone clipped by obstacles (anything unwalkable blocks sight), `?` suspicion meter → `!` double-take (shocked front face, sting) → A* chase → lose sight / leave their leash area → search last known spot → return to patrol. Sprinting nearby is heard. Caught → CAUGHT! panel with REPLAY / HOME.
- **Treasure & exit:** coins/gems are grabbed on the move (reach anim, fly to the HUD counter, sparkles, sound). Room 1's diamond sits in its case and must be stolen; the exit is locked (padlock + hint) until the level's treasure goal is met, then an escape walk + iris → level-complete screen (title shows the right level number) → NEXT LEVEL.
- **Levels & difficulty (`Level`):** 6 levels; per-level guards, patrol/chase speed, sight range/angle, detection time, reaction time, memory, leash, treasure goal. Levels 1/3/5 = Diamond Corridor with 1/2/3 guards, 2/4/6 = Grand Hall with 2/3/4 guards (no new room art yet). Progress is saved (`SharedPreferences "progress"`); Play continues from the furthest level; unlocked doors ≥3 get a gold play badge on the level-select screen.
- **Life:** flickering lamps, blinking security-camera LED, dust in the lamp light, treasure sparkles, chase vignette, camera shake, haptics, synthesized SFX (`app/assets/sfx`).

## Build
```bash
bash tools/setup_tools.sh          # JDK 21, android.jar (API 35), dx, apktool, uber-apk-signer  (--art adds LaMa + U2Net for the art scripts)
bash scripts/build.sh 7 "0.5"      # -> build/TreasureRun.apk  (javac -source 8 -> dx --min-sdk 21 -> apktool b -> zipalign + v1/v2/v3 sign)
```
Signing uses the committed debug key `keystore/treasurerun-debug.jks` (alias/passwords `treasurerun`) so builds from any account update each other. v0.4 was signed with another key: uninstall it once before installing a build from this repo. Keep versionCode increasing (0.4.1 = 6, 0.5 = 7).

## Test harness (Robolectric)
```bash
harness/robolectric/run.sh                    # all tests; rebuilds the APK first when app/ changed
harness/robolectric/run.sh GameplayTest#level1CanBeWonBySneaking
```
Maven + Robolectric 4.14 (SDK 35, native graphics). Screenshots/contact sheets land in `build/harness/`.
- `GameplayTest` — controls (swipe up/down/left/right, flick distance, smooth direction change, accel/decel, gait vs distance), guards (patrol/stops/looks, suspicious → alert → chase → catch → retry, no sight through obstacles, escape → search → return), coins, tap-to-grab, locked exit → diamond → escape → complete, difficulty ramp, Level 2 playable, **Level 1 can be won by sneaking** (bot times the patrol, never alerts the guard).
- `FlowTest` — the real app: tap PLAY, real MotionEvent swipe on GameView, complete screen, NEXT LEVEL → level 2, saved progress, level-select door 3.
- `LaunchTest`, `LookTest`, `AnimSheetTest` (zoomed walk strips in 8 directions, start/turn/stop, guard walk), `RigPreviewTest`.

## Layout
| Path | What |
|---|---|
| `app/java/com/treasurerun/game/` | `Game` (gameplay, input, HUD, render; android.graphics only), `Boy`/`Guard`/`Walker` (actors), `Rig` (character drawing), `Nav` (walk mask, perspective, LOS, A*), `Level` (level table), `RoomData`/`Room`/`Occ` (rooms), `GameView` (thin View wrapper), `MainActivity` (screens, assets, SoundPool, progress), `ScreenView`/`FullBleed`/`LayoutData`/`Screens` (menus) |
| `app/assets/` | `rooms/` backgrounds (painted guards/beams removed, diamond out of its case), occluders, walk masks (1/4 res); `rig/` character bodies/arms; `sfx/`; `hud/`, `layers/` menus |
| `art/pipeline/` | `v05_assets.py` (rig parts, room cleanup, walk-mask fixes), `sfx.py`, `complete_titles.py`, `routes_check.py` (draws + validates patrol routes → `build/harness/routes_L*.png`), older scripts (`room1_clean.py`, `rooms2.py`, `build_boy.py`, `inpaint.py`, `matte.py`) |
| `art/wip/` | work-in-progress images and cut parts |
| `harness/robolectric/` | the test harness above |
| `reference/` | v0.4 APK |

## Notes for the next session
- Source was recovered from the v0.4 APK with jadx; 0.4.1 fixed decompiler bugs (see git history). If you touch old menu code, compare against `reference/` with apktool.
- Tuning knobs live in `Level` (difficulty), `Rig.BOY/GUARD` + the `Rig.View` constants (proportions), `Boy.update` (speeds/turn rates), `Game.touchMove/touchUp` (swipe feel).
- Pause returns to the main menu (v0.4 behaviour). The complete screen's "REWARDS" panel is painted art.
- Wait for the owner's feedback on v0.5 before adding anything else.
