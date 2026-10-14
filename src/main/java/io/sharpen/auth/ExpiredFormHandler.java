package io.sharpen.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A form submitted after its session is gone — the tab was open across a restart, or the person sat on the sign-in
 * page for longer than the session lives — used to land on the 403 page, which read like something had gone wrong.
 * Nothing has: there is simply no session to check the form's token against, so the person is sent back to a fresh
 * form with a one-line note instead. A token that *is* present but wrong is a different matter and still gets the
 * 403 ({@link AccessDeniedHandlerImpl}), as does every other refusal.
 */
@Component
public class ExpiredFormHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException refusal)
            throws IOException, ServletException {
        if (refusal instanceof MissingCsrfTokenException) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            response.sendRedirect(request.getContextPath() + target(path));
            return;
        }
        otherwise.handle(request, response, refusal);
    }

    /** Where a session-less form goes: back to its own page for sign-up, home for sign-out, the sign-in form for the rest. */
    static String target(String path) {
        if ("/register".equals(path)) return "/register?expired";
        if ("/logout".equals(path)) return "/";   // no session means they are already signed out
        return "/login?expired";
    }
}
