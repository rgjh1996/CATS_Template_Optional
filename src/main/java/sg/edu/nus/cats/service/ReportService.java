package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.dto.view.AnnualUsage;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class ReportService {
  private final UserRepository users;
  private final CourseApplicationRepository applications;
  private final FeeClaimRepository claims;
  private final EntitlementService entitlements;
  private final LedgerService ledger;

  public ReportService(
      UserRepository u,
      CourseApplicationRepository a,
      FeeClaimRepository c,
      EntitlementService e,
      LedgerService l) {
    users = u;
    applications = a;
    claims = c;
    entitlements = e;
    ledger = l;
  }

  public record BudgetRow(
      String employee, AnnualUsage usage, BigDecimal reimbursed, boolean configured) {}

  public record Report(
      List<CourseApplication> attendance, List<FeeClaim> claims, List<BudgetRow> budgets) {}

  @Transactional(readOnly = true)
  public Report report(
      Long managerId, Long employeeId, LocalDate from, LocalDate to, CourseCategory category) {
    if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusYears(5)))
      throw new BusinessException("Choose an ordered reporting period of at most five years.");
    var manager = users.findById(managerId).orElseThrow();
    if (!manager.hasRole(Role.MANAGER)) throw new BusinessException("Manager access required.");
    var team = users.findByManagerIdOrderByName(managerId);
    if (employeeId != null && team.stream().noneMatch(u -> u.getId().equals(employeeId)))
      throw new NotFoundException("Employee not found in your team.");
    List<CourseApplication> attendance = new ArrayList<>();
    List<FeeClaim> feeClaims = new ArrayList<>();
    List<BudgetRow> budgets = new ArrayList<>();
    for (var u : team) {
      if (employeeId != null && !u.getId().equals(employeeId)) continue;
      applications.history(u.getId(), from, to).stream()
          .filter(
              a ->
                  a.getStatus() == ApplicationStatus.APPROVED
                      || a.getStatus() == ApplicationStatus.COMPLETED)
          .filter(a -> category == null || a.getCategory() == category)
          .forEach(attendance::add);
      claims.own(u.getId()).stream()
          .filter(
              c ->
                  !c.getSubmittedAt().toLocalDate().isBefore(from)
                      && !c.getSubmittedAt().toLocalDate().isAfter(to))
          .filter(c -> category == null || c.getApplication().getCategory() == category)
          .forEach(feeClaims::add);
      for (int year = from.getYear(); year <= to.getYear(); year++) {
        if (entitlements.configured(u.getId(), year)) {
          var usage = entitlements.getUsage(u.getId(), year);
          var reimbursed =
              ledger.entries(u.getId(), year).stream()
                  .filter(e -> e.getKind() == LedgerEntryKind.REIMBURSEMENT)
                  .map(LedgerEntry::getAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          budgets.add(new BudgetRow(u.getName(), usage, reimbursed, true));
        } else {
          budgets.add(
              new BudgetRow(
                  u.getName(),
                  new AnnualUsage(
                      year,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO),
                  BigDecimal.ZERO,
                  false));
        }
      }
    }
    return new Report(attendance, feeClaims, budgets);
  }

  public String csv(Report r, String kind) {
    StringBuilder out = new StringBuilder();
    switch (kind) {
      case "attendance" -> {
        row(out, "Employee", "Course", "Category", "Start", "End", "Days", "Fee", "Status");
        for (var a : r.attendance())
          row(
              out,
              a.getEmployee().getName(),
              a.getCourseTitle(),
              a.getCategory(),
              a.getStartDate(),
              a.getEndDate(),
              a.getTrainingDays(),
              a.getCourseFee(),
              a.getStatus());
      }
      case "claims" -> {
        row(out, "Employee", "Course", "Submitted", "Amount", "Status", "Decision reason");
        for (var c : r.claims())
          row(
              out,
              c.getApplication().getEmployee().getName(),
              c.getApplication().getCourseTitle(),
              c.getSubmittedAt(),
              c.getAmount(),
              c.getStatus(),
              c.getReason());
      }
      case "budget" -> {
        row(
            out,
            "Employee",
            "Configured",
            "Year",
            "Day limit",
            "Committed days",
            "Pending days",
            "Budget",
            "Committed fees",
            "Pending fees",
            "Reimbursed");
        for (var b : r.budgets()) {
          var u = b.usage();
          row(
              out,
              b.employee(),
              b.configured() ? "Yes" : "Missing entitlement",
              u.year(),
              u.dayLimit(),
              u.committedDays(),
              u.pendingDays(),
              u.budgetLimit(),
              u.committedFees(),
              u.pendingFees(),
              b.reimbursed());
        }
      }
      default -> throw new BusinessException("Unknown report type.");
    }
    return out.toString();
  }

  private void row(StringBuilder out, Object... values) {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) out.append(',');
      String s = Objects.toString(values[i], "");
      if (s.stripLeading().matches("(?s)^[=+@-].*")) s = "'" + s;
      out.append('"').append(s.replace("\"", "\"\"")).append('"');
    }
    out.append("\r\n");
  }
}
