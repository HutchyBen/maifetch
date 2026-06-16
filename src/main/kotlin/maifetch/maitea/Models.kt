package maifetch.maitea

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val id: Int = 0,
    val png: String = "",
    val webp: String = "",
)

@Serializable
data class LocalizedName(
    val en: String = "",
    val jp: String = "",
)

@Serializable
data class TrackInfo(
    val id: Int = 0,
    val code: String = "",
    val name: LocalizedName = LocalizedName(),
    val artist: LocalizedName = LocalizedName(),
)

@Serializable
data class DifficultyLevel(
    val key: Int = 0,
    val value: String = "",
    val label: String = "",
)

@Serializable
data class PlayStats(
    val total: Int = 0,
    val wins: Int = 0,
    val vs: Int = 0,
    val sync: Int = 0,
)

@Serializable
data class ProfileOptions(
    val icon: Image = Image(),
    @SerialName("icon_deka") val iconDeka: Image = Image(),
)

@Serializable
data class Profile(
    val id: Int = 0,
    val name: String = "",
    val rating: Int = 0,
    @SerialName("rating_highest") val ratingHighest: Int = 0,
    val level: Int = 0,
    @SerialName("play_stats") val playStats: PlayStats = PlayStats(),
    val options: ProfileOptions = ProfileOptions(),
)

@Serializable
data class Play(
    val id: Int = 0,
    val achievement: Int = 0,
    @SerialName("achievement_formatted") val achievementFormatted: String = "",
    val track: Int = 0,
    val score: Int = 0,
    @SerialName("score_formatted") val scoreFormatted: String = "",
    val rank: String = "",
    @SerialName("full_combo") val fullCombo: Int = 0,
    @SerialName("full_combo_label") val fullComboLabel: String? = null,
    @SerialName("difficulty_level") val difficultyLevel: DifficultyLevel = DifficultyLevel(),
    val song: TrackInfo = TrackInfo(),
    val player: Profile = Profile(),
)

@Serializable
data class DataEnvelope<T>(
    val data: T,
)

@Serializable
data class PageLinks(
    val first: String = "",
    val last: String = "",
    val prev: String? = null,
    val next: String? = null,
)

@Serializable
data class PagerPage<T>(
    val data: T,
    val links: PageLinks = PageLinks(),
)
