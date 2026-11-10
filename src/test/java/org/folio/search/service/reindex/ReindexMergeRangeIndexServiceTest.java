package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.search.model.types.ReindexEntityType.HOLDINGS;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.search.model.types.ReindexEntityType.ITEM;
import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.reindex.MigrationResult;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ItemIndexService;
import org.folio.search.service.reindex.jdbc.HoldingRepository;
import org.folio.search.service.reindex.jdbc.ItemRepository;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.RawLine;
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
import org.springframework.beans.factory.ObjectProvider;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexMergeRangeIndexServiceTest {

  private @Mock MergeInstanceRepository instanceRepository;
  private @Mock ItemRepository itemRepository;
  private @Mock HoldingRepository holdingRepository;
  private @Mock InventoryService inventoryService;
  private @Mock ReindexConfigurationProperties config;
  private @Mock InstanceChildrenResourceService instanceChildrenResourceService;
  private @Mock StagingMigrationService stagingMigrationService;
  private @Mock ReindexStatusService statusService;
  private @Mock ObjectProvider<ItemIndexService> itemIndexServiceProvider;
  private @Mock ItemIndexService itemIndexService;

  private ReindexMergeRangeIndexService service;
  private Map<ReindexEntityType, MergeRangeRepository> repositoryMap;

  @BeforeEach
  void setUp() {
    var repositories = List.of(instanceRepository, itemRepository, holdingRepository);
    repositories.forEach(repository -> when(repository.entityType()).thenCallRealMethod());
    // Create service with unified repositories
    service = new ReindexMergeRangeIndexService(repositories, inventoryService, config, stagingMigrationService,
      statusService, itemIndexServiceProvider);
    service.setInstanceChildrenResourceService(instanceChildrenResourceService);
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
  void createMergeRanges_positive_zeroRecordsCountCreatesEmptyRange() {
    // given – inventory returns 0 for every record type
    when(config.getMergeRangeSize()).thenReturn(100);
    when(inventoryService.fetchInventoryRecordsCount(any(InventoryRecordType.class))).thenReturn(0);

    // act
    var ranges = service.createMergeRanges(TENANT_ID);

    // assert – one empty range per InventoryRecordType, bounds span the full UUID space
    assertThat(ranges)
      .hasSize(InventoryRecordType.values().length)
      .are(new Condition<>(range -> range.getLowerId() != null, "lower id"))
      .are(new Condition<>(range -> range.getUpperId() != null, "upper id"))
      .extracting(MergeRangeEntity::getEntityType, MergeRangeEntity::getTenantId)
      .containsExactlyInAnyOrder(tuple(INSTANCE, TENANT_ID), tuple(HOLDINGS, TENANT_ID), tuple(ITEM, TENANT_ID));
  }

  @Test
  void createMergeRanges_positive_multipleRangesPerType() {
    // given – 9 records with range size 3 → 3 ranges per type
    when(config.getMergeRangeSize()).thenReturn(3);
    when(inventoryService.fetchInventoryRecordsCount(any(InventoryRecordType.class))).thenReturn(9);

    // act
    var ranges = service.createMergeRanges(TENANT_ID);

    // assert – 3 types × 3 ranges = 9 total
    assertThat(ranges).hasSize(9);
    assertThat(ranges.stream().filter(r -> r.getEntityType() == INSTANCE)).hasSize(3);
    assertThat(ranges.stream().filter(r -> r.getEntityType() == HOLDINGS)).hasSize(3);
    assertThat(ranges.stream().filter(r -> r.getEntityType() == ITEM)).hasSize(3);
  }

  @Test
  void fetchMergeRanges_positive() {
    var expected = List.of(
      new MergeRangeEntity(UUID.randomUUID(), UUID.randomUUID(), INSTANCE, TENANT_ID, "00", "ff",
        Timestamp.from(Instant.now()), null, null));
    when(instanceRepository.getMergeRanges()).thenReturn(expected);

    var result = service.fetchMergeRanges(INSTANCE);

    assertThat(result).isEqualTo(expected);
    verify(instanceRepository).getMergeRanges();
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

  @EnumSource(value = ReindexRecordType.class)
  @ParameterizedTest
  void saveEntities(ReindexRecordType recordType) {
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

  @EnumSource(value = ReindexRecordType.class)
  @ParameterizedTest
  void saveEntities_positive_withoutInstanceChildrenResourceService(ReindexRecordType recordType) {
    var entities = Map.<String, Object>of("id", UUID.randomUUID());
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(recordType);
    event.setRecords(List.of(entities));
    // service with no InstanceChildrenResourceService wired
    var serviceWithoutChildren = new ReindexMergeRangeIndexService(
      List.of(instanceRepository, itemRepository, holdingRepository),
      inventoryService, config, stagingMigrationService, statusService,
      itemIndexServiceProvider);

    serviceWithoutChildren.saveEntities(event);

    verify(repositoryMap.get(recordType.getEntityType())).saveEntities(TENANT_ID, List.of(entities));
    verify(instanceChildrenResourceService, never()).persistChildrenOnReindex(any(), any(), any());
  }

  @EnumSource(value = ReindexRecordType.class)
  @ParameterizedTest
  void saveEntitiesRaw_savesViaRepositoryAndPersistsChildren(ReindexRecordType recordType) {
    var rawJson = "{\"id\":\"" + UUID.randomUUID() + "\"}";
    var data = Map.<String, Object>of("id", UUID.randomUUID().toString());
    var rawLine = new RawLine(rawJson, data);

    service.saveEntitiesRaw(TENANT_ID, recordType, List.of(rawLine));

    verify(repositoryMap.get(recordType.getEntityType())).saveEntitiesRaw(TENANT_ID, List.of(rawLine));
    verify(instanceChildrenResourceService).persistChildrenOnReindex(TENANT_ID,
      RESOURCE_NAME_MAP.get(recordType.getEntityType()),
      List.of(data));
  }

  @EnumSource(value = ReindexRecordType.class)
  @ParameterizedTest
  void saveEntitiesRaw_positive_withoutInstanceChildrenResourceService(ReindexRecordType recordType) {
    var rawJson = "{\"id\":\"" + UUID.randomUUID() + "\"}";
    var data = Map.<String, Object>of("id", UUID.randomUUID().toString());
    var rawLine = new RawLine(rawJson, data);
    var serviceWithoutChildren = new ReindexMergeRangeIndexService(
      List.of(instanceRepository, itemRepository, holdingRepository),
      inventoryService, config, stagingMigrationService, statusService,
      itemIndexServiceProvider);

    serviceWithoutChildren.saveEntitiesRaw(TENANT_ID, recordType, List.of(rawLine));

    verify(repositoryMap.get(recordType.getEntityType())).saveEntitiesRaw(TENANT_ID, List.of(rawLine));
    verify(instanceChildrenResourceService, never()).persistChildrenOnReindex(any(), any(), any());
  }

  @Test
  void createItemMergeRanges_positive_onlyItemRangesAreCreated() {
    when(config.getMergeRangeSize()).thenReturn(3);
    when(inventoryService.fetchInventoryRecordsCount(InventoryRecordType.ITEM)).thenReturn(6);

    var ranges = service.createItemMergeRanges(TENANT_ID);

    assertThat(ranges).hasSize(2)
      .extracting(MergeRangeEntity::getEntityType, MergeRangeEntity::getTenantId)
      .containsOnly(tuple(ITEM, TENANT_ID));
    verify(inventoryService).fetchInventoryRecordsCount(InventoryRecordType.ITEM);
    verify(inventoryService, never()).fetchInventoryRecordsCount(InventoryRecordType.INSTANCE);
    verify(inventoryService, never()).fetchInventoryRecordsCount(InventoryRecordType.HOLDING);
  }

  @Test
  void deleteMergeRanges_positive_scopedToEntityType() {
    service.deleteMergeRanges(ITEM);

    verify(itemRepository).deleteMergeRanges();
    verify(instanceRepository, never()).deleteMergeRanges();
    verify(holdingRepository, never()).deleteMergeRanges();
    verify(itemRepository, never()).truncateMergeRanges();
  }

  @Test
  void fetchFailedMergeRanges_positive_filteredByEntityType() {
    var itemRange = new MergeRangeEntity(UUID.randomUUID(), UUID.randomUUID(), ITEM, TENANT_ID, "00", "ff",
      Timestamp.from(Instant.now()), ReindexRangeStatus.FAIL, "error");
    when(itemRepository.getFailedMergeRangesOfEntityType()).thenReturn(List.of(itemRange));

    var result = service.fetchFailedMergeRanges(ITEM);

    assertThat(result).containsExactly(itemRange);
    verify(itemRepository, never()).getFailedMergeRanges();
  }

  @Test
  void hasActiveMergeRanges_positive_delegatesToEntityTypeRepository() {
    var activeSince = Timestamp.from(Instant.now());
    when(itemRepository.hasActiveMergeRanges(activeSince)).thenReturn(true);

    assertThat(service.hasActiveMergeRanges(ITEM, activeSince)).isTrue();
  }

  @Test
  void saveEntities_positive_itemOnlyReindexIndexesItemsWithoutPersisting() {
    var item = Map.<String, Object>of("id", UUID.randomUUID().toString());
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(ReindexRecordType.ITEM);
    event.setRecords(List.of(item));
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);

    service.saveEntities(event);

    verify(itemIndexService).reindexItems(TENANT_ID, List.of(item));
    verify(itemRepository, never()).saveEntities(any(), any());
    verifyNoInteractions(instanceChildrenResourceService);
  }

  @Test
  void saveEntities_positive_itemOnlyReindexDoesNotAffectOtherRecordTypes() {
    var entities = Map.<String, Object>of("id", UUID.randomUUID().toString());
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(ReindexRecordType.HOLDINGS);
    event.setRecords(List.of(entities));

    service.saveEntities(event);

    verify(holdingRepository).saveEntities(TENANT_ID, List.of(entities));
    verifyNoInteractions(statusService, itemIndexServiceProvider);
  }

  @Test
  void saveEntities_negative_itemOnlyReindexWithoutItemIndexService() {
    var event = new ReindexRecordsEvent();
    event.setTenant(TENANT_ID);
    event.setRecordType(ReindexRecordType.ITEM);
    event.setRecords(List.of(Map.of("id", "1")));
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> service.saveEntities(event)).isInstanceOf(IllegalStateException.class);

    verify(itemRepository, never()).saveEntities(any(), any());
  }

  @Test
  void saveEntitiesRaw_positive_itemOnlyReindexIndexesItemsWithoutPersisting() {
    var data = Map.<String, Object>of("id", UUID.randomUUID().toString());
    var rawLine = new RawLine("{}", data);
    when(statusService.isItemOnlyReindex()).thenReturn(true);
    when(itemIndexServiceProvider.getIfAvailable()).thenReturn(itemIndexService);

    service.saveEntitiesRaw(TENANT_ID, ReindexRecordType.ITEM, List.of(rawLine));

    verify(itemIndexService).reindexItems(TENANT_ID, List.of(data));
    verify(itemRepository, never()).saveEntitiesRaw(any(), any());
    verifyNoInteractions(instanceChildrenResourceService);
  }

  @Test
  void fetchFailedMergeRanges() {
    // act
    service.fetchFailedMergeRanges();

    // assert
    verify(repositoryMap.values().iterator().next()).getFailedMergeRanges();
  }

  @Test
  void performStagingMigration_positive() {
    var result = new MigrationResult();
    result.setTotalInstances(10);
    result.setTotalHoldings(5);
    result.setTotalItems(3);
    result.setTotalRelationships(2);
    when(stagingMigrationService.migrateAllStagingTables(TENANT_ID)).thenReturn(result);

    service.performStagingMigration(TENANT_ID);

    verify(stagingMigrationService).migrateAllStagingTables(TENANT_ID);
  }
}
