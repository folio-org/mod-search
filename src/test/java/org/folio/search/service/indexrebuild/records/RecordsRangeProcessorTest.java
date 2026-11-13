package org.folio.search.service.indexrebuild.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.item.ItemRecordsRebuildStrategy;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildRangeRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RecordsRangeProcessorTest {

  private static final UUID RANGE_ID = IndexRebuildRangeIds.newId();
  private static final Map<String, Object> RECORD = Map.of("id", "item");
  private static final RecordsRebuildStrategy STRATEGY = new ItemRecordsRebuildStrategy(null, null);

  @Mock
  private IndexRebuildRangeRepository rangeRepository;
  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private RecordsIndexWriter indexWriter;
  @Mock
  private RecordsFileReader fileReader;
  @Mock
  private RecordsFinalizer finalizer;
  private RecordsRangeProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new RecordsRangeProcessor(new RecordsRebuildStrategies(List.of(STRATEGY)), rangeRepository,
      stateRepository, indexWriter, fileReader, finalizer);
  }

  @Test
  void handles_positive_itemRecordsOfRebuildRange() {
    assertThat(processor.handles(ReindexRecordType.ITEM, RANGE_ID.toString())).isTrue();
  }

  @Test
  void handles_negative_regularReindexRange() {
    assertThat(processor.handles(ReindexRecordType.ITEM, UUID.randomUUID().toString())).isFalse();
  }

  @Test
  void handles_negative_otherRecordType() {
    assertThat(processor.handles(ReindexRecordType.HOLDINGS, RANGE_ID.toString())).isFalse();
  }

  @Test
  void process_positive_writesRecordsAndFinalizes() {
    mockRange(IndexRebuildRangeStatus.PENDING);

    processor.process(recordsEvent());

    verify(indexWriter).write(STRATEGY, TENANT_ID, List.of(RECORD));
    verify(rangeRepository).markSuccess(RANGE_ID);
    verify(finalizer).tryFinalize(STRATEGY, TENANT_ID);
  }

  @Test
  void process_positive_recordsOfUnknownRangeAreIgnored() {
    when(rangeRepository.findById(ITEM, RANGE_ID)).thenReturn(Optional.empty());

    processor.process(recordsEvent());

    verifyNoInteractions(indexWriter, finalizer);
  }

  @Test
  void process_positive_redeliveredRecordsOfFinishedRangeAreIgnored() {
    mockRange(IndexRebuildRangeStatus.SUCCESS);

    processor.process(recordsEvent());

    verifyNoInteractions(indexWriter, finalizer);
  }

  @Test
  void process_negative_failureMarksRangeAndTenantFailed() {
    mockRange(IndexRebuildRangeStatus.PENDING);
    doThrow(new IllegalStateException("error")).when(indexWriter).write(any(), any(), any());

    processor.process(recordsEvent());

    verify(rangeRepository).markFailed(RANGE_ID, "error");
    verify(stateRepository).markFailed(eq(ITEM), eq(TENANT_ID), anyString());
    verify(finalizer, never()).tryFinalize(any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void process_positive_fileBatchesAreWritten() throws Exception {
    mockRange(IndexRebuildRangeStatus.PENDING);
    var event = new ReindexFileReadyEvent(TENANT_ID, ReindexRecordType.ITEM, RANGE_ID.toString(), "trace", "bucket",
      "key", "date");
    doAnswer(invocation -> {
      invocation.<Consumer<List<Map<String, Object>>>>getArgument(1).accept(List.of(RECORD));
      return null;
    }).when(fileReader).read(eq("key"), any(Consumer.class));

    processor.process(event);

    verify(indexWriter).write(STRATEGY, TENANT_ID, List.of(RECORD));
    verify(rangeRepository).markSuccess(RANGE_ID);
  }

  private void mockRange(IndexRebuildRangeStatus status) {
    when(rangeRepository.findById(ITEM, RANGE_ID)).thenReturn(Optional.of(
      new IndexRebuildRange(RANGE_ID, UUID.randomUUID(), ITEM, TENANT_ID, "0", "f", status, null)));
  }

  private static ReindexRecordsEvent recordsEvent() {
    var event = new ReindexRecordsEvent();
    event.setRangeId(RANGE_ID.toString());
    event.setRecordType(ReindexRecordType.ITEM);
    event.setTenant(TENANT_ID);
    event.setRecords(new ArrayList<>(List.of(RECORD)));
    return event;
  }
}
