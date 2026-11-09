package com.gallery.util;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Synchroniser-token CSRF protection for every state-changing request. */
public final class CsrfUtil {

    public static final String SESSION_KEY = "csrfToken";
    public static final String PARAM = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfUtil() {
    }

    /** Returns the session token, creating one when absent. */
    public static String getToken(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_KEY);
            if (existing instanceof String token && !token.isBlank()) {
                return token;
            }
            String token = generateToken();
            session.setAttribute(SESSION_KEY, token);
            return token;
        }
    }

    /** Replaces the token (used after privilege changes such as login). */
    public static void rotate(HttpSession session) {
        session.setAttribute(SESSION_KEY, generateToken());
    }

    /**
     * Validates the {@code _csrf} form field. Supports both url-encoded and
     * multipart forms, and compares with a constant-time equality check.
     */
    public static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        if (!(expected instanceof String expectedToken) || expectedToken.isBlank()) {
            return false;
        }
        String actual = request.getParameter(PARAM);
        if (actual == null) {
            actual = readMultipartField(request);
        }
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String readMultipartField(HttpServletRequest request) {
        try {
            Part part = request.getPart(PARAM);
            if (part == null || part.getSize() > 200) {
                return null;
            }
            return new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (ServletException | java.io.IOException | IllegalStateException e) {
            return null;
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
