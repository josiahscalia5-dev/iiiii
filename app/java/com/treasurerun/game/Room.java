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

    Room() {
    }

    float depth(float f) {
        return (f - this.horizon) / (this.yRef - this.horizon);
    }
}
