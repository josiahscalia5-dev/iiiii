package com.treasurerun.game;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.treasurerun.game.GameView;
import com.treasurerun.game.ScreenView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/* loaded from: classes.dex */
public final class MainActivity extends Activity implements ScreenView.Listener, ScreenView.ArtSource, GameView.Host {
    private final Map<String, Bitmap> bitmaps = new HashMap();
    private GameView game;
    private boolean inGame;
    private ScreenView screens;
    private Toast toast;

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
                MainActivity.this.bitmap("hud/joy_base.png");
                MainActivity.this.bitmap("hud/joy_knob.png");
                MainActivity.this.bitmap("hud/btn_hand.png");
                MainActivity.this.bitmap("hud/btn_run.png");
                MainActivity.this.bitmap("hud/btn_pause.png");
                MainActivity.this.bitmap("hud/icon_coin.png");
                MainActivity.this.bitmap("hud/panel.png");
                for (Pose pose : RoomData.POSES) {
                    MainActivity.this.bitmap(pose.sprite);
                }
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

    private void startGame() {
        cancelToast();
        this.inGame = true;
        this.game.setVisibility(0);
        this.screens.setVisibility(8);
        this.game.requestApplyInsets();
        this.game.startRun();
    }

    @Override // com.treasurerun.game.GameView.Host
    public void onGamePause() {
        go(0);
    }

    @Override // com.treasurerun.game.GameView.Host
    public void onGameFinished(int i, int i2) {
        runOnUiThread(new Runnable() { // from class: com.treasurerun.game.MainActivity.2
            @Override // java.lang.Runnable
            public void run() {
                MainActivity.this.go(2);
            }
        });
    }

    @Override // com.treasurerun.game.ScreenView.Listener
    public void onHotspot(int i, String str) {
        switch (i) {
            case 0:
                if ("play".equals(str)) {
                    startGame();
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
                    go(3);
                    break;
                } else if ("home".equals(str)) {
                    go(0);
                    break;
                } else if ("replay".equals(str)) {
                    startGame();
                    break;
                } else {
                    say("Coming soon");
                    break;
                }
            case 3:
                if ("back".equals(str) || "back2".equals(str)) {
                    go(0);
                    break;
                } else if ("door1".equals(str)) {
                    startGame();
                    break;
                } else if ("door2".equals(str)) {
                    say("Level 2 isn't built yet");
                    break;
                } else if (str.startsWith("door")) {
                    say("Level " + str.substring(4) + " is locked");
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
