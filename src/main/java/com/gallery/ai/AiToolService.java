package com.gallery.ai;

import com.gallery.dao.ImageDao;
import com.gallery.model.Image;
import com.gallery.model.SimilarImage;
import com.gallery.model.Tag;
import com.gallery.service.SimilarityService;
import com.gallery.service.TagService;
import com.gallery.util.TagParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Executes the tools the local model may call. Every result is compact JSON so
 * it can be fed back into the chat loop, and every hit is also returned as a
 * Java record so the UI can render real image cards.
 */
public class AiToolService {

    public record ImageHit(long imageId, String reason, Integer scorePercent) {
    }

    public record ToolResult(String content, List<ImageHit> hits) {
    }

    private final ImageDao imageDao = new ImageDao();
    private final TagService tagService = new TagService();
    private final SimilarityService similarityService = new SimilarityService();
    private final Gson gson = new Gson();

    public ToolResult execute(String name, JsonObject arguments) throws SQLException {
        if (name == null || name.isBlank()) {
            return error("No tool name was supplied.");
        }
        return switch (name) {
            case "list_tags" -> listTags();
            case "search_by_tags" -> searchByTags(arguments);
            case "search_by_text" -> searchByText(arguments);
            case "search_by_colour" -> searchByColour(arguments);
            case "find_similar" -> findSimilar(arguments);
            default -> error("Unknown tool: " + name);
        };
    }

    public List<Tag> knownTags(int limit) throws SQLException {
        return tagService.cloud(limit);
    }

    public ToolResult error(String message) {
        JsonObject content = new JsonObject();
        content.add("images", new JsonArray());
        content.addProperty("count", 0);
        content.addProperty("error", message);
        return new ToolResult(gson.toJson(content), List.of());
    }

    private ToolResult listTags() throws SQLException {
        List<Tag> tags = tagService.cloud(40);
        JsonArray array = new JsonArray();
        for (Tag tag : tags) {
            JsonObject item = new JsonObject();
            item.addProperty("name", tag.getName());
            item.addProperty("count", tag.getUsageCount());
            array.add(item);
        }
        String note = tags.isEmpty()
                ? "The gallery has no tags yet."
                : "The most-used tags are listed with their image counts.";
        JsonObject content = new JsonObject();
        content.add("tags", array);
        content.addProperty("count", tags.size());
        content.addProperty("note", note);
        return new ToolResult(gson.toJson(content), List.of());
    }

    private ToolResult searchByTags(JsonObject arguments) throws SQLException {
        List<String> rawTags = stringList(arguments, "tags");
        List<String> tags = TagParser.parseQuery(String.join(",", rawTags));
        if (tags.isEmpty()) {
            return error("No usable tag names were supplied.");
        }
        boolean all = "all".equalsIgnoreCase(stringArg(arguments, "match", "any"));
        int limit = intArg(arguments, "limit", AiConfig.maxResults(), 1, 24);

        List<Image> images = imageDao.searchByTags(tags, all, 0, limit);
        String note = "Matched " + (all ? "all" : "any") + " of these tags: " + String.join(", ", tags);

        if (images.isEmpty() && all) {
            images = imageDao.searchByTags(tags, false, 0, limit);
            note = "No image had every tag; these images match any of: " + String.join(", ", tags);
        }
        if (images.isEmpty()) {
            List<String> tokens = new ArrayList<>();
            for (String tag : tags) {
                for (String token : tag.split(" ")) {
                    if (!token.isBlank() && !tokens.contains(token)) {
                        tokens.add(token);
                    }
                }
            }
            if (tokens.size() > tags.size()) {
                images = imageDao.searchByTags(tokens, false, 0, limit);
                note = "No exact tag match; these images match one of the words: "
                        + String.join(", ", tokens);
            }
        }
        if (images.isEmpty()) {
            note = "No images matched the tags: " + String.join(", ", tags) + ".";
        }
        return imagesResult(images, note);
    }

    private ToolResult searchByText(JsonObject arguments) throws SQLException {
        String query = stringArg(arguments, "query", "").trim();
        if (query.isEmpty()) {
            return error("No search text was supplied.");
        }
        int limit = intArg(arguments, "limit", AiConfig.maxResults(), 1, 24);
        List<Image> images = imageDao.searchByText(query, limit);
        String note = images.isEmpty()
                ? "No title or description contained \"" + query + "\"."
                : "Title or description contains \"" + query + "\".";
        return imagesResult(images, note);
    }

