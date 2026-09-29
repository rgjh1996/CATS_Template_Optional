package sg.edu.nus.cats.service;

import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.dto.form.ApplicationForm;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class CourseApplicationService {
  public static final Set<ApplicationStatus> ACTIVE =
      Set.of(ApplicationStatus.APPLIED, ApplicationStatus.UPDATED, ApplicationStatus.APPROVED);
  public static final Set<ApplicationStatus> PENDING =
      Set.of(ApplicationStatus.APPLIED, ApplicationStatus.UPDATED);
  private final UserRepository users;
  private final CourseApplicationRepository applications;
  private final TrainingCalendarService calendar;
  private final EntitlementService entitlements;
  private final Clock clock;
  private final Validator validator;
  private final CourseRepository courses;
  private final NotificationService notifications;
  private final LedgerService ledger;
  private final CategorySettingsRepository categories;

  public CourseApplicationService(
      UserRepository u,
      CourseApplicationRepository a,
      TrainingCalendarService c,
      EntitlementService e,
      Clock clock,
      Validator validator,
      NotificationService notifications,
      LedgerService ledger,
      CategorySettingsRepository categories,
      CourseRepository courses) {
    users = u;
    applications = a;
    calendar = c;
    entitlements = e;
    this.clock = clock;
    this.validator = validator;
    this.notifications = notifications;
    this.ledger = ledger;
    this.categories = categories;
    this.courses = courses;
  }

  @Transactional
  public CourseApplication submit(Long employeeId, ApplicationForm form) {
    User employee = lockEmployee(employeeId);
    if (employee.getManager() == null)
      throw new BusinessException("A manager must be assigned before you apply.");
    CourseApplication a = new CourseApplication();
    a.setEmployee(employee);
    copyAndValidate(form, a, true);
    a.setStatus(ApplicationStatus.APPLIED);
    validateCapacityAndOverlap(a, null);
    applications.save(a);
    notifications.submitted(a);
    return a;
  }

  @Transactional
  public void update(Long employeeId, Long id, ApplicationForm form) {
    lockEmployee(employeeId);
    CourseApplication a = owned(employeeId, id);
    requirePending(a);
    if (!Objects.equals(a.getVersion(), form.getVersion()))
      throw new BusinessException(
          "This application changed since you opened it. Reload and try again.");
    copyAndValidate(form, a, true);
    validateCapacityAndOverlap(a, id);
    a.setStatus(ApplicationStatus.UPDATED);
  }

  @Transactional
  public void delete(Long employeeId, Long id) {
    lockEmployee(employeeId);
    var a = owned(employeeId, id);
    requirePending(a);
    a.setStatus(ApplicationStatus.DELETED);
  }

  @Transactional
  public void cancel(Long employeeId, Long id) {
    lockEmployee(employeeId);
    var a = owned(employeeId, id);
    requireApproved(a);
    a.setStatus(ApplicationStatus.CANCELLED);
    ledger.post(
        a.getEmployee(),
        a.getStartDate().getYear(),
        LedgerEntryKind.FEE_RELEASE,
        a.getCourseFee().negate(),
        "application-cancel-" + id,
        "Cancelled " + a.getCourseTitle());
  }

  @Transactional
  public void complete(Long employeeId, Long id, String experience) {
    lockEmployee(employeeId);
    var a = owned(employeeId, id);
    requireApproved(a);
    requireComment(experience);
    if (!LocalDate.now(clock).isAfter(a.getEndDate()))
      throw new BusinessException("You can mark attendance only after the course has ended.");
    a.setExperienceComment(experience.trim());
    a.setStatus(ApplicationStatus.COMPLETED);
  }

  @Transactional(readOnly = true)
  public CourseApplication getDetails(Long employeeId, Long id) {
    return owned(employeeId, id);
  }

  @Transactional(readOnly = true)
  public List<CourseApplication> getPersonalHistory(Long employeeId, int year) {
    return applications.history(employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
  }

  User lockEmployee(Long id) {
    return users.lockById(id).orElseThrow(() -> new NotFoundException("Employee not found."));
  }

  CourseApplication owned(Long employeeId, Long id) {
    var a =
        applications.details(id).orElseThrow(() -> new NotFoundException("Application not found."));
    if (!a.getEmployee().getId().equals(employeeId))
      throw new NotFoundException("Application not found.");
    return a;
  }

  public static void requirePending(CourseApplication a) {
    if (!PENDING.contains(a.getStatus()))
      throw new BusinessException("This application is no longer pending.");
  }

  private void requireApproved(CourseApplication a) {
    if (a.getStatus() != ApplicationStatus.APPROVED)
      throw new BusinessException("This action requires an approved application.");
  }

  public static void requireComment(String comment) {
    if (comment == null || comment.isBlank() || comment.length() > 2000)
      throw new BusinessException("Please provide comments of 1 to 2,000 characters.");
  }

  void validateCapacityAndOverlap(CourseApplication a, Long excludeId) {
    if (applications.overlaps(
            a.getEmployee().getId(), a.getStartDate(), a.getEndDate(), ACTIVE, excludeId)
        > 0)
      throw new BusinessException("The course overlaps another pending or approved application.");
    entitlements.checkCapacity(a.getEmployee().getId(), a, excludeId);
  }

  private void copyAndValidate(ApplicationForm f, CourseApplication a, boolean requireFuture) {
    if (!validator.validate(f).isEmpty())
      throw new BusinessException("Please complete all required fields with valid values.");
    if (requireFuture && !f.getStartDate().isAfter(LocalDate.now(clock)))
      throw new BusinessException("The course must start on a future date.");
    if (categories.findById(f.getCategory()).map(c -> !c.getActive()).orElse(false))
      throw new BusinessException("This course category is currently unavailable.");
    if (f.getCategory() == CourseCategory.INTERNAL_TRAINING) {
      if (f.getCourseFee().signum() != 0)
        throw new BusinessException("Internal training must have zero course fee.");
    } else {
      if (f.getCourseFee().signum() <= 0)
        throw new BusinessException("External courses and certifications require a positive fee.");
      if (f.getStartSession() != DaySession.AM || f.getEndSession() != DaySession.PM)
        throw new BusinessException("Only internal training supports half days.");
    }
    var days =
        calendar.daysByYear(
            f.getStartDate(), f.getEndDate(), f.getStartSession(), f.getEndSession());
    if (f.getCatalogueCourseId() != null) {
      var course =
          courses
              .findById(f.getCatalogueCourseId())
              .orElseThrow(() -> new BusinessException("Catalogue course not found."));
      if (!course.getActive() || !course.getProvider().getActive())
        throw new BusinessException("This catalogue course is unavailable.");
      a.setCatalogueCourse(course);
    } else a.setCatalogueCourse(null);
    a.setCourseTitle(f.getCourseTitle().trim());
    a.setTrainingProvider(f.getTrainingProvider().trim());
    a.setCategory(f.getCategory());
    a.setStartDate(f.getStartDate());
    a.setEndDate(f.getEndDate());
    a.setStartSession(f.getStartSession());
    a.setEndSession(f.getEndSession());
    a.setCourseFee(f.getCourseFee());
    a.setJustification(f.getJustification().trim());
    a.setWorkDissemination(f.getWorkDissemination());
    a.setTrainingDays(days.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  public ApplicationForm toForm(CourseApplication a) {
    var f = new ApplicationForm();
    f.setCourseTitle(a.getCourseTitle());
    f.setCatalogueCourseId(a.getCatalogueCourse() == null ? null : a.getCatalogueCourse().getId());
    f.setCategory(a.getCategory());
    f.setTrainingProvider(a.getTrainingProvider());
    f.setStartDate(a.getStartDate());
    f.setEndDate(a.getEndDate());
    f.setStartSession(a.getStartSession());
    f.setEndSession(a.getEndSession());
    f.setCourseFee(a.getCourseFee());
    f.setJustification(a.getJustification());
    f.setWorkDissemination(a.getWorkDissemination());
    f.setVersion(a.getVersion());
    return f;
  }
}
