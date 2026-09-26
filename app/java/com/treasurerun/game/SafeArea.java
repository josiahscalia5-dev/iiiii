package com.treasurerun.game;

import android.annotation.TargetApi;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.RoundedCorner;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import java.util.List;

/* loaded from: classes.dex */
final class SafeArea {
    static void read(WindowInsets windowInsets, Rect rect, List<Rect> list, int[] iArr, int[] iArr2, int[] iArr3) {
        list.clear();
        if (Build.VERSION.SDK_INT >= 30) {
            V30.read(windowInsets, rect);
        } else {
            V21.read(windowInsets, rect);
            if (Build.VERSION.SDK_INT >= 28) {
                V28.addCutout(windowInsets, rect);
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            V28.cutouts(windowInsets, list);
        }
        for (int i = 0; i < 4; i++) {
            iArr[i] = 0;
            iArr2[i] = 0;
            iArr3[i] = 0;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            V31.corners(windowInsets, iArr, iArr2, iArr3);
        }
    }

    static void hideBars(Window window) {
        if (Build.VERSION.SDK_INT >= 30) {
            V30.hideBars(window);
        } else {
            V21.hideBars(window);
        }
    }

    private static final class V21 {
        private V21() {
        }

        static void read(WindowInsets windowInsets, Rect rect) {
            rect.set(windowInsets.getStableInsetLeft(), windowInsets.getStableInsetTop(), windowInsets.getStableInsetRight(), windowInsets.getStableInsetBottom());
        }

        static void hideBars(Window window) {
            window.getDecorView().setSystemUiVisibility(5894);
        }
    }

    @TargetApi(28)
    private static final class V28 {
        private V28() {
        }

        static void cutouts(WindowInsets windowInsets, List<Rect> list) {
            DisplayCutout displayCutout = windowInsets.getDisplayCutout();
            if (displayCutout != null) {
                for (Rect rect : displayCutout.getBoundingRects()) {
                    if (!rect.isEmpty()) {
                        list.add(new Rect(rect));
                    }
                }
            }
        }

        static void addCutout(WindowInsets windowInsets, Rect rect) {
            DisplayCutout displayCutout = windowInsets.getDisplayCutout();
            if (displayCutout != null) {
                rect.set(Math.max(rect.left, displayCutout.getSafeInsetLeft()), Math.max(rect.top, displayCutout.getSafeInsetTop()), Math.max(rect.right, displayCutout.getSafeInsetRight()), Math.max(rect.bottom, displayCutout.getSafeInsetBottom()));
            }
        }
    }

    @TargetApi(30)
    private static final class V30 {
        private V30() {
        }

        static void read(WindowInsets windowInsets, Rect rect) {
            Insets insetsIgnoringVisibility = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            rect.set(insetsIgnoringVisibility.left, insetsIgnoringVisibility.top, insetsIgnoringVisibility.right, insetsIgnoringVisibility.bottom);
        }

        static void hideBars(Window window) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                insetsController.setSystemBarsBehavior(2);
            }
        }
    }

    @TargetApi(31)
    private static final class V31 {
        private V31() {
        }

        static void corners(WindowInsets windowInsets, int[] iArr, int[] iArr2, int[] iArr3) {
            int[] iArr4 = {0, 1, 2, 3};
            for (int i = 0; i < 4; i++) {
                RoundedCorner roundedCorner = windowInsets.getRoundedCorner(iArr4[i]);
                if (roundedCorner != null) {
                    iArr[i] = roundedCorner.getRadius();
                    iArr2[i] = roundedCorner.getCenter().x;
                    iArr3[i] = roundedCorner.getCenter().y;
                }
            }
        }
    }

    private SafeArea() {
    }
}
