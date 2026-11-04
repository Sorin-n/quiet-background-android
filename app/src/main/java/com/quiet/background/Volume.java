package com.quiet.background;

public final class Volume {
    private Volume() {}
    public static int clamp(int percent) { return Math.max(0, Math.min(100, percent)); }
    public static float gain(int percent) { return clamp(percent) / 100f; }
}
