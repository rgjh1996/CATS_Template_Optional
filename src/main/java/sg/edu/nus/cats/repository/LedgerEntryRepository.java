package sg.edu.nus.cats.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
  List<LedgerEntry> findByLedgerEmployeeIdAndLedgerYearOrderByRecordedAtDesc(
      Long employeeId, int year);

  boolean existsByEventKey(String eventKey);
}
