package sg.edu.nus.cats.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import sg.edu.nus.cats.model.*;
import sg.edu.nus.cats.model.enums.*;

public interface TrainingProviderRepository extends JpaRepository<TrainingProvider, Long> {
  List<TrainingProvider> findByActiveTrueOrderByName();
}
