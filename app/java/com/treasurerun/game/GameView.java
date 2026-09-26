package com.treasurerun.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
final class GameView extends View {
    private static final int DONE = 4;
    private static final int FADE_IN = 3;
    private static final int FADE_OUT = 1;
    private static final int PLAY = 0;
    static final String TAG = "TreasureRun";
    private static final int TITLE = 2;
    private Shader arrowShader;
    private float arrowShaderY;
    private float autoFromX;
    private float autoFromY;
    private final Paint bmp;
    private float bx;
    private float by;
    private final Comparator<Item> byDepth;
    private boolean camReady;
    private float camScale;
    private float camX;
    private float camY;
    private float coinBump;
    private final RectF coinPill;
    private float[] coinPop;
    private boolean[] coinTaken;
    private final PorterDuffColorFilter coinTint;
    private int coins;
    private final int[] cr;
    private final List<Rect> cutouts;
    private final int[] cx4;
    private final int[] cy4;
    private final Paint fill;
    private Typeface font;
    private float gemBump;
    private final RectF gemPill;
    private float[] gemPop;
    private boolean[] gemTaken;
    private final PorterDuffColorFilter gemTint;
    private int gems;
    private Bitmap glow;
    private final PorterDuffColorFilter goldTint;
    private float handFlash;
    private int handPointer;
    private float handR;
    private float handX;
    private float handY;
    private final Host host;
    private float hs;
    private boolean interactNear;
    private final List<Item> items;
    private float joyDx;
    private float joyDy;
    private int joyPointer;
    private float joyR;
    private float joyX;
    private float joyY;
    private float knobTravel;
    private long lastNanos;
    private boolean mirror;
    private float moveAmt;
    private final Matrix mtx;
    private volatile boolean nextLoaded;
    private int nextRoom;
    private final RectF panel;
    private final Path path;
    private float pauseFlash;
    private float pauseR;
    private float pauseX;
    private float pauseY;
    private int phase;
    private float phaseT;
    private Shader pillShader;
    private final Item[] pool;
    private int pose;
    private boolean prevMirror;
    private int prevPose;
    private Room room;
    private int roomIndex;
    private int roomPicked;
    private int roomTotal;
    private int runPointer;
    private float runR;
    private float runX;
    private float runY;
    private boolean running;
    private final Rect safe;
    private int sh;
    private final Paint stroke;
    private int sw;
    private final Paint text;
    private float time;
    private String titleMain;
    private String titleTop;
    private final RectF tmpR;
    private float turnT;
    private float vx;
    private float vy;
    private byte[] walk;
    private int walkH;
    private float walkPhase;
    private int walkW;

    interface Host {
        Bitmap bitmap(String str);

        void forget(String str);

        void onGameFinished(int i, int i2);

        void onGamePause();
    }

    private static final class Item {
        float depth;
        int index;
        int kind;

        private Item() {
        }
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    GameView(Context context, Host host) {
        super(context);
        this.roomIndex = -1;
        this.pose = PLAY;
        this.prevPose = PLAY;
        this.turnT = 1.0f;
        this.phase = DONE;
        this.titleTop = "";
        this.titleMain = "";
        this.joyPointer = -1;
        this.runPointer = -1;
        this.handPointer = -1;
        this.safe = new Rect();
        this.cutouts = new ArrayList();
        this.cr = new int[DONE];
        this.cx4 = new int[DONE];
        this.cy4 = new int[DONE];
        this.coinPill = new RectF();
        this.gemPill = new RectF();
        this.panel = new RectF();
        this.bmp = new Paint(7);
        this.fill = new Paint(FADE_OUT);
        this.text = new Paint(FADE_OUT);
        this.stroke = new Paint(FADE_OUT);
        this.tmpR = new RectF();
        this.path = new Path();
        this.goldTint = new PorterDuffColorFilter(Color.rgb(255, 214, 90), PorterDuff.Mode.SRC_IN);
        this.coinTint = new PorterDuffColorFilter(Color.rgb(255, 200, 60), PorterDuff.Mode.SRC_IN);
        this.gemTint = new PorterDuffColorFilter(Color.rgb(90, 200, 255), PorterDuff.Mode.SRC_IN);
        this.mtx = new Matrix();
        this.arrowShaderY = Float.NaN;
        this.items = new ArrayList();
        this.pool = new Item[256];
        this.byDepth = new Comparator<Item>() { // from class: com.treasurerun.game.GameView.1
            @Override // java.util.Comparator
            public int compare(Item item, Item item2) {
                return Float.compare(item.depth, item2.depth);
            }
        };
        this.host = host;
        for (int i = PLAY; i < this.pool.length; i += FADE_OUT) {
            this.pool[i] = new Item();
        }
        try {
            this.font = Typeface.createFromAsset(context.getAssets(), "fonts/LilitaOne-Regular.ttf");
        } catch (RuntimeException e) {
            this.font = Typeface.DEFAULT_BOLD;
        }
        this.text.setTypeface(this.font);
        this.stroke.setTypeface(this.font);
        this.stroke.setStyle(Paint.Style.STROKE);
        this.stroke.setStrokeJoin(Paint.Join.ROUND);
        this.glow = makeGlow(128);
        setBackgroundColor(-16777216);
        setHapticFeedbackEnabled(true);
    }

