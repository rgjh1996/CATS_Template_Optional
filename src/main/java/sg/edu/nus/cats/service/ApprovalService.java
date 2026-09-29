package sg.edu.nus.cats.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.dto.view.*;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class ApprovalService {
  private final UserRepository users;
  private final CourseApplicationRepository applications;
  private final EntitlementService entitlements;
  private final CourseApplicationService applicationService;
  private final Clock clock;
  private final LedgerService ledger;
  private final NotificationService notifications;

  public ApprovalService(
      UserRepository u,
      CourseApplicationRepository a,
      EntitlementService e,
      CourseApplicationService s,
      Clock c,
      LedgerService ledger,
      NotificationService notifications) {
    users = u;
    applications = a;
    entitlements = e;
    applicationService = s;
    clock = c;
    this.ledger = ledger;
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public Map<String, List<CourseApplication>> getPendingBySubordinate(Long managerId) {
    requireManager(managerId);
    Map<String, List<CourseApplication>> grouped = new LinkedHashMap<>();
    for (var a : applications.pending(managerId, CourseApplicationService.PENDING))
      grouped
          .computeIfAbsent(
              a.getEmployee().getName() + " (#" + a.getEmployee().getId() + ")",
              k -> new ArrayList<>())
          .add(a);
    return grouped;
  }

  @Transactional(readOnly = true)
  public List<User> getSubordinates(Long managerId) {
    requireManager(managerId);
    return users.findByManagerIdOrderByName(managerId);
  }

  @Transactional(readOnly = true)
  public List<CourseApplication> getSubordinateHistory(Long managerId, Long employeeId, int year) {
    requireManager(managerId);
    var employee =
        users.findById(employeeId).orElseThrow(() -> new NotFoundException("Employee not found."));
    requireSubordinate(managerId, employee);
    return applications.history(employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
  }

  @Transactional(readOnly = true)
  public ApprovalContext getApprovalContext(Long managerId, Long id) {
    requireManager(managerId);
    var a = load(id);
    requireSubordinate(managerId, a.getEmployee());
    Map<Integer, AnnualUsage> usage = new TreeMap<>();
    // Always display current-year usage, as required, plus any course years.
    Set<Integer> years = new TreeSet<>();
    years.add(LocalDate.now(clock).getYear());
    for (int y = a.getStartDate().getYear(); y <= a.getEndDate().getYear(); y++) years.add(y);
    for (int y : years) usage.put(y, entitlements.getUsage(a.getEmployee().getId(), y));
    return new ApprovalContext(
        a,
        usage,
        applications.teamOverlaps(
            managerId,
            a.getEmployee().getId(),
            a.getStartDate(),
            a.getEndDate(),
            ApplicationStatus.APPROVED));
  }

  @Transactional
  public void approve(Long managerId, Long id, String reason) {
    decide(managerId, id, reason, true);
  }

  @Transactional
  public void reject(Long managerId, Long id, String reason) {
    decide(managerId, id, reason, false);
  }

  private void decide(Long managerId, Long id, String reason, boolean approve) {
    User manager = requireManager(managerId);
    // Obtain employee id without loading the application into the persistence context before
    // locking.
    Long employeeId = applicationsEmployeeId(id);
    applicationService.lockEmployee(employeeId);
    var a = load(id);
    requireSubordinate(managerId, a.getEmployee());
    CourseApplicationService.requirePending(a);
    CourseApplicationService.requireComment(reason);
    if (approve) applicationService.validateCapacityAndOverlap(a, id);
    a.setStatus(approve ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED);
    a.setManagerComment(reason.trim());
    a.setDecidedBy(manager);
    a.setDecidedAt(LocalDateTime.now(clock));
    if (approve)
      ledger.post(
          a.getEmployee(),
          a.getStartDate().getYear(),
          LedgerEntryKind.FEE_APPROVED,
          a.getCourseFee(),
          "application-approved-" + id,
          "Approved " + a.getCourseTitle());
    notifications.decided(a);
  }

  private Long applicationsEmployeeId(Long id) {
    return applications
        .employeeId(id)
        .orElseThrow(() -> new NotFoundException("Application not found."));
  }

  private CourseApplication load(Long id) {
    return applications
        .details(id)
        .orElseThrow(() -> new NotFoundException("Application not found."));
  }

  private User requireManager(Long id) {
    var u = users.findById(id).orElseThrow(() -> new NotFoundException("Manager not found."));
    if (!u.hasRole(Role.MANAGER)) throw new BusinessException("Manager access required.");
    return u;
  }

  private void requireSubordinate(Long id, User u) {
    if (u.getManager() == null || !u.getManager().getId().equals(id) || u.getId().equals(id))
      throw new NotFoundException("Subordinate application not found.");
  }
}
