package io.sharpen.config;

import io.sharpen.repo.PersonRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates {@code /api/**} calls by the {@code X-Api-Key} header (or {@code Authorization: Bearer <key>}). */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final PersonRepository people;

    public ApiKeyAuthFilter(PersonRepository people) {
        this.people = people;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) key = auth.substring(7).trim();
        }
        if (key != null && !key.isBlank()) {
            people.findByApiKey(key).ifPresent(p -> {
                var token = new UsernamePasswordAuthenticationToken(p.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + (p.isCompany() ? "COMPANY" : "INDIVIDUAL")),
                                new SimpleGrantedAuthority("ROLE_API")));
                SecurityContextHolder.getContext().setAuthentication(token);
            });
        }
        chain.doFilter(request, response);
    }
}
