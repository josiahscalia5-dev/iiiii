package com.treasurerun.game;

/* loaded from: classes.dex */
final class Pose {
    static final int BACK = 0;
    static final int FRONT = 2;
    static final int TQ = 1;
    final float ax;
    final float ay;
    final float crouch;
    final float height;
    final String sprite;

    Pose(String str, float f, float f2, float f3, float f4) {
        this.sprite = str;
        this.ax = f;
        this.ay = f2;
        this.height = f3;
        this.crouch = f4;
    }
}
