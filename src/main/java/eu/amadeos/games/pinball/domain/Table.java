package eu.amadeos.games.pinball.domain;

import eu.amadeos.gameengine.pinball.Ball;
import eu.amadeos.gameengine.pinball.Disc;
import eu.amadeos.gameengine.pinball.Flipper;
import eu.amadeos.gameengine.pinball.Material;
import eu.amadeos.gameengine.pinball.Playfield;
import eu.amadeos.gameengine.pinball.Plunger;
import eu.amadeos.gameengine.pinball.Segment;
import eu.amadeos.gameengine.pinball.Track;
import eu.amadeos.gameengine.pinball.Zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine itself, built from a {@link TableSpec}: the playfield with its walls, posts, bumpers,
 * slingshots, flippers, targets, holes and ramps, and the mechanics around it that the rules use (the
 * shooter lane and its plunger, the holes that throw a ball back out, the drop target banks that reset, the
 * kickback, the magnet, the spin disc). It knows nothing about points: {@link Rules} decides those.
 *
 * <p>The geometry is in table units, 232 wide and 392 long, x to the right and y down the table.
 */
public final class Table {
    public static final double WIDTH = 232;
    public static final double LENGTH = 392;
    public static final double BALL_RADIUS = 5.5;
    /** The ball lies on the plunger here, in the shooter lane on the right. */
    public static final double LANE_X = 224;
    public static final double LANE_Y = 341;
    /** A ramp climbs this high before its wire form carries the ball back down. */
    public static final double RAMP_HEIGHT = 30;
    /**
     * How fast the table's time runs against the clock. A real ball crosses the table in a fraction of a second,
     * faster than the eye can follow on a screen at 60 frames; at four fifths it still feels quick, but each frame
     * shows the ball nearer to where it was. The paths do not change, only how fast they are followed.
     */
    public static final double PACE = 0.8;

    private final TableSpec spec;
    private final Playfield field = new Playfield(WIDTH, 386, Playfield.STANDARD_GRAVITY, 8, 2400);
    private final Plunger plunger = new Plunger(1.0, 260, 1500);
    private final List<Flipper> left = new ArrayList<>(), right = new ArrayList<>();
    private final Map<String, Segment> targets = new LinkedHashMap<>();
    private final Map<String, List<Segment>> banks = new LinkedHashMap<>();
    private final Map<String, Part> holes = new HashMap<>();
    private final Map<String, String> drains = new HashMap<>();
    private final Map<String, Part> ramps = new LinkedHashMap<>();
    private final Map<String, Integer> wireStarts = new HashMap<>();
    private final Map<String, double[]> spinners = new LinkedHashMap<>();
    private final List<double[]> pending = new ArrayList<>();
    private final List<Runnable> pendingActions = new ArrayList<>();
    private final Map<Ball, Double> grabbed = new HashMap<>();
    private final Map<Ball, double[]> before = new IdentityHashMap<>();
    private long steps;
    private Ball captive;
    private boolean kickbackLit;
    private double time;

    public Table(TableSpec spec) {
        this.spec = spec;
        for (Part p : spec.parts()) build(p);
    }

    public TableSpec spec() {
        return spec;
    }

    /** How many steps the table has made, so a view can tell a paused table from a running one. */
    public long steps() {
        return steps;
    }

    /** Where the ball was before the last step: x, y and height; null for a ball that came since. */
    public double[] before(Ball ball) {
        return before.get(ball);
    }

    public Playfield field() {
        return field;
    }

    public Plunger plunger() {
        return plunger;
    }

    public List<Flipper> leftFlippers() {
        return left;
    }

    public List<Flipper> rightFlippers() {
        return right;
    }

    public boolean isFlipper(String id) {
        for (Flipper f : field.flippers()) if (f.id.equals(id)) return true;
        return false;
    }

    public Ball captive() {
        return captive;
    }

    public Map<String, Part> ramps() {
        return ramps;
    }

    /** Where the wire form of a ramp begins: the index of its first point in the ramp's track. */
    public int wireStart(String ramp) {
        return wireStarts.getOrDefault(ramp, -1);
    }

