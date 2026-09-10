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
import java.util.regex.Pattern;

/**
 * {@code /sessions/} → {@code /sessions}. Spring 6 no longer matches a trailing slash to the same handler, so
 * a typed or auto-completed slash would 404. GET requests are redirected permanently to the canonical URL;
 * anything else is left alone (a POST with a stray slash is a bug worth seeing).
 *
 * <p>The redirect target is rebuilt from a strict whitelist, never copied from the request: a path such as
 * {@code //evil.com/} must not become {@code Location: //evil.com} (an open redirect, CWE-601). Anything that
 * is not a plain, single-slash-rooted path with a simple query string is passed through untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TrailingSlashFilter extends OncePerRequestFilter {

    /** One leading slash, then segments of unreserved characters, then the trailing slash(es); no "//" inside, no scheme. */
    private static final Pattern SAFE_PATH = Pattern.compile("/(?:[A-Za-z0-9._~-]+/)*[A-Za-z0-9._~-]+/+");
    private static final Pattern SAFE_QUERY = Pattern.compile("[A-Za-z0-9._~%=&+-]*");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if ("GET".equals(req.getMethod()) && uri.length() > 1 && uri.endsWith("/")) {
            String target = canonical(uri, req.getQueryString());
            if (target != null) {
                res.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                res.setHeader("Location", target);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /** The same URL without its trailing slashes, or {@code null} when the request is anything but a plain path. */
    static String canonical(String uri, String query) {
        if (!SAFE_PATH.matcher(uri).matches()) return null;
        if (query != null && !SAFE_QUERY.matcher(query).matches()) return null;
        StringBuilder sb = new StringBuilder();
        for (String segment : uri.split("/")) {
            if (!segment.isEmpty()) sb.append('/').append(segment);
        }
        if (query != null && !query.isEmpty()) sb.append('?').append(query);
        return sb.toString();
    }
}