    private static Bitmap makeGlow(int i) {
        Bitmap createBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(createBitmap);
        Paint paint = new Paint(FADE_OUT);
        paint.setShader(new RadialGradient(i / 2.0f, i / 2.0f, i / 2.0f, new int[]{Color.argb(255, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(PLAY, 255, 255, 255)}, new float[]{0.0f, 0.35f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(i / 2.0f, i / 2.0f, i / 2.0f, paint);
        return createBitmap;
    }

    void startRun() {
        this.coins = PLAY;
        this.gems = PLAY;
        this.phase = DONE;
        loadRoomSync(PLAY);
        beginIntro();
        resume();
    }

    void resume() {
        this.running = true;
        this.lastNanos = 0L;
        postInvalidateOnAnimation();
    }

    void pause() {
        this.running = false;
        this.handPointer = -1;
        this.runPointer = -1;
        this.joyPointer = -1;
        this.joyDy = 0.0f;
        this.joyDx = 0.0f;
    }

    private void loadRoomSync(int i) {
        Room room = RoomData.ROOMS[i];
        String[] strArr = room.bg;
        int length = strArr.length;
        for (int i2 = PLAY; i2 < length; i2 += FADE_OUT) {
            this.host.bitmap(strArr[i2]);
        }
        Occ[] occArr = room.occ;
        int length2 = occArr.length;
        for (int i3 = PLAY; i3 < length2; i3 += FADE_OUT) {
            this.host.bitmap(occArr[i3].sprite);
        }
        this.host.bitmap(room.walk);
        applyRoom(i);
    }

    private void applyRoom(int i) {
        if (this.roomIndex >= 0 && this.roomIndex != i) {
            this.host.forget("rooms/room" + (this.roomIndex + FADE_OUT) + "_");
        }
        this.roomIndex = i;
        this.room = RoomData.ROOMS[i];
        Bitmap bitmap = this.host.bitmap(this.room.walk);
        this.walkW = bitmap.getWidth();
        this.walkH = bitmap.getHeight();
        int[] iArr = new int[this.walkW * this.walkH];
        bitmap.getPixels(iArr, PLAY, this.walkW, PLAY, PLAY, this.walkW, this.walkH);
        this.walk = new byte[iArr.length];
        for (int i2 = PLAY; i2 < iArr.length; i2 += FADE_OUT) {
            this.walk[i2] = (byte) (((iArr[i2] >> 16) & 255) > 127 ? FADE_OUT : PLAY);
        }
        this.host.forget(this.room.walk);
        this.coinTaken = new boolean[this.room.coins.length];
        this.gemTaken = new boolean[this.room.gems.length];
        this.coinPop = new float[this.room.coins.length];
        this.gemPop = new float[this.room.gems.length];
        this.roomTotal = this.room.coins.length + this.room.gems.length;
        this.roomPicked = PLAY;
        this.bx = this.room.enterX;
        this.by = this.room.enterY;
        this.vy = 0.0f;
        this.vx = 0.0f;
        this.prevPose = PLAY;
        this.pose = PLAY;
        this.prevMirror = false;
        this.mirror = false;
        this.turnT = 1.0f;
        this.camReady = false;
        Log.i(TAG, "ROOM " + i + " " + this.room.title);
    }

    private void beginIntro() {
        this.titleTop = this.room.label;
        this.titleMain = this.room.title;
        this.phase = TITLE;
        this.phaseT = 0.35f;
        this.nextLoaded = true;
        this.nextRoom = this.roomIndex;
    }

    private void beginExit() {
        this.phase = FADE_OUT;
        this.phaseT = 0.0f;
        if (this.roomIndex + FADE_OUT < RoomData.ROOMS.length) {
            this.nextRoom = this.roomIndex + FADE_OUT;
            this.nextLoaded = false;
            final int i = this.nextRoom;
            new Thread(new Runnable() { // from class: com.treasurerun.game.GameView.2
                @Override // java.lang.Runnable
                public void run() {
                    Room room = RoomData.ROOMS[i];
                    String[] strArr = room.bg;
                    int length = strArr.length;
                    for (int i2 = GameView.PLAY; i2 < length; i2 += GameView.FADE_OUT) {
                        GameView.this.host.bitmap(strArr[i2]);
                    }
                    Occ[] occArr = room.occ;
                    int length2 = occArr.length;
                    for (int i3 = GameView.PLAY; i3 < length2; i3 += GameView.FADE_OUT) {
                        GameView.this.host.bitmap(occArr[i3].sprite);
                    }
                    GameView.this.host.bitmap(room.walk);
                    GameView.this.nextLoaded = true;
                }
            }, "room-loader").start();
        } else {
            this.nextRoom = -1;
            this.nextLoaded = true;
        }
        performHapticFeedback(FADE_OUT);
    }

    @Override // android.view.View
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        SafeArea.read(windowInsets, this.safe, this.cutouts, this.cr, this.cx4, this.cy4);
        layoutHud();
        return windowInsets;
    }

    @Override // android.view.View
    protected void onAttachedToWindow() {
        WindowInsets rootWindowInsets;
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 23 && (rootWindowInsets = getRootWindowInsets()) != null) {
            SafeArea.read(rootWindowInsets, this.safe, this.cutouts, this.cr, this.cx4, this.cy4);
        }
        requestApplyInsets();
    }

    @Override // android.view.View
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        layoutHud();
        this.camReady = false;
    }

    private void layoutHud() {
        int i;
        this.sw = getWidth();
        this.sh = getHeight();
        if (this.sw != 0 && this.sh != 0) {
            this.hs = Math.min(this.sw / 1056.0f, this.sh / 2240.0f) * 0.86f;
            float f = 0.03f * this.sw;
            int i2 = PLAY;
            Iterator<Rect> it = this.cutouts.iterator();
            while (true) {
                i = i2;
                if (!it.hasNext()) {
                    break;
                }
                Rect next = it.next();
                i2 = next.top < this.sh / DONE ? Math.max(i, next.bottom) : i;
            }
            float max = Math.max(Math.max(this.safe.top, i) + (0.012f * this.sw), f);
            float max2 = Math.max(this.safe.left, PLAY) + f;
            float max3 = (this.sh - Math.max(this.safe.bottom, PLAY)) - (0.02f * this.sw);
            float max4 = Math.max(max2, cornerInset() * 0.55f);
            float min = Math.min((this.sw - Math.max(this.safe.right, PLAY)) - f, this.sw - (cornerInset() * 0.55f));
            this.pauseR = 61.0f * this.hs;
            this.pauseX = this.pauseR + max4;
            this.pauseY = max + this.pauseR;
            float f2 = 92.0f * this.hs;
            float f3 = 0.022f * this.sw;
            float f4 = 250.0f * this.hs;
            this.pillShader = new LinearGradient(0.0f, this.pauseY - (f2 / 2.0f), 0.0f, this.pauseY + (f2 / 2.0f), Color.argb(225, 22, 26, 58), Color.argb(225, 10, 10, 30), Shader.TileMode.CLAMP);
            this.coinPill.set(this.pauseX + this.pauseR + f3, this.pauseY - (f2 / 2.0f), this.pauseX + this.pauseR + f3 + f4, this.pauseY + (f2 / 2.0f));
            this.gemPill.set(this.coinPill.right + f3, this.coinPill.top, this.coinPill.right + f3 + f4, this.coinPill.bottom);
            if (this.gemPill.right > min) {
                float f5 = ((min - this.coinPill.left) - f3) / (2.0f * f4);
                this.coinPill.right = this.coinPill.left + (f4 * f5);
                this.gemPill.set(this.coinPill.right + f3, this.coinPill.top, (f5 * f4) + this.coinPill.right + f3, this.coinPill.bottom);
            }
            float min2 = Math.min(931.0f * this.hs, min - max4);
            this.panel.set((this.sw - min2) / 2.0f, this.pauseY + this.pauseR + (0.018f * this.sw), (min2 + this.sw) / 2.0f, ((162.0f * min2) / 931.0f) + this.pauseY + this.pauseR + (0.018f * this.sw));
            this.joyR = 199.0f * this.hs;
            this.knobTravel = 118.0f * this.hs;
            this.joyX = (max4 - (0.01f * this.sw)) + this.joyR;
            this.joyY = max3 - this.joyR;
            this.runR = 132.0f * this.hs;
            this.handR = 118.0f * this.hs;
            this.runX = min - this.runR;
            this.runY = this.joyY + (20.0f * this.hs);
            this.handX = (min - this.handR) - (6.0f * this.hs);
            this.handY = ((this.runY - this.runR) - this.handR) - (36.0f * this.hs);
        }
    }

    private float cornerInset() {
        int[] iArr = this.cr;
        int length = iArr.length;
        int i = PLAY;
        int i2 = PLAY;
        while (i < length) {
            int max = Math.max(i2, iArr[i]);
            i += FADE_OUT;
            i2 = max;
        }
        return i2 * 0.3f;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        if (this.sw != getWidth() || this.sh != getHeight()) {
            layoutHud();
        }
        if (this.room == null || this.sw == 0) {
            if (this.running) {
                postInvalidateOnAnimation();
                return;
            }
            return;
        }
        long nanoTime = System.nanoTime();
        float min = this.lastNanos == 0 ? 0.016666668f : Math.min(0.05f, (nanoTime - this.lastNanos) / 1.0E9f);
        this.lastNanos = nanoTime;
        if (this.running) {
            update(min);
        }
        render(canvas);
        if (this.running) {
            postInvalidateOnAnimation();
        }
    }

    private void update(float f) {
        float f2;
        float f3;
        boolean z;
        int i;
        this.time += f;
        this.handFlash = Math.max(0.0f, this.handFlash - (3.0f * f));
        this.pauseFlash = Math.max(0.0f, this.pauseFlash - (3.0f * f));
        this.coinBump = Math.max(0.0f, this.coinBump - (4.0f * f));
        this.gemBump = Math.max(0.0f, this.gemBump - (4.0f * f));
        for (int i2 = PLAY; i2 < this.coinPop.length; i2 += FADE_OUT) {
            if (this.coinTaken[i2] && this.coinPop[i2] < 1.0f) {
                this.coinPop[i2] = Math.min(1.0f, this.coinPop[i2] + (f / 0.4f));
            }
        }
        for (int i3 = PLAY; i3 < this.gemPop.length; i3 += FADE_OUT) {
            if (this.gemTaken[i3] && this.gemPop[i3] < 1.0f) {
                this.gemPop[i3] = Math.min(1.0f, this.gemPop[i3] + (f / 0.45f));
            }
        }
        float f4 = 0.0f;
        float f5 = 0.0f;
        boolean z2 = false;
        switch (this.phase) {
            case PLAY /* 0 */:
                float hypot = (float) Math.hypot(this.joyDx, this.joyDy);
                if (hypot > 0.12f) {
                    float min = Math.min(1.0f, (hypot - 0.12f) / 0.78f) / hypot;
                    f4 = this.joyDx * min;
                    f5 = min * this.joyDy;
                    break;
                }
                break;
            case FADE_OUT /* 1 */:
                this.phaseT += f;
                float f6 = (this.room.exitX0 + this.room.exitX1) / 2.0f;
                float depth = this.room.exitY - (200.0f * this.room.depth(this.room.exitY));
                float f7 = f6 - this.bx;
                float f8 = depth - this.by;
                float hypot2 = (float) Math.hypot(f7, f8);
                if (hypot2 > 1.0f) {
                    f4 = f7 / hypot2;
                    f5 = f8 / hypot2;
                }
                z2 = true;
                if (this.phaseT >= 0.5f) {
                    if (this.nextRoom < 0) {
                        this.phase = DONE;
                        this.running = false;
                        this.host.onGameFinished(this.coins, this.gems);
                        return;
                    } else {
                        Room room = RoomData.ROOMS[this.nextRoom];
                        this.titleTop = room.label;
                        this.titleMain = room.title;
                        this.phase = TITLE;
                        this.phaseT = 0.0f;
                        break;
                    }
                }
                break;
            case TITLE /* 2 */:
                this.phaseT += f;
                if (this.phaseT >= 1.15f && this.nextLoaded) {
                    if (this.nextRoom != this.roomIndex) {
                        applyRoom(this.nextRoom);
                    }
                    this.autoFromX = this.room.enterX;
                    this.autoFromY = this.room.enterY;
                    this.bx = this.autoFromX;
                    this.by = this.autoFromY;
                    this.phase = FADE_IN;
                    this.phaseT = 0.0f;
                    this.camReady = false;
                    return;
                }
                return;
            case FADE_IN /* 3 */:
                this.phaseT += f;
                float f9 = this.room.spawnX - this.bx;
                float f10 = this.room.spawnY - this.by;
                float hypot3 = (float) Math.hypot(f9, f10);
                if (hypot3 <= 6.0f * this.room.depth(this.by)) {
                    f2 = PLAY;
                    f3 = PLAY;
                } else {
                    f3 = f9 / hypot3;
                    z2 = true;
                    f2 = f10 / hypot3;
                }
                if (this.phaseT < 0.9f || (z2 && this.phaseT <= 2.5f)) {
                    f5 = f2;
                    f4 = f3;
                    break;
                } else {
                    this.phase = PLAY;
                    f5 = f2;
                    f4 = f3;
                    break;
                }
            default:
                return;
        }
        float max = Math.max(0.12f, this.room.depth(this.by));
        float f11 = (this.runPointer < 0 || this.phase != 0) ? 1.0f : 1.65f;
        float f12 = this.room.speed * f4 * max * f11;
        float pow = this.room.speed * f5 * this.room.vFactor * ((float) Math.pow(max, this.room.vPow)) * f11;
        float min2 = Math.min(1.0f, 12.0f * f);
        this.vx = ((f12 - this.vx) * min2) + this.vx;
        this.vy += (pow - this.vy) * min2;
        float f13 = this.bx + (this.vx * f);
        float f14 = this.by + (this.vy * f);
        if (z2) {
            this.bx = f13;
            this.by = f14;
        } else if (canStand(f13, f14)) {
            this.bx = f13;
            this.by = f14;
        } else if (canStand(f13, this.by)) {
            this.bx = f13;
            this.vy *= 0.5f;
        } else if (canStand(this.bx, f14)) {
            this.by = f14;
            this.vx *= 0.5f;
        } else {
            this.vx *= 0.3f;
            this.vy *= 0.3f;
        }
        if (((float) Math.hypot(f4, f5)) > 0.25f) {
            double degrees = Math.toDegrees(Math.atan2(f4, -f5));
            double abs = Math.abs(degrees);
            if (abs <= 28.0d) {
                i = PLAY;
                z = false;
            } else if (abs <= 76.0d) {
                i = FADE_OUT;
                z = degrees < 0.0d;
            } else {
                z = Math.abs(f4) < 0.2f ? this.mirror : f4 < 0.0f;
                i = TITLE;
            }
            if (i != this.pose || z != this.mirror) {
                this.prevPose = this.pose;
                this.prevMirror = this.mirror;
                this.pose = i;
                this.mirror = z;
                this.turnT = 0.0f;
            }
        }
        this.turnT = Math.min(1.0f, this.turnT + (f / 0.16f));
        this.moveAmt = (((((float) Math.hypot((double) this.vx, (double) this.vy)) / (this.room.speed * max) > 0.12f ? 1.0f : 0.0f) - this.moveAmt) * Math.min(1.0f, 10.0f * f)) + this.moveAmt;
        this.walkPhase = ((f11 > 1.0f ? 4.4f : 3.3f) * f * 3.1415927f * this.moveAmt) + this.walkPhase;
        this.interactNear = false;
        float f15 = 2.2f * this.room.feetR * max;
        for (int i4 = PLAY; i4 < this.room.coins.length; i4 += FADE_OUT) {
            if (!this.coinTaken[i4] && near(this.room.coins[i4], f15, 1.0f)) {
                take(true, i4);
            }
        }
        for (int i5 = PLAY; i5 < this.room.gems.length; i5 += FADE_OUT) {
            if (!this.gemTaken[i5] && near(this.room.gems[i5], f15, 1.0f)) {
                take(false, i5);
            }
        }
        if (this.phase == 0) {
            float depth2 = this.room.depth(this.room.exitY);
            if (this.bx > this.room.exitX0 && this.bx < this.room.exitX1) {
                if (this.by < (depth2 * 260.0f) + this.room.exitY) {
                    this.interactNear = true;
                }
            }
            if (this.bx > this.room.exitX0 && this.bx < this.room.exitX1 && this.by <= this.room.exitY) {
                beginExit();
            }
        }
        updateCamera(f);
    }

    private boolean near(float[] fArr, float f, float f2) {
        return Math.hypot((double) (this.bx - fArr[PLAY]), (double) ((this.by - fArr[FADE_OUT]) * 1.5f)) < ((double) (((fArr[TITLE] * 0.35f) + f) * f2));
    }

    private void take(boolean z, int i) {
        if (z) {
            this.coinTaken[i] = true;
            this.coins += FADE_OUT;
            this.coinBump = 1.0f;
        } else {
            this.gemTaken[i] = true;
            this.gems += FADE_OUT;
            this.gemBump = 1.0f;
        }
        this.roomPicked += FADE_OUT;
        performHapticFeedback(FADE_IN);
    }

    private boolean canStand(float f, float f2) {
        float max = this.room.feetR * Math.max(0.15f, this.room.depth(f2));
        return walkable(f, f2) && walkable(f - max, f2) && walkable(f + max, f2) && walkable(f, f2 - (max * 0.6f)) && walkable(f, (max * 0.6f) + f2);
    }

    private boolean walkable(float f, float f2) {
        int i = (int) (f / this.room.walkDiv);
        int i2 = (int) (f2 / this.room.walkDiv);
        return i >= 0 && i2 >= 0 && i < this.walkW && i2 < this.walkH && this.walk[i + (i2 * this.walkW)] != 0;
    }

    private void updateCamera(float f) {
        float max = Math.max(this.sw / (float) this.room.w, this.sh / (float) this.room.h);
        float max2 = this.room.viewW > 0.0f ? Math.max(1.0f, (this.sw / this.room.viewW) / max) : 1.0f;
        float clamp = clamp((1.0f - this.room.depth(this.by)) / (1.0f - this.room.sFar), 0.0f, 1.0f);
        float f2 = max2 * (((3.0f - (clamp * 2.0f)) * clamp * clamp * (this.room.zoomFar - 1.0f)) + 1.0f) * max;
        if (this.camReady) {
            this.camScale = ((f2 - this.camScale) * Math.min(1.0f, 2.5f * f)) + this.camScale;
        } else {
            this.camScale = f2;
        }
        this.camScale = Math.max(this.camScale, max);
        float f3 = this.sw / this.camScale;
        float f4 = this.sh / this.camScale;
        float f5 = this.bx;
        float f6 = this.by - ((this.room.feetAt - 0.5f) * f4);
        float clamp2 = clamp(f5, f3 / 2.0f, this.room.w - (f3 / 2.0f));
        float clamp3 = clamp(f6, f4 / 2.0f, this.room.h - (f4 / 2.0f));
        if (!this.camReady) {
            this.camX = clamp2;
            this.camY = clamp3;
            this.camReady = true;
        } else {
            float min = Math.min(1.0f, 4.5f * f);
            this.camX = ((clamp2 - this.camX) * min) + this.camX;
            this.camY = (min * (clamp3 - this.camY)) + this.camY;
        }
        this.camX = clamp(this.camX, f3 / 2.0f, this.room.w - (f3 / 2.0f));
        this.camY = clamp(this.camY, f4 / 2.0f, this.room.h - (f4 / 2.0f));
    }

    private static float clamp(float f, float f2, float f3) {
        return f < f2 ? f2 : f > f3 ? f3 : f;
    }

    private void render(Canvas canvas) {
        float clamp;
        if (this.phase == TITLE) {
            canvas.drawColor(-16777216);
            drawTitle(canvas, 1.0f);
            return;
        }
        if (!this.camReady) {
            updateCamera(0.0f);
        }
        canvas.save();
        canvas.translate(this.sw / 2.0f, this.sh / 2.0f);
        canvas.scale(this.camScale, this.camScale);
        canvas.translate(-this.camX, -this.camY);
        this.bmp.setAlpha(255);
        for (int i = PLAY; i < this.room.bg.length; i += FADE_OUT) {
            Bitmap bitmap = this.host.bitmap(this.room.bg[i]);
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, 0.0f, this.room.bgY[i], this.bmp);
            }
        }
        drawExitGlow(canvas);
        drawWorldItems(canvas);
        drawExitArrow(canvas);
        canvas.restore();
        drawHud(canvas);
        if (this.phase == FADE_OUT) {
            clamp = clamp(this.phaseT / 0.5f, 0.0f, 1.0f);
        } else {
            clamp = this.phase == FADE_IN ? 1.0f - clamp(this.phaseT / 0.6f, 0.0f, 1.0f) : PLAY;
        }
        if (clamp > 0.0f) {
            this.fill.setShader(null);
            this.fill.setColor(Color.argb(Math.round(255.0f * clamp), PLAY, PLAY, PLAY));
            canvas.drawRect(0.0f, 0.0f, this.sw, this.sh, this.fill);
            if (this.phase == FADE_IN) {
                drawTitle(canvas, clamp);
            }
        }
    }

