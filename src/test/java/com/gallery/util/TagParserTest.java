package com.gallery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TagParserTest {

    @Test
    void lowercasesCollapsesWhitespaceAndTrims() {
        assertEquals(List.of("sunset", "golden hour"),
                TagParser.parse(" Sunset,  GOLDEN   hour "));
    }

    @Test
    void stripsCharactersOutsideTheAllowedSet() {
        assertEquals(List.of("caf", "fun"), TagParser.parse("café, #fun!!"));
    }

    @Test
    void dropsEmptyAndPunctuationOnlyTokens() {
        assertTrue(TagParser.parse(" , ,, !!! ").isEmpty());
        assertTrue(TagParser.parse(null).isEmpty());
        assertTrue(TagParser.parse("").isEmpty());
    }

    @Test
    void deduplicatesAfterNormalisation() {
        assertEquals(List.of("red", "blue"), TagParser.parse("red, RED, red!, blue, BLUE"));
    }

    @Test
    void acceptsExactlyTenTags() {
        String input = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.joining(", "));
        assertEquals(10, TagParser.parse(input).size());
    }

    @Test
    void rejectsMoreThanTenTags() {
        String input = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "tag" + i)
                .collect(Collectors.joining(", "));
        ValidationException error = assertThrows(ValidationException.class, () -> TagParser.parse(input));
        assertTrue(error.getMessage().contains("10"));
    }

    @Test
    void truncatesTagsToFortyCharacters() {
        List<String> parsed = TagParser.parse("a".repeat(45));
        assertEquals(1, parsed.size());
        assertEquals(40, parsed.get(0).length());
    }

    @Test
    void searchQueriesAreCappedRatherThanRejected() {
        String input = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "q" + i)
                .collect(Collectors.joining(", "));
        assertEquals(TagParser.MAX_QUERY_TAGS, TagParser.parseQuery(input).size());
    }
}
