package com.example.mdm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class DatasourceConfigurationTest {

  @Test
  void defaultDatasourceUsesEnvironmentProvidedMySqlUrl() throws IOException {
    var properties = new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .get(0);

    assertThat(properties.getProperty("spring.datasource.url"))
        .isEqualTo("${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:${MYSQL_PORT:3306}/${MYSQL_DATABASE:mdm_mpv}}");
  }
}
