package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.ApplicationForm;
import sg.edu.nus.cats.model.CourseApplication;
import sg.edu.nus.cats.service.*;

@RestController
@RequestMapping("/api/applications")
public class ApplicationRestController {
  public record Summary(
      Long id,
      String title,
      String category,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal fee,
      String status) {
    static Summary of(CourseApplication a) {
      return new Summary(
          a.getId(),
          a.getCourseTitle(),
          a.getCategory().name(),
          a.getStartDate(),
          a.getEndDate(),
          a.getCourseFee(),
          a.getStatus().name());
    }
  }

  public record ApplicationPage(
      java.util.List<Summary> content,
      int number,
      int size,
      long totalElements,
      int totalPages,
      boolean first,
      boolean last) {
    static ApplicationPage of(Page<CourseApplication> p) {
      return new ApplicationPage(
          p.getContent().stream().map(Summary::of).toList(),
          p.getNumber(),
          p.getSize(),
          p.getTotalElements(),
          p.getTotalPages(),
          p.isFirst(),
          p.isLast());
    }
  }

  private final CourseApplicationService applications;
  private final PagedApplicationService pages;

  public ApplicationRestController(CourseApplicationService a, PagedApplicationService p) {
    applications = a;
    pages = p;
  }

  @GetMapping
  public ApplicationPage list(
      HttpSession s,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Long id = WebConfig.user(s).id();
    return ApplicationPage.of(pages.history(id, id, false, page, size));
  }

  @GetMapping("/{id}")
  public Summary detail(HttpSession s, @PathVariable Long id) {
    return Summary.of(applications.getDetails(WebConfig.user(s).id(), id));
  }

  @PostMapping
  public ResponseEntity<Summary> submit(HttpSession s, @Valid @RequestBody ApplicationForm form) {
    var a = applications.submit(WebConfig.user(s).id(), form);
    return ResponseEntity.created(java.net.URI.create("/api/applications/" + a.getId()))
        .body(Summary.of(a));
  }
}
