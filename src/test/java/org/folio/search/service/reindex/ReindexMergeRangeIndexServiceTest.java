package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.HoldingRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexMergeRangeIndexServiceTest {

  private @Mock MergeRangeRepository repository;
  private @Mock ItemRepository itemRepository;
  private @Mock HoldingRepository holdingRepository;
  private @Mock InventoryService inventoryService;
  private @Mock ReindexConfigurationProperties config;

  private ReindexMergeRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(repository.entityType()).thenReturn(INSTANCE);
    service = new ReindexMergeRangeIndexService(List.of(repository), inventoryService, config);
  }

  @Test
  void saveMergeRanges_positive() {
    // act
    service.saveMergeRanges(List.of());

    // assert
    verify(repository).saveMergeRanges(Mockito.anyList());
  }

  @Test
  void createMergeRanges_positive() {
    // given
    when(config.getMergeRangeSize()).thenReturn(3);
    when(inventoryService.fetchInventoryRecordsCount(any(InventoryRecordType.class))).thenReturn(3);

    // act
    var ranges = service.createMergeRanges(TENANT_ID);

    // assert
    verify(inventoryService, times(InventoryRecordType.values().length))
      .fetchInventoryRecordsCount(any(InventoryRecordType.class));
    verify(config).getMergeRangeSize();
    assertThat(ranges)
      .hasSize(3)
      .are(new Condition<>(range -> range.getLowerId() != null, "lower id"))
      .are(new Condition<>(range -> range.getUpperId() != null, "upper id"))
      .extracting(MergeRangeEntity::getEntityType, MergeRangeEntity::getTenantId)
      .containsExactlyInAnyOrder(tuple(INSTANCE, TENANT_ID), tuple(HOLDINGS, TENANT_ID), tuple(ITEM, TENANT_ID));
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