    private void drawWorldItems(Canvas canvas) {
        this.items.clear();
        float depth = this.room.depth(this.by);
        Item[] itemArr = this.pool;
        int i = FADE_OUT;
        Item item = itemArr[PLAY];
        item.kind = PLAY;
        item.index = PLAY;
        item.depth = this.by;
        this.items.add(item);
        float f = this.by;
        float f2 = this.room.boyH * depth;
        float f3 = this.bx - (0.55f * f2);
        float f4 = this.bx + (0.55f * f2);
        float f5 = this.by - (1.15f * f2);
        float f6 = (0.1f * f2) + this.by;
        for (int i2 = PLAY; i2 < this.room.coins.length && i < this.pool.length; i2 += FADE_OUT) {
            if (!this.coinTaken[i2] || this.coinPop[i2] < 1.0f) {
                Item[] itemArr2 = this.pool;
                int i3 = i + FADE_OUT;
                Item item2 = itemArr2[i];
                item2.kind = FADE_OUT;
                item2.index = i2;
                item2.depth = this.room.coins[i2][FADE_OUT];
                this.items.add(item2);
                f = Math.min(f, item2.depth);
                float f7 = this.room.coins[i2][TITLE];
                f3 = Math.min(f3, this.room.coins[i2][PLAY] - f7);
                f4 = Math.max(f4, this.room.coins[i2][PLAY] + f7);
                f5 = Math.min(f5, this.room.coins[i2][FADE_OUT] - (2.5f * f7));
                f6 = Math.max(f6, (f7 * 0.2f) + this.room.coins[i2][FADE_OUT]);
                i = i3;
            }
        }
        for (int i4 = PLAY; i4 < this.room.gems.length && i < this.pool.length; i4 += FADE_OUT) {
            if (!this.gemTaken[i4] || this.gemPop[i4] < 1.0f) {
                Item[] itemArr3 = this.pool;
                int i5 = i + FADE_OUT;
                Item item3 = itemArr3[i];
                item3.kind = TITLE;
                item3.index = i4;
                item3.depth = this.room.gems[i4][FADE_OUT];
                this.items.add(item3);
                f = Math.min(f, item3.depth);
                float f8 = this.room.gems[i4][TITLE];
                f3 = Math.min(f3, this.room.gems[i4][PLAY] - f8);
                f4 = Math.max(f4, this.room.gems[i4][PLAY] + f8);
                f5 = Math.min(f5, this.room.gems[i4][FADE_OUT] - (2.5f * f8));
                f6 = Math.max(f6, (f8 * 0.2f) + this.room.gems[i4][FADE_OUT]);
                i = i5;
            }
        }
        float f9 = (this.sw / this.camScale) / 2.0f;
        float f10 = (this.sh / this.camScale) / 2.0f;
        float max = Math.max(f3, this.camX - f9);
        float min = Math.min(f4, f9 + this.camX);
        float max2 = Math.max(f5, this.camY - f10);
        float max3 = Math.max(f6, this.by + (f2 * 0.1f));
        for (int i6 = PLAY; i6 < this.room.occ.length && i < this.pool.length; i6 += FADE_OUT) {
            Occ occ = this.room.occ[i6];
            if (occ.base > f && occ.x <= min && occ.x + occ.w >= max && occ.y <= max3 && occ.y + occ.h >= max2 && occ.y <= this.camY + f10 && occ.y + occ.h >= this.camY - f10) {
                Item[] itemArr4 = this.pool;
                int i7 = i + FADE_OUT;
                Item item4 = itemArr4[i];
                item4.kind = FADE_IN;
                item4.index = i6;
                item4.depth = occ.base;
                this.items.add(item4);
                i = i7;
            }
        }
        Collections.sort(this.items, this.byDepth);
        for (Item item5 : this.items) {
            switch (item5.kind) {
                case PLAY /* 0 */:
                    drawBoy(canvas);
                    break;
                case FADE_OUT /* 1 */:
                    drawPickup(canvas, this.room.coins[item5.index], "rooms/coin.png", this.coinPop[item5.index], this.coinTaken[item5.index], this.coinTint, item5.index);
                    break;
                case TITLE /* 2 */:
                    drawPickup(canvas, this.room.gems[item5.index], "rooms/diamond.png", this.gemPop[item5.index], this.gemTaken[item5.index], this.gemTint, item5.index + 7);
                    break;
                default:
                    Occ r0 = this.room.occ[item5.index];
                    Bitmap bitmap = this.host.bitmap(r0.sprite);
                    if (bitmap != null) {
                        this.bmp.setAlpha(255);
                        canvas.drawBitmap(bitmap, r0.x, r0.y, this.bmp);
                        break;
                    } else {
                        break;
                    }
            }
        }
    }

