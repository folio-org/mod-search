package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.CLASSIFICATION;
import static org.folio.search.model.types.ReindexEntityType.CONTRIBUTOR;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.SUBJECT;
import static org.folio.search.model.types.ReindexStatus.MERGE_COMPLETED;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_COMPLETED;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_FAILED;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_IN_PROGRESS;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReindexStatusRepositoryIT {

  private static final UUID REINDEX_ID = UUID.fromString("180a92d3-6829-44ad-a81c-2cdd61650e4d");

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockBean FolioExecutionContext context;
  private ReindexStatusRepository repository;

  @BeforeEach
  void setUp() {
    repository = new ReindexStatusRepository(context, jdbcTemplate);
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
  void getUploadRanges_returnEmptyList_whenNoUploadRangesAndNotPopulate() {
    // act
    var statuses = repository.getReindexStatuses(REINDEX_ID);

    // assert
    assertThat(statuses).isEmpty();
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void getUploadRanges_returnList() {
    // act
    var statuses = repository.getReindexStatuses(REINDEX_ID);

    // assert
    assertThat(statuses)
      .hasSize(4)
      .are(new Condition<>(status -> REINDEX_ID.equals(status.getReindexId())
        && status.getTotalMergeRanges() == 3
        && "2024-04-01 01:37:34.15755".equals(status.getStartTimeMerge().toString())
        && "2024-04-01 01:37:35.15755".equals(status.getEndTimeMerge().toString()), "common properties match"))
      .extracting(ReindexStatusEntity::getEntityType, ReindexStatusEntity::getStatus,
        ReindexStatusEntity::getProcessedMergeRanges, ReindexStatusEntity::getTotalUploadRanges,
        ReindexStatusEntity::getProcessedUploadRanges, ReindexStatusEntity::getStartTimeUpload,
        ReindexStatusEntity::getEndTimeUpload)
      .containsExactly(tuple(CONTRIBUTOR, MERGE_COMPLETED, 3, 0, 0, null, null),
        tuple(SUBJECT, UPLOAD_IN_PROGRESS, 2, 2, 1, Timestamp.valueOf("2024-04-01 01:37:36.15755"), null),
        tuple(INSTANCE, UPLOAD_COMPLETED, 3, 2, 2, Timestamp.valueOf("2024-04-01 01:37:36.15755"),
          Timestamp.valueOf("2024-04-01 01:37:37.15755")),
        tuple(CLASSIFICATION, UPLOAD_FAILED, 3, 2, 1, Timestamp.valueOf("2024-04-01 01:37:36.15755"),
          Timestamp.valueOf("2024-04-01 01:37:37.15755")));
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setReindexUploadFailed() {
    // act
    repository.setReindexUploadFailed(REINDEX_ID, SUBJECT);

    // assert
    var statuses = repository.getReindexStatuses(REINDEX_ID);
    assertThat(statuses)
      .hasSize(4)
      .filteredOn(reindexStatus -> SUBJECT.equals(reindexStatus.getEntityType()))
      .anyMatch(reindexStatus -> UPLOAD_FAILED.equals(reindexStatus.getStatus())
        && reindexStatus.getEndTimeUpload() != null);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void addReindexCounts_shouldChangeStatus() {
    // act
    repository.addReindexCounts(REINDEX_ID, SUBJECT, 0, 1);

    // assert
    var statuses = repository.getReindexStatuses(REINDEX_ID);
    assertThat(statuses)
      .hasSize(4)
      .filteredOn(reindexStatus -> SUBJECT.equals(reindexStatus.getEntityType()))
      .anyMatch(reindexStatus -> UPLOAD_COMPLETED.equals(reindexStatus.getStatus())
        && reindexStatus.getEndTimeUpload() != null);
  }
}
