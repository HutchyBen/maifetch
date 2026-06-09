package com.github.hutchyben.maifetch;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class OutputRenderer {
    private OutputRenderer() {
    }

    static List<String> createInfoStrings(MaiteaModels.Profile profile, List<MaiteaModels.Play> plays, int scoreCount) {
        String name = wideToNormal(profile.name == null ? "" : profile.name);
        List<String> lines = new ArrayList<String>();
        lines.add(Console.colour(name));
        lines.add(repeat("-", name.length()));
        lines.add(Console.colour("ID") + ": " + profile.id);
        lines.add(String.format("%s: %.2f / %.2f", Console.colour("Rating"), profile.rating / 100.0f, profile.ratingHighest / 100.0f));
        lines.add(Console.colour("Level") + ": " + profile.level);
        int totalCredits = profile.playStats == null ? 0 : profile.playStats.total;
        lines.add(Console.colour("Total Credits") + ": " + totalCredits);
        lines.add(Console.colour("Recent Scores") + ":");

        int availableScores = Math.min(Math.max(scoreCount, 0), plays.size());
        for (int i = 0; i < availableScores; i++) {
            MaiteaModels.Play play = plays.get(i);
            String songName = play.song == null || play.song.name == null || play.song.name.en == null ? "" : play.song.name.en;
            String difficulty = play.difficultyLevel == null ? "" : play.difficultyLevel.value;
            String fullCombo = play.fullComboLabel == null ? "" : play.fullComboLabel;
            lines.add("  " + songName + "  " + Console.difficultyString(difficulty));
            lines.add("  " + nullToEmpty(play.scoreFormatted) + " " + nullToEmpty(play.achievementFormatted) + "% "
                + Console.rankString(nullToEmpty(play.rank)) + " " + fullCombo);
            lines.add("");
        }
        return lines;
    }

    static void output(List<MaiteaModels.Play> plays, MaiteaModels.Profile profile, int logoSize, int scoreCount, PrintStream out) throws IOException {
        List<String> infoLines = createInfoStrings(profile, plays, scoreCount);
        if (logoSize > 0 && profile.options != null && profile.options.icon != null && profile.options.icon.png != null) {
            String logo = urlToAscii(profile.options.icon.png, logoSize);
            printCombined(infoLines, splitLines(logo), logoSize, out);
        } else {
            for (String line : infoLines) {
                out.println(line);
            }
        }
    }

    static String wideToNormal(String value) {
        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (codePoint >= 0xFF01 && codePoint <= 0xFF5E) {
                builder.appendCodePoint(codePoint - 0xFEE0);
            } else if (codePoint == 0x3000) {
                builder.append(' ');
            } else {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    static String urlToAscii(String imageUrl, int size) throws IOException {
        BufferedImage source = ImageIO.read(new URL(imageUrl));
        if (source == null) {
            throw new IOException("could not decode image: " + imageUrl);
        }
        int width = Math.max(1, size * 2);
        int height = Math.max(1, size);
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        StringBuilder builder = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) {
                builder.append('\n');
            }
            for (int x = 0; x < width; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;
                if (alpha < 10) {
                    builder.append(Console.fg("#", 0, 0, 0));
                } else {
                    builder.append(Console.fg("#", red, green, blue));
                }
            }
        }
        return builder.toString();
    }

    private static void printCombined(List<String> infoLines, List<String> logoLines, int logoSize, PrintStream out) {
        int maxLength = Math.max(infoLines.size(), logoLines.size());
        String blankLogo = repeat(" ", logoSize * 2);
        for (int i = 0; i < maxLength; i++) {
            String logo = i < logoLines.size() ? logoLines.get(i) : blankLogo;
            String info = i < infoLines.size() ? infoLines.get(i) : "";
            out.println(logo + "  " + info);
        }
    }

    private static List<String> splitLines(String value) {
        List<String> lines = new ArrayList<String>();
        String[] parts = value.split("\\R", -1);
        for (String part : parts) {
            if (!part.isEmpty()) {
                lines.add(part);
            }
        }
        return lines;
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
