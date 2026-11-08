package eu.amadeos.games.pinball.domain;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * A table as its file describes it: its name and difficulty, its colours, every part on it, its eight
 * modes and the rules that tie the parts together (which hole starts a mode, which locks a ball, which
 * shots light the jackpot). {@link Table} builds the physics from it, the game's art draws it.
 *
 * @param palette field top, field bottom, walls, rubber, flippers, three accents
 */
public record TableSpec(String id, String name, int level, Color[] palette, String art, List<Part> parts,
                        List<ModeSpec> modes, ModeSpec wizard, Map<String, String> rules) {

    /**
     * A mode: hit any of its {@code shots} {@code count} times within {@code seconds}, {@code value} points each;
     * a shot ending in {@code *} stands for every part whose id starts so. A wizard mode plays with {@code balls}.
     */
    public record ModeSpec(String name, List<String> shots, int count, double seconds, long value, String lamp, int balls) {
        public boolean counts(String event) {
            for (String s : shots) if (event.equals(s) || s.endsWith("*") && event.startsWith(s.substring(0, s.length() - 1))) return true;
            return false;
        }
    }

    /** A colour of the table: {@code p0} … {@code p7} from the palette, or {@code xRRGGBB}. */
    public Color color(String word) {
        if (word == null) return Color.WHITE;
        if (word.startsWith("p") && word.length() == 2) return palette[word.charAt(1) - '0'];
        if (word.startsWith("x")) return new Color(Integer.parseInt(word.substring(1), 16));
        return new Color(Integer.parseInt(word, 16));
    }

    public String rule(String key, String fallback) {
        return rules.getOrDefault(key, fallback);
    }

    /** A rule that names parts, comma separated; empty when not set. */
    public List<String> ruleList(String key) {
        String v = rules.get(key);
        return v == null || v.isBlank() ? List.of() : List.of(v.split(","));
    }

    public double rule(String key, double fallback) {
        String v = rules.get(key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    public List<Part> parts(String kind) {
        return parts.stream().filter(p -> p.kind().equals(kind)).toList();
    }
}
