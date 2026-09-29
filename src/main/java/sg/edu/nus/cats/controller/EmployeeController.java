package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.service.*;

@Controller
public class EmployeeController {
  private final CourseApplicationService applications;
  private final EntitlementService entitlements;
  private final Clock clock;
  private final PagedApplicationService pages;

  public EmployeeController(
      CourseApplicationService a, EntitlementService e, Clock c, PagedApplicationService pages) {
    applications = a;
    entitlements = e;
    clock = c;
    this.pages = pages;
  }

  @GetMapping("/employee")
  public String dashboard(HttpSession s, Model m) {
    int year = LocalDate.now(clock).getYear();
    Long id = WebConfig.user(s).id();
    m.addAttribute("usage", entitlements.getUsage(id, year));
    m.addAttribute("applications", applications.getPersonalHistory(id, year));
    return "employee/dashboard";
  }

  @GetMapping("/applications")
  public String history(
      HttpSession s,
      Model m,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
    var result = pages.history(WebConfig.user(s).id(), WebConfig.user(s).id(), false, page, size);
    m.addAttribute("applications", result.getContent());
    m.addAttribute("page", result);
    m.addAttribute("pagePath", "/applications");
    m.addAttribute("heading", "My course history");
    m.addAttribute("managerView", false);
    return "applications/history";
  }
}
