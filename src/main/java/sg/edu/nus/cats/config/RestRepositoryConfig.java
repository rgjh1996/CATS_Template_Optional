package sg.edu.nus.cats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.RepositoryDetectionStrategy;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import sg.edu.nus.cats.model.Course;

@Configuration
public class RestRepositoryConfig implements RepositoryRestConfigurer {
  @Override
  public void configureRepositoryRestConfiguration(
      RepositoryRestConfiguration config, CorsRegistry cors) {
    config.setBasePath("/data");
    config.setRepositoryDetectionStrategy(
        RepositoryDetectionStrategy.RepositoryDetectionStrategies.ANNOTATED);
    config.exposeIdsFor(Course.class);
    config.setDefaultPageSize(10);
    config.setMaxPageSize(25);
    config
        .getExposureConfiguration()
        .forDomainType(Course.class)
        .withItemExposure(
            (metadata, methods) ->
                methods.disable(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE))
        .withCollectionExposure((metadata, methods) -> methods.disable(HttpMethod.POST))
        .withAssociationExposure(
            (metadata, methods) ->
                methods.disable(
                    HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.DELETE));
  }
}
