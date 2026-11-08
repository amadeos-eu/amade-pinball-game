package eu.amadeos.games.pinball.domain;

import java.util.List;
import java.util.Map;

/**
 * One line of a table file: a part of the table, such as {@code bumper pop1 146 82 10 p3 xfff4e0}. The
 * first word is its {@code kind}; the rest are kept as they were written ({@code words}) and split into the
 * numbers ({@code nums}), the other words ({@code names}) and the {@code key=value} options. A part on a
 * raised floor ends its kind in {@code @n}, e.g. {@code wall@1}.
 */
public record Part(String kind, int layer, List<String> words, double[] nums, List<String> names,
                   Map<String, String> options, int line) {

    /** The first plain word: the id of the part, if it has one. */
    public String id() {
        return names.isEmpty() ? null : names.get(0);
    }

    public double num(int i) {
        if (i >= nums.length) throw new IllegalArgumentException("line " + line + ": " + kind + " needs more numbers");
        return nums[i];
    }

    public String option(String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }

    public double option(String key, double fallback) {
        String v = options.get(key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    /** The {@code i}-th plain word, or {@code fallback}. */
    public String name(int i, String fallback) {
        return i < names.size() ? names.get(i) : fallback;
    }
}
