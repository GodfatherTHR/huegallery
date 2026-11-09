package com.gallery.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Small request/session helpers shared by controllers and JSPs. */
public final class WebUtil {

    private WebUtil() {
    }

    /**
     * Rejects the request with 403 when the CSRF token is missing or stale.
     *
     * @return true when processing may continue
     */
    public static boolean requireCsrf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (CsrfUtil.isValid(request)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Your form session expired. Please reload the page and try again.");
        return false;
    }

    public static void flash(HttpServletRequest request, String message, boolean error) {
        request.getSession().setAttribute("flash", new Flash(message, error));
    }

    public static void flashSuccess(HttpServletRequest request, String message) {
        flash(request, message, false);
    }

    public static void flashError(HttpServletRequest request, String message) {
        flash(request, message, true);
    }

    /**
     * Only permits relative, single-slash redirect targets so the login
     * {@code next} parameter cannot be used as an open redirect.
     */
    public static String safeRedirectTarget(String candidate, String fallback) {
        if (candidate == null) {
            return fallback;
        }
        String trimmed = candidate.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("\\")) {
            return trimmed;
        }
        return fallback;
    }
}
