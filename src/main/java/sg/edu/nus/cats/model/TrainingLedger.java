package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "ledger_year"}))
public class TrainingLedger {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User employee;

  @Column(name = "ledger_year")
  private int year;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public User getEmployee() {
    return employee;
  }

  public void setEmployee(User value) {
    this.employee = value;
  }

  public int getYear() {
    return year;
  }

  public void setYear(int value) {
    this.year = value;
  }
}
