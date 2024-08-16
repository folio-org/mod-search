package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
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
class ReindexRangeIndexServiceTest {

  private @Mock ReindexJdbcRepository repository;
  private @Mock FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private ReindexRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(repository.entityType()).thenReturn(ReindexEntityType.INSTANCE);
    service = new ReindexRangeIndexService(List.of(repository), indexRangeEventProducer);
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
      .containsExactly(Tuple.tuple(TENANT_ID, mockRecord, ResourceType.INSTANCE.getName()));
  }
}
