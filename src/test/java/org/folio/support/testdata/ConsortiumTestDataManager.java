package org.folio.support.testdata;

import static java.util.Collections.unmodifiableMap;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.utils.JsonTestUtils.readJsonFromFile;
import static org.folio.support.utils.TestUtils.array;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.folio.support.api.InventoryApi;
import org.folio.support.testdata.SharedTestDataManager.LockManager;
import org.folio.support.testdata.SharedTestDataManager.RecordsChildrenIndexer;
import tools.jackson.core.type.TypeReference;

/**
 * Shared ECS (consortium) inventory data set used by {@code SearchBrowseConsortiumSuiteIT} and
 * {@code BrowseCallNumberFromItemsConsortiumIT} (loaded by {@code EcsInventorySetup}).
 *
 * <p>The records live in {@code src/test/resources/test-data/ecs/<tenant>/} as {@code instances.json},
 * {@code holdings.json} and {@code items.json} (one folder per tenant: {@code central}, {@code member},
 * {@code member2}), in the same flat format as the non-consortium data set, so they are managed with the same
 * toolchain: {@code manage/import.py --data-dir ecs/<tenant>}. Holdings and items of a shared instance that belong
 * to a member tenant are in that tenant's folder, next to the member's shadow copy of the instance
 * (source {@code CONSORTIUM-MARC} / {@code CONSORTIUM-FOLIO}), as in a real consortium. The semantic-web sample
 * instance ({@code SampleInstances#getSemanticWeb()}) is added as a local record of the member tenant.
 *
 * <p>Data set:
 * <ul>
 *   <li>browse fixtures: call number instances ("Instance 1".."Instance 50", 30 shared and 20 local), classification,
 *       contributor and subject instances ("instance #NN"), a member2 call number instance with
 *       {@link #MEMBER2_LOCATION}, and member/member2 copies of "Instance 1";</li>
 *   <li>consortium search slice: the shared instance {@link #SHARED_INSTANCE_ID} with holdings and items in the
 *       central, member and member2 tenants, and the member-local instance {@link #LOCAL_INSTANCE_ID} with its
 *       holdings and items.</li>
 * </ul>
 *
 * <p>New tests should scope their assertions to their own fixtures (by id or by distinct values) so that adding
 * records does not change them; only the browse/facet tests that cover the whole data set use the counts below.
 *
 * <p>Unlike {@link SharedTestDataManager}, which indexes records through the {@code /search-index/records} endpoint,
 * this data set is sent through Kafka ({@link InventoryApi}). Only the Kafka path is consortium-aware: it stores
 * member-tenant records under the central tenant and indexes them into the central tenant index. The endpoint
 * keeps them in the member tenant, so shared and local records would not be combined.
 */
public final class ConsortiumTestDataManager {

  // consortium search slice
  public static final String SHARED_INSTANCE_ID = "4b9c1f3e-7d2a-4e8b-9c61-2f5a8d7e3b10";
  public static final String LOCAL_INSTANCE_ID = "9e2d7a41-3c5b-4f86-a1d9-6b0e8c4f2a75";
  // call number data
  public static final String MEMBER2_LOCATION = "b6bb6323-9db6-3df8-8fa8-271994a7afca";
  public static final String CALL_NUMBER_LC_TYPE_ID = "cbc422b0-b71a-4d2d-9a2a-9fcc56a57a3e";
  // classification data
  public static final String LC_TYPE_ID = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  public static final String LC2_TYPE_ID = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  public static final String DEWEY_TYPE_ID = "50524585-046b-49a1-8ca7-8d46f2a8dc19";
  // contributor data
  public static final String[] CONTRIBUTOR_NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985", "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e");
  public static final String[] CONTRIBUTOR_TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299", "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40");
  public static final String[] CONTRIBUTOR_AUTHORITY_IDS =
    array("0a4c6d10-2161-4f64-aace-9e919489b6c9", "7ff32633-cc49-4332-870a-b05e329d2a2d");
  // subject data
  public static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  public static final String MUSIC_SOURCE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91b";
  public static final String MUSIC_SOURCE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bd";
  public static final String MUSIC_TYPE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91c";
  public static final String MUSIC_TYPE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21be";

  // expected sub-resource document counts in the central tenant index for the whole data set
  // (instance, holdings and item counts are derived from the records, see instancesCount() etc.)
  public static final int CALL_NUMBER_COUNT = 105;
  public static final int CONTRIBUTOR_COUNT = 20;
  public static final int CLASSIFICATION_COUNT = 21;
  public static final int SUBJECT_COUNT = 33;

