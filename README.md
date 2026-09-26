# Treasure Run — Android game (continuation project)

Cute, colorful cartoon museum **stealth/heist** game: a masked boy sneaks through museum rooms, steals diamonds, avoids guards and moves room to room. Portrait, package `com.treasurerun.game`, **native Java with a custom Canvas engine** (no Unity/Godot, no Gradle).

## Where things stand (2026-09-26)
- **v0.5 is built and waiting for the owner to test**: `releases/TreasureRun-v0.5.apk` (versionCode 5). Everything in the v0.5 scope below is implemented. **Do nothing else until the owner has played it.**
- Baseline for comparison: `reference/TreasureRun-v0_4-rooms.apk` (v0.4 "rooms foundation", the last build from the previous account).
- **Install note:** builds from this repo are signed with the committed debug key (`keystore/`), v0.4 was not. **Uninstall v0.4 once** before installing v0.5; later builds from this repo update in place.

## v0.5 scope (agreed with the owner) — status
1. **Natural walk** ✅ — painted body (head, beanie, backpack) per view + painted arm swung about the shoulder, over procedural "3D-lite" legs: IK knees, heel strike / toe-off roll, hip bob and sway, lean, counter-swinging arm, phase driven by distance walked (planted feet don't slide), eased start/stop, smoothed heading choosing back / three-quarter / front-sneak views (mirrored) with a quick turn squash. Sprint = longer, bouncier stride. Jeans + red sneakers colour-matched to the painting; the shoe tread shows when a foot lifts away from the camera.
2. **Real guards** ✅ — painted guards/beams removed from the backgrounds and re-added live with the same art: patrol → "?" suspicious (meter ring) → "!" alert (shocked front face) → chase (A* on the walk mask) → loses the boy behind obstacles → searches the last-seen spot → walks back to the route. Flashlight vision cone in floor space, raycast so it stops at obstacles; line of sight blocked by anything not walkable. A guard also senses a boy right next to him, and hears a sprinting one nearby. **Caught = "CAUGHT!", iris out, room restarts** (coins/gems of that room reset). Room 1's guard patrols **across the middle of the corridor**; Room 2 has one guard on each route around the crown.
3. **Diamond** ✅ — Room 1's case diamond is a live prize: sparkles + tap ring when close, **tap it (or the hand button)** → the boy walks over if needed, reaches up, the diamond arcs up into the HUD gem counter, the case is left empty (patch + empty occluder). **Room 1's exit stays shut until it's stolen** (walking up to it shows "Steal the diamond first!"). Room 2's floating gems are walk-over pickups.
4. **Controls** ✅ — blue joystick removed. **Hold anywhere → the boy walks toward your finger** (routes around obstacles when the straight line is blocked); **quick tap → walks to that spot** (A* path, tap marker); release → eases to a stop. Pause, sprint (hold) and hand buttons kept. A one-time hint explains this at the start of the first run.
5. **Free movement** ✅ — free 2D inside the walk mask with sliding collision, plus a small "flow around" when pushing into a corner.
6. **Room transition** ✅ — walk through the doorway (camera push-in + gold-rimmed iris), arrive walking in at the next room's bottom entrance with a small name banner. The next room is decoded in the background as soon as a room starts, so the doorway never waits.
- Characters, museum art, colours, HUD and diamond style preserved. No shops, skins, rewards, menus or extra levels were added.

## Build
```bash
tools/setup_tools.sh          # JDK 21, android.jar (API 35), dx, apktool, uber-apk-signer  (add --art for LaMa + U2Net models)
scripts/build.sh 5 "0.5"      # -> build/TreasureRun.apk  (javac -source 8 -> dx --min-sdk 21 -> apktool b -> zipalign + v1/v2/v3 sign)
```
All tools come from GitHub-hosted URLs (the sandbox blocks Google Maven / dl.google.com, so d8 is unavailable; `dx` from the dex2jar release is used instead). Because of `dx`, **no lambdas / method references in `app/java`** (min SDK 21, no desugaring).

## Desktop render harness (visual QA without an emulator)
`harness/shim/android/graphics/` implements the android.graphics subset on Java2D, so the real `Game` runs on the desktop JVM:
```bash
scripts/harness.sh Sim <scenario>     # frames + log.txt in build/harness/<scenario>/
scripts/harness.sh RigSheet           # boy rig contact sheet (views x walk phases)
scripts/harness.sh SpawnCheck         # standing at each spawn must never raise suspicion
scripts/harness.sh Screens2           # HUD/camera on 16:9, 20:9 and tablet screens
```
Scenarios (`harness/src/.../Scenarios.java`): `intro`, `walk`, `heist`, `guard`, `caught`, `search`, `exit`, `room2`, `walkviews`, `full` (whole game by taps, guards removed — must end with `finished`).

## Layout
| Path | What |
|---|---|
| `app/java/com/treasurerun/game/` | `Game` = all gameplay (phases, controls, camera, pickups, heist, HUD, transitions; android.graphics only) · `Boy` = movement state + walk rig · `Guard` = guard AI + drawing · `Nav` = walk mask, clearance, LOS, vision rays, A* · `Room`/`RoomData`/`Occ` = room definitions (guards, heist) · `GameView` = thin Android view (frames, multi-touch, insets) · `MainActivity` = screens + asset cache · `ScreenView`/`FullBleed`/`LayoutData` = menus |
| `app/assets/` | Game assets. `rooms/` holds the **cleaned** v0.5 backgrounds/occluders (guards and beams painted out), `boy_*_body/arm.png`, `guard_side/front.png`, `room1_case_empty.png`, walk masks `roomN_walk.png` (1/4 res) |
| `app/apktool/` | Decoded manifest + resources (apktool 3.0.3); version is set by `scripts/build.sh` |
| `art/pipeline/` | Python art scripts, all reading the **original v0.4 assets from the reference APK**: `clean_rooms.py` (guard/beam removal, empty case, occluder re-cut), `walk_masks.py` (reopen the floor where painted guards stood), `guards.py` (guard sprites), `build_boy.py`+`boy_cuts.py` (boy body/arm parts), `inpaint.py` (LaMa ONNX, 1:1 tiled or resampled), `matte.py` (U2Net) |
| `art/wip/` | Review sheets (`review_*.png`: before/after of each cleanup), guard matte checks, older WIP images |
| `harness/` | Java2D android.graphics shim + desktop harness (`Sim`, `Scenarios`, `RigSheet`, …) |
| `keystore/` | Shared debug signing key (alias `treasurerun`, passwords `treasurerun`) |
| `reference/` | v0.4 APK (source of all original art) |
| `releases/` | APKs handed to the owner |

## Notes / known limitations
- Tested only through the desktop harness (no emulator/device in this environment): gameplay logic, rendering and every scenario above are verified there; on-device feel (speeds, touch sizes, performance) is what the owner test is for.
- The Room 2 beam-2 area (right of the crown, on the carpet) is a rebuilt, slightly darker patch of carpet — the painted beam had saturated the red channel. Room 1 has some pre-existing soft blotches on the carpet from v0.4's own coin removal.
- Guards only have side and front art (no back view); a guard walking away uses the side sprite.
- `GameView.onTouchEvent` from v0.4 could not be decompiled; v0.5 replaces the whole input path, so nothing of it is needed any more.

## Next steps
1. Owner installs `releases/TreasureRun-v0.5.apk` (uninstall v0.4 first) and plays both rooms.
2. Collect feedback (walk feel, guard difficulty, control feel, speeds) and tune — the main knobs are in `RoomData` (guard routes/pauses, `visionRange`, `guardH`), `Guard` (speeds, suspicion rates) and `Boy.RIGS` (stride, lift, leg sizes).
