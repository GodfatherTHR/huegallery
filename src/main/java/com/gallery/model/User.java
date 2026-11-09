package com.gallery.model;

import java.sql.Timestamp;

/**
 * Application user. The password hash is only populated for authentication
 * lookups and is cleared before the object is placed in the session.
 */
public class User {

    private long userId;
    private String username;
    private String passwordHash;
    private Timestamp createdAt;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
