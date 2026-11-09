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
import java.util.Optional;

/** Login form and credential submission. */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        request.setAttribute("pageTitle", "Sign in");
        request.setAttribute("next", request.getParameter("next"));
        request.setAttribute("usernameValue", "");
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!WebUtil.requireCsrf(request, response)) {
            return;
        }
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String next = request.getParameter("next");
        try {
            Optional<User> user = authService.login(username, password);
            if (user.isEmpty()) {
                request.setAttribute("pageTitle", "Sign in");
                request.setAttribute("next", next);
                request.setAttribute("usernameValue", username);
                request.setAttribute("error", "Invalid username or password.");
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
                return;
            }
            rotateSession(request, user.get());
            WebUtil.flashSuccess(request, "Welcome back, " + user.get().getUsername() + ".");
            String target = WebUtil.safeRedirectTarget(next, "/home");
            response.sendRedirect(request.getContextPath() + target);
        } catch (ValidationException e) {
            request.setAttribute("pageTitle", "Sign in");
            request.setAttribute("next", next);
            request.setAttribute("usernameValue", username);
            request.setAttribute("error", e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
        } catch (SQLException e) {
            throw new ServletException("Could not sign in", e);
        }
    }

    /** Prevents session fixation by replacing the session after authentication. */
    private void rotateSession(HttpServletRequest request, User user) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUser", user);
        CsrfUtil.rotate(session);
    }
}
