# Treasure Run — Android game (continuation project)

Cute, colorful cartoon museum **stealth/heist** game: a masked boy sneaks through museum rooms, steals diamonds, avoids guards and moves room to room. Portrait, package `com.treasurerun.game`, **native Java with a custom Canvas engine** (no Unity/Godot, no Gradle).

## Where things stand (2026-09-26)
- **Baseline:** `reference/TreasureRun-v0_4-rooms.apk` = v0.4 "rooms foundation", the last build from the previous Claude account. Install it to see exact v0.4 behaviour.
- **This repo:** full source recovered from that APK (jadx), fixed so it compiles *and runs* (0.4.1, see Known issues #1), a working no-Gradle build pipeline, and v0.5 art work in progress.
- **No v0.5 gameplay code has been written yet.** Next session starts at "Next steps" below.

## Known issues (read first)
1. **Fixed in 0.4.1 (2026-09-26):** builds from the recovered source hung forever on the Android launch splash (icon on dark background). Cause: jadx turned the hotspot→element search in the `ScreenView` constructor into `while (true)` with no exit, and the 9 level-select door hotspots match no element, so `onCreate` never returned. Also fixed: two int-division decompile errors (`GameView.updateCamera` camera scale, and HUD button size in `drawCircleSprite`), and `GameView.onTouchEvent` (joystick, sprint, hand and pause), rebuilt by hand from the v0.4 smali. The rebuilt dex now matches v0.4's arithmetic and calls method for method. To re-check after edits, decode both APKs with apktool and diff each method's `div-*`/`*-to-*`/`Math` ops.
2. **Signing:** `scripts/build.sh` signs with `keystore/treasurerun-debug.jks` (alias `treasurerun`, store/key password `treasurerun`). This debug key is committed on purpose so builds made from any account install as updates over each other. v0.4 was signed with a different key that isn't available, so **uninstall v0.4 once** before installing the first build from this repo.
3. `art/pipeline/rooms2.py` outputs (Room 2 guards/beams painted out, diamond removed from Room 1 case, guard side sprite) were produced at the end of a session and **have not been visually reviewed**. Check `art/wip/room2_final.png`, `r2_g1_clean.png`, `r2_g2_clean.png`, `case_empty.png`, `parts/guard_side_full.png` first.
4. `art/wip/parts/boy_tq_body.png` has a visible vertical seam where the arm was cut (`torso_x` in `art/pipeline/boy_cuts.py`); needs a fix.

## Build
```bash
tools/setup_tools.sh          # JDK 21, android.jar (API 35), dx, apktool, uber-apk-signer  (add --art for LaMa + U2Net models)
scripts/build.sh 7 "0.5"      # -> build/TreasureRun.apk  (0.4.1 shipped as versionCode 6; keep versionCode increasing)  (javac -source 8 -> dx --min-sdk 21 -> apktool b -> zipalign + v1/v2/v3 sign)
```
All tools come from GitHub-hosted URLs (the claude.ai sandbox blocks Google Maven / dl.google.com, so d8 is unavailable; `dx` from the dex2jar release is used instead).

## Layout
| Path | What |
|---|---|
| `app/java/com/treasurerun/game/` | Game source. `GameView` = gameplay (camera, movement, pickups, exit, HUD); `RoomData`/`Room`/`Occ` = room definitions; `MainActivity` = screens + asset cache; `ScreenView`/`FullBleed`/`LayoutData` = menus |
| `app/assets/` | **Original v0.4 assets** (HUD, menu layers, `rooms/` backgrounds, occluders, walk masks `roomN_walk.png` at 1/4 res, boy poses) |
| `app/apktool/` | Decoded manifest + resources (apktool 3.0.3); version is set by `scripts/build.sh` |
| `art/pipeline/` | Python art scripts: `room1_clean.py` (Room 1 guard+beam removal, LaMa inpainting), `rooms2.py` (Room 2 cleanup, diamond-out-of-case, guard sprite), `build_boy.py`+`boy_cuts.py` (boy body/arm parts), `inpaint.py` (LaMa ONNX), `matte.py` (U2Net cutouts), `seg_guard.py` (old GrabCut attempt, superseded) |
| `art/wip/` | All work-in-progress images incl. `parts/` (boy_*_body/arm, boy_meta.json, guard sprites, cleaned diamond case) and full-res room composites |
| `harness/shim/android/graphics/` | Java2D implementation of the android.graphics subset, so game rendering code can run on a desktop JVM and dump PNG frames for visual QA (no emulator available) |
| `keystore/` | Shared debug signing key |
| `reference/` | v0.4 APK |

## v0.5 scope (agreed with the owner — do nothing else until they test)
1. **Natural walk:** real walk cycle — alternating legs with heel-toe, counter-swinging arms, hip bob, lean, smooth start/stop/turn. Keep the existing painted boy (head, beanie, backpack, arm). Plan: procedural 3D-lite legs + shoes in matching jeans/red sneakers, painted body, painted arm rotated about the shoulder, phase driven by distance (no foot sliding), smoothed heading drives view choice (back / three-quarter / front-sneak, mirrored).
2. **Real guards:** painted guards + beams removed from backgrounds and re-added as live characters (same art): patrol → "?" suspicious → "!" alert (shocked front face) → chase → lose sight behind obstacles → search → return; can catch the boy. Vision cone in floor space, line of sight blocked by non-walkable cells, A* on the walk mask. **Caught = room restarts.** Room 1 guard patrols **across the middle of the corridor** (player must sneak past).
3. **Diamond:** Room 1 case diamond becomes a real object: sparkles when close, tap it (or hand button) → boy reaches, diamond flies to the HUD counter, case left empty. **Room 1 exit stays shut until it's stolen.** Room 2 floating gems stay walk-over pickups.
4. **Controls:** remove the blue joystick. **Hold anywhere → boy walks toward your finger** (left of him = left, right = right, above = forward); **quick tap → walks to that spot**; release → eases to a stop. Keep pause, sprint and hand buttons. No new joystick/d-pad.
5. **Free movement** through the room (already free 2D inside the walk mask; keep sliding collision).
6. **Room transition:** walk through the doorway (camera push + iris) and arrive walking in at Room 2's bottom entrance with a small name banner, instead of the black title card. Preload the next room.
- Preserve characters, museum art, colors, HUD, diamond style. No shops, skins, rewards, menus or extra levels.
- **Then STOP** and give the owner the APK to test on their phone.

## Findings from the v0.4 inspection
- Boy = 3 static images (`boy_back/tq/front.png`) frozen mid-stride; the old "walk" just bobs/tilts/squashes the whole image.
- All 3 guards (Room 1 ×1, Room 2 ×2) are painted into the backgrounds with their beams; `*_occ_guard*.png` are layering cutouts only; no guard logic existed.
- Room 1 diamond is painted inside the glass case; Room 1 has 6 coins, 0 gems; Room 2 has 6 walk-over gems.
- Perspective model: `depth(y) = (y-horizon)/(yRef-horizon)` scales size and speed; vertical speed uses `vFactor * depth^vPow`.
- Old transition: fade out → black title card → fade in.

## Next steps (in order)
1. Review/fix the unreviewed `rooms2.py` outputs and the TQ seam; write cleaned backgrounds/occluders into `app/assets/rooms/` (keep originals recoverable from `reference/`).
2. Move gameplay out of `GameView` (a View) into a `Game` class that uses only android.graphics, so it runs in the harness.
3. Implement walk rig, guards, diamond steal, touch controls, transition; QA with harness renders.
4. `scripts/build.sh 7 "0.5"`, hand APK to owner, stop.
