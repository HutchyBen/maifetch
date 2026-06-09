package com.github.hutchyben.maifetch;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class MaiteaModels {
    private MaiteaModels() {
    }

    public static final class Envelope<T> {
        public T data;
    }

    public static final class Image {
        public int id;
        public String png;
        public String webp;
    }

    public static final class LocalizedName {
        public String en;
        public String jp;
    }

    public static final class TrackInfo {
        public int id;
        public String code;
        public LocalizedName name;
        public LocalizedName artist;
    }

    public static final class PlayStats {
        public int total;
        public int wins;
        public int vs;
        public int sync;
        public PlayStatEntry first;
        public PlayStatEntry latest;
    }

    public static final class PlayStatEntry {
        public int id;
        public String date;
        @SerializedName("date_unix")
        public int dateUnix;
        @SerializedName("api_route")
        public String apiRoute;
    }

    public static final class ProfileOptions {
        public Image icon;
        @SerializedName("icon_deka")
        public Image iconDeka;
        public Image nameplate;
        public Image frame;
    }

    public static final class Profile {
        public int id;
        public String name;
        public int rating;
        @SerializedName("rating_highest")
        public int ratingHighest;
        public int level;
        @SerializedName("play_stats")
        public PlayStats playStats;
        public ProfileOptions options;
    }

    public static final class Notes {
        public int perfect;
        public int great;
        public int good;
        public int bad;
    }

    public static final class ScoreDetail {
        public Notes hits;
        public Notes tap;
        public Notes hold;
        public Notes slide;
        @SerializedName("break")
        public Notes breaker;
    }

    public static final class DifficultyLevel {
        public int key;
        public String value;
        public String label;
    }

    public static class PlayLike {
        public int id;
        public int achievement;
        @SerializedName("achievement_formatted")
        public String achievementFormatted;
        public int score;
        @SerializedName("score_formatted")
        public String scoreFormatted;
        public String rank;
        @SerializedName("full_combo")
        public int fullCombo;
        @SerializedName("full_combo_label")
        public String fullComboLabel;
        @SerializedName("is_all_perfect")
        public boolean allPerfect;
        @SerializedName("difficulty_level")
        public DifficultyLevel difficultyLevel;
        public TrackInfo song;
        public Profile player;
    }

    public static final class Play extends PlayLike {
        public int track;
        @SerializedName("score_detail")
        public ScoreDetail scoreDetail;
        @SerializedName("is_high_score")
        public boolean highScore;
        @SerializedName("is_track_skip")
        public boolean trackSkip;
        @SerializedName("play_date")
        public String playDate;
        @SerializedName("play_date_unix")
        public int playDateUnix;
    }

    public static final class Score extends PlayLike {
        @SerializedName("is_all_perfect_plus")
        public boolean allPerfectPlus;
    }

    public static final class Status {
        public Webui webui;
        public Game game;
        @SerializedName("last_updated")
        public long lastUpdated;
    }

    public static final class Webui {
        public String api;
        @SerializedName("db_read")
        public DbStatus dbRead;
        @SerializedName("db_write")
        public DbStatus dbWrite;
    }

    public static final class DbStatus {
        public String status;
        @SerializedName("query_time")
        public String queryTime;
    }

    public static final class Game {
        public String status;
    }

    public static final class Page<T> {
        public T data;
        public Links links;
        public Meta meta;
    }

    public static final class Links {
        public String first;
        public String last;
        public String prev;
        public String next;
    }

    public static final class Meta {
        @SerializedName("current_page")
        public int currentPage;
        public int from;
        @SerializedName("last_page")
        public int lastPage;
        public List<MetaLink> links;
        public String path;
        @SerializedName("per_page")
        public int perPage;
        public int to;
        public int total;
    }

    public static final class MetaLink {
        public String url;
        public String label;
        public boolean active;
    }
}
