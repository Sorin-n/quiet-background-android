package com.quiet.background;
import org.junit.Test;
import static org.junit.Assert.*;
public class VolumeTest {
    @Test public void gainHasCorrectEndpointsAndClamps() {
        assertEquals(0f, Volume.gain(-20), 0f); assertEquals(0f, Volume.gain(0), 0f);
        assertEquals(.5f, Volume.gain(50), 0f); assertEquals(1f, Volume.gain(100), 0f);
        assertEquals(1f, Volume.gain(150), 0f);
    }
    @Test public void numericSegmentOrderDoesNotSortTenBeforeTwo() {
        assertTrue(SoundCatalog.segment("Rain_2.m4a") < SoundCatalog.segment("Rain_10.m4a"));
        assertEquals(0, SoundCatalog.segment("WhiteNoise.m4a"));
    }
    @Test public void invalidSavedSoundRecoversToRain() { assertEquals("Rain", SoundCatalog.find("missing").key()); }
}
