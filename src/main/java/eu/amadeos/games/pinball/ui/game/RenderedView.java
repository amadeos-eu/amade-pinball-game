package eu.amadeos.games.pinball.ui.game;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Flipper;
import eu.amadeos.gameengine.pinball.Lamps;
import eu.amadeos.gameengine.pinball.Playfield;
import eu.amadeos.gameengine.pinball.Segment;
import eu.amadeos.games.pinball.domain.Table;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The table as Blender rendered it (tools/render): a picture of the whole machine with every lamp off, what each
 * lamp adds when it is lit, the flippers, drop targets and plunger at their positions, and on top the balls,
 * drawn here pixel by pixel as chrome that mirrors what the table around them looks like from where they are.
 *
 * <p>The camera that took the pictures stood where the game's screen puts the playfield ({@link TableView#QUAD}),
 * so a point on the table lands where the physics has it. Its matrix, written beside the pictures, places the
 * balls; the distance of every pixel from the camera hides a ball behind a post or a wall, and the ramps,
 * wire forms and plastics, rendered alone with their own distances, are laid over a ball that rolls beneath.
 */
final class RenderedView implements View {
    static final String ROOT = "/eu/amadeos/games/pinball/render/";
    /** The logical screen region the pictures cover: x 0 to 412, y 0 to 400. */
    static final double REGION_W = 412, REGION_H = 400;
    /** How much brighter than the probes the ball is, and how much light it always has. */
    private static final double GAIN = Double.parseDouble(System.getProperty("amade.pinball.gain", "1.7")),
            LIFT = Double.parseDouble(System.getProperty("amade.pinball.lift", "10"));

    private final Table table;
    private final int rw, rh;
    private final double s, length;
    private final double[] m = new double[16];
    private final double[] eye = new double[3], right = new double[3], up = new double[3], back = new double[3];
    private final BufferedImage base, over;
    private final int[] depth, overDepth;
    private final Map<String, Sprite> lamps = new LinkedHashMap<>();
    private final Map<String, Sprite[]> flippers = new HashMap<>();
    private final Map<String, Sprite> drops = new HashMap<>();
    private Sprite[] plunger = new Sprite[0];
    private double travel = 12;
    private int pnx, pny, pw, ph;
    private double px0, px1, py0, py1;
    private int[] probes;
    private int probesWidth;

    private Scaled scaled;
    private BufferedImage frame;
    private int[] pixels;
    private long seenSteps = -1;
    private double idle, alpha = 1;

    /** A picture placed at {@code (x, y)} in the pixels of the picture it belongs to. */
    private record Sprite(BufferedImage image, int x, int y) {
    }

    /** A lamp's light at the device's scale: what it adds to each pixel of its box. */
    private record Patch(int x, int y, int w, int h, int[] rgb) {
    }

    /** Everything at the device's scale. */
    private record Scaled(double k, int w, int h, int[] base, int[] over, int[] depth, int[] overDepth,
                          Map<String, Patch> lamps, Map<String, Sprite[]> flippers, Map<String, Sprite> drops,
                          Sprite[] plunger) {
    }

    /** The rendered table {@code id}, or null if there are no pictures for it. */
    static RenderedView load(Table table) {
        String dir = ROOT + table.spec().id() + "/";
        if (RenderedView.class.getResource(dir + "table.txt") == null) return null;
        return new RenderedView(table, dir);
    }

