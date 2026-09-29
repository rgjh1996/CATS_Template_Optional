package sg.edu.nus.cats.controller;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.service.*;

@Controller
public class ClaimController {
  private final ClaimService claims;
  private final CourseApplicationService applications;
  private final LedgerService ledger;
  private final EntitlementService entitlements;
  private final Clock clock;

  public ClaimController(
      ClaimService c,
      CourseApplicationService a,
      LedgerService l,
      EntitlementService e,
      Clock clock) {
    claims = c;
    applications = a;
    ledger = l;
    entitlements = e;
    this.clock = clock;
  }

  @GetMapping("/claims")
  public String own(HttpSession s, Model m) {
    m.addAttribute("claims", claims.own(WebConfig.user(s).id()));
    m.addAttribute("managerView", false);
    m.addAttribute("adminView", false);
    return "claims/list";
  }

  @GetMapping("/claims/new")
  public String form(@RequestParam Long applicationId, HttpSession s, Model m) {
    m.addAttribute(
        "courseApplication", applications.getDetails(WebConfig.user(s).id(), applicationId));
    return "claims/form";
  }

  @PostMapping("/claims")
  public String submit(
      HttpSession s,
      @RequestParam Long applicationId,
      @RequestParam(defaultValue = "false") boolean selfPaid,
      @RequestParam MultipartFile receipt,
      @RequestParam MultipartFile certificate,
      RedirectAttributes ra) {
    claims.submit(WebConfig.user(s).id(), applicationId, selfPaid, receipt, certificate);
    ra.addFlashAttribute("success", "Claim submitted with receipt and certificate.");
    return "redirect:/claims";
  }

  @GetMapping("/manager/claims")
  public String team(HttpSession s, Model m) {
    m.addAttribute("claims", claims.team(WebConfig.user(s).id()));
    m.addAttribute("managerView", true);
    m.addAttribute("adminView", false);
    return "claims/list";
  }

  @PostMapping("/manager/claims/{id}/{action:approve|reject}")
  public String decide(
      HttpSession s,
      @PathVariable Long id,
      @PathVariable String action,
      @RequestParam String reason,
      RedirectAttributes ra) {
    claims.decide(WebConfig.user(s).id(), id, reason, action.equals("approve"));
    ra.addFlashAttribute("success", "Claim decision recorded.");
    return "redirect:/manager/claims";
  }

  @GetMapping("/attachments/claims/{id}/{kind:receipt|certificate}")
  @ResponseBody
  public ResponseEntity<byte[]> attachment(
      HttpSession s, @PathVariable Long id, @PathVariable String kind) {
    var d = claims.download(WebConfig.user(s).id(), id, kind);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(d.filename()).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(d.content());
  }

  @GetMapping("/ledger")
  public String ledger(HttpSession s, @RequestParam(required = false) Integer year, Model m) {
    int y = year == null ? LocalDate.now(clock).getYear() : year;
    Long id = WebConfig.user(s).id();
    m.addAttribute("usage", entitlements.getUsage(id, y));
    m.addAttribute("entries", ledger.entries(id, y));
    m.addAttribute("year", y);
    return "claims/ledger";
  }
}
