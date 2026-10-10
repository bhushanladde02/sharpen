package io.sharpen.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Sharpen uses Google, GitHub and LinkedIn only to find out who is signing in, never to call them on the person's behalf
 * afterwards. Spring's default keeps each provider access token in memory for as long as the server runs; this
 * keeps none, so there is no token to leak and the privacy page can say so.
 */
public class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        // deliberately not kept
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        // nothing to remove
    }
}
