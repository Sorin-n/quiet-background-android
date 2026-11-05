package com.quiet.background;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class BackgroundTileService extends TileService {
    private PlaybackService playback;
    private boolean listening;
    private boolean bound;
    private AlertDialog panel;
    private PanelLayer layer;
    private ControlCard card;
    private Button power;
    private Button sound;
    private TextView status;
    private TextView volumeLabel;
    private SeekBar volume;
    private RadioGroup choices;
    private final Runnable changed = this::refresh;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder) binder).service();
            playback.addControlsListener(changed);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            playback = null;
            refresh();
        }
    };

    @Override public void onStartListening() {
        listening = true;
        connect();
        refresh();
    }

    @Override public void onStopListening() {
        listening = false;
        if (layer != null) layer.remove();
        releaseIfIdle();
    }

    private void connect() {
        if (!bound) bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
    }

    private void releaseIfIdle() {
        if (listening || panel != null || card != null || !bound) return;
        if (playback != null) playback.removeControlsListener(changed);
        unbindService(connection);
        bound = false;
        playback = null;
    }

    @Override public void onClick() {
        if (isLocked()) unlockAndRun(this::openPanel);
        else openPanel();
    }

    private void togglePower() {
        if (playback == null) return;
        if (playback.playing()) playback.stopPlayback();
        else {
            try {
                startForegroundService(new Intent(this, PlaybackService.class).setAction(PlaybackService.PLAY));
            } catch (IllegalStateException denied) {
                Toast.makeText(this, "系统未允许开始播放，请解锁并重新点按快捷开关。", Toast.LENGTH_LONG).show();
            }
        }
        refresh();
    }

    private void openPanel() {
        if (card != null) { layer.collapse(); return; }
        if (panel != null) return;
        layer = PanelLayer.connected();
        if (layer != null) {
            card = new ControlCard(layer, this::togglePower, layer::collapse);
            if (layer.show(card, () -> {
                card = null;
                layer = null;
                releaseIfIdle();
            })) {
                connect();
                refresh();
                return;
            }
            card = null;
            layer = null;
        }
        Context ui = new ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert);
        LinearLayout content = new LinearLayout(ui);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, 0);
        ScrollView scroll = new ScrollView(ui);
        scroll.addView(content);
        status = new TextView(ui);
        content.addView(status);
        power = button(content, "开启背景音");
        power.setOnClickListener(v -> togglePower());
        volumeLabel = new TextView(ui);
        content.addView(volumeLabel);
        volume = new SeekBar(ui);
        volume.setMax(100);
        volume.setContentDescription("背景音量百分比");
        volume.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        content.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && playback != null) playback.setPercent(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        sound = button(content, "切换背景音");
        choices = new RadioGroup(ui);
        choices.setVisibility(View.GONE);
        content.addView(choices);
        for (SoundCatalog.Sound entry : SoundCatalog.ALL) {
            RadioButton row = new RadioButton(ui);
            row.setText(entry.name());
            row.setTag(entry.key());
            choices.addView(row);
            row.setOnClickListener(v -> {
                if (playback != null) playback.select(entry.key());
                choices.setVisibility(View.GONE);
                refresh();
            });
        }
        Button settings = button(content, "背景音设置");
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        CheckBox screenOff = new CheckBox(ui);
        screenOff.setText("息屏时关闭背景音");
        screenOff.setVisibility(View.GONE);
        content.addView(screenOff);
        screenOff.setOnClickListener(v -> prefs.edit().putBoolean(
            PlaybackService.STOP_ON_SCREEN_OFF, screenOff.isChecked()).apply());
        settings.setOnClickListener(v -> {
            choices.setVisibility(View.GONE);
            screenOff.setChecked(prefs.getBoolean(PlaybackService.STOP_ON_SCREEN_OFF, false));
            toggle(screenOff);
        });
        sound.setOnClickListener(v -> {
            screenOff.setVisibility(View.GONE);
            toggle(choices);
        });
        panel = new AlertDialog.Builder(ui).setTitle("背景音").setView(scroll)
            .setPositiveButton("完成", null).create();
        // SystemUI can unbind the tile shortly after the shade collapses.
        // Keep the service that owns the dialog alive until the user dismisses it.
        startService(new Intent(this, BackgroundTileService.class));
        panel.setOnDismissListener(dialog -> {
            panel = null;
            stopSelf();
            releaseIfIdle();
        });
        connect();
        refresh();
        showDialog(panel);
    }

    private Button button(LinearLayout parent, String text) {
        Button button = new Button(parent.getContext());
        button.setText(text);
        parent.addView(button);
        return button;
    }

    private void toggle(View view) {
        view.setVisibility(view.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void refresh() {
        Tile tile = getQsTile();
        boolean playing = playback != null && playback.playing();
        String name = playback == null ? "" : SoundCatalog.find(playback.selected()).name();
        if (tile != null && listening) {
            tile.setState(playing ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(playing ? name : "已关闭");
            tile.updateTile();
        }
        if (card != null) card.update(playback);
        if (panel == null) return;
        power.setEnabled(playback != null);
        sound.setEnabled(playback != null);
        volume.setEnabled(playback != null);
        status.setText(playback == null ? "正在连接播放器…" : playback.status());
        if (playback == null) return;
        power.setText(playing ? "关闭背景音" : "开启背景音");
        volumeLabel.setText(getString(R.string.tile_volume, playback.percent()));
        volume.setProgress(playback.percent());
        sound.setText(getString(R.string.tile_sound, name));
        RadioButton selected = choices.findViewWithTag(playback.selected());
        if (selected != null) choices.check(selected.getId());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        listening = false;
        if (layer != null) layer.remove();
        if (panel != null) panel.dismiss();
        panel = null;
        releaseIfIdle();
        super.onDestroy();
    }
}
