package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.RampArt;
import eu.amadeos.gameengine.pinball.Relief;
import eu.amadeos.gameengine.pinball.Track;
import eu.amadeos.games.pinball.domain.Part;
import eu.amadeos.games.pinball.domain.Path;
import eu.amadeos.games.pinball.domain.Table;
import eu.amadeos.games.pinball.domain.TableFile;
import eu.amadeos.games.pinball.domain.TableSpec;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static eu.amadeos.games.pinball.ui.game.Art.c;

/**
 * The picture of a table seen from straight above, every part standing up with its height and shadow,
 * made once from the table file: a lower layer (the printed floor and everything low on it) and an upper
 * one (ramps, wire forms, plastics), so that balls can roll under the ramps. The parts that move (flippers,
 * drop targets, spinner plates, toys) and the lamps are left out; {@link TableView} draws them every frame.
 */
final class TableArt {
    /** The picture reaches past the playfield, where raised parts stick out at the top. */
    static final double LEFT = -8, TOP = -40, RIGHT = 240, BOTTOM = 400;

    /** A lamp under an insert: its shape and colour, lit by the rules. */
    record Lamp(String name, String shape, double x, double y, double size, double deg, Color color, int layer) {
    }

    final BufferedImage lower;
    final BufferedImage upper;
    final List<Lamp> lamps = new ArrayList<>();

    TableArt(Table table, double pixelsPerUnit) {
        TableSpec spec = table.spec();
        Color[] p = spec.palette();
        Relief low = new Relief(LEFT, TOP, RIGHT, BOTTOM, pixelsPerUnit);
        Relief high = low.above();
        Art.Shadows sh = low::shadow;
        Art.Shadows shHigh = high::shadow;
        Graphics2D f = low.floor();
        Graphics2D g = low.parts();
        floor(f, spec);
        rim(g, sh);
        lane(g, sh, p);
        for (Part part : spec.parts()) {
            try {
                draw(part, table, f, g, sh, high, shHigh, p);
            } catch (RuntimeException e) {
                throw new IllegalStateException("table " + spec.id() + " line " + part.line() + ": " + e.getMessage(), e);
            }
        }
        apron(g, sh, spec);
        lower = low.bake();
        upper = high.bake();
        low.dispose();
        high.dispose();
    }

    private static double layerHeight(TableSpec spec, int layer) {
        if (layer == 0) return 0;
        for (Part part : spec.parts("floor")) if ((int) part.num(0) == layer) return part.num(5);
        return 0;
    }

    // ---- the floor -----------------------------------------------------------------------------------

    static Shape playfield() {
        Path2D p = new Path2D.Double();
        p.moveTo(0, Table.LENGTH);
        p.lineTo(0, 64);
        p.curveTo(0, 20, 30, 0, 72, 0);
        p.lineTo(160, 0);
        p.curveTo(202, 0, 232, 20, 232, 64);
        p.lineTo(232, Table.LENGTH);
        p.closePath();
        return p;
    }

    private void floor(Graphics2D g, TableSpec spec) {
        Color[] p = spec.palette();
        Shape field = playfield();
        g.setColor(c(0x16110c));
        g.fill(new RoundRectangle2D.Double(-8, -6, 248, Table.LENGTH + 12, 14, 14));
        Shape old = g.getClip();
        g.clip(field);
        g.setPaint(new GradientPaint(0, 0, p[0], 0, (float) Table.LENGTH, p[1]));
        g.fill(field);
        switch (spec.art()) {
            case "fair" -> fairArt(g);
            case "reef" -> reefArt(g);
            default -> orbitArt(g);
        }
        floorLight(g, spec.art());
        g.setClip(old);
    }

