package io.sharpen.auth;

import io.sharpen.service.WindowLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Slows password guessing on the sign-in form. After {@link #PER_ACCOUNT} wrong passwords for one email, or
 * {@link #PER_ADDRESS} from one address, within {@link #WINDOW_SECONDS}, further attempts are not even checked:
 * the form comes back with "too many attempts" until the window passes. A correct sign-in clears the account's
 * count. Counts live in memory (Sharpen runs as one instance) and nothing about them is stored or logged beyond
 * the fact that a lock happened. The API-key chain has its own limit ({@code ApiKeyAuthFilter}).
 */
@Component
public class LoginThrottle extends OncePerRequestFilter implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);
    static final int PER_ACCOUNT = 10, PER_ADDRESS = 40;
    static final long WINDOW_SECONDS = 15 * 60;

    private final WindowLimiter byAccount = new WindowLimiter(PER_ACCOUNT, WINDOW_SECONDS, 20_000);
    private final WindowLimiter byAddress = new WindowLimiter(PER_ADDRESS, WINDOW_SECONDS, 20_000);

    /** Before the password is checked: a locked account or address goes straight back to the form. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI())) {
            String user = normalise(request.getParameter("username"));
            if (byAccount.exhausted(user) || byAddress.exhausted(request.getRemoteAddr())) {
                response.sendRedirect(request.getContextPath() + "/login?locked");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** A wrong password: count it, then show the usual "did not match" (or "locked" once the limit is reached). */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        String user = normalise(request.getParameter("username"));
        boolean accountOk = byAccount.allow(user), addressOk = byAddress.allow(request.getRemoteAddr());
        if (!accountOk || !addressOk) {
            log.info("Sign-in locked for {} minutes: {}", WINDOW_SECONDS / 60, accountOk ? "address limit" : "account limit");
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?error");
    }

    /** A correct sign-in clears the account's count; the address count stays (a bot does not get a reset by knowing one password). */
    public SavedRequestAwareAuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler h = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                org.springframework.security.core.Authentication authentication)
                    throws ServletException, IOException {
                byAccount.reset(normalise(request.getParameter("username")));
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        h.setDefaultTargetUrl("/dashboard");
        return h;
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
