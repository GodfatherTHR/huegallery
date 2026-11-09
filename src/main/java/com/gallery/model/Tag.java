package com.gallery.model;

/** A normalised tag plus the number of images that use it. */
public class Tag {

    private long tagId;
    private String name;
    private long usageCount;

    public Tag() {
    }

    public Tag(long tagId, String name, long usageCount) {
        this.tagId = tagId;
        this.name = name;
        this.usageCount = usageCount;
    }

    public long getTagId() {
        return tagId;
    }

    public void setTagId(long tagId) {
        this.tagId = tagId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(long usageCount) {
        this.usageCount = usageCount;
    }
}