  /**
   * Central instance ("instance #00") whose contributors are removed after it is created, to test that a contributor
   * is deleted when it is no longer linked to any instance.
   */
  private static final String UNLINKED_CONTRIBUTOR_INSTANCE_ID = "6db30ff9-f99a-3de7-be2a-0a4fb6077554";
  private static final String INSTANCE_SOURCE_FIELD = "source";
  private static final String ITEM_INSTANCE_SOURCE_FIELD = "instanceSource";
  private static final String BASE = "/test-data/ecs/";
  private static final Map<String, String> TENANT_FOLDERS = Map.of(
    CENTRAL_TENANT_ID, "central",
    MEMBER_TENANT_ID, "member",
    MEMBER2_TENANT_ID, "member2");
  private static final Map<String, TenantRecords> RECORDS = new LinkedHashMap<>();

  static {
    TENANT_FOLDERS.forEach((tenantId, folder) -> RECORDS.put(tenantId, new TenantRecords(
      loadRecords(folder, "instances.json"), loadRecords(folder, "holdings.json"), loadRecords(folder, "items.json"))));
  }

  private ConsortiumTestDataManager() { }

  /**
   * Returns the instance records of a tenant as stored in its JSON file (read-only).
   */
  public static List<Map<String, Object>> instances(String tenantId) {
    return RECORDS.get(tenantId).instances();
  }

  /**
   * Returns the holdings records of a tenant as stored in its JSON file (read-only).
   */
  public static List<Map<String, Object>> holdings(String tenantId) {
    return RECORDS.get(tenantId).holdings();
  }

  /**
   * Returns the item records of a tenant as stored in its JSON file (read-only).
   */
  public static List<Map<String, Object>> items(String tenantId) {
    return RECORDS.get(tenantId).items();
  }

  /**
   * Returns the holdings records of the given instance across all tenants, each with the id of its tenant.
   */
  public static List<TenantRecord> holdingsOfInstance(String instanceId) {
    return recordsOfInstance(instanceId, ConsortiumTestDataManager::holdings);
  }

  /**
   * Returns the item records of the given instance across all tenants, each with the id of its tenant.
   */
  public static List<TenantRecord> itemsOfInstance(String instanceId) {
    return recordsOfInstance(instanceId, ConsortiumTestDataManager::items);
  }

  /**
   * Returns the number of instance documents of the whole data set in the central tenant index: unique instance ids
   * of all tenants (shadow copies of a shared instance are the same document) plus the semantic-web instance.
   */
  public static int instancesCount() {
    var ids = RECORDS.keySet().stream()
      .flatMap(tenantId -> instanceIds(tenantId).stream())
      .collect(Collectors.toCollection(HashSet::new));
    ids.add(getSemanticWeb().getId());
    return ids.size();
  }

  /**
   * Returns the number of holdings of the whole data set: every holdings record of every tenant plus the semantic-web
   * holdings.
   */
  public static int holdingsCount() {
    return RECORDS.values().stream().mapToInt(records -> records.holdings().size()).sum()
      + getSemanticWeb().getHoldings().size();
  }

  /**
   * Returns the ids of all items of the whole data set: every item record of every tenant plus the semantic-web items.
   */
  public static List<String> itemIds() {
    var ids = RECORDS.values().stream()
      .flatMap(records -> records.items().stream())
      .map(item -> MapUtils.getString(item, ID_FIELD))
      .collect(Collectors.toCollection(ArrayList::new));
    getSemanticWeb().getItems().forEach(item -> ids.add(item.getId()));
    return ids;
  }

  /**
   * Creates the whole ECS inventory data set while holding all sub-resource locks, so that scheduled
   * sub-resource processing does not race with the data load. After the locks are released, sub-resources are
   * processed synchronously via {@code childrenIndexer}, as in {@link SharedTestDataManager#loadAll}.
   *
   * <p>The central, member and member2 tenants must be enabled before, and the item index must be enabled
   * ({@code folio.search-config.indexing.item-index-enabled=true}).
   *
   * <p>Central records are created and awaited first, because the member records include shadow copies of the shared
   * instances and holdings/items that belong to them; member and member2 records are then sent together.
   *
   * @param inventoryApi      sends the inventory events through Kafka
   * @param visibilityAwaiter waits until the given instances are searchable in the given tenant
   * @param indexingAwaiter   waits until the whole data set is indexed, runs after sub-resources are processed
   */
  public static void loadAll(LockManager lockManager, InventoryApi inventoryApi,
                             InstancesVisibilityAwaiter visibilityAwaiter,
                             RecordsChildrenIndexer childrenIndexer, Runnable indexingAwaiter) {
    lockManager.lockAll();
    try {
      createRecords(inventoryApi, CENTRAL_TENANT_ID);
      visibilityAwaiter.await(CENTRAL_TENANT_ID, instanceIds(CENTRAL_TENANT_ID));
      unlinkContributors(inventoryApi);

      createRecords(inventoryApi, MEMBER_TENANT_ID);
      inventoryApi.createInstance(MEMBER_TENANT_ID, getSemanticWeb());
      createRecords(inventoryApi, MEMBER2_TENANT_ID);
      var memberInstanceIds = new ArrayList<>(instanceIds(MEMBER_TENANT_ID));
      memberInstanceIds.add(getSemanticWeb().getId());
      visibilityAwaiter.await(MEMBER_TENANT_ID, memberInstanceIds);
      visibilityAwaiter.await(MEMBER2_TENANT_ID, instanceIds(MEMBER2_TENANT_ID));
    } finally {
      lockManager.unlockAll();
    }
    childrenIndexer.indexChildren();
    indexingAwaiter.run();
  }

