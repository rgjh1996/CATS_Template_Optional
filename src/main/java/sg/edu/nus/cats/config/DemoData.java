package sg.edu.nus.cats.config;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@org.springframework.core.annotation.Order(0)
@Component
@ConditionalOnProperty(name = "cats.seed", havingValue = "true")
public class DemoData implements CommandLineRunner {
  private final UserRepository users;
  private final AnnualEntitlementRepository entitlements;
  private final PublicHolidayRepository holidays;
  private final CourseApplicationRepository applications;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public DemoData(
      UserRepository u,
      AnnualEntitlementRepository e,
      PublicHolidayRepository h,
      CourseApplicationRepository a,
      PasswordEncoder p,
      Clock c) {
    users = u;
    entitlements = e;
    holidays = h;
    applications = a;
    encoder = p;
    clock = c;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    User director = user("carol", "Carol Lim", Set.of(Role.EMPLOYEE, Role.MANAGER), null);
    User manager = user("bob", "Bob Tan", Set.of(Role.EMPLOYEE, Role.MANAGER), director);
    User alice = user("alice", "Alice Goh", Set.of(Role.EMPLOYEE), manager);
    User charlie = user("charlie", "Charlie Lee", Set.of(Role.EMPLOYEE), manager);
    user("admin", "Administrator", Set.of(Role.ADMIN), null);
    int year = LocalDate.now(clock).getYear();
    for (User u : List.of(director, manager, alice, charlie))
      for (int y = year - 1; y <= year + 1; y++) {
        var e = new AnnualEntitlement();
        e.setEmployee(u);
        e.setYear(y);
        e.setTrainingDayLimit(new BigDecimal("10"));
        e.setBudgetLimit(new BigDecimal("2000"));
        entitlements.save(e);
      }
    // Illustrative demo calendar only. Replace with the organisation's full holiday calendar.
    for (int y = year - 1; y <= year + 1; y++)
      for (LocalDate d : List.of(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 25))) {
        var h = new PublicHoliday();
        h.setHolidayDate(d);
        h.setDescription(d.getMonthValue() == 1 ? "New Year's Day" : "Christmas Day");
        holidays.save(h);
      }
    sample(
        alice,
        manager,
        "Completed certification for fee claim",
        weekday(LocalDate.now(clock).minusDays(20)),
        ApplicationStatus.COMPLETED,
        new BigDecimal("200"));
    LocalDate future = weekday(LocalDate.now(clock).plusDays(7));
    sample(
        alice,
        manager,
        "Spring Boot fundamentals",
        future,
        ApplicationStatus.APPLIED,
        new BigDecimal("450"));
    sample(
        charlie,
        manager,
        "Cloud architecture workshop",
        future,
        ApplicationStatus.APPROVED,
        new BigDecimal("300"));
    sample(
        alice,
        manager,
        "Effective team communication",
        weekday(LocalDate.now(clock).minusDays(10)),
        ApplicationStatus.APPROVED,
        BigDecimal.ZERO);
  }

  private User user(String username, String name, Set<Role> roles, User manager) {
    var u = new User();
    u.setUsername(username);
    u.setName(name);
    u.setEmail(username + "@example.test");
    u.setPasswordHash(encoder.encode("DemoPass123!"));
    u.setRoles(new HashSet<>(roles));
    u.setDesignation(Designation.PROFESSIONAL);
    u.setManager(manager);
    return users.save(u);
  }

  private LocalDate weekday(LocalDate date) {
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY
        || date.getDayOfWeek() == DayOfWeek.SUNDAY
        || (date.getMonthValue() == 1 && date.getDayOfMonth() == 1)
        || (date.getMonthValue() == 12 && date.getDayOfMonth() == 25)) date = date.plusDays(1);
    return date;
  }

  private void sample(
      User employee,
      User manager,
      String title,
      LocalDate date,
      ApplicationStatus status,
      BigDecimal fee) {
    var a = new CourseApplication();
    a.setEmployee(employee);
    a.setCourseTitle(title);
    a.setCategory(
        fee.signum() == 0 ? CourseCategory.INTERNAL_TRAINING : CourseCategory.EXTERNAL_COURSE);
    a.setTrainingProvider("Demo Training Academy");
    a.setStartDate(date);
    a.setEndDate(date);
    a.setStartSession(DaySession.AM);
    a.setEndSession(DaySession.PM);
    a.setTrainingDays(BigDecimal.ONE);
    a.setCourseFee(fee);
    a.setJustification("Develop practical skills relevant to my current responsibilities.");
    a.setWorkDissemination("Share notes with the team after attending.");
    a.setStatus(status);
    if (status == ApplicationStatus.COMPLETED) {
      a.setCategory(CourseCategory.PROFESSIONAL_CERTIFICATION);
      a.setExperienceComment("Completed the certification and shared notes with the team.");
    }
    if (status == ApplicationStatus.APPROVED || status == ApplicationStatus.COMPLETED) {
      a.setDecidedBy(manager);
      a.setDecidedAt(LocalDateTime.now(clock));
      a.setManagerComment("Approved. Please share the key learning points with the team.");
    }
    applications.save(a);
  }
}
