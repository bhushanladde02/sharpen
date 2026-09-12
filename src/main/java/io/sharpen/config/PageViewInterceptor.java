package io.sharpen.config;

import io.sharpen.service.TrafficService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Counts a page view for every successful HTML page. Runs after the handler, so the request itself is never
 * slowed by analytics; the actual insert happens in a batch from {@link TrafficService#flush()}.
 */
@Component
public class PageViewInterceptor implements HandlerInterceptor {

    private final TrafficService traffic;

    public PageViewInterceptor(TrafficService traffic) {
        this.traffic = traffic;
    }

    @Override
    public void postHandle(HttpServletRequest req, HttpServletResponse res, Object handler, ModelAndView mav) {
        if (!"GET".equals(req.getMethod()) || res.getStatus() != 200 || mav == null || mav.getViewName() == null
                || mav.getViewName().startsWith("redirect:")) return;
        String path = req.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/admin/") || path.equals("/error")) return;
        String address = req.getHeader("X-Forwarded-For");          // set by Caddy in production
        if (address != null && address.contains(",")) address = address.substring(0, address.indexOf(',')).trim();
        if (address == null || address.isBlank()) address = req.getRemoteAddr();
        boolean signedIn = req.getUserPrincipal() != null;
        traffic.record(path, req.getHeader("Referer"), address, req.getHeader("User-Agent"), req.getHeader("Accept-Language"), signedIn);
    }
}
