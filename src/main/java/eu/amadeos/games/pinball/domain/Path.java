package eu.amadeos.games.pinball.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The path of a ramp, a wire form or a tube as a table file writes it, like SVG: a start point, then
 * {@code C} with three points of a cubic curve or {@code L} with one point of a straight line, as often as
 * needed. A ramp's path may go on with {@code wire} and more curves: the wire form that carries the ball
 * back down from the ramp's end.
 */
public final class Path {
    private Path() {
    }

    /**
     * The points of the path that starts at word {@code from}, about every {@code step} units, up to the
     * word {@code until} (or the end, or an option). The first point is the start, unless {@code start}
     * gives one (a wire goes on from the ramp's end).
     */
    public static List<double[]> points(List<String> words, int from, String until, double[] start, double step) {
        List<double[]> out = new ArrayList<>();
        int i = from;
        double x, y;
        if (start != null) {
            x = start[0];
            y = start[1];
        } else {
            x = Double.parseDouble(words.get(i++));
            y = Double.parseDouble(words.get(i++));
        }
        out.add(new double[]{x, y});
        while (i < words.size()) {
            String w = words.get(i);
            if (w.equals(until) || w.contains("=")) break;
            if (w.equals("C")) {
                double x1 = num(words, i + 1), y1 = num(words, i + 2), x2 = num(words, i + 3), y2 = num(words, i + 4);
                double x3 = num(words, i + 5), y3 = num(words, i + 6);
                double len = Math.hypot(x1 - x, y1 - y) + Math.hypot(x2 - x1, y2 - y1) + Math.hypot(x3 - x2, y3 - y2);
                int n = Math.max(2, (int) Math.ceil(len / step));
                for (int k = 1; k <= n; k++) {
                    double t = k / (double) n, u = 1 - t;
                    out.add(new double[]{u * u * u * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                            u * u * u * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3});
                }
                x = x3;
                y = y3;
                i += 7;
            } else if (w.equals("L")) {
                double x1 = num(words, i + 1), y1 = num(words, i + 2);
                int n = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x, y1 - y) / step));
                for (int k = 1; k <= n; k++) out.add(new double[]{x + (x1 - x) * k / n, y + (y1 - y) * k / n});
                x = x1;
                y = y1;
                i += 3;
            } else {
                throw new IllegalArgumentException("unknown path word " + w);
            }
        }
        return out;
    }

    /** The index of the word after {@code word}, or -1. */
    public static int after(List<String> words, String word) {
        int i = words.indexOf(word);
        return i < 0 ? -1 : i + 1;
    }

    private static double num(List<String> words, int i) {
        return Double.parseDouble(words.get(i));
    }

    /** Heights along points: from {@code h0} to {@code h1}, eased at both ends. */
    public static double[] heights(List<double[]> pts, double h0, double h1) {
        double[] s = new double[pts.size()];
        for (int i = 1; i < s.length; i++) s[i] = s[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
        double total = Math.max(1e-9, s[s.length - 1]);
        double[] h = new double[s.length];
        for (int i = 0; i < s.length; i++) {
            double t = s[i] / total;
            h[i] = h0 + (h1 - h0) * t * t * (3 - 2 * t);
        }
        return h;
    }
}
