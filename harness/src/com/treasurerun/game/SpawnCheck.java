package com.treasurerun.game;
/** standing still at each room's spawn must never raise suspicion */
public class SpawnCheck {
    public static void main(String[] a) {
        for (int r = 0; r < 2; r++) {
            Sim s = new Sim(1080, 2340, "build/harness/spawn");
            s.g.startRun();
            if (r == 1) { s.g.phase = Game.EXIT; s.g.phaseT = 2; s.g.nextRoom = 1; s.g.nextLoaded = true; s.step(); }
            float max = 0;
            for (int i = 0; i < 60 * 30; i++) { s.step(); for (Guard q : s.g.guards) max = Math.max(max, q.sus); }
            System.out.println("room " + r + " max suspicion over 30s at spawn: " + max + " phase=" + s.g.phase);
        }
    }
}
