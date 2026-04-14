package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.reindex.StagingMigrationService.RESOURCE_REINDEX_TIMESTAMP;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.testing.extension.Random;
import org.folio.spring.testing.extension.impl.RandomParametersExtension;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith({MockitoExtension.class, RandomParametersExtension.class})
class ReindexUploadRangeIndexServiceTest {

  private @Mock UploadRangeRepository repository;
  private @Mock FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private @Mock ReindexStatusService statusService;
  private ReindexUploadRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(repository.entityType()).thenReturn(ReindexEntityType.INSTANCE);
    service = new ReindexUploadRangeIndexService(List.of(repository), indexRangeEventProducer, statusService);
  }

  @Test
  void prepareAndSendIndexRanges_positive(@Random UploadRangeEntity uploadRange) {
    // arrange
    when(repository.createUploadRanges()).thenReturn(List.of(uploadRange));

    // act
    service.prepareAndSendIndexRanges(ReindexEntityType.INSTANCE);

    // assert
    verify(statusService).updateReindexUploadStarted(ReindexEntityType.INSTANCE, 1);
    var captor = ArgumentCaptor.<List<ReindexRangeIndexEvent>>captor();
    verify(indexRangeEventProducer).sendMessages(captor.capture());
    var events = captor.getValue();
    assertThat(events)
      .hasSize(1)
      .extracting(ReindexRangeIndexEvent::getEntityType,
        ReindexRangeIndexEvent::getLower,
        ReindexRangeIndexEvent::getUpper,
        ReindexRangeIndexEvent::getMemberTenantId)
      .containsExactly(Tuple.tuple(uploadRange.getEntityType(), uploadRange.getLower(), uploadRange.getUpper(), null));
  }

  @Test
  void prepareAndSendIndexRanges_positive_consortiumMember(@Random UploadRangeEntity uploadRange) {
    // arrange
    when(repository.createUploadRanges()).thenReturn(List.of(uploadRange));

    // act
    ReindexContext.setMemberTenantId(MEMBER_TENANT_ID);
    service.prepareAndSendIndexRanges(ReindexEntityType.INSTANCE);
    ReindexContext.clearMemberTenantId();

    // assert
    verify(statusService).updateReindexUploadStarted(ReindexEntityType.INSTANCE, 1);
    var captor = ArgumentCaptor.<List<ReindexRangeIndexEvent>>captor();
    verify(indexRangeEventProducer).sendMessages(captor.capture());
    var events = captor.getValue();
    assertThat(events)
      .hasSize(1)
      .extracting(ReindexRangeIndexEvent::getEntityType,
        ReindexRangeIndexEvent::getLower,
        ReindexRangeIndexEvent::getUpper,
        ReindexRangeIndexEvent::getMemberTenantId)
      .containsExactly(Tuple.tuple(uploadRange.getEntityType(), uploadRange.getLower(), uploadRange.getUpper(),
        MEMBER_TENANT_ID));
  }

  @Test
  void prepareAndSendIndexRanges_negative_shouldThrowExceptionForUnknownEntity() {
    // assert
    assertThrows(UnsupportedOperationException.class,
      () -> service.prepareAndSendIndexRanges(ReindexEntityType.SUBJECT));
  }

  @Test
  void fetchRecordRange_positive() {
    var recordId = UUID.randomUUID().toString();
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setId(UUID.randomUUID());
    indexEvent.setEntityType(ReindexEntityType.INSTANCE);
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setLower("00");
    indexEvent.setUpper("ff");

    var mockRecord = Map.<String, Object>of("id", recordId, "key", "val");
    when(repository.fetchByIdRange("00", "ff")).thenReturn(List.of(mockRecord));

    var actual = service.fetchRecordRange(indexEvent);

    assertThat(actual)
      .hasSize(1)
      .extracting(ResourceEvent::getId, ResourceEvent::getTenant, ResourceEvent::getNew, ResourceEvent::getResourceName)
      .containsExactly(Tuple.tuple(recordId, TENANT_ID, mockRecord, ResourceType.INSTANCE.getName()));
  }

  @Test
  void fetchRecordRange_positive_withMemberTenantId() {
    var recordId = UUID.randomUUID().toString();
    var indexEvent = new ReindexRangeIndexEvent();
    indexEvent.setId(UUID.randomUUID());
    indexEvent.setEntityType(ReindexEntityType.INSTANCE);
    indexEvent.setTenant(TENANT_ID);
    indexEvent.setLower("00");
    indexEvent.setUpper("ff");
    indexEvent.setMemberTenantId(MEMBER_TENANT_ID);

    var mockRecord = Map.<String, Object>of("id", recordId, "key", "val");
    when(repository.fetchByIdRangeWithTimestamp("00", "ff", RESOURCE_REINDEX_TIMESTAMP))
      .thenReturn(List.of(mockRecord));

    var actual = service.fetchRecordRange(indexEvent);

    assertThat(actual)
      .hasSize(1)
      .extracting(ResourceEvent::getId, ResourceEvent::getTenant, ResourceEvent::getNew, ResourceEvent::getResourceName)
      .containsExactly(Tuple.tuple(recordId, TENANT_ID, mockRecord, ResourceType.INSTANCE.getName()));
  }

  @Test
  void updateStatus_positive() {
    var eventId = UUID.randomUUID();
    var event = new ReindexRangeIndexEvent();
    event.setId(eventId);
    event.setEntityType(ReindexEntityType.INSTANCE);
    var failCause = "some error";

    service.updateStatus(event, ReindexRangeStatus.FAIL, failCause);

    var timestampCaptor = ArgumentCaptor.forClass(Timestamp.class);
    verify(repository).updateRangeStatus(eq(eventId), timestampCaptor.capture(),
      eq(ReindexRangeStatus.FAIL), eq(failCause));
    assertThat(timestampCaptor.getValue()).isNotNull();
  }
}
