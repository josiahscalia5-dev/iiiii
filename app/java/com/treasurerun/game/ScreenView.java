package com.treasurerun.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import com.treasurerun.game.FullBleed;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
final class ScreenView extends View {
    private static final long FADE_MS = 240;
    static final String TAG = "TreasureRun";
    private ValueAnimator animator;
    private final Paint bitmapPaint;
    private final Region clipRegion;
    private final int[] cornerR;
    private final int[] cornerX;
    private final int[] cornerY;
    private int current;
    private final List<Rect> cutouts;
    private final RectF dst;
    private float fade;
    private final RectF hitBounds;
    private final Region hitRegion;
    private final int[][] hotElem;
    private boolean layoutDirty;
    private int layoutH;
    private int layoutW;
    private FullBleed.Layout[] layouts;
    private Listener listener;
    private final boolean[] logged;
    private final List<Rect> newCutouts;
    private final int[] newR;
    private final Rect newSafe;
    private final int[] newX;
    private final int[] newY;
    private final Path path;
    private final Paint pressPaint;
    private Hotspot pressed;
    private int previous;
    private final Rect safe;
    private final ArtSource source;
    private final RectF tmp;

    interface ArtSource {
        Bitmap bitmap(String str);
    }

    interface Listener {
        void onHotspot(int i, String str);
    }

