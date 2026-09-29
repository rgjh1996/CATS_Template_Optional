package sg.edu.nus.cats.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.model.enums.EmailStatus;
import sg.edu.nus.cats.repository.EmailNotificationRepository;

@Component
@ConditionalOnProperty(name = "cats.mail.enabled", havingValue = "true")
public class EmailDispatcher {
  private final EmailNotificationRepository repo;
  private final JavaMailSender mail;
  private final Clock clock;
  private final String from;

  public EmailDispatcher(
      EmailNotificationRepository r,
      JavaMailSender m,
      Clock c,
      @Value("${cats.mail.from:cats@example.test}") String from) {
    repo = r;
    mail = m;
    clock = c;
    this.from = from;
  }

  @Scheduled(fixedDelayString = "${cats.mail.interval-ms:30000}")
  @Transactional
  public void dispatch() {
    for (var n :
        repo.findTop20ByStatusInAndAttemptsLessThanOrderByCreatedAt(
            List.of(EmailStatus.PENDING, EmailStatus.FAILED), 5)) {
      if (n.getAttempts() >= 5) continue;
      n.setAttempts(n.getAttempts() + 1);
      try {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(n.getRecipient());
        message.setSubject(n.getSubject());
        message.setText(n.getBody());
        mail.send(message);
        n.setStatus(EmailStatus.SENT);
        n.setSentAt(LocalDateTime.now(clock));
        n.setLastError(null);
      } catch (Exception e) {
        n.setStatus(EmailStatus.FAILED);
        n.setLastError("SMTP delivery failed. Check mail configuration and retry.");
      }
    }
  }
}