    private RenderedView(Table table, String dir) {
        this.table = table;
        int[] size = {0, 0};
        List<String[]> lines = new ArrayList<>();
        try (InputStream in = RenderedView.class.getResourceAsStream(dir + "table.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.isBlank() && !line.startsWith("#")) lines.add(line.trim().split("\\s+"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        double ss = 0, ll = 0;
        for (String[] w : lines) {
            switch (w[0]) {
                case "size" -> {
                    size[0] = Integer.parseInt(w[1]);
                    size[1] = Integer.parseInt(w[2]);
                }
                case "world" -> {
                    ss = d(w[1]);
                    ll = d(w[3]);
                }
                case "matrix" -> {
                    for (int i = 0; i < 16; i++) m[i] = d(w[i + 1]);
                }
                case "eye" -> {
                    for (int i = 0; i < 3; i++) eye[i] = d(w[i + 1]);
                }
                case "basis" -> {
                    for (int i = 0; i < 3; i++) {
                        right[i] = d(w[i + 1]);
                        up[i] = d(w[i + 4]);
                        back[i] = d(w[i + 7]);
                    }
                }
                case "lamp" -> lamps.put(w[1], new Sprite(image(dir + "lamp-" + w[1] + ".png"), i(w[2]), i(w[3])));
                case "flipper" -> {
                    Sprite[] frames = new Sprite[i(w[2])];
                    for (int k = 0; k < frames.length; k++) {
                        frames[k] = new Sprite(image(dir + "flip-" + w[1] + "-" + k + ".png"), i(w[3]), i(w[4]));
                    }
                    flippers.put(w[1], frames);
                }
                case "drop" -> drops.put(w[1], new Sprite(image(dir + "drop-" + w[1] + ".png"), i(w[2]), i(w[3])));
                case "plunger" -> {
                    plunger = new Sprite[i(w[1])];
                    for (int k = 0; k < plunger.length; k++) {
                        plunger[k] = new Sprite(image(dir + "plunger-" + k + ".png"), i(w[2]), i(w[3]));
                    }
                    travel = d(w[4]);
                }
                case "probes" -> {
                    pnx = i(w[1]);
                    pny = i(w[2]);
                    pw = i(w[3]);
                    ph = i(w[4]);
                    px0 = d(w[5]);
                    px1 = d(w[6]);
                    py0 = d(w[7]);
                    py1 = d(w[8]);
                }
                default -> {
                }
            }
        }
        rw = size[0];
        rh = size[1];
        s = ss;
        length = ll;
        base = image(dir + (RenderedView.class.getResource(dir + "base.jpg") != null ? "base.jpg" : "base.png"));
        over = image(dir + "over.png");
        depth = gray16(image(dir + "depth.png"));
        overDepth = gray16(image(dir + "overdepth.png"));
        BufferedImage atlas = image(dir + "probes.png");
        probesWidth = atlas.getWidth();
        probes = atlas.getRGB(0, 0, atlas.getWidth(), atlas.getHeight(), null, 0, atlas.getWidth());
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    private static BufferedImage image(String path) {
        try (InputStream in = RenderedView.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A 16-bit grey picture's values: distances from the camera in millimetres. */
    private static int[] gray16(BufferedImage img) {
        Raster r = img.getRaster();
        int w = img.getWidth(), h = img.getHeight();
        int[] out = new int[w * h];
        r.getSamples(0, 0, w, h, 0, out);
        if (r.getSampleModel().getSampleSize(0) == 8) {
            for (int i = 0; i < out.length; i++) out[i] *= 257;
        }
        return out;
    }

    @Override
    public void synchronous(boolean on) {
        // the pictures are scaled on the drawing thread anyway
    }

    // ---- every frame -------------------------------------------------------------------------------

    // --8<-- [start:paint]
    @Override
    public void paint(Graphics2D g, Lamps lampState, int locks, double seconds, double alpha) {
        long steps = table.steps();
        idle = steps == seenSteps ? idle + seconds : 0;
        seenSteps = steps;
        this.alpha = idle > 0.05 ? 1 : alpha;
        AffineTransform t = g.getTransform();
        double k = t.getScaleX();
        Scaled sc = scaled;
        if (sc == null || Math.abs(sc.k() - k) > 1e-6) {
            sc = scale(k);
            scaled = sc;
            frame = new BufferedImage(sc.w(), sc.h(), BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        }
        System.arraycopy(sc.base(), 0, pixels, 0, pixels.length);
        for (Map.Entry<String, Patch> e : sc.lamps().entrySet()) {
            double lit = lampState.level(e.getKey());
            if (lit > 0.02) add(e.getValue(), lit, sc.w(), sc.h());
        }
        sprites(sc);
        List<Ball> balls = new ArrayList<>();
        for (Ball b : table.field().balls()) if (b.state != Ball.State.LOST) balls.add(b);
        double f = sc.k() * REGION_W / rw;
        List<double[]> places = new ArrayList<>();
        for (Ball b : balls) places.add(place(b, f));
        for (int i = 0; i < balls.size(); i++) shadow(sc, balls.get(i), places.get(i), f);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < balls.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> -places.get(i)[3]));
        for (int i : order) chrome(sc, balls.get(i), places.get(i));
        g.setTransform(new AffineTransform());
        g.drawImage(frame, (int) Math.round(t.getTranslateX()), (int) Math.round(t.getTranslateY()), null);
        g.setTransform(t);
    }
    // --8<-- [end:paint]

    /** Adds a lamp's light, {@code lit} of it, to the frame. */
    private void add(Patch p, double lit, int w, int h) {
        int a = (int) Math.round(Math.min(1, lit) * 256);
        for (int y = 0; y < p.h(); y++) {
            int fy = p.y() + y;
            if (fy < 0 || fy >= h) continue;
            int row = fy * w, src = y * p.w();
            for (int x = 0; x < p.w(); x++) {
                int fx = p.x() + x;
                if (fx < 0 || fx >= w) continue;
                int l = p.rgb()[src + x];
                if ((l & 0xffffff) == 0) continue;
                int c = pixels[row + fx];
                int r = Math.min(255, ((c >> 16) & 255) + ((((l >> 16) & 255) * a) >> 8));
                int gg = Math.min(255, ((c >> 8) & 255) + ((((l >> 8) & 255) * a) >> 8));
                int b = Math.min(255, (c & 255) + (((l & 255) * a) >> 8));
                pixels[row + fx] = (r << 16) | (gg << 8) | b;
            }
        }
    }

    /** The drop targets that stand, the flippers at their angles and the plunger at its pull. */
    private void sprites(Scaled sc) {
        Graphics2D g = frame.createGraphics();
        for (Map.Entry<String, Sprite> e : sc.drops().entrySet()) {
            Segment target = table.target(e.getKey());
            if (target != null && !target.down) draw(g, e.getValue());
        }
        for (Flipper fl : table.field().flippers()) {
            Sprite[] frames = sc.flippers().get(fl.id);
            if (frames == null) continue;
            double raised = Math.max(0, Math.min(1, fl.raised()));
            draw(g, frames[(int) Math.round(raised * (frames.length - 1))]);
        }
        if (sc.plunger().length > 0) {
            double pull = Math.max(0, Math.min(1, table.plunger().pull() * 12 / travel));
            draw(g, sc.plunger()[(int) Math.round(pull * (sc.plunger().length - 1))]);
        }
        g.dispose();
    }

    private static void draw(Graphics2D g, Sprite sp) {
        g.drawImage(sp.image(), sp.x(), sp.y(), null);
    }

    // ---- the balls ---------------------------------------------------------------------------------

    /**
     * Where a ball is drawn in this frame, in the frame's pixels: {cx, cy, radius, depth of its centre in
     * metres, table x, table y, floor height, centre height}.
     */
    private double[] place(Ball b, double f) {
        Playfield field = table.field();
        double h = b.height(field);
        double x = b.x, y = b.y;
        double[] was = table.before(b);
        if (was != null && Math.hypot(b.x - was[0], b.y - was[1]) <= 30) {
            x = was[0] + (b.x - was[0]) * alpha;
            y = was[1] + (b.y - was[1]) * alpha;
            h = was[2] + (h - was[2]) * alpha;
        }
        double zc = h + b.radius;
        double[] c = project(x, y, zc);
        double wx = x * s, wy = (length - y) * s, wz = zc * s, rr = b.radius * s;
        double[] e = clip(wx + right[0] * rr, wy + right[1] * rr, wz + right[2] * rr);
        double ex = (e[0] / e[3] + 1) / 2 * rw, ey = (1 - e[1] / e[3]) / 2 * rh;
        double radius = Math.hypot(ex - c[0], ey - c[1]);
        return new double[]{c[0] * f, c[1] * f, radius * f, c[2], x, y, h, zc};
    }

    /** A point on the table, {@code h} above the playfield: {x, y} in the rendered picture's pixels, and its depth. */
    private double[] project(double x, double y, double h) {
        double[] c = clip(x * s, (length - y) * s, h * s);
        return new double[]{(c[0] / c[3] + 1) / 2 * rw, (1 - c[1] / c[3]) / 2 * rh, c[3]};
    }

    /** Where a point on the table, {@code h} above the playfield, lies on the logical screen. */
    double[] screen(double x, double y, double h) {
        double[] p = project(x, y, h);
        return new double[]{p[0] * REGION_W / rw, p[1] * REGION_W / rw};
    }

    private double[] clip(double x, double y, double z) {
        double[] o = new double[4];
        for (int r = 0; r < 4; r++) o[r] = m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z + m[r * 4 + 3];
        return o;
    }

    /** The soft shadow on the floor right under a ball, where that floor is not hidden by something nearer. */
    private void shadow(Scaled sc, Ball b, double[] at, double f) {
        double x = at[4], y = at[5], h = at[6], r = b.radius;
        double[] c = project(x, y, h), ex = project(x + r, y, h), ey = project(x, y + r, h);
        double cx = c[0] * f, cy = c[1] * f;
        double ax = Math.abs(ex[0] - c[0]) * f * 1.15, ay = Math.abs(ey[1] - c[1]) * f * 1.15 + 0.5;
        double floorMm = c[2] * 1000;
        int x0 = (int) Math.floor(cx - ax), x1 = (int) Math.ceil(cx + ax);
        int y0 = (int) Math.floor(cy - ay), y1 = (int) Math.ceil(cy + ay);
        for (int py = Math.max(0, y0); py <= Math.min(sc.h() - 1, y1); py++) {
            double dy = (py + 0.5 - cy) / ay;
            for (int px = Math.max(0, x0); px <= Math.min(sc.w() - 1, x1); px++) {
                double dx = (px + 0.5 - cx) / ax;
                double q = dx * dx + dy * dy;
                if (q >= 1) continue;
                int i = py * sc.w() + px;
                if (sc.depth()[i] < floorMm - 8) continue;
                double dark = 1 - 0.62 * (1 - q) * (1 - q);
                int p = pixels[i];
                pixels[i] = ((int) (((p >> 16) & 255) * dark) << 16) | ((int) (((p >> 8) & 255) * dark) << 8)
                        | (int) ((p & 255) * dark);
            }
        }
    }

    // --8<-- [start:chrome]
    /** The ball itself: each pixel mirrors what the table shows in that direction, seen from the ball. */
    private void chrome(Scaled sc, Ball b, double[] at) {
        double cx = at[0], cy = at[1], rad = at[2], centreMm = at[3] * 1000, rMm = b.radius * s * 1000;
        if (rad < 0.5) return;
        double wx = at[4] * s, wy = (length - at[5]) * s, wz = at[7] * s;
        double vx = wx - eye[0], vy = wy - eye[1], vz = wz - eye[2];
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl;
        vy /= vl;
        vz /= vl;
        // the four probes around the ball and their weights
        double gx = Math.max(0, Math.min(pnx - 1, (at[4] - px0) / (px1 - px0) * (pnx - 1)));
        double gy = Math.max(0, Math.min(pny - 1, (at[5] - py0) / (py1 - py0) * (pny - 1)));
        int ix = Math.min(pnx - 2, (int) gx), iy = Math.min(pny - 2, (int) gy);
        double fx = gx - ix, fy = gy - iy;
        int[] probe = {iy * pnx + ix, iy * pnx + ix + 1, (iy + 1) * pnx + ix, (iy + 1) * pnx + ix + 1};
        double[] weight = {(1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy};
        double[] rgb = new double[3];
        int x0 = (int) Math.floor(cx - rad - 1), x1 = (int) Math.ceil(cx + rad + 1);
        int y0 = (int) Math.floor(cy - rad - 1), y1 = (int) Math.ceil(cy + rad + 1);
        int w = sc.w();
        for (int py = Math.max(0, y0); py <= Math.min(sc.h() - 1, y1); py++) {
            for (int px = Math.max(0, x0); px <= Math.min(w - 1, x1); px++) {
                double u = (px + 0.5 - cx) / rad, v = -(py + 0.5 - cy) / rad;
                double q = u * u + v * v;
                double dist = Math.sqrt(q) * rad;
                double cover = Math.max(0, Math.min(1, rad - dist + 0.5));
                if (cover <= 0) continue;
                double nz = Math.sqrt(Math.max(0, 1 - Math.min(1, q)));
                int i = py * w + px;
                double surfaceMm = centreMm - nz * rMm;
                if (sc.depth()[i] < surfaceMm - 1.5) continue;
                double nx = u * right[0] + v * up[0] + nz * back[0];
                double ny = u * right[1] + v * up[1] + nz * back[1];
                double nzz = u * right[2] + v * up[2] + nz * back[2];
                double dn = vx * nx + vy * ny + vz * nzz;
                double rx = vx - 2 * dn * nx, ry = vy - 2 * dn * ny, rz = vz - 2 * dn * nzz;
                double eu = 0.5 - Math.atan2(ry, rx) / (2 * Math.PI);
                double ev = 0.5 - Math.asin(Math.max(-1, Math.min(1, rz))) / Math.PI;
                rgb[0] = rgb[1] = rgb[2] = 0;
                for (int k = 0; k < 4; k++) sample(probe[k], eu, ev, weight[k], rgb);
                // chrome mirrors nearly all; the probes, seen from the floor, are darker than the ball looks
                double refl = GAIN * (0.8 + 0.2 * Math.pow(1 - nz, 3));
                double r = rgb[0] * refl + LIFT, g = rgb[1] * refl + LIFT, bl = rgb[2] * refl + LIFT;
                // a ramp, a wire or a plastic between the ball and the eye lies over it
                int od = sc.overDepth()[i];
                if (od < surfaceMm - 0.5) {
                    int o = sc.over()[i];
                    double a = (o >>> 24) / 255.0;
                    r = r * (1 - a) + ((o >> 16) & 255) * a;
                    g = g * (1 - a) + ((o >> 8) & 255) * a;
                    bl = bl * (1 - a) + (o & 255) * a;
                }
                int p = pixels[i];
                r = ((p >> 16) & 255) * (1 - cover) + r * cover;
                g = ((p >> 8) & 255) * (1 - cover) + g * cover;
                bl = (p & 255) * (1 - cover) + bl * cover;
                pixels[i] = (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
            }
        }
    }

    // --8<-- [end:chrome]

    /** Adds {@code weight} of panorama {@code n}'s colour at {@code (u, v)} (0 to 1 each) to {@code rgb}. */
    private void sample(int n, double u, double v, double weight, double[] rgb) {
        if (weight <= 0) return;
        int ox = (n % pnx) * pw, oy = (n / pnx) * ph;
        double x = u * pw - 0.5, y = Math.max(0, Math.min(ph - 1, v * ph - 0.5));
        int xa = (int) Math.floor(x), ya = (int) Math.floor(y);
        double tx = x - xa, ty = y - ya;
        int yb = Math.min(ph - 1, ya + 1);
        int xA = Math.floorMod(xa, pw), xB = Math.floorMod(xa + 1, pw);
        int c00 = probes[(oy + ya) * probesWidth + ox + xA], c10 = probes[(oy + ya) * probesWidth + ox + xB];
        int c01 = probes[(oy + yb) * probesWidth + ox + xA], c11 = probes[(oy + yb) * probesWidth + ox + xB];
        for (int ch = 0, shift = 16; ch < 3; ch++, shift -= 8) {
            double a = ((c00 >> shift) & 255) * (1 - tx) + ((c10 >> shift) & 255) * tx;
            double b = ((c01 >> shift) & 255) * (1 - tx) + ((c11 >> shift) & 255) * tx;
            rgb[ch] += weight * (a * (1 - ty) + b * ty);
        }
    }

    private static int clamp(double v) {
        return v <= 0 ? 0 : v >= 255 ? 255 : (int) v;
    }

    // ---- the pictures at the device's scale --------------------------------------------------------

    private Scaled scale(double k) {
        int w = (int) Math.round(REGION_W * k), h = (int) Math.round(REGION_H * k);
        double f = k * REGION_W / rw;
        int[] baseRgb = scaled(base, w, h, BufferedImage.TYPE_INT_RGB).getRGB(0, 0, w, h, null, 0, w);
        int[] overArgb = scaled(over, w, h, BufferedImage.TYPE_INT_ARGB).getRGB(0, 0, w, h, null, 0, w);
        int[] dep = nearest(depth, w, h), odep = nearest(overDepth, w, h);
        Map<String, Patch> lampPatches = new LinkedHashMap<>();
        for (Map.Entry<String, Sprite> e : lamps.entrySet()) {
            Sprite sp = e.getValue();
            int x0 = (int) Math.floor(sp.x() * f), y0 = (int) Math.floor(sp.y() * f);
            int x1 = (int) Math.ceil((sp.x() + sp.image().getWidth()) * f), y1 = (int) Math.ceil((sp.y() + sp.image().getHeight()) * f);
            int pw2 = Math.max(1, x1 - x0), ph2 = Math.max(1, y1 - y0);
            int[] rgb = scaled(sp.image(), pw2, ph2, BufferedImage.TYPE_INT_RGB).getRGB(0, 0, pw2, ph2, null, 0, pw2);
            lampPatches.put(e.getKey(), new Patch(x0, y0, pw2, ph2, rgb));
        }
        Map<String, Sprite[]> flips = new HashMap<>();
        for (Map.Entry<String, Sprite[]> e : flippers.entrySet()) {
            Sprite[] frames = new Sprite[e.getValue().length];
            for (int i = 0; i < frames.length; i++) frames[i] = sprite(e.getValue()[i], f);
            flips.put(e.getKey(), frames);
        }
        Map<String, Sprite> dropSprites = new HashMap<>();
        for (Map.Entry<String, Sprite> e : drops.entrySet()) dropSprites.put(e.getKey(), sprite(e.getValue(), f));
        Sprite[] plungerSprites = new Sprite[plunger.length];
        for (int i = 0; i < plunger.length; i++) plungerSprites[i] = sprite(plunger[i], f);
        return new Scaled(k, w, h, baseRgb, overArgb, dep, odep, lampPatches, flips, dropSprites, plungerSprites);
    }

    private static Sprite sprite(Sprite sp, double f) {
        double x = sp.x() * f, y = sp.y() * f;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        int w = Math.max(1, (int) Math.round(sp.image().getWidth() * f)), h = Math.max(1, (int) Math.round(sp.image().getHeight() * f));
        return new Sprite(scaled(sp.image(), w, h, BufferedImage.TYPE_INT_ARGB_PRE), x0, y0);
    }

    /** The picture at {@code w × h}, shrunk in halving steps so that no detail is skipped. */
    static BufferedImage scaled(BufferedImage src, int w, int h, int type) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            cur = resample(cur, cur.getWidth() / 2, cur.getHeight() / 2, type == BufferedImage.TYPE_INT_RGB ? type : BufferedImage.TYPE_INT_ARGB_PRE);
        }
        return resample(cur, w, h, type);
    }

    private static BufferedImage resample(BufferedImage src, int w, int h, int type) {
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private int[] nearest(int[] src, int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(rh - 1, (int) ((y + 0.5) * rh / h));
            for (int x = 0; x < w; x++) {
                int sx = Math.min(rw - 1, (int) ((x + 0.5) * rw / w));
                out[y * w + x] = src[sy * rw + sx];
            }
        }
        return out;
    }
}
