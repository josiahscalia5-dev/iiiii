package com.treasurerun.game;

/* loaded from: classes.dex */
final class Screens {
    static final int COMPLETE = 2;
    static final int GAMEPLAY = 1;
    static final int LEVELS = 3;
    static final int SPLASH = 0;
    private static final float[][] DOOR_COLS = {new float[]{0.075f, 0.318f}, new float[]{0.388f, 0.618f}, new float[]{0.694f, 0.928f}};
    private static final float[][] DOOR_ROWS = {new float[]{0.283f, 0.438f}, new float[]{0.474f, 0.64f}, new float[]{0.666f, 0.84f}};
    static final int COUNT = 4;
    static final Hotspot[][] HOTSPOTS = new Hotspot[COUNT][];

    static {
        HOTSPOTS[0] = new Hotspot[]{Hotspot.rect("play", 0.12f, 0.778f, 0.88f, 0.852f, 0.07f), Hotspot.rect("levels", 0.16f, 0.866f, 0.843f, 0.915f, 0.05f), Hotspot.rect("shop", 0.067f, 0.923f, 0.201f, 0.97f, 0.035f), Hotspot.rect("missions", 0.317f, 0.923f, 0.451f, 0.97f, 0.035f), Hotspot.rect("skins", 0.563f, 0.923f, 0.697f, 0.97f, 0.035f), Hotspot.rect("settings_nav", 0.81f, 0.923f, 0.944f, 0.97f, 0.035f), Hotspot.circle("settings", 0.921f, 0.045f, 0.05f), Hotspot.circle("coins_plus", 0.775f, 0.047f, 0.034f)};
        HOTSPOTS[GAMEPLAY] = new Hotspot[]{Hotspot.circle("pause", 0.083f, 0.05f, 0.055f), Hotspot.circle("settings", 0.928f, 0.049f, 0.052f), Hotspot.circle("coins_plus", 0.533f, 0.049f, 0.034f), Hotspot.circle("interact", 0.875f, 0.742f, 0.105f), Hotspot.circle("sprint", 0.861f, 0.856f, 0.119f), Hotspot.circle("joystick", 0.195f, 0.875f, 0.183f)};
        HOTSPOTS[COMPLETE] = new Hotspot[]{Hotspot.rect("next", 0.08f, 0.795f, 0.917f, 0.869f, 0.06f), Hotspot.rect("home", 0.046f, 0.884f, 0.484f, 0.953f, 0.04f), Hotspot.rect("replay", 0.527f, 0.884f, 0.954f, 0.953f, 0.04f), Hotspot.circle("settings", 0.931f, 0.049f, 0.052f), Hotspot.circle("coins_plus", 0.73f, 0.05f, 0.034f)};
        Hotspot[] hotspotArr = new Hotspot[14];
        hotspotArr[0] = Hotspot.circle("back", 0.076f, 0.05f, 0.05f);
        hotspotArr[GAMEPLAY] = Hotspot.circle("back", 0.09f, 0.126f, 0.05f);
        hotspotArr[COMPLETE] = Hotspot.circle("settings", 0.917f, 0.127f, 0.052f);
        hotspotArr[LEVELS] = Hotspot.circle("coins_plus", 0.536f, 0.05f, 0.034f);
        hotspotArr[COUNT] = Hotspot.circle("keys_plus", 0.842f, 0.05f, 0.034f);
        for (int i = 0; i < LEVELS; i += GAMEPLAY) {
            for (int i2 = 0; i2 < LEVELS; i2 += GAMEPLAY) {
                int i3 = (i * LEVELS) + i2 + GAMEPLAY;
                hotspotArr[i3 + COUNT] = Hotspot.arch("door" + i3, DOOR_COLS[i2][0], DOOR_ROWS[i][0], DOOR_COLS[i2][GAMEPLAY], DOOR_ROWS[i][GAMEPLAY]);
            }
        }
        HOTSPOTS[LEVELS] = hotspotArr;
    }

    private Screens() {
    }
}
