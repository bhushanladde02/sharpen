package io.sharpen.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@code /sessions/} → {@code /sessions}. Spring 6 no longer matches a trailing slash to the same handler, so
 * a typed or auto-completed slash would 404. GET requests are redirected permanently to the canonical URL;
 * anything else is left alone (a POST with a stray slash is a bug worth seeing).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TrailingSlashFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if ("GET".equals(req.getMethod()) && uri.length() > 1 && uri.endsWith("/")) {
            String target = uri.replaceAll("/+$", "");
            if (req.getQueryString() != null) target += "?" + req.getQueryString();
            res.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            res.setHeader("Location", target);
            return;
        }
        chain.doFilter(req, res);
    }
}
