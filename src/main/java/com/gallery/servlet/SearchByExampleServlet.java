package com.gallery.servlet;

import com.gallery.model.SimilarImage;
import com.gallery.service.ImageService;
import com.gallery.service.SimilarityService;
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
import java.util.List;

/**
 * "Search by example": compares an uploaded, unsaved image with the gallery
 * and shows the closest matches.
 */
@WebServlet("/search-by-example")
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 8L * 1024 * 1024 + 64 * 1024, maxRequestSize = 10L * 1024 * 1024)
public class SearchByExampleServlet extends HttpServlet {

    private final SimilarityService similarityService = new SimilarityService();

    private static final long MAX_REQUEST_BYTES = ImageService.MAX_FILE_BYTES + 64 * 1024;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "Search by example");
        request.getRequestDispatcher("/WEB-INF/views/example-search.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
                showPage(request, response, null,
                        "That upload is too large. The maximum image size is 8 MB.");
                return;
            }
            if (!WebUtil.requireCsrf(request, response)) {
                return;
            }
            Part part = request.getPart("image");
            if (part == null || part.getSize() == 0) {
                showPage(request, response, null, "Choose an image to search with.");
                return;
            }
            if (part.getSize() > ImageService.MAX_FILE_BYTES) {
                showPage(request, response, null, "Images must be 8 MB or smaller.");
                return;
            }
            byte[] data;
            try (InputStream in = part.getInputStream()) {
                data = in.readAllBytes();
            }
            List<SimilarImage> results = similarityService.searchByExample(data,
                    SimilarityService.DEFAULT_LIMIT);
            showPage(request, response, results, null);
        } catch (IllegalStateException e) {
            showPage(request, response, null,
                    "That upload is too large. The maximum image size is 8 MB.");
        } catch (ValidationException e) {
            showPage(request, response, null, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Could not run the similarity search", e);
        }
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response,
            List<SimilarImage> results, String error) throws ServletException, IOException {
        request.setAttribute("pageTitle", "Search by example");
        request.setAttribute("results", results);
        request.setAttribute("error", error);
        request.getRequestDispatcher("/WEB-INF/views/example-search.jsp").forward(request, response);
    }
}
