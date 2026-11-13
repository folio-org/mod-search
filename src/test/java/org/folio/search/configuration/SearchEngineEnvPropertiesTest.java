package org.folio.search.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

@UnitTest
class SearchEngineEnvPropertiesTest {

  private static Stream<Arguments> searchEngineVariables() {
    return Stream.of(
      Arguments.of("spring.search-engine.uris", "SEARCH_ENGINE_URL", "ELASTICSEARCH_URL", "http://localhost:9200"),
      Arguments.of("spring.search-engine.username", "SEARCH_ENGINE_USERNAME", "ELASTICSEARCH_USERNAME", ""),
      Arguments.of("spring.search-engine.password", "SEARCH_ENGINE_PASSWORD", "ELASTICSEARCH_PASSWORD", ""),
      Arguments.of("spring.search-engine.compression-enabled", "SEARCH_ENGINE_COMPRESSION_ENABLED",
        "ELASTICSEARCH_COMPRESSION_ENABLED", "true"),
      Arguments.of("spring.search-engine.is-elasticsearch", "SEARCH_ENGINE_IS_ELASTICSEARCH",
        "ELASTICSEARCH_SERVER", "false"),
      Arguments.of("spring.search-engine.search-retry-attempts", "SEARCH_ENGINE_SEARCH_RETRY_ATTEMPTS",
        "OPENSEARCH_SEARCH_RETRY_ATTEMPTS", "3"),
      Arguments.of("spring.search-engine.search-retry-interval-ms", "SEARCH_ENGINE_SEARCH_RETRY_INTERVAL_MS",
        "OPENSEARCH_SEARCH_RETRY_INTERVAL_MS", "500"),
      Arguments.of("spring.search-engine.max-conn-per-route", "SEARCH_ENGINE_MAX_CONN_PER_ROUTE",
        "OPENSEARCH_MAX_CONN_PER_ROUTE", "25"),
      Arguments.of("spring.search-engine.max-conn-total", "SEARCH_ENGINE_MAX_CONN_TOTAL",
        "OPENSEARCH_MAX_CONN_TOTAL", "100"),
      Arguments.of("spring.search-engine.connection-time-to-live", "SEARCH_ENGINE_CONNECTION_TIME_TO_LIVE",
        "OPENSEARCH_CONNECTION_TIME_TO_LIVE", "5m"),
      Arguments.of("spring.search-engine.max-idle-time", "SEARCH_ENGINE_MAX_IDLE_TIME",
        "OPENSEARCH_MAX_IDLE_TIME", "60s"),
      Arguments.of("spring.search-engine.validate-after-inactivity", "SEARCH_ENGINE_VALIDATE_AFTER_INACTIVITY",
        "OPENSEARCH_VALIDATE_AFTER_INACTIVITY", "")
    );
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @MethodSource("searchEngineVariables")
  @DisplayName("getProperty_positive_defaultWhenNoVariableSet")
  void getProperty_positive_defaultWhenNoVariableSet(String property, String newVariable, String legacyVariable,
                                                     String defaultValue) throws IOException {
    var environment = environmentWith(Map.of());

    assertThat(environment.getProperty(property)).isEqualTo(defaultValue);
  }

  @ParameterizedTest(name = "[{index}] {0}: {2} fallback")
  @MethodSource("searchEngineVariables")
  @DisplayName("getProperty_positive_legacyVariableUsedAsFallback")
  void getProperty_positive_legacyVariableUsedAsFallback(String property, String newVariable, String legacyVariable,
                                                         String defaultValue) throws IOException {
    var environment = environmentWith(Map.of(legacyVariable, "legacy-value"));

    assertThat(environment.getProperty(property)).isEqualTo("legacy-value");
  }

  @ParameterizedTest(name = "[{index}] {0}: {1} wins")
  @MethodSource("searchEngineVariables")
  @DisplayName("getProperty_positive_newVariableTakesPrecedenceOverLegacy")
  void getProperty_positive_newVariableTakesPrecedenceOverLegacy(String property, String newVariable,
                                                                 String legacyVariable, String defaultValue)
    throws IOException {
    var environment = environmentWith(Map.of(newVariable, "new-value", legacyVariable, "legacy-value"));

    assertThat(environment.getProperty(property)).isEqualTo("new-value");
  }

  @Test
  @DisplayName("getProperty_positive_newVariableUsedWithoutLegacy")
  void getProperty_positive_newVariableUsedWithoutLegacy() throws IOException {
    var environment = environmentWith(Map.of("SEARCH_ENGINE_URL", "https://search.example.com:9200"));

    assertThat(environment.getProperty("spring.search-engine.uris")).isEqualTo("https://search.example.com:9200");
  }

  private static StandardEnvironment environmentWith(Map<String, Object> variables) throws IOException {
    var environment = new StandardEnvironment();
    var sources = environment.getPropertySources();
    // drop the real system environment so the test does not depend on the machine it runs on
    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    sources.addLast(new MapPropertySource("test-variables", variables));
    for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
      sources.addLast(source);
    }
    return environment;
  }
}
