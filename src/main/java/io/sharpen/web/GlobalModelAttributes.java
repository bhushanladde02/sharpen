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

    public GlobalModelAttributes(PersonService people, SessionService sessions) {
        this.people = people;
        this.sessions = sessions;
    }

    @ModelAttribute("me")
    public Person me() {
        return people.current().orElse(null);
    }

    @ModelAttribute("path")
    public String path(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("unratedCount")
    public long unratedCount() {
        Optional<Person> me = people.current();
        return me.isPresent() && !me.get().isCompany() ? sessions.countNeedingAssessment(me.get()) : 0;
    }
}
