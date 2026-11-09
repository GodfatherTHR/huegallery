package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.SimilarImage;
import com.gallery.model.Tag;
import com.gallery.model.User;
import com.gallery.service.ImageService;
import com.gallery.service.SimilarityService;
import com.gallery.service.TagService;
import com.gallery.util.ColourHistogram;
import com.gallery.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Image detail page with tags, dominant colours and similar images. */
@WebServlet("/photo/*")
public class ImageDetailServlet extends HttpServlet {

    private final ImageService imageService = new ImageService();
    private final TagService tagService = new TagService();
    private final SimilarityService similarityService = new SimilarityService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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
            Optional<Image> found = imageService.find(imageId);
            if (found.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Image image = found.get();
            List<Tag> tags = tagService.findByImage(imageId);
            List<SimilarImage> similar = similarityService.findSimilar(imageId,
                    SimilarityService.DEFAULT_LIMIT);
            List<ColourHistogram.Colour> dominant = similarityService.dominantColours(imageId);

            User current = (User) request.getSession().getAttribute("currentUser");
            boolean owner = current != null && current.getUserId() == image.getUserId();

            request.setAttribute("pageTitle", image.getTitle());
            request.setAttribute("image", image);
            request.setAttribute("tags", tags);
            request.setAttribute("similarImages", similar);
            request.setAttribute("dominantColours", dominant);
            request.setAttribute("owner", owner);
            request.getRequestDispatcher("/WEB-INF/views/image-detail.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not load the image", e);
        }
    }
}
