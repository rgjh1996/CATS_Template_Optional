package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class CategorySettings {
  @Id
  @Enumerated(EnumType.STRING)
  private CourseCategory code;

  @Column(nullable = false)
  private String label;

  private boolean active = true;

  public CourseCategory getCode() {
    return code;
  }

  public void setCode(CourseCategory value) {
    this.code = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String value) {
    this.label = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
