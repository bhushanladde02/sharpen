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

    /** The host the visitor used (e.g. {@code sharpen-ai.duckdns.org}), for showing profile URLs. */
    @ModelAttribute("siteHost")
    public String siteHost(jakarta.servlet.http.HttpServletRequest request) {
        String host = request.getHeader("Host");
        return host != null && !host.isBlank() ? host : request.getServerName();
    }

    @ModelAttribute("unratedCount")
    public long unratedCount() {
        Optional<Person> me = people.current();
        return me.isPresent() && !me.get().isCompany() ? sessions.countNeedingAssessment(me.get()) : 0;
    }
}
