package sg.edu.nus.cats.config;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;
import sg.edu.nus.cats.service.LedgerService;

@Component
@Order(1)
@ConditionalOnProperty(name = "cats.seed", havingValue = "true")
public class OptionalDemoData implements CommandLineRunner {
  private final CategorySettingsRepository categories;
  private final TrainingProviderRepository providers;
  private final CourseRepository courses;
  private final AnnualEntitlementRepository entitlements;
  private final CourseApplicationRepository applications;
  private final LedgerService ledger;

  public OptionalDemoData(
      CategorySettingsRepository c,
      TrainingProviderRepository p,
      CourseRepository co,
      AnnualEntitlementRepository e,
      CourseApplicationRepository a,
      LedgerService l) {
    categories = c;
    providers = p;
    courses = co;
    entitlements = e;
    applications = a;
    ledger = l;
  }

  @Transactional
  public void run(String... args) {
    if (categories.count() > 0) return;
    if (categories.count() == 0)
      for (var code : CourseCategory.values()) {
        var c = new CategorySettings();
        c.setCode(code);
        c.setLabel(code.name().replace('_', ' '));
        categories.save(c);
      }
    if (providers.count() == 0) {
      var p = new TrainingProvider();
      p.setName("Demo Training Academy");
      p.setDescription("Illustrative catalogue for exploring the optional workflows.");
      providers.save(p);
      for (var code : CourseCategory.values()) {
        var c = new Course();
        c.setTitle(
            switch (code) {
              case INTERNAL_TRAINING -> "Team knowledge sharing";
              case EXTERNAL_COURSE -> "Spring Boot fundamentals";
              case PROFESSIONAL_CERTIFICATION -> "Professional Java certification";
            });
        c.setCategory(code);
        c.setProvider(p);
        c.setDefaultFee(
            code == CourseCategory.INTERNAL_TRAINING ? BigDecimal.ZERO : new BigDecimal("450"));
        courses.save(c);
      }
    }
    for (var e : entitlements.findAll())
      ledger.post(
          e.getEmployee(),
          e.getYear(),
          LedgerEntryKind.BUDGET_ADJUSTMENT,
          e.getBudgetLimit(),
          "seed-budget-" + e.getId(),
          "Opening demo budget");
    for (var a : applications.findAll())
      if (a.getStatus() == ApplicationStatus.APPROVED
          || a.getStatus() == ApplicationStatus.COMPLETED)
        ledger.post(
            a.getEmployee(),
            a.getStartDate().getYear(),
            LedgerEntryKind.FEE_APPROVED,
            a.getCourseFee(),
            "application-approved-" + a.getId(),
            "Approved " + a.getCourseTitle());
  }
}
