package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.User;
import com.gallery.service.ImageService;
import com.gallery.util.ValidationException;
import com.gallery.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/** Upload form and the multipart upload pipeline entry point. */
@WebServlet("/upload")
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 8L * 1024 * 1024 + 64 * 1024, maxRequestSize = 10L * 1024 * 1024)
public class UploadServlet extends HttpServlet {

    private final ImageService imageService = new ImageService();

    /** 8 MB file plus generous room for title, description and tag fields. */
    private static final long MAX_REQUEST_BYTES = ImageService.MAX_FILE_BYTES + 64 * 1024;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "Upload");
        request.setAttribute("tagsValue", "");
        request.getRequestDispatcher("/WEB-INF/views/upload.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String title = null;
        String description = null;
        String tags = null;
        try {
            // Check the declared size before parsing multipart data: otherwise
            // Tomcat's limit aborts parsing before the CSRF field can be read.
            if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
                showForm(request, response, null, null, null,
                        "That upload is too large. The maximum image size is 8 MB.");
                return;
            }
            if (!WebUtil.requireCsrf(request, response)) {
                return;
            }
            title = request.getParameter("title");
            description = request.getParameter("description");
            tags = request.getParameter("tags");

            Part part = request.getPart("image");
            if (part == null || part.getSize() == 0) {
                showForm(request, response, title, description, tags, "Choose an image to upload.");
                return;
            }
            if (part.getSize() > ImageService.MAX_FILE_BYTES) {
                showForm(request, response, title, description, tags,
                        "Images must be 8 MB or smaller.");
                return;
            }
            String submittedName = part.getSubmittedFileName();
            byte[] data;
            try (InputStream in = part.getInputStream()) {
                data = in.readAllBytes();
            }

            User user = (User) request.getSession().getAttribute("currentUser");
            Image image = imageService.upload(user.getUserId(), title, description, tags,
                    submittedName, data);
            WebUtil.flashSuccess(request, "“" + image.getTitle() + "” was uploaded.");
            response.sendRedirect(request.getContextPath() + "/photo/" + image.getImageId());
        } catch (IllegalStateException e) {
            // Tomcat throws this when the multipart limits are exceeded.
            showForm(request, response, title, description, tags,
                    "That upload is too large. The maximum image size is 8 MB.");
        } catch (ValidationException e) {
            showForm(request, response, title, description, tags, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Could not store the image", e);
        }
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response,
            String title, String description, String tags, String error)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "Upload");
        request.setAttribute("error", error);
        request.setAttribute("titleValue", title);
        request.setAttribute("descriptionValue", description);
        request.setAttribute("tagsValue", tags);
        request.getRequestDispatcher("/WEB-INF/views/upload.jsp").forward(request, response);
    }
}
