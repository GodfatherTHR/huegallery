package com.gallery.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gallery.util.ColourHistogram;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColourNamesTest {

    @Test
    void redMapsToTheRedBin() {
        double[] histogram = ColourNames.toHistogram(List.of("red"));
        assertNotNull(histogram);
        assertEquals(1.0, histogram[ColourHistogram.binIndex(212, 31, 31)], 1e-9);
        assertEquals(1.0, sum(histogram), 1e-9);
    }

    @Test
    void multipleColoursAreAveraged() {
        double[] histogram = ColourNames.toHistogram(List.of("red", "blue"));
        assertNotNull(histogram);
        assertEquals(0.5, histogram[ColourHistogram.binIndex(212, 31, 31)], 1e-9);
        assertEquals(0.25, histogram[ColourHistogram.binIndex(42, 91, 215)], 1e-9);
        assertEquals(0.25, histogram[ColourHistogram.binIndex(0, 0, 255)], 1e-9);
        assertEquals(1.0, sum(histogram), 1e-9);
    }

    @Test
    void hexValuesAreAccepted() {
        double[] histogram = ColourNames.toHistogram(List.of("#ff0000"));
        assertNotNull(histogram);
        assertEquals(1.0, histogram[ColourHistogram.binIndex(255, 0, 0)], 1e-9);
    }

    @Test
    void extractFindsColourAndSceneWords() {
        List<String> colours = ColourNames.extract("a warm red sunset over the ocean");
        assertTrue(colours.contains("warm"));
        assertTrue(colours.contains("red"));
        assertTrue(colours.contains("sunset"));
        assertTrue(colours.contains("ocean"));
    }

    @Test
    void unknownColoursReturnNull() {
        assertNull(ColourNames.toHistogram(List.of("not-a-colour")));
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }
}
