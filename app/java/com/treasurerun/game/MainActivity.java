package com.treasurerun.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.treasurerun.game.ScreenView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/* loaded from: classes.dex */
public final class MainActivity extends Activity implements ScreenView.Listener, ScreenView.ArtSource, Game.Host {
    private final Map<String, Bitmap> bitmaps = new HashMap();
    private GameView game;
    private boolean inGame;
    private ScreenView screens;
    private Toast toast;
    private SoundPool sounds;
    private final int[] soundIds = new int[Game.SOUNDS.length];
    private int unlocked = 1, lastLevel = 1;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        int i;
        super.onCreate(bundle);
        Window window = getWindow();
        window.addFlags(128);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            if (Build.VERSION.SDK_INT >= 30) {
                i = 3;
            } else {
                i = 1;
            }
            attributes.layoutInDisplayCutoutMode = i;
            window.setAttributes(attributes);
        }
        loadScreen(0);
        SharedPreferences prefs = getSharedPreferences("progress", MODE_PRIVATE);
        unlocked = Math.max(1, Math.min(Level.COUNT, prefs.getInt("unlocked", 1)));
        lastLevel = unlocked;
        initSounds();
        FrameLayout frameLayout = new FrameLayout(this);
        this.screens = new ScreenView(this, this);
        this.screens.setListener(this);
        this.game = new GameView(this, this);
        this.game.setVisibility(8);
        frameLayout.addView(this.screens, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(this.game, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
        hideSystemBars();
        new Thread(new Runnable() { // from class: com.treasurerun.game.MainActivity.1
            @Override // java.lang.Runnable
            public void run() {
                for (int i2 = 0; i2 < 4; i2++) {
                    if (i2 != 1) {
                        MainActivity.this.loadScreen(i2);
                    }
                }
                MainActivity.this.bitmap("hud/btn_hand.png");
                MainActivity.this.bitmap("hud/btn_run.png");
                MainActivity.this.bitmap("hud/btn_pause.png");
                MainActivity.this.bitmap("hud/icon_coin.png");
                MainActivity.this.bitmap("hud/panel.png");
                for (Rig.View v : new Rig.View[]{Rig.BOY_BACK, Rig.BOY_TQ, Rig.BOY_FRONT, Rig.GUARD_TQB, Rig.GUARD_TQF, Rig.GUARD_FRONT}) {
                    MainActivity.this.bitmap(v.body);
                    if (v.arm != null) MainActivity.this.bitmap(v.arm);
                }
                MainActivity.this.bitmap("layers/complete_replay.png");
                MainActivity.this.bitmap("layers/complete_home.png");
                MainActivity.this.bitmap("rooms/coin.png");
                MainActivity.this.bitmap("rooms/diamond.png");
            }
        }, "asset-loader").start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadScreen(int i) {
        bitmap(LayoutData.BACKGROUND[i]);
        for (Elem elem : LayoutData.ELEMENTS[i]) {
            bitmap(elem.sprite);
        }
    }

    @Override // com.treasurerun.game.ScreenView.ArtSource, com.treasurerun.game.GameView.Host
    public Bitmap bitmap(String str) {
        Bitmap bitmap;
        synchronized (this.bitmaps) {
            if (this.bitmaps.containsKey(str)) {
                bitmap = this.bitmaps.get(str);
            } else {
                Bitmap decode = decode(str);
                synchronized (this.bitmaps) {
                    bitmap = this.bitmaps.get(str);
                    if (bitmap == null) {
                        this.bitmaps.put(str, decode);
                        bitmap = decode;
                    }
                }
            }
        }
        return bitmap;
    }

    @Override // com.treasurerun.game.GameView.Host
    public void forget(String str) {
        synchronized (this.bitmaps) {
            ArrayList arrayList = new ArrayList();
            for (String str2 : this.bitmaps.keySet()) {
                if (str2.startsWith(str)) {
                    arrayList.add(str2);
                }
            }
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                this.bitmaps.remove((String) it.next());
            }
        }
    }

    private Bitmap decode(String str) {
        InputStream in = null;
        try {
            in = getAssets().open(str);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) { try { in.close(); } catch (IOException e) { } }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void go(int i) {
        loadScreen(i);
        this.screens.setUnlocked(unlocked);
        this.screens.setCompletedLevel(lastLevel);
        cancelToast();
        showMenus();
        this.screens.showScreen(i);
    }

    private void showMenus() {
        if (this.inGame) {
            this.game.pause();
            this.game.setVisibility(8);
            this.screens.setVisibility(0);
            this.inGame = false;
        }
    }

    private void startGame(int level) {
        cancelToast();
        lastLevel = Math.max(1, Math.min(Level.COUNT, level));
        this.inGame = true;
        this.game.setVisibility(0);
        this.screens.setVisibility(8);
        this.game.requestApplyInsets();
        this.game.startLevel(lastLevel);
    }

    private void initSounds() {
        sounds = new SoundPool.Builder().setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        for (int i = 0; i < Game.SOUNDS.length; i++) {
            try {
                AssetFileDescriptor fd = getAssets().openFd(Game.SOUNDS[i]);
                soundIds[i] = sounds.load(fd, 1);
                fd.close();
            } catch (IOException e) {
                soundIds[i] = 0;
            }
        }
    }

    // ---- Game.Host ----
    @Override
    public void sound(int id, float volume) {
        if (sounds != null && id >= 0 && id < soundIds.length && soundIds[id] != 0) sounds.play(soundIds[id], volume, volume, 1, 0, 1f);
    }

    @Override
    public void haptic(final int kind) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (game != null) game.performHapticFeedback(kind >= 2 ? HapticFeedbackConstants.LONG_PRESS : kind == 1 ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });
    }

    @Override
    public void pause() {
        go(0);
    }

    @Override
    public void home() {
        go(0);
    }

    @Override
    public void levelComplete(final int level, int coins, int gems) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastLevel = level;
                if (level + 1 > unlocked) {
                    unlocked = Math.min(Level.COUNT, level + 1);
                    getSharedPreferences("progress", MODE_PRIVATE).edit().putInt("unlocked", unlocked).apply();
                }
                MainActivity.this.go(2);
            }
        });
    }

    int unlockedLevels() {
        return unlocked;
    }

    @Override // com.treasurerun.game.ScreenView.Listener
    public void onHotspot(int i, String str) {
        switch (i) {
            case 0:
                if ("play".equals(str)) {
                    startGame(unlocked);
                    break;
                } else if ("levels".equals(str)) {
                    go(3);
                    break;
                } else if ("shop".equals(str)) {
                    say("Shop is coming soon");
                    break;
                } else if ("missions".equals(str)) {
                    say("Missions are coming soon");
                    break;
                } else if ("skins".equals(str)) {
                    say("Skins are coming soon");
                    break;
                } else {
                    say("Settings are coming soon");
                    break;
                }
            case 2:
                if ("next".equals(str)) {
                    if (lastLevel < Level.COUNT) {
                        startGame(lastLevel + 1);
                    } else {
                        go(3);
                        say("More levels are coming soon!");
                    }
                    break;
                } else if ("home".equals(str)) {
                    go(0);
                    break;
                } else if ("replay".equals(str)) {
                    startGame(lastLevel);
                    break;
                } else {
                    say("Coming soon");
                    break;
                }
            case 3:
                if ("back".equals(str) || "back2".equals(str)) {
                    go(0);
                    break;
                } else if (str.startsWith("door")) {
                    int n = Integer.parseInt(str.substring(4));
                    if (n <= unlocked) {
                        startGame(n);
                    } else if (n <= Level.COUNT) {
                        say("Level " + n + " is locked. Escape level " + (n - 1) + " first!");
                    } else {
                        say("Level " + n + " is coming soon");
                    }
                    break;
                } else {
                    say("Coming soon");
                    break;
                }
        }
    }

    private void say(String str) {
        cancelToast();
        this.toast = Toast.makeText(this, str, 0);
        this.toast.show();
    }

    private void cancelToast() {
        if (this.toast != null) {
            this.toast.cancel();
            this.toast = null;
        }
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.inGame) {
            go(0);
        } else if (this.screens.getScreen() != 0) {
            go(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        if (this.inGame) {
            this.game.pause();
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (this.inGame) {
            this.game.resume();
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        SafeArea.hideBars(getWindow());
        if (this.screens != null) {
            this.screens.requestApplyInsets();
        }
        if (this.game != null) {
            this.game.requestApplyInsets();
        }
    }
}
