package com.gallery.servlet;

import com.gallery.model.ImageBinary;
import com.gallery.service.ImageService;
import com.gallery.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Serves image bytes at {@code /image/{id}} and thumbnails at
 * {@code /thumb/{id}}. Supports ETag/304 and streams BLOBs in 8 KB chunks.
 */
@WebServlet(name = "imageServlet", urlPatterns = { "/image/*", "/thumb/*" })
public class ImageServlet extends HttpServlet {

    private final ImageService imageService = new ImageService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        boolean thumbnail = request.getServletPath().startsWith("/thumb");
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        long imageId = ValidationUtil.parseId(pathInfo.substring(1));
        if (imageId < 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            Optional<ImageBinary> found = imageService.binaryInfo(imageId, thumbnail);
            if (found.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            ImageBinary binary = found.get();
            String etag = "\"" + (thumbnail ? "thumb-" : "img-") + imageId + "-"
                    + binary.getUploadedAt().getTime() + "\"";
            response.setHeader("ETag", etag);
            response.setHeader("Cache-Control", "private, max-age=86400");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (etag.equals(request.getHeader("If-None-Match"))) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
            response.setContentType(binary.getMimeType());
            response.setContentLengthLong(binary.getLength());
            imageService.streamBinary(imageId, thumbnail, response.getOutputStream());
        } catch (SQLException e) {
            throw new ServletException("Could not read image data", e);
        }
    }
}
