package com.quiet.background;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;


public class MainActivity extends Activity {
    private static final int INK = Color.rgb(32,62,73), MUTED = Color.rgb(88,111,120), TEAL = Color.rgb(40,122,136), BG = Color.rgb(242,247,249);
    private PlaybackService service;
    private boolean bound;
    private String renderedState = "";
    private TextView current, status, volumeLabel, systemLabel;
    private Button play;
    private SeekBar volume;
    private Button sounds;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { render(); handler.postDelayed(this, 500); } };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).service(); render();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; render(); }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24), dp(22), dp(24), dp(24));
        scroll.addView(body); root.addView(scroll); setContentView(root);
        TextView title = text("背景音", 30, INK); title.setTypeface(null, Typeface.BOLD); marginAdd(body, title, 0, 20);
        LinearLayout player = new LinearLayout(this); player.setOrientation(LinearLayout.VERTICAL); player.setPadding(dp(20), dp(20), dp(20), dp(18));
        player.setBackground(shape(Color.WHITE, 20)); body.addView(player);
        current = text("雨声", 26, INK); current.setTypeface(null, Typeface.BOLD); player.addView(current);
        status = text("正在连接播放器…", 13, MUTED); marginAdd(player, status, 8, 16);
        play = new Button(this); play.setText("播放"); play.setTextSize(17); play.setTextColor(Color.WHITE); play.setAllCaps(false);
        play.setBackgroundTintList(ColorStateList.valueOf(TEAL)); play.setMinHeight(dp(54)); player.addView(play, new LinearLayout.LayoutParams(-1, dp(56)));
        play.setOnClickListener(v -> {
            if (service == null) return;
            if (service.playing()) service.stopPlayback();
            else {
                startForegroundService(new Intent(this, PlaybackService.class).setAction(PlaybackService.PLAY));
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !getPreferences(0).getBoolean("notificationAsked", false)) {
                    getPreferences(0).edit().putBoolean("notificationAsked", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
                }
            }
            render();
        });
        volumeLabel = text("背景音量  40%", 17, INK); marginAdd(player, volumeLabel, 20, 2);
        volume = new SeekBar(this); volume.setMax(100); volume.setProgress(40); volume.setContentDescription("背景音量百分比");
        player.addView(volume, new LinearLayout.LayoutParams(-1, dp(48)));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && service != null) { service.setPercent(value); render(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        systemLabel = text("", 13, MUTED); player.addView(systemLabel);
        sounds = new Button(this);
        sounds.setText("切换背景音");
        sounds.setOnClickListener(v -> chooseSound());
        marginAdd(body, sounds, 18, 4);
        Button settings = new Button(this);
        settings.setText("背景音设置");
        settings.setOnClickListener(v -> showSettings());
        body.addView(settings);
    }

    private void chooseSound() {
        if (service == null) return;
        String[] names = new String[SoundCatalog.ALL.size()];
        int selected = 0;
        for (int i = 0; i < names.length; i++) {
            SoundCatalog.Sound sound = SoundCatalog.ALL.get(i);
            names[i] = sound.name();
            if (sound.key().equals(service.selected())) selected = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("切换背景音")
            .setSingleChoiceItems(names, selected, (dialog, which) -> {
                if (service != null) service.select(SoundCatalog.ALL.get(which).key());
                render();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showSettings() {
        SharedPreferences preferences = getSharedPreferences("settings", MODE_PRIVATE);
        new AlertDialog.Builder(this)
            .setTitle("背景音设置")
            .setMultiChoiceItems(new String[]{"息屏时关闭背景音"},
                new boolean[]{preferences.getBoolean(PlaybackService.STOP_ON_SCREEN_OFF, false)},
                (dialog, which, checked) -> preferences.edit()
                    .putBoolean(PlaybackService.STOP_ON_SCREEN_OFF, checked).apply())
            .setPositiveButton("完成", null)
            .show();
    }
    private void render() {
        AudioManager audio = getSystemService(AudioManager.class);
        int system = Math.round(100f * audio.getStreamVolume(AudioManager.STREAM_MUSIC) / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        String fingerprint = (service == null ? "disconnected" : service.selected() + service.status() + service.percent()) + ":" + system;
        if (fingerprint.equals(renderedState)) return;
        renderedState = fingerprint;
        play.setEnabled(service != null); volume.setEnabled(service != null);
        String key = service != null ? service.selected() : getSharedPreferences("settings", 0).getString("sound", "Rain");
        current.setText(SoundCatalog.find(key).name());
        if (service != null) {
            status.setText(service.status()); play.setText(service.playing() ? "关闭背景音" : "开启背景音");
            volume.setProgress(service.percent()); volumeLabel.setText("背景音量  " + service.percent() + "%");
        }
        systemLabel.setText("当前系统媒体音量 " + system + "%" + (system == 0 ? " · 系统已静音" : ""));
        sounds.setEnabled(service != null);
    }
    @Override protected void onStart() {
        super.onStart(); bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE); handler.post(refresh);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(refresh); if (bound) { unbindService(connection); bound = false; } service = null; super.onStop();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void marginAdd(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(top), 0, dp(bottom)); parent.addView(view, lp);
    }
}
