package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class LedgerService {
  private final TrainingLedgerRepository ledgers;
  private final LedgerEntryRepository entries;
  private final Clock clock;

  public LedgerService(TrainingLedgerRepository l, LedgerEntryRepository e, Clock c) {
    ledgers = l;
    entries = e;
    clock = c;
  }

  // Caller holds the employee row lock; event keys make posting idempotent.
  @Transactional
  public void post(
      User employee,
      int year,
      LedgerEntryKind kind,
      BigDecimal amount,
      String key,
      String description) {
    if (entries.existsByEventKey(key)) return;
    TrainingLedger ledger =
        ledgers
            .findByEmployeeIdAndYear(employee.getId(), year)
            .orElseGet(
                () -> {
                  var l = new TrainingLedger();
                  l.setEmployee(employee);
                  l.setYear(year);
                  return ledgers.save(l);
                });
    var entry = new LedgerEntry();
    entry.setLedger(ledger);
    entry.setKind(kind);
    entry.setAmount(amount);
    entry.setEventKey(key);
    entry.setDescription(description);
    entry.setRecordedAt(LocalDateTime.now(clock));
    entries.save(entry);
  }

  @Transactional(readOnly = true)
  public List<LedgerEntry> entries(Long employeeId, int year) {
    return entries.findByLedgerEmployeeIdAndLedgerYearOrderByRecordedAtDesc(employeeId, year);
  }
}
