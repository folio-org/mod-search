package org.folio.support.testdata;

import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.HOLDINGS;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_HUB;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import tools.jackson.core.type.TypeReference;

/**
 * Loads all shared integration-test data from JSON classpath resources
 * via the {@code POST /search/index/records} HTTP endpoint.
 */
public final class SharedTestDataManager {

  private static final String BASE = "/test-data/";

  private static final List<Map<String, Object>> INSTANCE_RECORDS = loadRecords("instances.json");
  private static final List<Map<String, Object>> HOLDINGS_RECORDS = loadRecords("holdings.json");
  private static final List<Map<String, Object>> ITEM_RECORDS = loadRecords("items.json");
  private static final List<Map<String, Object>> AUTHORITY_RECORDS = loadRecords("authorities.json");
  private static final List<Map<String, Object>> LD_INSTANCE_RECORDS = loadRecords("linked-data-instances.json");
  private static final List<Map<String, Object>> LD_WORK_RECORDS = loadRecords("linked-data-works.json");
  private static final List<Map<String, Object>> LD_HUB_RECORDS = loadRecords("linked-data-hubs.json");

  private SharedTestDataManager() { }

  public static List<Map<String, Object>> instances() {
    return INSTANCE_RECORDS;
  }

  public static List<Map<String, Object>> holdings() {
    return HOLDINGS_RECORDS;
  }

  public static List<Map<String, Object>> items() {
    return ITEM_RECORDS;
  }

  public static List<Map<String, Object>> authorities() {
    return AUTHORITY_RECORDS;
  }

  public static List<Map<String, Object>> linkedDataInstances() {
    return LD_INSTANCE_RECORDS;
  }

  public static List<Map<String, Object>> linkedDataWorks() {
    return LD_WORK_RECORDS;
  }

  public static List<Map<String, Object>> linkedDataHubs() {
    return LD_HUB_RECORDS;
  }

  public static List<String> instanceIds() {
    return INSTANCE_RECORDS.stream().map(i -> i.get(ID_FIELD).toString()).toList();
  }

  public static List<String> holdingIds() {
    return HOLDINGS_RECORDS.stream().map(i -> i.get(ID_FIELD).toString()).toList();
  }

  public static List<String> authorityIds() {
    return AUTHORITY_RECORDS.stream().map(i -> i.get(ID_FIELD).toString()).toList();
  }

  public static int instancesCount() {
    return INSTANCE_RECORDS.size();
  }

  public static int holdingsCount() {
    return HOLDINGS_RECORDS.size();
  }

  public static int itemsCount() {
    return ITEM_RECORDS.size();
  }

  public static int linkedDataInstancesCount() {
    return LD_INSTANCE_RECORDS.size();
  }

  public static int linkedDataWorksCount() {
    return LD_WORK_RECORDS.size();
  }

  public static int linkedDataHubsCount() {
    return LD_HUB_RECORDS.size();
  }

  public static int authoritiesCount() {
    return AUTHORITY_RECORDS.size();
  }

  public static void loadAll(String tenantId, LockManager lockManager,
                             RecordsChildrenIndexer childrenIndexer, RecordsIndexer indexer,
                             Runnable postProcesAction) {
    loadInventory(tenantId, lockManager, childrenIndexer, indexer);
    loadAuthorities(tenantId, indexer);
    loadLinkedData(tenantId, indexer);
    postProcesAction.run();
  }

  /**
   * Indexes all inventory records (instances, holdings, items) under sub-resource locks so that
   * the scheduled job does not race with initial data load. After releasing locks, calls
   * {@code RecordsChildrenIndexer#indexChildren()} directly to process sub-resources synchronously.
   */
  public static void loadInventory(String tenantId, LockManager lockManager,
                                   RecordsChildrenIndexer childrenIndexer,
                                   RecordsIndexer indexer) {
    lockManager.lockAll();
    indexer.index(instances().stream().map(i -> event(i, INSTANCE, tenantId)).toList(), tenantId);
    indexer.index(holdings().stream().map(i -> event(i, HOLDINGS, tenantId)).toList(), tenantId);
    indexer.index(items().stream().map(i -> event(i, ITEM, tenantId)).toList(), tenantId);
    lockManager.unlockAll();
    childrenIndexer.indexChildren();
  }

  /**
   * Indexes all authority records.
   */
  public static void loadAuthorities(String tenantId, RecordsIndexer indexer) {
    var events = authorities().stream()
      .map(a -> event(a, AUTHORITY, tenantId))
      .toList();
    indexer.index(events, tenantId);
  }

  /**
   * Indexes all linked-data records (instances, works, hubs) in order.
   */
  public static void loadLinkedData(String tenantId, RecordsIndexer indexer) {
    loadLinkedDataInstances(tenantId, indexer);
    loadLinkedDataWorks(tenantId, indexer);
    loadLinkedDataHubs(tenantId, indexer);
  }

  public static void loadLinkedDataInstances(String tenantId, RecordsIndexer indexer) {
    indexer.index(linkedDataInstances().stream().map(i -> event(i, LINKED_DATA_INSTANCE, tenantId)).toList(), tenantId);
  }

  public static void loadLinkedDataWorks(String tenantId, RecordsIndexer indexer) {
    indexer.index(linkedDataWorks().stream().map(w -> event(w, LINKED_DATA_WORK, tenantId)).toList(), tenantId);
  }

  public static void loadLinkedDataHubs(String tenantId, RecordsIndexer indexer) {
    indexer.index(linkedDataHubs().stream().map(h -> event(h, LINKED_DATA_HUB, tenantId)).toList(), tenantId);
  }

  private static List<Map<String, Object>> loadRecords(String fileName) {
    return readJsonFromFile(BASE + fileName, new TypeReference<>() { });
  }

  private static ResourceEvent event(Map<String, Object> payload, ResourceType type, String tenantId) {
    return new ResourceEvent()
      .id(payload.get(ID_FIELD).toString())
      .resourceName(type.getName())
      .type(CREATE)
      .tenant(tenantId)
      ._new(payload);
  }

  @FunctionalInterface
  public interface RecordsIndexer {
    void index(List<ResourceEvent> events, String tenantId);
  }

  @FunctionalInterface
  public interface RecordsChildrenIndexer {
    void indexChildren();
  }

  public interface LockManager {

    void lockAll();

    void unlockAll();
  }
}