    private static void fairArt(Graphics2D g) {
        for (int k = 0; k < 3; k++) {
            double y0 = 150 + k * 46;
            Path2D s = new Path2D.Double();
            s.moveTo(0, y0);
            s.quadTo(116, y0 + 30, 232, y0);
            g.setColor(c(0x4a3a6a));
            g.setStroke(new BasicStroke(0.6f));
            g.draw(s);
            Color[] cols = {c(0xffb43c), c(0xff5a8c), c(0x4ad8b0), c(0x5aa8ff)};
            for (int i = 0; i <= 18; i++) {
                double t = i / 18.0;
                double x = 232 * t;
                double y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + 30) + t * t * y0;
                g.setColor(cols[(i + k) % 4]);
                g.fill(new Ellipse2D.Double(x - 1.2, y - 0.5, 2.4, 2.4));
            }
        }
        tent(g, 150, 236, 40, 30, c(0x5a2040), c(0x6a5a7a));
        tent(g, 40, 236, 30, 22, c(0x202a5a), c(0x5a5a7a));
    }

    static void tent(Graphics2D g, double x, double y, double w, double h, Color a, Color b) {
        int stripes = 6;
        for (int i = 0; i < stripes; i++) {
            Path2D p = new Path2D.Double();
            p.moveTo(x + w / 2, y - h);
            p.lineTo(x + i * w / stripes, y);
            p.lineTo(x + (i + 1) * w / stripes, y);
            p.closePath();
            g.setColor(i % 2 == 0 ? a : b);
            g.fill(p);
        }
        g.setColor(c(0x000000, 70));
        g.fill(new Rectangle2D.Double(x + w * 0.42, y - h * 0.3, w * 0.16, h * 0.3));
        g.setColor(c(0xffd27a));
        g.fill(new Ellipse2D.Double(x + w / 2 - 1.5, y - h - 3, 3, 3));
    }

    private static void reefArt(Graphics2D g) {
        Random r = new Random(8);
        for (int i = 0; i < 6; i++) {
            Path2D ray = new Path2D.Double();
            double x = 20 + i * 40;
            ray.moveTo(x, 0);
            ray.lineTo(x + 16, 0);
            ray.lineTo(x + 60, Table.LENGTH);
            ray.lineTo(x + 20, Table.LENGTH);
            ray.closePath();
            g.setColor(c(0xaef8ff, 14));
            g.fill(ray);
        }
        Path2D hull = new Path2D.Double();
        hull.moveTo(30, 238);
        hull.lineTo(190, 214);
        hull.lineTo(176, 250);
        hull.lineTo(52, 268);
        hull.closePath();
        g.setColor(c(0x3a2a1c, 200));
        g.fill(hull);
        g.setColor(c(0x5a4028, 200));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Line2D.Double(100, 228, 92, 168));
        g.draw(new Line2D.Double(92, 176, 70, 190));
        for (int i = 0; i < 5; i++) g.fill(new Ellipse2D.Double(70 + i * 22, 240 - i * 3, 4, 4));
        for (int i = 0; i < 22; i++) {
            double x = r.nextDouble() * 232, y = 270 + r.nextDouble() * 110;
            Color[] cc = {c(0xff7a5a, 150), c(0xff4a8c, 140), c(0xffd040, 120), c(0x3ae0a8, 120)};
            g.setColor(cc[i % 4]);
            for (int b = 0; b < 4; b++) {
                double an = -Math.PI / 2 + (r.nextDouble() - 0.5) * 1.6;
                g.draw(new Line2D.Double(x, y, x + Math.cos(an) * 10, y + Math.sin(an) * 10));
            }
        }
        for (int i = 0; i < 40; i++) {
            double x = r.nextDouble() * 232, y = r.nextDouble() * Table.LENGTH, s = 1 + r.nextDouble() * 2.5;
            g.setColor(c(0xd8ffff, 70));
            g.setStroke(new BasicStroke(0.5f));
            g.draw(new Ellipse2D.Double(x, y, s, s));
        }
        Path2D t = new Path2D.Double();
        t.moveTo(-10, 120);
        t.curveTo(40, 140, 10, 190, 50, 200);
        g.setColor(c(0x8a3a6a, 60));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(t);
        g.setStroke(new BasicStroke(1f));
    }

    private static void orbitArt(Graphics2D g) {
        Random r = new Random(9);
        for (int i = 0; i < 160; i++) {
            g.setColor(c(0xffffff, 40 + r.nextInt(170)));
            double s = r.nextDouble() < 0.1 ? 1.3 : 0.7;
            g.fill(new Ellipse2D.Double(r.nextDouble() * 232, r.nextDouble() * Table.LENGTH, s, s));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(96, 214), 50f, new float[]{0f, 1f},
                new Color[]{c(0x4a6ad8), c(0x101840)}));
        g.fill(new Ellipse2D.Double(70, 200, 76, 76));
        g.setColor(c(0x9ab8ff, 110));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(46, 228, 124, 22));
        g.setColor(c(0xff3ac8, 80));
        g.draw(new Ellipse2D.Double(38, 224, 140, 30));
        g.setColor(c(0x3ae0ff, 40));
        g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3, 3}, 0));
        g.draw(new Ellipse2D.Double(20, 60, 192, 120));
        g.draw(new Ellipse2D.Double(36, 90, 160, 80));
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(0x5a6a8a, 140));
        for (int i = 0; i < 6; i++) g.draw(new Rectangle2D.Double(130 + i * 9, 300, 9, 9));
    }

    /** The general illumination under the plastics, a printed grain and a border line. */
    private static void floorLight(Graphics2D g, String art) {
        Color warm = art.equals("orbit") ? c(0xb8d0ff) : art.equals("reef") ? c(0xc8fff0) : c(0xffe0b0);
        double[][] pools = {{30, 300}, {190, 300}, {20, 200}, {200, 200}, {40, 90}, {180, 90}, {110, 30}, {110, 240}, {70, 160}, {150, 160}};
        for (double[] pl : pools) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(pl[0], pl[1]), 46f, new float[]{0f, 1f},
                    new Color[]{Art.alpha(warm, 46), Art.alpha(warm, 0)}));
            g.fill(new Ellipse2D.Double(pl[0] - 46, pl[1] - 46, 92, 92));
        }
        Random r = new Random(99 + art.length());
        for (int i = 0; i < 9000; i++) {
            g.setColor(r.nextBoolean() ? c(0xffffff, 10) : c(0x000000, 18));
            g.fill(new Rectangle2D.Double(r.nextDouble() * 232, r.nextDouble() * Table.LENGTH, 0.6, 0.6));
        }
        g.setColor(c(0xffffff, 36));
        g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{2, 2}, 0));
        g.draw(AffineTransform.getTranslateInstance(116, 196).createTransformedShape(
                AffineTransform.getScaleInstance(0.9, 0.9).createTransformedShape(
                        AffineTransform.getTranslateInstance(-116, -196).createTransformedShape(playfield()))));
        g.setStroke(new BasicStroke(1f));
    }

    // ---- the cabinet ----------------------------------------------------------------------------------

    private static void rim(Graphics2D g, Art.Shadows sh) {
        Shape rim = new BasicStroke(5f).createStrokedShape(playfield());
        Area inner = new Area(rim);
        inner.subtract(new Area(new Rectangle2D.Double(-20, Table.LENGTH, 280, 40)));
        sh.shadow(inner, 10);
        Relief.extrude(g, inner, 0, 12, c(0x6a4a30), new GradientPaint(0, 0, c(0xb8b4c8), 0, (float) Table.LENGTH, c(0x6a6878)));
    }

    /** The shooter lane's worn floor, the spring's bracket where the rod leaves the cabinet. */
    private static void lane(Graphics2D g, Art.Shadows sh, Color[] p) {
        double x = Table.LANE_X;
        g.setPaint(new GradientPaint((float) (x - 7.5), 0, c(0x3a3a44), (float) (x + 7.5), 0, c(0x5a5a66)));
        g.fill(new Rectangle2D.Double(x - 7.5, 300, 15, 92));
        g.setColor(c(0x000000, 60));
        for (int i = 0; i < 8; i++) g.fill(new Rectangle2D.Double(x - 0.6, 304 + i * 11, 1.2, 5));
        Shape bracket = new Rectangle2D.Double(x - 6.5, 384, 13, 4);
        sh.shadow(bracket, 10);
        Relief.extrude(g, bracket, 0, 10, c(0x8a8a98), c(0xd8d8e4));
        AffineTransform at = g.getTransform();
        g.translate(0, -10);
        g.setColor(c(0x50505a));
        g.fill(new Ellipse2D.Double(x - 5, 385, 2, 2));
        g.fill(new Ellipse2D.Double(x + 3, 385, 2, 2));
        g.setTransform(at);
        Art.label(g, "SHOOT", x, 326, p[5], 3.4f);
    }

    private static void apron(Graphics2D g, Art.Shadows sh, TableSpec spec) {
        Path2D ap = new Path2D.Double();
        ap.moveTo(-4, 370);
        ap.lineTo(84, 370);
        ap.lineTo(98, Table.LENGTH + 4);
        ap.lineTo(-4, Table.LENGTH + 4);
        ap.closePath();
        Path2D ap2 = new Path2D.Double();
        ap2.moveTo(216, 370);
        ap2.lineTo(136, 370);
        ap2.lineTo(122, Table.LENGTH + 4);
        ap2.lineTo(216, Table.LENGTH + 4);
        ap2.closePath();
        for (Shape a : new Shape[]{ap, ap2}) {
            sh.shadow(a, 6);
            Relief.extrude(g, a, 0, 6, c(0x3a3848), new GradientPaint(0, 362, c(0x34323f), 0, (float) Table.LENGTH, c(0x1a1822)));
        }
        AffineTransform at = g.getTransform();
        g.translate(0, -6);
        g.setFont(Art.font(java.awt.Font.BOLD, 5.5f));
        g.setColor(spec.palette()[5]);
        g.drawString(spec.name().toUpperCase(), 8, 384);
        g.setColor(c(0x8a86a0));
        g.setFont(Art.font(java.awt.Font.PLAIN, 4.5f));
        g.drawString("3 BALLS PER GAME", 144, 384);
        g.setTransform(at);
    }

    // ---- the parts --------------------------------------------------------------------------------------

    // --8<-- [start:draw]
    private void draw(Part part, Table table, Graphics2D floor, Graphics2D g, Art.Shadows sh, Relief high,
                      Art.Shadows shHigh, Color[] p) {
        TableSpec spec = table.spec();
        double h = layerHeight(spec, part.layer());
        AffineTransform at = g.getTransform();
        AffineTransform fat = floor.getTransform();
        if (h > 0) {
            // a part on a raised floor: drawn that much higher, its shadow falls on the raised floor
            g.translate(0, -h);
            floor = g;
        }
        Art.Shadows s = h > 0 ? Art.Shadows.NONE : sh;
        double[] n = part.nums();
        switch (part.kind()) {
            case "wall" -> Art.wallPath(g, s, p[2], n);
            case "band" -> Art.band(g, s, p[3], n);
            case "post" -> Art.post(g, s, n[0], n[1], p[3]);
            case "bumper" -> {
                Art.bumper(g, s, n[0], n[1], n[2], spec.color(part.name(1, "p3")), spec.color(part.name(2, "xffffff")), 0);
                lamps.add(new Lamp(part.id(), "bumper", n[0], n[1], n[2], 0, spec.color(part.name(1, "p3")), part.layer()));
            }
            case "sling" -> Art.sling(g, s, n, p);
            case "standup" -> Art.standup(g, s, n[0], n[1], n[2], spec.color(part.name(1, "p7")));
            case "saucer" -> Art.saucer(floor, s, n[0], n[1], spec.color(part.name(1, "p6")));
            case "spinner" -> Art.spinnerLegs(g, s, n[0], n[1]);
            case "rollover", "kickback" -> Art.rollover(floor, n[0], n[1]);
            case "gate" -> Art.gate(g, s, n[0], n[1], n[2], n[3]);
            case "lanes" -> lanes(part, floor, g, s, p);
            case "magnet" -> Art.whirlpool(floor, n[0], n[1], n[2]);
            case "floor" -> {
                g.setTransform(at);
                Art.floorBox(g, sh, n[1], n[2], n[3], n[4], n[5], spec.color(part.name(0, "x404040")),
                        spec.color(part.name(1, "x202020")), p[2]);
            }
            case "plastic" -> {
                double[] pts = new double[n.length - 2];
                System.arraycopy(n, 2, pts, 0, pts.length);
                double[] posts = {pts[0], pts[1], pts[pts.length - 2], pts[pts.length - 1]};
                Art.plastic(high.parts(), shHigh, Art.guideShape(n[1], pts), spec.color(part.name(0, "p6")),
                        spec.color(part.name(1, "p5")), n[0], posts);
            }
            case "plasticoval" -> Art.plastic(high.parts(), shHigh, new Ellipse2D.Double(n[1], n[2], n[3], n[4]),
                    spec.color(part.name(0, "p7")), spec.color(part.name(1, "p6")), n[0],
                    n[1] + 4, n[2] + n[4] / 2, n[1] + n[3] - 4, n[2] + n[4] / 2);
            case "label" -> Art.label(floor, TableFile.text(part.id()), n[0], n[1], spec.color(part.name(1, "xffffff")), (float) n[2]);
            case "insert" -> {
                Color col = spec.color(part.name(1, "p5"));
                Art.insert(floor, n[0], n[1], n[2], col, 0);
                lamps.add(new Lamp(part.id(), "insert", n[0], n[1], 0, n[2], col, part.layer()));
            }
            case "round" -> {
                Color col = spec.color(part.name(1, "p5"));
                Art.roundInsert(floor, n[0], n[1], n[2], col, 0);
                lamps.add(new Lamp(part.id(), "round", n[0], n[1], n[2], 0, col, part.layer()));
            }
            case "fan" -> {
                Color[] cols = {p[5], p[6], p[7]};
                double a = Math.toRadians(n[2] - 90);
                for (int i = 0; i < 3; i++) {
                    double x = n[0] + Math.cos(a) * i * 9, y = n[1] + Math.sin(a) * i * 9;
                    Art.insert(floor, x, y, n[2], cols[i], 0);
                    lamps.add(new Lamp(part.id() + (i + 1), "insert", x, y, 0, n[2], cols[i], 0));
                }
            }
            case "modering" -> {
                Art.modeRingBase(floor, n[0], n[1], n[2], p[5], p[6]);
                for (int i = 0; i < 8; i++) {
                    Point2D.Double q = Art.ringLamp(n[0], n[1], n[2], i);
                    Art.roundInsert(floor, q.x, q.y, 3.2, p[5], 0);
                    lamps.add(new Lamp("mode" + i, "round", q.x, q.y, 3.2, 0, p[5], 0));
                }
                lamps.add(new Lamp("wizard", "star", n[0], n[1], n[2], 0, p[6], 0));
            }
            case "toy" -> toy(part, g, s, p);
            case "captive" -> {
                // the lane's floor under the captive ball glows like a pearl
                floor.setPaint(new RadialGradientPaint(new Point2D.Double(n[0], n[1]), 8f, new float[]{0f, 1f},
                        new Color[]{c(0xfff8f0, 120), c(0xfff8f0, 0)}));
                floor.fill(new Ellipse2D.Double(n[0] - 8, n[1] - 8, 16, 16));
            }
            case "ramp" -> ramp(part, table, high, p, true);
            case "tube" -> ramp(part, table, high, p, false);
            case "track" -> {
                Track t = table.field().track(part.id());
                if (part.id().equals("lift")) tube(high, t, c(0x9ab8ff));
                else RampArt.wire(high, t, 8);
            }
            default -> {
                // physics only: holes, switches, the plunger's tip, the rim; dynamic: flippers, drops
            }
        }
        g.setTransform(at);
        if (h > 0) floor.setTransform(at);
        else floor.setTransform(fat);
    }
    // --8<-- [end:draw]

    private void lanes(Part part, Graphics2D floor, Graphics2D g, Art.Shadows s, Color[] p) {
        double x0 = part.num(0), y = part.num(1), step = part.num(3);
        int n = (int) part.num(2);
        for (int i = 0; i <= n; i++) {
            double x = x0 + i * step;
            Art.wallPath(g, s, p[2], x, y, x, y + 16);
            Art.post(g, s, x, y, p[3]);
        }
        for (int i = 0; i < n; i++) {
            double x = x0 + step / 2 + i * step;
            Art.rollover(floor, x, y + 10);
            Art.roundInsert(floor, x, y + 22, 3.3, p[5], 0);
            lamps.add(new Lamp(part.id() + (i + 1), "round", x, y + 22, 3.3, 0, p[5], 0));
            String letter = part.name(i + 1, "");
            floor.setFont(Art.font(java.awt.Font.BOLD, 4.4f));
            floor.setColor(c(0x8a7a5a));
            floor.drawString(letter, (float) (x - floor.getFontMetrics().stringWidth(letter) / 2.0), (float) (y + 23.6));
        }
    }

    private void toy(Part part, Graphics2D g, Art.Shadows s, Color[] p) {
        double[] n = part.nums();
        switch (part.id()) {
            case "ferris" -> {
                Art.ferrisBase(g, s, n[0], n[1], n[2]);
                Art.label(g, "LOCK", n[0], n[1] + 9, p[5], 3.8f);
            }
            case "kraken" -> Art.kraken(g, s, n[0], n[1], p);
            case "satellite" -> {
                Art.satelliteDisc(g, s, n[0], n[1], n[2]);
                Art.label(g, "SPIN DISC", n[0], n[1] + n[2] + 7, p[5], 3.6f);
            }
            default -> {
            }
        }
    }

    // --8<-- [start:ramps]
    /** A ramp's plastic part and its wire form; a tube is a clear ramp with a roof, ending on a raised floor. */
    private static void ramp(Part part, Table table, Relief high, Color[] p, boolean open) {
        Track whole = table.field().track(part.id());
        int split = table.wireStart(part.id());
        Color col = table.spec().color(part.name(1, "p6"));
        Track up = sub(whole, 0, split < 0 ? whole.points() - 1 : split);
        if (open) RampArt.plastic(high, up, part.num(0), col, 9);
        else tube(high, up, col);
        if (split > 0 && split < whole.points() - 1) RampArt.wire(high, sub(whole, split, whole.points() - 1), 8);
        // the flap at the mouth
        Graphics2D g = high.parts();
        double a = Math.atan2(whole.y(2) - whole.y(0), whole.x(2) - whole.x(0));
        AffineTransform at = g.getTransform();
        g.translate(whole.x(0), whole.y(0));
        g.rotate(a + Math.PI / 2);
        g.setColor(c(0xd8d8e8));
        g.fill(new RoundRectangle2D.Double(-part.num(0) / 2, -1.5, part.num(0), 3, 2, 2));
        g.setTransform(at);
    }
    // --8<-- [end:ramps]

    private static void tube(Relief high, Track t, Color col) {
        RampArt.plastic(high, t, 10, Art.alpha(col, 150), 7);
        Graphics2D g = high.parts();
        List<double[]> pts = t.samples(1);
        Path2D roof = new Path2D.Double();
        for (int i = 0; i < pts.size(); i++) {
            double[] q = pts.get(i);
            if (i == 0) roof.moveTo(q[0], q[1] - q[2] - 9);
            else roof.lineTo(q[0], q[1] - q[2] - 9);
        }
        g.setColor(Art.alpha(Color.WHITE, 90));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(roof);
        g.setColor(Art.alpha(Color.WHITE, 200));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(AffineTransform.getTranslateInstance(-1.2, -1).createTransformedShape(roof));
    }

    private static Track sub(Track t, int from, int to) {
        double[] xyh = new double[(to - from + 1) * 3];
        for (int i = from; i <= to; i++) {
            xyh[(i - from) * 3] = t.x(i);
            xyh[(i - from) * 3 + 1] = t.y(i);
            xyh[(i - from) * 3 + 2] = t.h(i);
        }
        if (xyh.length < 6) return new Track(t.id, t.exitLayer, t.x(from), t.y(from), t.h(from), t.x(to) + 0.01, t.y(to), t.h(to));
        return new Track(t.id, t.exitLayer, xyh);
    }

    static double[] path(Part part, String word) {
        List<double[]> pts = Path.points(part.words(), Path.after(part.words(), word), null, null, 3);
        double[] out = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            out[2 * i] = pts.get(i)[0];
            out[2 * i + 1] = pts.get(i)[1];
        }
        return out;
    }
}
