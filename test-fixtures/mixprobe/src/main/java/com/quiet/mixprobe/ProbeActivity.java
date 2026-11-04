package com.quiet.mixprobe;
import android.app.Activity;
import android.os.Bundle;
import android.media.*;
import android.widget.TextView;
public class ProbeActivity extends Activity {
    private MediaPlayer player;
    private AudioFocusRequest request;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AudioManager manager=getSystemService(AudioManager.class);
        TextView label=new TextView(this); label.setText("Independent app requests AUDIOFOCUS_GAIN and loops audio."); setContentView(label);
        request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener(change -> android.util.Log.i("MixProbe","focus="+change)).build();
        manager.requestAudioFocus(request);
        player=MediaPlayer.create(this, com.quiet.mixprobe.R.raw.tone); player.setLooping(true); player.start();
    }
    @Override public void onDestroy() { if(player!=null)player.release(); getSystemService(AudioManager.class).abandonAudioFocusRequest(request); super.onDestroy(); }
}
