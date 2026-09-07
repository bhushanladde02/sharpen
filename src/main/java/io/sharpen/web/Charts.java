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


    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }
}
