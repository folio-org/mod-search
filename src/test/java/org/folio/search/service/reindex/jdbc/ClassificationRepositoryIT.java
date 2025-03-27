package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.BatchUpdateException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.AggregatedBatchUpdateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClassificationRepositoryIT {

  private @MockitoSpyBean JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private ClassificationRepository repository;

  @BeforeEach
  void setUp() {
    var properties = new ReindexConfigurationProperties();
    var jsonConverter = new JsonConverter(new ObjectMapper());
    repository = spy(new ClassificationRepository(jdbcTemplate, jsonConverter, context, properties));
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
  @Sql("/sql/populate-classifications.sql")
  void deleteByInstanceIds_oneClassificationRemovedAndOneInstanceCounterDecremented() {
    var instanceIds = List.of("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "b3bae8a9-cfb1-4afe-83d5-2cdae4580e07");

    // act
    repository.deleteByInstanceIds(instanceIds, null);

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(1)
      .extracting("number", "instances")
      .contains(
        tuple("Sci-Fi", List.of(
          Map.of("count", 1, "shared", true, "tenantId", "consortium"),
          Map.of("count", 1, "shared", false, "tenantId", "member_tenant"))
        )
      );
  }

  @Test
  @Sql("/sql/populate-classifications.sql")
  void deleteByInstanceIds_OneInstanceCounterDecrementedForSharedInstance() {
    var instanceIds = List.of("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    // act
    repository.deleteByInstanceIds(instanceIds, "member_tenant");

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(2)
      .extracting("number", "instances")
      .contains(
        tuple("Sci-Fi", List.of(
          Map.of("count", 2, "shared", true, "tenantId", "consortium"),
          Map.of("count", 1, "shared", false, "tenantId", "member_tenant"))
        ),
        tuple("Genre", List.of(
          Map.of("count", 1, "shared", true, "tenantId", "consortium"))
        )
      );
  }

  @Test
  void saveAll() {
    var entities = Set.of(classificationEntity("1"), classificationEntity("2"));
    var entityRelations = List.of(
      classificationRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      classificationRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      classificationRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(2)
      .extracting("number", "instances")
      .contains(
        tuple("number1", List.of(Map.of("count", 1, "shared", false, "tenantId", TENANT_ID))),
        tuple("number2", List.of(Map.of("count", 2, "shared", false, "tenantId", TENANT_ID))));
  }

  @Test
  void saveAll_batchFailure() {
    doThrow(InvalidDataAccessResourceUsageException.class)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    saveAll();
  }

  @Test
  void saveAll_throwPessimisticLockingFailureException() {
    BatchUpdateException batchUpdateException = new BatchUpdateException("Nested exception", new int[0]);
    var aggregatedException = new AggregatedBatchUpdateException(new int[0][0], batchUpdateException);
    var exception = new PessimisticLockingFailureException("Test exception", aggregatedException);
    doThrow(exception)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    saveAll();

    verify(jdbcTemplate, times(2)).update(any(), any(), any(), any());
  }

  private Map<String, Object> classificationEntity(String id) {
    return Map.of(
      "id", id,
      CLASSIFICATION_NUMBER_FIELD, "number" + id,
      CLASSIFICATION_TYPE_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6"
    );
  }

  private Map<String, Object> classificationRelation(String instanceId, String classificationId) {
    return Map.of(
      "instanceId", instanceId,
      "classificationId", classificationId,
      "tenantId", TENANT_ID,
      "shared", false
    );
  }
}
