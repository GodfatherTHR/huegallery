package com.gallery.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Redirects anonymous visitors away from owner-only pages. */
@WebFilter(filterName = "authFilter", urlPatterns = {
        "/upload", "/my-images", "/delete", "/edit-tags", "/logout"
})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("currentUser") != null;
        if (loggedIn) {
            chain.doFilter(request, response);
            return;
        }

        String contextPath = httpRequest.getContextPath();
        String target = httpRequest.getRequestURI().substring(contextPath.length());
        String query = httpRequest.getQueryString();
        if (query != null && !query.isEmpty()) {
            target = target + "?" + query;
        }
        String loginUrl = contextPath + "/login?next="
                + URLEncoder.encode(target, StandardCharsets.UTF_8);
        httpResponse.sendRedirect(loginUrl);
    }
}
