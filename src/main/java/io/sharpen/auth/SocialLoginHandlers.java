package io.sharpen.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Where a Google/GitHub sign-in lands. A sign-in from the login or sign-up page goes where a password sign-in
 * goes (the page that asked for it, else the dashboard); a <i>Connect</i> from Settings goes back to Settings.
 * A refusal goes back to whichever page started it, with a code the page turns into a sentence.
 */
@Component
public class SocialLoginHandlers {

    private final SavedRequestAwareAuthenticationSuccessHandler normal = new SavedRequestAwareAuthenticationSuccessHandler();

    public SocialLoginHandlers() {
        normal.setDefaultTargetUrl("/dashboard");
    }

    public AuthenticationSuccessHandler success() {
        return (HttpServletRequest req, HttpServletResponse res, Authentication auth) -> {
            if (takeLinkFlag(req)) {
                String provider = auth instanceof OAuth2AuthenticationToken t ? t.getAuthorizedClientRegistrationId() : "";
                res.sendRedirect(req.getContextPath() + UriComponentsBuilder.fromPath("/settings")
                        .queryParam("connected", provider).fragment("sign-in").build().toUriString());
                return;
            }
            normal.onAuthenticationSuccess(req, res, auth);
        };
    }

    public AuthenticationFailureHandler failure() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            String code = ex instanceof OAuth2AuthenticationException o ? o.getError().getErrorCode() : "failed";
            String uri = req.getRequestURI();
            String provider = uri.substring(uri.lastIndexOf('/') + 1);
            boolean linking = takeLinkFlag(req);
            res.sendRedirect(req.getContextPath() + UriComponentsBuilder.fromPath(linking ? "/settings" : "/login")
                    .queryParam("signin_error", code).queryParam("provider", provider)
                    .fragment(linking ? "sign-in" : null).build().toUriString());
        };
    }

    /** True once if this sign-in started from Settings → Connect; the flag is removed either way. */
    private static boolean takeLinkFlag(HttpServletRequest req) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(SocialUserServices.LINK_ATTRIBUTE) == null) return false;
        session.removeAttribute(SocialUserServices.LINK_ATTRIBUTE);
        return true;
    }
}