    private void drawBoy(Canvas canvas) {
        float depth = this.room.depth(this.by);
        float abs = this.moveAmt * Math.abs((float) Math.sin(this.walkPhase));
        float f = (1.0f - (0.12f * abs)) * this.room.boyH * depth * 0.36f;
        float f2 = (this.room.vPow > 1.5f ? 0.22f : 0.3f) * f;
        this.fill.setShader(null);
        this.fill.setColor(Color.argb(95, 20, 5, 20));
        this.tmpR.set(this.bx - (f / 2.0f), this.by - (f2 / 2.0f), (f / 2.0f) + this.bx, (f2 / 2.0f) + this.by);
        canvas.drawOval(this.tmpR, this.fill);
        boolean z = this.turnT < 0.5f;
        int i = z ? this.prevPose : this.pose;
        boolean z2 = z ? this.prevMirror : this.mirror;
        float max = Math.max(0.08f, Math.abs((float) Math.cos(this.turnT * 3.141592653589793d)));
        Pose pose = RoomData.POSES[i];
        Bitmap bitmap = this.host.bitmap(pose.sprite);
        if (bitmap != null) {
            float f3 = depth * this.room.boyH * pose.crouch;
            float f4 = f3 / pose.height;
            float cos = (this.moveAmt * 0.035f * ((float) Math.cos(this.walkPhase * 2.0f))) + ((1.0f - this.moveAmt) * 0.012f * ((float) Math.sin(this.time * 2.3f)));
            float f5 = 1.0f + cos;
            float sin = this.moveAmt * 3.2f * ((float) Math.sin(this.walkPhase));
            canvas.save();
            canvas.translate(this.bx, this.by - ((f3 * abs) * 0.05f));
            canvas.rotate(sin);
            canvas.scale((z2 ? -1 : FADE_OUT) * max * (1.0f - (cos * 0.7f)) * f4, f5 * f4);
            this.bmp.setAlpha(255);
            canvas.drawBitmap(bitmap, -pose.ax, -pose.ay, this.bmp);
            canvas.restore();
        }
    }

