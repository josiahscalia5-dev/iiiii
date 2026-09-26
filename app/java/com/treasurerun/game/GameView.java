package com.treasurerun.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.List;

/** Thin Android wrapper around {@link Game}: frame timing, size/insets and multi-touch. */
final class GameView extends View {
    final Game game;
    private boolean running;
    private long lastNanos;
    private final Rect safe = new Rect();
    private final List<Rect> cutouts = new ArrayList<>();
    private final int[] cr = new int[4], cx4 = new int[4], cy4 = new int[4];

    GameView(Context context, Game.Host host) {
        super(context);
        Typeface font;
        try { font = Typeface.createFromAsset(context.getAssets(), "fonts/LilitaOne-Regular.ttf"); }
        catch (RuntimeException e) { font = Typeface.DEFAULT_BOLD; }
        game = new Game(host, font);
        setBackgroundColor(0xFF000000);
        setHapticFeedbackEnabled(true);
    }

    void startLevel(int n) {
        game.start(n);
        resume();
    }

    void resume() {
        running = true;
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    void pause() {
        running = false;
        game.touchCancel();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        SafeArea.read(insets, safe, cutouts, cr, cx4, cy4);
        game.setInsets(safe, cutouts, cr);
        return insets;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets w = getRootWindowInsets();
            if (w != null) { SafeArea.read(w, safe, cutouts, cr, cx4, cy4); game.setInsets(safe, cutouts, cr); }
        }
        requestApplyInsets();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        game.layout(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game.sw != getWidth() || game.sh != getHeight()) game.layout(getWidth(), getHeight());
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (running) game.update(dt);
        game.render(canvas);
        if (running) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int i = e.getActionIndex();
        float t = e.getEventTime() / 1000f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                game.touchDown(e.getPointerId(i), e.getX(i), e.getY(i), t);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < e.getPointerCount(); k++) game.touchMove(e.getPointerId(k), e.getX(k), e.getY(k), t);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                game.touchUp(e.getPointerId(i), e.getX(i), e.getY(i), t);
                break;
            case MotionEvent.ACTION_CANCEL:
                game.touchCancel();
                break;
        }
        return true;
    }
}
