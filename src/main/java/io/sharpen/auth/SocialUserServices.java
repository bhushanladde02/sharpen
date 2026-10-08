package io.sharpen.auth;

import io.sharpen.auth.SignInService.ProviderUser;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns "Google/GitHub says this is user X" into "this is Sharpen person P". The rest of the application
 * identifies the signed-in person by email ({@code PersonService.current()}), so the principal built here is named
 * after the Sharpen account's email — not the provider's — and carries the same role a password sign-in gets.
 */
@Component
public class SocialUserServices {

    /** Session attribute set by Settings → Connect: the id of the signed-in person the next sign-in links to. */
    public static final String LINK_ATTRIBUTE = "sharpen.oauth.link";
    /** The extra attribute the principal is named by. */
    static final String NAME_ATTRIBUTE = "sharpen_email";

    private final SignInService signIn;
    private final PersonRepository people;
    private final OidcUserService oidcDelegate = new OidcUserService();
    private final DefaultOAuth2UserService oauthDelegate = new DefaultOAuth2UserService();
    private final RestClient rest = RestClient.create();

    public SocialUserServices(SignInService signIn, PersonRepository people) {
        this.signIn = signIn;
        this.people = people;
    }

    /** OpenID Connect providers (Google): the ID token has been verified by Spring before this runs. */
    public OAuth2UserService<OidcUserRequest, OidcUser> oidc() {
        return request -> {
            OidcUser user = oidcDelegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            String name = user.getFullName() != null ? user.getFullName()
                    : join(user.getGivenName(), user.getFamilyName());
            Person p = signIn.resolve(new ProviderUser(provider, user.getSubject(), user.getEmail(),
                    Boolean.TRUE.equals(user.getEmailVerified()), name, null), linkTarget());
            Map<String, Object> claims = new HashMap<>(user.getUserInfo() != null ? user.getUserInfo().getClaims() : Map.of("sub", user.getSubject()));
            claims.put(NAME_ATTRIBUTE, p.getEmail());
            return new DefaultOidcUser(authorities(p), user.getIdToken(), new OidcUserInfo(claims), NAME_ATTRIBUTE);
        };
    }

    /** Plain OAuth 2.0 providers (GitHub): the profile, plus the account's verified email from {@code /user/emails}. */
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2() {
        return request -> {
            OAuth2User user = oauthDelegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            Map<String, Object> a = user.getAttributes();
            String login = a.get("login") == null ? null : a.get("login").toString();
            String name = a.get("name") == null ? null : a.get("name").toString();
            String[] email = verifiedEmail(request, a);
            Person p = signIn.resolve(new ProviderUser(provider, Objects.toString(a.get("id")), email[0],
                    email[1] != null, name, login), linkTarget());
            Map<String, Object> attributes = new HashMap<>(a);
            attributes.put(NAME_ATTRIBUTE, p.getEmail());
            return new DefaultOAuth2User(authorities(p), attributes, NAME_ATTRIBUTE);
        };
    }

    /**
     * GitHub's primary verified address ({@code [email, "verified"]}), or else any verified one; {@code [null, null]}
     * when there is none. The public {@code email} on the profile is not used: it may be unverified or hidden.
     */
    private String[] verifiedEmail(OAuth2UserRequest request, Map<String, Object> attributes) {
        String uri = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri().replaceAll("/$", "") + "/emails";
        try {
            List<Map<String, Object>> emails = rest.get().uri(uri)
                    .headers(h -> { h.setBearerAuth(request.getAccessToken().getTokenValue()); h.set("User-Agent", "Sharpen"); })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (emails != null) {
                for (boolean wantPrimary : new boolean[] {true, false}) {
                    for (Map<String, Object> e : emails) {
                        if (Boolean.TRUE.equals(e.get("verified")) && (!wantPrimary || Boolean.TRUE.equals(e.get("primary")))) {
                            return new String[] {Objects.toString(e.get("email")), "verified"};
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // No email scope or the call failed: treat as "no verified email"; resolve() decides what that means.
        }
        return new String[] {null, null};
    }

    /** The person to link to, when this sign-in started from Settings → Connect by the person still signed in. */
    private Long linkTarget() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpSession session = attrs.getRequest().getSession(false);
        if (session == null || !(session.getAttribute(LINK_ATTRIBUTE) instanceof Long id)) return null;
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !current.isAuthenticated()) return null;
        return people.findById(id).filter(p -> p.getEmail().equalsIgnoreCase(current.getName())).map(Person::getId).orElse(null);
    }

    private static List<GrantedAuthority> authorities(Person p) {
        return List.of(new SimpleGrantedAuthority(p.isCompany() ? "ROLE_COMPANY" : "ROLE_INDIVIDUAL"));
    }

    private static String join(String first, String last) {
        String s = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return s.isEmpty() ? null : s;
    }
}
