package com.gallery.util;

/**
 * Pluggable similarity metric. Implementations receive two already-normalised
 * histograms and return a score in [0, 1] where 1 means identical.
 */
public interface HistogramComparator {

    double similarity(double[] a, double[] b);
}
