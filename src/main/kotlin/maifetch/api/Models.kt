package maifetch.api

import java.math.BigDecimal

object Models {
    data class ImageInfo(
        val id: Int = 0,
        val png: String = "",
        val webp: String = "",
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): ImageInfo =
                if (map == null) ImageInfo() else ImageInfo(intValue(map["id"]), stringValue(map["png"]), stringValue(map["webp"]))
        }
    }

    data class LocalizedName(
        val en: String = "",
        val jp: String = "",
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): LocalizedName =
                if (map == null) LocalizedName() else LocalizedName(stringValue(map["en"]), stringValue(map["jp"]))
        }
    }

    data class TrackInfo(
        val id: Int = 0,
        val code: String = "",
        val name: LocalizedName = LocalizedName(),
        val artist: LocalizedName = LocalizedName(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): TrackInfo =
                if (map == null) {
                    TrackInfo()
                } else {
                    TrackInfo(
                        intValue(map["id"]),
                        stringValue(map["code"]),
                        LocalizedName.fromMap(asMap(map["name"])),
                        LocalizedName.fromMap(asMap(map["artist"])),
                    )
                }
        }
    }

    data class DifficultyLevel(
        val key: Int = 0,
        val value: String = "",
        val label: String = "",
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): DifficultyLevel =
                if (map == null) DifficultyLevel() else DifficultyLevel(intValue(map["key"]), stringValue(map["value"]), stringValue(map["label"]))
        }
    }

    data class Notes(
        val perfect: Int = 0,
        val great: Int = 0,
        val good: Int = 0,
        val bad: Int = 0,
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): Notes =
                if (map == null) {
                    Notes()
                } else {
                    Notes(
                        intValue(map["perfect"]),
                        intValue(map["great"]),
                        intValue(map["good"]),
                        intValue(map["bad"]),
                    )
                }
        }
    }

    data class ScoreDetail(
        val hits: Notes = Notes(),
        val tap: Notes = Notes(),
        val hold: Notes = Notes(),
        val slide: Notes = Notes(),
        val breakNotes: Notes = Notes(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): ScoreDetail =
                if (map == null) {
                    ScoreDetail()
                } else {
                    ScoreDetail(
                        Notes.fromMap(asMap(map["hits"])),
                        Notes.fromMap(asMap(map["tap"])),
                        Notes.fromMap(asMap(map["hold"])),
                        Notes.fromMap(asMap(map["slide"])),
                        Notes.fromMap(asMap(map["break"])),
                    )
                }
        }
    }

    data class PlayStats(
        val total: Int = 0,
        val wins: Int = 0,
        val vs: Int = 0,
        val sync: Int = 0,
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): PlayStats =
                if (map == null) {
                    PlayStats()
                } else {
                    PlayStats(
                        intValue(map["total"]),
                        intValue(map["wins"]),
                        intValue(map["vs"]),
                        intValue(map["sync"]),
                    )
                }
        }
    }

    data class ProfileOptions(
        val icon: ImageInfo = ImageInfo(),
        val iconDeka: ImageInfo = ImageInfo(),
        val nameplate: ImageInfo = ImageInfo(),
        val frame: ImageInfo = ImageInfo(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): ProfileOptions =
                if (map == null) {
                    ProfileOptions()
                } else {
                    ProfileOptions(
                        ImageInfo.fromMap(asMap(map["icon"])),
                        ImageInfo.fromMap(asMap(map["icon_deka"])),
                        ImageInfo.fromMap(asMap(map["nameplate"])),
                        ImageInfo.fromMap(asMap(map["frame"])),
                    )
                }
        }
    }

    data class Profile(
        val id: Int = 0,
        val name: String = "",
        val rating: Int = 0,
        val ratingHighest: Int = 0,
        val level: Int = 0,
        val playStats: PlayStats = PlayStats(),
        val options: ProfileOptions = ProfileOptions(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): Profile =
                if (map == null) {
                    Profile()
                } else {
                    Profile(
                        intValue(map["id"]),
                        stringValue(map["name"]),
                        intValue(map["rating"]),
                        intValue(map["rating_highest"]),
                        intValue(map["level"]),
                        PlayStats.fromMap(asMap(map["play_stats"])),
                        ProfileOptions.fromMap(asMap(map["options"])),
                    )
                }
        }
    }

    data class Play(
        val id: Int = 0,
        val achievement: Int = 0,
        val achievementFormatted: String = "",
        val track: Int = 0,
        val score: Int = 0,
        val scoreFormatted: String = "",
        val scoreDetail: ScoreDetail = ScoreDetail(),
        val rank: String = "",
        val fullCombo: Int = 0,
        val fullComboLabel: String? = null,
        val highScore: Boolean = false,
        val allPerfect: Boolean = false,
        val trackSkip: Boolean = false,
        val difficultyLevel: DifficultyLevel = DifficultyLevel(),
        val playDateUnix: Long = 0L,
        val song: TrackInfo = TrackInfo(),
        val player: Profile = Profile(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): Play =
                if (map == null) {
                    Play()
                } else {
                    Play(
                        id = intValue(map["id"]),
                        achievement = intValue(map["achievement"]),
                        achievementFormatted = stringValue(map["achievement_formatted"]),
                        track = intValue(map["track"]),
                        score = intValue(map["score"]),
                        scoreFormatted = stringValue(map["score_formatted"]),
                        scoreDetail = ScoreDetail.fromMap(asMap(map["score_detail"])),
                        rank = stringValue(map["rank"]),
                        fullCombo = intValue(map["full_combo"]),
                        fullComboLabel = nullableString(map["full_combo_label"]),
                        highScore = booleanValue(map["is_high_score"]),
                        allPerfect = booleanValue(map["is_all_perfect"]),
                        trackSkip = booleanValue(map["is_track_skip"]),
                        difficultyLevel = DifficultyLevel.fromMap(asMap(map["difficulty_level"])),
                        playDateUnix = longValue(map["play_date_unix"]),
                        song = TrackInfo.fromMap(asMap(map["song"])),
                        player = Profile.fromMap(asMap(map["player"])),
                    )
                }
        }
    }

    data class Score(
        val id: Int = 0,
        val achievement: Int = 0,
        val achievementFormatted: String = "",
        val score: Int = 0,
        val scoreFormatted: String = "",
        val rank: String = "",
        val fullCombo: Int = 0,
        val fullComboLabel: String? = null,
        val allPerfect: Boolean = false,
        val allPerfectPlus: Boolean = false,
        val difficultyLevel: DifficultyLevel = DifficultyLevel(),
        val song: TrackInfo = TrackInfo(),
        val player: Profile = Profile(),
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): Score =
                if (map == null) {
                    Score()
                } else {
                    Score(
                        id = intValue(map["id"]),
                        achievement = intValue(map["achievement"]),
                        achievementFormatted = stringValue(map["achievement_formatted"]),
                        score = intValue(map["score"]),
                        scoreFormatted = stringValue(map["score_formatted"]),
                        rank = stringValue(map["rank"]),
                        fullCombo = intValue(map["full_combo"]),
                        fullComboLabel = nullableString(map["full_combo_label"]),
                        allPerfect = booleanValue(map["is_all_perfect"]),
                        allPerfectPlus = booleanValue(map["is_all_perfect_plus"]),
                        difficultyLevel = DifficultyLevel.fromMap(asMap(map["difficulty_level"])),
                        song = TrackInfo.fromMap(asMap(map["song"])),
                        player = Profile.fromMap(asMap(map["player"])),
                    )
                }
        }
    }

    data class Status(
        val description: String = "",
        val indicator: String = "",
    ) {
        companion object {
            fun fromMap(map: Map<String, Any?>?): Status =
                if (map == null) Status() else Status(stringValue(map["description"]), stringValue(map["indicator"]))
        }
    }

    fun interface Mapper<T> {
        fun map(input: Map<String, Any?>): T
    }

    fun <T> mapList(value: Any?, mapper: Mapper<T>): List<T> =
        asList(value).map { mapper.map(asMap(it).orEmpty()) }

    @Suppress("UNCHECKED_CAST")
    fun asMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun asList(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    fun stringValue(value: Any?): String = nullableString(value).orEmpty()

    fun nullableString(value: Any?): String? = value?.toString()

    fun intValue(value: Any?): Int =
        when (value) {
            is Number -> value.toInt()
            is String -> value.takeIf { it.isNotBlank() }?.let { BigDecimal(it).toInt() } ?: 0
            else -> 0
        }

    private fun longValue(value: Any?): Long =
        when (value) {
            is Number -> value.toLong()
            is String -> value.takeIf { it.isNotBlank() }?.let { BigDecimal(it).toLong() } ?: 0L
            else -> 0L
        }

    private fun booleanValue(value: Any?): Boolean =
        when (value) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> false
        }
}