    private void drawPickup(Canvas canvas, float[] fArr, String str, float f, boolean z, ColorFilter colorFilter, int i) {
        Bitmap bitmap = this.host.bitmap(str);
        if (bitmap != null) {
            float f2 = fArr[TITLE];
            float sin = (0.62f * f2) + (((float) Math.sin((this.time * 2.6f) + (i * 1.3f))) * f2 * 0.08f);
            float f3 = 1.0f;
            float f4 = 1.0f;
            float f5 = 0.0f;
            if (z) {
                f3 = 1.0f + (0.6f * f);
                f4 = 1.0f - f;
                f5 = 0.9f * f2 * f;
            }
            this.fill.setShader(null);
            if (!z) {
                this.fill.setColor(Color.argb(70, 20, 5, 20));
                this.tmpR.set(fArr[PLAY] - (0.36f * f2), fArr[FADE_OUT] - (0.09f * f2), fArr[PLAY] + (0.36f * f2), fArr[FADE_OUT] + (0.09f * f2));
                canvas.drawOval(this.tmpR, this.fill);
            }
            float f6 = (fArr[FADE_OUT] - sin) - f5;
            this.bmp.setColorFilter(colorFilter);
            this.bmp.setAlpha(Math.round(150.0f * f4 * (0.85f + (0.15f * ((float) Math.sin((this.time * 3.0f) + i))))));
            float f7 = 1.25f * f2 * f3;
            this.tmpR.set(fArr[PLAY] - f7, f6 - f7, fArr[PLAY] + f7, f7 + f6);
            canvas.drawBitmap(this.glow, (Rect) null, this.tmpR, this.bmp);
            this.bmp.setColorFilter(null);
            this.bmp.setAlpha(Math.round(f4 * 255.0f));
            float f8 = (f2 / 2.0f) * f3;
            float height = (bitmap.getHeight() * f8) / bitmap.getWidth();
            float cos = str.contains("coin") ? 0.75f + (0.25f * ((float) Math.cos((this.time * 2.2f) + i))) : 1.0f;
            this.tmpR.set(fArr[PLAY] - (f8 * cos), f6 - height, (cos * f8) + fArr[PLAY], f6 + height);
            canvas.drawBitmap(bitmap, (Rect) null, this.tmpR, this.bmp);
            this.bmp.setAlpha(255);
        }
    }

