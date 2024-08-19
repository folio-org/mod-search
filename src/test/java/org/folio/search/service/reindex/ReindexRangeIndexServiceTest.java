package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.reindex.ReindexRangeIndexService.REQUEST_NOT_ALLOWED_MSG;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.folio.search.converter.ReindexStatusMapper;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.Random;
import org.folio.spring.testing.extension.impl.RandomParametersExtension;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith({MockitoExtension.class, RandomParametersExtension.class})
class ReindexRangeIndexServiceTest {

  private @Mock UploadRangeRepository repository;
  private @Mock FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private @Mock ReindexStatusRepository statusRepository;
  private @Mock ReindexStatusMapper reindexStatusMapper;
  private @Mock ConsortiumTenantService consortiumTenantService;
  private ReindexRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(repository.entityType()).thenReturn(ReindexEntityType.INSTANCE);
    service = new ReindexRangeIndexService(List.of(repository), indexRangeEventProducer, statusRepository,
      reindexStatusMapper, consortiumTenantService);
  }

  @Test
  void prepareAndSendIndexRanges_positive(@Random UploadRangeEntity uploadRange) {
    // arrange
    when(repository.getUploadRanges(true)).thenReturn(List.of(uploadRange));

    // act
    service.prepareAndSendIndexRanges(ReindexEntityType.INSTANCE);

    // assert
    ArgumentCaptor<List<ReindexRangeIndexEvent>> captor = ArgumentCaptor.captor();
    verify(indexRangeEventProducer).sendMessages(captor.capture());
    List<ReindexRangeIndexEvent> events = captor.getValue();
    assertThat(events)
      .hasSize(1)
      .extracting(ReindexRangeIndexEvent::getEntityType,
        ReindexRangeIndexEvent::getLimit,
        ReindexRangeIndexEvent::getOffset)
      .containsExactly(Tuple.tuple(uploadRange.getEntityType(), uploadRange.getLimit(), uploadRange.getOffset()));
  }

  @Test
  void prepareAndSendIndexRanges_negative_shouldThrowExceptionForUnknownEntity() {
    // assert
    assertThrows(UnsupportedOperationException.class,
      () -> service.prepareAndSendIndexRanges(ReindexEntityType.SUBJECT));
  }

  @Test
  void fetchRecordRange_positive() {
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setId(UUID.randomUUID());
    indexEvent.setEntityType(ReindexEntityType.INSTANCE);
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setLimit(10);
    indexEvent.setOffset(50);

    Map<String, Object> mockRecord = Map.of("key", "val");
    when(repository.fetchBy(10, 50)).thenReturn(List.of(mockRecord));

    var actual = service.fetchRecordRange(indexEvent);

    assertThat(actual)
    .hasSize(1)
      .extracting(ResourceEvent::getTenant, ResourceEvent::getNew, ResourceEvent::getResourceName)
      .containsExactly(Tuple.tuple(TENANT_ID, mockRecord, INSTANCE_RESOURCE));
  }

  @Test
  void getReindexStatuses() {
    var statusEntities = List.of(new ReindexStatusEntity(ReindexEntityType.INSTANCE, ReindexStatus.MERGE_COMPLETED));
    var expected = List.of(new ReindexStatusItem());

    when(statusRepository.getReindexStatuses()).thenReturn(statusEntities);
    when(reindexStatusMapper.convert(statusEntities.get(0))).thenReturn(expected.get(0));

    var actual = service.getReindexStatuses(TENANT_ID);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getReindexStatuses_negative_consortiumMemberTenant() {
    when(consortiumTenantService.isMemberTenantInConsortium(TENANT_ID)).thenReturn(true);

    var ex = Assertions.assertThrows(RequestValidationException.class, () -> service.getReindexStatuses(TENANT_ID));

    assertThat(ex.getMessage()).isEqualTo(REQUEST_NOT_ALLOWED_MSG);
    assertThat(ex.getKey()).isEqualTo(XOkapiHeaders.TENANT);
    assertThat(ex.getValue()).isEqualTo(TENANT_ID);
    verifyNoInteractions(statusRepository);
    verifyNoInteractions(reindexStatusMapper);
  }

  @Test
  void setReindexUploadFailed() {
    var entityType = ReindexEntityType.INSTANCE;

    service.setReindexUploadFailed(entityType);

    verify(statusRepository).setReindexUploadFailed(entityType);
  }

  @Test
  void addProcessedMergeRanges() {
    var entityType = ReindexEntityType.INSTANCE;
    var ranges = 5;

    service.addProcessedMergeRanges(entityType, ranges);

    verify(statusRepository).addReindexCounts(entityType, ranges, 0);
  }

  @Test
  void addProcessedUploadRanges() {
    var entityType = ReindexEntityType.INSTANCE;
    var ranges = 5;

    service.addProcessedUploadRanges(entityType, ranges);

    verify(statusRepository).addReindexCounts(entityType, 0, ranges);
  }
}
