package org.folio.search.service.reindex.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.CLASSIFICATION;
import static org.folio.search.model.types.ReindexEntityType.CONTRIBUTOR;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.model.types.ReindexEntityType.SUBJECT;
import static org.folio.search.model.types.ReindexStatus.MERGE_COMPLETED;
import static org.folio.search.model.types.ReindexStatus.MERGE_IN_PROGRESS;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_COMPLETED;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_FAILED;
import static org.folio.search.model.types.ReindexStatus.UPLOAD_IN_PROGRESS;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@JdbcTest
@EnablePostgres
@AutoConfigureJson
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReindexStatusRepositoryIT {

  private @Autowired JdbcTemplate jdbcTemplate;
  private @MockitoBean FolioExecutionContext context;
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
    var statuses = repository.getReindexStatuses();

    // assert
    assertThat(statuses).isEmpty();
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void getUploadRanges_returnList() {
    // act
    var statuses = repository.getReindexStatuses();

    // assert
    assertThat(statuses)
      .hasSize(4)
      .are(new Condition<>(status -> status.getTotalMergeRanges() == 3
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
    repository.setReindexUploadFailed(SUBJECT);

    // assert
    var statuses = repository.getReindexStatuses();

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
    repository.addReindexCounts(SUBJECT, 0, 1);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .hasSize(4)
      .filteredOn(reindexStatus -> SUBJECT.equals(reindexStatus.getEntityType()))
      .anyMatch(reindexStatus -> UPLOAD_COMPLETED.equals(reindexStatus.getStatus())
        && reindexStatus.getEndTimeUpload() != null);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setMergeInProgress() {
    var entityTypes = Set.of(INSTANCE);

    repository.setMergeInProgress(entityTypes);

    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .hasSize(4)
      .filteredOn(reindexStatus -> entityTypes.contains(reindexStatus.getEntityType()))
      .hasSize(1)
      .allMatch(reindexStatus -> reindexStatus.getStatus() == MERGE_IN_PROGRESS);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void truncate_shouldRemoveAllRecords() {
    // act
    repository.truncate();

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses).isEmpty();
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void delete_shouldRemoveSpecificEntityType() {
    // act
    repository.delete(INSTANCE);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .hasSize(3)
      .noneMatch(status -> status.getEntityType() == INSTANCE);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setMergeReindexStarted_shouldSetTotalRangesAndStartTime() {
    // given
    int totalRanges = 100;

    // act
    repository.setMergeReindexStarted(INSTANCE, totalRanges);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(status -> status.getEntityType() == INSTANCE)
      .hasSize(1)
      .first()
      .satisfies(status -> {
        assertThat(status.getTotalMergeRanges()).isEqualTo(totalRanges);
        assertThat(status.getStartTimeMerge()).isNotNull();
      });
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setUploadReindexStarted_shouldSetTotalRangesAndStartTime() {
    // given
    int totalRanges = 50;

    // act
    repository.setUploadReindexStarted(CONTRIBUTOR, totalRanges);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(status -> status.getEntityType() == CONTRIBUTOR)
      .hasSize(1)
      .first()
      .satisfies(status -> {
        assertThat(status.getTotalUploadRanges()).isEqualTo(totalRanges);
        assertThat(status.getStartTimeUpload()).isNotNull();
      });
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setMergeReindexFailed_shouldUpdateStatusAndEndTimeForMultipleEntities() {
    // given
    var entityTypes = java.util.List.of(INSTANCE, CONTRIBUTOR);

    // act
    repository.setMergeReindexFailed(entityTypes);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(status -> entityTypes.contains(status.getEntityType()))
      .hasSize(2)
      .allMatch(status -> status.getStatus() == org.folio.search.model.types.ReindexStatus.MERGE_FAILED)
      .allMatch(status -> status.getEndTimeMerge() != null);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setStagingStarted_shouldSetStartTimeForMultipleEntities() {
    // given
    var entityTypes = java.util.List.of(INSTANCE, CONTRIBUTOR);

    // act
    repository.setStagingStarted(entityTypes);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(s -> entityTypes.contains(s.getEntityType()))
      .hasSize(2)
      .allMatch(s -> s.getStartTimeStaging() != null);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setStagingCompleted_shouldSetEndTimeForMultipleEntities() {
    // given
    var entityTypes = java.util.List.of(INSTANCE, SUBJECT);

    // act
    repository.setStagingCompleted(entityTypes);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(s -> entityTypes.contains(s.getEntityType()))
      .hasSize(2)
      .allMatch(s -> s.getEndTimeStaging() != null);
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void setStagingFailed_shouldSetStatusAndEndTimeForMultipleEntities() {
    // given
    var entityTypes = java.util.List.of(INSTANCE, CONTRIBUTOR, CLASSIFICATION);

    // act
    repository.setStagingFailed(entityTypes);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .filteredOn(s -> entityTypes.contains(s.getEntityType()))
      .hasSize(3)
      .allMatch(s -> s.getStatus() == org.folio.search.model.types.ReindexStatus.STAGING_FAILED)
      .allMatch(s -> s.getEndTimeStaging() != null);
  }

  @Test
  void saveReindexStatusRecords_shouldInsertNewRecords() {
    // given
    var entity1 = new ReindexStatusEntity(INSTANCE, MERGE_IN_PROGRESS);
    entity1.setTargetTenantId("target_tenant");
    entity1.setTotalMergeRanges(100);

    var entity2 = new ReindexStatusEntity(CONTRIBUTOR, MERGE_COMPLETED);
    entity2.setTotalMergeRanges(50);
    entity2.setProcessedMergeRanges(50);

    var statusRecords = java.util.List.of(entity1, entity2);

    // act
    repository.saveReindexStatusRecords(statusRecords);

    // assert
    var statuses = repository.getReindexStatuses();
    assertThat(statuses)
      .hasSize(2)
      .extracting(ReindexStatusEntity::getEntityType, ReindexStatusEntity::getStatus)
      .containsExactlyInAnyOrder(
        tuple(INSTANCE, MERGE_IN_PROGRESS),
        tuple(CONTRIBUTOR, MERGE_COMPLETED)
      );

    assertThat(statuses)
      .filteredOn(s -> s.getEntityType() == INSTANCE)
      .first()
      .satisfies(s -> {
        assertThat(s.getTargetTenantId()).isEqualTo("target_tenant");
        assertThat(s.getTotalMergeRanges()).isEqualTo(100);
      });
  }

  @Test
  @Sql("/sql/populate-reindex-status.sql")
  void isMergeCompleted_shouldReturnFalseWhenNotAllCompleted() {
    // act
    var result = repository.isMergeCompleted();

    // assert - based on test data, not all are completed
    assertThat(result).isFalse();
  }

  @Test
  void isMergeCompleted_shouldReturnTrueWhenAllCompleted() {
    var item = new ReindexStatusEntity(ITEM, MERGE_COMPLETED);
    item.setTotalMergeRanges(10);
    item.setProcessedMergeRanges(10);

    var instance = new ReindexStatusEntity(INSTANCE, MERGE_COMPLETED);
    instance.setTotalMergeRanges(10);
    instance.setProcessedMergeRanges(10);

    var holdings = new ReindexStatusEntity(HOLDINGS, MERGE_COMPLETED);
    holdings.setTotalMergeRanges(10);
    holdings.setProcessedMergeRanges(10);

    repository.saveReindexStatusRecords(java.util.List.of(item, instance, holdings));

    // act
    var result = repository.isMergeCompleted();

    // assert
    assertThat(result).isTrue();
  }

  @Test
  void getTargetTenantId_shouldReturnNullWhenNotSet() {
    // given - insert record without target tenant
    var entity = new ReindexStatusEntity(INSTANCE, MERGE_IN_PROGRESS);
    repository.saveReindexStatusRecords(java.util.List.of(entity));

    // act
    var result = repository.getTargetTenantId();

    // assert
    assertThat(result).isNull();
  }

  @Test
  void getTargetTenantId_shouldReturnTenantIdWhenSet() {
    // given
    var targetTenantId = MEMBER_TENANT_ID;
    var entity = new ReindexStatusEntity(INSTANCE, MERGE_IN_PROGRESS);
    entity.setTargetTenantId(targetTenantId);
    repository.saveReindexStatusRecords(java.util.List.of(entity));

    // act
    var result = repository.getTargetTenantId();

    // assert
    assertThat(result).isEqualTo(targetTenantId);
  }

  @Test
  void recreateReindexStatusTrigger_withStandardMode_shouldSucceed() {
    // act - should not throw exception
    repository.recreateReindexStatusTrigger(false);

    // assert - verify trigger exists by checking if we can use the table normally
    var entity = new ReindexStatusEntity(INSTANCE, MERGE_IN_PROGRESS);
    repository.saveReindexStatusRecords(java.util.List.of(entity));
    assertThat(repository.getReindexStatuses()).hasSize(1);
  }

  @Test
  void recreateReindexStatusTrigger_withConsortiumMode_shouldSucceed() {
    // act - should not throw exception
    repository.recreateReindexStatusTrigger(true);

    // assert - verify trigger exists by checking if we can use the table normally
    var entity = new ReindexStatusEntity(CONTRIBUTOR, MERGE_IN_PROGRESS);
    entity.setTargetTenantId("consortium_member");
    repository.saveReindexStatusRecords(java.util.List.of(entity));
    assertThat(repository.getReindexStatuses()).hasSize(1);
  }
}
