package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;
import static org.folio.search.utils.TestConstants.TENANT_ID;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.AggregatedBatchUpdateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContributorRepositoryIT {

  private @SpyBean JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private ContributorRepository repository;
  private ReindexConfigurationProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ReindexConfigurationProperties();
    var jsonConverter = new JsonConverter(new ObjectMapper());
    repository = spy(new ContributorRepository(jdbcTemplate, jsonConverter, context, properties));
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
  @Sql("/sql/populate-contributors.sql")
  void deleteByInstanceIds_oneContributorRemovedAndOneInstanceCounterDecremented() {
    var instanceIds = List.of("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "b3bae8a9-cfb1-4afe-83d5-2cdae4580e07");

    // act
    repository.deleteByInstanceIds(instanceIds);

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(1)
      .extracting("name", "instances")
      .contains(
        tuple("Sci-Fi", List.of(
          Map.of("count", 1, "shared", true, "tenantId", "consortium",
            "typeId", List.of("aab8fff4-49c6-4578-979e-439b6ba3600b")),
            Map.of("count", 1, "shared", false, "tenantId", "member_tenant",
              "typeId", List.of("9ec55e4f-6a76-427c-b47b-197046f44a53")))));
  }

  @Test
  void saveAll() {
    var entities = Set.of(contributorEntity("1"), contributorEntity("2"));
    var entityRelations = List.of(
      contributorRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      contributorRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      contributorRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(2)
      .extracting("name", "instances")
      .contains(
        tuple("name1", List.of(Map.of("count", 1, "shared", false, "tenantId", TENANT_ID,
          "typeId", List.of("b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5")))),
        tuple("name2", List.of(Map.of("count", 2, "shared", false, "tenantId", TENANT_ID,
          "typeId", List.of("b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5")))));
  }

  @Test
  void saveAll_throwPessimisticLockingFailureException() {
    BatchUpdateException batchUpdateException = new BatchUpdateException("Nested exception", new int[0]);
    var aggregatedException = new AggregatedBatchUpdateException(new int[0][0], batchUpdateException);
    var exception = new PessimisticLockingFailureException("Test exception", aggregatedException);
    doThrow(exception)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    var entities = Set.of(contributorEntity("1"), contributorEntity("2"));
    var entityRelations = List.of(
      contributorRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      contributorRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      contributorRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(entities, entityRelations);

    verify(jdbcTemplate, times(2)).update(any(), any(), any(), any(), any());
  }

  @Test
  void saveAll_batchFailure() {
    doThrow(InvalidDataAccessResourceUsageException.class)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    saveAll();
  }

  private Map<String, Object> contributorEntity(String id) {
    return Map.of(
      "id", id,
      "name", "name" + id,
      "nameTypeId", "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6",
      AUTHORITY_ID_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5"
    );
  }

  private Map<String, Object> contributorRelation(String instanceId, String contributorId) {
    return Map.of(
      "instanceId", instanceId,
      "contributorId", contributorId,
      CONTRIBUTOR_TYPE_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5",
      "tenantId", TENANT_ID,
      "shared", false
    );
  }
}
