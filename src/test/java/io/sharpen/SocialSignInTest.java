package io.sharpen;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonIdentityRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.service.PersonService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Continue with Google / GitHub", end to end. A small stand-in for both providers runs inside the test — the
 * token endpoint, user info, GitHub's email list, and for Google a signed ID token checked against a published key
 * — and the real application talks to it over HTTP exactly as it talks to Google and GitHub in production. The
 * test plays the browser: it follows the redirect to the provider, comes back with a code, and checks where it
 * lands and what the database holds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sharpen-social;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "sharpen.demo-data=false"
})
class SocialSignInTest {

    // ---- the stand-in provider ---------------------------------------------------------------------------------

    static final HttpServer PROVIDER;
    static final RSAKey KEY;
    static final ObjectMapper JSON = new ObjectMapper();
    /** What the stand-in says about the person signing in; each test sets what it needs. */
    static volatile Map<String, Object> googleUser = Map.of();
    static volatile Map<String, Object> githubUser = Map.of();
    static volatile List<Map<String, Object>> githubEmails = List.of();
    static volatile String lastNonce;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PROVIDER.createContext("/", SocialSignInTest::provider);
            PROVIDER.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stop() { PROVIDER.stop(0); }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
        r.add("sharpen.oauth.google.client-id", () -> "google-test-client");
        r.add("sharpen.oauth.google.client-secret", () -> "google-test-secret");
        r.add("sharpen.oauth.google.endpoint-base", () -> base + "/g");
        r.add("sharpen.oauth.github.client-id", () -> "github-test-client");
        r.add("sharpen.oauth.github.client-secret", () -> "github-test-secret");
        r.add("sharpen.oauth.github.endpoint-base", () -> base + "/gh");
    }

    private static void provider(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        Object body;
        try {
            if (path.startsWith("/g/") && path.endsWith("/token")) {
                Map<String, Object> u = googleUser;
                JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                        .issuer("https://accounts.google.com").subject((String) u.get("sub")).audience("google-test-client")
                        .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("nonce", lastNonce);
                u.forEach(claims::claim);
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
                jwt.sign(new RSASSASigner(KEY));
                body = Map.of("access_token", "g-access", "token_type", "Bearer", "expires_in", 300,
                        "scope", "openid profile email", "id_token", jwt.serialize());
            } else if (path.startsWith("/g/") && path.endsWith("/userinfo")) {
                body = googleUser;
            } else if (path.startsWith("/g/") && path.endsWith("/certs")) {
                body = new JWKSet(KEY.toPublicJWK()).toJSONObject();
            } else if (path.startsWith("/gh/") && path.endsWith("/access_token")) {
                body = Map.of("access_token", "gh-access", "token_type", "bearer", "scope", "read:user,user:email");
            } else if (path.equals("/gh/user/emails")) {
                body = githubEmails;
            } else if (path.equals("/gh/user")) {
                body = githubUser;
            } else {
                x.sendResponseHeaders(404, -1);
                return;
            }
        } catch (Exception e) {
            x.sendResponseHeaders(500, -1);
            return;
        }
        byte[] out = JSON.writeValueAsBytes(body);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(200, out.length);
        x.getResponseBody().write(out);
        x.close();
    }

    // ---- the browser -------------------------------------------------------------------------------------------

    @LocalServerPort int port;
    @Autowired PersonRepository people;
    @Autowired PersonService personService;
    @Autowired PersonIdentityRepository identities;
    @Autowired org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.OAuth2AuthorizedClientService> authorizedClients;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** One browser: its cookies, kept by hand so the test sees every redirect. */
    final class Browser {
        final Map<String, String> cookies = new LinkedHashMap<>();

        HttpResponse<String> get(String url) throws Exception {
            return send(HttpRequest.newBuilder(uri(url)).GET());
        }

        HttpResponse<String> post(String url, Map<String, String> form) throws Exception {
            String body = form.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            return send(HttpRequest.newBuilder(uri(url)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
        }

        private HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
            if (!cookies.isEmpty()) b.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            for (String c : r.headers().allValues("Set-Cookie")) {
                String[] kv = c.split(";", 2)[0].split("=", 2);
                cookies.put(kv[0], kv[1]);
            }
            return r;
        }

        private URI uri(String url) { return URI.create(url.startsWith("http") ? url : "http://localhost:" + port + url); }

        String csrf(String page) throws Exception {
            Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(get(page).body());
            assertTrue(m.find(), "a form with a CSRF token on " + page);
            return m.group(1);
        }

        /** Press "Continue with …", let the provider approve, come back. Returns where the app sends the browser. */
        String continueWith(String provider) throws Exception {
            return returnFromProvider(get("/oauth2/authorization/" + provider));
        }

        String returnFromProvider(HttpResponse<String> toProvider) throws Exception {
            assertEquals(302, toProvider.statusCode(), "the app sends the browser to the provider");
            Map<String, String> q = query(toProvider.headers().firstValue("Location").orElseThrow());
            lastNonce = q.get("nonce");
            HttpResponse<String> back = get(q.get("redirect_uri") + "?code=test-code&state=" + URLEncoder.encode(q.get("state"), StandardCharsets.UTF_8));
            assertEquals(302, back.statusCode(), "the app answers the provider's return with a redirect");
            return back.headers().firstValue("Location").orElseThrow();
        }

        String passwordSignIn(String email, String password) throws Exception {
            String token = csrf("/login");
            return post("/login", Map.of("username", email, "password", password, "_csrf", token)).headers().firstValue("Location").orElse("");
        }
    }

    private static Map<String, String> query(String url) {
        Map<String, String> out = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return out;
    }

    private static Map<String, Object> google(String sub, String email, boolean verified, String given, String family) {
        return Map.of("sub", sub, "email", email, "email_verified", verified, "given_name", given, "family_name", family, "name", given + " " + family);
    }

    // ---- the tests ---------------------------------------------------------------------------------------------

    @Test
    void loginAndSignUpOfferTheConfiguredProviders() throws Exception {
        Browser b = new Browser();
        String login = b.get("/login").body();
        assertTrue(login.contains("Continue with Google") && login.contains("Continue with GitHub"));
        assertTrue(login.contains("href=\"/oauth2/authorization/github\""));
        assertTrue(b.get("/register").body().contains("Sign up with GitHub"));
    }

    @Test
    void githubCreatesAnAccountOnceAndSignsInAfterwards() throws Exception {
        githubUser = Map.of("id", 101, "login", "octo", "name", "Octo Cat");
        githubEmails = List.of(Map.of("email", "octo-hidden@users.example", "primary", false, "verified", true),
                               Map.of("email", "octo@users.example", "primary", true, "verified", true));
        long before = people.count();

        Browser b = new Browser();
        assertTrue(b.continueWith("github").endsWith("/dashboard"));
        assertTrue(b.get("/dashboard").body().contains("Hello, Octo"));
        Person octo = people.findByEmailIgnoreCase("octo@users.example").orElseThrow();   // the primary verified address
        assertFalse(octo.hasPassword());
        assertEquals("Octo", octo.getFirstName());
        assertEquals("Cat", octo.getLastName());
        assertEquals(AccountType.INDIVIDUAL, octo.getAccountType());
        assertEquals("octo", identities.findByProviderAndSubject("github", "101").orElseThrow().getUsername());

        // Signing in again — even after the email on GitHub changed — is the same account, not a second one.
        githubEmails = List.of(Map.of("email", "octo-new@users.example", "primary", true, "verified", true));
        Browser again = new Browser();
        assertTrue(again.continueWith("github").endsWith("/dashboard"));
        assertEquals(before + 1, people.count());
        assertTrue(again.get("/settings").body().contains("octo@users.example"));

        // Sharpen asked GitHub who this is and nothing more: no access token is kept anywhere after sign-in.
        authorizedClients.ifAvailable(svc -> assertNull(svc.loadAuthorizedClient("github", "octo@users.example")));

        // No password, so the password form treats the address like an unknown one.
        assertTrue(new Browser().passwordSignIn("octo@users.example", "anything-at-all").contains("/login?error"));
    }

    @Test
    void noAccountWithoutAVerifiedEmail() throws Exception {
        githubUser = Map.of("id", 102, "login", "ghost", "name", "Ghost");
        githubEmails = List.of(Map.of("email", "ghost@users.example", "primary", true, "verified", false));
        Browser b = new Browser();
        String landed = b.continueWith("github");
        assertTrue(landed.contains("/login?signin_error=no_verified_email&provider=github"), landed);
        assertTrue(b.get(landed).body().contains("did not share a verified email address"));
        assertTrue(people.findByEmailIgnoreCase("ghost@users.example").isEmpty());

        googleUser = google("g-unverified", "maybe@gmail.example", false, "May", "Be");
        assertTrue(new Browser().continueWith("google").contains("signin_error=no_verified_email"));
        assertTrue(people.findByEmailIgnoreCase("maybe@gmail.example").isEmpty());
    }

    @Test
    void googleCreatesAnAccountWithTheNamesItGives() throws Exception {
        googleUser = google("g-grace", "grace@gmail.example", true, "Grace", "Hopper");
        Browser b = new Browser();
        assertTrue(b.continueWith("google").endsWith("/dashboard"));
        Person grace = people.findByEmailIgnoreCase("grace@gmail.example").orElseThrow();
        assertEquals("Grace Hopper", grace.getDisplayName());
        assertEquals("grace-hopper", grace.getHandle());
        assertTrue(b.get("/settings").body().contains("Set a password"));
    }

    @Test
    void anExistingEmailIsNeverMergedAutomatically() throws Exception {
        personService.register("taken@example.com", "password123", "Taken Person", AccountType.INDIVIDUAL);
        googleUser = google("g-taken", "taken@example.com", true, "Someone", "Else");
        Browser b = new Browser();
        String landed = b.continueWith("google");
        assertTrue(landed.contains("signin_error=email_in_use"), landed);
        assertTrue(b.get(landed).body().contains("already uses the email address on your Google account"));
        assertTrue(identities.findByProviderAndSubject("google", "g-taken").isEmpty());
    }

    @Test
    void connectFromSettingsThenSignInWithIt() throws Exception {
        personService.register("linker@example.com", "password123", "Lin Kerr", AccountType.INDIVIDUAL);
        Browser b = new Browser();
        assertTrue(b.passwordSignIn("linker@example.com", "password123").endsWith("/dashboard"));
        String token = b.csrf("/settings");
        assertTrue(b.get("/settings").body().contains("Connect"));

        // A different email on GitHub is fine: the person proved both sides by being signed in and approving.
        githubUser = Map.of("id", 202, "login", "linkerk", "name", "L K");
        githubEmails = List.of(Map.of("email", "lk@elsewhere.example", "primary", true, "verified", true));
        HttpResponse<String> connect = b.post("/settings/connect/github", Map.of("_csrf", token));
        assertEquals("/oauth2/authorization/github", URI.create(connect.headers().firstValue("Location").orElseThrow()).getPath());
        String landed = b.returnFromProvider(b.get("/oauth2/authorization/github"));
        assertEquals("/settings", URI.create(landed).getPath(), landed);
        assertEquals("connected=github", URI.create(landed).getQuery(), landed);
        String settings = b.get(landed).body();
        assertTrue(settings.contains("GitHub connected"));
        assertTrue(settings.contains("linkerk") && settings.contains("Disconnect"), "Settings shows which GitHub account");
        Long linker = people.findByEmailIgnoreCase("linker@example.com").orElseThrow().getId();
        assertEquals(linker, identities.findByProviderAndSubject("github", "202").orElseThrow().getPersonId());
        assertTrue(people.findByEmailIgnoreCase("lk@elsewhere.example").isEmpty(), "no second account");

        // Later, from a fresh browser: Continue with GitHub is Lin.
        Browser fresh = new Browser();
        assertTrue(fresh.continueWith("github").endsWith("/dashboard"));
        assertTrue(fresh.get("/dashboard").body().contains("Hello, Lin"));

        // Someone else cannot connect the same GitHub account to theirs.
        personService.register("second@example.com", "password123", "Sec Ond", AccountType.INDIVIDUAL);
        Browser other = new Browser();
        other.passwordSignIn("second@example.com", "password123");
        other.post("/settings/connect/github", Map.of("_csrf", other.csrf("/settings")));
        String refused = other.returnFromProvider(other.get("/oauth2/authorization/github"));
        assertTrue(refused.contains("/settings?signin_error=identity_in_use"), refused);
        assertEquals(linker, identities.findByProviderAndSubject("github", "202").orElseThrow().getPersonId());
    }

    @Test
    void theLastWayInStaysAndDeletionWorksWithoutAPassword() throws Exception {
        googleUser = google("g-solo", "solo@gmail.example", true, "Solo", "Person");
        Browser b = new Browser();
        b.continueWith("google");
        Person solo = people.findByEmailIgnoreCase("solo@gmail.example").orElseThrow();

        // Disconnecting the only sign-in is refused.
        b.post("/settings/disconnect/google", Map.of("_csrf", b.csrf("/settings")));
        assertTrue(identities.findByProviderAndSubject("google", "g-solo").isPresent());

        // A password can be set without a "current" one; then Google may go.
        b.post("/settings/password", Map.of("_csrf", b.csrf("/settings"), "next", "brand-new-pass", "repeat", "brand-new-pass"));
        assertTrue(people.findById(solo.getId()).orElseThrow().hasPassword());
        b.post("/settings/disconnect/google", Map.of("_csrf", b.csrf("/settings")));
        assertTrue(identities.findByProviderAndSubject("google", "g-solo").isEmpty());
        assertTrue(new Browser().passwordSignIn("solo@gmail.example", "brand-new-pass").endsWith("/dashboard"));

        // An account with no password is deleted by typing its handle.
        googleUser = google("g-leaver", "leaver@gmail.example", true, "Lea", "Ver");
        Browser leaver = new Browser();
        leaver.continueWith("google");
        Person lea = people.findByEmailIgnoreCase("leaver@gmail.example").orElseThrow();
        assertTrue(leaver.get("/settings").body().contains("Type your handle"));
        leaver.post("/settings/delete", Map.of("_csrf", leaver.csrf("/settings"), "password", "not-the-handle"));
        assertTrue(people.findById(lea.getId()).isPresent(), "wrong handle: nothing deleted");
        leaver.post("/settings/delete", Map.of("_csrf", leaver.csrf("/settings"), "password", lea.getHandle()));
        assertTrue(people.findById(lea.getId()).isEmpty());
        assertTrue(identities.findByProviderAndSubject("google", "g-leaver").isEmpty());
    }
}
