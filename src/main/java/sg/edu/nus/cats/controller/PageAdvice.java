package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.config.WebConfig;

@ControllerAdvice
public class PageAdvice {
  private final Clock clock;
  private final sg.edu.nus.cats.repository.CategorySettingsRepository categories;

  public PageAdvice(Clock clock, sg.edu.nus.cats.repository.CategorySettingsRepository categories) {
    this.clock = clock;
    this.categories = categories;
  }

  @ModelAttribute
  public void common(
      HttpSession session, Model model, jakarta.servlet.http.HttpServletRequest request) {
    model.addAttribute("currentUser", WebConfig.user(session));
    model.addAttribute(
        "csrfToken",
        ((org.springframework.security.web.csrf.CsrfToken) request.getAttribute("_csrf"))
            .getToken());
    model.addAttribute("today", LocalDate.now(clock));
    var labels = new java.util.HashMap<sg.edu.nus.cats.model.enums.CourseCategory, String>();
    for (var c : sg.edu.nus.cats.model.enums.CourseCategory.values())
      labels.put(c, c.name().replace('_', ' '));
    for (var c : categories.findAll())
      labels.put(c.getCode(), c.getLabel() + (c.getActive() ? "" : " (unavailable)"));
    model.addAttribute("categoryLabels", labels);
  }
}
