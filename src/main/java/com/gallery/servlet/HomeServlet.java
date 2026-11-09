package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.PageResult;
import com.gallery.model.Tag;
import com.gallery.service.ImageService;
import com.gallery.service.TagService;
import com.gallery.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Home page: paginated gallery plus tag cloud. */
@WebServlet(name = "homeServlet", urlPatterns = { "", "/home" })
public class HomeServlet extends HttpServlet {

    private static final int PAGE_SIZE = 24;

    private final ImageService imageService = new ImageService();
    private final TagService tagService = new TagService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            int page = ValidationUtil.parsePage(request.getParameter("page"));
            PageResult<Image> pageResult = imageService.listAll(page, PAGE_SIZE);
            List<Tag> cloud = tagService.cloud();
            long maxCount = cloud.stream().mapToLong(Tag::getUsageCount).max().orElse(1L);
            request.setAttribute("pageTitle", "Gallery");
            request.setAttribute("pageResult", pageResult);
            request.setAttribute("tagCloud", cloud);
            request.setAttribute("maxTagCount", Math.max(1L, maxCount));
            request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not load the gallery", e);
        }
    }
}
