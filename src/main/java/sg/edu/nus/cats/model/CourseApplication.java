package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class CourseApplication {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User employee;

  @Column(length = 2000)
  private String courseTitle;

  @Column(length = 2000)
  private String trainingProvider;

  @Column(length = 2000)
  private String justification;

  @Column(length = 2000)
  private String workDissemination;

  @Column(length = 2000)
  private String managerComment;

  @Column(length = 2000)
  private String experienceComment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CourseCategory category;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  private DaySession startSession;

  @Enumerated(EnumType.STRING)
  private DaySession endSession;

  @Column(precision = 12, scale = 2, nullable = false)
  private BigDecimal courseFee;

  @Column(precision = 8, scale = 1, nullable = false)
  private BigDecimal trainingDays;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApplicationStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  private User decidedBy;

  private LocalDateTime decidedAt;
  @Version private Long version;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getEmployee() {
    return employee;
  }

  public void setEmployee(User employee) {
    this.employee = employee;
  }

  public String getCourseTitle() {
    return courseTitle;
  }

  public void setCourseTitle(String courseTitle) {
    this.courseTitle = courseTitle;
  }

  public String getTrainingProvider() {
    return trainingProvider;
  }

  public void setTrainingProvider(String trainingProvider) {
    this.trainingProvider = trainingProvider;
  }

  public String getJustification() {
    return justification;
  }

  public void setJustification(String justification) {
    this.justification = justification;
  }

  public String getWorkDissemination() {
    return workDissemination;
  }

  public void setWorkDissemination(String workDissemination) {
    this.workDissemination = workDissemination;
  }

  public String getManagerComment() {
    return managerComment;
  }

  public void setManagerComment(String managerComment) {
    this.managerComment = managerComment;
  }

  public String getExperienceComment() {
    return experienceComment;
  }

  public void setExperienceComment(String experienceComment) {
    this.experienceComment = experienceComment;
  }

  public CourseCategory getCategory() {
    return category;
  }

  public void setCategory(CourseCategory category) {
    this.category = category;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public DaySession getStartSession() {
    return startSession;
  }

  public void setStartSession(DaySession startSession) {
    this.startSession = startSession;
  }

  public DaySession getEndSession() {
    return endSession;
  }

  public void setEndSession(DaySession endSession) {
    this.endSession = endSession;
  }

  public BigDecimal getCourseFee() {
    return courseFee;
  }

  public void setCourseFee(BigDecimal courseFee) {
    this.courseFee = courseFee;
  }

  public BigDecimal getTrainingDays() {
    return trainingDays;
  }

  public void setTrainingDays(BigDecimal trainingDays) {
    this.trainingDays = trainingDays;
  }

  public ApplicationStatus getStatus() {
    return status;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public User getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(User decidedBy) {
    this.decidedBy = decidedBy;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(LocalDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  private Course catalogueCourse;

  public Course getCatalogueCourse() {
    return catalogueCourse;
  }

  public void setCatalogueCourse(Course value) {
    this.catalogueCourse = value;
  }
}
