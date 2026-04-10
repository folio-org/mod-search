package org.folio.search.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for streaming inventory records from mod-inventory-storage via HTTP NDJSON endpoints.
 */
public interface InventoryStreamingClient {

  void streamInstances(String okapiUrl, String tenantId, String token, UUID familyId,
                       Consumer<List<Map<String, Object>>> consumer);

  void streamHoldings(String okapiUrl, String tenantId, String token, UUID familyId,
                      Consumer<List<Map<String, Object>>> consumer);

  void streamItems(String okapiUrl, String tenantId, String token, UUID familyId,
                   Consumer<List<Map<String, Object>>> consumer);

  void clearCursors(UUID familyId);

  /**
   * Fetches holdings records for the given instance IDs via CQL query.
   *
   * @param okapiUrl     the Okapi URL for routing
   * @param tenantId     the tenant identifier
   * @param token        the authentication token
   * @param instanceIds  list of instance IDs to fetch holdings for
   * @return list of holdings records as maps
   */
  List<Map<String, Object>> fetchHoldingsByInstanceIds(String okapiUrl, String tenantId,
                                                       String token, List<String> instanceIds);

  /**
   * Fetches item records for the given holdings IDs via CQL query.
   *
   * @param okapiUrl     the Okapi URL for routing
   * @param tenantId     the tenant identifier
   * @param token        the authentication token
   * @param holdingsIds  list of holdings IDs to fetch items for
   * @return list of item records as maps
   */
  List<Map<String, Object>> fetchItemsByHoldingIds(String okapiUrl, String tenantId,
                                                   String token, List<String> holdingsIds);
}
