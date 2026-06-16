package maifetch

object Ansi {
    fun fg(text: String, r: Int, g: Int, b: Int): String = "\u001b[38;2;$r;$g;${b}m$text\u001b[0m"

    fun color(text: String, fgR: Int, fgG: Int, fgB: Int, bgR: Int, bgG: Int, bgB: Int): String =
        "\u001b[38;2;$fgR;$fgG;${fgB}m\u001b[48;2;$bgR;$bgG;${bgB}m$text\u001b[0m"
}
