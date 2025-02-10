package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.Condition;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.reindex.jdbc.HoldingRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
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
  private @Mock InstanceChildrenResourceService instanceChildrenResourceService;

  private ReindexMergeRangeIndexService service;
  private Map<ReindexEntityType, MergeRangeRepository> repositoryMap;

  @BeforeEach
  void setUp() {
    var repositories = List.of(instanceRepository, itemRepository, holdingRepository);
    repositories.forEach(repository -> when(repository.entityType()).thenCallRealMethod());
    service = new ReindexMergeRangeIndexService(
      repositories, inventoryService, config, instanceChildrenResourceService);
    repositoryMap = repositories.stream()
      .collect(Collectors.toMap(ReindexJdbcRepository::entityType, Function.identity()));
  }

  @Test
  void saveMergeRanges_positive() {
    // act
    service.saveMergeRanges(List.of());

    // assert
    verify(repositoryMap.values().iterator().next()).saveMergeRanges(Mockito.anyList());
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
  void updateStatus() {
    var testStartTime = Timestamp.from(Instant.now());
    var rangeId = UUID.randomUUID();
    var captor = ArgumentCaptor.<Timestamp>captor();
    var failCause = "fail cause";

    service.updateStatus(ReindexEntityType.INSTANCE, rangeId.toString(), ReindexRangeStatus.FAIL, failCause);

    verify(instanceRepository)
      .updateRangeStatus(eq(rangeId), captor.capture(), eq(ReindexRangeStatus.FAIL), eq(failCause));

    var timestamp = captor.getValue();
    assertThat(timestamp).isAfterOrEqualTo(testStartTime);
  }

  @EnumSource(value = ReindexRecordsEvent.ReindexRecordType.class)
  @ParameterizedTest
  void saveEntities(ReindexRecordsEvent.ReindexRecordType recordType) {
    var entities = Map.<String, Object>of("id", UUID.randomUUID());
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(recordType);
    event.setRecords(List.of(entities));

    service.saveEntities(event);

    verify(repositoryMap.get(recordType.getEntityType())).saveEntities(TENANT_ID, List.of(entities));
    verify(instanceChildrenResourceService).persistChildrenOnReindex(TENANT_ID,
      RESOURCE_NAME_MAP.get(recordType.getEntityType()),
      List.of(entities));
  }

  @Test
  void fetchFailedMergeRanges() {
    // act
    service.fetchFailedMergeRanges();

    // assert
    verify(repositoryMap.values().iterator().next()).getFailedMergeRanges();
  }
}

