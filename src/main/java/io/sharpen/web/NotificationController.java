package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.NotificationService;
import io.sharpen.service.PersonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** {@code /notifications}: the list; opening one marks it read and follows its link; one button marks all read. */
@Controller
public class NotificationController {

    private final NotificationService notifications;
    private final PersonService people;

    public NotificationController(NotificationService notifications, PersonService people) {
        this.notifications = notifications;
        this.people = people;
    }

    @GetMapping("/notifications")
    public String list(Model model) {
        Person me = people.requireCurrent();
        model.addAttribute("items", notifications.latest(me));
        model.addAttribute("unread", notifications.unread(me));
        return "notifications";
    }

    @GetMapping("/notifications/{id}")
    public String open(@PathVariable long id, Model model) {
        return "redirect:" + notifications.open(id, people.requireCurrent()).orElse("/notifications");
    }

    @PostMapping("/notifications/read-all")
    public String readAll() {
        notifications.markAllRead(people.requireCurrent());
        return "redirect:/notifications";
    }
}
