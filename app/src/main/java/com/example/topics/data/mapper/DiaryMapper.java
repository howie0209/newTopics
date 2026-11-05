package com.example.topics.data.mapper;

import com.example.topics.data.model.DiaryDto;
import com.example.topics.data.model.LocationDto;
import com.example.topics.data.model.MoodDto;
import com.example.topics.data.model.ReactionDto;
import com.example.topics.data.model.UserDto;

public class DiaryMapper {
    private DiaryMapper() {}

    public static DiaryUiModel toUiModel(DiaryDto diary, String currentUserId) {
        LocationDto location = diary.location;
        Double lat = location == null ? null : location.getLatitude();
        Double lng = location == null ? null : location.getLongitude();
        if (lat == null || lng == null) return null;

        MoodDto mood = diary.getMood();
        ReactionDto reactions = diary.getReactions();
        UserDto author = diary.getAuthor();
        String authorId = author == null ? "" : author.getId();
        String imageUrl = diary.getImages().isEmpty() ? "" : diary.getImages().get(0);
        String placeName = location == null || location.placeName == null ? "" : location.placeName;
        String authorAvatar = author == null || author.avatar == null ? "" : author.avatar;
        boolean isMine = sameId(authorId, currentUserId);

        return new DiaryUiModel(
                diary.getId(),
                lat,
                lng,
                diary.getTitle(),
                mood.getType(),
                mood.getIntensity(),
                diary.getText(),
                diary.createdAt == null ? "" : diary.createdAt,
                imageUrl,
                placeName,
                authorAvatar,
                diary.userReaction == null ? "" : diary.userReaction,
                diary.canEdit,
                isMine,
                visibilityToIndex(diary.getVisibility()),
                author == null ? "" : author.getDisplayName(),
                reactions.understand,
                reactions.hug,
                reactions.relate
        );
    }

    public static String moodApiValueFromSpinner(String value) {
        if (value == null) return "other";
        if (value.contains("開心")) return "joy";
        if (value.contains("難過")) return "sad";
        if (value.contains("平靜")) return "calm";
        if (value.contains("興奮")) return "wonder";
        if (value.contains("累")) return "nostalgic";
        return "other";
    }

    // 對齊網頁版 constants/app.js 的 MOOD_LABELS（去掉前面的符號）
    public static String moodLabel(String moodType) {
        if ("calm".equals(moodType)) return "平靜";
        if ("joy".equals(moodType) || "happy".equals(moodType)) return "喜悅";
        if ("sad".equals(moodType)) return "低落";
        if ("wonder".equals(moodType) || "excited".equals(moodType)) return "驚奇";
        if ("anxious".equals(moodType)) return "焦慮";
        if ("confused".equals(moodType)) return "疑惑";
        if ("nostalgic".equals(moodType)) return "懷舊";
        return "其他";
    }

    // 對齊網頁版 MOOD_LABELS（含符號），用在日記詳細與動態牆
    public static String moodDisplayLabel(String moodType) {
        if ("calm".equals(moodType)) return "○ 平靜";
        if ("joy".equals(moodType) || "happy".equals(moodType)) return "✦ 喜悅";
        if ("sad".equals(moodType)) return "● 低落";
        if ("wonder".equals(moodType) || "excited".equals(moodType)) return "◇ 驚奇";
        if ("anxious".equals(moodType)) return "△ 焦慮";
        if ("confused".equals(moodType)) return "◔ 疑惑";
        if ("nostalgic".equals(moodType)) return "● 懷舊";
        return "• 其他";
    }

    // 地圖標記顏色，對齊網頁版 constants/moodStyles.js 的 MOOD_MARKER_STYLES
    public static int moodMarkerColor(String moodType) {
        if ("calm".equals(moodType)) return 0xFF70E6D2;
        if ("joy".equals(moodType) || "happy".equals(moodType)) return 0xFFF4D278;
        if ("sad".equals(moodType)) return 0xFF78AAE6;
        if ("anxious".equals(moodType)) return 0xFFAA8CF0;
        if ("confused".equals(moodType)) return 0xFF818CF8;
        if ("angry".equals(moodType)) return 0xFFF08278;
        if ("wonder".equals(moodType) || "excited".equals(moodType)) return 0xFFDCBCFF;
        if ("nostalgic".equals(moodType)) return 0xFFC9AD84;
        return 0xFF7DD3FC;
    }

    // 可見性文字，對齊網頁版 visibilityLabels
    public static String visibilityLabel(String visibility) {
        if ("friends".equals(visibility)) return "好友";
        if ("public".equals(visibility)) return "公開";
        return "私人";
    }

    public static String visibilityApiValue(int visibility) {
        if (visibility == 1) return "friends";
        if (visibility == 2) return "public";
        return "private";
    }

    public static int visibilityToIndex(String visibility) {
        if ("friends".equals(visibility)) return 1;
        if ("public".equals(visibility)) return 2;
        return 0;
    }

    private static boolean sameId(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.equals(b);
    }
}
