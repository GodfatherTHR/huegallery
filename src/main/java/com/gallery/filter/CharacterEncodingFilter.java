package com.gallery.filter;

import com.gallery.util.CsrfUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Forces UTF-8 for every request/response and exposes a CSRF token to all
 * pages (the logout form in the navigation needs one too).
 */
@WebFilter(filterName = "characterEncodingFilter", urlPatterns = "/*")
public class CharacterEncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        // Response encoding is set by each view/servlet. Setting it globally here
        // would append "charset=UTF-8" to binary image content types.
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        httpRequest.setAttribute("csrfToken", CsrfUtil.getToken(httpRequest.getSession()));
        chain.doFilter(request, response);
    }
}
