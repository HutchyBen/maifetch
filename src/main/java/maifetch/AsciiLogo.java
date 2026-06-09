package maifetch;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class AsciiLogo {
    private static final char[] RAMP = "@%#*+=-:. ".toCharArray();

    private AsciiLogo() {
    }

    public static List<String> fromUrl(String imageUrl, int size) throws IOException {
        BufferedImage image = ImageIO.read(new URL(imageUrl));
        if (image == null) {
            throw new IOException("could not decode profile icon");
        }
        int width = Math.max(1, size * 2);
        int height = Math.max(1, size);
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();

        List<String> rows = new ArrayList<String>();
        for (int y = 0; y < height; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < width; x++) {
                int rgb = target.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
                int index = luminance * (RAMP.length - 1) / 255;
                char ch = RAMP[index];
                row.append(Ansi.foreground(String.valueOf(ch == ' ' ? '#' : ch), red, green, blue));
            }
            rows.add(row.toString());
        }
        return rows;
    }
}