    private void drawExitGlow(Canvas canvas) {
        float depth = this.room.depth(this.room.exitY);
        float f = (this.room.exitX0 + this.room.exitX1) / 2.0f;
        float f2 = (this.room.exitX1 - this.room.exitX0) * 0.62f;
        float sin = 0.75f + (0.25f * ((float) Math.sin(this.time * 3.2f)));
        this.bmp.setColorFilter(this.goldTint);
        this.bmp.setAlpha(Math.round(sin * 120.0f));
        this.tmpR.set(f - f2, this.room.exitY - (((f2 * 0.3f) * depth) * 2.0f), f + f2, (depth * f2 * 0.3f * 2.0f) + this.room.exitY);
        canvas.drawBitmap(this.glow, (Rect) null, this.tmpR, this.bmp);
        this.bmp.setColorFilter(null);
        this.bmp.setAlpha(255);
    }

    private void drawExitArrow(Canvas canvas) {
        float clamp = clamp((this.by - this.room.exitY) / (420.0f * this.room.depth(this.room.exitY)), 0.0f, 1.0f);
        if (clamp > 0.02f) {
            int round = Math.round(255.0f * clamp);
            float max = Math.max(60.0f, this.room.depth(this.room.doorY) * 150.0f) * (this.room.w > 1500 ? 1.4f : 1.0f);
            float sin = ((float) Math.sin(this.time * 4.0f)) * max * 0.18f;
            float f = this.room.doorX;
            float f2 = this.room.doorY + sin;
            this.path.reset();
            this.path.moveTo(f, (0.55f * max) + f2);
            this.path.lineTo(f - (max * 0.5f), f2);
            this.path.lineTo(f - (max * 0.2f), f2);
            this.path.lineTo(f - (max * 0.2f), f2 - (max * 0.5f));
            this.path.lineTo((max * 0.2f) + f, f2 - (max * 0.5f));
            this.path.lineTo((max * 0.2f) + f, f2);
            this.path.lineTo((max * 0.5f) + f, f2);
            this.path.close();
            canvas.save();
            canvas.rotate(180.0f, f, f2);
            this.fill.setShader(null);
            this.stroke.setColor(Color.argb(Math.round(clamp * 220.0f), 60, 25, PLAY));
            this.stroke.setStrokeWidth(0.12f * max);
            canvas.drawPath(this.path, this.stroke);
            this.fill.setAlpha(round);
            if (this.arrowShader == null || this.arrowShaderY != this.room.doorY) {
                this.arrowShader = new LinearGradient(0.0f, (-max) * 0.7f, 0.0f, max * 0.7f, Color.rgb(255, 160, 20), Color.rgb(255, 236, 120), Shader.TileMode.CLAMP);
                this.arrowShaderY = this.room.doorY;
            }
            this.mtx.setTranslate(0.0f, f2);
            this.arrowShader.setLocalMatrix(this.mtx);
            this.fill.setShader(this.arrowShader);
            this.fill.setAlpha(round);
            canvas.drawPath(this.path, this.fill);
            this.fill.setShader(null);
            this.fill.setAlpha(255);
            canvas.restore();
            this.text.setTextAlign(Paint.Align.CENTER);
            this.text.setTextSize(max * 0.5f);
            this.stroke.setTextAlign(Paint.Align.CENTER);
            this.stroke.setTextSize(max * 0.5f);
            this.stroke.setStrokeWidth(0.1f * max);
            this.stroke.setColor(Color.argb(round, 40, 15, PLAY));
            float f3 = f2 - (0.75f * max);
            canvas.drawText("EXIT", f, f3, this.stroke);
            this.text.setColor(Color.argb(round, 255, 238, 150));
            canvas.drawText("EXIT", f, f3, this.text);
        }
    }

