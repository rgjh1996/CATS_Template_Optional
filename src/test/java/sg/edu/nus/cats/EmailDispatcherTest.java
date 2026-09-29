package sg.edu.nus.cats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import sg.edu.nus.cats.model.EmailNotification;
import sg.edu.nus.cats.model.enums.EmailStatus;
import sg.edu.nus.cats.repository.EmailNotificationRepository;
import sg.edu.nus.cats.service.EmailDispatcher;

class EmailDispatcherTest {
  EmailNotification notification() {
    var n = new EmailNotification();
    n.setRecipient("employee@example.test");
    n.setSubject("Course application APPROVED");
    n.setBody("Reason: relevant training. Sign in: http://localhost:8080/employee/login");
    return n;
  }

  @Test
  void deliversMessageOverSmtp() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var executor = Executors.newSingleThreadExecutor()) {
      server.setSoTimeout(10000);
      Future<String> received =
          executor.submit(
              () -> {
                try (var socket = server.accept();
                    var reader =
                        new BufferedReader(
                            new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                    var writer =
                        new BufferedWriter(
                            new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                  socket.setSoTimeout(10000);
                  writer.write("220 localhost test SMTP\r\n");
                  writer.flush();
                  StringBuilder data = new StringBuilder();
                  boolean inData = false;
                  for (String line; (line = reader.readLine()) != null; ) {
                    if (inData && !line.equals(".")) {
                      data.append(line).append('\n');
                      continue;
                    }
                    if (inData) {
                      inData = false;
                      writer.write("250 accepted\r\n");
                    } else if (line.startsWith("DATA")) {
                      inData = true;
                      writer.write("354 send message\r\n");
                    } else if (line.startsWith("QUIT")) {
                      writer.write("221 goodbye\r\n");
                      writer.flush();
                      break;
                    } else writer.write("250 OK\r\n");
                    writer.flush();
                  }
                  return data.toString();
                }
              });
      var repo = mock(EmailNotificationRepository.class);
      var n = notification();
      when(repo.findTop20ByStatusInAndAttemptsLessThanOrderByCreatedAt(anyCollection(), eq(5)))
          .thenReturn(List.of(n));
      var sender = new JavaMailSenderImpl();
      sender.setHost(InetAddress.getLoopbackAddress().getHostAddress());
      sender.setPort(server.getLocalPort());
      new EmailDispatcher(repo, sender, Clock.systemUTC(), "cats@example.test").dispatch();
      assertThat(n.getStatus()).isEqualTo(EmailStatus.SENT);
      assertThat(n.getSentAt()).isNotNull();
      assertThat(received.get(10, TimeUnit.SECONDS))
          .contains("employee@example.test", "APPROVED", "relevant training", "/employee/login");
    }
  }

  @Test
  void failedDeliveryCanBeRetried() {
    var repo = mock(EmailNotificationRepository.class);
    var sender = mock(JavaMailSender.class);
    var n = notification();
    when(repo.findTop20ByStatusInAndAttemptsLessThanOrderByCreatedAt(anyCollection(), eq(5)))
        .thenReturn(List.of(n));
    doThrow(new MailSendException("test offline"))
        .doNothing()
        .when(sender)
        .send(any(SimpleMailMessage.class));
    var worker = new EmailDispatcher(repo, sender, Clock.systemUTC(), "cats@example.test");
    worker.dispatch();
    assertThat(n.getStatus()).isEqualTo(EmailStatus.FAILED);
    assertThat(n.getAttempts()).isEqualTo(1);
    worker.dispatch();
    assertThat(n.getStatus()).isEqualTo(EmailStatus.SENT);
    assertThat(n.getAttempts()).isEqualTo(2);
  }
}
