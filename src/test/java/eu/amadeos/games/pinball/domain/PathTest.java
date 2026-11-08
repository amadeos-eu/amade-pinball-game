package eu.amadeos.games.pinball.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTest {
    private static List<String> words(String s) {
        return List.of(s.split(" "));
    }

    @Test
    void aPathOfLinesAndCurvesBecomesEvenlySpacedPoints() {
        List<String> w = words("ramp x p6 12 path 0 0 L 0 100 C 0 150 50 150 50 100 wire L 50 200");
        int from = Path.after(w, "path");
        List<double[]> ramp = Path.points(w, from, "wire", null, 5);
        assertEquals(0, ramp.getFirst()[0], 1e-9);
        assertEquals(0, ramp.getFirst()[1], 1e-9);
        assertEquals(50, ramp.getLast()[0], 1e-9);
        assertEquals(100, ramp.getLast()[1], 1e-9);
        for (int i = 1; i < ramp.size(); i++) {
            double d = Math.hypot(ramp.get(i)[0] - ramp.get(i - 1)[0], ramp.get(i)[1] - ramp.get(i - 1)[1]);
            assertTrue(d > 0.5 && d < 8, "step " + d);
        }
        List<double[]> wire = Path.points(w, Path.after(w, "wire"), null, ramp.getLast(), 5);
        assertEquals(50, wire.getLast()[0], 1e-9);
        assertEquals(200, wire.getLast()[1], 1e-9);
    }

    @Test
    void heightsRiseSmoothlyFromStartToEnd() {
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{0, 10}, new double[]{0, 20}, new double[]{0, 30},
                new double[]{0, 40});
        double[] h = Path.heights(pts, 0, 30);
        assertEquals(0, h[0], 1e-9);
        assertEquals(30, h[4], 1e-9);
        assertEquals(15, h[2], 1e-9);
        for (int i = 1; i < h.length; i++) assertTrue(h[i] >= h[i - 1]);
        assertTrue(h[1] < 7.5, "eased at the start");
    }
}