    private void drawTitle(Canvas canvas, float f) {
        int round = Math.round(255.0f * clamp(f, 0.0f, 1.0f));
        float f2 = this.sw * 0.12f;
        this.text.setTextAlign(Paint.Align.CENTER);
        this.stroke.setTextAlign(Paint.Align.CENTER);
        this.text.setTextSize(0.5f * f2);
        this.text.setColor(Color.argb(round, 150, 200, 255));
        canvas.drawText(this.titleTop, this.sw / 2.0f, this.sh * 0.46f, this.text);
        this.stroke.setTextSize(f2);
        this.stroke.setStrokeWidth(f2 * 0.12f);
        this.stroke.setColor(Color.argb(round, 60, 20, PLAY));
        drawFitted(canvas, this.titleMain, this.sw / 2.0f, (this.sh * 0.46f) + (1.15f * f2), f2, this.sw * 0.9f, round);
    }

    private void drawFitted(Canvas canvas, String str, float f, float f2, float f3, float f4, int i) {
        this.text.setTextSize(f3);
        float measureText = this.text.measureText(str);
        if (measureText > f4) {
            f3 *= f4 / measureText;
        }
        this.text.setTextSize(f3);
        this.stroke.setTextSize(f3);
        this.stroke.setStrokeWidth(0.14f * f3);
        this.stroke.setColor(Color.argb(i, 60, 20, PLAY));
        canvas.drawText(str, f, f2, this.stroke);
        this.text.setShader(new LinearGradient(0.0f, f2 - f3, 0.0f, f2, Color.argb(i, 255, 240, 140), Color.argb(i, 255, 165, 20), Shader.TileMode.CLAMP));
        this.text.setColor(Color.argb(i, 255, 255, 255));
        canvas.drawText(str, f, f2, this.text);
        this.text.setShader(null);
    }

    private void drawHud(Canvas canvas) {
        drawCircleSprite(canvas, "hud/btn_pause.png", this.pauseX, this.pauseY, this.pauseR * (1.0f - (0.08f * this.pauseFlash)), 255);
        drawPill(canvas, this.coinPill, "hud/icon_coin.png", String.valueOf(this.coins), this.coinBump);
        drawPill(canvas, this.gemPill, "rooms/diamond.png", String.valueOf(this.gems), this.gemBump);
        Bitmap bitmap = this.host.bitmap("hud/panel.png");
        if (bitmap != null) {
            this.bmp.setAlpha(235);
            canvas.drawBitmap(bitmap, (Rect) null, this.panel, this.bmp);
            this.bmp.setAlpha(255);
            float height = this.panel.height() * 0.36f;
            this.text.setTextAlign(Paint.Align.LEFT);
            float width = this.panel.left + (this.panel.width() * 0.2f);
            float width2 = this.panel.width() * 0.56f;
            this.text.setTextSize(height);
            float measureText = this.text.measureText(this.room.objective);
            if (measureText > width2) {
                height *= width2 / measureText;
            }
            float centerY = this.panel.centerY() + (0.36f * height);
            outlined(canvas, this.room.objective, width, centerY, height, -1, Paint.Align.LEFT);
            outlined(canvas, this.roomPicked + "/" + this.roomTotal, this.panel.right - (this.panel.width() * 0.05f), centerY, this.panel.height() * 0.36f, (this.roomPicked != this.roomTotal || this.roomTotal <= 0) ? -1 : Color.rgb(255, 225, 90), Paint.Align.RIGHT);
        }
        drawCircleSprite(canvas, "hud/joy_base.png", this.joyX, this.joyY, this.joyR, 235);
        drawCircleSprite(canvas, "hud/joy_knob.png", this.joyX + (this.joyDx * this.knobTravel), this.joyY + (this.joyDy * this.knobTravel), this.hs * 106.0f * (this.joyPointer >= 0 ? 0.94f : 1.0f), 255);
        drawCircleSprite(canvas, "hud/btn_run.png", this.runX, this.runY, this.runR * (this.runPointer >= 0 ? 0.92f : 1.0f), 255);
        if (this.interactNear && this.phase == 0) {
            float sin = 0.5f + (0.5f * ((float) Math.sin(this.time * 6.0f)));
            this.bmp.setColorFilter(this.goldTint);
            this.bmp.setAlpha(Math.round(sin * 160.0f));
            float f = this.handR * 1.45f;
            this.tmpR.set(this.handX - f, this.handY - f, this.handX + f, f + this.handY);
            canvas.drawBitmap(this.glow, (Rect) null, this.tmpR, this.bmp);
            this.bmp.setColorFilter(null);
            this.bmp.setAlpha(255);
        }
        drawCircleSprite(canvas, "hud/btn_hand.png", this.handX, this.handY, this.handR * (1.0f - (0.1f * this.handFlash)), 255);
    }

