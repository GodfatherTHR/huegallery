package com.gallery.servlet;

import com.gallery.model.User;
import com.gallery.service.AuthService;
import com.gallery.util.CsrfUtil;
import com.gallery.util.ValidationException;
import com.gallery.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

/** Registration form and account creation. */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        request.setAttribute("pageTitle", "Create account");
        request.setAttribute("usernameValue", "");
        request.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!WebUtil.requireCsrf(request, response)) {
            return;
        }
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirm = request.getParameter("confirmPassword");
        try {
            User user = authService.register(username, password, confirm);
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("currentUser", user);
            CsrfUtil.rotate(session);
            WebUtil.flashSuccess(request, "Welcome to HueGallery, " + user.getUsername() + ".");
            response.sendRedirect(request.getContextPath() + "/home");
        } catch (ValidationException e) {
            request.setAttribute("pageTitle", "Create account");
            request.setAttribute("usernameValue", username);
            request.setAttribute("error", e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not create the account", e);
        }
    }
}
