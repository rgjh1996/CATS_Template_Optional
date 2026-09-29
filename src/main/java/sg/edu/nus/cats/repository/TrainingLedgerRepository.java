package sg.edu.nus.cats.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;

public interface TrainingLedgerRepository extends JpaRepository<TrainingLedger, Long> {
  Optional<TrainingLedger> findByEmployeeIdAndYear(Long employeeId, int year);
}