    /** A spinner's plate: its angle (radians) and how fast it turns. */
    public double spinnerAngle(String id) {
        double[] s = spinners.get(id);
        return s == null ? 0 : s[0];
    }

    /** A ball passing the spinner sets its plate turning, the faster the faster the ball. */
    public void spinSpinner(String id, double ballSpeed) {
        double[] s = spinners.get(id);
        if (s != null) s[1] = Math.min(60, ballSpeed / 12);
    }

    // --8<-- [start:build]
    private void build(Part p) {
        int layer = p.layer();
        switch (p.kind()) {
            case "rim" -> {
                List<double[]> pts = Path.points(p.words(), Path.after(p.words(), "path"), null, null, 4);
                for (int i = 0; i + 1 < pts.size(); i++) {
                    field.add(new Segment(null, pts.get(i)[0], pts.get(i)[1], pts.get(i + 1)[0], pts.get(i + 1)[1], 4,
                            Material.WALL, 0));
                }
            }
            case "plungertip" -> field.add(new Segment("plunger", p.num(0), p.num(1), p.num(2), p.num(3), 2.2, Material.WALL, 0));
            case "wall" -> chain(p, 0, 2.2, Material.WALL, null);
            case "band" -> chain(p, 0, 5.2, Material.RUBBER, null);
            case "post" -> field.add(new Disc("post", p.num(0), p.num(1), 2.6, Material.RUBBER, layer));
            case "bumper" -> field.add(new Disc(p.id(), p.num(0), p.num(1), p.num(2) * 0.95, Material.BUMPER, layer));
            case "sling" -> {
                double[] n = p.nums();
                field.add(new Segment(p.id(), n[0], n[1], n[4], n[5], 3, Material.SLING, layer));
                field.add(new Segment(null, n[0], n[1], n[2], n[3], 3, Material.RUBBER, layer));
                field.add(new Segment(null, n[2], n[3], n[4], n[5], 3, Material.RUBBER, layer));
                for (int i = 0; i < 6; i += 2) field.add(new Disc(null, n[i], n[i + 1], 2.6, Material.RUBBER, layer));
            }
            case "flipper" -> {
                boolean isLeft = p.name(1, "left").equals("left");
                Flipper f = field.add(Flipper.of(p.id(), isLeft, p.num(0), p.num(1), p.num(2), Math.toRadians(p.num(3)),
                        Math.toRadians(p.option("swing", 52)), layer));
                (isLeft ? left : right).add(f);
            }
            case "drop", "standup" -> {
                double a = Math.toRadians(p.num(2)), dx = Math.cos(a) * 4, dy = Math.sin(a) * 4;
                Segment s = field.add(new Segment(p.id(), p.num(0) - dx, p.num(1) - dy, p.num(0) + dx, p.num(1) + dy,
                        2.4, Material.TARGET, layer));
                targets.put(p.id(), s);
                if (p.kind().equals("drop")) banks.computeIfAbsent(bank(p.id()), k -> new ArrayList<>()).add(s);
            }
            case "saucer" -> {
                field.add(new Zone(p.id(), Zone.Kind.HOLE, p.num(0), p.num(1), 4.5, layer, p.option("catch", 520)));
                holes.put(p.id(), p);
            }
            case "hole" -> {
                field.add(new Zone(p.id(), Zone.Kind.HOLE, p.num(0), p.num(1), p.num(2), layer, p.option("catch", 1e9)));
                holes.put(p.id(), p);
                if (p.options().containsKey("exit")) drains.put(p.id(), p.option("exit", ""));
            }
            case "spinner" -> {
                field.add(new Zone(p.id(), Zone.Kind.SPINNER, p.num(0), p.num(1), 7, layer, 0));
                spinners.put(p.id(), new double[2]);
            }
            case "rollover", "kickback", "spindisc", "switch" ->
                    field.add(new Zone(p.id(), Zone.Kind.SWITCH, p.num(0), p.num(1), p.option("r", 4.5), layer, 0));
            case "lanes" -> lanes(p);
            case "gate" -> field.add(new Segment(null, p.num(0), p.num(1), p.num(2), p.num(3), 1.2, Material.WALL, layer,
                    new double[]{p.num(4), p.num(5)}));
            case "ramp", "tube" -> ramp(p);
            case "track" -> {
                double[] xyh = new double[p.nums().length];
                System.arraycopy(p.nums(), 0, xyh, 0, xyh.length);
                field.add(new Track(p.id(), (int) p.option("exit", 0), xyh));
            }
            case "captive" -> {
                captive = new Ball(p.num(0), p.num(1), BALL_RADIUS, true);
                captive.fence = new double[]{p.num(2), p.num(3), p.num(4), p.num(5)};
                field.add(captive);
            }
            case "magnet" -> field.add(new Zone(p.id(), Zone.Kind.MAGNET, p.num(0), p.num(1), p.num(2), layer, p.option("pull", 60)));
            case "floor" -> {
                double x = p.num(1), y = p.num(2), w = p.num(3), h = p.num(4);
                field.layer((int) p.num(0), p.num(5));
                // the raised floor is a box on the playfield: balls below bounce off its sides. Its far
                // side is a shallow roof with cut corners, so a ball that comes round the top neither rests
                // on it nor sticks between it and the rim.
                double roof = Math.min(8, y - 17), cut = Math.min(8, w / 6);
                chain(new double[]{x, y + h, x, y + cut, x + cut, y, x + w / 2, y - roof, x + w - cut, y, x + w, y + cut,
                        x + w, y + h, x, y + h}, 2.2, Material.WALL, 0, null);
            }
            default -> {
                // art only: labels, inserts, plastics, toys
            }
        }
    }
    // --8<-- [end:build]

