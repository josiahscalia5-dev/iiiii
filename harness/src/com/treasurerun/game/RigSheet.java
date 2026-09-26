package com.treasurerun.game;

import android.graphics.*;
import java.io.File;

/** Contact sheet of the boy rig: rows = views (and mirrored), columns = walk phases (+ idle, + sprint). */
public class RigSheet {
    public static void main(String[] a) throws Exception {
        DesktopHost host = new DesktopHost("app/assets");
        Room room = RoomData.ROOMS[0];
        int cols = 9, cw = 260, ch = 360;
        int[] views = {Boy.BACK, Boy.TQ, Boy.FRONT, Boy.TQ, Boy.FRONT};
        float[] heads = {0, 55, 100, -55, -100};
        Bitmap out = Bitmap.createBitmap(cols * cw, views.length * ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.rgb(96, 78, 92));
        Paint p = new Paint(1); p.setColor(Color.rgb(120, 100, 110));
        for (int row = 0; row < views.length; row++) {
            for (int col = 0; col < cols; col++) {
                Boy b = new Boy();
                float h = (float) Math.toRadians(heads[row]);
                b.place(0, 0, h);
                boolean idle = col == 0, sprint = col == cols - 1;
                b.moveAmt = idle ? 0 : 1;
                b.runAmt = sprint ? 1 : 0;
                b.phase = idle ? 0 : (col - 1) / (float) (cols - 2);
                b.time = 1.3f;
                c.save();
                float gx = col * cw + cw / 2f, gy = row * ch + ch - 30;
                c.drawLine(col * cw, gy, col * cw + cw, gy, p);
                // draw the boy at a room depth such that he is ~300px tall
                float depth = 300f / room.boyH;
                b.y = room.horizon + depth * (room.yRef - room.horizon);
                c.translate(gx, gy - b.y);
                b.draw(c, room, host);
                c.restore();
            }
        }
        javax.imageio.ImageIO.write(out.img, "png", new File(a.length > 0 ? a[0] : "build/harness/rig.png"));
        System.out.println("wrote rig sheet");
    }
}
