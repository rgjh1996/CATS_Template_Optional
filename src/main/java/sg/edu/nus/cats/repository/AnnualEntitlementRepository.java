package sg.edu.nus.cats.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.cats.model.AnnualEntitlement;

public interface AnnualEntitlementRepository extends JpaRepository<AnnualEntitlement, Long> {
  Optional<AnnualEntitlement> findByEmployeeIdAndYear(Long employeeId, int year);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"employee"})
  java.util.List<AnnualEntitlement> findAll();
}
