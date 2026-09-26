package com.treasurerun.game;

import android.graphics.Bitmap;

/** What the game needs from its container (Android activity, or the desktop render harness). */
interface Host {
    /** decoded asset, cached; null if missing */
    Bitmap bitmap(String path);

    /** drop cached assets whose path starts with the prefix */
    void forget(String prefix);

    void onGameFinished(int coins, int gems);

    void onGamePause();

    /** 0 = light tick, 1 = confirm, 2 = heavy (caught) */
    void haptic(int kind);

    void log(String msg);
}
