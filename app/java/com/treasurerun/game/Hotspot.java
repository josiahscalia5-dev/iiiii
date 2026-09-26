package com.treasurerun.game;

/* loaded from: classes.dex */
final class Hotspot {
    static final int ARCH = 2;
    static final int CIRCLE = 1;
    static final int RECT = 0;
    final float a;
    final float b;
    final float c;
    final float corner;
    final float d;
    final String id;
    final int shape;

    private Hotspot(String str, int i, float f, float f2, float f3, float f4, float f5) {
        this.id = str;
        this.shape = i;
        this.a = f;
        this.b = f2;
        this.c = f3;
        this.d = f4;
        this.corner = f5;
    }

    static Hotspot rect(String str, float f, float f2, float f3, float f4, float f5) {
        return new Hotspot(str, 0, f, f2, f3, f4, f5);
    }

    static Hotspot circle(String str, float f, float f2, float f3) {
        return new Hotspot(str, CIRCLE, f, f2, f3, 0.0f, 0.0f);
    }

    static Hotspot arch(String str, float f, float f2, float f3, float f4) {
        return new Hotspot(str, ARCH, f, f2, f3, f4, 0.0f);
    }
}
