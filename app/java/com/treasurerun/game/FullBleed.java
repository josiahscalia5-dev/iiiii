package com.treasurerun.game;

import android.graphics.Rect;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes.dex */
final class FullBleed {
    static final float K_MIN = 0.7f;
    static final float K_STEP = 0.005f;

    static final class Layout {
        float bottom;
        float[] cx;
        float[] cy;
        float k;
        float nudge;
        float overlap;
        float ox;
        float oy;
        float s;
        float top;

        Layout() {
        }
    }

    static final class Device {
        final List<Rect> cutouts;
        final int h;
        final int[] r;
        final int w;
        final int[] x;
        final int[] y;

        Device(int i, int i2, List<Rect> list, int[] iArr, int[] iArr2, int[] iArr3) {
            this.w = i;
            this.h = i2;
            this.cutouts = list == null ? new ArrayList<>() : list;
            this.r = iArr;
            this.x = iArr2;
            this.y = iArr3;
        }
    }

    static Layout[] solve(Device device) {
        int length = LayoutData.ELEMENTS.length;
        float f = 1.0f;
        for (int i = 0; i < length; i++) {
            float f2 = 1.0f;
            while (evaluate(device, i, f2).overlap > 0.5f && f2 > 0.700001f) {
                f2 = Math.round((f2 - K_STEP) * 10000.0f) / 10000.0f;
            }
            f = Math.min(f, f2);
        }
        Layout[] layoutArr = new Layout[length];
        for (int i2 = 0; i2 < length; i2++) {
            layoutArr[i2] = evaluate(device, i2, f);
        }
        return layoutArr;
    }

    private static float groupTop(Elem[] elemArr) {
        float f = Float.NaN;
        for (Elem elem : elemArr) {
            if (elem.group == 0) {
                f = Float.isNaN(f) ? elem.top() : Math.min(f, elem.top());
            }
        }
        return f;
    }

    private static float groupBottom(Elem[] elemArr) {
        float f = Float.NaN;
        for (Elem elem : elemArr) {
            if (elem.group == 1) {
                f = Float.isNaN(f) ? elem.bottom() : Math.max(f, elem.bottom());
            }
        }
        return f;
    }

    private static void place(Device device, Elem[] elemArr, float f, float f2, float f3, float f4, float f5, float f6, float f7, float[][] fArr, float[] fArr2, float[] fArr3) {
        float f8 = 0.012f * device.w;
        for (int i = 0; i < elemArr.length; i++) {
            Elem elem = elemArr[i];
            float halfW = elem.halfW() * f * f2;
            float halfH = elem.halfH() * f * f2;
            float min = Math.min(Math.max((elem.cx * f) + f3, f8 + halfW), (device.w - f8) - halfW);
            float f9 = elem.group == 0 ? ((elem.cy - f6) * f * f2) + f4 : f5 - (((f7 - elem.cy) * f) * f2);
            fArr[i][0] = min - halfW;
            fArr[i][1] = f9 - halfH;
            fArr[i][2] = halfW + min;
            fArr[i][3] = halfH + f9;
            fArr2[i] = min;
            fArr3[i] = f9;
        }
    }

