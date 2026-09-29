package sg.edu.nus.cats.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;

public interface FeeClaimRepository extends JpaRepository<FeeClaim, Long> {
  boolean existsByApplicationId(Long applicationId);

  @EntityGraph(
      attributePaths = {
        "application",
        "application.employee",
        "application.employee.manager",
        "decidedBy"
      })
  @Query("select c from FeeClaim c where c.id=:id")
  Optional<FeeClaim> details(Long id);

  @EntityGraph(attributePaths = {"application", "application.employee", "decidedBy"})
  @Query(
      "select c from FeeClaim c where c.application.employee.id=:employeeId order by c.submittedAt"
          + " desc")
  List<FeeClaim> own(Long employeeId);

  @EntityGraph(attributePaths = {"application", "application.employee", "decidedBy"})
  @Query(
      "select c from FeeClaim c where c.application.employee.manager.id=:managerId order by"
          + " c.submittedAt desc")
  List<FeeClaim> team(Long managerId);

  @Query("select c.application.employee.id from FeeClaim c where c.id=:id")
  Optional<Long> employeeId(Long id);

  @EntityGraph(attributePaths = {"application", "application.employee", "decidedBy"})
  @Query("select c from FeeClaim c order by c.submittedAt desc")
  java.util.List<FeeClaim> allDetails();
}