    ScreenView(Context context, ArtSource artSource) {
        super(context);
        this.bitmapPaint = new Paint(7);
        this.pressPaint = new Paint(1);
        this.dst = new RectF();
        this.tmp = new RectF();
        this.path = new Path();
        this.current = 0;
        this.previous = -1;
        this.fade = 1.0f;
        this.cutouts = new ArrayList();
        this.safe = new Rect();
        this.cornerR = new int[4];
        this.cornerX = new int[4];
        this.cornerY = new int[4];
        this.newCutouts = new ArrayList();
        this.newSafe = new Rect();
        this.newR = new int[4];
        this.newX = new int[4];
        this.newY = new int[4];
        this.layoutW = -1;
        this.layoutH = -1;
        this.layoutDirty = true;
        this.hotElem = new int[4][];
        this.logged = new boolean[4];
        this.hitBounds = new RectF();
        this.hitRegion = new Region();
        this.clipRegion = new Region();
        this.source = artSource;
        this.pressPaint.setColor(Color.argb(95, 0, 0, 0));
        setBackgroundColor(Color.rgb(8, 12, 30));
        setHapticFeedbackEnabled(true);
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < 4) {
                Hotspot[] hotspotArr = Screens.HOTSPOTS[i2];
                Elem[] elemArr = LayoutData.ELEMENTS[i2];
                float f = LayoutData.ART_SIZE[i2][0];
                float f2 = LayoutData.ART_SIZE[i2][1];
                this.hotElem[i2] = new int[hotspotArr.length];
                for (int i3 = 0; i3 < hotspotArr.length; i3++) {
                    float f3 = (hotspotArr[i3].shape == 1 ? hotspotArr[i3].a : (hotspotArr[i3].a + hotspotArr[i3].c) / 2.0f) * f;
                    float f4 = (hotspotArr[i3].shape == 1 ? hotspotArr[i3].b : (hotspotArr[i3].b + hotspotArr[i3].d) / 2.0f) * f2;
                    this.hotElem[i2][i3] = -1;
                    // jadx emitted this as while(true) with no exit when nothing matched,
                    // which hung onCreate on the splash screen.
                    for (int i4 = 0; i4 < elemArr.length; i4++) {
                        Elem elem = elemArr[i4];
                        if (f3 >= elem.cx - elem.halfW() && f3 <= elem.cx + elem.halfW() && f4 >= elem.cy - elem.halfH() && f4 <= elem.cy + elem.halfH()) {
                            this.hotElem[i2][i3] = i4;
                            break;
                        }
                    }
                }
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    int getScreen() {
        return this.current;
    }

    boolean isTransitioning() {
        return this.animator != null && this.animator.isRunning();
    }

    void showScreen(int i) {
        if (i != this.current || isTransitioning()) {
            if (this.animator != null) {
                this.animator.cancel();
            }
            this.previous = this.current;
            this.current = i;
            this.pressed = null;
            this.fade = 0.0f;
            this.animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.animator.setDuration(FADE_MS);
            this.animator.setInterpolator(new DecelerateInterpolator());
            this.animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.treasurerun.game.ScreenView.1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    ScreenView.this.fade = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                    ScreenView.this.invalidate();
                }
            });
            this.animator.addListener(new AnimatorListenerAdapter() { // from class: com.treasurerun.game.ScreenView.2
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    ScreenView.this.fade = 1.0f;
                    ScreenView.this.previous = -1;
                    ScreenView.this.invalidate();
                }
            });
            this.animator.start();
        }
    }

    @Override // android.view.View
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        readInsets(windowInsets);
        return windowInsets;
    }

    @Override // android.view.View
    protected void onAttachedToWindow() {
        WindowInsets rootWindowInsets;
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 23 && (rootWindowInsets = getRootWindowInsets()) != null) {
            readInsets(rootWindowInsets);
        }
        requestApplyInsets();
    }

    private void readInsets(WindowInsets windowInsets) {
        SafeArea.read(windowInsets, this.newSafe, this.newCutouts, this.newR, this.newX, this.newY);
        boolean z = (this.newSafe.equals(this.safe) && this.newCutouts.equals(this.cutouts)) ? false : true;
        for (int i = 0; i < 4; i++) {
            if (this.newR[i] != this.cornerR[i] || this.newX[i] != this.cornerX[i] || this.newY[i] != this.cornerY[i]) {
                z = true;
            }
            this.cornerR[i] = this.newR[i];
            this.cornerX[i] = this.newX[i];
            this.cornerY[i] = this.newY[i];
        }
        this.safe.set(this.newSafe);
        this.cutouts.clear();
        this.cutouts.addAll(this.newCutouts);
        if (z) {
            this.layoutDirty = true;
            invalidate();
        }
    }

    private boolean ensureLayout() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return false;
        }
        if (this.layouts == null || this.layoutDirty || width != this.layoutW || height != this.layoutH) {
            FullBleed.Device device = new FullBleed.Device(width, height, new ArrayList(this.cutouts), (int[]) this.cornerR.clone(), (int[]) this.cornerX.clone(), (int[]) this.cornerY.clone());
            long nanoTime = System.nanoTime();
            this.layouts = FullBleed.solve(device);
            this.layoutW = width;
            this.layoutH = height;
            this.layoutDirty = false;
            for (int i = 0; i < this.logged.length; i++) {
                this.logged[i] = false;
            }
            Log.i(TAG, String.format(Locale.US, "DEVICE view=%dx%d cutouts=%s corners=%d,%d,%d,%d safe=%s solve=%dms", Integer.valueOf(width), Integer.valueOf(height), this.cutouts, Integer.valueOf(this.cornerR[0]), Integer.valueOf(this.cornerR[1]), Integer.valueOf(this.cornerR[2]), Integer.valueOf(this.cornerR[3]), this.safe, Long.valueOf((System.nanoTime() - nanoTime) / 1000000)));
        }
        return true;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        if (ensureLayout()) {
            if (this.previous >= 0 && this.fade < 1.0f) {
                drawScreen(canvas, this.previous, 255, false);
                drawScreen(canvas, this.current, Math.round(255.0f * this.fade), false);
            } else {
                drawScreen(canvas, this.current, 255, true);
                logLayout(this.current);
            }
        }
    }

    private void drawScreen(Canvas canvas, int i, int i2, boolean z) {
        FullBleed.Layout layout = this.layouts[i];
        Bitmap bitmap = this.source.bitmap(LayoutData.BACKGROUND[i]);
        if (bitmap != null && layout != null) {
            this.bitmapPaint.setAlpha(i2);
            float f = LayoutData.ART_SIZE[i][0];
            float f2 = LayoutData.ART_SIZE[i][1];
            this.dst.set(layout.ox, layout.oy, (f * layout.s) + layout.ox, (f2 * layout.s) + layout.oy);
            canvas.drawBitmap(bitmap, (Rect) null, this.dst, this.bitmapPaint);
            Elem[] elemArr = LayoutData.ELEMENTS[i];
            float f3 = layout.k * layout.s;
            for (int i3 = 0; i3 < elemArr.length; i3++) {
                Elem elem = elemArr[i3];
                Bitmap bitmap2 = this.source.bitmap(elem.sprite);
                if (bitmap2 != null) {
                    this.dst.set(layout.cx[i3] + ((elem.sx0 - elem.cx) * f3), layout.cy[i3] + ((elem.sy0 - elem.cy) * f3), layout.cx[i3] + ((elem.sx1 - elem.cx) * f3), ((elem.sy1 - elem.cy) * f3) + layout.cy[i3]);
                    canvas.drawBitmap(bitmap2, (Rect) null, this.dst, this.bitmapPaint);
                }
            }
            if (z && this.pressed != null) {
                buildPath(i, this.pressed, this.path, 0.0f);
                canvas.drawPath(this.path, this.pressPaint);
            }
        }
    }

    private void buildPath(int i, Hotspot hotspot, Path path, float f) {
        float f2;
        float f3;
        float f4;
        path.reset();
        FullBleed.Layout layout = this.layouts[i];
        float f5 = LayoutData.ART_SIZE[i][0];
        float f6 = LayoutData.ART_SIZE[i][1];
        int indexOf = indexOf(i, hotspot);
        int i2 = indexOf < 0 ? -1 : this.hotElem[i][indexOf];
        if (i2 >= 0) {
            Elem elem = LayoutData.ELEMENTS[i][i2];
            f2 = layout.k * layout.s;
            f3 = layout.cx[i2] - (elem.cx * f2);
            f4 = layout.cy[i2] - (elem.cy * f2);
        } else {
            f2 = layout.s;
            f3 = layout.ox;
            f4 = layout.oy;
        }
        switch (hotspot.shape) {
            case 1:
                path.addCircle(f3 + (hotspot.a * f5 * f2), f4 + (hotspot.b * f6 * f2), (f2 * hotspot.c * f5) + f, Path.Direction.CW);
                break;
            case 2:
                this.tmp.set((((hotspot.a * f5) * f2) + f3) - f, (((hotspot.b * f6) * f2) + f4) - f, f3 + (f5 * hotspot.c * f2) + f, f4 + (f2 * hotspot.d * f6) + f);
                float width = this.tmp.width() * 0.42f;
                float width2 = this.tmp.width() * 0.04f;
                path.addRoundRect(this.tmp, new float[]{width, width, width, width, width2, width2, width2, width2}, Path.Direction.CW);
                break;
            default:
                this.tmp.set((((hotspot.a * f5) * f2) + f3) - f, (((hotspot.b * f6) * f2) + f4) - f, f3 + (hotspot.c * f5 * f2) + f, f4 + (f6 * hotspot.d * f2) + f);
                float f7 = hotspot.corner * f5 * f2;
                path.addRoundRect(this.tmp, f7, f7, Path.Direction.CW);
                break;
        }
    }

    private static int indexOf(int i, Hotspot hotspot) {
        Hotspot[] hotspotArr = Screens.HOTSPOTS[i];
        for (int i2 = 0; i2 < hotspotArr.length; i2++) {
            if (hotspotArr[i2] == hotspot) {
                return i2;
            }
        }
        return -1;
    }

    private Hotspot hitTest(float f, float f2) {
        if (!ensureLayout()) {
            return null;
        }
        float width = getWidth() * 0.015f;
        Hotspot[] hotspotArr = Screens.HOTSPOTS[this.current];
        for (int length = hotspotArr.length - 1; length >= 0; length--) {
            buildPath(this.current, hotspotArr[length], this.path, width);
            this.path.computeBounds(this.hitBounds, true);
            if (this.hitBounds.contains(f, f2)) {
                this.clipRegion.set(((int) this.hitBounds.left) - 1, ((int) this.hitBounds.top) - 1, ((int) this.hitBounds.right) + 1, ((int) this.hitBounds.bottom) + 1);
                this.hitRegion.setPath(this.path, this.clipRegion);
                if (this.hitRegion.contains((int) f, (int) f2)) {
                    return hotspotArr[length];
                }
            }
        }
        return null;
    }

    private void logLayout(int i) {
        if (!this.logged[i]) {
            this.logged[i] = true;
            FullBleed.Layout layout = this.layouts[i];
            Log.i(TAG, String.format(Locale.US, "LAYOUT screen=%d view=%dx%d bg=%.1f,%.1f,%.1f,%.1f scale=%.4f uiScale=%.3f overlap=%.0f nudge=%.0f", Integer.valueOf(i), Integer.valueOf(getWidth()), Integer.valueOf(getHeight()), Float.valueOf(layout.ox), Float.valueOf(layout.oy), Float.valueOf((LayoutData.ART_SIZE[i][0] * layout.s) + layout.ox), Float.valueOf((LayoutData.ART_SIZE[i][1] * layout.s) + layout.oy), Float.valueOf(layout.s), Float.valueOf(layout.k), Float.valueOf(layout.overlap), Float.valueOf(layout.nudge)));
            for (Hotspot hotspot : Screens.HOTSPOTS[i]) {
                buildPath(i, hotspot, this.path, 0.0f);
                this.path.computeBounds(this.hitBounds, true);
                Log.i(TAG, String.format(Locale.US, "HOT screen=%d id=%s x=%d y=%d", Integer.valueOf(i), hotspot.id, Integer.valueOf(Math.round(this.hitBounds.centerX())), Integer.valueOf(Math.round(this.hitBounds.centerY()))));
            }
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (!isTransitioning()) {
            switch (motionEvent.getActionMasked()) {
                case 0:
                    Hotspot hitTest = hitTest(motionEvent.getX(), motionEvent.getY());
                    if (hitTest != null) {
                        this.pressed = hitTest;
                        performHapticFeedback(1);
                        invalidate();
                        break;
                    }
                    break;
                case 1:
                    Hotspot hotspot = this.pressed;
                    this.pressed = null;
                    invalidate();
                    if (hotspot != null && hitTest(motionEvent.getX(), motionEvent.getY()) == hotspot) {
                        playSoundEffect(0);
                        if (this.listener != null) {
                            this.listener.onHotspot(this.current, hotspot.id);
                            break;
                        }
                    }
                    break;
                case 2:
                    if (this.pressed != null && hitTest(motionEvent.getX(), motionEvent.getY()) != this.pressed) {
                        this.pressed = null;
                        invalidate();
                        break;
                    }
                    break;
                case 3:
                    this.pressed = null;
                    invalidate();
                    break;
            }
        }
        return true;
    }
}
