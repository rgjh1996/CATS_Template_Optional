package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class Course {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  private CourseCategory category;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  private TrainingProvider provider;

  @Column(precision = 12, scale = 2)
  private BigDecimal defaultFee;

  private boolean active = true;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public CourseCategory getCategory() {
    return category;
  }

  public void setCategory(CourseCategory value) {
    this.category = value;
  }

  public TrainingProvider getProvider() {
    return provider;
  }

  public void setProvider(TrainingProvider value) {
    this.provider = value;
  }

  public BigDecimal getDefaultFee() {
    return defaultFee;
  }

  public void setDefaultFee(BigDecimal value) {
    this.defaultFee = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
