package maifetch;

public final class Ansi {
    private Ansi() {
    }

    public static String foreground(String text, int red, int green, int blue) {
        return "\u001B[38;2;" + red + ";" + green + ";" + blue + "m" + text + "\u001B[0m";
    }

    public static String foregroundBackground(
            String text,
            int foregroundRed,
            int foregroundGreen,
            int foregroundBlue,
            int backgroundRed,
            int backgroundGreen,
            int backgroundBlue
    ) {
        return "\u001B[38;2;" + foregroundRed + ";" + foregroundGreen + ";" + foregroundBlue
                + ";48;2;" + backgroundRed + ";" + backgroundGreen + ";" + backgroundBlue
                + "m" + text + "\u001B[0m";
    }
}
