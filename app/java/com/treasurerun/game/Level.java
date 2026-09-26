package com.treasurerun.game;

/**
 * Level table: which museum room, the guards (patrol routes) and the difficulty knobs.
 * Distances are in boy-heights on the floor, speeds in boy-heights per second, so they mean the same in every room.
 */
final class Level {
    /** A patrol waypoint: world position, how long to stop there and how far to look around while stopped. */
    static final class Wp {
        final float x, y, pause, look;
        Wp(float x, float y, float pause, float look) { this.x = x; this.y = y; this.pause = pause; this.look = look; }
    }
    static Wp wp(float x, float y) { return new Wp(x, y, 0, 0); }
    static Wp stop(float x, float y, float pause, float look) { return new Wp(x, y, pause, look); }

    final int number, room;
    final String objective;
    final Wp[][] guards;
    // difficulty
    final float patrolSpeed, chaseSpeed;   // boy-heights / s  (boy walks ~1.1, sprints ~1.8)
    final float sight, fov;                // vision range (boy-heights), half-angle (deg)
    final float detect;                    // seconds of clear view (mid range) until a guard is sure
    final float react;                     // "!" reaction before the chase starts
    final float lose;                      // seconds out of sight before the guard gives up the chase
    final float search;                    // seconds spent looking around the last known spot
    final float leash;                     // how far (boy-heights) a guard will chase away from his route
    final int need;                        // treasures that must be stolen before the exit opens
    final float[][] coins, gems;           // extra pickups {x, y, size}

    Level(int number, int room, String objective, int need, float patrolSpeed, float chaseSpeed, float sight, float fov,
          float detect, float react, float lose, float search, float[][] coins, float[][] gems, Wp[]... guards) {
        this.number = number; this.room = room; this.objective = objective; this.need = need;
        this.patrolSpeed = patrolSpeed; this.chaseSpeed = chaseSpeed; this.sight = sight; this.fov = fov;
        this.detect = detect; this.react = react; this.lose = lose; this.search = search;
        this.coins = coins; this.gems = gems; this.guards = guards;
        this.leash = 1.25f + 0.35f * (number - 1);
    }

    static final float[][] R1_COINS = {{485f, 1185.34f, 78f}, {394f, 1340.36f, 112f}, {304f, 1516.26f, 142f}, {760f, 1650f, 170.687f}, {660f, 1300f, 101.221f}, {560f, 1880f, 216.336f}};
    static final float[][] R2_GEMS = {{324f, 1056f, 105f}, {1194f, 1056f, 102.9f}, {2002f, 1372f, 111.3f}, {634f, 1782f, 128.1f}, {1406f, 2180f, 153.3f}, {1972f, 2694f, 149.1f}};
    static final float[][] NONE = new float[0][];

    static final Level[] ALL = {
        // 1 - Diamond Corridor: one slow guard pacing across the middle of the corridor.
        new Level(1, 0, "STEAL THE DIAMOND", 1, 0.42f, 1.2f, 2.3f, 30f, 1.35f, 0.9f, 1.3f, 3.0f, R1_COINS, NONE,
            new Wp[]{stop(250, 1575, 1.6f, 55), wp(560, 1560), stop(850, 1575, 1.6f, 55), wp(560, 1560)}),
        // 2 - Grand Hall: two guards on longer routes, quicker to notice.
        new Level(2, 1, "STEAL 4 GEMS", 4, 0.48f, 1.3f, 2.5f, 32f, 1.05f, 0.7f, 1.8f, 3.5f, NONE, R2_GEMS,
            new Wp[]{stop(640, 1150, 1.4f, 60), wp(410, 1150), stop(260, 1140, 1.2f, 50), wp(410, 1150)},
            new Wp[]{stop(1480, 1880, 1.2f, 50), wp(1480, 2130), stop(1480, 2380, 1.0f, 40), wp(1480, 2130)}),
        // 3 - Diamond Corridor again, now watched at the case as well.
        new Level(3, 0, "STEAL THE DIAMOND", 1, 0.5f, 1.35f, 2.6f, 32f, 1.0f, 0.65f, 2.0f, 3.5f, R1_COINS, NONE,
            new Wp[]{stop(250, 1760, 1.0f, 50), wp(560, 1745), stop(850, 1760, 1.0f, 50), wp(560, 1745)},
            new Wp[]{stop(420, 1330, 1.8f, 70), wp(700, 1335), stop(820, 1420, 1.2f, 40), wp(700, 1335)}),
        // 4 - Grand Hall, three guards with overlapping routes.
        new Level(4, 1, "STEAL 5 GEMS", 5, 0.55f, 1.4f, 2.7f, 34f, 0.85f, 0.55f, 2.2f, 4.0f, NONE, R2_GEMS,
            new Wp[]{stop(640, 1150, 1.0f, 60), wp(410, 1150), stop(260, 1140, 1.0f, 50), wp(410, 1150)},
            new Wp[]{stop(1480, 1880, 1.0f, 50), wp(1480, 2130), stop(1480, 2380, 0.8f, 40), wp(1480, 2130)},
            new Wp[]{stop(1000, 1150, 1.2f, 60), wp(1250, 1150), stop(1360, 1120, 1.0f, 50), wp(1250, 1150)}),
        // 5 - Diamond Corridor, three guards incl. one at the door.
        new Level(5, 0, "STEAL THE DIAMOND", 1, 0.58f, 1.45f, 2.8f, 34f, 0.8f, 0.5f, 2.4f, 4.0f, R1_COINS, NONE,
            new Wp[]{stop(250, 1760, 0.8f, 50), wp(560, 1745), stop(850, 1760, 0.8f, 50), wp(560, 1745)},
            new Wp[]{stop(420, 1330, 1.4f, 70), wp(700, 1335), stop(820, 1420, 1.0f, 40), wp(700, 1335)},
            new Wp[]{stop(530, 1180, 2.2f, 80), wp(640, 1230), stop(420, 1235, 1.0f, 50), wp(640, 1230)}),
        // 6 - Grand Hall, four guards, every gem needed.
        new Level(6, 1, "STEAL ALL 6 GEMS", 6, 0.6f, 1.5f, 3.0f, 35f, 0.7f, 0.45f, 2.6f, 4.5f, NONE, R2_GEMS,
            new Wp[]{stop(640, 1150, 0.8f, 60), wp(410, 1150), stop(260, 1140, 0.8f, 50), wp(410, 1150)},
            new Wp[]{stop(1480, 1880, 0.8f, 50), wp(1480, 2130), stop(1480, 2380, 0.8f, 40), wp(1480, 2130)},
            new Wp[]{stop(1000, 1150, 1.0f, 60), wp(1250, 1150), stop(1360, 1120, 0.8f, 50), wp(1250, 1150)},
            new Wp[]{stop(1900, 2500, 1.0f, 60), wp(1900, 2250), stop(1900, 1950, 1.0f, 50), wp(1900, 2250)}),
    };

    static final int COUNT = ALL.length;
    static Level get(int n) { return ALL[Math.max(1, Math.min(COUNT, n)) - 1]; }
}
