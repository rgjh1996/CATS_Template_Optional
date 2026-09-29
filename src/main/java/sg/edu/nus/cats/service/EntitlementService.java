package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.dto.view.AnnualUsage;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.ApplicationStatus;
import sg.edu.nus.cats.repository.*;

@Service
public class EntitlementService {
  private final AnnualEntitlementRepository entitlements;
  private final CourseApplicationRepository applications;
  private final TrainingCalendarService calendar;

  public EntitlementService(
      AnnualEntitlementRepository e, CourseApplicationRepository a, TrainingCalendarService c) {
    entitlements = e;
    applications = a;
    calendar = c;
  }

  @Transactional(readOnly = true)
  public AnnualUsage getUsage(Long employeeId, int year) {
    return usage(employeeId, year, null);
  }

  @Transactional(readOnly = true)
  public boolean configured(Long employeeId, int year) {
    return entitlements.findByEmployeeIdAndYear(employeeId, year).isPresent();
  }

  public AnnualUsage usage(Long employeeId, int year, Long excludeId) {
    AnnualEntitlement e =
        entitlements
            .findByEmployeeIdAndYear(employeeId, year)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "No training entitlement configured for "
                            + year
                            + ". Contact your administrator."));
    BigDecimal cd = BigDecimal.ZERO,
        pd = BigDecimal.ZERO,
        cf = BigDecimal.ZERO,
        pf = BigDecimal.ZERO;
    for (CourseApplication a :
        applications.history(employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
      if (Objects.equals(a.getId(), excludeId)) continue;
      boolean pending =
          a.getStatus() == ApplicationStatus.APPLIED || a.getStatus() == ApplicationStatus.UPDATED;
      boolean committed =
          a.getStatus() == ApplicationStatus.APPROVED
              || a.getStatus() == ApplicationStatus.COMPLETED;
      if (!pending && !committed) continue;
      BigDecimal days =
          calendar
              .daysByYear(a.getStartDate(), a.getEndDate(), a.getStartSession(), a.getEndSession())
              .getOrDefault(year, BigDecimal.ZERO);
      BigDecimal fee = a.getStartDate().getYear() == year ? a.getCourseFee() : BigDecimal.ZERO;
      if (pending) {
        pd = pd.add(days);
        pf = pf.add(fee);
      } else {
        cd = cd.add(days);
        cf = cf.add(fee);
      }
    }
    return new AnnualUsage(year, e.getTrainingDayLimit(), e.getBudgetLimit(), cd, pd, cf, pf);
  }

  public void checkCapacity(Long employeeId, CourseApplication proposed, Long excludeId) {
    var byYear =
        calendar.daysByYear(
            proposed.getStartDate(),
            proposed.getEndDate(),
            proposed.getStartSession(),
            proposed.getEndSession());
    for (var entry : byYear.entrySet()) {
      AnnualUsage usage = usage(employeeId, entry.getKey(), excludeId);
      if (entry.getValue().compareTo(usage.remainingDays()) > 0)
        throw new BusinessException("Insufficient training days for " + entry.getKey() + ".");
      if (entry.getKey() == proposed.getStartDate().getYear()
          && proposed.getCourseFee().compareTo(usage.remainingBudget()) > 0)
        throw new BusinessException("The fee exceeds your remaining training budget.");
    }
  }
}