    private ToolResult searchByColour(JsonObject arguments) throws SQLException {
        List<String> colours = stringList(arguments, "colours");
        double[] target = ColourNames.toHistogram(colours);
        if (target == null) {
            return error("None of those colour names is recognised. Known colours include: "
                    + ColourNames.knownNames() + ".");
        }
        int limit = intArg(arguments, "limit", AiConfig.maxResults(), 1, 24);
        List<SimilarImage> candidates = similarityService.searchByHistogram(target,
                Math.min(48, Math.max(limit, limit * 4)));
        List<SimilarImage> matches = positiveMatches(candidates, limit);
        String note = matches.isEmpty()
                ? "No images had a colour distribution close to: " + String.join(", ", colours) + "."
                : "Ranked by colour distribution for: " + String.join(", ", colours) + ".";
        return similarResult(matches, note);
    }

    private ToolResult findSimilar(JsonObject arguments) throws SQLException {
        long imageId = longArg(arguments, "image_id", -1);
        if (imageId <= 0) {
            imageId = longArg(arguments, "id", -1);
        }
        if (imageId <= 0) {
            return error("No valid image_id was supplied.");
        }
        Optional<Image> target = imageDao.findById(imageId);
        if (target.isEmpty()) {
            return error("No gallery image has id " + imageId + ".");
        }
        int limit = intArg(arguments, "limit", AiConfig.maxResults(), 1, 24);
        List<SimilarImage> candidates = similarityService.findSimilar(imageId,
                Math.min(48, Math.max(limit, limit * 4)));
        List<SimilarImage> matches = positiveMatches(candidates, limit);
        String note = matches.isEmpty()
                ? "Image " + imageId + " has no visually similar matches right now."
                : "Visually similar to \"" + target.get().getTitle() + "\" (image " + imageId + ").";
        return similarResult(matches, note);
    }

    /** Drops 0% matches so the assistant does not show unrelated images. */
    private static List<SimilarImage> positiveMatches(List<SimilarImage> candidates, int limit) {
        List<SimilarImage> matches = new ArrayList<>();
        for (SimilarImage candidate : candidates) {
            if (candidate.getScore() >= 0.005) {
                matches.add(candidate);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }

    private ToolResult imagesResult(List<Image> images, String note) {
        JsonArray array = new JsonArray();
        List<ImageHit> hits = new ArrayList<>(images.size());
        for (Image image : images) {
            array.add(imageJson(image, null, note));
            hits.add(new ImageHit(image.getImageId(), note, null));
        }
        return result(array, images.size(), note, hits);
    }

    private ToolResult similarResult(List<SimilarImage> matches, String note) {
        JsonArray array = new JsonArray();
        List<ImageHit> hits = new ArrayList<>(matches.size());
        for (SimilarImage match : matches) {
            Image image = match.getImage();
            Integer score = match.getScorePercent();
            array.add(imageJson(image, score, note));
            hits.add(new ImageHit(image.getImageId(), note, score));
        }
        return result(array, matches.size(), note, hits);
    }

    private ToolResult result(JsonArray images, int count, String note, List<ImageHit> hits) {
        JsonObject content = new JsonObject();
        content.add("images", images);
        content.addProperty("count", count);
        content.addProperty("note", note);
        return new ToolResult(gson.toJson(content), hits);
    }

    private JsonObject imageJson(Image image, Integer score, String reason) {
        JsonObject item = new JsonObject();
        item.addProperty("id", image.getImageId());
        item.addProperty("title", image.getTitle());
        item.addProperty("username", image.getUsername());
        item.addProperty("width", image.getWidth());
        item.addProperty("height", image.getHeight());
        if (image.getDescription() != null && !image.getDescription().isBlank()) {
            String description = image.getDescription().replaceAll("\\s+", " ").trim();
            if (description.length() > 160) {
                description = description.substring(0, 157) + "...";
            }
            item.addProperty("description", description);
        }
        if (score != null) {
            item.addProperty("score", score);
        }
        if (reason != null && !reason.isBlank()) {
            item.addProperty("reason", reason);
        }
        return item;
    }

    private static List<String> stringList(JsonObject arguments, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (arguments == null) {
            return List.of();
        }
        JsonElement element = arguments.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    addValues(values, item.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            addValues(values, element.getAsString());
        }
        return new ArrayList<>(values);
    }

    private static void addValues(Set<String> values, String raw) {
        if (raw == null) {
            return;
        }
        for (String part : raw.split("[,;]")) {
            String value = part.trim();
            if (!value.isEmpty() && values.size() < 10) {
                values.add(value);
            }
        }
    }

    private static String stringArg(JsonObject arguments, String key, String fallback) {
        if (arguments == null) {
            return fallback;
        }
        JsonElement element = arguments.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int intArg(JsonObject arguments, String key, int fallback, int min, int max) {
        if (arguments == null) {
            return fallback;
        }
        JsonElement element = arguments.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            int value = element.getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long longArg(JsonObject arguments, String key, long fallback) {
        if (arguments == null) {
            return fallback;
        }
        JsonElement element = arguments.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
