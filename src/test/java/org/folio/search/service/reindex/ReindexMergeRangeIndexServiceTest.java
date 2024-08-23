package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexMergeRangeIndexServiceTest {

  private @Mock MergeRangeRepository repository;

  private ReindexMergeRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(repository.entityType()).thenReturn(ReindexEntityType.INSTANCE);
    service = new ReindexMergeRangeIndexService(List.of(repository), null, null);
  }

  @Test
  void updateFinishDate() {
    var testStartTime = Timestamp.from(Instant.now());
    var rangeId = UUID.randomUUID();
    var captor = ArgumentCaptor.<Timestamp>captor();

    service.updateFinishDate(ReindexEntityType.INSTANCE, rangeId.toString());

    verify(repository).setIndexRangeFinishDate(eq(rangeId), captor.capture());

    var timestamp = captor.getValue();
    assertThat(timestamp).isAfter(testStartTime);
  }

  @Test
  void saveEntities() {
    var entities = Map.<String, Object>of("id", UUID.randomUUID());
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(ReindexRecordsEvent.ReindexRecordType.INSTANCE);
    event.setRecords(List.of(entities));

    service.saveEntities(event);

    verify(repository).saveEntities(TENANT_ID, List.of(entities));
  }
}
