package com.gallery.util;

/** Server-side validation helpers shared by the services and servlets. */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    public static String requireText(String value, String fieldName, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    /** Returns null for blank input so Oracle stores a real NULL, not an empty string. */
    public static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String requireUsername(String value) {
        String username = value == null ? "" : value.trim();
        if (!username.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new ValidationException(
                    "Username must be 3-50 characters using letters, numbers, dots, underscores or hyphens.");
        }
        return username;
    }

    public static void requirePassword(String password, String confirm) {
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters.");
        }
        if (password.length() > 72) {
            throw new ValidationException("Password must be at most 72 characters.");
        }
        if (!password.equals(confirm)) {
            throw new ValidationException("The two passwords do not match.");
        }
    }

    /** Parses a positive page number, defaulting to 1 for missing or invalid input. */
    public static int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            return Math.max(1, page);
        } catch (NumberFormatException | NullPointerException e) {
            return 1;
        }
    }

    /** Parses a positive numeric id, returning -1 when the value is not a valid id. */
    public static long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : -1;
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    public static long parsePositiveLong(String value, String fieldName) {
        long parsed = parseId(value);
        if (parsed < 0) {
            throw new ValidationException(fieldName + " is invalid.");
        }
        return parsed;
    }
}
