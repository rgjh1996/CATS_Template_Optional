package sg.edu.nus.cats.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.cats.exception.BusinessException;
import sg.edu.nus.cats.model.User;
import sg.edu.nus.cats.model.enums.Role;
import sg.edu.nus.cats.repository.UserRepository;

@Service
public class AuthenticationService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AuthenticationService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional(readOnly = true)
  public User authenticate(String username, String password, boolean admin) {
    User user =
        users
            .findByUsername(username.trim())
            .orElseThrow(() -> new BusinessException("Invalid username or password."));
    if (!user.getEnabled()
        || !encoder.matches(password, user.getPasswordHash())
        || (admin
            ? !user.hasRole(Role.ADMIN)
            : !(user.hasRole(Role.EMPLOYEE) || user.hasRole(Role.MANAGER))))
      throw new BusinessException("Invalid credentials for this login page.");
    return user;
  }
}
