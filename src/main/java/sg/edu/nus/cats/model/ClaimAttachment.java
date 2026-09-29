package sg.edu.nus.cats.model;

import jakarta.persistence.*;
import java.time.*;
import sg.edu.nus.cats.model.enums.*;

@Entity
public class ClaimAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String filename;

  @Column(nullable = false)
  private String contentType;

  @Lob
  @Column(nullable = false, length = 5242880)
  private byte[] content;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String value) {
    this.filename = value;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String value) {
    this.contentType = value;
  }

  public byte[] getContent() {
    return content;
  }

  public void setContent(byte[] value) {
    this.content = value;
  }
}
