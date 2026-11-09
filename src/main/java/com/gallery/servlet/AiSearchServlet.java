package com.gallery.servlet;

import com.gallery.ai.AiSearchResult;
import com.gallery.ai.AiSearchService;
import com.gallery.ai.OllamaException;
import com.gallery.util.CsrfUtil;
import com.gallery.util.ValidationException;
import com.gallery.util.WebUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-rendered AI search page plus the JSON endpoint used by its
 * progressive-enhancement JavaScript. The page works without JavaScript via a
 * normal GET request.
 */
@WebServlet(name = "aiSearchServlet", urlPatterns = { "/ai-search", "/api/ai-search" })
public class AiSearchServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AiSearchServlet.class);

    private final AiSearchService aiSearchService = new AiSearchService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/api/ai-search".equals(request.getServletPath())) {
            writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Use POST for the AI search endpoint.");
            return;
        }
        String query = request.getParameter("q");
        request.setAttribute("pageTitle", "AI image search");
        request.setAttribute("query", query == null ? "" : query.trim());
        if (query != null && !query.isBlank()) {
            try {
                request.setAttribute("result", aiSearchService.search(query));
            } catch (ValidationException e) {
                request.setAttribute("error", e.getMessage());
            } catch (SQLException e) {
                throw new ServletException("Could not run the AI search", e);
            }
        }
        request.getRequestDispatcher("/WEB-INF/views/ai-search.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/api/ai-search".equals(request.getServletPath())) {
            handleApi(request, response);
            return;
        }
        if (!WebUtil.requireCsrf(request, response)) {
            return;
        }
        String query = request.getParameter("q");
        request.setAttribute("pageTitle", "AI image search");
        request.setAttribute("query", query == null ? "" : query.trim());
        if (query != null && !query.isBlank()) {
            try {
                request.setAttribute("result", aiSearchService.search(query));
            } catch (ValidationException e) {
                request.setAttribute("error", e.getMessage());
            } catch (SQLException e) {
                throw new ServletException("Could not run the AI search", e);
            }
        }
        request.getRequestDispatcher("/WEB-INF/views/ai-search.jsp").forward(request, response);
    }

    private void handleApi(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!CsrfUtil.isValid(request)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Your form session expired. Reload the page and try again.");
            return;
        }
        String prompt = request.getParameter("prompt");
        if (prompt == null) {
            prompt = request.getParameter("q");
        }
        try {
            AiSearchResult result = aiSearchService.search(prompt);
            JsonObject payload = new JsonObject();
            payload.addProperty("ok", true);
            payload.add("result", gson.toJsonTree(result));
            writeJson(response, HttpServletResponse.SC_OK, payload);
        } catch (ValidationException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (OllamaException e) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        } catch (SQLException e) {
            LOG.error("AI search database failure", e);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The gallery database is unavailable right now.");
        } catch (RuntimeException e) {
            LOG.error("Unexpected AI search failure", e);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The AI search failed. Please try again.");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("ok", false);
        payload.addProperty("error", message == null ? "The AI search failed." : message);
        writeJson(response, status, payload);
    }

    private void writeJson(HttpServletResponse response, int status, JsonObject payload)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(gson.toJson(payload));
    }
}
