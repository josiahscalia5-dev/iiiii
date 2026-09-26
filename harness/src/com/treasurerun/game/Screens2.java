package com.treasurerun.game;
/** HUD / camera on other screen shapes */
public class Screens2 {
    public static void main(String[] a) {
        int[][] sizes = {{1080, 1920}, {1440, 3200}, {1600, 2560}};
        for (int[] sz : sizes) {
            Sim s = new Sim(sz[0], sz[1], "build/harness/screens");
            s.g.startRun();
            s.run(3.2f);
            s.shot("r1_" + sz[0] + "x" + sz[1]);
        }
    }
}
