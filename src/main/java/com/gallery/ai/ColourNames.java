package com.gallery.ai;

import com.gallery.util.ColourHistogram;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps everyday colour words to a target colour histogram. This is how a
 * natural-language request such as "warm red sunset" becomes a vector that can
 * be compared with the stored 64-bin histograms.
 *
 * <p>Each word contributes its representative colour plus one or more pure
 * base colours. That softens the 4x4x4 quantisation enough for a pure blue
 * image to match the word "blue" rather than scoring zero.</p>
 */
public final class ColourNames {

    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6})");
    private static final Map<String, int[]> COLOURS = new LinkedHashMap<>();
    private static final Map<String, int[]> PURE = new LinkedHashMap<>();
    private static final Map<String, String[]> BASE = new LinkedHashMap<>();

    static {
        // Primary and common colours.
        add("red", 212, 31, 31);
        add("crimson", 180, 20, 60);
        add("scarlet", 208, 42, 16);
        add("orange", 226, 118, 31);
        add("amber", 232, 165, 43);
        add("yellow", 230, 207, 53);
        add("gold", 201, 162, 39);
        add("green", 47, 143, 78);
        add("forest", 38, 90, 59);
        add("olive", 123, 127, 43);
        add("lime", 124, 191, 59);
        add("teal", 31, 127, 127);
        add("cyan", 47, 185, 201);
        add("blue", 42, 91, 215);
        add("navy", 28, 44, 92);
        add("sky", 123, 182, 230);
        add("purple", 122, 75, 184);
        add("violet", 154, 95, 208);
        add("magenta", 196, 42, 141);
        add("pink", 229, 138, 168);
        add("brown", 111, 74, 50);
        add("beige", 217, 201, 168);
        add("cream", 242, 231, 207);
        add("white", 245, 245, 245);
        add("grey", 128, 128, 128);
        add("gray", 128, 128, 128);
        add("black", 20, 20, 20);
        add("silver", 191, 196, 200);
        add("sepia", 138, 106, 69);

        // Mood and scene words that often stand in for a palette.
        add("warm", 230, 138, 60);
        add("cool", 90, 143, 196);
        add("pastel", 216, 198, 224);
        add("dark", 48, 48, 52);
        add("light", 239, 231, 216);
        add("sunset", 217, 90, 51);
        add("sunrise", 231, 151, 71);
        add("ocean", 30, 110, 160);
        add("sea", 35, 120, 165);
        add("fire", 220, 70, 30);
        add("snow", 240, 240, 245);
        add("sand", 220, 200, 150);
        add("night", 20, 30, 60);
        add("earth", 120, 90, 60);
        add("monochrome", 128, 128, 128);

        // Pure representatives used to bridge the coarse 4x4x4 bins.
        pure("red", 255, 0, 0);
        pure("orange", 255, 128, 0);
        pure("amber", 255, 191, 0);
        pure("yellow", 255, 255, 0);
        pure("gold", 255, 215, 0);
        pure("green", 0, 255, 0);
        pure("forest", 34, 139, 34);
        pure("olive", 128, 128, 0);
        pure("lime", 0, 255, 0);
        pure("teal", 0, 128, 128);
        pure("cyan", 0, 255, 255);
        pure("blue", 0, 0, 255);
        pure("navy", 0, 0, 128);
        pure("sky", 135, 206, 235);
        pure("purple", 128, 0, 255);
        pure("violet", 138, 43, 226);
        pure("magenta", 255, 0, 255);
        pure("pink", 255, 128, 192);
        pure("brown", 128, 64, 0);
        pure("beige", 222, 205, 160);
        pure("white", 255, 255, 255);
        pure("grey", 128, 128, 128);
        pure("black", 0, 0, 0);

        base("red", "red");
        base("crimson", "red");
        base("scarlet", "red");
        base("orange", "orange");
        base("amber", "amber");
        base("yellow", "yellow");
        base("gold", "gold");
        base("green", "green");
        base("forest", "forest", "green");
        base("olive", "olive");
        base("lime", "lime");
        base("teal", "teal");
        base("cyan", "cyan");
        base("blue", "blue");
        base("navy", "navy", "blue");
        base("sky", "sky", "blue");
        base("purple", "purple");
        base("violet", "violet");
        base("magenta", "magenta");
        base("pink", "pink");
        base("brown", "brown");
        base("beige", "beige");
        base("cream", "white");
        base("white", "white");
        base("grey", "grey");
        base("gray", "grey");
        base("black", "black");
        base("silver", "grey");
        base("sepia", "brown");
        base("warm", "orange");
        base("cool", "blue");
        base("pastel", "white");
        base("dark", "black");
        base("light", "white");
        base("sunset", "red", "orange");
        base("sunrise", "orange", "yellow");
        base("ocean", "blue");
        base("sea", "blue");
        base("fire", "red", "orange");
        base("snow", "white");
        base("sand", "beige");
        base("night", "black", "navy");
        base("earth", "brown");
        base("monochrome", "grey");
    }

    private ColourNames() {
    }

    private static void add(String name, int red, int green, int blue) {
        COLOURS.put(name, new int[] { red, green, blue });
    }

    private static void pure(String name, int red, int green, int blue) {
        PURE.put(name, new int[] { red, green, blue });
    }

    private static void base(String name, String... baseColours) {
        BASE.put(name, baseColours);
    }

    /**
     * Builds a normalised target histogram from colour words. Returns
     * {@code null} when none of the supplied words is recognised.
     */
    public static double[] toHistogram(List<String> colours) {
        if (colours == null || colours.isEmpty()) {
            return null;
        }
        List<double[]> vectors = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : colours) {
            for (int[] rgb : resolveAll(raw)) {
                String key = rgb[0] + "," + rgb[1] + "," + rgb[2];
                if (!seen.add(key)) {
                    continue;
                }
                double[] vector = new double[ColourHistogram.BINS];
                vector[ColourHistogram.binIndex(rgb[0], rgb[1], rgb[2])] = 1.0;
                vectors.add(vector);
                if (vectors.size() >= 8) {
                    break;
                }
            }
            if (vectors.size() >= 8) {
                break;
            }
        }
        if (vectors.isEmpty()) {
            return null;
        }
        double[] target = new double[ColourHistogram.BINS];
        for (double[] vector : vectors) {
            for (int i = 0; i < target.length; i++) {
                target[i] += vector[i];
            }
        }
        for (int i = 0; i < target.length; i++) {
            target[i] /= vectors.size();
        }
        return target;
    }

    /** Finds colour words in free text, including #rrggbb values. */
    public static List<String> extract(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher hex = HEX.matcher(lower);
        while (hex.find()) {
            found.add("#" + hex.group(1));
        }
        for (String name : COLOURS.keySet()) {
            if (containsWord(lower, name)) {
                found.add(name);
            }
        }
        return new ArrayList<>(found);
    }

    /** The names the assistant may use, for error messages and prompts. */
    public static String knownNames() {
        return String.join(", ", COLOURS.keySet());
    }

    private static boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            int before = index - 1;
            int after = index + word.length();
            boolean leftOk = before < 0 || !Character.isLetter(text.charAt(before));
            boolean rightOk = after >= text.length() || !Character.isLetter(text.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    private static List<int[]> resolveAll(String raw) {
        if (raw == null) {
            return List.of();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return List.of();
        }
        if (value.startsWith("#") && value.length() == 7) {
            int[] hex = parseHex(value);
            return hex == null ? List.of() : List.of(hex);
        }
        LinkedHashMap<String, int[]> resolved = new LinkedHashMap<>();
        addColour(resolved, value);
        if (resolved.isEmpty()) {
            for (String token : value.split("[^a-z]+")) {
                addColour(resolved, token);
            }
        }
        return new ArrayList<>(resolved.values());
    }

    private static void addColour(Map<String, int[]> target, String name) {
        int[] rgb = COLOURS.get(name);
        if (rgb == null) {
            return;
        }
        target.putIfAbsent(key(rgb), rgb);
        String[] baseColours = BASE.get(name);
        if (baseColours != null) {
            for (String baseColour : baseColours) {
                int[] pureRgb = PURE.get(baseColour);
                if (pureRgb != null) {
                    target.putIfAbsent(key(pureRgb), pureRgb);
                }
            }
        }
    }

    private static String key(int[] rgb) {
        return rgb[0] + "," + rgb[1] + "," + rgb[2];
    }

    private static int[] parseHex(String value) {
        try {
            return new int[] {
                    Integer.parseInt(value.substring(1, 3), 16),
                    Integer.parseInt(value.substring(3, 5), 16),
                    Integer.parseInt(value.substring(5, 7), 16)
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
