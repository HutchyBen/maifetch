package maifetch

import java.io.File

data class Config(
    val accessToken: String,
    val configFile: File?,
    val logoSize: Int,
    val scoreCount: Int,
)
