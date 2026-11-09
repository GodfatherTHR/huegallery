package com.gallery.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Normalises comma-separated tag input: lower-cases, strips characters outside
 * {@code [a-z0-9 -]}, collapses whitespace, drops empties, de-duplicates and
 * enforces the tag limits.
 */
public final class TagParser {

    public static final int MAX_TAGS = 10;
    public static final int MAX_TAG_LENGTH = 40;
    public static final int MAX_QUERY_TAGS = 20;

    private TagParser() {
    }

    /**
     * Parses user input for an image. Throws {@link ValidationException} when
     * more than {@link #MAX_TAGS} distinct tags are supplied.
     */
    public static List<String> parse(String raw) {
        return normalise(raw, MAX_TAGS, true);
    }

    /**
     * Lenient variant for search queries: caps the number of tags instead of
     * rejecting the request.
     */
    public static List<String> parseQuery(String raw) {
        return normalise(raw, MAX_QUERY_TAGS, false);
    }

    private static List<String> normalise(String raw, int maxTags, boolean strict) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (String candidate : raw.split(",")) {
            String tag = candidate.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9 -]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (tag.isEmpty() || !tag.matches(".*[a-z0-9].*")) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                tag = tag.substring(0, MAX_TAG_LENGTH).trim();
            }
            if (tag.isEmpty()) {
                continue;
            }
            if (strict && tags.size() >= maxTags) {
                throw new ValidationException("You can add at most " + MAX_TAGS + " tags.");
            }
            if (tags.size() >= maxTags) {
                break;
            }
            tags.add(tag);
        }
        return new ArrayList<>(tags);
    }
}