    private void drawCircleSprite(Canvas canvas, String str, float f, float f2, float f3, int i) {
        Bitmap bitmap = this.host.bitmap(str);
        if (bitmap != null) {
            float width = (bitmap.getWidth() / (float) (bitmap.getWidth() - 8)) * f3;
            this.tmpR.set(f - width, f2 - width, f + width, width + f2);
            this.bmp.setAlpha(i);
            canvas.drawBitmap(bitmap, (Rect) null, this.tmpR, this.bmp);
            this.bmp.setAlpha(255);
        }
    }

    private void drawPill(Canvas canvas, RectF rectF, String str, String str2, float f) {
        float height = rectF.height() / 2.0f;
        this.fill.setShader(this.pillShader);
        canvas.drawRoundRect(rectF, height, height, this.fill);
        this.fill.setShader(null);
        this.stroke.setStrokeWidth(rectF.height() * 0.05f);
        this.stroke.setColor(Color.argb(200, 90, 150, 255));
        canvas.drawRoundRect(rectF, height, height, this.stroke);
        Bitmap bitmap = this.host.bitmap(str);
        float height2 = rectF.height() * 1.02f;
        float height3 = rectF.left + (rectF.height() * 0.08f);
        if (bitmap != null) {
            float height4 = (bitmap.getHeight() * height2) / bitmap.getWidth();
            this.tmpR.set(height3, rectF.centerY() - (height4 / 2.0f), height3 + height2, (height4 / 2.0f) + rectF.centerY());
            canvas.drawBitmap(bitmap, (Rect) null, this.tmpR, this.bmp);
        }
        float height5 = rectF.height() * 0.56f * (1.0f + (0.25f * f));
        outlined(canvas, str2, (((height3 + height2) + rectF.right) - (height * 0.6f)) / 2.0f, rectF.centerY() + (0.36f * height5), height5, -1, Paint.Align.CENTER);
    }

    private void outlined(Canvas canvas, String str, float f, float f2, float f3, int i, Paint.Align align) {
        this.stroke.setTextAlign(align);
        this.stroke.setTextSize(f3);
        this.stroke.setStrokeWidth(0.16f * f3);
        this.stroke.setColor(Color.argb(230, 10, 8, 30));
        canvas.drawText(str, f, f2, this.stroke);
        this.text.setTextAlign(align);
        this.text.setTextSize(f3);
        this.text.setColor(i);
        canvas.drawText(str, f, f2, this.text);
    }

    // Rebuilt by hand from the v0.4 smali (jadx could not decompile it).
    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        switch (action) {
            case 0: // ACTION_DOWN
            case 5: { // ACTION_POINTER_DOWN
                float x = e.getX(index);
                float y = e.getY(index);
                int id = e.getPointerId(index);
                if (dist(x, y, this.pauseX, this.pauseY) < this.pauseR * 1.35f) {
                    this.pauseFlash = 1.0f;
                    performHapticFeedback(FADE_OUT);
                    this.host.onGamePause();
                } else if (dist(x, y, this.runX, this.runY) < this.runR * 1.25f) {
                    this.runPointer = id;
                    performHapticFeedback(FADE_OUT);
                } else if (dist(x, y, this.handX, this.handY) < this.handR * 1.25f) {
                    this.handPointer = id;
                    this.handFlash = 1.0f;
                    onHand();
                } else if (this.joyPointer < 0 && (dist(x, y, this.joyX, this.joyY) < this.joyR * 1.6f || (x < this.sw * 0.5f && y > this.sh * 0.6f))) {
                    this.joyPointer = id;
                    setKnob(x, y);
                }
                break;
            }
            case 2: // ACTION_MOVE
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) == this.joyPointer) {
                        setKnob(e.getX(i), e.getY(i));
                    }
                }
                break;
            case 1: // ACTION_UP
            case 3: // ACTION_CANCEL
                this.handPointer = -1;
                this.runPointer = -1;
                this.joyPointer = -1;
                this.joyDy = 0.0f;
                this.joyDx = 0.0f;
                break;
            case 6: { // ACTION_POINTER_UP
                int id = e.getPointerId(index);
                if (id == this.joyPointer) {
                    this.joyPointer = -1;
                    this.joyDy = 0.0f;
                    this.joyDx = 0.0f;
                }
                if (id == this.runPointer) {
                    this.runPointer = -1;
                }
                if (id == this.handPointer) {
                    this.handPointer = -1;
                }
                break;
            }
        }
        return true;
    }

    private void setKnob(float f, float f2) {
        float f3 = (f - this.joyX) / this.knobTravel;
        float f4 = (f2 - this.joyY) / this.knobTravel;
        float hypot = (float) Math.hypot(f3, f4);
        if (hypot > 1.0f) {
            f3 /= hypot;
            f4 /= hypot;
        }
        this.joyDx = f3;
        this.joyDy = f4;
    }

    private void onHand() {
        performHapticFeedback(FADE_OUT);
        if (this.phase == 0) {
            if (this.interactNear) {
                beginExit();
                return;
            }
            float depth = 5.0f * this.room.depth(this.by) * this.room.feetR;
            for (int i = PLAY; i < this.room.coins.length; i += FADE_OUT) {
                if (!this.coinTaken[i] && near(this.room.coins[i], depth, 1.0f)) {
                    take(true, i);
                }
            }
            for (int i2 = PLAY; i2 < this.room.gems.length; i2 += FADE_OUT) {
                if (!this.gemTaken[i2] && near(this.room.gems[i2], depth, 1.0f)) {
                    take(false, i2);
                }
            }
        }
    }

    private static float dist(float f, float f2, float f3, float f4) {
        return (float) Math.hypot(f - f3, f2 - f4);
    }

    String debugState() {
        return String.format(Locale.US, "room=%d boy=%.0f,%.0f cam=%.0f,%.0f scale=%.3f", Integer.valueOf(this.roomIndex), Float.valueOf(this.bx), Float.valueOf(this.by), Float.valueOf(this.camX), Float.valueOf(this.camY), Float.valueOf(this.camScale));
    }
}
