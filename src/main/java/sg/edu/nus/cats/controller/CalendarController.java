package sg.edu.nus.cats.controller;

import java.time.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.service.PagedApplicationService;

@Controller
public class CalendarController {
  private final CourseApplicationRepository applications;
  private final Clock clock;

  public CalendarController(CourseApplicationRepository a, Clock c) {
    applications = a;
    clock = c;
  }

  @GetMapping("/calendar")
  public String calendar(
      @RequestParam(required = false) String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      Model m) {
    var ym = month == null ? YearMonth.now(clock) : YearMonth.parse(month);
    var result =
        applications.calendar(
            ym.atDay(1), ym.atEndOfMonth(), PagedApplicationService.pageable(page, size));
    m.addAttribute("month", ym.toString());
    m.addAttribute(
        "months",
        java.util.stream.IntStream.rangeClosed(-12, 12)
            .mapToObj(i -> ym.plusMonths(i).toString())
            .toList());
    m.addAttribute("applications", result.getContent());
    m.addAttribute("page", result);
    m.addAttribute("pagePath", "/calendar");
    m.addAttribute("previous", ym.minusMonths(1));
    m.addAttribute("next", ym.plusMonths(1));
    return "calendar/month";
  }
}
