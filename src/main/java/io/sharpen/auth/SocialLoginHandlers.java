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
import java.util.List;

/**
 * Where a Google/GitHub/LinkedIn sign-in lands. A sign-in from the login or sign-up page goes where a password sign-in
 * goes (the page that asked for it, else the dashboard); a <i>Connect</i> from Settings goes back to Settings.
 * A refusal goes back to whichever page started it, with a code the page turns into a sentence.
 *
 * <p>Every redirect goes to a fixed path on this site, and nothing a visitor can influence reaches its address:
 * the provider and the error code (which a provider, or anyone calling the callback URL by hand with
 * {@code ?error=…}, supplies) are each replaced by the matching value from a fixed list Sharpen owns — a configured
 * provider id, or one of {@link #CODES} — and anything else becomes "unknown" / "failed". The values are also
 * URL-encoded. (Returning Sharpen's own strings rather than the checked input is also what lets code scanning see
 * that no request data reaches the redirect.)
 */
@Component
public class SocialLoginHandlers {

    /** The error codes the sign-in pages have a sentence for; any other code is shown as "failed". */
    static final List<String> CODES = List.of("access_denied", "email_in_use", "no_verified_email", "identity_in_use",
            "provider_already_linked", "link_expired", "failed");

    private final SavedRequestAwareAuthenticationSuccessHandler normal = new SavedRequestAwareAuthenticationSuccessHandler();
    private final SocialRegistrations registrations;

    public SocialLoginHandlers(SocialRegistrations registrations) {
        this.registrations = registrations;
        normal.setDefaultTargetUrl("/dashboard");
    }

    /**
     * The id of the configured provider the candidate names, or "unknown". It returns the id from Sharpen's own
     * configuration, never the candidate itself, so no text from the request reaches the redirect.
     */
    String knownProvider(String candidate) {
        for (SocialRegistrations.Provider p : registrations.providers()) {
            if (p.id().equals(candidate)) return p.id();
        }
        return "unknown";
    }

    /** The matching code from {@link #CODES}, or "failed" — again Sharpen's own string, not the request's. */
    static String safeCode(String candidate) {
        for (String code : CODES) {
            if (code.equals(candidate)) return code;
        }
        return "failed";
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
