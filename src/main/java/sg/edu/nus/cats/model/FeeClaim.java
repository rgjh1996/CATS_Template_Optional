package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class FeeClaim {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(unique = true)
  private CourseApplication application;

  @Column(precision = 12, scale = 2, nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  private ClaimStatus status;

  @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
  private ClaimAttachment receipt;

  @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
  private ClaimAttachment certificate;

  @Column(length = 2000)
  private String reason;

  @ManyToOne(fetch = FetchType.LAZY)
  private User decidedBy;

  private LocalDateTime submittedAt;

  private LocalDateTime decidedAt;

  private LocalDateTime reimbursedAt;
  @Version private Long version;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public CourseApplication getApplication() {
    return application;
  }

  public void setApplication(CourseApplication value) {
    this.application = value;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    this.amount = value;
  }

  public ClaimStatus getStatus() {
    return status;
  }

  public void setStatus(ClaimStatus value) {
    this.status = value;
  }

  public ClaimAttachment getReceipt() {
    return receipt;
  }

  public void setReceipt(ClaimAttachment value) {
    this.receipt = value;
  }

  public ClaimAttachment getCertificate() {
    return certificate;
  }

  public void setCertificate(ClaimAttachment value) {
    this.certificate = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public User getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(User value) {
    this.decidedBy = value;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(LocalDateTime value) {
    this.submittedAt = value;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(LocalDateTime value) {
    this.decidedAt = value;
  }

  public LocalDateTime getReimbursedAt() {
    return reimbursedAt;
  }

  public void setReimbursedAt(LocalDateTime value) {
    this.reimbursedAt = value;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    this.version = value;
  }
}
