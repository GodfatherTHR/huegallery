package com.gallery.ai;

/** One image card returned by the AI search. */
public class AiImageCard {

    private final long imageId;
    private final String title;
    private final String username;
    private final String description;
    private final Integer scorePercent;
    private final String reason;

    public AiImageCard(long imageId, String title, String username, String description,
            Integer scorePercent, String reason) {
        this.imageId = imageId;
        this.title = title;
        this.username = username;
        this.description = description;
        this.scorePercent = scorePercent;
        this.reason = reason;
    }

    public long getImageId() {
        return imageId;
    }

    public String getTitle() {
        return title;
    }

    public String getUsername() {
        return username;
    }

    public String getDescription() {
        return description;
    }

    public Integer getScorePercent() {
        return scorePercent;
    }

    public String getReason() {
        return reason;
    }
}
