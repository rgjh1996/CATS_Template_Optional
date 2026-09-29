package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.DecisionForm;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.service.ApprovalService;

@Controller
@RequestMapping("/manager")
public class ManagerController {
  private final ApprovalService service;
  private final Clock clock;
  private final sg.edu.nus.cats.service.PagedApplicationService pages;

  public ManagerController(
      ApprovalService s, Clock c, sg.edu.nus.cats.service.PagedApplicationService pages) {
    service = s;
    clock = c;
    this.pages = pages;
  }

  @GetMapping("/pending")
  public String pending(
      HttpSession s,
      Model m,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    var id = WebConfig.user(s).id();
    var result = pages.pending(id, page, size);
    var groups =
        new java.util.LinkedHashMap<
            String, java.util.List<sg.edu.nus.cats.model.CourseApplication>>();
    for (var a : result)
      groups
          .computeIfAbsent(
              a.getEmployee().getName() + " (#" + a.getEmployee().getId() + ")",
              k -> new java.util.ArrayList<>())
          .add(a);
    m.addAttribute("groups", groups);
    m.addAttribute("page", result);
    m.addAttribute("pagePath", "/manager/pending");
    m.addAttribute("team", service.getSubordinates(id));
    return "manager/pending";
  }

  @GetMapping("/employees/{id}/history")
  public String history(
      @PathVariable Long id,
      HttpSession s,
      Model m,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    var result = pages.history(WebConfig.user(s).id(), id, true, page, size);
    m.addAttribute("applications", result.getContent());
    m.addAttribute("page", result);
    m.addAttribute("pagePath", "/manager/employees/" + id + "/history");
    m.addAttribute("heading", "Subordinate course history");
    m.addAttribute("managerView", true);
    return "applications/history";
  }

  @GetMapping("/applications/{id}")
  public String detail(@PathVariable Long id, HttpSession s, Model m) {
    context(id, s, m);
    m.addAttribute("form", new DecisionForm());
    return "manager/decision";
  }

  @PostMapping("/applications/{id}/{action:approve|reject}")
  public String decide(
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @ModelAttribute("form") DecisionForm f,
      BindingResult errors,
      HttpSession s,
      Model m,
      RedirectAttributes ra) {
    if (errors.hasErrors()) {
      context(id, s, m);
      return "manager/decision";
    }
    try {
      if (action.equals("approve")) service.approve(WebConfig.user(s).id(), id, f.getReason());
      else service.reject(WebConfig.user(s).id(), id, f.getReason());
      ra.addFlashAttribute("success", "Decision recorded.");
      return "redirect:/manager/applications/" + id;
    } catch (BusinessException e) {
      context(id, s, m);
      m.addAttribute("error", e.getMessage());
      return "manager/decision";
    }
  }

  private void context(Long id, HttpSession s, Model m) {
    var c = service.getApprovalContext(WebConfig.user(s).id(), id);
    m.addAttribute("context", c);
    m.addAttribute("courseApplication", c.application());
  }
}
