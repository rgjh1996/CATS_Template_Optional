package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;
import sg.edu.nus.cats.service.*;

@Controller
@RequestMapping("/admin")
public class AdminController {
  private final AdministrationService admin;
  private final UserRepository users;
  private final AnnualEntitlementRepository entitlements;
  private final PublicHolidayRepository holidays;
  private final TrainingProviderRepository providers;
  private final CourseRepository courses;
  private final CategorySettingsRepository categories;
  private final EmailNotificationRepository emails;
  private final FeeClaimRepository claims;
  private final ClaimService claimService;
  private final Clock clock;

  public AdminController(
      AdministrationService a,
      UserRepository u,
      AnnualEntitlementRepository e,
      PublicHolidayRepository h,
      TrainingProviderRepository p,
      CourseRepository c,
      CategorySettingsRepository cat,
      EmailNotificationRepository mail,
      FeeClaimRepository claims,
      ClaimService cs,
      Clock clock) {
    admin = a;
    users = u;
    entitlements = e;
    holidays = h;
    providers = p;
    courses = c;
    categories = cat;
    emails = mail;
    this.claims = claims;
    claimService = cs;
    this.clock = clock;
  }

  @GetMapping
  public String home(
      Model m,
      @RequestParam(required = false) Long editUser,
      @RequestParam(required = false) Long editProvider,
      @RequestParam(required = false) Long editCourse,
      @RequestParam(required = false) Long editHoliday) {
    m.addAttribute("users", users.findAll());
    m.addAttribute("entitlements", entitlements.findAll());
    m.addAttribute("holidays", holidays.findAll());
    m.addAttribute("providers", providers.findAll());
    m.addAttribute("courses", courses.findAll());
    m.addAttribute(
        "categories",
        Arrays.stream(CourseCategory.values())
            .map(
                code ->
                    categories
                        .findById(code)
                        .orElseGet(
                            () -> {
                              var c = new sg.edu.nus.cats.model.CategorySettings();
                              c.setCode(code);
                              c.setLabel(code.name().replace('_', ' '));
                              return c;
                            }))
            .toList());
    m.addAttribute("roleValues", Role.values());
    m.addAttribute("designations", Designation.values());
    m.addAttribute("categoryValues", CourseCategory.values());
    m.addAttribute("year", LocalDate.now(clock).getYear());
    m.addAttribute("editingUser", editUser == null ? null : users.findById(editUser).orElseThrow());
    m.addAttribute(
        "editingProvider",
        editProvider == null ? null : providers.findById(editProvider).orElseThrow());
    m.addAttribute(
        "editingCourse", editCourse == null ? null : courses.findById(editCourse).orElseThrow());
    m.addAttribute(
        "editingHoliday",
        editHoliday == null ? null : holidays.findById(editHoliday).orElseThrow());
    return "admin/home";
  }

  @PostMapping("/users")
  public String user(
      HttpSession s,
      @RequestParam(required = false) Long id,
      @RequestParam String username,
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam(defaultValue = "") String password,
      @RequestParam Set<Role> roles,
      @RequestParam Designation designation,
      @RequestParam(required = false) Long managerId,
      @RequestParam(defaultValue = "false") boolean enabled,
      RedirectAttributes ra) {
    admin.saveUser(
        WebConfig.user(s).id(),
        id,
        username,
        name,
        email,
        password,
        roles,
        designation,
        managerId,
        enabled);
    return success(ra, "User saved.");
  }

  @PostMapping("/users/{id}/delete")
  public String delete(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
    admin.deleteUser(WebConfig.user(s).id(), id);
    return success(ra, "Employee access deleted. Historical records are retained.");
  }

  @PostMapping("/entitlements")
  public String entitlement(
      @RequestParam Long employeeId,
      @RequestParam int year,
      @RequestParam BigDecimal days,
      @RequestParam BigDecimal budget,
      RedirectAttributes ra) {
    admin.setEntitlement(employeeId, year, days, budget);
    return success(ra, "Entitlement updated; the budget adjustment is recorded in the ledger.");
  }

  @PostMapping("/providers")
  public String provider(
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam(defaultValue = "") String description,
      @RequestParam(defaultValue = "false") boolean active,
      RedirectAttributes ra) {
    admin.saveProvider(id, name, description, active);
    return success(ra, "Provider saved.");
  }

  @PostMapping("/categories")
  public String category(
      @RequestParam CourseCategory code,
      @RequestParam String label,
      @RequestParam(defaultValue = "false") boolean active,
      RedirectAttributes ra) {
    admin.saveCategory(code, label, active);
    return success(ra, "Category saved.");
  }

  @PostMapping("/courses")
  public String course(
      @RequestParam(required = false) Long id,
      @RequestParam String title,
      @RequestParam CourseCategory category,
      @RequestParam Long providerId,
      @RequestParam BigDecimal fee,
      @RequestParam(defaultValue = "false") boolean active,
      RedirectAttributes ra) {
    admin.saveCourse(id, title, category, providerId, fee, active);
    return success(ra, "Catalogue course saved.");
  }

  @PostMapping("/holidays")
  public String holiday(
      @RequestParam(required = false) Long id,
      @RequestParam LocalDate date,
      @RequestParam String description,
      RedirectAttributes ra) {
    admin.saveHoliday(id, date, description);
    return success(ra, "Holiday saved.");
  }

  @PostMapping("/holidays/{id}/delete")
  public String deleteHoliday(@PathVariable Long id, RedirectAttributes ra) {
    admin.deleteHoliday(id);
    return success(ra, "Holiday deleted.");
  }

  @GetMapping("/mail")
  public String mail(Model m) {
    m.addAttribute(
        "emails",
        emails.findAll(org.springframework.data.domain.Sort.by("createdAt").descending()));
    return "admin/mail";
  }

  @PostMapping("/mail/{id}/retry")
  public String retry(@PathVariable Long id) {
    var n = emails.findById(id).orElseThrow();
    if (n.getStatus() != EmailStatus.SENT) {
      n.setStatus(EmailStatus.PENDING);
      n.setAttempts(0);
      emails.save(n);
    }
    return "redirect:/admin/mail";
  }

  @GetMapping("/claims")
  public String claims(Model m) {
    m.addAttribute("claims", claims.allDetails());
    m.addAttribute("adminView", true);
    m.addAttribute("managerView", false);
    return "claims/list";
  }

  @PostMapping("/claims/{id}/reimburse")
  public String reimburse(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
    claimService.reimburse(WebConfig.user(s).id(), id);
    ra.addFlashAttribute(
        "success", "Reimbursement recorded. This does not initiate a bank payment.");
    return "redirect:/admin/claims";
  }

  private String success(RedirectAttributes ra, String message) {
    ra.addFlashAttribute("success", message);
    return "redirect:/admin";
  }
}