    private static boolean pointOk(Device device, float f, float f2) {
        for (int i = 0; i < 4; i++) {
            if (device.r[i] > 0) {
                float f3 = device.x[i];
                float f4 = device.y[i];
                boolean z = f3 < ((float) device.w) / 2.0f ? f < f3 : f > f3;
                boolean z2 = f4 < ((float) device.h) / 2.0f ? f2 < f4 : f2 > f4;
                if (z && z2) {
                    float f5 = f - f3;
                    float f6 = f2 - f4;
                    if (Math.sqrt((f5 * f5) + (f6 * f6)) > device.r[i] - 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean rectOk(Device device, float[] fArr, boolean z) {
        float f = fArr[0];
        float f2 = fArr[1];
        float f3 = fArr[2];
        float f4 = fArr[3];
        if (f2 < 0.006f * device.w || f4 > device.h - (0.01f * device.w)) {
            return false;
        }
        float f5 = device.w * 0.011f;
        for (Rect rect : device.cutouts) {
            if (f < rect.right + f5 && f3 > rect.left - f5 && f2 < rect.bottom + f5 && f4 > rect.top - f5) {
                return false;
            }
        }
        if (z) {
            float f6 = (f + f3) / 2.0f;
            float f7 = (f2 + f4) / 2.0f;
            float f8 = (f3 - f) / 2.0f;
            for (int i = 0; i < 24; i++) {
                double d = (i * 3.141592653589793d) / 12.0d;
                if (!pointOk(device, (float) (f6 + (f8 * Math.cos(d))), (float) ((Math.sin(d) * f8) + f7))) {
                    return false;
                }
            }
        } else {
            for (int i2 = 0; i2 <= 8; i2++) {
                float f9 = i2 / 8.0f;
                float f10 = ((f3 - f) * f9) + f;
                float f11 = (f9 * (f4 - f2)) + f2;
                if (!pointOk(device, f10, f2) || !pointOk(device, f10, f4) || !pointOk(device, f, f11) || !pointOk(device, f3, f11)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static float overlap(float[] fArr, float f, float f2, float f3, float f4) {
        float min = Math.min(fArr[2], f3) - Math.max(fArr[0], f);
        float min2 = Math.min(fArr[3], f4) - Math.max(fArr[1], f2);
        if (min <= 0.0f || min2 <= 0.0f) {
            return 0.0f;
        }
        return min * min2;
    }

    static Layout evaluate(Device device, int i, float f) {
        int i2;
        int i3;
        float f2;
        Elem[] elemArr = LayoutData.ELEMENTS[i];
        float[][] fArr = LayoutData.PROTECTED[i];
        float f3 = LayoutData.ART_SIZE[i][0];
        float f4 = LayoutData.ART_SIZE[i][1];
        int i4 = device.w;
        int i5 = device.h;
        float max = Math.max(i4 / f3, i5 / f4);
        float f5 = f4 * max;
        float f6 = (i4 - (f3 * max)) / 2.0f;
        float groupTop = groupTop(elemArr);
        float groupBottom = groupBottom(elemArr);
        boolean z = !Float.isNaN(groupTop);
        boolean z2 = !Float.isNaN(groupBottom);
        int length = elemArr.length;
        float[][] fArr2 = (float[][]) Array.newInstance((Class<?>) Float.TYPE, length, 4);
        float[] fArr3 = new float[length];
        float[] fArr4 = new float[length];
        if (!z) {
            i2 = 0;
        } else {
            int i6 = i5 / 2;
            int i7 = 0;
            while (true) {
                int i8 = i7;
                if (i8 >= i5 / 2) {
                    i2 = i6;
                    break;
                }
                place(device, elemArr, max, f, f6, i8, i5, groupTop, z2 ? groupBottom : 0.0f, fArr2, fArr3, fArr4);
                boolean z3 = true;
                for (int i9 = 0; i9 < length && z3; i9++) {
                    if (elemArr[i9].group == 0) {
                        z3 = rectOk(device, fArr2[i9], elemArr[i9].shape == 0);
                    }
                }
                if (z3) {
                    i2 = i8;
                    break;
                }
                i7 = i8 + 1;
            }
        }
        if (z2) {
            int i10 = i5 / 2;
            int i11 = i5;
            while (true) {
                if (i11 <= i5 / 2) {
                    i3 = i10;
                    break;
                }
                place(device, elemArr, max, f, f6, 0.0f, i11, z ? groupTop : 0.0f, groupBottom, fArr2, fArr3, fArr4);
                boolean z4 = true;
                for (int i12 = 0; i12 < length && z4; i12++) {
                    if (elemArr[i12].group == 1) {
                        z4 = rectOk(device, fArr2[i12], elemArr[i12].shape == 0);
                    }
                }
                if (z4) {
                    i3 = i11;
                    break;
                }
                i11--;
            }
        } else {
            i3 = i5;
        }
        Layout layout = null;
        float f7 = 0.0f;
        float f8 = 0.0f;
        float f9 = 0.0f;
        int ceil = (int) Math.ceil(i5 - f5);
        if (ceil > 0) {
            ceil = 0;
        }
        int i13 = ceil;
        while (i13 <= 0) {
            float max2 = z ? Math.max(i2, i13 + (groupTop * max)) : 0.0f;
            float min = z2 ? Math.min(i3, i13 + (groupBottom * max)) : i5;
            place(device, elemArr, max, f, f6, max2, min, z ? groupTop : 0.0f, z2 ? groupBottom : 0.0f, fArr2, fArr3, fArr4);
            float f10 = 0.0f;
            int i14 = 0;
            while (i14 < length) {
                float f11 = f10;
                for (float[] fArr5 : fArr) {
                    f11 += overlap(fArr2[i14], (fArr5[0] * max) + f6, i13 + (fArr5[1] * max), (fArr5[2] * max) + f6, (fArr5[3] * max) + i13);
                }
                for (int i15 = i14 + 1; i15 < length; i15++) {
                    if (elemArr[i14].group != elemArr[i15].group) {
                        f11 += overlap(fArr2[i14], fArr2[i15][0], fArr2[i15][1], fArr2[i15][2], fArr2[i15][3]);
                    }
                }
                i14++;
                f10 = f11;
            }
            float abs = (z ? Math.abs(max2 - (i13 + (groupTop * max))) : 0.0f) + (z2 ? Math.abs(min - (i13 + (groupBottom * max))) : 0.0f);
            float abs2 = Math.abs(i13 - ((i5 - f5) / 2.0f));
            if (layout == null || f10 < f7 || (f10 == f7 && (abs < f8 || (abs == f8 && abs2 < f9)))) {
                if (layout == null) {
                    layout = new Layout();
                }
                layout.s = max;
                layout.k = f;
                layout.ox = f6;
                layout.oy = i13;
                layout.top = max2;
                layout.bottom = min;
                layout.cx = (float[]) fArr3.clone();
                layout.cy = (float[]) fArr4.clone();
                layout.overlap = f10;
                layout.nudge = abs;
                f2 = abs2;
            } else {
                f2 = f9;
                abs = f8;
                f10 = f7;
            }
            i13++;
            f9 = f2;
            f8 = abs;
            f7 = f10;
        }
        return layout;
    }

    private FullBleed() {
    }
}
