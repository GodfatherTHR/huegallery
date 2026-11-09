package com.gallery.model;

import java.sql.Timestamp;

/** Metadata required to serve an image BLOB without loading it into memory. */
public class ImageBinary {

    private final String mimeType;
    private final Timestamp uploadedAt;
    private final long length;

    public ImageBinary(String mimeType, Timestamp uploadedAt, long length) {
        this.mimeType = mimeType;
        this.uploadedAt = uploadedAt;
        this.length = length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public long getLength() {
        return length;
    }
}
