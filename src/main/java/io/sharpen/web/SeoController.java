package io.sharpen.web;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * What search engines ask for: {@code /robots.txt} (crawl the public pages, stay out of the personal ones)
 * and {@code /sitemap.xml} (the landing page, the directory, and every public profile).
 */
@Controller
public class SeoController {

    private final PersonRepository people;

    public SeoController(PersonRepository people) {
        this.people = people;
    }

    private static String base(HttpServletRequest req) {
        String host = req.getHeader("Host");
        return "https://" + (host == null || host.isBlank() ? req.getServerName() : host);
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots(HttpServletRequest req) {
        String body = """
            User-agent: *
            Allow: /
            Disallow: /dashboard
            Disallow: /sessions
            Disallow: /import
            Disallow: /reports
            Disallow: /settings
            Disallow: /admin
            Disallow: /candidates
            Disallow: /login
            Disallow: /register
            Disallow: /feedback
            Disallow: /api/

            Sitemap: %s/sitemap.xml
            """.formatted(base(req));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(body);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest req) {
        String base = base(req);
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(sb, base + "/", today, "weekly", "1.0");
        url(sb, base + "/p", today, "daily", "0.8");
        for (Person p : people.findByAccountTypeAndPublicProfileTrue(AccountType.INDIVIDUAL)) {
            url(sb, base + "/p/" + p.getHandle(), today, "weekly", "0.6");
        }
        sb.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(sb.toString());
    }

    private static void url(StringBuilder sb, String loc, String lastmod, String freq, String priority) {
        sb.append("  <url><loc>").append(loc).append("</loc><lastmod>").append(lastmod)
          .append("</lastmod><changefreq>").append(freq).append("</changefreq><priority>").append(priority)
          .append("</priority></url>\n");
    }
}
