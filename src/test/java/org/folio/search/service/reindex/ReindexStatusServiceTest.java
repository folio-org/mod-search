package org.folio.search.service.reindex;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.exception.RequestValidationException.REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.folio.search.converter.ReindexStatusMapper;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexStatusServiceTest {

  @Mock
  private ReindexStatusRepository statusRepository;

  @Mock
  private ReindexStatusMapper reindexStatusMapper;

  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;

  @InjectMocks
  private ReindexStatusService service;

  @Captor
  private ArgumentCaptor<List<ReindexStatusEntity>> reindexStatusEntitiesCaptor;

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
  void getReindexStatuses() {
    var statusEntities = List.of(new ReindexStatusEntity(ReindexEntityType.INSTANCE, ReindexStatus.MERGE_COMPLETED));
    var expected = List.of(new ReindexStatusItem());

    when(statusRepository.getReindexStatuses()).thenReturn(statusEntities);
    when(reindexStatusMapper.convert(statusEntities.getFirst())).thenReturn(expected.getFirst());

    var actual = service.getReindexStatuses(TENANT_ID);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getReindexStatuses_negative_consortiumMemberTenant() {
    when(consortiumTenantProvider.isMemberTenant(TENANT_ID)).thenReturn(true);

    var ex = Assertions.assertThrows(RequestValidationException.class, () -> service.getReindexStatuses(TENANT_ID));

    assertThat(ex.getMessage()).isEqualTo(REQUEST_NOT_ALLOWED_FOR_CONSORTIUM_MEMBER_MSG);
    assertThat(ex.getKey()).isEqualTo(XOkapiHeaders.TENANT);
    assertThat(ex.getValue()).isEqualTo(TENANT_ID);
    verifyNoInteractions(statusRepository);
    verifyNoInteractions(reindexStatusMapper);
  }

  @Test
  void updateReindexMergeFailed() {
    // act
    service.updateReindexMergeFailed();

    // assert
    verify(statusRepository).setMergeReindexFailed(ReindexEntityType.supportMergeTypes());
  }

  @Test
  void updateReindexMergeFailed_forEntity() {
    // act
    service.updateReindexMergeFailed(ReindexEntityType.INSTANCE);

    // assert
    verify(statusRepository).setMergeReindexFailed(singletonList(INSTANCE));
  }

  @Test
  void updateReindexMergeStarted() {
    // act
    service.updateReindexMergeStarted(INSTANCE, 1000);

    // assert
    verify(statusRepository).setMergeReindexStarted(INSTANCE, 1000);
  }

  @Test
  void updateReindexUploadStarted() {
    // act
    service.updateReindexUploadStarted(INSTANCE, 1000);

    // assert
    verify(statusRepository).setUploadReindexStarted(INSTANCE, 1000);
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
    service.recreateMergeStatusRecords(null);

    // assert
    verify(statusRepository).truncate();
    verify(statusRepository).recreateReindexStatusTrigger(false);
    verify(statusRepository).saveReindexStatusRecords(reindexStatusEntitiesCaptor.capture());
    var savedEntities = reindexStatusEntitiesCaptor.getValue();
    assertThat(savedEntities)
      .hasSize(ReindexEntityType.supportMergeTypes().size())
      .are(new Condition<>(statusEntity ->
        ReindexEntityType.supportMergeTypes().contains(statusEntity.getEntityType()), "merge status entity"))
      .are(new Condition<>(statusEntity ->
        ReindexStatus.MERGE_IN_PROGRESS.equals(statusEntity.getStatus()), "merge status entity"));
  }

  @Test
  void updateReindexMergeInProgress() {
    // given
    var entityTypes = Set.of(INSTANCE, HOLDINGS);

    // act
    service.updateReindexMergeInProgress(entityTypes);

    // assert
    verify(statusRepository).setMergeInProgress(entityTypes);
  }

  @Test
  void recreateMergeStatusRecords_withTargetTenantId() {
    // given
    var targetTenantId = "member_tenant";

    // act
    service.recreateMergeStatusRecords(targetTenantId);

    // assert
    verify(statusRepository).truncate();
    verify(statusRepository).recreateReindexStatusTrigger(true);
    verify(statusRepository).saveReindexStatusRecords(reindexStatusEntitiesCaptor.capture());

    var savedEntities = reindexStatusEntitiesCaptor.getValue();
    assertThat(savedEntities)
      .hasSize(ReindexEntityType.supportMergeTypes().size())
      .allMatch(entity -> targetTenantId.equals(entity.getTargetTenantId()),
        "All entities should have targetTenantId set");
  }

  @Test
  void recreateUploadStatusRecord_shouldPreserveTargetTenantId() {
    // given
    var targetTenantId = "member_tenant";
    when(statusRepository.getTargetTenantId()).thenReturn(targetTenantId);

    // act
    service.recreateUploadStatusRecord(INSTANCE);

    // assert
    verify(statusRepository).getTargetTenantId();
    verify(statusRepository).delete(INSTANCE);
    verify(statusRepository).saveReindexStatusRecords(reindexStatusEntitiesCaptor.capture());

    var savedEntities = reindexStatusEntitiesCaptor.getValue();
    assertThat(savedEntities)
      .hasSize(1)
      .first()
      .satisfies(entity -> assertThat(entity.getEntityType()).isEqualTo(INSTANCE))
      .satisfies(entity -> assertThat(entity.getStatus()).isEqualTo(ReindexStatus.UPLOAD_IN_PROGRESS))
      .satisfies(entity -> assertThat(entity.getTargetTenantId()).isEqualTo(targetTenantId));
  }

  @Test
  void recreateUploadStatusRecord_whenNoExistingTargetTenantId_shouldSetNull() {
    // given
    when(statusRepository.getTargetTenantId()).thenThrow(new RuntimeException("No data"));

    // act
    service.recreateUploadStatusRecord(INSTANCE);

    // assert
    verify(statusRepository).delete(INSTANCE);
    verify(statusRepository).saveReindexStatusRecords(reindexStatusEntitiesCaptor.capture());

    var savedEntities = reindexStatusEntitiesCaptor.getValue();
    assertThat(savedEntities)
      .hasSize(1)
      .first()
      .satisfies(entity -> assertThat(entity.getTargetTenantId()).isNull());
  }
}
