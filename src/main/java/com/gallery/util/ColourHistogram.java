package com.gallery.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A 4x4x4 colour histogram computed with plain Java2D.
 *
 * <p>Pipeline: composite onto white, force {@code TYPE_INT_RGB}, downscale so
 * the longest side is at most 128 px, quantise each channel to four levels and
 * count pixels per bin, then normalise by the pixel count.</p>
 */
public final class ColourHistogram {

    public static final int LEVELS = 4;
    public static final int BINS = LEVELS * LEVELS * LEVELS;
    public static final int MAX_SAMPLE_SIDE = 128;

    private ColourHistogram() {
    }

    /** Computes a normalised 64-bin histogram for the supplied image. */
    public static double[] compute(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("Image is required");
        }
        BufferedImage rgb = ImageUtil.toOpaqueRgb(source);
        BufferedImage sample = scaleDown(rgb, MAX_SAMPLE_SIDE);
        int width = sample.getWidth();
        int height = sample.getHeight();
        int[] counts = new int[BINS];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbValue = sample.getRGB(x, y);
                int r = (rgbValue >> 16) & 0xFF;
                int g = (rgbValue >> 8) & 0xFF;
                int b = rgbValue & 0xFF;
                counts[binIndex(r, g, b)]++;
            }
        }
        long total = (long) width * height;
        double[] histogram = new double[BINS];
        for (int i = 0; i < BINS; i++) {
            histogram[i] = counts[i] / (double) total;
        }
        return histogram;
    }

    /** Bin index for the specified 8-bit channel values. */
    public static int binIndex(int r, int g, int b) {
        return (r * LEVELS / 256) * LEVELS * LEVELS
                + (g * LEVELS / 256) * LEVELS
                + (b * LEVELS / 256);
    }

    /** Serialises as 64 comma-separated values with six decimal places. */
    public static String serialise(double[] histogram) {
        if (histogram == null || histogram.length != BINS) {
            throw new IllegalArgumentException("Expected " + BINS + " bins");
        }
        StringBuilder sb = new StringBuilder(BINS * 9);
        for (int i = 0; i < BINS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", histogram[i]));
        }
        return sb.toString();
    }

    /** Parses a stored histogram, rejecting malformed data. */
    public static double[] parse(String bins) {
        if (bins == null) {
            throw new IllegalArgumentException("Histogram is required");
        }
        String[] parts = bins.split(",");
        if (parts.length != BINS) {
            throw new IllegalArgumentException("Expected " + BINS + " bins but found " + parts.length);
        }
        double[] histogram = new double[BINS];
        for (int i = 0; i < BINS; i++) {
            try {
                histogram[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed histogram bin at index " + i, e);
            }
        }
        return histogram;
    }

    /**
     * Returns the strongest bins as colours, derived from each bin's centre
     * value, ordered by decreasing mass.
     */
    public static List<Colour> topColours(double[] histogram, int limit) {
        if (histogram == null || histogram.length != BINS) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>(BINS);
        for (int i = 0; i < BINS; i++) {
            indexes.add(i);
        }
        indexes.sort(Comparator.comparingDouble((Integer i) -> histogram[i]).reversed());
        List<Colour> colours = new ArrayList<>();
        for (Integer index : indexes) {
            if (colours.size() >= limit || histogram[index] <= 0.0) {
                break;
            }
            int rBin = index / (LEVELS * LEVELS);
            int gBin = (index / LEVELS) % LEVELS;
            int bBin = index % LEVELS;
            int r = Math.min(255, (int) Math.round((rBin + 0.5) * 256.0 / LEVELS));
            int g = Math.min(255, (int) Math.round((gBin + 0.5) * 256.0 / LEVELS));
            int b = Math.min(255, (int) Math.round((bBin + 0.5) * 256.0 / LEVELS));
            colours.add(new Colour(r, g, b, histogram[index]));
        }
        return colours;
    }

    private static BufferedImage scaleDown(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSide && height <= maxSide) {
            return source;
        }
        double scale = Math.min(maxSide / (double) width, maxSide / (double) height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    /** A representative colour for one histogram bin. */
    public static class Colour {

        private final int red;
        private final int green;
        private final int blue;
        private final double weight;

        public Colour(int red, int green, int blue, double weight) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.weight = weight;
        }

        public int getRed() {
            return red;
        }

        public int getGreen() {
            return green;
        }

        public int getBlue() {
            return blue;
        }

        public double getWeight() {
            return weight;
        }

        public int getPercent() {
            return (int) Math.round(weight * 100.0);
        }

        public String getHex() {
            return String.format(Locale.ROOT, "%02x%02x%02x", red, green, blue);
        }
    }
}
