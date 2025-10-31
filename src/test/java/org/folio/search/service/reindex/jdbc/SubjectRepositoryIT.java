package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
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
import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
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
class SubjectRepositoryIT {

  private @MockitoSpyBean JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
  private SubjectRepository repository;
  private ReindexConfigurationProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ReindexConfigurationProperties();
    var jsonConverter = new JsonConverter(new ObjectMapper());
    repository = spy(new SubjectRepository(jdbcTemplate, jsonConverter, context, properties));
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
  void getUploadRanges_returnList() {
    // arrange
    properties.setUploadRangeLevel(1);

    // act
    var ranges = repository.createUploadRanges();

    // assert
    assertThat(ranges)
      .hasSize(16)
      .are(new Condition<>(range -> range.getEntityType() == ReindexEntityType.SUBJECT, "subject range"))
      .extracting(UploadRangeEntity::getLower, UploadRangeEntity::getUpper)
      .startsWith(tuple("0", "1"))
      .contains(tuple("a", "b"))
      .endsWith(tuple("f", "x"));
  }

  @Test
  @Sql("/sql/populate-subjects.sql")
  void fetchBy_returnListOfMaps() {
    // act
    var ranges = repository.fetchByIdRange("20", "21");

    // assert
    assertThat(ranges)
      .hasSize(2)
      .allMatch(map -> map.keySet().containsAll(List.of("id", "value", "authorityId", "instances")))
      .extracting("value", "authorityId", "sourceId", "typeId")
      .containsExactlyInAnyOrder(
        tuple(
          "Alternative History",
          null,
          "a5a0b02e-c868-4074-ab01-348a4e87fd9f",
          "b36c89f9-79fe-4a0a-bc13-02d95e032c08"),
        tuple(
          "History",
          "79144653-7a98-4dfb-aa6a-13ad49e80952",
          null,
          null)
      );
  }

  @Test
  @Sql("/sql/populate-subjects.sql")
  void deleteByInstanceIds_oneSubjectRemovedAndOneInstanceCounterDecremented() {
    var instanceIds = List.of("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "b3bae8a9-cfb1-4afe-83d5-2cdae4580e07");

    // act
    repository.deleteByInstanceIds(instanceIds, null);

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(16)
      .extracting("value", "instances")
      .contains(
        tuple("Sci-Fi", List.of(
          Map.of("count", 1, "shared", true, "tenantId", "consortium"),
          Map.of("count", 1, "shared", false, "tenantId", MEMBER_TENANT_ID))));
  }

  @Test
  void saveAll() {
    var entities = Set.of(subjectEntity("1"), subjectEntity("2"));
    var entityRelations = List.of(
      subjectRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      subjectRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      subjectRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    // assert
    var ranges = repository.fetchByIdRange("0", "50");
    assertThat(ranges)
      .hasSize(2)
      .extracting("value", "instances")
      .contains(
        tuple("value1", List.of(Map.of("count", 1, "shared", false, "tenantId", TENANT_ID))),
        tuple("value2", List.of(Map.of("count", 2, "shared", false, "tenantId", TENANT_ID))));
  }

  @Test
  void saveAll_throwPessimisticLockingFailureException() {
    BatchUpdateException batchUpdateException = new BatchUpdateException("Nested exception", new int[0]);
    var aggregatedException = new AggregatedBatchUpdateException(new int[0][0], batchUpdateException);
    var exception = new PessimisticLockingFailureException("Test exception", aggregatedException);
    doThrow(exception)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    var entities = Set.of(subjectEntity("1"), subjectEntity("2"));
    var entityRelations = List.of(
      subjectRelation("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11", "1"),
      subjectRelation("b3bae8a9-cfb1-4afe-83d5-2cdae4580e07", "2"),
      subjectRelation("9ec55e4f-6a76-427c-b47b-197046f44a54", "2"));

    repository.saveAll(new ChildResourceEntityBatch(entities, entityRelations));

    verify(jdbcTemplate, times(2)).update(any(), any(), any(), any(), any(), any());
  }

  @Test
  void saveAll_batchFailure() {
    doThrow(InvalidDataAccessResourceUsageException.class)
      .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

    saveAll();
  }

  private Map<String, Object> subjectEntity(String id) {
    return Map.of(
      "id", id,
      SUBJECT_VALUE_FIELD, "value" + id,
      AUTHORITY_ID_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6",
      SUBJECT_SOURCE_ID_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5",
      SUBJECT_TYPE_ID_FIELD, "b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d4"
    );
  }

  private Map<String, Object> subjectRelation(String instanceId, String subjectId) {
    return Map.of(
      "instanceId", instanceId,
      "subjectId", subjectId,
      "tenantId", TENANT_ID,
      "shared", false
    );
  }
}