    /** The bank a drop target belongs to: its id without the trailing number ({@code gallery3} → {@code gallery}). */
    public static String bank(String id) {
        int i = id.length();
        while (i > 0 && Character.isDigit(id.charAt(i - 1))) i--;
        return id.substring(0, i);
    }

    private void chain(Part p, int from, double thickness, Material m, String id) {
        chain(p.nums(), thickness, m, p.layer(), id == null ? p.id() : id);
    }

    private void chain(double[] n, double thickness, Material m, int layer, String id) {
        for (int i = 0; i + 3 < n.length; i += 2) field.add(new Segment(id, n[i], n[i + 1], n[i + 2], n[i + 3], thickness, m, layer));
        if (m == Material.RUBBER) for (int i = 0; i + 1 < n.length; i += 2) field.add(new Disc(null, n[i], n[i + 1], 2.6, m, layer));
    }

    private void lanes(Part p) {
        double x0 = p.num(0), y = p.num(1), step = p.num(3);
        int n = (int) p.num(2);
        for (int i = 0; i <= n; i++) {
            double x = x0 + i * step;
            field.add(new Segment(null, x, y, x, y + 16, 2.2, Material.WALL, p.layer()));
            field.add(new Disc(null, x, y, 2.6, Material.RUBBER, p.layer()));
        }
        for (int i = 0; i < n; i++) {
            field.add(new Zone(p.id() + (i + 1), Zone.Kind.SWITCH, x0 + step / 2 + i * step, y + 10, 4, p.layer(), 0));
        }
    }

