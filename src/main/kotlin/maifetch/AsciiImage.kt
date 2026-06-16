package maifetch

import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.max

private const val RAMP = "@%#*+=-:. "

fun urlToAscii(url: String, size: Int): String {
    val source = URI(url).toURL().openStream().use { ImageIO.read(it) }
    val width = max(1, size * 2)
    val height = max(1, size)
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = scaled.createGraphics()
    graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
    graphics.dispose()

    return buildString {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = scaled.getRGB(x, y)
                val r = rgb shr 16 and 0xff
                val g = rgb shr 8 and 0xff
                val b = rgb and 0xff
                val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
                val char = RAMP[(luminance * (RAMP.length - 1)) / 255]
                append(Ansi.fg(if (char == ' ') "#" else char.toString(), r, g, b))
            }
            append('\n')
        }
    }
}
