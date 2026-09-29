package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.model.enums.CourseCategory;
import sg.edu.nus.cats.service.*;

@Controller
@RequestMapping("/manager/reports")
public class ReportController {
  private final ReportService reports;
  private final ApprovalService approvals;
  private final Clock clock;

  public ReportController(ReportService r, ApprovalService a, Clock c) {
    reports = r;
    approvals = a;
    clock = c;
  }

  @GetMapping
  public String report(
      HttpSession s,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long employeeId,
      @RequestParam(required = false) CourseCategory category,
      Model m) {
    if (from == null) from = LocalDate.now(clock).withDayOfYear(1);
    if (to == null) to = LocalDate.now(clock).withMonth(12).withDayOfMonth(31);
    var id = WebConfig.user(s).id();
    m.addAttribute("report", reports.report(id, employeeId, from, to, category));
    m.addAttribute("team", approvals.getSubordinates(id));
    m.addAttribute("from", from);
    m.addAttribute("to", to);
    m.addAttribute("employeeId", employeeId);
    m.addAttribute("category", category);
    m.addAttribute("categories", CourseCategory.values());
    return "manager/reports";
  }

  @GetMapping("/{kind}.csv")
  @ResponseBody
  public ResponseEntity<String> csv(
      HttpSession s,
      @PathVariable String kind,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long employeeId,
      @RequestParam(required = false) CourseCategory category) {
    var report = reports.report(WebConfig.user(s).id(), employeeId, from, to, category);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cats-" + kind + ".csv\"")
        .body(reports.csv(report, kind));
  }
}
