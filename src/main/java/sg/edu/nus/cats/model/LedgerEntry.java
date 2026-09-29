package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private TrainingLedger ledger;

  @Enumerated(EnumType.STRING)
  private LedgerEntryKind kind;

  @Column(precision = 12, scale = 2, nullable = false)
  private BigDecimal amount;

  @Column(unique = true, nullable = false)
  private String eventKey;

  @Column(length = 2000)
  private String description;

  private LocalDateTime recordedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public TrainingLedger getLedger() {
    return ledger;
  }

  public void setLedger(TrainingLedger value) {
    this.ledger = value;
  }

  public LedgerEntryKind getKind() {
    return kind;
  }

  public void setKind(LedgerEntryKind value) {
    this.kind = value;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    this.amount = value;
  }

  public String getEventKey() {
    return eventKey;
  }

  public void setEventKey(String value) {
    this.eventKey = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public LocalDateTime getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(LocalDateTime value) {
    this.recordedAt = value;
  }
}
