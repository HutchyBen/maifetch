package maifetch

object Ansi {
    fun foreground(text: String, red: Int, green: Int, blue: Int): String =
        "\u001B[38;2;$red;$green;${blue}m$text\u001B[0m"

    fun foregroundBackground(
        text: String,
        foregroundRed: Int,
        foregroundGreen: Int,
        foregroundBlue: Int,
        backgroundRed: Int,
        backgroundGreen: Int,
        backgroundBlue: Int,
    ): String =
        "\u001B[38;2;$foregroundRed;$foregroundGreen;$foregroundBlue;48;2;$backgroundRed;$backgroundGreen;${backgroundBlue}m$text\u001B[0m"
}
