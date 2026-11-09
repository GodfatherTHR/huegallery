package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.User;
import com.gallery.service.ImageService;
import com.gallery.service.TagService;
import com.gallery.util.ValidationException;
import com.gallery.util.ValidationUtil;
import com.gallery.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/** Owner-only tag editing. */
@WebServlet("/edit-tags")
public class EditTagsServlet extends HttpServlet {

    private final ImageService imageService = new ImageService();
    private final TagService tagService = new TagService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long imageId = ValidationUtil.parseId(request.getParameter("id"));
        if (imageId < 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Optional<Image> found = imageService.find(imageId);
            if (found.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            User user = (User) request.getSession().getAttribute("currentUser");
            if (found.get().getUserId() != user.getUserId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "You can only edit tags on your own images.");
                return;
            }
            request.setAttribute("pageTitle", "Edit tags");
            request.setAttribute("image", found.get());
            request.setAttribute("tagsValue", tagService.commaSeparatedTags(imageId));
            request.getRequestDispatcher("/WEB-INF/views/edit-tags.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not load the image", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!WebUtil.requireCsrf(request, response)) {
            return;
        }
        long imageId = ValidationUtil.parseId(request.getParameter("id"));
        if (imageId < 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        User user = (User) request.getSession().getAttribute("currentUser");
        String tagsValue = request.getParameter("tags");
        try {
            tagService.replaceTags(imageId, user.getUserId(), tagsValue);
            WebUtil.flashSuccess(request, "Tags updated.");
            response.sendRedirect(request.getContextPath() + "/photo/" + imageId);
        } catch (ValidationException e) {
            try {
                Optional<Image> found = imageService.find(imageId);
                if (found.isPresent()) {
                    request.setAttribute("image", found.get());
                }
            } catch (SQLException ignored) {
                // Rendering the form with the validation error is still useful.
            }
            request.setAttribute("pageTitle", "Edit tags");
            request.setAttribute("error", e.getMessage());
            request.setAttribute("tagsValue", tagsValue);
            request.getRequestDispatcher("/WEB-INF/views/edit-tags.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not update tags", e);
        }
    }
}
