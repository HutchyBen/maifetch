package com.github.hutchyben.maifetch;

final class Console {
    private static final String RESET = "\u001B[0m";

    private Console() {
    }

    static String colour(String text) {
        return fg(text, 72, 184, 200);
    }

    static String difficultyString(String difficulty) {
        if ("easy".equals(difficulty)) {
            return fgBg("Easy", 255, 255, 255, 69, 174, 255);
        }
        if ("basic".equals(difficulty)) {
            return fgBg("Basic", 255, 255, 255, 111, 212, 61);
        }
        if ("advanced".equals(difficulty)) {
            return fgBg("Advanced", 255, 255, 255, 248, 183, 9);
        }
        if ("expert".equals(difficulty)) {
            return fgBg("Expert", 255, 255, 255, 255, 46, 66);
        }
        if ("master".equals(difficulty)) {
            return fgBg("Master", 255, 255, 255, 171, 140, 233);
        }
        if ("remaster".equals(difficulty) || "re:master".equals(difficulty)) {
            return fgBg("Re:Master", 255, 255, 255, 207, 114, 237);
        }
        if ("utage".equals(difficulty)) {
            return fgBg("Utage", 255, 255, 255, 255, 68, 1);
        }
        return difficulty;
    }

    static String rankString(String rank) {
        if ("SSS+".equals(rank)) {
            return fg("S", 255, 200, 54) + fg("S", 225, 38, 165) + fg("S", 73, 64, 233) + fg("+", 21, 203, 148);
        }
        if ("SSS".equals(rank)) {
            return fg("S", 255, 200, 54) + fg("S", 232, 39, 148) + fg("S", 18, 195, 144);
        }
        if ("SS+".equals(rank)) {
            return fgBg("SS+", 248, 200, 75, 143, 71, 33);
        }
        if ("SS".equals(rank)) {
            return fgBg("SS", 248, 200, 75, 143, 71, 33);
        }
        if ("S+".equals(rank)) {
            return fgBg("S+", 248, 200, 75, 75, 82, 82);
        }
        if ("S".equals(rank)) {
            return fgBg("S", 248, 200, 75, 75, 82, 82);
        }
        if ("AAA".equals(rank)) {
            return fg("AAA", 23, 163, 255);
        }
        if ("AA".equals(rank)) {
            return fg("AA", 23, 163, 255);
        }
        if ("A".equals(rank)) {
            return fg("A", 23, 163, 255);
        }
        return rank;
    }

    static String fg(String text, int r, int g, int b) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m" + text + RESET;
    }

    static String fgBg(String text, int fr, int fg, int fb, int br, int bg, int bb) {
        return "\u001B[38;2;" + fr + ";" + fg + ";" + fb + ";48;2;" + br + ";" + bg + ";" + bb + "m" + text + RESET;
    }
}
