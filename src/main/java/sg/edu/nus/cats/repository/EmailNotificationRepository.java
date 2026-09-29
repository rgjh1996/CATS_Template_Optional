package sg.edu.nus.cats.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {
  List<EmailNotification> findTop20ByStatusInAndAttemptsLessThanOrderByCreatedAt(
      Collection<EmailStatus> statuses, int maxAttempts);
}
