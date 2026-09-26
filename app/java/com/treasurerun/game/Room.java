package com.treasurerun.game;

/* loaded from: classes.dex */
final class Room {
    String[] bg;
    int[] bgY;
    float boyH;
    float[][] coins;
    float doorX;
    float doorY;
    float enterX;
    float enterY;
    float exitX0;
    float exitX1;
    float exitY;
    float feetAt;
    float feetR;
    float[][] gems;
    int h;
    float horizon;
    String label;
    String objective;
    Occ[] occ;
    float sFar;
    float spawnX;
    float spawnY;
    float speed;
    String title;
    float vFactor;
    float vPow;
    float viewW;
    int w;
    String walk;
    int walkDiv;
    float yRef;
    float zoomFar;

    // ---- v0.5 ----
    /** world rectangles {x0,y0,x1,y1} that are never walkable (on top of the walk mask) */
    int[][] blocks = new int[0][];
    GuardDef[] guards = new GuardDef[0];
    /** guard height in world px at depth 1 */
    float guardH;
    /** flashlight / vision colour (rgb) */
    int beamColor;
    Heist heist;
    /** objective shown until the heist is done (null = none) */
    String objectiveHeist;

    Room() {
    }

    float depth(float f) {
        return (f - this.horizon) / (this.yRef - this.horizon);
    }

    /** a guard's patrol: waypoints {x,y} (feet, world px) walked in a loop, with a look-around pause at each */
    static final class GuardDef {
        final float[][] route;
        final float[] pause;
        final boolean pingPong;

        GuardDef(float[][] route, float[] pause, boolean pingPong) {
            this.route = route;
            this.pause = pause;
            this.pingPong = pingPong;
        }
    }

    /** the room's big prize: a gem in a case that must be stolen before the exit opens */
    static final class Heist {
        /** gem centre inside the case (world px) and its drawn size */
        float gemX, gemY, gemSize;
        /** where the boy stands to reach it */
        float standX, standY;
        /** screen-tap radius around the gem (world px) */
        float tapR;
        /** background patch drawn once the gem is gone, and the occluder that swaps to its empty variant */
        String emptyPatch;
        int patchX, patchY;
        String occ, occEmpty;
        /** exit gate kept closed until the gem is taken {x0,y0,x1,y1} */
        int[] gate;
    }
}
