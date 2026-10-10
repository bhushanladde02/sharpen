package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One page view, recorded first-party (no third-party script, no cookie). {@code visitor} is a hash of the
 * day, the client address and the user agent with a secret salt — it lets a day's views from one browser be
 * counted as one visitor, but cannot be joined across days or traced back to a person. {@code country} is the
 * country the address belongs to, looked up on the server when the view is recorded; the address is not kept.
 */
@Entity
@Table(name = "page_view", indexes = {
        @Index(name = "ix_page_view_day", columnList = "view_day"),
        @Index(name = "ix_page_view_path", columnList = "path")})
public class PageView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "view_day", nullable = false)
    private LocalDate viewDay;   // "day" is an HQL keyword; the property is named to stay clear of it

    @Column(nullable = false, length = 200)
    private String path;

    /** Referring host (e.g. {@code news.ycombinator.com}); null for direct visits. */
    @Column(length = 190)
    private String referrer;

    @Column(nullable = false, length = 64)
    private String visitor;

    /** First tag of Accept-Language, e.g. {@code en-US}. */
    @Column(length = 16)
    private String lang;

    @Column(name = "signed_in", nullable = false)
    private boolean signedIn;

    /**
     * ISO 3166-1 alpha-2 code ({@code IN}, {@code US}); {@code ZZ} when the address could not be placed; null for
     * views recorded before countries were (10 October 2026).
     */
    @Column(length = 2)
    private String country;

    protected PageView() {}

    public PageView(LocalDate day, String path, String referrer, String visitor, String lang, boolean signedIn, String country) {
        this.viewDay = day;
        this.path = path;
        this.referrer = referrer;
        this.visitor = visitor;
        this.lang = lang;
        this.signedIn = signedIn;
        this.country = country;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public LocalDate getDay() { return viewDay; }
    public String getPath() { return path; }
    public String getReferrer() { return referrer; }
    public String getVisitor() { return visitor; }
    public String getLang() { return lang; }
    public boolean isSignedIn() { return signedIn; }
    public String getCountry() { return country; }
}
