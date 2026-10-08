package io.sharpen.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Sign in with …" providers this deployment offers. A provider is offered only when its client id and secret
 * are configured ({@code SHARPEN_OAUTH_GOOGLE_CLIENT_ID} / {@code _CLIENT_SECRET}, the same for GITHUB), so the
 * site runs exactly as before until they are set, and each can be switched on separately.
 *
 * <p>Unlike Spring's in-memory repository this one may be empty. {@code endpoint-base} (tests only) points a
 * provider's authorization, token, user-info and key endpoints at a stand-in server; the issuer stays the real one.
 */
@Component
public class SocialRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    /** A provider as the pages show it. */
    public record Provider(String id, String label) {}

    private final Map<String, ClientRegistration> byId = new LinkedHashMap<>();
    private final List<Provider> providers = new ArrayList<>();

    public SocialRegistrations(@Value("${sharpen.oauth.google.client-id:}") String googleId,
                               @Value("${sharpen.oauth.google.client-secret:}") String googleSecret,
                               @Value("${sharpen.oauth.google.endpoint-base:}") String googleBase,
                               @Value("${sharpen.oauth.github.client-id:}") String githubId,
                               @Value("${sharpen.oauth.github.client-secret:}") String githubSecret,
                               @Value("${sharpen.oauth.github.endpoint-base:}") String githubBase) {
        if (configured(googleId, googleSecret)) {
            // OpenID Connect: openid + profile + email, the ID token is checked against Google's keys.
            add(rebase(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId.trim()).clientSecret(googleSecret.trim()).build(), googleBase), "Google");
        }
        if (configured(githubId, githubSecret)) {
            // Plain OAuth 2.0: read:user for the name and login, user:email because many people hide their address.
            add(rebase(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubId.trim()).clientSecret(githubSecret.trim())
                    .scope("read:user", "user:email").build(), githubBase), "GitHub");
        }
    }

    private static boolean configured(String id, String secret) {
        return id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }

    private void add(ClientRegistration r, String label) {
        byId.put(r.getRegistrationId(), r);
        providers.add(new Provider(r.getRegistrationId(), label));
    }

    static ClientRegistration rebase(ClientRegistration r, String base) {
        if (base == null || base.isBlank()) return r;
        var p = r.getProviderDetails();
        ClientRegistration.Builder b = ClientRegistration.withClientRegistration(r)
                .authorizationUri(onto(base, p.getAuthorizationUri()))
                .tokenUri(onto(base, p.getTokenUri()))
                .userInfoUri(onto(base, p.getUserInfoEndpoint().getUri()));
        if (p.getJwkSetUri() != null) b.jwkSetUri(onto(base, p.getJwkSetUri()));
        return b.build();
    }

    private static String onto(String base, String uri) {
        return base.replaceAll("/$", "") + URI.create(uri).getRawPath();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return byId.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return Collections.unmodifiableCollection(byId.values()).iterator();
    }

    public boolean isEmpty() { return byId.isEmpty(); }

    public List<Provider> providers() { return Collections.unmodifiableList(providers); }

    public boolean offers(String id) { return byId.containsKey(id); }

    public String label(String id) {
        return providers.stream().filter(p -> p.id().equals(id)).map(Provider::label).findFirst()
                .orElse(id == null ? "the provider" : Character.toUpperCase(id.charAt(0)) + id.substring(1));
    }
}
