package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.Relief;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The parts of a table drawn from above with their heights, in table units. The same drawing serves the
 * table picture baked once ({@link Relief}) and the moving parts drawn every frame: each method takes a
 * {@link Shadows} that either collects the shadows to be blurred or paints them at once.
 */
final class Art {
    /** Where the shadow of a part {@code h} units high goes. */
    interface Shadows {
        void shadow(Shape footprint, double h);

        /** Shadows painted straight onto {@code g}, a little softer than the baked ones. */
        static Shadows direct(Graphics2D g) {
            return (s, h) -> {
                AffineTransform at = g.getTransform();
                Composite c = g.getComposite();
                g.translate(h * Relief.SHADOW_X, h * Relief.SHADOW_Y);
                g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                g.setColor(Color.BLACK);
                g.fill(s);
                g.setComposite(c);
                g.setTransform(at);
            };
        }

        Shadows NONE = (s, h) -> { };
    }

    private Art() {
    }

    static Color c(int rgb) {
        return new Color(rgb);
    }

    static Color c(int rgb, int a) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    static Color alpha(Color col, int a) {
        return new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 1).deriveFont(size);
    }

    /** Parts drawn every frame are lifted in fewer, plain slices: the frame must stay cheap. */
    static final ThreadLocal<Boolean> FAST = ThreadLocal.withInitial(() -> false);

    static void extrude(Graphics2D g, Shape s, double from, double to, Color side, Paint top) {
        if (!FAST.get()) {
            Relief.extrude(g, s, from, to, side, top);
            return;
        }
        AffineTransform at = g.getTransform();
        int n = Math.max(1, (int) Math.ceil((to - from) / 0.7));
        for (int i = 0; i < n; i++) {
            double h = from + (to - from) * i / n;
            g.setTransform(at);
            g.translate(0, -h);
            g.setColor(shade(side, 0.6 + 0.4 * i / n));
            g.fill(s);
        }
        g.setTransform(at);
        if (top != null) {
            g.translate(0, -to);
            g.setPaint(top);
            g.fill(s);
            g.setTransform(at);
        }
    }

    static Color shade(Color col, double f) {
        return Relief.shade(col, f);
    }

    // ---- floor pieces ---------------------------------------------------------------------------

    static void insert(Graphics2D g, double x, double y, double deg, Color col, double lit) {
        Path2D t = new Path2D.Double();
        t.moveTo(0, -5);
        t.lineTo(3.5, 3);
        t.lineTo(-3.5, 3);
        t.closePath();
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(deg));
        if (lit > 0) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 9f, new float[]{0f, 1f},
                    new Color[]{alpha(col, (int) (110 * lit)), alpha(col, 0)}));
            g.fill(new Ellipse2D.Double(-9, -9, 18, 18));
            g.setColor(alpha(col, (int) (255 * lit)));
        } else {
            g.setColor(new Color(col.getRed() / 4, col.getGreen() / 4, col.getBlue() / 4));
        }
        g.fill(t);
        if (lit > 0) {
            g.setColor(c(0xffffff, (int) (150 * lit)));
            g.fill(new Ellipse2D.Double(-1, -2, 2, 2));
        }
        g.setTransform(at);
    }

    static void roundInsert(Graphics2D g, double x, double y, double r, Color col, double lit) {
        if (lit > 0) {
            g.setColor(alpha(col, (int) (70 * lit)));
            g.fill(new Ellipse2D.Double(x - r * 1.9, y - r * 1.9, r * 3.8, r * 3.8));
            g.setColor(alpha(col, (int) (255 * lit)));
        } else {
            g.setColor(new Color(col.getRed() / 4, col.getGreen() / 4, col.getBlue() / 4));
        }
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        if (lit > 0) {
            g.setColor(c(0xffffff, (int) (140 * lit)));
            g.fill(new Ellipse2D.Double(x - r * 0.4, y - r * 0.6, r * 0.6, r * 0.5));
        }
    }

    static void label(Graphics2D g, String s, double x, double y, Color col, float size) {
        g.setFont(font(Font.BOLD, size));
        g.setColor(col);
        g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s) / 2.0), (float) y);
    }

    static void modeRingBase(Graphics2D g, double x, double y, double r, Color on, Color star) {
        g.setColor(c(0x000000, 70));
        g.fill(new Ellipse2D.Double(x - r - 6, y - r - 6, 2 * r + 12, 2 * r + 12));
        g.setColor(alpha(on, 70));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Ellipse2D.Double(x - r - 6, y - r - 6, 2 * r + 12, 2 * r + 12));
        g.setColor(new Color(star.getRed() / 3, star.getGreen() / 3, star.getBlue() / 3));
        g.fill(star(x, y, r));
        g.setColor(alpha(star, 120));
        g.draw(star(x, y, r));
    }

    static Shape star(double x, double y, double r) {
        Path2D s = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double an = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r * 0.62 : r * 0.27;
            if (i == 0) s.moveTo(x + Math.cos(an) * rr, y + Math.sin(an) * rr);
            else s.lineTo(x + Math.cos(an) * rr, y + Math.sin(an) * rr);
        }
        s.closePath();
        return s;
    }

    /** Where the lamp of mode {@code i} of eight sits on a ring. */
    static Point2D.Double ringLamp(double x, double y, double r, int i) {
        double an = -Math.PI / 2 + i * Math.PI * 2 / 8;
        return new Point2D.Double(x + Math.cos(an) * r, y + Math.sin(an) * r);
    }

    static void saucer(Graphics2D g, Shadows sh, double x, double y, Color rim) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y + 1), 6f, new float[]{0f, 0.7f, 1f},
                new Color[]{c(0x000000), c(0x101018), c(0x40404c)}));
        g.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
        Shape ring = new BasicStroke(1.6f).createStrokedShape(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
        extrude(g, ring, 0, 1.5, shade(rim, 0.7), rim);
        g.setColor(c(0xa0a0b0));
        g.fill(new RoundRectangle2D.Double(x - 2, y + 1, 4, 3, 1, 1));
    }

    static void rollover(Graphics2D g, double x, double y) {
        g.setColor(c(0x000000, 160));
        g.fill(new RoundRectangle2D.Double(x - 1, y - 5, 2, 10, 1, 1));
        g.setColor(c(0xe0e0ea));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Arc2D.Double(x - 2.5, y - 5, 5, 10, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(x - 2.5, y, x - 2.5, y - 2));
        g.draw(new Line2D.Double(x + 2.5, y, x + 2.5, y - 2));
    }

    static void whirlpool(Graphics2D g, double x, double y, double r) {
        for (int i = 0; i < 4; i++) {
            g.setColor(c(0x7ae8ff, 60 - i * 10));
            g.setStroke(new BasicStroke(1f));
            double w = r * 0.8 + i * r * 0.4, h = r * 0.5 + i * r * 0.25;
            g.draw(new Arc2D.Double(x - w / 2, y - h / 2, w, h, i * 40, 260, Arc2D.OPEN));
        }
    }

    // ---- raised parts ---------------------------------------------------------------------------------

    static void wallPath(Graphics2D g, Shadows sh, Color wall, double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        Shape s = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(p);
        sh.shadow(s, 6);
        extrude(g, s, 0, 6, shade(wall, 0.6), new GradientPaint(0, 0, c(0xffffff), 0, 392, shade(wall, 1.05)));
    }

    static void post(Graphics2D g, Shadows sh, double x, double y, Color rubber) {
        Ellipse2D ring = new Ellipse2D.Double(x - 2.6, y - 2.6, 5.2, 5.2);
        Ellipse2D pin = new Ellipse2D.Double(x - 1.2, y - 1.2, 2.4, 2.4);
        sh.shadow(ring, 6);
        extrude(g, ring, 1, 3.2, shade(rubber, 0.7), rubber);
        extrude(g, pin, 3.2, 7, c(0xb8b8c8), c(0xffffff));
    }

    static void band(Graphics2D g, Shadows sh, Color rubber, double... xy) {
        Path2D pth = new Path2D.Double();
        pth.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) pth.lineTo(xy[i], xy[i + 1]);
        for (int i = xy.length - 4; i >= 0; i -= 2) pth.lineTo(xy[i], xy[i + 1]);
        Area a = new Area(new BasicStroke(6.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(pth));
        a.subtract(new Area(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(pth)));
        sh.shadow(a, 4);
        extrude(g, a, 1.4, 3.8, shade(rubber, 0.6), rubber);
        for (int i = 0; i + 1 < xy.length; i += 2) {
            Shape pin = new Ellipse2D.Double(xy[i] - 1.2, xy[i + 1] - 1.2, 2.4, 2.4);
            sh.shadow(pin, 7);
            extrude(g, pin, 0, 7, c(0xb8b8c8), c(0xffffff));
        }
    }

    static void sling(Graphics2D g, Shadows sh, double[] pts, Color[] p) {
        Path2D s = new Path2D.Double();
        s.moveTo(pts[0], pts[1]);
        s.lineTo(pts[2], pts[3]);
        s.lineTo(pts[4], pts[5]);
        s.closePath();
        Shape band = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s);
        sh.shadow(s, 7);
        extrude(g, band, 1, 4.5, shade(p[3], 0.8), p[3]);
        extrude(g, s, 4.5, 8, c(0xd8d4e8, 220), new GradientPaint((float) pts[0], (float) pts[1], c(0xfdfbff),
                (float) pts[4], (float) pts[5], c(0xb8b0d0)));
        AffineTransform at = g.getTransform();
        g.translate(0, -8);
        double cx = (pts[0] + pts[2] + pts[4]) / 3, cy = (pts[1] + pts[3] + pts[5]) / 3;
        g.setColor(p[5]);
        g.fill(new Ellipse2D.Double(cx - 2.5, cy - 2.5, 5, 5));
        g.setColor(c(0xffffff, 160));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(pts[0] + 1, pts[1] + 3, pts[2] + 1, pts[3] - 2));
        g.setTransform(at);
        post(g, sh, pts[0], pts[1], p[3]);
        post(g, sh, pts[2], pts[3], p[3]);
        post(g, sh, pts[4], pts[5], p[3]);
    }

    /** A flipper at its pivot, turned to {@code angle} (radians, 0 pointing right, clockwise). */
    static void flipper(Graphics2D g, Shadows sh, double px, double py, double len, double angle, Color[] p) {
        double tx = px + Math.cos(angle) * len, ty = py + Math.sin(angle) * len;
        double r0 = 5.5, r1 = 2.8;
        double nx = -Math.sin(angle), ny = Math.cos(angle);
        Path2D f = new Path2D.Double();
        f.moveTo(px + nx * r0, py + ny * r0);
        f.lineTo(tx + nx * r1, ty + ny * r1);
        f.lineTo(tx - nx * r1, ty - ny * r1);
        f.lineTo(px - nx * r0, py - ny * r0);
        f.closePath();
        Area ar = new Area(f);
        ar.add(new Area(new Ellipse2D.Double(px - r0, py - r0, 2 * r0, 2 * r0)));
        ar.add(new Area(new Ellipse2D.Double(tx - r1, ty - r1, 2 * r1, 2 * r1)));
        sh.shadow(ar, 5);
        extrude(g, ar, 0, 2.5, shade(p[3], 0.75), p[3]);
        extrude(g, ar, 2.5, 5, shade(p[4], 0.7), new GradientPaint((float) px, (float) (py - 6), p[4], (float) px,
                (float) (py + 6), shade(p[4], 0.8)));
        AffineTransform at = g.getTransform();
        g.translate(0, -5);
        g.setColor(c(0x707080));
        g.fill(new Ellipse2D.Double(px - 1.8, py - 1.8, 3.6, 3.6));
        g.setColor(c(0xffffff));
        g.fill(new Ellipse2D.Double(px - 0.8, py - 1.2, 1.4, 1.2));
        g.setTransform(at);
    }

    static void bumper(Graphics2D g, Shadows sh, double x, double y, double r, Color cap, Color stripe, double lit) {
        if (lit > 0) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) (r * 2.2f),
                    new float[]{0f, 1f}, new Color[]{alpha(cap, (int) (110 * lit)), new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Double(x - r * 2.2, y - r * 2.2, r * 4.4, r * 4.4));
        }
        Ellipse2D skirt = new Ellipse2D.Double(x - r * 1.15, y - r * 1.15, r * 2.3, r * 2.3);
        Ellipse2D body = new Ellipse2D.Double(x - r * 0.72, y - r * 0.72, r * 1.44, r * 1.44);
        Ellipse2D ring = new Ellipse2D.Double(x - r * 0.95, y - r * 0.95, r * 1.9, r * 1.9);
        Ellipse2D top = new Ellipse2D.Double(x - r * 0.9, y - r * 0.9, r * 1.8, r * 1.8);
        sh.shadow(top, 10);
        extrude(g, skirt, 0, 1.2, c(0xb0b0c0), new RadialGradientPaint(new Point2D.Double(x, y - 1.2), (float) (r * 1.15),
                new float[]{0f, 1f}, new Color[]{c(0xffffff), c(0x9898a8)}));
        extrude(g, body, 1.2, 7, shade(cap, 0.7 + 0.4 * lit), null);
        extrude(g, ring, 2.5, 4.5, c(0x242428), c(0x3a3a40));
        extrude(g, top, 7, 9, c(0xe8e8f0), null);
        AffineTransform at = g.getTransform();
        g.translate(0, -9);
        g.setColor(c(0xf4f4f8));
        g.fill(top);
        double rc = r * 0.74;
        for (int i = 0; i < 8; i++) {
            g.setColor(i % 2 == 0 ? shade(cap, 0.7 + 0.3 * lit) : stripe);
            g.fill(new Arc2D.Double(x - rc, y - rc, 2 * rc, 2 * rc, i * 45, 45, Arc2D.PIE));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - rc * 0.3, y - rc * 0.4), (float) (rc * 0.9), new float[]{0f, 1f},
                new Color[]{c(0xffffff, 170), c(0xffffff, 0)}));
        g.fill(new Ellipse2D.Double(x - rc, y - rc, 2 * rc, 2 * rc));
        g.setColor(c(0x303038));
        g.fill(new Ellipse2D.Double(x - 1.5, y - 1.5, 3, 3));
        g.setTransform(at);
    }

    /** A lit bumper: the glow on the floor and its cap lit from inside, over the baked bumper. */
    static void bumperLit(Graphics2D g, double x, double y, double r, Color cap, double lit) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) (r * 2.2f),
                new float[]{0f, 1f}, new Color[]{alpha(cap, (int) (110 * lit)), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(x - r * 2.2, y - r * 2.2, r * 4.4, r * 4.4));
        double rc = r * 0.74;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y - 9), (float) rc, new float[]{0f, 1f},
                new Color[]{alpha(Color.WHITE, (int) (150 * lit)), alpha(cap, (int) (90 * lit))}));
        g.fill(new Ellipse2D.Double(x - rc, y - 9 - rc, 2 * rc, 2 * rc));
    }

    static Shape targetShape(double x, double y, double deg, double len, double width) {
        return AffineTransform.getTranslateInstance(x, y).createTransformedShape(
                AffineTransform.getRotateInstance(Math.toRadians(deg)).createTransformedShape(
                        new Rectangle2D.Double(-len / 2, -width / 2, len, width)));
    }

    /** A drop target, {@code up} 0 when sunk into the floor and 1 when standing. */
    static void dropTarget(Graphics2D g, Shadows sh, double x, double y, double deg, Color face, double up) {
        Shape fs = targetShape(x, y, deg, 8, 2.4);
        g.setColor(c(0x000000, 160));
        g.fill(fs);
        if (up <= 0.02) return;
        double h = 7 * up;
        sh.shadow(fs, h);
        extrude(g, fs, 0, h, face, shade(face, 1.3));
    }

    static void standup(Graphics2D g, Shadows sh, double x, double y, double deg, Color face) {
        Shape s = targetShape(x, y, deg, 8, 2);
        sh.shadow(s, 7);
        extrude(g, s, 0, 2, c(0x707080), c(0x9090a0));
        extrude(g, s, 2, 7, face, shade(face, 1.3));
    }

    /** The two legs of a spinner; the plate is drawn every frame. */
    static void spinnerLegs(Graphics2D g, Shadows sh, double x, double y) {
        for (double sx : new double[]{x - 7.5, x + 7.5}) {
            Shape leg = new Rectangle2D.Double(sx - 0.7, y - 0.7, 1.4, 1.4);
            sh.shadow(leg, 10);
            extrude(g, leg, 0, 10, c(0xb0b0c0), c(0xffffff));
        }
        AffineTransform at = g.getTransform();
        g.translate(0, -9.5);
        g.setColor(c(0xd0d0dc));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new Line2D.Double(x - 8, y, x + 8, y));
        g.setTransform(at);
    }

    /** The spinner's plate hanging from its wire, turned {@code angle} about it. */
    static void spinnerPlate(Graphics2D g, Shadows sh, double x, double y, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        // the plate hangs 6 units below its wire at 9.5; turning, its lower edge swings towards the player
        double top = 9.5, bottomH = top - 6 * c, bottomY = y + 6 * s;
        Path2D plate = new Path2D.Double();
        plate.moveTo(x - 6.5, y - top);
        plate.lineTo(x + 6.5, y - top);
        plate.lineTo(x + 6.5, bottomY - bottomH);
        plate.lineTo(x - 6.5, bottomY - bottomH);
        plate.closePath();
        sh.shadow(new Rectangle2D.Double(x - 6.5, y - 0.6, 13, 1.2), 8);
        boolean front = c > 0;
        g.setPaint(new GradientPaint(0, (float) (y - top), c(0xffffff), 0, (float) (bottomY - bottomH), front ? c(0x8a8a9a) : c(0x5a5a68)));
        g.fill(plate);
        if (front) {
            double my = (y - top) * 0.55 + (bottomY - bottomH) * 0.45;
            g.setColor(c(0xff4a5a));
            g.fill(new Rectangle2D.Double(x - 6.5, my - 0.6, 13, 1.2));
        }
    }

    static void gate(Graphics2D g, Shadows sh, double x0, double y0, double x1, double y1) {
        for (double[] e : new double[][]{{x0, y0}, {x1, y1}}) {
            Shape leg = new Rectangle2D.Double(e[0] - 0.7, e[1] - 0.7, 1.4, 1.4);
            sh.shadow(leg, 8);
            extrude(g, leg, 0, 8, c(0xb0b0c0), c(0xffffff));
        }
        sh.shadow(new BasicStroke(1f).createStrokedShape(new Line2D.Double(x0, y0, x1, y1)), 6);
        g.setColor(c(0xe8e8f4));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x0, y0 - 8, x1, y1 - 8));
        g.setColor(c(0xb8b8c8));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(x0 + 1, y0 - 8, x0 + 1, y0 - 2));
        g.draw(new Line2D.Double(x1 - 1, y1 - 8, x1 - 1, y1 - 2));
        g.draw(new Line2D.Double(x0 + 1, y0 - 2, x1 - 1, y1 - 2));
    }

    static Shape guideShape(double w, double... xy) {
        Path2D pth = new Path2D.Double();
        pth.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) pth.lineTo(xy[i], xy[i + 1]);
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(pth);
    }

    /** A printed plastic on standoffs above the field: translucent, coloured, white edge, screws. */
    static void plastic(Graphics2D g, Shadows sh, Shape s, Color col, Color art, double h, double... posts) {
        for (int i = 0; i + 1 < posts.length; i += 2) {
            Shape st = new Ellipse2D.Double(posts[i] - 1.3, posts[i + 1] - 1.3, 2.6, 2.6);
            sh.shadow(st, h);
            extrude(g, st, 0, h, c(0x9a9aa8), null);
        }
        sh.shadow(s, h + 1);
        extrude(g, s, h, h + 0.8, c(0xffffff, 200), null);
        AffineTransform at = g.getTransform();
        g.translate(0, -h - 0.8);
        Rectangle2D b = s.getBounds2D();
        g.setPaint(new java.awt.LinearGradientPaint((float) b.getMinX(), (float) b.getMinY(), (float) b.getMaxX(),
                (float) b.getMaxY() + 0.01f, new float[]{0f, 0.5f, 1f}, new Color[]{alpha(col, 215),
                shade(alpha(col, 215), 0.65), alpha(col, 200)}));
        g.fill(s);
        Shape old = g.getClip();
        g.clip(s);
        g.setColor(alpha(art, 200));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Line2D.Double(b.getMinX(), b.getMaxY(), b.getMaxX(), b.getMinY()));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(b.getMinX(), b.getMaxY() - 3, b.getMaxX(), b.getMinY() - 3));
        g.setClip(old);
        g.setColor(c(0xffffff, 230));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(s);
        for (int i = 0; i + 1 < posts.length; i += 2) {
            g.setColor(c(0xd8d8e4));
            g.fill(new Ellipse2D.Double(posts[i] - 1.1, posts[i + 1] - 1.1, 2.2, 2.2));
            g.setColor(c(0x50505a));
            g.draw(new Line2D.Double(posts[i] - 0.8, posts[i + 1], posts[i] + 0.8, posts[i + 1]));
        }
        g.setTransform(at);
    }

    // ---- toys ------------------------------------------------------------------------------------------

    /** The upright Ferris wheel's feet and frame; the wheel itself turns and is drawn every frame. */
    static void ferrisBase(Graphics2D g, Shadows sh, double cx, double cy, double r) {
        double hub = r * 1.45 + 4;
        Shape base = new RoundRectangle2D.Double(cx - r * 0.7, cy - 3, r * 1.4, 6, 3, 3);
        sh.shadow(base, 3);
        extrude(g, base, 0, 3, c(0x4a3a6a), c(0x8a7ab0));
        sh.shadow(new Ellipse2D.Double(cx - r, cy - 6, 2 * r, 10), hub);
    }

    /** The wheel turned by {@code angle}, with {@code balls} locked balls riding in its gondolas. */
    static void ferrisWheel(Graphics2D g, double cx, double cy, double r, double angle, int balls, Color hubColor,
                            double lights) {
        double hub = r * 1.45 + 4;
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x8a7ab0));
        g.draw(new Line2D.Double(cx - r * 0.6, cy - 3, cx, cy - hub));
        g.draw(new Line2D.Double(cx + r * 0.6, cy - 3, cx, cy - hub));
        double wy = cy - hub;
        AffineTransform wat = g.getTransform();
        g.translate(cx, wy);
        g.scale(1, 1.45);
        g.translate(-cx, -wy);
        g.setColor(c(0xd8c8f0));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Double(cx - r, wy - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Ellipse2D.Double(cx - r * 0.92, wy - r * 0.92, r * 1.84, r * 1.84));
        Color[] cols = {c(0xff5a5a), c(0xffc83c), c(0x4ad8b0), c(0x5aa8ff)};
        for (int i = 0; i < 10; i++) {
            double an = i * Math.PI / 5 + angle;
            double gx = cx + Math.cos(an) * r, gy = wy + Math.sin(an) * r;
            g.setColor(c(0xb8a8d8));
            g.setStroke(new BasicStroke(0.6f));
            g.draw(new Line2D.Double(cx, wy, gx, gy));
            boolean on = ((int) (lights * 4) + i) % 3 != 0;
            g.setColor(on ? c(0xfff0b0) : c(0x806a40));
            g.fill(new Ellipse2D.Double(cx + Math.cos(an + 0.31) * r - 0.9, wy + Math.sin(an + 0.31) * r - 0.9, 1.8, 1.8));
        }
        for (int i = 0; i < 10; i++) {
            double an = i * Math.PI / 5 + angle;
            double gx = cx + Math.cos(an) * r, gy = wy + Math.sin(an) * r;
            Shape gond = new RoundRectangle2D.Double(gx - 3.5, gy, 7, 5.5, 2, 2);
            g.setPaint(new GradientPaint((float) (gx - 3.5), 0, shade(cols[i % 4], 1.2), (float) (gx + 3.5), 0, shade(cols[i % 4], 0.7)));
            g.fill(gond);
            if (i < balls) {
                g.setPaint(new RadialGradientPaint(new Point2D.Double(gx - 0.8, gy - 0.4), 2.6f, new float[]{0f, 1f},
                        new Color[]{c(0xffffff), c(0x5a5a68)}));
                g.fill(new Ellipse2D.Double(gx - 2.2, gy - 2.6, 4.4, 4.4));
            }
            g.setColor(c(0x000000, 90));
            g.fill(new Rectangle2D.Double(gx - 2.5, gy + 1, 5, 1.6));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - 1, wy - 1), 4f, new float[]{0f, 1f},
                new Color[]{c(0xfff0c0), hubColor}));
        g.fill(new Ellipse2D.Double(cx - 3.5, wy - 3.5, 7, 7));
        g.setTransform(wat);
    }

    static void kraken(Graphics2D g, Shadows sh, double x, double y, Color[] p) {
        sh.shadow(new Ellipse2D.Double(x - 22, y - 16, 44, 36), 14);
        extrude(g, new Ellipse2D.Double(x - 22, y - 10, 44, 26), 0, 10, c(0x6a2050), null);
        AffineTransform at = g.getTransform();
        g.translate(0, -10);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - 4, y - 16), 26f, new float[]{0f, 1f},
                new Color[]{c(0xc8508a), c(0x5a1a42)}));
        g.fill(new Ellipse2D.Double(x - 22, y - 26, 44, 34));
        g.setColor(c(0xffe060));
        g.fill(new Ellipse2D.Double(x - 12, y - 16, 7, 6));
        g.fill(new Ellipse2D.Double(x + 4, y - 16, 7, 6));
        g.setColor(c(0x101010));
        g.fill(new Ellipse2D.Double(x - 9.5, y - 15, 2, 4));
        g.fill(new Ellipse2D.Double(x + 6.5, y - 15, 2, 4));
        g.setTransform(at);
        for (int i = 0; i < 5; i++) {
            Path2D t = new Path2D.Double();
            double sx = x - 18 + i * 9;
            if (i == 2) continue;
            t.moveTo(sx, y + 4);
            t.curveTo(sx - 8 + i * 3, y + 18, sx + 6, y + 26, sx - 2 + (i - 2) * 6, y + 36);
            sh.shadow(new BasicStroke(4f).createStrokedShape(t), 3);
            g.setColor(c(0x8a2a62));
            g.setStroke(new BasicStroke(4f - i * 0.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(t);
            g.setColor(c(0xf0a0c8, 140));
            g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{0.5f, 3f}, 0));
            g.draw(t);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** The spin disc's ring; its arrows turn and are drawn every frame, the satellite model on its mast too. */
    static void satelliteDisc(Graphics2D g, Shadows sh, double x, double y, double r) {
        Ellipse2D disc = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        extrude(g, new BasicStroke(1.5f).createStrokedShape(disc), 0, 1.5, c(0x5a6a9a), c(0x9ab8ff));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r, new float[]{0f, 0.85f, 1f},
                new Color[]{c(0x3a4a7a), c(0x1a2040), c(0x2a3060)}));
        g.fill(disc);
        Shape mast = new Rectangle2D.Double(x - 0.7, y - 0.7, 1.4, 1.4);
        sh.shadow(mast, 14);
        extrude(g, mast, 0, 14, c(0xb0b0c0), c(0xffffff));
    }

    static void satelliteTop(Graphics2D g, Shadows sh, double x, double y, double r, double angle, Color accent) {
        for (int i = 0; i < 3; i++) {
            double an = i * Math.PI * 2 / 3 + angle;
            g.setColor(accent);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Arc2D.Double(x - r * 0.8, y - r * 0.8, r * 1.6, r * 1.6, Math.toDegrees(an), 60, Arc2D.OPEN));
        }
        Shape bodyS = new RoundRectangle2D.Double(x - 3, y - 2, 6, 4, 1, 1);
        sh.shadow(bodyS, 16);
        extrude(g, bodyS, 14, 19, c(0xd8c070), c(0xffe8a0));
        double swing = Math.sin(angle * 0.3) * 1.5;
        for (double sx : new double[]{x - 13, x + 4}) {
            Shape panel = new Rectangle2D.Double(sx, y - 1 + swing * (sx < x ? 1 : -1) * 0.2, 9, 2);
            sh.shadow(panel, 16);
            extrude(g, panel, 15.5, 17.5, c(0x2a4aa8), new GradientPaint((float) sx, 0, c(0x5a8aff), (float) (sx + 9), 0, c(0x2a4aa8)));
        }
    }

    /** A raised floor: a box with a printed top and a rim; its parts are drawn {@code h} higher. */
    static void floorBox(Graphics2D g, Shadows sh, double x, double y, double w, double hgt, double h, Color a, Color b, Color rim) {
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, hgt, 10, 10);
        sh.shadow(box, h);
        extrude(g, box, 0, h, shade(a, 0.8), null);
        AffineTransform at = g.getTransform();
        g.translate(0, -h);
        g.setPaint(new GradientPaint(0, (float) y, a, 0, (float) (y + hgt), b));
        g.fill(box);
        g.setColor(shade(a, 1.3));
        g.setStroke(new BasicStroke(0.5f));
        for (int i = 1; i * 10 < hgt; i++) g.draw(new Line2D.Double(x, y + i * 10, x + w, y + i * 10));
        g.setTransform(at);
    }
}
