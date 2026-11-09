package com.gallery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class HistogramComparatorTest {

    private final HistogramComparator comparator = new HistogramIntersectionComparator();

    @Test
    void identicalImagesScoreOne() {
        double[] red = ColourHistogram.compute(solid(64, 64, new Color(210, 30, 30).getRGB()));
        assertEquals(1.0, comparator.similarity(red, red), 1e-9);
    }

    @Test
    void solidRedVersusSolidBlueScoresZero() {
        double[] red = ColourHistogram.compute(solid(64, 64, new Color(255, 0, 0).getRGB()));
        double[] blue = ColourHistogram.compute(solid(64, 64, new Color(0, 0, 255).getRGB()));
        assertEquals(0.0, comparator.similarity(red, blue), 1e-9);
    }

    @Test
    void aShadeOfRedScoresHigherThanBlue() {
        double[] red = ColourHistogram.compute(solid(64, 64, new Color(255, 0, 0).getRGB()));
        double[] darkRed = ColourHistogram.compute(solid(64, 64, new Color(200, 0, 0).getRGB()));
        double[] blue = ColourHistogram.compute(solid(64, 64, new Color(0, 0, 255).getRGB()));

        double redVsDarkRed = comparator.similarity(red, darkRed);
        double redVsBlue = comparator.similarity(red, blue);
        assertTrue(redVsDarkRed > redVsBlue,
                "red vs dark red (" + redVsDarkRed + ") should beat red vs blue (" + redVsBlue + ")");
        assertEquals(1.0, redVsDarkRed, 1e-9);
    }

    @Test
    void intersectionIsSymmetricAndBounded() {
        double[] red = ColourHistogram.compute(solid(32, 32, new Color(255, 0, 0).getRGB()));
        double[] green = ColourHistogram.compute(solid(32, 32, new Color(0, 255, 0).getRGB()));
        assertEquals(comparator.similarity(red, green), comparator.similarity(green, red), 1e-12);
        assertTrue(comparator.similarity(red, green) >= 0.0);
        assertTrue(comparator.similarity(red, green) <= 1.0);
    }

    @Test
    void mismatchedVectorLengthsScoreZero() {
        assertEquals(0.0, comparator.similarity(new double[10], new double[64]), 1e-12);
    }

    private BufferedImage solid(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(rgb));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }
}
