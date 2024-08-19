package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.assertj.core.api.Condition;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexStatusServiceTest {

  @Mock
  private ReindexStatusRepository statusRepository;

  @InjectMocks
  private ReindexStatusService service;

  @Test
  void updateReindexUploadFailed() {
    // given
    var entityType = INSTANCE;

    //act
    service.updateReindexUploadFailed(entityType);

    //assert
    verify(statusRepository).setReindexUploadFailed(entityType);
  }

  @Test
  void updateReindexMergeFailed() {
    // act
    service.updateReindexMergeFailed();

    // assert
    verify(statusRepository).setMergeReindexFailed(MERGE_RANGE_ENTITY_TYPES);
  }

  @Test
  void updateReindexMergeFailedForStatuses() {
    // act
    service.updateReindexMergeFailed(List.of(INSTANCE));

    // assert
    verify(statusRepository).setMergeReindexFailed(List.of(INSTANCE));
  }

  @Test
  void updateReindexMergeStarted() {
    // act
    service.updateReindexMergeStarted(INSTANCE, 1000);

    // assert
    verify(statusRepository).setMergeReindexStarted(INSTANCE, 1000);
  }

  @Test
  void addProcessedMergeRanges() {
    // given
    var entityType = INSTANCE;
    var ranges = 5;

    // act
    service.addProcessedMergeRanges(entityType, ranges);

    // assert
    verify(statusRepository).addReindexCounts(entityType, ranges, 0);
  }

  @Test
  void addProcessedUploadRanges() {
    // given
    var entityType = INSTANCE;
    var ranges = 5;

    // act
    service.addProcessedUploadRanges(entityType, ranges);

    // assert
    verify(statusRepository).addReindexCounts(entityType, 0, ranges);
  }

  @Test
  void shouldRecreateMergeReindexStatusEntities() {
    // act
    service.recreateMergeStatusRecords();
    var captor = ArgumentCaptor.forClass(List.class);

    // assert
    verify(statusRepository).truncate();
    verify(statusRepository).saveReindexStatusRecords(captor.capture());
    var savedEntities = (List<ReindexStatusEntity>) captor.getValue();
    assertThat(savedEntities)
      .hasSize(MERGE_RANGE_ENTITY_TYPES.size())
      .are(new Condition<>(statusEntity ->
        MERGE_RANGE_ENTITY_TYPES.contains(statusEntity.getEntityType()), "merge status entity"))
      .are(new Condition<>(statusEntity ->
        ReindexStatus.MERGE_IN_PROGRESS.equals(statusEntity.getStatus()), "merge status entity"));
  }
}
