package com.quiet.background;

import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlaybackTest {
    @Rule public ServiceTestRule rule = new ServiceTestRule();
    private PlaybackService service;
    private ExoPlayer player;
    private Activity activity;
    private Context context;
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private boolean playing() {
        boolean[] value = {false}; main(() -> value[0] = player.isPlaying()); return value[0];
    }
    private void ready() throws Exception {
        for (int i=0; i<100 && !playing(); i++) Thread.sleep(100);
        assertTrue("Playback must reach READY and advance", playing());
    }
    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("settings", 0).edit().clear().commit();
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        IBinder binder = rule.bindService(new Intent(context, PlaybackService.class));
        service = ((PlaybackService.LocalBinder) binder).service();
        Field field = PlaybackService.class.getDeclaredField("player"); field.setAccessible(true); player = (ExoPlayer) field.get(service);
    }
    @After public void teardown() {
        main(() -> { service.pause(); activity.finish(); });
        context.stopService(new Intent(context, PlaybackService.class));
    }
    @Test public void everySoundPlaysAndSegmentOrderIsNumeric() throws Exception {
        assertEquals(15, SoundCatalog.ALL.size());
        int total=0;
        for (SoundCatalog.Sound sound : SoundCatalog.ALL) {
            total += SoundCatalog.files(context, sound.key()).size();
            main(() -> service.startPlayback(sound.key())); ready();
            main(() -> assertNull(player.getPlayerError()));
        }
        assertEquals(83, total);
        assertEquals("Rain_2.m4a", SoundCatalog.files(context,"Rain").get(1));
        assertEquals("Rain_10.m4a", SoundCatalog.files(context,"Rain").get(9));
    }
    @Test public void independentVolumeMixingAndBackgroundPlayback() throws Exception {
        AudioManager audio=context.getSystemService(AudioManager.class);
        int original=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        MediaPlayer other=new MediaPlayer();
        AudioFocusRequest focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener(change -> {}).build();
        try (AssetFileDescriptor fd=context.getAssets().openFd("audio/WhiteNoise.m4a")) {
            other.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build());
            other.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength()); other.setLooping(true); other.prepare();
            audio.requestAudioFocus(focus); other.start();
            main(() -> { service.startPlayback("Rain"); service.setPercent(50); assertEquals(.5f, player.getVolume(), .001f); }); ready();
            assertTrue(other.isPlaying()); assertEquals(original,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            main(() -> { service.setPercent(0); assertEquals(0f, player.getVolume(),0f); service.setPercent(100); assertEquals(1f,player.getVolume(),0f); });
            audio.abandonAudioFocusRequest(focus); audio.requestAudioFocus(focus);
            Thread.sleep(1000); assertTrue(playing()); assertTrue(other.isPlaying());
            main(() -> activity.moveTaskToBack(true));
            Thread.sleep(5000); assertTrue("Playback survives leaving activity",playing());
            assertEquals(original,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        } finally { other.release(); audio.abandonAudioFocusRequest(focus); }
    }
    @Test public void playlistLoopsAcrossLastSegment() throws Exception {
        main(() -> service.startPlayback("Rain")); ready();
        main(() -> player.seekTo(0, player.getDuration()-250));
        Thread.sleep(1600);
        main(() -> assertEquals(1,player.getCurrentMediaItemIndex()));
        main(() -> player.seekTo(player.getMediaItemCount()-1,0)); ready();
        Thread.sleep(300);
        main(() -> player.seekTo(player.getDuration()-250)); Thread.sleep(1600);
        main(() -> assertEquals(0,player.getCurrentMediaItemIndex()));
    }
}
