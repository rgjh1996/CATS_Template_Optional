package sg.edu.nus.cats.service;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.exception.NotFoundException;
import sg.edu.nus.cats.model.CourseApplication;
import sg.edu.nus.cats.repository.*;

@Service
public class PagedApplicationService {
  private final CourseApplicationRepository applications;
  private final UserRepository users;
  private final Clock clock;

  public PagedApplicationService(CourseApplicationRepository a, UserRepository u, Clock c) {
    applications = a;
    users = u;
    clock = c;
  }

  public static Pageable pageable(int page, int size) {
    return PageRequest.of(
        Math.max(0, page),
        Set.of(10, 20, 25).contains(size) ? size : 10,
        Sort.by("startDate").descending().and(Sort.by("id").descending()));
  }

  @Transactional(readOnly = true)
  public Page<CourseApplication> history(
      Long actor, Long employee, boolean manager, int page, int size) {
    if (manager) {
      var u = users.findById(employee).orElseThrow();
      if (u.getManager() == null || !u.getManager().getId().equals(actor))
        throw new NotFoundException("Employee not found.");
    } else if (!actor.equals(employee)) throw new NotFoundException("Employee not found.");
    int y = LocalDate.now(clock).getYear();
    return applications.historyPage(
        employee, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), pageable(page, size));
  }

  @Transactional(readOnly = true)
  public Page<CourseApplication> pending(Long manager, int page, int size) {
    return applications.pendingPage(
        manager, CourseApplicationService.PENDING, pageable(page, size));
  }
}
