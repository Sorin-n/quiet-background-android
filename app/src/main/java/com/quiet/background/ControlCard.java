package com.quiet.background;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class ControlCard extends LinearLayout {
    private static final int CARD = 0xff232426, SURFACE = 0xff37383b, TRACK = 0xff414347;
    private static final int WHITE = 0xfffafafa, SECONDARY = 0xffc6c7ca, BLUE = 0xff3478f6;
    private final LinearLayout body;
    private final TextView title;
    private PlaybackService playback;
    private final Runnable togglePower;
    private ImageButton power;
    private TextView soundName, playState, percent, volumeMode;
    private SeekBar gain;
    private enum Page { MAIN, SOUNDS, SETTINGS }
    private Page page;

    ControlCard(Context context, Runnable togglePower, Runnable close) {
        super(context);
        this.togglePower = togglePower;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(6), dp(16), dp(8));
        setBackground(shape(CARD, 24));
        setClipToOutline(true);
        LinearLayout header = row(this, 48);
        title = label("背景音", 19, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LayoutParams(0, -2, 1));
        ImageButton collapse = new ImageButton(context);
        collapse.setImageResource(R.drawable.ic_collapse);
        collapse.setContentDescription("收回");
        collapse.setColorFilter(SECONDARY);
        collapse.setBackground(clickable(CARD, 24));
        collapse.setOnClickListener(v -> close.run());
        header.addView(collapse, dp(48), dp(48));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LayoutParams(-1, 0, 1));
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        scroll.addView(body);
        show(Page.MAIN);
    }

    private void show(Page next) {
        page = next;
        body.removeAllViews();
        ((ScrollView) body.getParent()).scrollTo(0, 0);
        title.setText(next == Page.MAIN ? "背景音" : next == Page.SOUNDS ? "‹  选择声音" : "‹  背景音设置");
        title.setContentDescription(next == Page.MAIN ? "背景音" : "返回背景音");
        title.setMinHeight(dp(48));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(next == Page.MAIN ? null : v -> show(Page.MAIN));
        if (next == Page.SOUNDS) {
            for (SoundCatalog.Sound entry : SoundCatalog.ALL) {
                boolean selected = playback != null && entry.key().equals(playback.selected());
                TextView option = action(entry.name() + (selected ? "   ✓" : ""), selected ? BLUE : SURFACE);
                option.setOnClickListener(v -> {
                    if (playback != null) playback.select(entry.key());
                    show(Page.MAIN);
                });
                body.addView(option, new LayoutParams(-1, dp(48)));
            }
            return;
        }
        if (next == Page.SETTINGS) {
            SharedPreferences prefs = getContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
            Switch screen = new Switch(getContext());
            screen.setText("息屏时关闭背景音");
            screen.setTextColor(WHITE);
            screen.setTextSize(16);
            screen.setMinHeight(dp(52));
            screen.setThumbTintList(ColorStateList.valueOf(WHITE));
            screen.setTrackTintList(new ColorStateList(new int[][]{
                {android.R.attr.state_checked}, {}}, new int[]{BLUE, TRACK}));
            screen.setChecked(prefs.getBoolean(PlaybackService.STOP_ON_SCREEN_OFF, false));
            screen.setOnCheckedChangeListener((v, checked) -> prefs.edit()
                .putBoolean(PlaybackService.STOP_ON_SCREEN_OFF, checked).apply());
            body.addView(screen);
            return;
        }
        LinearLayout player = row(body, 52);
        LinearLayout names = new LinearLayout(getContext());
        names.setOrientation(VERTICAL);
        player.addView(names, new LayoutParams(0, -2, 1));
        soundName = label("", 18, WHITE);
        playState = label("", 13, SECONDARY);
        names.addView(soundName);
        names.addView(playState);
        power = new ImageButton(getContext());
        power.setColorFilter(WHITE);
        power.setOnClickListener(v -> togglePower.run());
        player.addView(power, dp(48), dp(48));
        LinearLayout volumeRow = row(body, 28);
        volumeMode = label("背景音量", 14, SECONDARY);
        volumeRow.addView(volumeMode, new LayoutParams(0, -2, 1));
        percent = label("", 14, WHITE);
        volumeRow.addView(percent);
        gain = new VolumeBar(getContext());
        gain.setContentDescription("背景音量百分比");
        gain.setMax(100);
        body.addView(gain, new LayoutParams(-1, dp(46)));
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && playback != null) playback.setPercent(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout actions = row(body, 48);
        TextView sounds = action("切换声音", SURFACE);
        sounds.setOnClickListener(v -> show(Page.SOUNDS));
        actions.addView(sounds, new LayoutParams(0, dp(40), 1));
        TextView settings = action("背景音设置", SURFACE);
        settings.setOnClickListener(v -> show(Page.SETTINGS));
        LayoutParams size = new LayoutParams(0, dp(40), 1);
        size.leftMargin = dp(8);
        actions.addView(settings, size);
        update(playback);
    }

    void update(PlaybackService service) {
        playback = service;
        if (page != Page.MAIN) return;
        power.setEnabled(service != null);
        gain.setEnabled(service != null);
        if (service == null) { playState.setText("连接中"); return; }
        boolean on = service.playing();
        soundName.setText(SoundCatalog.find(service.selected()).name());
        playState.setText(on ? "正在播放" : "已关闭");
        power.setImageResource(on ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        power.setContentDescription(on ? "关闭背景音" : "开启背景音");
        power.setBackground(clickable(on ? BLUE : SURFACE, 24));
        volumeMode.setText(service.volumeLabel());
        percent.setText(service.percent() + "%");
        gain.setProgress(service.percent());
    }

    private final class VolumeBar extends SeekBar {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        VolumeBar(Context context) {
            super(context);
            setPadding(dp(12), 0, dp(12), 0);
            setBackground(null);
        }
        @Override protected synchronized void onDraw(Canvas canvas) {
            float y = getHeight() / 2f, r = dp(9);
            float fraction = (float) getProgress() / getMax();
            float x = getPaddingLeft() + fraction * (getWidth() - getPaddingLeft() - getPaddingRight());
            paint.setColor(TRACK);
            canvas.drawRoundRect(0, y-r, getWidth(), y+r, r, r, paint);
            paint.setColor(BLUE);
            canvas.drawRoundRect(0, y-r, x+r, y+r, r, r, paint);
            paint.setColor(WHITE);
            canvas.drawCircle(x, y, dp(6), paint);
        }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }
    private RippleDrawable clickable(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape(color, radius), null);
    }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(getContext());
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }
    private TextView action(String text, int color) {
        TextView v = label(text, 15, WHITE);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setBackground(clickable(color, 20));
        return v;
    }
    private LinearLayout row(LinearLayout parent, int height) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, new LayoutParams(-1, dp(height)));
        return r;
    }
}
