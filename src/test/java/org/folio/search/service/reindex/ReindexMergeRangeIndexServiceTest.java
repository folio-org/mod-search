package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.HOLDING;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.service.reindex.jdbc.HoldingRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexMergeRangeIndexServiceTest {

  private @Mock MergeInstanceRepository instanceRepository;
  private @Mock ItemRepository itemRepository;
  private @Mock HoldingRepository holdingRepository;
  private @Mock InventoryService inventoryService;
  private @Mock ReindexConfigurationProperties config;

  private ReindexMergeRangeIndexService service;

  @BeforeEach
  void setUp() {
    when(instanceRepository.entityType()).thenReturn(INSTANCE);
    service = new ReindexMergeRangeIndexService(List.of(instanceRepository), inventoryService, config);
  }

  @Test
  void deleteAllRangeRecords_positive() {
    // given
    when(holdingRepository.entityType()).thenReturn(HOLDING);
    when(itemRepository.entityType()).thenReturn(ITEM);
    service = new ReindexMergeRangeIndexService(List.of(instanceRepository, holdingRepository, itemRepository),
      inventoryService, config);

    // act
    service.deleteAllRangeRecords();

    // assert
    verify(instanceRepository).truncateMergeRanges();
    verify(instanceRepository).truncate();
    verify(holdingRepository).truncate();
    verify(itemRepository).truncate();
  }

  @Test
  void saveMergeRanges_positive() {
    // act
    service.saveMergeRanges(List.of());

    // assert
    verify(instanceRepository).saveMergeRanges(Mockito.anyList());
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
      .containsExactlyInAnyOrder(tuple(INSTANCE, TENANT_ID), tuple(HOLDING, TENANT_ID), tuple(ITEM, TENANT_ID));
  }
}
