package com.gallery.servlet;

import com.gallery.model.Image;
import com.gallery.model.PageResult;
import com.gallery.model.User;
import com.gallery.service.ImageService;
import com.gallery.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/** Lists the signed-in user's own images. */
@WebServlet("/my-images")
public class MyImagesServlet extends HttpServlet {

    private static final int PAGE_SIZE = 24;

    private final ImageService imageService = new ImageService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("currentUser");
        int page = ValidationUtil.parsePage(request.getParameter("page"));
        try {
            PageResult<Image> pageResult = imageService.listForUser(user.getUserId(), page, PAGE_SIZE);
            request.setAttribute("pageTitle", "My images");
            request.setAttribute("pageResult", pageResult);
            request.getRequestDispatcher("/WEB-INF/views/my-images.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not load your images", e);
        }
    }
}
