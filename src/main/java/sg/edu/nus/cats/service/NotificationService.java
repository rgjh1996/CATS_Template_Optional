package sg.edu.nus.cats.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.repository.EmailNotificationRepository;

@Service
public class NotificationService {
  private final EmailNotificationRepository outbox;
  private final Clock clock;
  private final String baseUrl;

  public NotificationService(
      EmailNotificationRepository outbox,
      Clock clock,
      @Value("${cats.public-url:http://localhost:8080}") String baseUrl) {
    this.outbox = outbox;
    this.clock = clock;
    this.baseUrl = baseUrl;
  }

  public void submitted(CourseApplication a) {
    queue(
        a.getEmployee().getManager(),
        "Course request: " + a.getCourseTitle(),
        a.getEmployee().getName() + " submitted " + a.getCourseTitle() + " for approval.");
  }

  public void decided(CourseApplication a) {
    queue(
        a.getEmployee(),
        "Course application " + a.getStatus(),
        "Course: "
            + a.getCourseTitle()
            + "\nDecision: "
            + a.getStatus()
            + "\nReason: "
            + a.getManagerComment());
  }

  private void queue(User recipient, String subject, String body) {
    if (recipient.getEmail() == null || recipient.getEmail().isBlank())
      throw new sg.edu.nus.cats.exception.BusinessException(
          "Please ask the administrator to configure the recipient's email address.");
    var n = new EmailNotification();
    n.setRecipient(recipient.getEmail());
    n.setSubject(subject);
    n.setBody(
        body + "\n\nSign in to view the application and comments: " + baseUrl + "/employee/login");
    n.setCreatedAt(LocalDateTime.now(clock));
    outbox.save(n);
  }
}
