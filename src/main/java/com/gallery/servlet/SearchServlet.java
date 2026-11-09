package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.PageResult;
import com.gallery.service.ImageService;
import com.gallery.util.TagParser;
import com.gallery.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Tag search with ANY (OR) / ALL (AND) modes. */
@WebServlet("/search")
public class SearchServlet extends HttpServlet {

    private static final int PAGE_SIZE = 24;

    private final ImageService imageService = new ImageService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String rawTags = request.getParameter("tags");
        List<String> tags = TagParser.parseQuery(rawTags);
        if (tags.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        boolean all = "all".equalsIgnoreCase(request.getParameter("mode"));
        int page = ValidationUtil.parsePage(request.getParameter("page"));
        try {
            PageResult<Image> pageResult = imageService.searchByTags(tags, all, page, PAGE_SIZE);
            request.setAttribute("pageTitle", "Search");
            request.setAttribute("pageResult", pageResult);
            request.setAttribute("searchTags", tags);
            request.setAttribute("tagsInput", String.join(", ", tags));
            request.setAttribute("modeAll", all);
            request.getRequestDispatcher("/WEB-INF/views/search.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not run the search", e);
        }
    }
}
