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
import java.util.regex.Pattern;

/**
 * Where a Google/GitHub sign-in lands. A sign-in from the login or sign-up page goes where a password sign-in
 * goes (the page that asked for it, else the dashboard); a <i>Connect</i> from Settings goes back to Settings.
 * A refusal goes back to whichever page started it, with a code the page turns into a sentence.
 *
 * <p>Every redirect goes to a fixed path on this site, and nothing a visitor can influence reaches its address
 * unchecked: the provider must be one this deployment offers, and the error code — which a provider (or anyone
 * calling the callback URL by hand with {@code ?error=…}) supplies — must be a short lower-case word. Anything
 * else becomes "unknown" / "failed". The values are then URL-encoded, so they can never add parameters.
 */
@Component
public class SocialLoginHandlers {

    /** What an error code may look like: OAuth codes are short snake_case words ({@code access_denied}). */
    private static final Pattern CODE = Pattern.compile("[a-z_]{1,40}");

    private final SavedRequestAwareAuthenticationSuccessHandler normal = new SavedRequestAwareAuthenticationSuccessHandler();
    private final SocialRegistrations registrations;

    public SocialLoginHandlers(SocialRegistrations registrations) {
        this.registrations = registrations;
        normal.setDefaultTargetUrl("/dashboard");
    }

    /** The provider id if this deployment offers it, else "unknown" — never a visitor's own text. */
    String knownProvider(String candidate) {
        return candidate != null && registrations.offers(candidate) ? candidate : "unknown";
    }

    /** The error code if it is a plain OAuth-style word, else "failed". */
    static String safeCode(String candidate) {
        return candidate != null && CODE.matcher(candidate).matches() ? candidate : "failed";
    }

    public AuthenticationSuccessHandler success() {
        return (HttpServletRequest req, HttpServletResponse res, Authentication auth) -> {
            if (takeLinkFlag(req)) {
                String provider = knownProvider(auth instanceof OAuth2AuthenticationToken t ? t.getAuthorizedClientRegistrationId() : null);
                res.sendRedirect(req.getContextPath() + UriComponentsBuilder.fromPath("/settings")
                        .queryParam("connected", provider).fragment("sign-in").encode().build().toUriString());
                return;
            }
            normal.onAuthenticationSuccess(req, res, auth);
        };
    }

    public AuthenticationFailureHandler failure() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            String code = safeCode(ex instanceof OAuth2AuthenticationException o ? o.getError().getErrorCode() : null);
            String uri = req.getRequestURI();
            String provider = knownProvider(uri.substring(uri.lastIndexOf('/') + 1));
            boolean linking = takeLinkFlag(req);
            res.sendRedirect(req.getContextPath() + UriComponentsBuilder.fromPath(linking ? "/settings" : "/login")
                    .queryParam("signin_error", code).queryParam("provider", provider)
                    .fragment(linking ? "sign-in" : null).encode().build().toUriString());
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
