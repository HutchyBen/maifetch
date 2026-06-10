package maifetch

import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

object AsciiLogo {
    private val ramp = "@%#*+=-:. ".toCharArray()

    fun fromUrl(imageUrl: String, size: Int): List<String> {
        val image = ImageIO.read(URL(imageUrl)) ?: throw java.io.IOException("could not decode profile icon")
        val width = maxOf(1, size * 2)
        val height = maxOf(1, size)
        val scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(scaled, 0, 0, null)
        graphics.dispose()

        return (0 until height).map { y ->
            buildString {
                for (x in 0 until width) {
                    val rgb = target.getRGB(x, y)
                    val red = (rgb shr 16) and 0xFF
                    val green = (rgb shr 8) and 0xFF
                    val blue = rgb and 0xFF
                    val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                    val index = luminance * (ramp.size - 1) / 255
                    val ch = if (ramp[index] == ' ') '#' else ramp[index]
                    append(Ansi.foreground(ch.toString(), red, green, blue))
                }
            }
        }
    }
}
