package eu.amadeos.games.pinball.domain;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a table file: one part per line, its kind first, then numbers, names and {@code key=value}
 * options; {@code #} starts a comment. Underscores in names stand for spaces ({@code Lantern_Fair}).
 * See {@code tables/README.md} for the kinds of parts.
 */
// --8<-- [start:parse]
public final class TableFile {
    /** The three tables, from easy to hard. */
    public static final List<String> TABLES = List.of("lantern-fair", "coral-deep", "orbit-yard");

    private TableFile() {
    }

    /** A table by its id, after the parts every table shares ({@code common.table}). */
    public static TableSpec load(String id) {
        return parse(id, read("common") + "\n" + read(id));
    }

    private static String read(String name) {
        String path = "/eu/amadeos/games/pinball/tables/" + name + ".table";
        try (InputStream in = TableFile.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("no table " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static TableSpec parse(String id, String text) {
        String name = id;
        int level = 1;
        Color[] palette = new Color[8];
        String art = id;
        List<Part> parts = new ArrayList<>();
        List<TableSpec.ModeSpec> modes = new ArrayList<>();
        TableSpec.ModeSpec wizard = null;
        Map<String, String> rules = new LinkedHashMap<>();
        String[] lines = text.split("\n");
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.strip();
            if (line.isEmpty()) continue;
            Part p = part(line, n + 1);
            switch (p.kind()) {
                case "name" -> name = text(p.id());
                case "level" -> level = (int) p.num(0);
                case "art" -> art = p.id();
                case "palette" -> {
                    for (int i = 0; i < 8; i++) palette[i] = new Color(Integer.parseInt(p.words().get(i), 16));
                }
                case "rules" -> rules.putAll(p.options());
                case "mode", "wizard" -> {
                    TableSpec.ModeSpec m = new TableSpec.ModeSpec(text(p.id()),
                            List.of(p.option("shots", "").split(",")), (int) p.option("count", 3),
                            p.option("time", 30), (long) p.option("value", 25000), p.option("lamp", "mode" + modes.size()), (int) p.option("balls", 1));
                    if (p.kind().equals("wizard")) wizard = m;
                    else modes.add(m);
                }
                default -> parts.add(p);
            }
        }
        return new TableSpec(id, name, level, palette, art, List.copyOf(parts), List.copyOf(modes), wizard, rules);
    }

    static Part part(String line, int number) {
        String[] tokens = line.split("\\s+");
        String kind = tokens[0];
        int layer = 0;
        int at = kind.indexOf('@');
        if (at > 0) {
            layer = Integer.parseInt(kind.substring(at + 1));
            kind = kind.substring(0, at);
        }
        List<String> words = new ArrayList<>();
        List<Double> nums = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            words.add(t);
            int eq = t.indexOf('=');
            if (eq > 0) {
                options.put(t.substring(0, eq), t.substring(eq + 1));
                continue;
            }
            try {
                nums.add(Double.parseDouble(t));
            } catch (NumberFormatException e) {
                names.add(t);
            }
        }
        double[] ns = nums.stream().mapToDouble(Double::doubleValue).toArray();
        return new Part(kind, layer, List.copyOf(words), ns, List.copyOf(names), options, number);
    }
    // --8<-- [end:parse]

    /** A name as written in the file, with spaces for underscores. */
    public static String text(String word) {
        return word == null ? "" : word.replace('_', ' ');
    }
}
