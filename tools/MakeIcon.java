import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the program icon of Pinball: a table seen from the player's end, narrowing away, with a pop bumper,
 * two flippers and a chrome ball. Drawn as shapes on a 48 × 48 grid, so it is sharp at every size.
 *
 * <pre>
 * java tools/MakeIcon.java 48 src/main/resources/icons/amade-pinball-game.png
 * java tools/MakeIcon.java 256 build/icon-256.png
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-pinball-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 *
 * Run it in the repository's folder. The files are part of the repository; run this again only to change
 * the icon.
 */
public class MakeIcon {
    public static void main(String[] args) throws IOException {
        int size = Integer.parseInt(args[0]);
        File file = new File(args[1]);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 48.0, size / 48.0);
        paint(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    static void paint(Graphics2D g) {
        // The background, a dark rounded square.
        RoundRectangle2D frame = new RoundRectangle2D.Double(1, 1, 46, 46, 10, 10);
        g.setPaint(new GradientPaint(0, 1, new Color(0x1A1030), 0, 47, new Color(0x08060E)));
        g.fill(frame);
        g.setClip(frame);
        // The table, narrower at the far end, in a chrome rail.
        Path2D table = new Path2D.Double();
        table.moveTo(14, 5);
        table.lineTo(34, 5);
        table.lineTo(44, 46);
        table.lineTo(4, 46);
        table.closePath();
        g.setColor(new Color(0xB8B4C8));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(table);
        g.setPaint(new GradientPaint(0, 5, new Color(0x3A2470), 0, 46, new Color(0x6A3A9A)));
        g.fill(table);
        // Lamps in a fan towards the bumper.
        Color[] lamps = {new Color(0xFF5A8C), new Color(0xFFC83C), new Color(0x4AD8B0)};
        for (int i = 0; i < 3; i++) {
            g.setColor(lamps[i]);
            g.fill(new Ellipse2D.Double(19.5 + i * 3.6, 24 - Math.abs(i - 1) * 1.2, 2.4, 2.4));
        }
        // The pop bumper: a skirt and a striped cap.
        g.setColor(new Color(0x201838));
        g.fill(new Ellipse2D.Double(18.5, 12.5, 11, 6));
        g.setColor(new Color(0xF4F0FA));
        g.fill(new Ellipse2D.Double(19, 9.5, 10, 6));
        g.setColor(new Color(0xE83A6A));
        g.fill(new Ellipse2D.Double(21, 10.3, 6, 3.6));
        // The flippers, rubber-edged.
        flipper(g, 12, 36.5, 21, 40.5);
        flipper(g, 36, 36.5, 27, 40.5);
        // The chrome ball.
        g.setPaint(new RadialGradientPaint(new Point2D.Double(28.3, 27.3), 5f, new float[]{0f, 0.45f, 1f},
                new Color[]{Color.WHITE, new Color(0xB8C0D0), new Color(0x3A3850)}));
        g.fill(new Ellipse2D.Double(26, 25, 7, 7));
    }

    private static void flipper(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.setColor(new Color(0xD8343A));
        g.setStroke(new BasicStroke(4.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
        g.setColor(new Color(0xF4ECDC));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
    }
}
