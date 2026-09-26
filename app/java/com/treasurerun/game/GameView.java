package com.treasurerun.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

/** Android host view for {@link Game}: frame clock, multi-touch, insets. All gameplay lives in Game. */
final class GameView extends View {
    final Game game;
    private long lastNanos;
    private final int[] cx4 = new int[4], cy4 = new int[4];

    GameView(Context context, Host host) {
        super(context);
        Typeface font;
        try {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/LilitaOne-Regular.ttf");
        } catch (RuntimeException e) {
            font = Typeface.DEFAULT_BOLD;
        }
        game = new Game(host, font);
        game.dp = context.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF000000);
        setHapticFeedbackEnabled(true);
    }

    void startRun() {
        game.startRun();
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    void resume() {
        game.resume();
        lastNanos = 0;
        postInvalidateOnAnimation();
    }

    void pause() {
        game.pause();
    }

    void haptic(int kind) {
        performHapticFeedback(kind == 2 ? HapticFeedbackConstants.LONG_PRESS
                : kind == 1 ? HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        SafeArea.read(insets, game.safe, game.cutouts, game.corners, cx4, cy4);
        game.layoutHud();
        return insets;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets wi = getRootWindowInsets();
            if (wi != null) SafeArea.read(wi, game.safe, game.cutouts, game.corners, cx4, cy4);
        }
        requestApplyInsets();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        game.setSize(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game.sw != getWidth() || game.sh != getHeight()) game.setSize(getWidth(), getHeight());
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        game.frame(canvas, dt);
        if (game.running) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.touchDown(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) game.touchMove(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.touchUp(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                game.touchCancel();
                break;
        }
        return true;
    }

    String debugState() {
        return game.debugState();
    }
}
