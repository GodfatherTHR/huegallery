package com.gallery.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the local Ollama assistant. Reads {@code ai.properties}
 * from the classpath, then lets system properties and environment variables
 * override each value. Defaults work with a stock local Ollama install.
 */
public final class AiConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfig.class);
    private static final String CONFIG_FILE = "ai.properties";
    private static final Properties PROPERTIES = load();

    private AiConfig() {
    }

    public static String ollamaUrl() {
        return resolve("ai.ollama.url", "GALLERY_OLLAMA_URL", "http://127.0.0.1:11434");
    }

    /** Prompt-mode model. qwen2:1.5b has no tool capability in Ollama. */
    public static String model() {
        return resolve("ai.model", "GALLERY_AI_MODEL", "qwen2:1.5b");
    }

    /** Tool-capable model used automatically when tool calling is available. */
    public static String toolModel() {
        return resolve("ai.tool-model", "GALLERY_AI_TOOL_MODEL", "qwen3.5:0.8b");
    }

    public static boolean preferTools() {
        return Boolean.parseBoolean(resolve("ai.prefer-tools", "GALLERY_AI_PREFER_TOOLS", "true"));
    }

    /**
     * Thinking makes the 0.8B model much slower on CPU. Tool selection is good
     * without it, so it is off by default and can be re-enabled here.
     */
    public static boolean think() {
        return Boolean.parseBoolean(resolve("ai.think", "GALLERY_AI_THINK", "false"));
    }

    public static int timeoutSeconds() {
        return intSetting("ai.timeout-seconds", "GALLERY_AI_TIMEOUT_SECONDS", 180, 10, 900);
    }

    public static int maxToolRounds() {
        return intSetting("ai.max-tool-rounds", "GALLERY_AI_MAX_TOOL_ROUNDS", 4, 1, 8);
    }

    public static int maxResults() {
        return intSetting("ai.max-results", "GALLERY_AI_MAX_RESULTS", 12, 1, 24);
    }

    public static int maxPromptLength() {
        return 500;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = AiConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}; using defaults", CONFIG_FILE, e);
        }
        return properties;
    }

    private static String resolve(String key, String envName, String fallback) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String fileValue = PROPERTIES.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }
        return fallback;
    }

    private static int intSetting(String key, String envName, int fallback, int min, int max) {
        String raw = resolve(key, envName, Integer.toString(fallback));
        try {
            int value = Integer.parseInt(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            LOG.warn("Ignoring invalid value for {}: {}", key, raw);
            return fallback;
        }
    }
}
