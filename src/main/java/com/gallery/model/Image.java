package com.gallery.model;

import java.sql.Timestamp;

/**
 * Image metadata. Binary data is never carried on this object: list views use
 * thumbnails served by {@code /thumb/{id}} and the detail page uses
 * {@code /image/{id}}, so BLOBs are only read on demand.
 */
public class Image {

    private long imageId;
    private long userId;
    private String title;
    private String description;
    private String fileName;
    private String mimeType;
    private long fileSize;
    private int width;
    private int height;
    private Timestamp uploadedAt;
    private String username;

    public long getImageId() {
        return imageId;
    }

    public void setImageId(long imageId) {
        this.imageId = imageId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getFileSizeKb() {
        return Math.max(1, Math.round(fileSize / 1024.0));
    }
}
