package org.folio.support.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Instance;

/**
 * Loads all shared integration-test data from JSON classpath resources and
 * verifies that each batch is fully indexed before returning. No caller should
 * need an extra await() after invoking these methods.
 */
public final class SharedTestDataManager {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String BASE = "test-data/";

  private SharedTestDataManager() {}

  // -------------------------------------------------------------------------
  // Read helpers — parse JSON arrays from classpath
  // -------------------------------------------------------------------------

  public static List<Instance> instances() {
    return readArray(BASE + "instances.json", Instance[].class);
  }

  public static List<Map<String, Object>> holdings() {
    return readMapArray(BASE + "holdings.json");
  }

  public static List<Map<String, Object>> items() {
    return readMapArray(BASE + "items.json");
  }

  public static List<Authority> authorities() {
    return readArray(BASE + "authorities.json", Authority[].class);
  }

  public static List<Map<String, Object>> linkedDataInstances() {
    return readMapArray(BASE + "linked-data-instances.json");
  }

  public static List<Map<String, Object>> linkedDataWorks() {
    return readMapArray(BASE + "linked-data-works.json");
  }

  public static List<Map<String, Object>> linkedDataHubs() {
    return readMapArray(BASE + "linked-data-hubs.json");
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
  // Main entry point — orchestrates load + verify for all resource types
  // -------------------------------------------------------------------------

  /**
   * Full data-load sequence: instances (under sub-resource lock), authorities,
   * linked-data. Each batch blocks until fully indexed.
   */
  public static void loadAll(
      String tenantId,
      InstanceSender instanceSender,
      AuthoritySender authoritySender,
      LinkedDataSender linkedDataSender,
      LockManager lockManager,
      IndexVerifier verifier) {

    loadInstancesUnderLock(tenantId, instanceSender, lockManager);
    verifier.awaitIndexed(tenantId, "/search/instances", instanceCount());

    loadAuthorities(tenantId, authoritySender);
    verifier.awaitIndexed(tenantId, "/search/authorities", authorityCount());

    loadLinkedData(tenantId, linkedDataSender, verifier);
  }

  // -------------------------------------------------------------------------
  // Private load methods
  // -------------------------------------------------------------------------

  private static void loadInstancesUnderLock(
      String tenantId, InstanceSender sender, LockManager lock) {
    lock.acquireAll();
    instances().forEach(i -> sender.send(tenantId, i));
    holdings().forEach(h -> sender.sendHolding(tenantId, h));
    items().forEach(it -> sender.sendItem(tenantId, it));
    lock.releaseAll();
  }

  private static void loadAuthorities(String tenantId, AuthoritySender sender) {
    authorities().forEach(a -> sender.send(tenantId, a));
  }

  private static void loadLinkedData(
      String tenantId, LinkedDataSender sender, IndexVerifier verifier) {
    var ldInstances = linkedDataInstances();
    ldInstances.forEach(i -> sender.sendInstance(tenantId, i));
    verifier.awaitIndexed(tenantId, "/search/linked-data/instances", ldInstances.size());

    var ldWorks = linkedDataWorks();
    ldWorks.forEach(w -> sender.sendWork(tenantId, w));
    verifier.awaitIndexed(tenantId, "/search/linked-data/works", ldWorks.size());

    var ldHubs = linkedDataHubs();
    ldHubs.forEach(h -> sender.sendHub(tenantId, h));
    verifier.awaitIndexed(tenantId, "/search/linked-data/hubs", ldHubs.size());
  }

  // -------------------------------------------------------------------------
  // Private JSON parsing utils
  // -------------------------------------------------------------------------

  private static <T> List<T> readArray(String path, Class<T[]> type) {
    try (InputStream in = SharedTestDataManager.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Test data resource not found on classpath: " + path);
      }
      return Arrays.asList(MAPPER.readValue(in, type));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read test data: " + path, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> readMapArray(String path) {
    return (List<Map<String, Object>>) (List<?>) readArray(path, Map[].class);
  }

  // -------------------------------------------------------------------------
  // Functional interfaces — keep this class Spring-free
  // -------------------------------------------------------------------------

  /** Sends instance events and separate holding/item events to Kafka. */
  public interface InstanceSender {
    void send(String tenantId, Instance instance);

    void sendHolding(String tenantId, Map<String, Object> holding);

    void sendItem(String tenantId, Map<String, Object> item);
  }

  /** Sends authority events to Kafka. */
  @FunctionalInterface
  public interface AuthoritySender {
    void send(String tenantId, Authority authority);
  }

  /** Sends linked-data events (instance / work / hub) to Kafka. */
  public interface LinkedDataSender {
    void sendInstance(String tenantId, Map<String, Object> instance);

    void sendWork(String tenantId, Map<String, Object> work);

    void sendHub(String tenantId, Map<String, Object> hub);
  }

  /** Acquires and releases sub-resource index locks around instance loading. */
  public interface LockManager {
    void acquireAll();

    void releaseAll();
  }

  /**
   * Blocks until a resource batch is fully indexed.
   */
  @FunctionalInterface
  public interface IndexVerifier {
    void awaitIndexed(String tenantId, String searchPath, int expectedCount);
  }
}
