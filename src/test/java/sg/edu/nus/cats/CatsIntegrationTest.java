package sg.edu.nus.cats;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.cats.config.WebConfig;
import sg.edu.nus.cats.dto.form.ApplicationForm;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;
import sg.edu.nus.cats.service.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${CATS_TEST_DB_URL:jdbc:h2:mem:cats-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
      "spring.datasource.username=${CATS_TEST_DB_USER:sa}",
      "spring.datasource.password=${CATS_TEST_DB_PASSWORD:}",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "cats.seed=false"
    })
@AutoConfigureMockMvc
class CatsIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneId.of("Asia/Singapore"));
    }
  }

  @Autowired UserRepository users;
  @Autowired CourseApplicationRepository applications;
  @Autowired AnnualEntitlementRepository entitlementRepo;
  @Autowired PublicHolidayRepository holidays;
  @Autowired CourseApplicationService service;
  @Autowired ApprovalService approval;
  @Autowired EntitlementService entitlements;
  @Autowired TrainingCalendarService calendar;
  @Autowired AuthenticationService authentication;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  @Autowired FeeClaimRepository claims;
  @Autowired ClaimAttachmentRepository attachments;
  @Autowired LedgerEntryRepository ledgerEntries;
  @Autowired TrainingLedgerRepository ledgers;
  @Autowired EmailNotificationRepository emails;
  @Autowired CourseRepository courses;
  @Autowired TrainingProviderRepository providers;
  @Autowired CategorySettingsRepository categories;
  @Autowired ClaimService claimService;
  @Autowired AdministrationService administration;
  @Autowired ReportService reports;
  User alice, bob, charlie, other, admin;

  @BeforeEach
  void setup() {
    claims.deleteAll();
    attachments.deleteAll();
    ledgerEntries.deleteAll();
    ledgers.deleteAll();
    emails.deleteAll();
    applications.deleteAll();
    courses.deleteAll();
    providers.deleteAll();
    categories.deleteAll();
    entitlementRepo.deleteAll();
    holidays.deleteAll();
    for (var u : users.findAll()) {
      u.setManager(null);
      users.save(u);
    }
    users.deleteAll();
    bob = user("bob", Set.of(Role.EMPLOYEE, Role.MANAGER), null);
    other = user("other", Set.of(Role.EMPLOYEE, Role.MANAGER), null);
    alice = user("alice", Set.of(Role.EMPLOYEE), bob);
    charlie = user("charlie", Set.of(Role.EMPLOYEE), bob);
    admin = user("admin", Set.of(Role.ADMIN), null);
    for (var u : List.of(alice, bob, charlie, other))
      for (int year : List.of(2026, 2027)) {
        var e = new AnnualEntitlement();
        e.setEmployee(u);
        e.setYear(year);
        e.setBudgetLimit(new BigDecimal("2000"));
        e.setTrainingDayLimit(new BigDecimal("10"));
        entitlementRepo.save(e);
      }
  }

  User user(String name, Set<Role> roles, User manager) {
    var u = new User();
    u.setUsername(name);
    u.setName(name);
    u.setEmail(name + "@example.test");
    u.setRoles(new HashSet<>(roles));
    u.setManager(manager);
    u.setDesignation(Designation.PROFESSIONAL);
    u.setPasswordHash(encoder.encode("DemoPass123!"));
    return users.save(u);
  }

  ApplicationForm form(String start, String end, String fee) {
    var f = new ApplicationForm();
    f.setCourseTitle("Java training");
    f.setTrainingProvider("ISS");
    f.setCategory(CourseCategory.EXTERNAL_COURSE);
    f.setStartDate(LocalDate.parse(start));
    f.setEndDate(LocalDate.parse(end));
    f.setCourseFee(new BigDecimal(fee));
    f.setJustification("Improve application design.");
    return f;
  }

  MockHttpSession session(User u) {
    var s = new MockHttpSession();
    s.setAttribute(
        "user", new WebConfig.SessionUser(u.getId(), u.getName(), Set.copyOf(u.getRoles())));
    var context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(
                u.getUsername(),
                null,
                u.getRoles().stream()
                    .map(
                        r ->
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + r))
                    .toList()));
    s.setAttribute("SPRING_SECURITY_CONTEXT", context);
    return s;
  }

  @Test
  void submitUpdateApproveCancelAndRelease() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-08", "600"));
    assertThat(a.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("1400");
    var f = service.toForm(service.getDetails(alice.getId(), a.getId()));
    f.setCourseFee(new BigDecimal("700"));
    service.update(alice.getId(), a.getId(), f);
    assertThat(service.getDetails(alice.getId(), a.getId()).getStatus())
        .isEqualTo(ApplicationStatus.UPDATED);
    approval.approve(bob.getId(), a.getId(), "Relevant skills.");
    assertThat(entitlements.getUsage(alice.getId(), 2026).committedFees())
        .isEqualByComparingTo("700");
    assertThatThrownBy(() -> service.delete(alice.getId(), a.getId()))
        .isInstanceOf(BusinessException.class);
    service.cancel(alice.getId(), a.getId());
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("2000");
  }

  @Test
  void overlapAndBudgetFailuresLeaveNoRows() {
    service.submit(alice.getId(), form("2026-09-07", "2026-09-08", "1500"));
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-08", "2026-09-09", "100")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("overlaps");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-10", "2026-09-10", "501")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("budget");
    assertThat(applications.count()).isEqualTo(1);
    service.submit(charlie.getId(), form("2026-09-07", "2026-09-08", "1500"));
    assertThat(applications.count()).isEqualTo(2);
  }

  @Test
  void calendarCountsWorkingDaysAndHalfDays() {
    var holiday = new PublicHoliday();
    holiday.setHolidayDate(LocalDate.parse("2026-09-08"));
    holiday.setDescription("Test holiday");
    holidays.save(holiday);
    assertThat(
            calendar
                .daysByYear(
                    LocalDate.parse("2026-09-04"),
                    LocalDate.parse("2026-09-09"),
                    DaySession.AM,
                    DaySession.PM)
                .get(2026))
        .isEqualByComparingTo("3");
    var f = form("2026-09-07", "2026-09-07", "0");
    f.setCategory(CourseCategory.INTERNAL_TRAINING);
    f.setEndSession(DaySession.AM);
    assertThat(service.submit(alice.getId(), f).getTrainingDays()).isEqualByComparingTo("0.5");
    assertThatThrownBy(
            () ->
                calendar.daysByYear(f.getStartDate(), f.getEndDate(), DaySession.PM, DaySession.AM))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                calendar.daysByYear(
                    LocalDate.parse("2026-09-08"),
                    LocalDate.parse("2026-09-09"),
                    DaySession.AM,
                    DaySession.PM))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void invalidCategoryDatesAndEntitlement() {
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-01", "2026-09-01", "1")))
        .hasMessageContaining("future");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-05", "2026-09-07", "1")))
        .hasMessageContaining("working");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-10", "2026-09-09", "1")))
        .hasMessageContaining("ordered");
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-07", "2026-09-25", "1")))
        .hasMessageContaining("training days");
    var f = form("2026-09-07", "2026-09-07", "10");
    f.setEndSession(DaySession.AM);
    assertThatThrownBy(() -> service.submit(alice.getId(), f)).hasMessageContaining("half days");
    f.setCategory(CourseCategory.INTERNAL_TRAINING);
    assertThatThrownBy(() -> service.submit(alice.getId(), f))
        .hasMessageContaining("zero course fee");
  }

  @Test
  void reasonsAndOwnershipEnforced() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    assertThatThrownBy(() -> approval.approve(bob.getId(), a.getId(), " "))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> approval.reject(other.getId(), a.getId(), "No"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.getDetails(charlie.getId(), a.getId()))
        .isInstanceOf(NotFoundException.class);
    approval.reject(bob.getId(), a.getId(), "Not relevant.");
    assertThat(service.getDetails(alice.getId(), a.getId()).getStatus())
        .isEqualTo(ApplicationStatus.REJECTED);
    assertThat(entitlements.getUsage(alice.getId(), 2026).remainingBudget())
        .isEqualByComparingTo("2000");
    assertThatThrownBy(() -> approval.approve(bob.getId(), a.getId(), "Changed mind"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void completionRequiresEndedApprovedCourseAndComment() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    approval.approve(bob.getId(), a.getId(), "Yes");
    Long futureId = a.getId();
    assertThatThrownBy(() -> service.complete(alice.getId(), futureId, "Useful"))
        .hasMessageContaining("ended");
    a = applications.findById(a.getId()).orElseThrow();
    a.setStartDate(LocalDate.parse("2026-08-31"));
    a.setEndDate(a.getStartDate());
    applications.save(a);
    Long id = a.getId();
    assertThatThrownBy(() -> service.complete(alice.getId(), id, ""))
        .isInstanceOf(BusinessException.class);
    service.complete(alice.getId(), id, "Learned service design.");
    assertThat(service.getDetails(alice.getId(), id).getStatus())
        .isEqualTo(ApplicationStatus.COMPLETED);
  }

  @Test
  void crossYearDaysAndStartYearFee() {
    service.submit(alice.getId(), form("2026-12-31", "2027-01-04", "300"));
    assertThat(entitlements.getUsage(alice.getId(), 2026).pendingDays()).isEqualByComparingTo("1");
    assertThat(entitlements.getUsage(alice.getId(), 2027).pendingDays()).isEqualByComparingTo("2");
    assertThat(entitlements.getUsage(alice.getId(), 2027).pendingFees()).isEqualByComparingTo("0");
  }

  @Test
  void staleEditsAndTerminalHistory() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    var f = service.toForm(a);
    service.update(alice.getId(), a.getId(), f);
    assertThatThrownBy(() -> service.update(alice.getId(), a.getId(), f))
        .hasMessageContaining("changed");
    service.delete(alice.getId(), a.getId());
    assertThat(service.getPersonalHistory(alice.getId(), 2026)).hasSize(1);
    assertThat(service.getPersonalHistory(alice.getId(), 2025)).isEmpty();
  }

  @Test
  void concurrentSubmissionsCannotOverspend() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    var sequence = new java.util.concurrent.atomic.AtomicInteger();
    try {
      Callable<Boolean> task =
          () -> {
            gate.await();
            try {
              String date = sequence.getAndIncrement() == 0 ? "2026-09-07" : "2026-09-08";
              service.submit(alice.getId(), form(date, date, "1500"));
              return true;
            } catch (BusinessException e) {
              return false;
            }
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(applications.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedUpdateRollsBackAndManagerContextExcludesApplicant() {
    var original = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "2000"));
    var update = service.toForm(original);
    update.setCourseTitle("Must not persist");
    update.setCourseFee(new BigDecimal("2001"));
    assertThatThrownBy(() -> service.update(alice.getId(), original.getId(), update))
        .isInstanceOf(BusinessException.class);
    assertThat(service.getDetails(alice.getId(), original.getId()).getCourseTitle())
        .isEqualTo("Java training");
    var teammate = service.submit(charlie.getId(), form("2026-09-07", "2026-09-07", "100"));
    approval.approve(bob.getId(), teammate.getId(), "Relevant");
    var context = approval.getApprovalContext(bob.getId(), original.getId());
    assertThat(context.otherTeamApprovedCourses())
        .extracting(CourseApplication::getId)
        .containsExactly(teammate.getId());
    assertThat(context.annualUsage().get(2026).remainingBudget()).isEqualByComparingTo("0");
  }

  @Test
  void pagesRenderAndWorkflowThroughHttp() throws Exception {
    var s = session(alice);

    mvc.perform(get("/employee").session(s))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, alice")));
    mvc.perform(get("/applications/new").session(s)).andExpect(status().isOk());
    mvc.perform(
            post("/applications")
                .session(s)
                .with(csrf())
                .param("courseTitle", "Web course")
                .param("trainingProvider", "ISS")
                .param("category", "EXTERNAL_COURSE")
                .param("startDate", "2026-09-07")
                .param("endDate", "2026-09-07")
                .param("startSession", "AM")
                .param("endSession", "PM")
                .param("courseFee", "100")
                .param("justification", "Learn MVC"))
        .andExpect(status().is3xxRedirection());
    Long id = applications.findAll().getFirst().getId();
    for (String path :
        List.of(
            "/applications",
            "/applications/" + id,
            "/applications/" + id + "/edit",
            "/applications/" + id + "/complete"))
      mvc.perform(get(path).session(s)).andExpect(status().isOk());
    var manager = session(bob);
    for (String path :
        List.of(
            "/manager/pending",
            "/manager/applications/" + id,
            "/manager/employees/" + alice.getId() + "/history"))
      mvc.perform(get(path).session(manager)).andExpect(status().isOk());
    mvc.perform(
            post("/manager/applications/" + id + "/approve")
                .session(manager)
                .with(csrf())
                .param("reason", "Approved for development"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/applications/" + id).session(s))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Approved for development")));
  }

  @Test
  void loginSecurityAndValidation() throws Exception {
    for (String path : List.of("/employee/login", "/admin/login"))
      mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(get("/employee")).andExpect(redirectedUrl("/employee/login"));
    var s = new MockHttpSession();

    mvc.perform(
            post("/employee/login")
                .session(s)
                .with(csrf())
                .param("username", "alice")
                .param("password", "wrong"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid")));
    mvc.perform(
            post("/employee/login")
                .session(s)
                .with(csrf())
                .param("username", "alice")
                .param("password", "DemoPass123!"))
        .andExpect(redirectedUrl("/employee"));
    mvc.perform(post("/applications").session(session(alice))).andExpect(status().isForbidden());
    mvc.perform(get("/manager/pending").session(session(alice))).andExpect(status().isForbidden());
    mvc.perform(get("/admin").session(session(admin))).andExpect(status().isOk());
    assertThatThrownBy(() -> authentication.authenticate("alice", "DemoPass123!", true))
        .isInstanceOf(BusinessException.class);
  }

  org.springframework.mock.web.MockMultipartFile pdf(String name) {
    return new org.springframework.mock.web.MockMultipartFile(
        name,
        name + ".pdf",
        "application/pdf",
        "%PDF-1.7 test evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  CourseApplication completed() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    approval.approve(bob.getId(), a.getId(), "Relevant");
    a = applications.findById(a.getId()).orElseThrow();
    a.setStartDate(LocalDate.parse("2026-08-31"));
    a.setEndDate(a.getStartDate());
    applications.save(a);
    service.complete(alice.getId(), a.getId(), "Completed training");
    return a;
  }

  @Test
  void claimLifecycleLedgerAndAccess() throws Exception {
    var a = completed();
    var c = claimService.submit(alice.getId(), a.getId(), true, pdf("receipt"), pdf("certificate"));
    assertThat(c.getAmount()).isEqualByComparingTo("100");
    assertThatThrownBy(
            () ->
                claimService.submit(
                    alice.getId(), a.getId(), true, pdf("receipt"), pdf("certificate")))
        .hasMessageContaining("already exists");
    assertThatThrownBy(() -> claimService.decide(other.getId(), c.getId(), "Yes", true))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> claimService.decide(bob.getId(), c.getId(), " ", true))
        .isInstanceOf(BusinessException.class);
    mvc.perform(get("/attachments/claims/" + c.getId() + "/receipt").session(session(charlie)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/attachments/claims/" + c.getId() + "/receipt").session(session(bob)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(pdf("receipt").getBytes()));
    for (var u : List.of(alice, bob, admin))
      mvc.perform(
              get(u == admin ? "/admin/claims" : u == bob ? "/manager/claims" : "/claims")
                  .session(session(u)))
          .andExpect(status().isOk());
    claimService.decide(bob.getId(), c.getId(), "Receipt verified", true);
    claimService.reimburse(admin.getId(), c.getId());
    assertThat(claims.findById(c.getId()).orElseThrow().getStatus())
        .isEqualTo(ClaimStatus.REIMBURSED);
    assertThat(entitlements.getUsage(alice.getId(), 2026).committedFees())
        .isEqualByComparingTo("100");
    assertThat(
            ledgerEntries.findAll().stream()
                .filter(e -> e.getKind() == LedgerEntryKind.REIMBURSEMENT)
                .count())
        .isEqualTo(1);
    assertThatThrownBy(() -> claimService.reimburse(admin.getId(), c.getId()))
        .hasMessageContaining("unpaid");
  }

  @Test
  void claimEvidenceEligibilityAndRejection() {
    var a = completed();
    assertThatThrownBy(
            () ->
                claimService.submit(
                    alice.getId(), a.getId(), false, pdf("receipt"), pdf("certificate")))
        .hasMessageContaining("yourself");
    assertThatThrownBy(
            () ->
                claimService.submit(
                    charlie.getId(), a.getId(), true, pdf("receipt"), pdf("certificate")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                claimService.submit(
                    alice.getId(),
                    a.getId(),
                    true,
                    new org.springframework.mock.web.MockMultipartFile(
                        "receipt", "bad.pdf", "application/pdf", "<script>bad</script>".getBytes()),
                    pdf("certificate")))
        .hasMessageContaining("PDF");
    assertThat(claims.count()).isZero();
    assertThat(attachments.count()).isZero();
    var c = claimService.submit(alice.getId(), a.getId(), true, pdf("receipt"), pdf("certificate"));
    claimService.decide(bob.getId(), c.getId(), "Receipt does not match", false);
    assertThatThrownBy(() -> claimService.reimburse(admin.getId(), c.getId()))
        .isInstanceOf(BusinessException.class);
    assertThat(claims.findById(c.getId()).orElseThrow().getReason())
        .isEqualTo("Receipt does not match");
  }

  @Test
  void optionalPagesAndMultipartHttp() throws Exception {
    var a = completed();
    var s = session(alice);
    for (String path :
        List.of(
            "/claims",
            "/claims/new?applicationId=" + a.getId(),
            "/ledger",
            "/calendar",
            "/calendar?month=2026-08&size=20",
            "/rest-client")) mvc.perform(get(path).session(s)).andExpect(status().isOk());
    mvc.perform(
            multipart("/claims")
                .file(pdf("receipt"))
                .file(pdf("certificate"))
                .param("applicationId", a.getId().toString())
                .param("selfPaid", "true")
                .session(s)
                .with(csrf()))
        .andExpect(redirectedUrl("/claims"));
    Long id = claims.findAll().getFirst().getId();
    mvc.perform(
            post("/manager/claims/" + id + "/approve")
                .session(session(bob))
                .with(csrf())
                .param("reason", "Verified"))
        .andExpect(redirectedUrl("/manager/claims"));
    mvc.perform(post("/admin/claims/" + id + "/reimburse").session(session(admin)).with(csrf()))
        .andExpect(redirectedUrl("/admin/claims"));
    for (String path :
        List.of("/admin", "/admin?editUser=" + alice.getId(), "/admin/mail", "/admin/claims"))
      mvc.perform(get(path).session(session(admin))).andExpect(status().isOk());
    mvc.perform(get("/manager/reports").session(session(bob))).andExpect(status().isOk());
  }

  @Test
  void adminMaintenanceAndHierarchy() throws Exception {
    var u =
        administration.saveUser(
            admin.getId(),
            null,
            "david",
            "David",
            "david@example.test",
            "DemoPass123!",
            Set.of(Role.EMPLOYEE),
            Designation.ADMINISTRATIVE,
            bob.getId(),
            true);
    assertThat(entitlements.getUsage(u.getId(), 2026).dayLimit()).isEqualByComparingTo("5");
    administration.setEntitlement(u.getId(), 2026, new BigDecimal("6.5"), new BigDecimal("2500"));
    assertThat(entitlements.getUsage(u.getId(), 2026).budgetLimit()).isEqualByComparingTo("2500");
    assertThat(
            ledgerEntries
                .findByLedgerEmployeeIdAndLedgerYearOrderByRecordedAtDesc(u.getId(), 2026)
                .stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("2500");
    assertThatThrownBy(
            () ->
                administration.saveUser(
                    admin.getId(),
                    bob.getId(),
                    "bob",
                    "Bob",
                    "bob@example.test",
                    "",
                    Set.of(Role.MANAGER),
                    Designation.PROFESSIONAL,
                    bob.getId(),
                    true))
        .hasMessageContaining("cycle");
    assertThatThrownBy(() -> administration.deleteUser(admin.getId(), bob.getId()))
        .hasMessageContaining("Reassign");
    assertThatThrownBy(() -> administration.deleteUser(admin.getId(), admin.getId()))
        .hasMessageContaining("own");
    var s = session(u);
    administration.deleteUser(admin.getId(), u.getId());
    mvc.perform(get("/employee").session(s)).andExpect(redirectedUrl("/employee/login"));
    assertThatThrownBy(() -> authentication.authenticate("david", "DemoPass123!", false))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void catalogueAndCategoryMaintenance() throws Exception {
    administration.saveProvider(null, "Training Co", "Provider", true);
    var p = providers.findAll().getFirst();
    administration.saveCourse(
        null,
        "Java catalogue",
        CourseCategory.EXTERNAL_COURSE,
        p.getId(),
        new BigDecimal("100"),
        true);
    var c = courses.findAll().getFirst();
    mvc.perform(
            get("/applications/new")
                .param("courseId", c.getId().toString())
                .session(session(alice)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Java catalogue")));
    for (String path :
        List.of("/admin?editCourse=" + c.getId(), "/admin?editProvider=" + p.getId()))
      mvc.perform(get(path).session(session(admin))).andExpect(status().isOk());
    administration.saveCategory(CourseCategory.EXTERNAL_COURSE, "External learning", false);
    var f = form("2026-09-07", "2026-09-07", "100");
    f.setCatalogueCourseId(c.getId());
    assertThatThrownBy(() -> service.submit(alice.getId(), f)).hasMessageContaining("category");
    administration.saveCategory(CourseCategory.EXTERNAL_COURSE, "External learning", true);
    administration.saveProvider(p.getId(), "Training Co", "Provider", false);
    assertThatThrownBy(() -> service.submit(alice.getId(), f)).hasMessageContaining("unavailable");
    administration.saveProvider(p.getId(), "Training Co", "Provider", true);
    assertThat(service.submit(alice.getId(), f).getCatalogueCourse().getId()).isEqualTo(c.getId());
    administration.saveHoliday(null, LocalDate.parse("2026-12-25"), "Christmas");
    var h = holidays.findAll().getFirst();
    administration.saveHoliday(h.getId(), h.getHolidayDate(), "Observed Christmas");
    administration.deleteHoliday(h.getId());
    assertThat(holidays.count()).isZero();
  }

  @Test
  void reportsScopeCsvAndMissingEntitlements() throws Exception {
    var a = completed();
    a = applications.findById(a.getId()).orElseThrow();
    a.setCourseTitle("=FORMULA,\"quoted\"\nsecond line");
    applications.save(a);
    var r =
        reports.report(
            bob.getId(),
            alice.getId(),
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-09-30"),
            CourseCategory.EXTERNAL_COURSE);
    assertThat(r.attendance()).hasSize(1);
    assertThat(r.budgets()).hasSize(1);
    assertThat(reports.csv(r, "attendance")).contains("\"'=FORMULA,\"\"quoted\"\"\nsecond line\"");
    assertThat(
            reports
                .report(
                    bob.getId(),
                    alice.getId(),
                    LocalDate.parse("2026-08-01"),
                    LocalDate.parse("2026-09-30"),
                    CourseCategory.INTERNAL_TRAINING)
                .attendance())
        .isEmpty();
    assertThatThrownBy(
            () ->
                reports.report(
                    other.getId(),
                    alice.getId(),
                    LocalDate.parse("2026-08-01"),
                    LocalDate.parse("2026-09-30"),
                    null))
        .isInstanceOf(NotFoundException.class);
    var missing =
        reports.report(
            bob.getId(),
            alice.getId(),
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-12-31"),
            null);
    assertThat(missing.budgets().getFirst().configured()).isFalse();
    for (String kind : List.of("attendance", "claims", "budget"))
      mvc.perform(
              get("/manager/reports/" + kind + ".csv?from=2026-01-01&to=2026-12-31")
                  .session(session(bob)))
          .andExpect(status().isOk())
          .andExpect(
              header()
                  .string(
                      "Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
  }

  @Test
  void restControllerAndReadOnlyRepository() throws Exception {
    var s = session(alice);
    String json =
        """
{"courseTitle":"API course","trainingProvider":"ISS","category":"EXTERNAL_COURSE","startDate":"2026-09-07","endDate":"2026-09-07","startSession":"AM","endSession":"PM","courseFee":100,"justification":"Learn REST"}
""";
    mvc.perform(
            post("/api/applications")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("title").value("API course"));
    mvc.perform(get("/api/applications?size=20").session(s))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content.length()").value(1))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));
    mvc.perform(
            get("/api/applications/" + applications.findAll().getFirst().getId())
                .session(session(charlie)))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/applications").session(s).contentType("application/json").content(json))
        .andExpect(status().isForbidden());
    mvc.perform(get("/data/courses").session(s)).andExpect(status().isOk());
    mvc.perform(get("/data/users").session(s)).andExpect(status().isNotFound());
    mvc.perform(
            post("/data/courses")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void paginationIsScopedAndStable() throws Exception {
    for (int i = 0; i < 31; i++) {
      var a = new CourseApplication();
      a.setEmployee(alice);
      a.setCourseTitle("Page course " + i);
      a.setCategory(CourseCategory.EXTERNAL_COURSE);
      a.setTrainingProvider("ISS");
      a.setStartDate(LocalDate.parse("2026-09-07"));
      a.setEndDate(a.getStartDate());
      a.setStartSession(DaySession.AM);
      a.setEndSession(DaySession.PM);
      a.setCourseFee(BigDecimal.ONE);
      a.setTrainingDays(BigDecimal.ONE);
      a.setJustification("Pagination fixture");
      a.setStatus(ApplicationStatus.APPLIED);
      applications.save(a);
    }
    mvc.perform(get("/api/applications?page=1&size=10").session(session(alice)))
        .andExpect(jsonPath("content.length()").value(10))
        .andExpect(jsonPath("totalElements").value(31));
    mvc.perform(get("/api/applications?page=1&size=25").session(session(alice)))
        .andExpect(jsonPath("content.length()").value(6));
    mvc.perform(get("/api/applications?size=10000").session(session(charlie)))
        .andExpect(jsonPath("totalElements").value(0));
    mvc.perform(get("/applications?page=1&size=10").session(session(alice)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Next")));
    mvc.perform(get("/manager/pending?page=1&size=20").session(session(bob)))
        .andExpect(status().isOk());
  }

  @Test
  void emailOutboxFollowsCommittedDecisions() {
    var a = service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100"));
    assertThat(emails.findAll())
        .singleElement()
        .satisfies(
            n -> {
              assertThat(n.getRecipient()).isEqualTo("bob@example.test");
              assertThat(n.getBody()).contains("/employee/login");
            });
    assertThatThrownBy(() -> service.submit(alice.getId(), form("2026-09-07", "2026-09-07", "100")))
        .isInstanceOf(BusinessException.class);
    assertThat(emails.count()).isEqualTo(1);
    approval.reject(bob.getId(), a.getId(), "Not relevant this year");
    assertThat(emails.findAll())
        .anySatisfy(
            n -> {
              assertThat(n.getRecipient()).isEqualTo("alice@example.test");
              assertThat(n.getBody()).contains("REJECTED", "Not relevant this year");
            });
  }

  @Test
  void roleChangesApplyToExistingSessions() throws Exception {
    var s = session(bob);
    bob.setRoles(new HashSet<>(Set.of(Role.EMPLOYEE)));
    users.save(bob);
    mvc.perform(get("/manager/reports").session(s)).andExpect(status().isForbidden());
    mvc.perform(post("/admin/entitlements").session(session(alice)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/admin/entitlements").session(session(admin)))
        .andExpect(status().isForbidden());
  }

  @Test
  void largeEvidencePersistsAndCalendarProtectsAccounting() {
    var a = completed();
    byte[] payload = new byte[100000];
    System.arraycopy(
        "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, payload, 0, 8);
    var receipt =
        new org.springframework.mock.web.MockMultipartFile(
            "receipt", "large.pdf", "application/pdf", payload);
    var c = claimService.submit(alice.getId(), a.getId(), true, receipt, pdf("certificate"));
    assertThat(claimService.download(alice.getId(), c.getId(), "receipt").content())
        .hasSize(100000);
    assertThatThrownBy(() -> administration.saveHoliday(null, a.getStartDate(), "New holiday"))
        .hasMessageContaining("overlaps");
    assertThat(entitlements.getUsage(alice.getId(), 2026).committedDays())
        .isEqualByComparingTo("1");
  }

  @Test
  void anonymousApiGetsUnauthorized() throws Exception {
    mvc.perform(get("/api/applications").servletPath("/api/applications"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/data/courses").servletPath("/data/courses"))
        .andExpect(status().isUnauthorized());
  }
}
