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

  private SharedTestDataManager() { }

  // -------------------------------------------------------------------------
  // Read helpers — parse JSON arrays from classpath
  // -------------------------------------------------------------------------

  public static List<Map<String, Object>> instances() {
    return readJsonFromFile(BASE + "instances.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> holdings() {
    return readJsonFromFile(BASE + "holdings.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> items() {
    return readJsonFromFile(BASE + "items.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> authorities() {
    return readJsonFromFile(BASE + "authorities.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> linkedDataInstances() {
    return readJsonFromFile(BASE + "linked-data-instances.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> linkedDataWorks() {
    return readJsonFromFile(BASE + "linked-data-works.json", new TypeReference<>() { });
  }

  public static List<Map<String, Object>> linkedDataHubs() {
    return readJsonFromFile(BASE + "linked-data-hubs.json", new TypeReference<>() { });
  }

  // -------------------------------------------------------------------------
  // Count helpers
  // -------------------------------------------------------------------------

  public static int instanceCount() {
    return instances().size();
  }

  public static int authorityCount() {
    return authorities().size();
  }

  // -------------------------------------------------------------------------
  // Loading entry points
  // -------------------------------------------------------------------------

  /**
   * Indexes all inventory records (instances, holdings, items) under sub-resource locks so that
   * the scheduled job does not race with initial data load. After releasing locks, calls
   * {@code persistChildren()} directly to process sub-resources synchronously.
   */
  public static void loadInventory(String tenantId, LockManager lockManager,
                                   RecordsChildrenIndexer childrenIndexer,
                                   RecordsIndexer indexer) {
    lockManager.lockAll();
    indexer.index(instances().stream().map(i -> event(i, INSTANCE, tenantId)).toList());
    indexer.index(holdings().stream().map(i -> event(i, HOLDINGS, tenantId)).toList());
    indexer.index(items().stream().map(i -> event(i, ITEM, tenantId)).toList());
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
    indexer.index(events);
  }

  /**
   * Indexes all linked-data records (instances, works, hubs) in order.
   */
  public static void loadLinkedData(String tenantId, RecordsIndexer indexer) {
    indexer.index(linkedDataInstances().stream().map(i -> event(i, LINKED_DATA_INSTANCE, tenantId)).toList());
    indexer.index(linkedDataWorks().stream().map(w -> event(w, LINKED_DATA_WORK, tenantId)).toList());
    indexer.index(linkedDataHubs().stream().map(h -> event(h, LINKED_DATA_HUB, tenantId)).toList());
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
    void index(List<ResourceEvent> events);
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
