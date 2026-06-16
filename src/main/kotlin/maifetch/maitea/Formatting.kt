package maifetch.maitea

import maifetch.Ansi

fun difficultyString(diff: String): String = when (diff) {
    "easy" -> Ansi.color("Easy", 255, 255, 255, 69, 174, 255)
    "basic" -> Ansi.color("Basic", 255, 255, 255, 111, 212, 61)
    "advanced" -> Ansi.color("Advanced", 255, 255, 255, 248, 183, 9)
    "expert" -> Ansi.color("Expert", 255, 255, 255, 255, 46, 66)
    "master" -> Ansi.color("Master", 255, 255, 255, 171, 140, 233)
    "remaster", "re:master" -> Ansi.color("Re:Master", 255, 255, 255, 207, 114, 237)
    "utage" -> Ansi.color("Utage", 255, 255, 255, 255, 68, 1)
    else -> diff
}

fun rankString(rank: String): String = when (rank) {
    "SSS+" -> Ansi.fg("S", 255, 200, 54) + Ansi.fg("S", 225, 38, 165) + Ansi.fg("S", 73, 64, 233) + Ansi.fg("+", 21, 203, 148)
    "SSS" -> Ansi.fg("S", 255, 200, 54) + Ansi.fg("S", 232, 39, 148) + Ansi.fg("S", 18, 195, 144)
    "SS+" -> Ansi.color("SS+", 248, 200, 75, 143, 71, 33)
    "SS" -> Ansi.color("SS", 248, 200, 75, 143, 71, 33)
    "S+" -> Ansi.color("S+", 248, 200, 75, 75, 82, 82)
    "S" -> Ansi.color("S", 248, 200, 75, 75, 82, 82)
    "AAA" -> Ansi.fg("AAA", 23, 163, 255)
    "AA" -> Ansi.fg("AA", 23, 163, 255)
    "A" -> Ansi.fg("A", 23, 163, 255)
    else -> rank
}
