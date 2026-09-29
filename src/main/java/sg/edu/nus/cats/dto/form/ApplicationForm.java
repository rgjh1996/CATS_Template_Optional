package sg.edu.nus.cats.dto.form;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import sg.edu.nus.cats.model.enums.*;

public class ApplicationForm {
  @NotBlank
  @Size(max = 200)
  private String courseTitle;

  @NotNull private CourseCategory category;

  @NotBlank
  @Size(max = 200)
  private String trainingProvider;

  @NotNull private LocalDate startDate;
  @NotNull private LocalDate endDate;
  @NotNull private DaySession startSession = DaySession.AM;
  @NotNull private DaySession endSession = DaySession.PM;

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 10, fraction = 2)
  private BigDecimal courseFee = BigDecimal.ZERO;

  @NotBlank
  @Size(max = 2000)
  private String justification;

  @Size(max = 2000)
  private String workDissemination;

  private Long version;

  public String getCourseTitle() {
    return courseTitle;
  }

  public void setCourseTitle(String courseTitle) {
    this.courseTitle = courseTitle;
  }

  public CourseCategory getCategory() {
    return category;
  }

  public void setCategory(CourseCategory category) {
    this.category = category;
  }

  public String getTrainingProvider() {
    return trainingProvider;
  }

  public void setTrainingProvider(String trainingProvider) {
    this.trainingProvider = trainingProvider;
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

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  private Long catalogueCourseId;

  public Long getCatalogueCourseId() {
    return catalogueCourseId;
  }

  public void setCatalogueCourseId(Long id) {
    this.catalogueCourseId = id;
  }
}
