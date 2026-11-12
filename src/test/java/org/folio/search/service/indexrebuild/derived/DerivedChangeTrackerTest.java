package org.folio.search.service.indexrebuild.derived;

import static org.folio.search.service.indexrebuild.IndexRebuildTarget.CALL_NUMBER;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.indexrebuild.jdbc.DirtyKeyRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DerivedChangeTrackerTest {

  @Mock
  private DerivedRebuildStrategy strategy;
  @Mock
  private DirtyKeyRepository dirtyKeyRepository;
  @Mock
  private DerivedIndexReader reader;
  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  private DerivedChangeTracker tracker;

  @BeforeEach
  void setUp() {
    lenient().when(strategy.target()).thenReturn(CALL_NUMBER);
    lenient().when(strategy.source()).thenReturn(ITEM);
    lenient().when(strategy.isTrackingEnabled()).thenReturn(true);
    lenient().when(strategy.keysOf(any())).thenAnswer(invocation -> Set.of(
      String.valueOf(invocation.<Map<?, ?>>getArgument(0).get("key"))));
    tracker = new DerivedChangeTracker(new DerivedRebuildStrategies(List.of(strategy)), dirtyKeyRepository, reader,
      consortiumTenantExecutor);
    lenient().doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(consortiumTenantExecutor).run(any());
  }

  @Test
  void track_positive_enqueuesKeysOfOldAndNewRecords() {
    var event = new ResourceEvent()._new(Map.of("key", "new")).old(Map.of("key", "old"));

    tracker.track(ITEM, TENANT_ID, List.of(event));

    verify(dirtyKeyRepository).add(CALL_NUMBER, TENANT_ID, Set.of("new", "old"));
  }

  @Test
  void track_positive_nothingEnqueuedForRecordsWithoutKeys() {
    doReturn(Set.of()).when(strategy).keysOf(any());

    tracker.track(ITEM, TENANT_ID, List.of(new ResourceEvent()._new(Map.of("id", "item"))));

    verifyNoInteractions(dirtyKeyRepository);
  }

  @Test
  void track_positive_trackingDisabled() {
    when(strategy.isTrackingEnabled()).thenReturn(false);

    tracker.track(ITEM, TENANT_ID, List.of(new ResourceEvent()._new(Map.of("key", "new"))));

    verifyNoInteractions(dirtyKeyRepository);
  }

  @Test
  void track_positive_otherSourceIsIgnored() {
    tracker.track(CALL_NUMBER, TENANT_ID, List.of(new ResourceEvent()._new(Map.of("key", "new"))));

    verifyNoInteractions(dirtyKeyRepository);
  }

  @Test
  void trackMatching_positive_enqueuesKeysOfMatchingSourceDocuments() {
    var query = termsQuery("instanceId", List.of("instance"));
    when(reader.findKeys(strategy, TENANT_ID, query)).thenReturn(Set.of("key"));

    tracker.trackMatching(ITEM, TENANT_ID, query);

    verify(reader).refresh(ITEM, TENANT_ID);
    verify(dirtyKeyRepository).add(CALL_NUMBER, TENANT_ID, Set.of("key"));
  }

  @Test
  void trackMatching_positive_trackingDisabled() {
    when(strategy.isTrackingEnabled()).thenReturn(false);

    tracker.trackMatching(ITEM, TENANT_ID, termsQuery("instanceId", List.of("instance")));

    verifyNoInteractions(reader, dirtyKeyRepository);
  }
}
