package org.folio.search.service.indexrebuild.records;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.folio.search.service.indexrebuild.IndexRebuildTarget.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RecordsDeleteTrackerTest {

  private static final String ITEM_ID = "item";

  @Mock
  private IndexRebuildStateRepository stateRepository;
  @Mock
  private DeletedRecordRepository deletedRecordRepository;
  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  @InjectMocks
  private RecordsDeleteTracker tracker;

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(consortiumTenantExecutor).run(any());
  }

  @Test
  void track_positive_deleteIsRecordedDuringRun() {
    when(stateRepository.isTracking(ITEM, TENANT_ID)).thenReturn(true);

    tracker.track(ITEM, TENANT_ID, List.of(event(ResourceEventType.DELETE)));

    verify(deletedRecordRepository).add(ITEM, TENANT_ID, List.of(ITEM_ID));
  }

  @Test
  void track_positive_createRemovesRecordedDelete() {
    when(stateRepository.isTracking(ITEM, TENANT_ID)).thenReturn(true);

    tracker.track(ITEM, TENANT_ID, List.of(event(ResourceEventType.CREATE)));

    verify(deletedRecordRepository).remove(ITEM, TENANT_ID, List.of(ITEM_ID));
  }

  @Test
  void track_positive_nothingRecordedWithoutRun() {
    when(stateRepository.isTracking(ITEM, TENANT_ID)).thenReturn(false);

    tracker.track(ITEM, TENANT_ID, List.of(event(ResourceEventType.DELETE)));

    verifyNoInteractions(deletedRecordRepository);
  }

  @Test
  void track_positive_updatesDontQueryDatabase() {
    tracker.track(ITEM, TENANT_ID, List.of(event(ResourceEventType.UPDATE)));

    verifyNoInteractions(stateRepository, deletedRecordRepository, consortiumTenantExecutor);
  }

  @Test
  void track_positive_missingTablesAreIgnored() {
    when(stateRepository.isTracking(ITEM, TENANT_ID))
      .thenThrow(new BadSqlGrammarException("isTracking", "sql", new SQLException("relation does not exist")));
    var events = List.of(event(ResourceEventType.DELETE));

    assertThatCode(() -> tracker.track(ITEM, TENANT_ID, events)).doesNotThrowAnyException();
    verifyNoInteractions(deletedRecordRepository);
  }

  private static ResourceEvent event(ResourceEventType type) {
    return new ResourceEvent().id(ITEM_ID).type(type).tenant(TENANT_ID);
  }
}
