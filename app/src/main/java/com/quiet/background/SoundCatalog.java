package com.quiet.background;

import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class SoundCatalog {
    public record Sound(String key, String name, String description, String group) {}
    public static final List<Sound> ALL = List.of(
        new Sound("Rain", "雨声", "连绵细雨，轻轻落下", "自然"),
        new Sound("Ocean", "海洋", "潮水起落，层层涌来", "自然"),
        new Sound("Stream", "溪流", "流水潺潺，缓缓流过", "自然"),
        new Sound("Night", "夜晚", "夜色里的自然声响", "自然"),
        new Sound("Fire", "炉火", "木柴燃烧的细碎声", "自然"),
        new Sound("RainOnRoof", "屋顶雨声", "雨滴敲打屋顶", "自然"),
        new Sound("WhiteNoise", "白噪音", "明亮、均匀的沙沙声", "噪音"),
        new Sound("PinkNoise", "粉红噪音", "柔和、平衡的底噪", "噪音"),
        new Sound("BrownNoise", "棕噪音", "低沉、厚实的底噪", "噪音"),
        new Sound("Steam", "蒸汽", "持续轻柔的气流声", "环境与旅途"),
        new Sound("Babble", "人声", "远处模糊的交谈声", "环境与旅途"),
        new Sound("Airplane", "飞机", "机舱里的稳定轰鸣", "环境与旅途"),
        new Sound("Boat", "船舶", "水上航行的背景声", "环境与旅途"),
        new Sound("Bus", "巴士", "行驶途中的陪伴", "环境与旅途"),
        new Sound("Train", "火车", "沿着铁轨，慢慢前行", "环境与旅途")
    );
    public static Sound find(String key) {
        for (Sound s : ALL) if (s.key().equals(key)) return s;
        return ALL.get(0);
    }
    static int segment(String name) {
        int underscore = name.lastIndexOf('_');
        return underscore < 0 ? 0 : Integer.parseInt(name.substring(underscore + 1, name.lastIndexOf('.')));
    }
    public static List<String> files(Context context, String key) throws IOException {
        String[] names = context.getAssets().list("audio");
        List<String> result = new ArrayList<>();
        if (names != null) for (String name : names) {
            if (name.equals(key + ".m4a") || name.startsWith(key + "_")) result.add(name);
        }
        result.sort(Comparator.comparingInt(SoundCatalog::segment));
        return result;
    }
}
