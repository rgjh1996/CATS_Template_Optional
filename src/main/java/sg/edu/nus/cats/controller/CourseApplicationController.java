package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.*;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.service.CourseApplicationService;

@Controller
@RequestMapping("/applications")
public class CourseApplicationController {
  private final CourseApplicationService service;
  private final sg.edu.nus.cats.repository.CourseRepository courses;

  public CourseApplicationController(
      CourseApplicationService service, sg.edu.nus.cats.repository.CourseRepository courses) {
    this.service = service;
    this.courses = courses;
  }

  @ModelAttribute("categories")
  public CourseCategory[] categories() {
    return CourseCategory.values();
  }

  @GetMapping("/new")
  public String form(Model m, @RequestParam(required = false) Long courseId) {
    var f = new ApplicationForm();
    if (courseId != null) {
      var course = courses.findById(courseId).orElseThrow();
      f.setCatalogueCourseId(courseId);
      f.setCourseTitle(course.getTitle());
      f.setTrainingProvider(course.getProvider().getName());
      f.setCategory(course.getCategory());
      f.setCourseFee(course.getDefaultFee());
    }
    m.addAttribute("form", f);
    return "applications/form";
  }

  @PostMapping
  public String submit(
      @Valid @ModelAttribute("form") ApplicationForm form,
      BindingResult errors,
      HttpSession s,
      Model m,
      RedirectAttributes ra) {
    if (errors.hasErrors()) return "applications/form";
    try {
      var a = service.submit(WebConfig.user(s).id(), form);
      ra.addFlashAttribute("success", "Application submitted for approval.");
      return "redirect:/applications/" + a.getId();
    } catch (BusinessException e) {
      m.addAttribute("error", e.getMessage());
      return "applications/form";
    }
  }

  @GetMapping("/{id}")
  public String detail(@PathVariable Long id, HttpSession s, Model m) {
    m.addAttribute("courseApplication", service.getDetails(WebConfig.user(s).id(), id));
    return "applications/detail";
  }

  @GetMapping("/{id}/edit")
  public String edit(@PathVariable Long id, HttpSession s, Model m) {
    var a = service.getDetails(WebConfig.user(s).id(), id);
    CourseApplicationService.requirePending(a);
    m.addAttribute("form", service.toForm(a));
    m.addAttribute("applicationId", id);
    return "applications/form";
  }

  @PostMapping("/{id}/update")
  public String update(
      @PathVariable Long id,
      @Valid @ModelAttribute("form") ApplicationForm form,
      BindingResult errors,
      HttpSession s,
      Model m,
      RedirectAttributes ra) {
    m.addAttribute("applicationId", id);
    if (errors.hasErrors()) return "applications/form";
    try {
      service.update(WebConfig.user(s).id(), id, form);
      ra.addFlashAttribute("success", "Application updated.");
      return "redirect:/applications/" + id;
    } catch (BusinessException e) {
      m.addAttribute("error", e.getMessage());
      return "applications/form";
    }
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
    service.delete(WebConfig.user(s).id(), id);
    ra.addFlashAttribute("success", "Application withdrawn and retained in history.");
    return "redirect:/applications/" + id;
  }

  @PostMapping("/{id}/cancel")
  public String cancel(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
    service.cancel(WebConfig.user(s).id(), id);
    ra.addFlashAttribute("success", "Application cancelled.");
    return "redirect:/applications/" + id;
  }

  @GetMapping("/{id}/complete")
  public String completion(@PathVariable Long id, HttpSession s, Model m) {
    m.addAttribute("courseApplication", service.getDetails(WebConfig.user(s).id(), id));
    m.addAttribute("form", new CompletionForm());
    return "applications/complete";
  }

  @PostMapping("/{id}/complete")
  public String complete(
      @PathVariable Long id,
      @Valid @ModelAttribute("form") CompletionForm f,
      BindingResult errors,
      HttpSession s,
      Model m,
      RedirectAttributes ra) {
    m.addAttribute("courseApplication", service.getDetails(WebConfig.user(s).id(), id));
    if (errors.hasErrors()) return "applications/complete";
    try {
      service.complete(WebConfig.user(s).id(), id, f.getExperience());
      ra.addFlashAttribute(
          "success", "Attendance recorded. Thank you for sharing your experience.");
      return "redirect:/applications/" + id;
    } catch (BusinessException e) {
      m.addAttribute("error", e.getMessage());
      return "applications/complete";
    }
  }
}
