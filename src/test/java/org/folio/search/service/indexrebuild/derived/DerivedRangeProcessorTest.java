package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.search.model.event.IndexRebuildRangeEvent;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.callnumber.CallNumberRebuildStrategy;
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
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedRangeProcessorTest {

  private static final UUID RANGE_ID = IndexRebuildRangeIds.newId();
  private static final DerivedRebuildStrategy STRATEGY = new CallNumberRebuildStrategy(null, null);

  @Mock
  private IndexRebuildRangeRepository rangeRepository;
  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private DerivedIndexReader reader;
  @Mock
  private DerivedDocumentWriter documentWriter;
  @Mock
  private DerivedFinalizer finalizer;
  private DerivedRangeProcessor processor;

  @BeforeEach
  void setUp() {
    var retryTemplate = new RetryTemplate(RetryPolicy.builder().maxRetries(1).delay(Duration.ZERO).build());
    processor = new DerivedRangeProcessor(new DerivedRebuildStrategies(List.of(STRATEGY)), rangeRepository,
      stateRepository, reader, documentWriter, finalizer, retryTemplate);
  }

  @Test
  void process_positive_wholeIndexRange() {
    mockRange(IndexRebuildRangeStatus.PENDING, null);
    when(reader.findKeys(STRATEGY, TENANT_ID, "0", "1", null)).thenReturn(Set.of("key"));

    processor.process(event());

    verify(documentWriter).rebuild(STRATEGY, TENANT_ID, Set.of("key"));
    verify(rangeRepository).markSuccess(RANGE_ID);
    verify(finalizer).tryFinalize(CALL_NUMBER, TENANT_ID);
    verify(reader, never()).findDocumentKeysOfTenant(any(), any(), any(), any(), any());
  }

  @Test
  void process_positive_singleTenantRangeIncludesExistingDocuments() {
    mockRange(IndexRebuildRangeStatus.PENDING, MEMBER_TENANT_ID);
    when(reader.findKeys(STRATEGY, TENANT_ID, "0", "1", MEMBER_TENANT_ID)).thenReturn(Set.of("item"));
    when(reader.findDocumentKeysOfTenant(STRATEGY, TENANT_ID, "0", "1", MEMBER_TENANT_ID))
      .thenReturn(Set.of("document"));

    processor.process(event());

    verify(documentWriter).rebuild(STRATEGY, TENANT_ID, Set.of("item", "document"));
  }

  @Test
  void process_positive_unknownRangeIsIgnored() {
    when(rangeRepository.findById(CALL_NUMBER, RANGE_ID)).thenReturn(Optional.empty());

    processor.process(event());

    verifyNoInteractions(reader, documentWriter, finalizer);
  }

  @Test
  void process_positive_finishedRangeIsIgnored() {
    mockRange(IndexRebuildRangeStatus.SUCCESS, null);

    processor.process(event());

    verifyNoInteractions(reader, documentWriter, finalizer);
  }

  @Test
  void process_positive_transientFailureIsRetried() {
    mockRange(IndexRebuildRangeStatus.PENDING, null);
    when(reader.findKeys(STRATEGY, TENANT_ID, "0", "1", null)).thenReturn(Set.of("key"));
    doThrow(new IllegalStateException("error")).doNothing().when(documentWriter).rebuild(any(), any(), any());

    processor.process(event());

    verify(documentWriter, times(2)).rebuild(STRATEGY, TENANT_ID, Set.of("key"));
    verify(rangeRepository).markSuccess(RANGE_ID);
    verify(stateRepository, never()).markAllFailed(any(), any());
  }

  @Test
  void process_negative_failureMarksRunFailed() {
    mockRange(IndexRebuildRangeStatus.PENDING, null);
    when(reader.findKeys(STRATEGY, TENANT_ID, "0", "1", null)).thenReturn(Set.of("key"));
    doThrow(new IllegalStateException("error")).when(documentWriter).rebuild(any(), any(), any());

    processor.process(event());

    verify(rangeRepository).markFailed(RANGE_ID, "error");
    verify(stateRepository).markAllFailed(CALL_NUMBER, "error");
    verify(finalizer, never()).tryFinalize(any(), any());
  }

  private void mockRange(IndexRebuildRangeStatus status, String scopeTenantId) {
    when(rangeRepository.findById(CALL_NUMBER, RANGE_ID)).thenReturn(Optional.of(
      new IndexRebuildRange(RANGE_ID, UUID.randomUUID(), CALL_NUMBER, TENANT_ID, "0", "1", status, scopeTenantId)));
  }

  private static IndexRebuildRangeEvent event() {
    var event = new IndexRebuildRangeEvent();
    event.setId(RANGE_ID);
    event.setResource(CALL_NUMBER.getResource());
    return event;
  }
}
