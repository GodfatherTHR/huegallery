package com.gallery.servlet;

import com.gallery.model.Tag;
import com.gallery.service.TagService;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** JSON autocomplete endpoint used by the tag chip inputs. */
@WebServlet("/api/tags")
public class TagApiServlet extends HttpServlet {

    private final TagService tagService = new TagService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String query = request.getParameter("q");
        if (query != null && query.length() > 40) {
            query = query.substring(0, 40);
        }
        try {
            List<TagDto> payload = tagService.autocomplete(query).stream()
                    .map(tag -> new TagDto(tag.getName(), tag.getUsageCount()))
                    .toList();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(gson.toJson(payload));
        } catch (SQLException e) {
            throw new ServletException("Could not load tags", e);
        }
    }

    private record TagDto(String name, long count) {
    }
}
