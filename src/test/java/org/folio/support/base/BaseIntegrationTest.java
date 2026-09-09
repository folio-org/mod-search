package org.folio.support.base;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.support.utils.TestUtils.removeEnvProperty;
import static org.folio.support.utils.TestUtils.setEnvProperty;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.core.ConditionTimeoutException;
import org.folio.search.SearchApplication;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
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

  /**
   * Locks a sub-resource for test setup purposes, retrying for a short while if the lock is
   * currently held. In tests, {@code ScheduledInstanceSubResourcesService} runs on a very short
   * fixed delay and can briefly hold the same lock right after a tenant is enabled, so a single
   * lock attempt right after {@code enableTenant} is inherently racy.
   *
   * @return the fencing timestamp for the acquired lock
   * @throws IllegalStateException if the lock could not be acquired within the retry window,
   *     which points to a genuine problem (e.g. a stuck/preempted lock) rather than transient
   *     contention with the scheduler
   */
  protected static Timestamp lockSubResourceWithRetry(SubResourcesLockRepository lockRepository,
                                                       ReindexEntityType entityType, String tenantId) {
    var lockResult = new AtomicReference<>(Optional.<Timestamp>empty());
    try {
      await("lock sub-resource [%s] for tenant [%s]".formatted(entityType, tenantId))
        .atMost(TEN_SECONDS)
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .until(() -> {
          lockResult.set(lockRepository.lockSubResource(entityType, tenantId));
          return lockResult.get().isPresent();
        });
    } catch (ConditionTimeoutException e) {
      throw new IllegalStateException(
        "Unexpected state of database: unable to lock required resource [%s] for tenant [%s]"
          .formatted(entityType, tenantId), e);
    }
    return lockResult.get().orElseThrow();
  }

  /**
   * Runs {@code action} while holding the lock for the given sub-resource, acquired via
   * {@link #lockSubResourceWithRetry}, and releases it afterward even if {@code action} throws.
   */
  protected static void withLockedSubResource(SubResourcesLockRepository lockRepository,
                                              ReindexEntityType entityType, String tenantId, Runnable action) {
    var timestamp = lockSubResourceWithRetry(lockRepository, entityType, tenantId);
    try {
      action.run();
    } finally {
      lockRepository.unlockSubResourceFenced(entityType, timestamp, tenantId, timestamp);
    }
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
