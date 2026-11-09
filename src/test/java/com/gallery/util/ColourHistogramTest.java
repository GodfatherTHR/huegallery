package com.gallery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ColourHistogramTest {

    @Test
    void solidRedPutsAllMassInOneBin() {
        double[] histogram = ColourHistogram.compute(solid(80, 60, 0xFF0000));
        int expected = ColourHistogram.binIndex(255, 0, 0);
        assertEquals(1.0, histogram[expected], 1e-9);
        for (int i = 0; i < histogram.length; i++) {
            if (i != expected) {
                assertEquals(0.0, histogram[i], 1e-12, "bin " + i + " should be empty");
            }
        }
    }

    @Test
    void binsAlwaysSumToOne() {
        double[] histogram = ColourHistogram.compute(gradient(500, 350));
        double sum = 0.0;
        for (double value : histogram) {
            sum += value;
        }
        assertEquals(1.0, sum, 1e-9);
        assertEquals(ColourHistogram.BINS, histogram.length);
    }

    @Test
    void sameImageAtTwoSizesGivesNearlyIdenticalHistograms() {
        double[] large = ColourHistogram.compute(gradient(800, 600));
        double[] small = ColourHistogram.compute(gradient(200, 150));
        double score = new HistogramIntersectionComparator().similarity(large, small);
        assertTrue(score > 0.95, "expected a close match but scored " + score);
    }

    @Test
    void serialisationRoundTripsAndStaysWithinTheColumnLimit() {
        double[] histogram = ColourHistogram.compute(gradient(320, 240));
        String serialised = ColourHistogram.serialise(histogram);
        assertTrue(serialised.length() <= 1000, "bins column is VARCHAR2(1000)");
        double[] parsed = ColourHistogram.parse(serialised);
        for (int i = 0; i < histogram.length; i++) {
            assertEquals(histogram[i], parsed[i], 1e-5);
        }
    }

    @Test
    void dominantColoursComeFromTheLargestBins() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(220, 20, 20));
        g.fillRect(0, 0, 100, 70);
        g.setColor(new Color(20, 20, 220));
        g.fillRect(0, 70, 100, 30);
        g.dispose();

        var colours = ColourHistogram.topColours(ColourHistogram.compute(image), 3);
        assertTrue(colours.size() >= 2);
        assertTrue(colours.get(0).getRed() > colours.get(0).getBlue());
        assertTrue(colours.get(0).getPercent() >= 60);
    }

    @Test
    void binIndexMatchesTheDocumentedFormula() {
        assertEquals(0, ColourHistogram.binIndex(0, 0, 0));
        assertEquals(63, ColourHistogram.binIndex(255, 255, 255));
        assertEquals(3 * 16, ColourHistogram.binIndex(255, 0, 0));
        assertEquals(3 * 4, ColourHistogram.binIndex(0, 255, 0));
        assertEquals(3, ColourHistogram.binIndex(0, 0, 255));
    }

    private BufferedImage solid(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(rgb));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = width == 1 ? 0 : x * 255 / (width - 1);
                int g = height == 1 ? 0 : y * 255 / (height - 1);
                int b = (r + g) / 2;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }
}
