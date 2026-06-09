package maifetch.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {
    }

    public static final class ImageInfo {
        public final int id;
        public final String png;
        public final String webp;

        public ImageInfo(int id, String png, String webp) {
            this.id = id;
            this.png = png == null ? "" : png;
            this.webp = webp == null ? "" : webp;
        }

        static ImageInfo fromMap(Map<String, Object> map) {
            if (map == null) {
                return new ImageInfo(0, "", "");
            }
            return new ImageInfo(intValue(map.get("id")), stringValue(map.get("png")), stringValue(map.get("webp")));
        }
    }

    public static final class LocalizedName {
        public final String en;
        public final String jp;

        public LocalizedName(String en, String jp) {
            this.en = en == null ? "" : en;
            this.jp = jp == null ? "" : jp;
        }

        static LocalizedName fromMap(Map<String, Object> map) {
            if (map == null) {
                return new LocalizedName("", "");
            }
            return new LocalizedName(stringValue(map.get("en")), stringValue(map.get("jp")));
        }
    }

    public static final class TrackInfo {
        public final int id;
        public final String code;
        public final LocalizedName name;
        public final LocalizedName artist;

        public TrackInfo(int id, String code, LocalizedName name, LocalizedName artist) {
            this.id = id;
            this.code = code == null ? "" : code;
            this.name = name == null ? new LocalizedName("", "") : name;
            this.artist = artist == null ? new LocalizedName("", "") : artist;
        }

        static TrackInfo fromMap(Map<String, Object> map) {
            if (map == null) {
                return new TrackInfo(0, "", new LocalizedName("", ""), new LocalizedName("", ""));
            }
            return new TrackInfo(
                    intValue(map.get("id")),
                    stringValue(map.get("code")),
                    LocalizedName.fromMap(asMap(map.get("name"))),
                    LocalizedName.fromMap(asMap(map.get("artist")))
            );
        }
    }

    public static final class DifficultyLevel {
        public final int key;
        public final String value;
        public final String label;

        public DifficultyLevel(int key, String value, String label) {
            this.key = key;
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
        }

        static DifficultyLevel fromMap(Map<String, Object> map) {
            if (map == null) {
                return new DifficultyLevel(0, "", "");
            }
            return new DifficultyLevel(intValue(map.get("key")), stringValue(map.get("value")), stringValue(map.get("label")));
        }
    }

    public static final class Notes {
        public final int perfect;
        public final int great;
        public final int good;
        public final int bad;

        public Notes(int perfect, int great, int good, int bad) {
            this.perfect = perfect;
            this.great = great;
            this.good = good;
            this.bad = bad;
        }

        static Notes fromMap(Map<String, Object> map) {
            if (map == null) {
                return new Notes(0, 0, 0, 0);
            }
            return new Notes(
                    intValue(map.get("perfect")),
                    intValue(map.get("great")),
                    intValue(map.get("good")),
                    intValue(map.get("bad"))
            );
        }
    }

    public static final class ScoreDetail {
        public final Notes hits;
        public final Notes tap;
        public final Notes hold;
        public final Notes slide;
        public final Notes breakNotes;

        public ScoreDetail(Notes hits, Notes tap, Notes hold, Notes slide, Notes breakNotes) {
            this.hits = hits == null ? new Notes(0, 0, 0, 0) : hits;
            this.tap = tap == null ? new Notes(0, 0, 0, 0) : tap;
            this.hold = hold == null ? new Notes(0, 0, 0, 0) : hold;
            this.slide = slide == null ? new Notes(0, 0, 0, 0) : slide;
            this.breakNotes = breakNotes == null ? new Notes(0, 0, 0, 0) : breakNotes;
        }

        static ScoreDetail fromMap(Map<String, Object> map) {
            if (map == null) {
                return new ScoreDetail(null, null, null, null, null);
            }
            return new ScoreDetail(
                    Notes.fromMap(asMap(map.get("hits"))),
                    Notes.fromMap(asMap(map.get("tap"))),
                    Notes.fromMap(asMap(map.get("hold"))),
                    Notes.fromMap(asMap(map.get("slide"))),
                    Notes.fromMap(asMap(map.get("break")))
            );
        }
    }

    public static final class PlayStats {
        public final int total;
        public final int wins;
        public final int vs;
        public final int sync;

        public PlayStats(int total, int wins, int vs, int sync) {
            this.total = total;
            this.wins = wins;
            this.vs = vs;
            this.sync = sync;
        }

        static PlayStats fromMap(Map<String, Object> map) {
            if (map == null) {
                return new PlayStats(0, 0, 0, 0);
            }
            return new PlayStats(
                    intValue(map.get("total")),
                    intValue(map.get("wins")),
                    intValue(map.get("vs")),
                    intValue(map.get("sync"))
            );
        }
    }

    public static final class ProfileOptions {
        public final ImageInfo icon;
        public final ImageInfo iconDeka;
        public final ImageInfo nameplate;
        public final ImageInfo frame;

        public ProfileOptions(ImageInfo icon, ImageInfo iconDeka, ImageInfo nameplate, ImageInfo frame) {
            this.icon = icon == null ? new ImageInfo(0, "", "") : icon;
            this.iconDeka = iconDeka == null ? new ImageInfo(0, "", "") : iconDeka;
            this.nameplate = nameplate == null ? new ImageInfo(0, "", "") : nameplate;
            this.frame = frame == null ? new ImageInfo(0, "", "") : frame;
        }

        static ProfileOptions fromMap(Map<String, Object> map) {
            if (map == null) {
                return new ProfileOptions(null, null, null, null);
            }
            return new ProfileOptions(
                    ImageInfo.fromMap(asMap(map.get("icon"))),
                    ImageInfo.fromMap(asMap(map.get("icon_deka"))),
                    ImageInfo.fromMap(asMap(map.get("nameplate"))),
                    ImageInfo.fromMap(asMap(map.get("frame")))
            );
        }
    }

    public static final class Profile {
        public final int id;
        public final String name;
        public final int rating;
        public final int ratingHighest;
        public final int level;
        public final PlayStats playStats;
        public final ProfileOptions options;

        public Profile(int id, String name, int rating, int ratingHighest, int level, PlayStats playStats, ProfileOptions options) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.rating = rating;
            this.ratingHighest = ratingHighest;
            this.level = level;
            this.playStats = playStats == null ? new PlayStats(0, 0, 0, 0) : playStats;
            this.options = options == null ? new ProfileOptions(null, null, null, null) : options;
        }

        public static Profile fromMap(Map<String, Object> map) {
            if (map == null) {
                return new Profile(0, "", 0, 0, 0, null, null);
            }
            return new Profile(
                    intValue(map.get("id")),
                    stringValue(map.get("name")),
                    intValue(map.get("rating")),
                    intValue(map.get("rating_highest")),
                    intValue(map.get("level")),
                    PlayStats.fromMap(asMap(map.get("play_stats"))),
                    ProfileOptions.fromMap(asMap(map.get("options")))
            );
        }
    }

    public static final class Play {
        public final int id;
        public final int achievement;
        public final String achievementFormatted;
        public final int track;
        public final int score;
        public final String scoreFormatted;
        public final ScoreDetail scoreDetail;
        public final String rank;
        public final int fullCombo;
        public final String fullComboLabel;
        public final boolean highScore;
        public final boolean allPerfect;
        public final boolean trackSkip;
        public final DifficultyLevel difficultyLevel;
        public final long playDateUnix;
        public final TrackInfo song;
        public final Profile player;

        public Play(
                int id,
                int achievement,
                String achievementFormatted,
                int track,
                int score,
                String scoreFormatted,
                ScoreDetail scoreDetail,
                String rank,
                int fullCombo,
                String fullComboLabel,
                boolean highScore,
                boolean allPerfect,
                boolean trackSkip,
                DifficultyLevel difficultyLevel,
                long playDateUnix,
                TrackInfo song,
                Profile player
        ) {
            this.id = id;
            this.achievement = achievement;
            this.achievementFormatted = achievementFormatted == null ? "" : achievementFormatted;
            this.track = track;
            this.score = score;
            this.scoreFormatted = scoreFormatted == null ? "" : scoreFormatted;
            this.scoreDetail = scoreDetail == null ? new ScoreDetail(null, null, null, null, null) : scoreDetail;
            this.rank = rank == null ? "" : rank;
            this.fullCombo = fullCombo;
            this.fullComboLabel = fullComboLabel;
            this.highScore = highScore;
            this.allPerfect = allPerfect;
            this.trackSkip = trackSkip;
            this.difficultyLevel = difficultyLevel == null ? new DifficultyLevel(0, "", "") : difficultyLevel;
            this.playDateUnix = playDateUnix;
            this.song = song == null ? new TrackInfo(0, "", null, null) : song;
            this.player = player == null ? new Profile(0, "", 0, 0, 0, null, null) : player;
        }

        public static Play fromMap(Map<String, Object> map) {
            if (map == null) {
                return new Play(0, 0, "", 0, 0, "", null, "", 0, null, false, false, false, null, 0, null, null);
            }
            return new Play(
                    intValue(map.get("id")),
                    intValue(map.get("achievement")),
                    stringValue(map.get("achievement_formatted")),
                    intValue(map.get("track")),
                    intValue(map.get("score")),
                    stringValue(map.get("score_formatted")),
                    ScoreDetail.fromMap(asMap(map.get("score_detail"))),
                    stringValue(map.get("rank")),
                    intValue(map.get("full_combo")),
                    nullableString(map.get("full_combo_label")),
                    booleanValue(map.get("is_high_score")),
                    booleanValue(map.get("is_all_perfect")),
                    booleanValue(map.get("is_track_skip")),
                    DifficultyLevel.fromMap(asMap(map.get("difficulty_level"))),
                    longValue(map.get("play_date_unix")),
                    TrackInfo.fromMap(asMap(map.get("song"))),
                    Profile.fromMap(asMap(map.get("player")))
            );
        }
    }

    public static final class Score {
        public final int id;
        public final int achievement;
        public final String achievementFormatted;
        public final int score;
        public final String scoreFormatted;
        public final String rank;
        public final int fullCombo;
        public final String fullComboLabel;
        public final boolean allPerfect;
        public final boolean allPerfectPlus;
        public final DifficultyLevel difficultyLevel;
        public final TrackInfo song;
        public final Profile player;

        public Score(
                int id,
                int achievement,
                String achievementFormatted,
                int score,
                String scoreFormatted,
                String rank,
                int fullCombo,
                String fullComboLabel,
                boolean allPerfect,
                boolean allPerfectPlus,
                DifficultyLevel difficultyLevel,
                TrackInfo song,
                Profile player
        ) {
            this.id = id;
            this.achievement = achievement;
            this.achievementFormatted = achievementFormatted == null ? "" : achievementFormatted;
            this.score = score;
            this.scoreFormatted = scoreFormatted == null ? "" : scoreFormatted;
            this.rank = rank == null ? "" : rank;
            this.fullCombo = fullCombo;
            this.fullComboLabel = fullComboLabel;
            this.allPerfect = allPerfect;
            this.allPerfectPlus = allPerfectPlus;
            this.difficultyLevel = difficultyLevel == null ? new DifficultyLevel(0, "", "") : difficultyLevel;
            this.song = song == null ? new TrackInfo(0, "", null, null) : song;
            this.player = player == null ? new Profile(0, "", 0, 0, 0, null, null) : player;
        }

        public static Score fromMap(Map<String, Object> map) {
            if (map == null) {
                return new Score(0, 0, "", 0, "", "", 0, null, false, false, null, null, null);
            }
            return new Score(
                    intValue(map.get("id")),
                    intValue(map.get("achievement")),
                    stringValue(map.get("achievement_formatted")),
                    intValue(map.get("score")),
                    stringValue(map.get("score_formatted")),
                    stringValue(map.get("rank")),
                    intValue(map.get("full_combo")),
                    nullableString(map.get("full_combo_label")),
                    booleanValue(map.get("is_all_perfect")),
                    booleanValue(map.get("is_all_perfect_plus")),
                    DifficultyLevel.fromMap(asMap(map.get("difficulty_level"))),
                    TrackInfo.fromMap(asMap(map.get("song"))),
                    Profile.fromMap(asMap(map.get("player")))
            );
        }
    }

    public static final class Status {
        public final String description;
        public final String indicator;

        public Status(String description, String indicator) {
            this.description = description == null ? "" : description;
            this.indicator = indicator == null ? "" : indicator;
        }

        public static Status fromMap(Map<String, Object> map) {
            if (map == null) {
                return new Status("", "");
            }
            return new Status(stringValue(map.get("description")), stringValue(map.get("indicator")));
        }
    }

    public interface Mapper<T> {
        T map(Map<String, Object> input);
    }

    public static <T> List<T> mapList(Object value, Mapper<T> mapper) {
        List<Object> raw = asList(value);
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> mapped = new ArrayList<T>();
        for (Object item : raw) {
            mapped.add(mapper.map(asMap(item)));
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return Collections.emptyList();
    }

    static String stringValue(Object value) {
        String string = nullableString(value);
        return string == null ? "" : string;
    }

    static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return new BigDecimal((String) value).intValue();
        }
        return 0;
    }

    static long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return new BigDecimal((String) value).longValue();
        }
        return 0L;
    }

    static boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }
}
