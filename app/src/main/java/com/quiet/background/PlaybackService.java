package com.quiet.background;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.ArrayList;
import java.util.List;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlaybackService extends Service {
    public static final String PLAY = "com.quiet.background.PLAY", PAUSE = "com.quiet.background.PAUSE",
        STOP = "com.quiet.background.STOP";
    private static final String CHANNEL = "playback";
    private final LocalBinder binder = new LocalBinder();
    private ExoPlayer player;
    private MediaSession session;
    private String selected;
    private int soloPercent;
    private int mixedPercent;
    private boolean otherMedia;
    private AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkMedia = this::refreshMediaMode;
    private final AudioManager.AudioPlaybackCallback mediaCallback = new AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) {
            scheduleMediaCheck();
        }
    };

    private void scheduleMediaCheck() {
        handler.removeCallbacks(checkMedia);
        handler.postDelayed(checkMedia, 150);
    }

    private void refreshMediaMode() {
        // Public playback records are anonymous. Discount one matching ambient track
        // while ours is playing, and ignore calls, alerts and system UI sounds.
        boolean discountSelf = player.getPlayWhenReady();
        boolean external = false;
        for (AudioPlaybackConfiguration config : audio.getActivePlaybackConfigurations()) {
            android.media.AudioAttributes attributes = config.getAudioAttributes();
            int usage = attributes.getUsage();
            if (usage != C.USAGE_MEDIA && usage != C.USAGE_GAME && usage != C.USAGE_UNKNOWN) continue;
            if (discountSelf && usage == C.USAGE_MEDIA && attributes.getContentType() == C.AUDIO_CONTENT_TYPE_SONIFICATION) {
                discountSelf = false;
                continue;
            }
            external = true;
            break;
        }
        if (external == otherMedia) return;
        otherMedia = external;
        player.setVolume(Volume.gain(percent()));
        notifyControls();
    }
    private String error = "";
    private boolean foreground;
    private final java.util.Set<Runnable> controlsListeners = new java.util.HashSet<>();
    void addControlsListener(Runnable listener) { controlsListeners.add(listener); }
    void removeControlsListener(Runnable listener) { controlsListeners.remove(listener); }
    private void notifyControls() {
        for (Runnable listener : controlsListeners) listener.run();
    }
    static final String STOP_ON_SCREEN_OFF = "stopOnScreenOff";
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (playing() && getSharedPreferences("settings", MODE_PRIVATE)
                    .getBoolean(STOP_ON_SCREEN_OFF, false)) {
                stopPlayback();
            }
        }
    };
    public class LocalBinder extends Binder { public PlaybackService service() { return PlaybackService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onCreate() {
        super.onCreate();
        selected = SoundCatalog.find(getSharedPreferences("settings", 0).getString("sound", "Rain")).key();
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        soloPercent = Volume.clamp(prefs.getInt("volume", 40));
        mixedPercent = Volume.clamp(prefs.getInt("mixedVolume", soloPercent));
        if (!prefs.contains("mixedVolume")) prefs.edit().putInt("mixedVolume", mixedPercent).apply();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "背景音播放", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        player = new ExoPlayer.Builder(this)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION).build(), false)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build();
        player.setVolume(Volume.gain(percent()));
        audio = getSystemService(AudioManager.class);
        audio.registerAudioPlaybackCallback(mediaCallback, handler);
        refreshMediaMode();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        session = new MediaSession(this, "QuietBackground");
        session.setSessionActivity(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { startPlayback(selected); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onCustomAction(String action, Bundle extras) { if (STOP.equals(action)) stopPlayback(); }
        });
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { scheduleMediaCheck(); }
            @Override public void onEvents(Player p, Player.Events events) { updateState(); }
            @Override public void onPlayerError(PlaybackException e) {
                error = "音源播放失败，请重新选择音源后重试。";
                player.pause();
                updateState();
            }
        });
    }
    public String selected() { return selected; }
    public int percent() { return otherMedia ? mixedPercent : soloPercent; }
    String volumeLabel() { return otherMedia ? "与其他媒体同播音量" : "单独播放音量"; }
    public boolean playing() { return player.getPlayWhenReady() && player.getPlayerError() == null; }
    public String status() {
        if (!error.isEmpty()) return error;
        if (playing() && player.getPlaybackState() == Player.STATE_BUFFERING) return "正在准备音源…";
        return playing() ? "正在播放 · 可与其他应用共同播放" : "背景音已关闭";
    }
    public void setPercent(int value) {
        int percent = Volume.clamp(value);
        if (otherMedia) mixedPercent = percent;
        else soloPercent = percent;
        player.setVolume(Volume.gain(percent));
        getSharedPreferences("settings", 0).edit().putInt(otherMedia ? "mixedVolume" : "volume", percent).apply();
        notifyControls();
    }
    public void select(String key) {
        String next = SoundCatalog.find(key).key();
        if (next.equals(selected)) return;
        boolean resume = playing();
        selected = next;
        getSharedPreferences("settings", 0).edit().putString("sound", selected).apply();
        player.stop();
        player.clearMediaItems();
        error = "";
        if (resume) startPlayback(selected); else updateState();
    }
    public void startPlayback(String key) {
        // Foreground promotion must precede asynchronous player preparation.
        promote();
        try {
            String next = SoundCatalog.find(key).key();
            if (!selected.equals(next) || player.getMediaItemCount() == 0 || player.getPlayerError() != null) {
                selected = next;
                getSharedPreferences("settings", 0).edit().putString("sound", selected).apply();
                List<MediaItem> items = new ArrayList<>();
                for (String file : SoundCatalog.files(this, selected)) items.add(MediaItem.fromUri("asset:///audio/" + file));
                if (items.isEmpty()) throw new IllegalStateException("Missing audio");
                player.setMediaItems(items);
            }
            error = "";
            player.prepare();
            player.play();
            session.setActive(true);
            updateState();
        } catch (Exception e) {
            error = "无法打开音源，请重新安装完整安装包。";
            player.pause();
            updateState();
        }
    }
    public void pause() { player.pause(); updateState(); }
    void stopPlayback() {
        player.pause(); player.stop(); player.clearMediaItems();
        session.setActive(false);
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false;
        getSystemService(NotificationManager.class).cancel(1);
        stopSelf();
        notifyControls();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (STOP.equals(action)) stopPlayback();
            else if (PAUSE.equals(action)) pause();
            else if (PLAY.equals(action)) startPlayback(intent.getStringExtra("sound") == null ? selected : intent.getStringExtra("sound"));
        }
        return START_NOT_STICKY;
    }
    private PendingIntent command(String action, int id) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        return PLAY.equals(action) ? PendingIntent.getForegroundService(this, id, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
            : PendingIntent.getService(this, id, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification() {
        boolean active = playing();
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_wave)
            .setContentTitle(SoundCatalog.find(selected).name() + " · 静听")
            .setContentText(error.isEmpty() ? (active ? "背景播放中" : "已暂停") : error)
            .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(active)
            .addAction(new Notification.Action.Builder(active ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                active ? "暂停" : "播放", command(active ? PAUSE : PLAY, 1)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "停止", command(STOP, 2)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1)).build();
    }
    private void promote() {
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, notification());
        foreground = true;
    }
    private void updateState() {
        boolean active = playing();
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, SoundCatalog.find(selected).name())
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "静听·背景音").build());
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
            .addCustomAction(STOP, "停止", android.R.drawable.ic_menu_close_clear_cancel)
            .setState(active ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, player.getCurrentPosition(), active ? 1 : 0).build());
        if (active && !foreground) promote();
        if (foreground || session.isActive()) getSystemService(NotificationManager.class).notify(1, notification());
        if (!active && foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false; }
        notifyControls();
    }
    // Swiping away the activity deliberately leaves an active foreground playback service running.
    @Override public void onDestroy() {
        audio.unregisterAudioPlaybackCallback(mediaCallback);
        unregisterReceiver(screenOff);
        player.release(); handler.removeCallbacks(checkMedia); session.release();
        getSystemService(NotificationManager.class).cancel(1);
        super.onDestroy();
    }
}
