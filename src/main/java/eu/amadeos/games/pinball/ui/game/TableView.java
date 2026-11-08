package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.ChromeBall;
import eu.amadeos.gameengine.pinball.Flipper;
import eu.amadeos.gameengine.pinball.Lamps;
import eu.amadeos.gameengine.pinball.Perspective;
import eu.amadeos.gameengine.pinball.Playfield;
import eu.amadeos.gameengine.pinball.Segment;
import eu.amadeos.games.pinball.domain.Part;
import eu.amadeos.games.pinball.domain.Table;
import eu.amadeos.games.pinball.domain.TableSpec;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static eu.amadeos.games.pinball.ui.game.Art.c;

/**
 * The table on the screen, seen from the player's end: the baked table picture tilted into a trapezoid by a
 * {@link Perspective}, and on it every frame the moving parts, the lit lamps and the balls, then the glass,
 * the cabinet's side rails and its front with the lockdown bar, the plunger knob and the flipper buttons.
 *
 * <p>Tilting the picture is a few hundred milliseconds of work; it is done once per window size, on a
 * background thread, while the last tilted picture (scaled) stands in.
 */
final class TableView {
    /** Where the playfield lies on the 640 × 400 screen. */
    static final double[] QUAD = Perspective.trapezoid(206, 30, 352, 240, 352);
    private static final ExecutorService WARPER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pinball-warp");
        t.setDaemon(true);
        return t;
    });

    private final Table table;
    private final TableArt art;
    private final Perspective view = new Perspective(Table.WIDTH, Table.LENGTH, QUAD);
    private final ChromeBall chrome;
    private final Color[] p;
    private volatile Warped warped;
    private volatile boolean warping;
    private boolean synchronous;
    private double time;

    /** The tilted pictures for one device transform. */
    private record Warped(AffineTransform device, Rectangle box, BufferedImage lower, BufferedImage upper,
                          Rectangle frontBox, BufferedImage front) {
    }

    TableView(Table table, TableArt art) {
        this.table = table;
        this.art = art;
        this.p = table.spec().palette();
        this.chrome = new ChromeBall(p[0], p[5], p[6], p[7], c(0xfff0d0));
    }

    /** Tilts the table picture on the drawing thread, at once: for screenshots and tests. */
    void synchronous(boolean on) {
        synchronous = on;
    }

    Perspective perspective() {
        return view;
    }

    Table table() {
        return table;
    }

    // --8<-- [start:paint]
    /** Draws the table, in logical coordinates; {@code g}'s transform maps them to the device. */
    void paint(Graphics2D g, Lamps lamps, int locks, double seconds) {
        time += seconds;
        Art.FAST.set(true);
        try {
            paintTable(g, lamps, locks);
        } finally {
            Art.FAST.set(false);
        }
    }

    private void paintTable(Graphics2D g, Lamps lamps, int locks) {
        cabinet(g);
        Warped w = warped(g.getTransform());
        AffineTransform t = g.getTransform();
        if (w != null) image(g, w.lower(), w, t);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        lampGlow(g, lamps, 0);
        List<Drawable> below = new ArrayList<>(), above = new ArrayList<>();
        collect(below, above, lamps, locks);
        below.sort(Comparator.comparingDouble(Drawable::depth));
        for (Drawable d : below) d.draw().accept(g);
        if (w != null) image(g, w.upper(), w, t);
        lampGlow(g, lamps, 1);
        above.sort(Comparator.comparingDouble(Drawable::depth));
        for (Drawable d : above) d.draw().accept(g);
        if (w != null) {
            AffineTransform at = g.getTransform();
            g.setTransform(new AffineTransform());
            g.drawImage(w.front(), w.frontBox().x + (int) Math.round(t.getTranslateX() - w.device().getTranslateX()),
                    w.frontBox().y + (int) Math.round(t.getTranslateY() - w.device().getTranslateY()), null);
            g.setTransform(at);
        }
        knob(g);
    }
    // --8<-- [end:paint]

    private record Drawable(double depth, Consumer<Graphics2D> draw) {
    }

    private void collect(List<Drawable> below, List<Drawable> above, Lamps lamps, int locks) {
        Playfield field = table.field();
        TableSpec spec = table.spec();
        for (Flipper f : field.flippers()) {
            double h = field.layerHeight(f.layer);
            (f.layer > 0 ? above : below).add(new Drawable(f.py, g -> part(g, f.px, f.py, h,
                    gg -> Art.flipper(gg, Art.Shadows.direct(gg), f.px, f.py, f.length, f.angle(), p))));
        }
        for (Part part : spec.parts()) {
            double h = field.layerHeight(part.layer());
            List<Drawable> list = part.layer() > 0 ? above : below;
            double[] n = part.nums();
            switch (part.kind()) {
                case "drop" -> {
                    Segment s = table.target(part.id());
                    double up = s == null || s.down ? 0 : 1;
                    Color face = spec.color(part.name(1, "p7"));
                    list.add(new Drawable(n[1], g -> part(g, n[0], n[1], h,
                            gg -> Art.dropTarget(gg, Art.Shadows.direct(gg), n[0], n[1], n[2], face, up))));
                }
                case "spinner" -> {
                    double a = table.spinnerAngle(part.id());
                    list.add(new Drawable(n[1], g -> part(g, n[0], n[1], h,
                            gg -> Art.spinnerPlate(gg, Art.Shadows.direct(gg), n[0], n[1], a))));
                }
                case "bumper" -> {
                    double lit = lamps.level(part.id());
                    if (lit > 0.02) {
                        Color cap = spec.color(part.name(1, "p3")), stripe = spec.color(part.name(2, "xffffff"));
                        list.add(new Drawable(n[1], g -> part(g, n[0], n[1], h,
                                gg -> Art.bumperLit(gg, n[0], n[1], n[2], cap, lit))));
                    }
                }
                case "toy" -> {
                    if (part.id().equals("ferris")) {
                        double angle = time * 0.35;
                        below.add(new Drawable(n[1], g -> part(g, n[0], n[1], 0,
                                gg -> Art.ferrisWheel(gg, n[0], n[1], n[2], angle, locks, p[5], time))));
                    } else if (part.id().equals("satellite")) {
                        double angle = time * 2.2;
                        below.add(new Drawable(n[1], g -> part(g, n[0], n[1], 0,
                                gg -> Art.satelliteTop(gg, Art.Shadows.direct(gg), n[0], n[1], n[2], angle, p[5]))));
                    } else if (part.id().equals("kraken") && lamps.level("lock") > 0.02) {
                        double lit = lamps.level("lock");
                        below.add(new Drawable(n[1] - 1, g -> part(g, n[0], n[1], 0, gg -> {
                            gg.setPaint(new RadialGradientPaint(new Point2D.Double(n[0], n[1] + 2), 9f, new float[]{0f, 1f},
                                    new Color[]{Art.alpha(p[6], (int) (200 * lit)), Art.alpha(p[6], 0)}));
                            gg.fill(new Ellipse2D.Double(n[0] - 9, n[1] - 7, 18, 18));
                        })));
                    }
                }
                case "hole" -> {
                    if (part.id().equals("moonlock")) {
                        // the lock tube on the Moon Dock shows the locked balls
                        list.add(new Drawable(n[1], g -> part(g, n[0], n[1], h, gg -> lockTube(gg, n[0], n[1], locks))));
                    }
                }
                default -> {
                }
            }
        }
        for (Ball b : field.balls()) {
            if (b.state == Ball.State.LOST) continue;
            double h = b.height(field);
            boolean high = b.state == Ball.State.ON_TRACK || b.layer > 0;
            (high ? above : below).add(new Drawable(b.y + 0.5, g -> ball(g, b, h)));
        }
        below.add(new Drawable(Table.LANE_Y + 6, this::plunger));
    }

    /** Draws a part in table units at {@code (x, y)}, {@code h} above the playfield, through the local perspective. */
    private void part(Graphics2D g, double x, double y, double h, Consumer<Graphics2D> draw) {
        AffineTransform at = g.getTransform();
        g.transform(view.local(x, y));
        if (h != 0) g.translate(0, -h);
        draw.accept(g);
        g.setTransform(at);
    }

    // --8<-- [start:ball]
    /** A ball: its shadow on the floor below it, then the chrome sphere, smaller further up the table. */
    private void ball(Graphics2D g, Ball b, double h) {
        double r = b.radius;
        double s = view.scale(b.x, b.y);
        double floorH = b.state == Ball.State.ON_TRACK ? h : table.field().layerHeight(b.layer);
        Point2D.Double shadowAt = view.view(b.x + (h - floorH) * 0.35 + 1.2, b.y + (h - floorH) * 0.6 - floorH + 1.5);
        g.setPaint(new RadialGradientPaint(shadowAt, (float) (r * s * 1.1), new float[]{0f, 1f},
                new Color[]{c(0x000000, 150), c(0x000000, 0)}));
        g.fill(new Ellipse2D.Double(shadowAt.x - r * s * 1.1, shadowAt.y - r * s * 0.8, r * s * 2.2, r * s * 1.6));
        Point2D.Double centre = view.view(b.x, b.y - h - r * 0.9);
        double scaleX = Math.hypot(g.getTransform().getScaleX(), g.getTransform().getShearY());
        chrome.draw(g, centre.x, centre.y, r * s * scaleX);
    }
    // --8<-- [end:ball]

    private void lockTube(Graphics2D g, double x, double y, int locks) {
        Shape tube = new RoundRectangle2D.Double(x - 13, y - 6, 38, 13, 6, 6);
        g.setColor(c(0x9ab8ff, 50));
        g.fill(tube);
        for (int i = 0; i < Math.min(3, locks); i++) {
            double bx = x - 6 + i * 11;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(bx - 1.5, y - 2), 5.5f, new float[]{0f, 1f},
                    new Color[]{c(0xffffff), c(0x4a4a58)}));
            g.fill(new Ellipse2D.Double(bx - 5, y - 5, 10, 10));
        }
        g.setColor(c(0xd8e8ff, 180));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(tube);
    }

    /** The plunger: the rod with its coiled spring, pulled back by {@code pull}, and its rubber tip. */
    private void plunger(Graphics2D g) {
        double pull = table.plunger().pull();
        double x = Table.LANE_X, back = pull * 12;
        part(g, x, 360, 0, gg -> {
            Shape rod = new java.awt.geom.Rectangle2D.Double(x - 1.4, 350 + back, 2.8, 44 - back);
            Art.Shadows.direct(gg).shadow(rod, 4);
            Art.extrude(gg, rod, 2, 4.8, c(0x9a9aa8), new GradientPaint((float) (x - 1.4), 0, c(0xffffff), (float) (x + 1.4), 0, c(0x7a7a88)));
            double top = 357 + back, bottom = 382;
            double step = (bottom - top) / 13;
            for (double yy = bottom; yy >= top; yy -= step) {
                AffineTransform at = gg.getTransform();
                gg.translate(x, yy - 3.4);
                gg.setColor(c(0x26262c));
                gg.setStroke(new BasicStroke(1.3f));
                gg.draw(new java.awt.geom.Arc2D.Double(-4.2, -4.4, 8.4, 6.0, 180, 180, java.awt.geom.Arc2D.OPEN));
                gg.setPaint(new GradientPaint(-4, 0, c(0xffffff), 4, 0, c(0x8a8a98)));
                gg.setStroke(new BasicStroke(0.9f));
                Path2D near = new Path2D.Double();
                near.moveTo(-4.2, -1.0);
                near.curveTo(-4.0, -5.0, 4.0, -5.6, 4.2, -2.0);
                gg.draw(near);
                gg.setTransform(at);
            }
            Shape tip = new RoundRectangle2D.Double(x - 4, 347 + back, 8, 4, 2, 2);
            Art.Shadows.direct(gg).shadow(tip, 7);
            Art.extrude(gg, tip, 0, 7, c(0x2a2a30), c(0x4a4a52));
        });
    }

    /** The lamps that are lit, glowing over the floor (or over a raised floor, for {@code upper} 1). */
    private void lampGlow(Graphics2D g, Lamps lamps, int upper) {
        for (TableArt.Lamp l : art.lamps) {
            if ((l.layer() > 0 ? 1 : 0) != upper || l.shape().equals("bumper")) continue;
            double lit = lamps.level(l.name());
            if (lit <= 0.02) continue;
            double h = table.field().layerHeight(l.layer());
            part(g, l.x(), l.y(), h, gg -> {
                switch (l.shape()) {
                    case "insert" -> Art.insert(gg, l.x(), l.y(), l.deg(), l.color(), lit);
                    case "round" -> Art.roundInsert(gg, l.x(), l.y(), l.size(), l.color(), lit);
                    case "star" -> {
                        gg.setColor(Art.alpha(l.color(), (int) (60 * lit)));
                        gg.fill(new Ellipse2D.Double(l.x() - l.size(), l.y() - l.size(), l.size() * 2, l.size() * 2));
                        gg.setColor(Art.alpha(l.color(), (int) (255 * lit)));
                        gg.fill(Art.star(l.x(), l.y(), l.size()));
                    }
                    default -> {
                    }
                }
            });
        }
    }

    // ---- the tilted picture ----------------------------------------------------------------------------

    private void image(Graphics2D g, BufferedImage img, Warped w, AffineTransform now) {
        AffineTransform at = g.getTransform();
        g.setTransform(new AffineTransform());
        if (sameScale(w.device(), now)) {
            g.drawImage(img, w.box().x + (int) Math.round(now.getTranslateX() - w.device().getTranslateX()),
                    w.box().y + (int) Math.round(now.getTranslateY() - w.device().getTranslateY()), null);
        } else {
            // the window changed size: the old picture, scaled, until the new one is ready
            double k = now.getScaleX() / w.device().getScaleX();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double x = (w.box().x - w.device().getTranslateX()) * k + now.getTranslateX();
            double y = (w.box().y - w.device().getTranslateY()) * k + now.getTranslateY();
            g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(img.getWidth() * k),
                    (int) Math.round(img.getHeight() * k), null);
        }
        g.setTransform(at);
    }

    private static boolean sameScale(AffineTransform a, AffineTransform b) {
        return Math.abs(a.getScaleX() - b.getScaleX()) < 1e-6 && Math.abs(a.getScaleY() - b.getScaleY()) < 1e-6;
    }

    private Warped warped(AffineTransform device) {
        Warped w = warped;
        if (w != null && sameScale(w.device(), device)) return w;
        AffineTransform d = new AffineTransform(device);
        if (synchronous) {
            warped = warp(d);
            return warped;
        }
        if (!warping) {
            warping = true;
            WARPER.submit(() -> {
                try {
                    warped = warp(d);
                } finally {
                    warping = false;
                }
            });
        }
        return w;
    }

    // --8<-- [start:warp]
    private Warped warp(AffineTransform device) {
        Rectangle box = bounds(device);
        AffineTransform toPixels = new AffineTransform();
        toPixels.translate(-box.x, -box.y);
        toPixels.concatenate(device);
        BufferedImage lower = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage upper = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_ARGB);
        view.warp(art.lower, TableArt.LEFT, TableArt.TOP, TableArt.RIGHT, TableArt.BOTTOM, lower, toPixels);
        view.warp(art.upper, TableArt.LEFT, TableArt.TOP, TableArt.RIGHT, TableArt.BOTTOM, upper, toPixels);
        Graphics2D g = upper.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.transform(toPixels);
        glass(g);
        g.dispose();
        // the cabinet's front: the same in every frame but for the knob and the buttons
        Point2D a = device.transform(new Point2D.Double(QUAD[6] - 20, QUAD[7] - 4), null);
        Point2D b = device.transform(new Point2D.Double(QUAD[4] + 20, 400), null);
        Rectangle frontBox = new Rectangle((int) Math.floor(a.getX()), (int) Math.floor(a.getY()),
                (int) Math.ceil(b.getX() - a.getX()) + 1, (int) Math.ceil(b.getY() - a.getY()) + 1);
        BufferedImage front = new BufferedImage(frontBox.width, frontBox.height, BufferedImage.TYPE_INT_ARGB);
        g = front.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-frontBox.x, -frontBox.y);
        g.transform(device);
        frontPanel(g);
        g.dispose();
        return new Warped(device, box, lower, upper, frontBox, front);
    }
    // --8<-- [end:warp]

    private Rectangle bounds(AffineTransform device) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] corner : new double[][]{{TableArt.LEFT, TableArt.TOP}, {TableArt.RIGHT, TableArt.TOP},
                {TableArt.RIGHT, TableArt.BOTTOM}, {TableArt.LEFT, TableArt.BOTTOM}}) {
            Point2D q = device.transform(view.view(corner[0], corner[1]), null);
            minX = Math.min(minX, q.getX());
            minY = Math.min(minY, q.getY());
            maxX = Math.max(maxX, q.getX());
            maxY = Math.max(maxY, q.getY());
        }
        int x = (int) Math.floor(minX), y = (int) Math.floor(minY);
        return new Rectangle(x, y, Math.max(1, (int) Math.ceil(maxX) - x), Math.max(1, (int) Math.ceil(maxY) - y));
    }

    // ---- the cabinet around the table ----------------------------------------------------------------------

    private static void cabinet(Graphics2D g) {
        double[] q = QUAD;
        double wall = 14;
        double x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], x2 = q[4], y2 = q[5], x3 = q[6], y3 = q[7];
        Path2D left = new Path2D.Double();
        left.moveTo(x0, y0);
        left.lineTo(x0 - wall * 0.55, y0 - wall * 0.2);
        left.lineTo(x3 - wall, y3);
        left.lineTo(x3, y3);
        left.closePath();
        g.setPaint(new GradientPaint((float) (x3 - wall), 0, c(0x3c3a48), (float) x3, 0, c(0x9894a8)));
        g.fill(left);
        Path2D right = new Path2D.Double();
        right.moveTo(x1, y1);
        right.lineTo(x1 + wall * 0.55, y1 - wall * 0.2);
        right.lineTo(x2 + wall, y2);
        right.lineTo(x2, y2);
        right.closePath();
        g.setPaint(new GradientPaint((float) x2, 0, c(0x8a86a0), (float) (x2 + wall), 0, c(0x2c2a36)));
        g.fill(right);
        g.setColor(c(0xd8d4e8, 120));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x0, y0, x3, y3));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void glass(Graphics2D g) {
        double[] q = QUAD;
        Path2D s = new Path2D.Double();
        s.moveTo(q[0], q[1]);
        s.lineTo(q[2], q[3]);
        s.lineTo(q[4], q[5]);
        s.lineTo(q[6], q[7]);
        s.closePath();
        Shape old = g.getClip();
        g.clip(s);
        g.setPaint(new LinearGradientPaint((float) q[0], (float) q[1], (float) q[4], (float) q[5],
                new float[]{0f, 0.38f, 0.44f, 0.5f, 1f},
                new Color[]{c(0xffffff, 0), c(0xffffff, 0), c(0xffffff, 22), c(0xffffff, 0), c(0xffffff, 0)}));
        g.fill(s);
        g.setClip(old);
    }

    /** The cabinet front and the lockdown bar. */
    private static void frontPanel(Graphics2D g) {
        double[] q = QUAD;
        double xl = q[6], xr = q[4], y = q[7];
        Path2D panel = new Path2D.Double();
        panel.moveTo(xl - 14, y);
        panel.lineTo(xr + 14, y);
        panel.lineTo(xr + 16, y + 40);
        panel.lineTo(xl - 16, y + 40);
        panel.closePath();
        g.setPaint(new GradientPaint(0, (float) y, c(0x1e1c26), 0, (float) (y + 40), c(0x0a090e)));
        g.fill(panel);
        RoundRectangle2D bar = new RoundRectangle2D.Double(xl - 16, y - 2, (xr - xl) + 32, 9, 6, 6);
        g.setPaint(new LinearGradientPaint(0f, (float) (y - 2), 0f, (float) (y + 7), new float[]{0f, 0.35f, 0.6f, 1f},
                new Color[]{c(0xf4f4fa), c(0xa8a8b8), c(0x5a5a68), c(0x9a9aa8)}));
        g.fill(bar);
    }

    /** The plunger knob on the line of the rod, continued towards the player, and the flipper buttons. */
    private void knob(Graphics2D g) {
        double xl = QUAD[6], xr = QUAD[4], y = QUAD[7];
        // the knob: on the rod's line, continued towards the player
        Point2D.Double p0 = view.view(Table.LANE_X, Table.LENGTH * 0.9), p1 = view.view(Table.LANE_X, Table.LENGTH);
        double dx = p1.x - p0.x, dy = p1.y - p0.y, dl = Math.hypot(dx, dy);
        dx /= dl;
        dy /= dl;
        double pull = table.plunger().pull() * 6;
        double sx0 = p1.x + dx * 8, sy0 = p1.y + dy * 8 - 3;
        double ks = 1.5;
        double nx = -dy, ny = dx;
        g.setPaint(new GradientPaint(0, (float) sy0, c(0xd8d8e4), 0, (float) (sy0 + 4 * ks), c(0x6a6a78)));
        g.fill(new Ellipse2D.Double(sx0 - 6 * ks, sy0 - 2 * ks, 12 * ks, 5 * ks));
        double len = 13 * ks + pull, hw = 1.8 * ks;
        Path2D shaft = new Path2D.Double();
        shaft.moveTo(sx0 + nx * hw, sy0 + ny * hw);
        shaft.lineTo(sx0 + dx * len + nx * hw, sy0 + dy * len + ny * hw);
        shaft.lineTo(sx0 + dx * len - nx * hw, sy0 + dy * len - ny * hw);
        shaft.lineTo(sx0 - nx * hw, sy0 - ny * hw);
        shaft.closePath();
        g.setPaint(new GradientPaint((float) (sx0 - hw), 0, c(0xffffff), (float) (sx0 + hw), 0, c(0x7a7a88)));
        g.fill(shaft);
        double kx = sx0 + dx * (len + 5 * ks), ky = sy0 + dy * (len + 5 * ks);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(kx - 2 * ks, ky - 3 * ks), (float) (8 * ks), new float[]{0f, 0.5f, 1f},
                new Color[]{c(0xffb0a0), c(0xd83a2a), c(0x6a1010)}));
        g.fill(new Ellipse2D.Double(kx - 6 * ks, ky - 6 * ks, 12 * ks, 12 * ks));
        g.setColor(c(0xffffff, 200));
        g.fill(new Ellipse2D.Double(kx - 3.5 * ks, ky - 4 * ks, 3 * ks, 2 * ks));
        boolean leftDown = !table.leftFlippers().isEmpty() && table.leftFlippers().get(0).held();
        boolean rightDown = !table.rightFlippers().isEmpty() && table.rightFlippers().get(0).held();
        double[][] buttons = {{xl - 18, leftDown ? 1 : 0}, {xr + 12, rightDown ? 1 : 0}};
        for (double[] b : buttons) {
            double bx = b[0] + (b[0] < xl ? b[1] * 1.5 : -b[1] * 1.5);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(bx + 2, y + 18), 5f, new float[]{0f, 1f},
                    new Color[]{c(0xfff0a0), c(0xc89020)}));
            g.fill(new Ellipse2D.Double(bx, y + 14, 6, 9));
        }
    }
}
