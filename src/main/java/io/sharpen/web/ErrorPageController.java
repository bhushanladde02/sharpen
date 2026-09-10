package io.sharpen.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Replaces Spring Boot's "Whitelabel Error Page" with a Sharpen-styled one that says something useful. */
@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public String error(HttpServletRequest req, Model model) {
        Object statusObj = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusObj instanceof Integer i ? i : 500;
        Object uri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String title, hint;
        switch (status) {
            case 404 -> { title = "Page not found"; hint = "There is nothing at this address. Check the link, or start from the home page."; }
            case 403 -> { title = "That request was refused"; hint = "Usually the page you submitted from was open for a while (or the site restarted) and its security token expired. Go back, reload the page and try again."; }
            case 401 -> { title = "Please sign in"; hint = "You need to be signed in to see this page."; }
            case 405 -> { title = "Wrong kind of request"; hint = "This address does not accept that kind of request."; }
            case 413 -> { title = "File too large"; hint = "The upload is bigger than the site accepts. Try a smaller file."; }
            default -> { title = status >= 500 ? "Something went wrong" : "Request failed"; hint = status >= 500
                    ? "The error has been logged. Please try again in a moment; if it keeps happening, tell us via Feedback."
                    : "The request could not be completed."; }
        }
        model.addAttribute("status", status);
        model.addAttribute("errTitle", title);
        model.addAttribute("errHint", hint);
        model.addAttribute("uri", uri == null ? "" : uri.toString());
        return "error";
    }
}
