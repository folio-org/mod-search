package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.folio.search.model.entity.FailedIndexingEvent;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.config.TestNoOpCacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@Import(TestNoOpCacheConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FailedIndexingEventRepositoryIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private FailedIndexingEventRepository repository;

  @BeforeEach
  void setUp() {
    repository = new FailedIndexingEventRepository(jdbcTemplate, context);
    when(context.getFolioModuleMetadata()).thenReturn(new FolioModuleMetadata() {
      @Override
      public String getModuleName() {
        return null;
      }

      @Override
      public String getDBSchemaName(String tenantId) {
        return "public";
      }
    });
    when(context.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void save_positive_storesEvent() {
    var failedEvent = failedEvent(Instant.now());

    repository.save(failedEvent);

    var stored = jdbcTemplate.queryForMap(
      "SELECT resource_type, resource_id, event_type, event->>'id' AS event_id, error_message, status, error_type "
      + "FROM public.failed_indexing_event WHERE id = ?", failedEvent.id());
    assertThat(stored)
      .containsEntry("resource_type", "item")
      .containsEntry("resource_id", "item-id")
      .containsEntry("event_type", "CREATE")
      .containsEntry("event_id", "item-id")
      .containsEntry("error_message", "index is read-only")
      .containsEntry("status", 400)
      .containsEntry("error_type", "mapper_parsing_exception");
  }

  @Test
  void deleteOlderThan_positive_deletesOnlyExpiredEvents() {
    var expired = failedEvent(Instant.now().minus(31, ChronoUnit.DAYS));
    var recent = failedEvent(Instant.now().minus(1, ChronoUnit.DAYS));
    repository.save(expired);
    repository.save(recent);

    var deleted = repository.deleteOlderThan(Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));

    assertThat(deleted).isEqualTo(1);
    var remainingIds = jdbcTemplate.queryForList("SELECT id FROM public.failed_indexing_event", UUID.class);
    assertThat(remainingIds).contains(recent.id()).doesNotContain(expired.id());
  }

  private static FailedIndexingEvent failedEvent(Instant createdDate) {
    return new FailedIndexingEvent(UUID.randomUUID(), "item", "item-id", "CREATE", "{\"id\": \"item-id\"}",
      "index is read-only", 400, "mapper_parsing_exception", Timestamp.from(createdDate));
  }
}
