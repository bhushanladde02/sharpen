package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.PersonService;
import io.sharpen.service.SessionService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/** Puts the signed-in person and their unrated-session count on every page model. */
@ControllerAdvice(basePackages = "io.sharpen.web")
public class GlobalModelAttributes {

    private final PersonService people;
    private final SessionService sessions;
    private final io.sharpen.service.CommunityService community;
    private final io.sharpen.auth.SocialRegistrations social;
    private final io.sharpen.service.InsightsService insights;

    public GlobalModelAttributes(PersonService people, SessionService sessions, io.sharpen.service.CommunityService community,
                                 io.sharpen.auth.SocialRegistrations social, io.sharpen.service.InsightsService insights) {
        this.people = people;
        this.sessions = sessions;
        this.community = community;
        this.social = social;
        this.insights = insights;
    }

    /** Insights is in the menus only once there is something on it (ten people); the footer links it regardless. */
    @ModelAttribute("insightsPublished")
    public boolean insightsPublished() {
        return insights.current().published();
    }

    /** The "Continue with …" providers this deployment offers (empty until a client id and secret are configured). */
    @ModelAttribute("socialProviders")
    public java.util.List<io.sharpen.auth.SocialRegistrations.Provider> socialProviders() {
        return social.providers();
    }

    /** A refused Google/GitHub/LinkedIn sign-in, as a sentence: {@code ?signin_error=<code>&provider=<id>} on login and Settings. */
    @ModelAttribute("signInError")
    public String signInError(jakarta.servlet.http.HttpServletRequest request) {
        String code = request.getParameter("signin_error");
        return code == null ? null : io.sharpen.auth.SignInService.message(code, social.label(request.getParameter("provider")));
    }

    /** Live community counters (cached 10 s) for the shell and the landing page. */
    @ModelAttribute("community")
    public io.sharpen.service.CommunityStats community() {
        return community.stats();
    }

    @ModelAttribute("me")
    public Person me() {
        return people.current().orElse(null);
    }

    @ModelAttribute("path")
    public String path(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI();
    }

    @org.springframework.beans.factory.annotation.Value("${sharpen.demo-data:false}")
    private boolean demoData;

    /** True only when demo accounts are seeded (dev); the login page shows their credentials then. */
    @ModelAttribute("demoData")
    public boolean demoData() {
        return demoData;
    }

    /** The host the visitor used (e.g. {@code sharpen-ai.duckdns.org}), for showing profile URLs. */
    @ModelAttribute("siteHost")
    public String siteHost(jakarta.servlet.http.HttpServletRequest request) {
        String host = request.getHeader("Host");
        return host != null && !host.isBlank() ? host : request.getServerName();
    }

    /** Pages that are personal to the signed-in user must never be indexed, even if a crawler gets a login page. */
    private static final java.util.List<String> PRIVATE_PREFIXES = java.util.List.of(
            "/dashboard", "/sessions", "/import", "/reports", "/settings", "/admin", "/candidates", "/login", "/register", "/feedback", "/error",
            "/check/r/", "/check/result");   // a habits-check result is one person's answers: shareable, not for search

    @ModelAttribute("noindex")
    public boolean noindex(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PRIVATE_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /**
     * The configured public origin ({@code sharpen.site-host}), e.g. {@code https://sharpenscore.com} or
     * {@code http://localhost:8080}. Canonical, og:url and og:image tags are built from this, never from the
     * request's {@code Host} header, so a crafted header cannot change what a crawler is told the page's URL is.
     */
    @org.springframework.beans.factory.annotation.Value("${sharpen.site-host:localhost:8080}")
    private String configuredSiteHost;

    @ModelAttribute("canonicalHost")
    public String canonicalHost() {
        String host = configuredSiteHost == null || configuredSiteHost.isBlank() ? "localhost:8080" : configuredSiteHost.trim();
        boolean local = host.startsWith("localhost") || host.startsWith("127.");
        return (local ? "http://" : "https://") + host;
    }

    /** Absolute, canonical URL of the current page (configured origin + path, no query string) for canonical/og:url tags. */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(jakarta.servlet.http.HttpServletRequest request) {
        return canonicalHost() + request.getRequestURI();
    }

    /** Default description for search engines and link previews; controllers override for specific pages. */
    public static final String DEFAULT_DESCRIPTION =
            "Sharpen is a free, open-source AI profile: track how you work with AI at work and at home, get a monthly "
            + "report and an AI score (0–100) that rewards independence, verification and learning — never volume — "
            + "and share a public profile that shows employers how you actually use AI.";

    @ModelAttribute("pageDescription")
    public String pageDescription() {
        return DEFAULT_DESCRIPTION;
    }

    @org.springframework.beans.factory.annotation.Value("${sharpen.admin-email:}")
    private String adminEmail;

    /** True for the owner's account, which sees the Traffic and Inbox links. */
    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        String admin = adminEmail == null ? "" : adminEmail.trim().toLowerCase(java.util.Locale.ROOT);
        return !admin.isEmpty() && people.current().map(p -> admin.equals(p.getEmail().toLowerCase(java.util.Locale.ROOT))).orElse(false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private io.sharpen.service.NotificationService notifications;

    /** Unread notifications, for the count beside the menu link and on the phone's bell. */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        return people.current().map(notifications::unread).orElse(0L);
    }

    @ModelAttribute("unratedCount")
    public long unratedCount() {
        Optional<Person> me = people.current();
        return me.isPresent() && !me.get().isCompany() ? sessions.countNeedingAssessment(me.get()) : 0;
    }

    /** A month or number that is not one ({@code /reports/garbage}, {@code ?days=abc}) is the caller's mistake, not a crash. */
    @org.springframework.web.bind.annotation.ExceptionHandler({java.time.format.DateTimeParseException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public void badInput(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendError(400);   // rendered by ErrorPageController like any other status
    }
}