  /**
   * Sends the instances, holdings and items of a tenant. {@link InventoryApi} modifies the maps it gets, so it is
   * given copies and the loaded records stay unchanged.
   *
   * <p>Item events carry the {@code instanceSource} of their instance in the same tenant, as inventory item events
   * do: the item index derives the {@code shared} flag of member items of shared instances from it.
   */
  private static void createRecords(InventoryApi inventoryApi, String tenantId) {
    instances(tenantId).forEach(instance -> inventoryApi.createInstance(tenantId, new LinkedHashMap<>(instance)));
    holdings(tenantId).forEach(holding ->
      inventoryApi.createHolding(tenantId, instanceId(holding), new LinkedHashMap<>(holding)));
    var instanceSources = new HashMap<String, String>();
    instances(tenantId).forEach(instance -> instanceSources.put(
      MapUtils.getString(instance, ID_FIELD), MapUtils.getString(instance, INSTANCE_SOURCE_FIELD)));
    items(tenantId).forEach(item -> {
      var itemEvent = new LinkedHashMap<>(item);
      var instanceSource = instanceSources.get(instanceId(item));
      if (instanceSource != null) {
        itemEvent.put(ITEM_INSTANCE_SOURCE_FIELD, instanceSource);
      }
      inventoryApi.createItem(tenantId, instanceId(item), itemEvent);
    });
  }

  private static void unlinkContributors(InventoryApi inventoryApi) {
    var instance = instances(CENTRAL_TENANT_ID).stream()
      .filter(instanceMap -> UNLINKED_CONTRIBUTOR_INSTANCE_ID.equals(instanceMap.get(ID_FIELD)))
      .findFirst()
      .orElseThrow();
    var updated = new LinkedHashMap<>(instance);
    updated.put("contributors", List.of());
    inventoryApi.updateInstance(CENTRAL_TENANT_ID, updated);
  }

  private static List<String> instanceIds(String tenantId) {
    return instances(tenantId).stream()
      .map(instanceMap -> MapUtils.getString(instanceMap, ID_FIELD))
      .distinct()
      .toList();
  }

  private static String instanceId(Map<String, Object> inventoryRecord) {
    return MapUtils.getString(inventoryRecord, INSTANCE_ID_FIELD);
  }

  private static List<TenantRecord> recordsOfInstance(String instanceId,
                                                      Function<String, List<Map<String, Object>>> recordsOfTenant) {
    return RECORDS.keySet().stream()
      .flatMap(tenantId -> recordsOfTenant.apply(tenantId).stream()
        .filter(inventoryRecord -> instanceId.equals(instanceId(inventoryRecord)))
        .map(inventoryRecord -> new TenantRecord(tenantId, inventoryRecord)))
      .toList();
  }

  private static List<Map<String, Object>> loadRecords(String folder, String fileName) {
    List<Map<String, Object>> records = readJsonFromFile(BASE + folder + "/" + fileName, new TypeReference<>() { });
    return records.stream().map(jsonRecord -> unmodifiableMap(new LinkedHashMap<>(jsonRecord))).toList();
  }

  /**
   * Waits until the instances with the given ids are searchable in the given tenant.
   */
  @FunctionalInterface
  public interface InstancesVisibilityAwaiter {

    void await(String tenantId, List<String> instanceIds);
  }

  /**
   * An inventory record (holdings or item) as stored in its JSON file together with the id of its tenant.
   */
  public record TenantRecord(String tenantId, Map<String, Object> inventoryRecord) { }

  private record TenantRecords(List<Map<String, Object>> instances, List<Map<String, Object>> holdings,
                               List<Map<String, Object>> items) { }
}
