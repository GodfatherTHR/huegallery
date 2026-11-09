package com.gallery.ai;

import com.gallery.ai.AiToolService.ImageHit;
import com.gallery.ai.AiToolService.ToolResult;
import com.gallery.ai.OllamaClient.ChatReply;
import com.gallery.ai.OllamaClient.ModelInfo;
import com.gallery.ai.OllamaClient.ToolCall;
import com.gallery.dao.ImageDao;
import com.gallery.model.Image;
import com.gallery.model.Tag;
import com.gallery.util.ValidationException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Natural-language image search. When the configured tool-capable model is
 * available (qwen3.5:0.8b by default) the model calls the gallery tools in a
 * loop. When only a completion model is available (qwen2:1.5b by default) the
 * model extracts a structured query and the same tools run locally. If Ollama
 * is offline, a deterministic keyword/colour search still returns results.
 */
public class AiSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(AiSearchService.class);
    private static final long MODEL_CACHE_MILLIS = 120_000L;
    private static final Map<String, CachedModel> MODEL_CACHE = new ConcurrentHashMap<>();

    private static final String TOOL_SYSTEM_PROMPT = """
            You are HueGallery's image search assistant. You can call tools that search a real Oracle image gallery.
            Rules:
            - Always call at least one tool for a gallery request.
            - Use search_by_tags for scenes, objects, places and themes.
            - Also use search_by_colour when the request mentions colours or moods (warm, cool, sunset, ocean).
            - Use search_by_text for words likely to appear in a title or description.
            - Use find_similar only when the user gives an existing image id.
            - Never invent image ids, titles or results. Only describe what the tools return.
            - After the tool results, answer in 1-3 short sentences and suggest a broader search if nothing matched.
            """;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "for", "from", "find", "give",
            "i", "in", "is", "it", "like", "me", "more", "my", "of", "on", "or", "please", "show",
            "some", "that", "the", "this", "to", "with", "photos", "photo", "pictures", "picture",
            "images", "image", "gallery", "something", "similar", "look", "looks");

    private final OllamaClient ollama = new OllamaClient();
    private final AiToolService toolService = new AiToolService();
    private final ImageDao imageDao = new ImageDao();

    public AiSearchResult search(String rawPrompt) throws SQLException {
        String prompt = normalisePrompt(rawPrompt);
        long started = System.currentTimeMillis();
        LinkedHashMap<Long, ImageHit> hits = new LinkedHashMap<>();
        LinkedHashSet<String> toolsUsed = new LinkedHashSet<>();
        String answer = "";
        String modelUsed = AiConfig.model();
        String mode = "local";
        String warning = null;
        boolean toolCalled = false;

        try {
            ModelChoice choice = resolveModel();
            modelUsed = choice.model();
            if (choice.tools()) {
                mode = "tools";
                ToolRun run = runToolLoop(choice.model(), prompt, hits, toolsUsed);
                answer = run.answer();
                toolCalled = run.anyToolCalled();
                if (run.warning() != null) {
                    warning = run.warning();
                }
            } else {
                mode = "prompt";
                answer = runPromptMode(choice.model(), prompt, hits, toolsUsed);
            }
        } catch (OllamaException e) {
            LOG.warn("Ollama unavailable, using local search: {}", e.getMessage());
            warning = e.getMessage();
            mode = "local";
            modelUsed = "local fallback";
        }

        if (hits.isEmpty() && (!toolCalled || !"tools".equals(mode) || warning != null)) {
            int before = hits.size();
            localSearch(prompt, hits);
            if (hits.size() > before && "tools".equals(mode)) {
                answer = "";
            }
        }

        List<AiImageCard> cards = loadCards(hits);
        if (warning != null) {
            String suffix = answer == null || answer.isBlank()
                    ? summarise(cards, mode, toolsUsed) : answer;
            answer = warning + " " + suffix;
        } else if (answer == null || answer.isBlank()) {
            answer = summarise(cards, mode, toolsUsed);
        }

        long elapsed = System.currentTimeMillis() - started;
        return new AiSearchResult(answer.trim(), cards, modelUsed, mode,
                new ArrayList<>(toolsUsed), elapsed, warning);
    }

    private ToolRun runToolLoop(String model, String prompt, Map<Long, ImageHit> hits,
            Set<String> toolsUsed) throws SQLException {
        JsonArray messages = new JsonArray();
        messages.add(message("system", TOOL_SYSTEM_PROMPT + "\nKnown gallery tags: " + knownTagsForPrompt()));
        messages.add(message("user", prompt));

        String answer = "";
        boolean anyToolCalled = false;
        String warning = null;
        int rounds = AiConfig.maxToolRounds();
        for (int round = 0; round < rounds; round++) {
            ChatReply reply;
            try {
                reply = ollama.chat(model, messages, AiToolDefinitions.all());
            } catch (OllamaException e) {
                if (!anyToolCalled) {
                    throw e;
                }
                LOG.warn("The local model stopped after returning tool results", e);
                warning = "The local model stopped before writing its summary, so here are the results found so far.";
                break;
            }
            if (!reply.hasToolCalls()) {
                answer = reply.content();
                break;
            }
            anyToolCalled = true;
            messages.add(OllamaClient.assistantMessage(reply));
            for (ToolCall call : reply.toolCalls()) {
                toolsUsed.add(call.name());
                ToolResult result;
                try {
                    result = toolService.execute(call.name(), call.arguments());
                } catch (SQLException e) {
                    LOG.warn("AI tool {} failed", call.name(), e);
                    result = toolService.error("The gallery database is temporarily unavailable.");
                }
                mergeHits(hits, result.hits());
                JsonObject toolMessage = new JsonObject();
                toolMessage.addProperty("role", "tool");
                toolMessage.addProperty("tool_name", call.name());
                toolMessage.addProperty("content", result.content());
                messages.add(toolMessage);
            }
        }
        if ((answer == null || answer.isBlank()) && anyToolCalled && !hits.isEmpty()) {
            answer = summariseWithModel(model, messages);
        }
        return new ToolRun(answer, anyToolCalled, warning);
    }

    /**
     * The small model sometimes stops after tool calls without writing a final
     * sentence. One no-tools turn asks it to summarise what it already found.
     */
    private String summariseWithModel(String model, JsonArray messages) {
        JsonArray request = messages.deepCopy();
        request.add(message("user",
                "Summarize the tool results in 1-2 short sentences for the user. Do not call any tools."));
        try {
            ChatReply reply = ollama.chat(model, request, null);
            return reply.content();
        } catch (OllamaException e) {
            LOG.warn("Could not generate the final AI summary", e);
            return "";
        }
    }

    private String runPromptMode(String model, String prompt, Map<Long, ImageHit> hits,
            Set<String> toolsUsed) throws SQLException {
        String instruction = """
                You are the search-query parser for a colour-first image gallery.
                Reply with ONLY a JSON object and no markdown:
                {"tags":["..."],"colours":["..."],"text":"...","answer":"..."}
                tags are scene, object or theme words likely to appear as gallery tags.
                colours are colour or mood words such as red, blue, warm, cool, sunset, ocean.
                text is words likely to appear in a title or description.
                answer is one short sentence describing the search.
                Use empty arrays or an empty string when a field does not apply.
                Known gallery tags: %s
                User request: %s
                """.formatted(knownTagsForPrompt(), prompt);

        JsonArray messages = new JsonArray();
        messages.add(message("system", "You convert image-search requests into compact JSON."));
        messages.add(message("user", instruction));
        ChatReply reply = ollama.chat(model, messages, null);
        JsonObject plan = parsePlan(reply.content());

        String answer = "";
        if (plan == null) {
            localSearch(prompt, hits);
            return "";
        }

        List<String> tags = jsonStringList(plan, "tags");
        List<String> colours = jsonStringList(plan, "colours");
        if (colours.isEmpty()) {
            colours = jsonStringList(plan, "colors");
        }
        if (colours.isEmpty()) {
            colours = ColourNames.extract(prompt);
        }
        String text = jsonString(plan, "text");
        if (text.isBlank()) {
            text = jsonString(plan, "query");
        }
        answer = jsonString(plan, "answer");

        if (!tags.isEmpty()) {
            mergeHits(hits, toolService.execute("search_by_tags", tagArguments(tags)).hits());
            toolsUsed.add("search_by_tags");
        }
        if (!colours.isEmpty()) {
            mergeHits(hits, toolService.execute("search_by_colour", colourArguments(colours)).hits());
            toolsUsed.add("search_by_colour");
        }
        if (!text.isBlank()) {
            mergeHits(hits, toolService.execute("search_by_text", textArguments(text)).hits());
            toolsUsed.add("search_by_text");
        }
        if (hits.isEmpty()) {
            localSearch(prompt, hits);
        }
        return answer;
    }

    /**
     * Deterministic fallback. It matches known tags, colour words and a few
     * meaningful keywords, so the page still works when Ollama is offline.
     */
    private void localSearch(String prompt, Map<Long, ImageHit> hits) throws SQLException {
        String lower = prompt.toLowerCase(Locale.ROOT);
        List<Tag> knownTags = toolService.knownTags(40);
        List<String> matchedTags = new ArrayList<>();
        for (Tag tag : knownTags) {
            if (containsWord(lower, tag.getName())) {
                matchedTags.add(tag.getName());
            }
            if (matchedTags.size() >= 5) {
                break;
            }
        }
        List<String> colours = ColourNames.extract(prompt);

        if (!matchedTags.isEmpty()) {
            mergeHits(hits, toolService.execute("search_by_tags", tagArguments(matchedTags)).hits());
        }
        if (!colours.isEmpty()) {
            mergeHits(hits, toolService.execute("search_by_colour", colourArguments(colours)).hits());
        }
        if (hits.isEmpty()) {
            for (String keyword : keywords(prompt)) {
                mergeHits(hits, toolService.execute("search_by_text", textArguments(keyword)).hits());
                if (!hits.isEmpty()) {
                    break;
                }
            }
        }
    }

    private List<AiImageCard> loadCards(Map<Long, ImageHit> hits) throws SQLException {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(hits.keySet());
        Map<Long, Image> images = new HashMap<>();
        for (Image image : imageDao.findByIds(ids)) {
            images.put(image.getImageId(), image);
        }
        List<AiImageCard> cards = new ArrayList<>();
        for (Map.Entry<Long, ImageHit> entry : hits.entrySet()) {
            Image image = images.get(entry.getKey());
            if (image == null) {
                continue;
            }
            ImageHit hit = entry.getValue();
            cards.add(new AiImageCard(image.getImageId(), image.getTitle(), image.getUsername(),
                    image.getDescription(), hit.scorePercent(), hit.reason()));
            if (cards.size() >= 24) {
                break;
            }
        }
        return cards;
    }

    private static void mergeHits(Map<Long, ImageHit> hits, List<ImageHit> incoming) {
        for (ImageHit hit : incoming) {
            ImageHit existing = hits.get(hit.imageId());
            if (existing == null) {
                hits.put(hit.imageId(), hit);
                continue;
            }
            String reason = existing.reason() == null ? "" : existing.reason();
            if (hit.reason() != null && !hit.reason().isBlank() && !reason.contains(hit.reason())) {
                reason = reason.isBlank() ? hit.reason() : reason + "; " + hit.reason();
            }
            Integer score = existing.scorePercent();
            if (hit.scorePercent() != null && (score == null || hit.scorePercent() > score)) {
                score = hit.scorePercent();
            }
            hits.put(hit.imageId(), new ImageHit(hit.imageId(), reason, score));
        }
    }

    private ModelChoice resolveModel() {
        List<String> candidates = new ArrayList<>();
        if (AiConfig.preferTools()) {
            candidates.add(AiConfig.toolModel());
        }
        candidates.add(AiConfig.model());
        if (AiConfig.preferTools() && !candidates.contains(AiConfig.toolModel())) {
            candidates.add(AiConfig.toolModel());
        }

        ModelInfo firstAvailable = null;
        String firstError = null;
        for (String candidate : candidates) {
            try {
                ModelInfo info = inspect(candidate);
                if (info.tools()) {
                    return new ModelChoice(info.model(), true);
                }
                if (firstAvailable == null) {
                    firstAvailable = info;
                }
            } catch (OllamaException e) {
                if (firstError == null) {
                    firstError = e.getMessage();
                }
            }
        }
        if (firstAvailable != null) {
            return new ModelChoice(firstAvailable.model(), false);
        }
        throw new OllamaException(firstError == null
                ? "No Ollama model is available. Pull qwen3.5:0.8b or qwen2:1.5b first."
                : firstError);
    }

    private ModelInfo inspect(String model) {
        long now = System.currentTimeMillis();
        CachedModel cached = MODEL_CACHE.get(model);
        if (cached != null && cached.expiresAt() > now) {
            return cached.info();
        }
        ModelInfo info = ollama.show(model);
        MODEL_CACHE.put(model, new CachedModel(info, now + MODEL_CACHE_MILLIS));
        return info;
    }

    private String knownTagsForPrompt() {
        try {
            List<String> names = new ArrayList<>();
            for (Tag tag : toolService.knownTags(24)) {
                names.add(tag.getName());
            }
            return names.isEmpty() ? "(none yet)" : String.join(", ", names);
        } catch (SQLException e) {
            LOG.warn("Could not load tags for the AI prompt", e);
            return "(unavailable)";
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static JsonObject tagArguments(List<String> tags) {
        JsonObject arguments = new JsonObject();
        JsonArray array = new JsonArray();
        for (String tag : tags) {
            array.add(tag);
        }
        arguments.add("tags", array);
        arguments.addProperty("match", "any");
        return arguments;
    }

    private static JsonObject colourArguments(List<String> colours) {
        JsonObject arguments = new JsonObject();
        JsonArray array = new JsonArray();
        for (String colour : colours) {
            array.add(colour);
        }
        arguments.add("colours", array);
        return arguments;
    }

    private static JsonObject textArguments(String text) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("query", text);
        return arguments;
    }

    private static List<String> jsonStringList(JsonObject object, String key) {
        Set<String> values = new LinkedHashSet<>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    String value = item.getAsString().trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
        } else if (element.isJsonPrimitive()) {
            for (String part : element.getAsString().split("[,;]")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private static String jsonString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }

    private static JsonObject parsePlan(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(content.substring(start, end + 1));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> keywords(String prompt) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String word : prompt.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
            if (keywords.size() >= 3) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private static boolean containsWord(String text, String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
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

    private static String normalisePrompt(String rawPrompt) {
        String prompt = rawPrompt == null ? "" : rawPrompt.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ").trim();
        if (prompt.isEmpty()) {
            throw new ValidationException("Describe the image you are looking for.");
        }
        if (prompt.length() > AiConfig.maxPromptLength()) {
            throw new ValidationException("Keep the search under " + AiConfig.maxPromptLength()
                    + " characters.");
        }
        return prompt;
    }

    private static String summarise(List<AiImageCard> cards, String mode, Set<String> toolsUsed) {
        if (cards.isEmpty()) {
            return "I could not find a matching image. Try a broader colour or tag, or upload a few photos first.";
        }
        StringBuilder summary = new StringBuilder("I found ")
                .append(cards.size())
                .append(cards.size() == 1 ? " image" : " images")
                .append(" for you.");
        if ("tools".equals(mode) && !toolsUsed.isEmpty()) {
            summary.append(" I searched with ").append(String.join(", ", toolsUsed)).append(".");
        }
        return summary.toString();
    }

    private record CachedModel(ModelInfo info, long expiresAt) {
    }

    private record ModelChoice(String model, boolean tools) {
    }

    private record ToolRun(String answer, boolean anyToolCalled, String warning) {
    }
}
