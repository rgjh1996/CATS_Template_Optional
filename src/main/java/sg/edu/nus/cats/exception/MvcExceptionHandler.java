package sg.edu.nus.cats.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class MvcExceptionHandler {
  @ExceptionHandler({
    java.time.DateTimeException.class,
    org.springframework.web.multipart.MaxUploadSizeExceededException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String invalid(Exception e, Model m) {
    m.addAttribute(
        "message", "Check the supplied fields and dates. Uploads must be at most 5 MB each.");
    return "error/problem";
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public String duplicate(Exception e, Model m) {
    m.addAttribute(
        "message",
        "This conflicts with an existing record. Check unique names and dates, then retry.");
    return "error/problem";
  }

  @ExceptionHandler(BusinessException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String business(BusinessException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error/problem";
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String missing(NotFoundException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error/problem";
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  public String conflict(Exception e, Model m) {
    m.addAttribute("message", "Another request changed this record. Reload and try again.");
    return "error/problem";
  }
}
