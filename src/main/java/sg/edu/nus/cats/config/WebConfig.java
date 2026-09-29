package sg.edu.nus.cats.config;

import jakarta.servlet.http.HttpSession;
import java.util.Set;
import sg.edu.nus.cats.model.enums.Role;

public class WebConfig {
  public record SessionUser(Long id, String name, Set<Role> roles) {
    public boolean manager() {
      return roles.contains(Role.MANAGER);
    }

    public boolean admin() {
      return roles.contains(Role.ADMIN);
    }

    public boolean employee() {
      return roles.contains(Role.EMPLOYEE);
    }
  }

  public static SessionUser user(HttpSession session) {
    return (SessionUser) session.getAttribute("user");
  }
}
