package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.User;
import com.gallery.service.ImageService;
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

/** Owner-only image deletion. */
@WebServlet("/delete")
public class DeleteImageServlet extends HttpServlet {

    private final ImageService imageService = new ImageService();

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
        try {
            Optional<Image> found = imageService.find(imageId);
            if (found.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (found.get().getUserId() != user.getUserId()) {
                WebUtil.flashError(request, "You can only delete your own images.");
                response.sendRedirect(request.getContextPath() + "/photo/" + imageId);
                return;
            }
            imageService.delete(imageId, user.getUserId());
            WebUtil.flashSuccess(request, "The image was deleted.");
            response.sendRedirect(request.getContextPath() + "/my-images");
        } catch (ValidationException e) {
            WebUtil.flashError(request, e.getMessage());
            response.sendRedirect(request.getContextPath() + "/photo/" + imageId);
        } catch (SQLException e) {
            throw new ServletException("Could not delete the image", e);
        }
    }
}
