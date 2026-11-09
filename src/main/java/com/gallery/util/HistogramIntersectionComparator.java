package com.gallery.util;

/**
 * Histogram intersection: {@code sum(min(a[i], b[i]))}. Because both vectors
 * are normalised to sum to 1, the result is already in [0, 1].
 */
public class HistogramIntersectionComparator implements HistogramComparator {

    @Override
    public double similarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double score = 0.0;
        for (int i = 0; i < a.length; i++) {
            score += Math.min(a[i], b[i]);
        }
        return Math.max(0.0, Math.min(1.0, score));
    }
}
