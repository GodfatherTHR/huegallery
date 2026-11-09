package com.gallery.ai;

import java.util.List;

/** Answer plus the real gallery images the assistant selected. */
public class AiSearchResult {

    private final String answer;
    private final List<AiImageCard> images;
    private final String model;
    private final String mode;
    private final List<String> toolsUsed;
    private final long elapsedMs;
    private final String warning;

    public AiSearchResult(String answer, List<AiImageCard> images, String model, String mode,
            List<String> toolsUsed, long elapsedMs, String warning) {
        this.answer = answer;
        this.images = images;
        this.model = model;
        this.mode = mode;
        this.toolsUsed = toolsUsed;
        this.elapsedMs = elapsedMs;
        this.warning = warning;
    }

    public String getAnswer() {
        return answer;
    }

    public List<AiImageCard> getImages() {
        return images;
    }

    public String getModel() {
        return model;
    }

    public String getMode() {
        return mode;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getWarning() {
        return warning;
    }

    public int getImageCount() {
        return images == null ? 0 : images.size();
    }

    /** Elapsed time for display, e.g. "12.4". */
    public String getElapsedSeconds() {
        return String.format(java.util.Locale.ROOT, "%.1f", elapsedMs / 1000.0);
    }
}
