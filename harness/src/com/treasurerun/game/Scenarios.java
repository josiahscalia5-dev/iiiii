package com.treasurerun.game;

/** Scripted play-throughs for visual QA. Each writes numbered frames + log.txt to build/harness/<name>/ */
final class Scenarios {
    static void run(String name, Sim s) {
        Game g = s.g;
        switch (name) {
            case "intro": {
                g.startRun();
                s.film("intro", 2.4f, 0.3f);
                break;
            }
            case "walk": {
                g.startRun();
                s.run(2.2f);
                s.shot("start");
                // hold above-left of the boy: he walks towards the finger
                float bx = g.screenX(g.boy.x), by = g.screenY(g.boy.y);
                s.down(0, bx - 150, by - 700);
                s.film("hold", 1.6f, 0.2f);
                s.up(0, bx - 150, by - 700);
                s.film("release", 0.5f, 0.1f);
                break;
            }
            case "heist": {
                g.startRun();
                s.run(2.2f);
                s.tapWorld(g.room.heist.gemX, g.room.heist.gemY);
                s.shot("tapped");
                boolean ok = s.waitFor(() -> g.heistT >= 0, 12);
                System.out.println("steal started: " + ok + " boy=" + g.boy.x + "," + g.boy.y);
                s.film("steal", 1.8f, 0.1f);
                break;
            }
            case "guard": {
                g.startRun();
                s.run(2.2f);
                // walk straight up into the guard's patrol line
                float bx = g.screenX(g.boy.x), by = g.screenY(g.boy.y);
                s.down(0, bx + 60, by - 900);
                for (int i = 0; i < 40; i++) {
                    s.run(0.2f);
                    if (i % 2 == 0) s.shot(String.format("g%02d", i));
                    if (g.phase == Game.CAUGHT) break;
                }
                s.up(0, bx, by - 900);
                s.film("after", 2.4f, 0.3f);
                break;
            }
            case "exit": {
                g.startRun();
                s.run(2.2f);
                // cheat: steal directly, then teleport near the door and walk in
                g.heistDone = true;
                g.nav.block(370, 0, 690, 1170, true);
                g.nav.computeClearance();
                g.guards.clear();
                g.boy.place(520, 1380, 0);
                s.run(0.1f);
                float bx = g.screenX(g.boy.x), by = g.screenY(g.boy.y);
                s.down(0, bx, by - 600);
                for (int i = 0; i < 30 && g.phase == Game.PLAY; i++) { s.run(0.1f); s.move(0, g.screenX(g.boy.x), g.screenY(g.boy.y) - 600); }
                s.up(0, bx, by);
                s.film("exit", 1.3f, 0.1f);
                s.film("room2", 3.0f, 0.25f);
                break;
            }
            case "room2": {
                g.startRun();
                g.phase = Game.EXIT;
                g.phaseT = 2;
                g.nextRoom = 1;
                g.nextLoaded = true;
                s.step();
                s.film("r2", 2.6f, 0.4f);
                float bx = g.screenX(g.boy.x), by = g.screenY(g.boy.y);
                s.down(0, bx, by - 800);
                s.film("up", 3f, 0.5f);
                s.up(0, bx, by - 800);
                break;
            }
            case "caught": {
                g.startRun();
                s.run(2.2f);
                // walk to the middle of the guard's patrol line and stand in his way
                s.tapWorld(560, 1760);
                s.run(1.6f);
                s.shot("wait");
                for (int i = 0; i < 60 && g.phase != Game.CAUGHT; i++) {
                    s.run(0.15f);
                    if (i % 2 == 0) s.shot(String.format("c%02d", i));
                }
                s.film("caught", 2.4f, 0.2f);
                break;
            }
            case "walkviews": {
                // film the boy walking in 6 directions in Room 2 (open floor near the start), cropped around him
                g.startRun();
                g.phase = Game.EXIT; g.phaseT = 2; g.nextRoom = 1; g.nextLoaded = true;
                s.step();
                g.guards.clear();
                s.run(2.5f);
                g.guards.clear();
                float[][] dirs = {{0, -1}, {0.55f, -0.85f}, {1, 0}, {0.7f, 0.7f}, {0, 1}, {-1, 0}};
                StringBuilder sb = new StringBuilder();
                for (int d = 0; d < dirs.length; d++) {
                    g.boy.place(764, 2670, 0);
                    g.camReady = false;
                    s.run(0.1f);
                    float bx = g.screenX(g.boy.x), by = g.screenY(g.boy.y);
                    s.down(0, bx + dirs[d][0] * 500, by + dirs[d][1] * 500);
                    s.run(0.5f);
                    for (int f = 0; f < 10; f++) {
                        s.run(1 / 15f);
                        s.crop(String.format("v%d_%02d", d, f), g.boy.x, g.boy.y, 1.25f);
                        if (f % 3 == 0) System.out.println(String.format("dir %d f%d boy=%.0f,%.0f heading=%.0f view=%d mirror=%b move=%.2f walking=%b steerN=%d", d, f, g.boy.x, g.boy.y, Math.toDegrees(g.boy.heading), g.boy.view, g.boy.mirror, g.boy.moveAmt, g.walking, g.steerN));
                        s.move(0, g.screenX(g.boy.x) + dirs[d][0] * 500, g.screenY(g.boy.y) + dirs[d][1] * 500);
                    }
                    s.up(0, bx, by);
                    s.run(0.6f);
                    s.crop(String.format("v%d_stop", d), g.boy.x, g.boy.y, 1.25f);
                }
                break;
            }
            case "full": {
                // whole run by taps only, guards removed: proves A* reaches every objective and the flow completes
                g.startRun();
                s.run(2.2f);
                g.guards.clear();
                s.tapWorld(g.room.heist.gemX, g.room.heist.gemY);
                System.out.println("steal: " + s.waitFor(() -> g.heistDone, 15));
                s.run(1f);
                s.shot("r1_stolen");
                // tap the doorway (tap again if the path stops short)
                for (int k = 0; k < 4 && g.phase == Game.PLAY; k++) { s.tapWorld(g.room.doorX, g.room.exitY - 10); s.waitFor(() -> g.phase != Game.PLAY || !g.walking, 12); }
                System.out.println("exit1: phase=" + g.phase + " boy=" + g.boy.x + "," + g.boy.y);
                boolean in2 = s.waitFor(() -> g.roomIndex == 1 && g.phase == Game.PLAY, 8);
                System.out.println("in room 2: " + in2 + " " + g.debugState());
                g.guards.clear();
                s.shot("r2_start");
                for (int k = 0; k < 6 && g.phase == Game.PLAY; k++) {
                    s.tapWorld(g.room.doorX, g.room.exitY - 10);
                    s.waitFor(() -> g.phase != Game.PLAY || !g.walking, 20);
                    s.shot("r2_leg" + k);
                    System.out.println("r2 leg " + k + " boy=" + g.boy.x + "," + g.boy.y + " walkN=" + g.walkN);
                }
                s.waitFor(() -> g.phase == Game.DONE, 5);
                System.out.println("final phase=" + g.phase + " events=" + s.host.events + " coins=" + g.coins + " gems=" + g.gems);
                break;
            }
            default:
                throw new IllegalArgumentException("unknown scenario " + name);
        }
    }
}
