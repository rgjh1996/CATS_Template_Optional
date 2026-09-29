package sg.edu.nus.cats.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);

  List<User> findByManagerIdOrderByName(Long managerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id=:id")
  Optional<User> lockById(Long id);

  @EntityGraph(attributePaths = {"manager", "roles"})
  java.util.List<User> findAll();
}
