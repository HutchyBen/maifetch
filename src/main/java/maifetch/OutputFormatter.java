package maifetch;

import maifetch.api.Models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OutputFormatter {
    private OutputFormatter() {
    }

    public static List<String> createInfoLines(Models.Profile profile, List<Models.Play> plays, int scoreCount) {
        String name = wideToNormal(profile.name);
        List<String> lines = new ArrayList<String>();
        lines.add(color(name));
        lines.add(repeat("-", name.length()));
        lines.add(color("ID") + ": " + profile.id);
        lines.add(String.format("%s: %.2f / %.2f", color("Rating"), profile.rating / 100.0, profile.ratingHighest / 100.0));
        lines.add(color("Level") + ": " + profile.level);
        lines.add(color("Total Credits") + ": " + profile.playStats.total);
        lines.add(color("Recent Scores") + ":");

        int limit = Math.min(scoreCount, plays.size());
        for (int i = 0; i < limit; i++) {
            Models.Play play = plays.get(i);
            String fullCombo = play.fullComboLabel == null ? "" : play.fullComboLabel;
            lines.add("  " + play.song.name.en + "  " + difficultyString(play.difficultyLevel.value));
            lines.add("  " + play.scoreFormatted + " " + play.achievementFormatted + "% "
                    + rankString(play.rank) + " " + fullCombo);
            lines.add("");
        }
        return Collections.unmodifiableList(lines);
    }

    public static String renderWithoutLogo(Models.Profile profile, List<Models.Play> plays, int scoreCount) {
        return joinLines(createInfoLines(profile, plays, scoreCount));
    }

    public static String renderCombined(List<String> infoLines, List<String> logoLines, int logoSize) {
        int maxLength = Math.max(infoLines.size(), logoLines.size());
        StringBuilder output = new StringBuilder();
        String blankLogo = repeat(" ", Math.max(0, logoSize * 2));
        for (int i = 0; i < maxLength; i++) {
            String logo = i < logoLines.size() ? logoLines.get(i) : blankLogo;
            String info = i < infoLines.size() ? infoLines.get(i) : "";
            output.append(logo).append("   ").append(info);
            if (i + 1 < maxLength) {
                output.append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    public static String wideToNormal(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\u3000') {
                builder.append(' ');
            } else if (ch >= '\uFF01' && ch <= '\uFF5E') {
                builder.append((char) (ch - 0xFEE0));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public static String color(String text) {
        return Ansi.foreground(text, 72, 184, 200);
    }

    public static String difficultyString(String difficulty) {
        if ("easy".equals(difficulty)) {
            return Ansi.foregroundBackground("Easy", 255, 255, 255, 69, 174, 255);
        }
        if ("basic".equals(difficulty)) {
            return Ansi.foregroundBackground("Basic", 255, 255, 255, 111, 212, 61);
        }
        if ("advanced".equals(difficulty)) {
            return Ansi.foregroundBackground("Advanced", 255, 255, 255, 248, 183, 9);
        }
        if ("expert".equals(difficulty)) {
            return Ansi.foregroundBackground("Expert", 255, 255, 255, 255, 46, 66);
        }
        if ("master".equals(difficulty)) {
            return Ansi.foregroundBackground("Master", 255, 255, 255, 171, 140, 233);
        }
        if ("remaster".equals(difficulty) || "re:master".equals(difficulty)) {
            return Ansi.foregroundBackground("Re:Master", 255, 255, 255, 207, 114, 237);
        }
        if ("utage".equals(difficulty)) {
            return Ansi.foregroundBackground("Utage", 255, 255, 255, 255, 68, 1);
        }
        return difficulty == null ? "" : difficulty;
    }

    public static String rankString(String rank) {
        if ("SSS+".equals(rank)) {
            return Ansi.foreground("S", 255, 200, 54)
                    + Ansi.foreground("S", 225, 38, 165)
                    + Ansi.foreground("S", 73, 64, 233)
                    + Ansi.foreground("+", 21, 203, 148);
        }
        if ("SSS".equals(rank)) {
            return Ansi.foreground("S", 255, 200, 54)
                    + Ansi.foreground("S", 232, 39, 148)
                    + Ansi.foreground("S", 18, 195, 144);
        }
        if ("SS+".equals(rank) || "SS".equals(rank)) {
            return Ansi.foregroundBackground(rank, 248, 200, 75, 143, 71, 33);
        }
        if ("S+".equals(rank) || "S".equals(rank)) {
            return Ansi.foregroundBackground(rank, 248, 200, 75, 75, 82, 82);
        }
        if ("AAA".equals(rank) || "AA".equals(rank) || "A".equals(rank)) {
            return Ansi.foreground(rank, 23, 163, 255);
        }
        return rank == null ? "" : rank;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i + 1 < lines.size()) {
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
