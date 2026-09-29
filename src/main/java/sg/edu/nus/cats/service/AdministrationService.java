package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class AdministrationService {
  private final CourseApplicationRepository applications;
  private final UserRepository users;
  private final AnnualEntitlementRepository entitlements;
  private final PublicHolidayRepository holidays;
  private final TrainingProviderRepository providers;
  private final CourseRepository courses;
  private final CategorySettingsRepository categories;
  private final PasswordEncoder encoder;
  private final LedgerService ledger;
  private final Clock clock;

  public AdministrationService(
      UserRepository u,
      AnnualEntitlementRepository e,
      PublicHolidayRepository h,
      TrainingProviderRepository p,
      CourseRepository c,
      CategorySettingsRepository cat,
      PasswordEncoder encoder,
      LedgerService ledger,
      Clock clock,
      CourseApplicationRepository applications) {
    this.applications = applications;
    users = u;
    entitlements = e;
    holidays = h;
    providers = p;
    courses = c;
    categories = cat;
    this.encoder = encoder;
    this.ledger = ledger;
    this.clock = clock;
  }

  @Transactional
  public User saveUser(
      Long actorId,
      Long id,
      String username,
      String name,
      String email,
      String password,
      Set<Role> roles,
      Designation designation,
      Long managerId,
      boolean enabled) {
    if (username == null
        || !username.matches("[A-Za-z0-9._-]{3,60}")
        || name == null
        || name.isBlank()
        || name.length() > 120
        || email == null
        || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || email.length() > 254
        || roles == null
        || roles.isEmpty()
        || designation == null)
      throw new BusinessException(
          "Provide a valid username, name, email, designation and at least one role.");
    var duplicate = users.findByUsername(username);
    if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), id))
      throw new BusinessException("Username already exists.");
    User u =
        id == null
            ? new User()
            : users.lockById(id).orElseThrow(() -> new NotFoundException("User not found."));
    if (Objects.equals(actorId, id) && (!enabled || !roles.contains(Role.ADMIN)))
      throw new BusinessException("You cannot disable your own administrator access.");
    if (id != null
        && (!enabled || !roles.contains(Role.MANAGER))
        && !users.findByManagerIdOrderByName(id).isEmpty())
      throw new BusinessException("Reassign this manager's reporting employees first.");
    User manager =
        managerId == null
            ? null
            : users
                .findById(managerId)
                .orElseThrow(() -> new BusinessException("Manager not found."));
    if (manager != null && (!manager.getEnabled() || !manager.hasRole(Role.MANAGER)))
      throw new BusinessException("Choose an active manager.");
    Set<Long> seen = new HashSet<>();
    for (User cursor = manager; cursor != null; cursor = cursor.getManager()) {
      if (Objects.equals(cursor.getId(), id) || !seen.add(cursor.getId()))
        throw new BusinessException("Approval hierarchy must not contain a cycle.");
    }
    if (id == null || password != null && !password.isBlank()) {
      if (password == null || password.length() < 10 || password.length() > 72)
        throw new BusinessException("Use a password of 10 to 72 characters.");
      u.setPasswordHash(encoder.encode(password));
    }
    u.setUsername(username);
    u.setName(name.trim());
    u.setEmail(email.trim());
    u.setRoles(new HashSet<>(roles));
    u.setDesignation(designation);
    u.setManager(manager);
    u.setEnabled(enabled);
    users.save(u);
    int year = LocalDate.now(clock).getYear();
    if (id == null && !roles.equals(Set.of(Role.ADMIN)))
      setEntitlement(
          u.getId(),
          year,
          designation == Designation.ADMINISTRATIVE ? new BigDecimal("5") : new BigDecimal("10"),
          new BigDecimal("2000"));
    return u;
  }

  @Transactional
  public void deleteUser(Long actorId, Long id) {
    var u = users.lockById(id).orElseThrow(() -> new NotFoundException("User not found."));
    if (id.equals(actorId)) throw new BusinessException("You cannot delete your own account.");
    if (!users.findByManagerIdOrderByName(id).isEmpty())
      throw new BusinessException("Reassign reporting employees before deleting this manager.");
    u.setEnabled(false);
  }

  @Transactional
  public void setEntitlement(Long employeeId, int year, BigDecimal days, BigDecimal budget) {
    if (year < 2000
        || year > 2100
        || days == null
        || budget == null
        || days.signum() < 0
        || budget.signum() < 0
        || days.remainder(new BigDecimal("0.5")).signum() != 0
        || budget.scale() > 2)
      throw new BusinessException("Enter a valid year, half-day allowance and nonnegative budget.");
    var u =
        users.lockById(employeeId).orElseThrow(() -> new NotFoundException("Employee not found."));
    var e =
        entitlements
            .findByEmployeeIdAndYear(employeeId, year)
            .orElseGet(
                () -> {
                  var item = new AnnualEntitlement();
                  item.setEmployee(u);
                  item.setYear(year);
                  return item;
                });
    BigDecimal previous = e.getBudgetLimit() == null ? BigDecimal.ZERO : e.getBudgetLimit();
    e.setTrainingDayLimit(days);
    e.setBudgetLimit(budget);
    entitlements.save(e);
    ledger.post(
        u,
        year,
        LedgerEntryKind.BUDGET_ADJUSTMENT,
        budget.subtract(previous),
        "budget-" + UUID.randomUUID(),
        "Annual budget set to " + budget);
  }

  @Transactional
  public void saveProvider(Long id, String name, String description, boolean active) {
    if (name == null || name.isBlank() || name.length() > 200)
      throw new BusinessException("Provider name is required (maximum 200 characters).");
    var p =
        id == null
            ? new TrainingProvider()
            : providers
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Provider not found."));
    p.setName(name.trim());
    p.setDescription(description);
    p.setActive(active);
    providers.save(p);
  }

  @Transactional
  public void saveCategory(CourseCategory code, String label, boolean active) {
    if (code == null || label == null || label.isBlank() || label.length() > 100)
      throw new BusinessException("Category and label are required.");
    var c = categories.findById(code).orElseGet(CategorySettings::new);
    c.setCode(code);
    c.setLabel(label);
    c.setActive(active);
    categories.save(c);
  }

  @Transactional
  public void saveCourse(
      Long id,
      String title,
      CourseCategory category,
      Long providerId,
      BigDecimal fee,
      boolean active) {
    if (title == null
        || title.isBlank()
        || title.length() > 200
        || category == null
        || fee == null
        || fee.signum() < 0
        || fee.scale() > 2) throw new BusinessException("Provide valid course details and fee.");
    if (category == CourseCategory.INTERNAL_TRAINING ? fee.signum() != 0 : fee.signum() <= 0)
      throw new BusinessException(
          "Internal training must be free; external courses/certifications require a positive"
              + " fee.");
    var provider =
        providers
            .findById(providerId)
            .orElseThrow(() -> new BusinessException("Provider not found."));
    var c =
        id == null
            ? new Course()
            : courses.findById(id).orElseThrow(() -> new NotFoundException("Course not found."));
    c.setTitle(title.trim());
    c.setCategory(category);
    c.setProvider(provider);
    c.setDefaultFee(fee);
    c.setActive(active);
    courses.save(c);
  }

  @Transactional
  public void saveHoliday(Long id, LocalDate date, String description) {
    if (date == null || description == null || description.isBlank())
      throw new BusinessException("Holiday date and description are required.");
    var h =
        id == null
            ? new PublicHoliday()
            : holidays.findById(id).orElseThrow(() -> new NotFoundException("Holiday not found."));
    if (!date.equals(h.getHolidayDate())) {
      checkHolidayChange(date);
      if (h.getHolidayDate() != null) checkHolidayChange(h.getHolidayDate());
    }
    h.setHolidayDate(date);
    h.setDescription(description);
    holidays.save(h);
  }

  @Transactional
  public void deleteHoliday(Long id) {
    var h = holidays.findById(id).orElseThrow(() -> new NotFoundException("Holiday not found."));
    checkHolidayChange(h.getHolidayDate());
    holidays.delete(h);
  }

  private void checkHolidayChange(LocalDate date) {
    if (applications.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusIn(
        date,
        date,
        Set.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.UPDATED,
            ApplicationStatus.APPROVED,
            ApplicationStatus.COMPLETED)))
      throw new BusinessException(
          "This date overlaps a pending, approved or completed course. Calendar changes cannot"
              + " alter its training-day accounting.");
  }
}
