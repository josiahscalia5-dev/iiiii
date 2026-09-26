package com.treasurerun.game;
public class ConeDebug {
    public static void main(String[] a) {
        Sim s = new Sim(1080, 2340, "build/harness/cone");
        s.g.startRun(); s.run(1.0f);
        Guard q = s.g.guards.get(0);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888);
        q.drawCone(new android.graphics.Canvas(b), s.g.nav, 0);
        System.out.println("guard " + q.x + "," + q.y + " heading " + q.heading + " fanN " + q.fanN);
        for (int i = 0; i < q.fanN; i += 8) System.out.println(q.fan[i] + "," + q.fan[i+1]);
        System.out.println("ray " + s.g.nav.ray(q.x, q.y, 1, 0, q.range));
    }
}
