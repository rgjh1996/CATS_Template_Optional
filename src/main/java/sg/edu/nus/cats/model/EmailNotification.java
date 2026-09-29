package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class EmailNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String recipient;

  private String subject;

  @Column(length = 6000)
  private String body;

  @Enumerated(EnumType.STRING)
  private EmailStatus status = EmailStatus.PENDING;

  private int attempts = 0;

  private LocalDateTime createdAt;

  private LocalDateTime sentAt;

  @Column(length = 1000)
  private String lastError;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String value) {
    this.recipient = value;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String value) {
    this.subject = value;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String value) {
    this.body = value;
  }

  public EmailStatus getStatus() {
    return status;
  }

  public void setStatus(EmailStatus value) {
    this.status = value;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int value) {
    this.attempts = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime value) {
    this.sentAt = value;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String value) {
    this.lastError = value;
  }
}
