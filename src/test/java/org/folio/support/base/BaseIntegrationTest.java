package org.folio.support.base;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.folio.support.utils.TestUtils.removeEnvProperty;
import static org.folio.support.utils.TestUtils.setEnvProperty;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.folio.search.SearchApplication;
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.support.api.InventoryApi;
import org.folio.support.extension.EnableElasticSearch;
import org.folio.support.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@EnableKafka
@EnablePostgres
@EnableElasticSearch
@AutoConfigureMockMvc
@SpringBootTest(classes = SearchApplication.class,
  properties = {
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer"
  })
@Import({
  BaseIntegrationTest.KafkaTemplateTestConfiguration.class})
public abstract class BaseIntegrationTest extends BaseSharedTest {

  private static final WireMockServer WIRE_MOCK_SERVER;

  static {
    WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    WIRE_MOCK_SERVER.start();
  }

  @DynamicPropertySource
  static void okapiProperties(DynamicPropertyRegistry registry) {
    registry.add("folio.okapi-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @BeforeAll
  static void setUpDefaultTenant(
    @Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, Object> kafkaTemplate,
    @Autowired ObjectMapper objectMapper,
    @Autowired RestHighLevelClient restHighLevelClient,
    @Autowired CacheManager cacheManager) {
    setEnvProperty("folio-test");
    BaseSharedTest.mockMvc = mockMvc;
    BaseSharedTest.kafkaTemplate = kafkaTemplate;
    BaseSharedTest.objectMapper = objectMapper;
    BaseSharedTest.inventoryApi = new InventoryApi(kafkaTemplate);
    BaseSharedTest.elasticClient = restHighLevelClient;
    BaseSharedTest.cacheManager = cacheManager;
    BaseSharedTest.okapi = new OkapiConfiguration(WIRE_MOCK_SERVER, WIRE_MOCK_SERVER.port());
  }

  @BeforeAll
  static void resetWireMock() {
    WIRE_MOCK_SERVER.resetAll();
  }

  @BeforeAll
  static void cleanUpCaches(@Autowired CacheManager cacheManager) {
    TestUtils.cleanUpCaches(cacheManager);
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }

  @TestConfiguration
  public static class KafkaTemplateTestConfiguration {

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaObjectTemplate(ProducerFactory<String, Object> producerFactory) {
      return new KafkaTemplate<>(producerFactory);
    }
  }
}
