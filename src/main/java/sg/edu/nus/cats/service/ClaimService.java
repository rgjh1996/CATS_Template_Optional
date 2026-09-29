package sg.edu.nus.cats.service;

import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.cats.exception.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;
import sg.edu.nus.cats.repository.*;

@Service
public class ClaimService {
  private final FeeClaimRepository claims;
  private final UserRepository users;
  private final CourseApplicationRepository applications;
  private final LedgerService ledger;
  private final Clock clock;

  public ClaimService(
      FeeClaimRepository c,
      UserRepository u,
      CourseApplicationRepository a,
      LedgerService l,
      Clock clock) {
    claims = c;
    users = u;
    applications = a;
    ledger = l;
    this.clock = clock;
  }

  @Transactional
  public FeeClaim submit(
      Long actorId,
      Long applicationId,
      boolean selfPaid,
      MultipartFile receipt,
      MultipartFile certificate) {
    users.lockById(actorId).orElseThrow(() -> new NotFoundException("Employee not found."));
    var a =
        applications
            .details(applicationId)
            .orElseThrow(() -> new NotFoundException("Application not found."));
    if (!a.getEmployee().getId().equals(actorId))
      throw new NotFoundException("Application not found.");
    if (a.getStatus() != ApplicationStatus.COMPLETED
        || a.getCategory() == CourseCategory.INTERNAL_TRAINING
        || !selfPaid)
      throw new BusinessException(
          "Claims require a completed fee-paying course that you paid for yourself.");
    if (claims.existsByApplicationId(applicationId))
      throw new BusinessException("A claim already exists for this application.");
    if (a.getEmployee().getManager() == null)
      throw new BusinessException("Assign a manager before submitting a claim.");
    var c = new FeeClaim();
    c.setApplication(a);
    c.setAmount(a.getCourseFee());
    c.setStatus(ClaimStatus.SUBMITTED);
    c.setReceipt(attachment(receipt));
    c.setCertificate(attachment(certificate));
    c.setSubmittedAt(LocalDateTime.now(clock));
    return claims.save(c);
  }

  private ClaimAttachment attachment(MultipartFile f) {
    if (f == null || f.isEmpty() || f.getSize() > 5 * 1024 * 1024)
      throw new BusinessException("Both attachments are required; maximum file size is 5 MB each.");
    try {
      byte[] bytes = f.getBytes();
      String type;
      if (bytes.length >= 5
          && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
        type = "application/pdf";
      else if (bytes.length >= 8
          && bytes[0] == (byte) 137
          && bytes[1] == 80
          && bytes[2] == 78
          && bytes[3] == 71
          && bytes[4] == 13
          && bytes[5] == 10
          && bytes[6] == 26
          && bytes[7] == 10) type = "image/png";
      else if (bytes.length >= 3
          && bytes[0] == (byte) 255
          && bytes[1] == (byte) 216
          && bytes[2] == (byte) 255) type = "image/jpeg";
      else throw new BusinessException("Attachments must be PDF, PNG or JPEG files.");
      var a = new ClaimAttachment();
      String name =
          Objects.toString(f.getOriginalFilename(), "attachment")
              .replaceAll("[^A-Za-z0-9._-]", "_");
      a.setFilename(name.substring(0, Math.min(name.length(), 120)));
      a.setContentType(type);
      a.setContent(bytes);
      return a;
    } catch (IOException e) {
      throw new BusinessException("Could not read attachment. Please upload again.");
    }
  }

  @Transactional(readOnly = true)
  public List<FeeClaim> own(Long id) {
    return claims.own(id);
  }

  @Transactional(readOnly = true)
  public List<FeeClaim> team(Long id) {
    return claims.team(id);
  }

  @Transactional
  public void decide(Long managerId, Long id, String reason, boolean approve) {
    var manager =
        users.findById(managerId).orElseThrow(() -> new NotFoundException("Manager not found."));
    if (!manager.hasRole(Role.MANAGER)) throw new BusinessException("Manager access required.");
    Long employeeId =
        claims.employeeId(id).orElseThrow(() -> new NotFoundException("Claim not found."));
    users.lockById(employeeId).orElseThrow();
    var c = load(id);
    var employee = c.getApplication().getEmployee();
    if (employee.getManager() == null
        || !employee.getManager().getId().equals(managerId)
        || employeeId.equals(managerId)) throw new NotFoundException("Claim not found.");
    if (c.getStatus() != ClaimStatus.SUBMITTED)
      throw new BusinessException("This claim already has a decision.");
    CourseApplicationService.requireComment(reason);
    c.setStatus(approve ? ClaimStatus.APPROVED : ClaimStatus.REJECTED);
    c.setReason(reason.trim());
    c.setDecidedBy(manager);
    c.setDecidedAt(LocalDateTime.now(clock));
  }

  @Transactional
  public void reimburse(Long adminId, Long id) {
    var admin = users.findById(adminId).orElseThrow();
    if (!admin.hasRole(Role.ADMIN)) throw new BusinessException("Administrator access required.");
    var employeeId =
        claims.employeeId(id).orElseThrow(() -> new NotFoundException("Claim not found."));
    users.lockById(employeeId).orElseThrow();
    var c = load(id);
    if (c.getStatus() != ClaimStatus.APPROVED)
      throw new BusinessException("Only an approved, unpaid claim can be recorded as reimbursed.");
    c.setStatus(ClaimStatus.REIMBURSED);
    c.setReimbursedAt(LocalDateTime.now(clock));
    var a = c.getApplication();
    ledger.post(
        a.getEmployee(),
        a.getStartDate().getYear(),
        LedgerEntryKind.REIMBURSEMENT,
        c.getAmount(),
        "claim-paid-" + id,
        "Reimbursement recorded for " + a.getCourseTitle());
  }

  @Transactional(readOnly = true)
  public FeeClaim detail(Long actorId, Long id) {
    var c = load(id);
    authorize(actorId, c);
    return c;
  }

  public record Download(String filename, String contentType, byte[] content) {}

  @Transactional(readOnly = true)
  public Download download(Long actorId, Long id, String kind) {
    var c = load(id);
    authorize(actorId, c);
    ClaimAttachment a =
        switch (kind) {
          case "receipt" -> c.getReceipt();
          case "certificate" -> c.getCertificate();
          default -> throw new NotFoundException("Attachment not found.");
        };
    return new Download(a.getFilename(), a.getContentType(), a.getContent());
  }

  private FeeClaim load(Long id) {
    return claims.details(id).orElseThrow(() -> new NotFoundException("Claim not found."));
  }

  private void authorize(Long actorId, FeeClaim c) {
    var u = users.findById(actorId).orElseThrow();
    var employee = c.getApplication().getEmployee();
    if (!u.hasRole(Role.ADMIN)
        && !employee.getId().equals(actorId)
        && !(u.hasRole(Role.MANAGER)
            && employee.getManager() != null
            && employee.getManager().getId().equals(actorId)))
      throw new NotFoundException("Claim not found.");
  }
}
