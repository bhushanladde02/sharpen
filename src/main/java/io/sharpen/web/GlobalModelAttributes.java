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

    public GlobalModelAttributes(PersonService people, SessionService sessions, io.sharpen.service.CommunityService community) {
        this.people = people;
        this.sessions = sessions;
        this.community = community;
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
            "/dashboard", "/sessions", "/import", "/reports", "/settings", "/admin", "/candidates", "/login", "/register", "/feedback", "/error");

    @ModelAttribute("noindex")
    public boolean noindex(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PRIVATE_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /** Absolute, canonical URL of the current page (https, no query string) for canonical/og:url tags. */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(jakarta.servlet.http.HttpServletRequest request) {
        return "https://" + siteHost(request) + request.getRequestURI();
    }

    /** Default description for search engines and link previews; controllers override for specific pages. */
    public static final String DEFAULT_DESCRIPTION =
            "Sharpen is a free, open-source AI profile: track how you work with AI at work and at home, get a monthly "
            + "report and an AI score (0–1000) that rewards independence, verification and learning — never volume — "
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

    @ModelAttribute("unratedCount")
    public long unratedCount() {
        Optional<Person> me = people.current();
        return me.isPresent() && !me.get().isCompany() ? sessions.countNeedingAssessment(me.get()) : 0;
    }
}
