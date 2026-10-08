package io.sharpen;

import io.sharpen.service.SpamGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production runs behind Caddy, which sets X-Forwarded-For and X-Forwarded-Proto. These checks need real Tomcat
 * (its remote-IP valve does the work, MockMvc never sees it), so this test starts the server on a random port
 * and talks to it over HTTP, the way Caddy does from inside the Docker network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sharpen-proxy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "sharpen.demo-data=false"
})
class ProxyHeadersTest {

    @LocalServerPort int port;
    @Autowired SpamGuard guard;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) b.headers(headers);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> cookies(HttpResponse<?> r) {
        return r.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID=")).toList();
    }

    @Test
    void sessionCookieIsSecureWhenTheProxySaysHttps() throws Exception {
        // Through Caddy: the browser spoke https, so the cookie must never travel over plain http.
        HttpResponse<String> viaProxy = get("/login", "X-Forwarded-Proto", "https", "X-Forwarded-For", "203.0.113.20");
        assertEquals(200, viaProxy.statusCode());
        String cookie = cookies(viaProxy).getFirst();
        assertTrue(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);

        // Plain http with no proxy (development): no Secure flag, or a local browser could not sign in.
        HttpClient fresh = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> direct = fresh.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).build(),
                HttpResponse.BodyHandlers.ofString());
        String plain = cookies(direct).getFirst();
        assertFalse(plain.contains("Secure"), plain);
        assertTrue(plain.contains("SameSite=Lax"), plain);
    }

    @Test
    void signUpLimitFollowsTheRealClientNotAnAddressTheVisitorWrote() throws Exception {
        // A visitor can put anything at the start of X-Forwarded-For; the proxy appends the real address at the
        // end. The limit must count the real one, so five sign-ups with five invented "first" addresses from the
        // same client still use up that client's allowance.
        for (int i = 1; i <= 5; i++) {
            HttpResponse<String> r = register("spoof" + i, "10.9.9." + i + ", 198.51.100." + i + ", 203.0.113.50");
            assertEquals(302, r.statusCode(), "attempt " + i + " should be accepted");
            String location = r.headers().firstValue("Location").orElse("");
            assertTrue(location.contains("/login?registered") && !location.startsWith("http:"), location);
        }
        HttpResponse<String> sixth = register("spoof6", "192.0.2.99, 203.0.113.50");
        assertEquals(200, sixth.statusCode());
        assertTrue(sixth.body().contains("Several accounts were created from your connection"), "sixth from the same client is refused");

        // A different real client is unaffected.
        assertEquals(302, register("other", "203.0.113.51").statusCode());
    }

    /**
     * The test talks plain http to the port, as Caddy does; the cookie it gets back is marked Secure because the
     * proxy said https, so a normal cookie jar would (rightly) refuse to send it back. Carry it by hand, as Caddy
     * passes the browser's Cookie header through.
     */
    private HttpResponse<String> register(String name, String forwardedFor) throws Exception {
        HttpResponse<String> form = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", forwardedFor).build(), HttpResponse.BodyHandlers.ofString());
        String session = cookies(form).getFirst().split(";")[0];
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(form.body());
        assertTrue(csrf.find(), "register form carries a CSRF token");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("_csrf", csrf.group(1));
        fields.put("t", guard.tokenIssuedAgo(10));
        fields.put("website", "");
        fields.put("firstName", "Proxy");
        fields.put("lastName", name);
        fields.put("email", name + "@proxy.example.com");
        fields.put("password", "password123");
        fields.put("accountType", "INDIVIDUAL");
        String body = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", forwardedFor)
                        .header("Cookie", session)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
