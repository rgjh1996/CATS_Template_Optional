package sg.edu.nus.cats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import sg.edu.nus.cats.model.Course;

@RepositoryRestResource(path = "courses", collectionResourceRel = "courses")
public interface CourseRepository extends JpaRepository<Course, Long> {}
