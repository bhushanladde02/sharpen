package io.sharpen.web;

import java.util.List;
import java.util.Locale;

/** Tiny server-side SVG helpers so pages need no charting library. */
public final class Charts {

    /** Horizontal inset so the first and last month labels are not clipped. */
    public static final int INSET_X = 24;

    private Charts() {}

    /**
     * SVG path for a line through evenly spaced points. Values below zero are gaps (month without a score).
     * Coordinates are in a {@code width x height} box, inset {@link #INSET_X} horizontally and 4px vertically.
     */
    public static String linePath(List<Integer> values, int max, int width, int height) {
        if (values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = values.size();
        double stepX = n == 1 ? 0 : (width - 2.0 * INSET_X) / (n - 1);
        boolean pen = false;
        for (int i = 0; i < n; i++) {
            int v = values.get(i);
            if (v < 0) { pen = false; continue; }
            double x = INSET_X + i * stepX;
            double y = 4 + (height - 8) * (1 - Math.min(max, v) / (double) max);
            sb.append(pen ? " L" : " M").append(fmt(x)).append(' ').append(fmt(y));
            pen = true;
        }
        return sb.toString().trim();
    }

    /**
     * SVG paths for pie slices, clockwise from twelve o'clock, in a circle of radius {@code r} centred on
     * ({@code r}, {@code r}). One path per value, in order; a zero value gets an empty path. A single non-zero value
     * is a whole circle (drawn as two half-arcs, since one arc cannot start and end at the same point).
     */
    public static List<String> pieSlices(List<Long> values, double r) {
        long total = values.stream().mapToLong(Long::longValue).sum();
        List<String> out = new java.util.ArrayList<>();
        if (total <= 0) { values.forEach(v -> out.add("")); return out; }
        double angle = 0;
        for (long v : values) {
            if (v <= 0) { out.add(""); continue; }
            if (v == total) {
                out.add("M" + fmt(r) + " 0 A" + fmt(r) + " " + fmt(r) + " 0 1 1 " + fmt(r) + " " + fmt(2 * r)
                        + " A" + fmt(r) + " " + fmt(r) + " 0 1 1 " + fmt(r) + " 0 Z");
                continue;
            }
            double sweep = 2 * Math.PI * v / total;
            double x0 = r + r * Math.sin(angle), y0 = r - r * Math.cos(angle);
            double x1 = r + r * Math.sin(angle + sweep), y1 = r - r * Math.cos(angle + sweep);
            out.add("M" + fmt(r) + " " + fmt(r) + " L" + fmt(x0) + " " + fmt(y0) + " A" + fmt(r) + " " + fmt(r) + " 0 "
                    + (sweep > Math.PI ? 1 : 0) + " 1 " + fmt(x1) + " " + fmt(y1) + " Z");
            angle += sweep;
        }
        return out;
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }
}