    // --8<-- [start:ramp]
    /**
     * A ramp: the plastic part climbs from its mouth to {@link #RAMP_HEIGHT}, then the wire form brings the
     * ball back down to the playfield (or onto a raised floor). For the ball it is one {@link Track}; its
     * mouth is an entry zone, flanked by two short walls so that a ball beside the mouth bounces off.
     */
    private void ramp(Part p) {
        List<String> w = p.words();
        double top = p.option("h", RAMP_HEIGHT);
        List<double[]> up = Path.points(w, Path.after(w, "path"), "wire", null, 3);
        double[] hu = Path.heights(up, p.layer() > 0 ? field.layerHeight(p.layer()) : 0, top);
        List<double[]> pts = new ArrayList<>();
        List<Double> hs = new ArrayList<>();
        for (int i = 0; i < up.size(); i++) {
            pts.add(up.get(i));
            hs.add(hu[i]);
        }
        int wire = Path.after(w, "wire");
        if (wire > 0) {
            List<double[]> down = Path.points(w, wire, null, up.get(up.size() - 1), 3);
            double end = p.option("end", 0.5);
            double[] hd = Path.heights(down, top, end);
            for (int i = 1; i < down.size(); i++) {
                pts.add(down.get(i));
                hs.add(hd[i]);
            }
        } else {
            hs.set(hs.size() - 1, top);
        }
        int exit = (int) p.option("exit", 0);
        if (exit > 0) {
            // ends on a raised floor: the last stretch stays at the floor's height
            double fh = field.layerHeight(exit);
            for (int i = 0; i < hs.size(); i++) hs.set(i, Math.max(hs.get(i), Math.min(fh, hs.get(i) * 4)));
            hs.set(hs.size() - 1, fh);
        }
        double[] xyh = new double[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            xyh[3 * i] = pts.get(i)[0];
            xyh[3 * i + 1] = pts.get(i)[1];
            xyh[3 * i + 2] = hs.get(i);
        }
        Track t = field.add(new Track(p.id(), exit, xyh));
        wireStarts.put(p.id(), up.size() - 1);
        double[] a = pts.get(0), b = pts.get(Math.min(3, pts.size() - 1));
        double dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy);
        double width = p.num(0);
        field.add(new Zone(p.id() + "-mouth", Zone.Kind.ENTRY, a[0], a[1], width / 2 + 1, p.layer(), p.option("min", 60), dx, dy, t.id));
        // the side walls of the ramp's first units: a ball beside the mouth bounces off, one in it is already riding
        double nx = -dy / l * (width / 2 + 1.2), ny = dx / l * (width / 2 + 1.2);
        for (int s : new int[]{1, -1}) {
            field.add(new Segment(null, a[0] + s * nx + dx / l * 3, a[1] + s * ny + dy / l * 3,
                    a[0] + s * nx + dx / l * 16, a[1] + s * ny + dy / l * 16, 2.2, Material.WALL, p.layer()));
        }
        ramps.put(p.id(), p);
    }
    // --8<-- [end:ramp]

    // ---- the mechanics the rules use -----------------------------------------------------------------

    /** A new ball on the plunger. */
    public Ball serve() {
        Ball b = field.add(new Ball(LANE_X, LANE_Y, BALL_RADIUS));
        return b;
    }

    /** Is a ball lying in the shooter lane, waiting for the plunger? */
    public boolean ballInLane() {
        for (Ball b : field.balls()) if (inLane(b)) return true;
        return false;
    }

    private static boolean inLane(Ball b) {
        return b.state == Ball.State.FREE && !b.captive && b.x > 216 && b.y > LANE_Y - 12 && Math.abs(b.vy) < 60;
    }

    /** The plunger: pulled while held, returns the launch speed when let go (0 otherwise). */
    public double pullPlunger(boolean held, double seconds) {
        double v = plunger.update(held, seconds);
        if (v > 0) launch(v);
        return v;
    }

    /** Shoots every ball lying on the plunger up the lane. */
    public void launch(double speed) {
        for (Ball b : field.balls()) {
            if (inLane(b)) {
                b.vx = 0;
                b.vy = -speed;
            }
        }
    }

    /** A new ball shot into play at once, for a multiball or a ball saved. */
    public void autoLaunch() {
        Ball b = serve();
        b.vy = -1250 - Math.random() * 100;
    }

    public void flip(boolean leftHeld, boolean rightHeld) {
        for (Flipper f : left) f.hold(leftHeld);
        for (Flipper f : right) f.hold(rightHeld);
    }

    /** Throws the ball out of a hole after {@code delay} seconds, the way the hole's {@code out} option points. */
    public void eject(Ball ball, double delay) {
        String hole = ball.holder();
        Part p = holes.get(hole);
        double deg = p == null ? 90 : p.option("out", 90);
        double speed = p == null ? 300 : p.option("kick", 380);
        later(delay, () -> {
            if (ball.state != Ball.State.HELD) return;
            double a = Math.toRadians(deg + (Math.random() - 0.5) * 6);
            field.eject(ball, Math.cos(a) * speed, Math.sin(a) * speed);
        });
    }

    /** A drained raised floor: the ball rides the hole's exit track back down. */
    public boolean drainFloor(Ball ball) {
        String track = drains.get(ball.holder());
        if (track == null) return false;
        ball.state = Ball.State.FREE;
        field.ride(ball, track, 120);
        return true;
    }

    public Segment target(String id) {
        return targets.get(id);
    }

    public List<Segment> bank(String bank, boolean unused) {
        return banks.getOrDefault(bank, List.of());
    }

    public Map<String, List<Segment>> banks() {
        return banks;
    }

    /** Drops a target: the ball passes over it now. */
    public void drop(String id) {
        Segment s = targets.get(id);
        if (s != null) s.down = true;
    }

    /** Is every target of the bank down? */
    public boolean bankDown(String bank) {
        List<Segment> b = banks.get(bank);
        if (b == null || b.isEmpty()) return false;
        for (Segment s : b) if (!s.down) return false;
        return true;
    }

    /** Raises a bank again, after a moment, unless a ball lies on it. */
    public void resetBank(String bank, double delay) {
        later(delay, () -> {
            for (Segment s : banks.getOrDefault(bank, List.of())) s.down = false;
        });
    }

    public void setKickback(boolean lit) {
        kickbackLit = lit;
    }

    public boolean kickbackLit() {
        return kickbackLit;
    }

    /** The kickback in the left outlane fires: the ball goes back up into play. */
    public void kick(Ball ball) {
        ball.vx = 0;
        ball.vy = -1100;
        kickbackLit = false;
    }

    /** A magnet grabs the ball and lets it go after {@code seconds}, flung off sideways. */
    public void grab(String magnet, Ball ball, double seconds) {
        Zone z = field.zone(magnet);
        if (z == null) return;
        z.active = true;
        grabbed.put(ball, time + seconds);
    }

    /** The spin disc throws a ball rolling over it off in another direction. */
    public void spin(Ball ball) {
        double a = (Math.random() - 0.5) * 2.2;
        double c = Math.cos(a), s = Math.sin(a);
        double vx = ball.vx * c - ball.vy * s, vy = ball.vx * s + ball.vy * c;
        ball.vx = vx * 1.15;
        ball.vy = vy * 1.15;
    }

    /** Runs {@code action} after {@code delay} seconds, on the loop thread during {@link #step}. */
    public void later(double delay, Runnable action) {
        pending.add(new double[]{time + delay, pendingActions.size()});
        pendingActions.add(action);
    }

    /** Moves the table on by {@code seconds}. */
    public void step(double seconds) {
        time += seconds;
        steps++;
        before.clear();
        for (Ball b : field.balls()) before.put(b, new double[]{b.x, b.y, b.height(field)});
        field.step(seconds * PACE);
        for (double[] s : spinners.values()) {
            s[0] += s[1] * seconds;
            s[1] *= Math.max(0, 1 - 1.2 * seconds);
            if (s[1] < 1.5) {
                // the plate comes to rest hanging down
                double rest = Math.round(s[0] / (2 * Math.PI)) * 2 * Math.PI;
                s[0] += (rest - s[0]) * Math.min(1, 6 * seconds);
                s[1] = 0;
            }
        }
        for (var it = grabbed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (time < e.getValue()) continue;
            it.remove();
            for (Zone z : field.zones()) {
                if (z.kind != Zone.Kind.MAGNET || !z.active) continue;
                z.active = false;
                Ball b = e.getKey();
                double a = Math.random() < 0.5 ? Math.PI * 0.15 : Math.PI * 0.85;
                b.vx = Math.cos(a) * 520;
                b.vy = Math.sin(a) * 520;
            }
        }
        if (!pending.isEmpty()) {
            List<double[]> due = new ArrayList<>();
            for (double[] d : pending) if (d[0] <= time) due.add(d);
            pending.removeAll(due);
            for (double[] d : due) pendingActions.get((int) d[1]).run();
            if (pending.isEmpty()) pendingActions.clear();
        }
    }

    public double time() {
        return time;
    }
}
