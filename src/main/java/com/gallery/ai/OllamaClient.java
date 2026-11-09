package com.gallery.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal client for Ollama's local HTTP API. Uses the JDK HttpClient and Gson
 * so no additional runtime dependency is required.
 */
public class OllamaClient {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaClient.class);

    public record ModelInfo(String model, boolean tools, boolean thinking) {
    }

    public record ToolCall(String name, JsonObject arguments, JsonObject raw) {
    }

    public record ChatReply(String content, String thinking, List<ToolCall> toolCalls,
            JsonArray rawToolCalls) {

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }

    private final String baseUrl;
    private final Duration requestTimeout;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public OllamaClient() {
        this(AiConfig.ollamaUrl(), Duration.ofSeconds(AiConfig.timeoutSeconds()));
    }

    public OllamaClient(String baseUrl, Duration requestTimeout) {
        String normalised = baseUrl == null || baseUrl.isBlank()
                ? "http://127.0.0.1:11434" : baseUrl.trim();
        this.baseUrl = normalised.endsWith("/")
                ? normalised.substring(0, normalised.length() - 1) : normalised;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Reads the capabilities declared by an installed model. */
    public ModelInfo show(String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonObject response = post("/api/show", body);
        JsonArray capabilities = response.has("capabilities") && response.get("capabilities").isJsonArray()
                ? response.getAsJsonArray("capabilities") : new JsonArray();
        boolean tools = false;
        boolean thinking = false;
        for (JsonElement element : capabilities) {
            String capability = element.isJsonPrimitive() ? element.getAsString() : "";
            if ("tools".equalsIgnoreCase(capability)) {
                tools = true;
            } else if ("thinking".equalsIgnoreCase(capability)) {
                thinking = true;
            }
        }
        String name = response.has("model") && response.get("model").isJsonPrimitive()
                ? response.get("model").getAsString() : model;
        return new ModelInfo(name, tools, thinking);
    }

    /**
     * Sends one non-streaming chat turn. Tool definitions may be null or empty
     * for prompt-only models.
     */
    public ChatReply chat(String model, JsonArray messages, JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.addProperty("keep_alive", "30m");
        body.addProperty("think", AiConfig.think());
        body.add("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
        }
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        body.add("options", options);

        JsonObject response = post("/api/chat", body);
        JsonObject message = response.has("message") && response.get("message").isJsonObject()
                ? response.getAsJsonObject("message") : new JsonObject();
        String content = stringValue(message, "content");
        String thinking = stringValue(message, "thinking");
        JsonArray rawCalls = message.has("tool_calls") && message.get("tool_calls").isJsonArray()
                ? message.getAsJsonArray("tool_calls") : new JsonArray();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonElement element : rawCalls) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rawCall = element.getAsJsonObject();
            JsonObject function = rawCall.has("function") && rawCall.get("function").isJsonObject()
                    ? rawCall.getAsJsonObject("function") : new JsonObject();
            String name = stringValue(function, "name");
            JsonObject arguments = arguments(function.get("arguments"));
            if (!name.isBlank()) {
                calls.add(new ToolCall(name, arguments, rawCall));
            }
        }
        return new ChatReply(content, thinking, calls, rawCalls);
    }

    /** JSON body of an assistant message that can be sent back with tool results. */
    public static JsonObject assistantMessage(ChatReply reply) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", reply.content() == null ? "" : reply.content());
        if (reply.toolCalls() != null && !reply.toolCalls().isEmpty()) {
            JsonArray calls = new JsonArray();
            for (ToolCall call : reply.toolCalls()) {
                JsonObject function = new JsonObject();
                function.addProperty("name", call.name());
                function.add("arguments", call.arguments() == null ? new JsonObject() : call.arguments());
                JsonObject toolCall = new JsonObject();
                toolCall.add("function", function);
                calls.add(toolCall);
            }
            message.add("tool_calls", calls);
        }
        return message;
    }

    private JsonObject post(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = errorDetail(response.body());
                if (response.statusCode() == 404) {
                    throw new OllamaException("The Ollama model " + modelFrom(body)
                            + " is not installed. Run: ollama pull " + modelFrom(body));
                }
                throw new OllamaException("Ollama returned HTTP " + response.statusCode()
                        + (detail.isBlank() ? "." : ": " + detail));
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (HttpTimeoutException e) {
            throw new OllamaException(
                    "The local model took too long to respond. Try a shorter prompt or a smaller model.", e);
        } catch (ConnectException e) {
            throw new OllamaException(offlineMessage(), e);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("connection refused") || message.contains("failed to connect")) {
                throw new OllamaException(offlineMessage(), e);
            }
            LOG.warn("Ollama request to {} failed", path, e);
            throw new OllamaException("Could not reach the local Ollama service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaException("The AI request was interrupted.", e);
        }
    }

    private String offlineMessage() {
        return "Ollama is not reachable at " + baseUrl
                + ". Start it with `ollama serve`, then try again.";
    }

    private static String modelFrom(JsonObject body) {
        return body.has("model") && body.get("model").isJsonPrimitive()
                ? body.get("model").getAsString() : "unknown";
    }

    private static String errorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                if (object.has("error") && object.get("error").isJsonPrimitive()) {
                    return object.get("error").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the raw body.
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
    }

    private static JsonObject arguments(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        if (element.isJsonPrimitive()) {
            try {
                JsonElement parsed = JsonParser.parseString(element.getAsString());
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (RuntimeException ignored) {
                // Leave empty; the tool will report invalid arguments.
            }
        }
        return new JsonObject();
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }
}
