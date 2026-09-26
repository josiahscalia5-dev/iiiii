package com.treasurerun.game;

/* loaded from: classes.dex */
final class Elem {
    static final int BOTTOM = 1;
    static final int CIRCLE = 0;
    static final int RECT = 1;
    static final int TOP = 0;
    final float a;
    final float b;
    final float c;
    final float cx;
    final float cy;
    final float d;
    final int group;
    final String id;
    final int shape;
    final String sprite;
    final float sx0;
    final float sx1;
    final float sy0;
    final float sy1;

    Elem(String str, String str2, int i, int i2, float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9, float f10) {
        this.id = str;
        this.sprite = str2;
        this.group = i;
        this.shape = i2;
        this.a = f;
        this.b = f2;
        this.c = f3;
        this.d = f4;
        this.cx = f5;
        this.cy = f6;
        this.sx0 = f7;
        this.sy0 = f8;
        this.sx1 = f9;
        this.sy1 = f10;
    }

    float top() {
        return this.shape == 0 ? this.b - this.c : this.b;
    }

    float bottom() {
        return this.shape == 0 ? this.b + this.c : this.d;
    }

    float halfW() {
        return this.shape == 0 ? this.c : (this.c - this.a) / 2.0f;
    }

    float halfH() {
        return this.shape == 0 ? this.c : (this.d - this.b) / 2.0f;
    }
}
