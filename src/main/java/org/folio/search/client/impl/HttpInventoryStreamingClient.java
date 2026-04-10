package org.folio.search.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.InventoryStreamingClient;
import org.folio.search.configuration.properties.StreamingClientProperties;
import org.folio.search.exception.SearchServiceException;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class HttpInventoryStreamingClient implements InventoryStreamingClient {

  private static final String INSTANCES_STREAM_PATH = "/_internal/instance-storage/instances/stream";
  private static final String HOLDINGS_STREAM_PATH = "/_internal/instance-storage/holdings/stream";
  private static final String ITEMS_STREAM_PATH = "/_internal/instance-storage/items/stream";

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() { };
  private static final int CQL_PAGE_SIZE = 1000;

  private final StreamingClientProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Map<String, String> cursorCache = new ConcurrentHashMap<>();

  public HttpInventoryStreamingClient(StreamingClientProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
      .build();
  }

  @Override
  public void streamInstances(String okapiUrl, String tenantId, String token, UUID familyId,
                              Consumer<List<Map<String, Object>>> consumer) {
    streamRecords(okapiUrl, tenantId, token, familyId, INSTANCES_STREAM_PATH, "instances", consumer);
  }

  @Override
  public void streamHoldings(String okapiUrl, String tenantId, String token, UUID familyId,
                             Consumer<List<Map<String, Object>>> consumer) {
    streamRecords(okapiUrl, tenantId, token, familyId, HOLDINGS_STREAM_PATH, "holdings", consumer);
  }

  @Override
  public void streamItems(String okapiUrl, String tenantId, String token, UUID familyId,
                          Consumer<List<Map<String, Object>>> consumer) {
    streamRecords(okapiUrl, tenantId, token, familyId, ITEMS_STREAM_PATH, "items", consumer);
  }

  @Override
  public void clearCursors(UUID familyId) {
    var prefix = familyId.toString() + ":";
    cursorCache.keySet().removeIf(key -> key.startsWith(prefix));
  }

  @Override
  public List<Map<String, Object>> fetchHoldingsByInstanceIds(String okapiUrl, String tenantId,
                                                              String token, List<String> instanceIds) {
    return fetchByCql(okapiUrl, tenantId, token,
      "/holdings-storage/holdings", "holdingsRecords", "instanceId", instanceIds);
  }

  @Override
  public List<Map<String, Object>> fetchItemsByHoldingIds(String okapiUrl, String tenantId,
                                                          String token, List<String> holdingsIds) {
    return fetchByCql(okapiUrl, tenantId, token,
      "/item-storage/items", "items", "holdingsRecordId", holdingsIds);
  }

  private List<Map<String, Object>> fetchByCql(String okapiUrl, String tenantId, String token,
                                                String basePath, String resultKey,
                                                String fieldName, List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    var cqlValues = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(" or "));
    var cql = fieldName + "==(" + cqlValues + ")";
    var encodedCql = java.net.URLEncoder.encode(cql, StandardCharsets.UTF_8);

    var allRecords = new ArrayList<Map<String, Object>>();
    int offset = 0;

    while (true) {
      var url = okapiUrl + basePath + "?query=" + encodedCql
        + "&limit=" + CQL_PAGE_SIZE + "&offset=" + offset;

      var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("x-okapi-tenant", tenantId)
        .header("x-okapi-token", token)
        .header("Accept", "application/json")
        .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
        .GET()
        .build();

      try {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
          log.warn("fetchByCql:: failed [path: {}, status: {}, offset: {}]", basePath, response.statusCode(), offset);
          return allRecords;
        }

        var responseMap = objectMapper.readValue(response.body(), MAP_TYPE_REF);
        var totalRecords = responseMap.get("totalRecords");
        var records = responseMap.get(resultKey);
        if (!(records instanceof List<?> list) || list.isEmpty()) {
          break;
        }

        @SuppressWarnings("unchecked")
        var page = (List<Map<String, Object>>) list;
        allRecords.addAll(page);

        int total = totalRecords instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
        if (allRecords.size() >= total || page.size() < CQL_PAGE_SIZE) {
          break;
        }
        offset += page.size();
      } catch (IOException e) {
        throw new SearchServiceException("IO error fetching %s from %s".formatted(basePath, url), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SearchServiceException("Interrupted while fetching %s".formatted(basePath), e);
      }
    }

    return allRecords;
  }

  private void streamRecords(String okapiUrl, String tenantId, String token, UUID familyId,
                             String path, String resourceType,
                             Consumer<List<Map<String, Object>>> consumer) {
    var cacheKey = familyId + ":" + tenantId + ":" + resourceType;
    var cursor = cursorCache.get(cacheKey);
    long totalRecords = 0;
    var page = new ArrayList<Map<String, Object>>(properties.getPageSize());
    var hasMore = true;

    if (cursor != null) {
      log.info("streamRecords:: resuming [resource: {}, tenant: {}, cursor: {}]", resourceType, tenantId, cursor);
    } else {
      log.info("streamRecords:: starting [resource: {}, tenant: {}]", resourceType, tenantId);
    }

    while (hasMore) {
      var url = buildStreamUrl(okapiUrl, path, cursor);

      var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("x-okapi-tenant", tenantId)
        .header("x-okapi-token", token)
        .header("Accept", "application/x-ndjson")
        .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
        .GET()
        .build();

      HttpResponse<java.io.InputStream> response;
      try {
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      } catch (IOException e) {
        throw new SearchServiceException("IO error streaming %s from %s".formatted(resourceType, url), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SearchServiceException("Interrupted while streaming %s from %s".formatted(resourceType, url), e);
      }

      if (response.statusCode() != 200) {
        throw new SearchServiceException(
          "Failed to stream %s: HTTP %d".formatted(resourceType, response.statusCode()));
      }

      int pageCount;
      try (var reader = new BufferedReader(
        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
        pageCount = readNdjsonLines(reader, resourceType, consumer, page, cacheKey);
      } catch (IOException e) {
        throw new SearchServiceException("IO error reading %s stream from %s".formatted(resourceType, url), e);
      }

      totalRecords += pageCount;

      hasMore = response.headers().firstValue("X-Has-More").map("true"::equals).orElse(false);
      var nextCursor = response.headers().firstValue("X-Next-Cursor").orElse(null);

      if (hasMore && (nextCursor == null || nextCursor.equals(cursor))) {
        throw new SearchServiceException(
          "Cursor did not advance for %s at cursor %s".formatted(resourceType, cursor));
      }
      cursor = nextCursor;

      log.info("streamRecords:: page streamed [resource: {}, pageRecords: {}, total: {}, hasMore: {}]",
        resourceType, pageCount, totalRecords, hasMore);
    }

    if (!page.isEmpty()) {
      consumer.accept(List.copyOf(page));
      totalRecords += page.size();
      cursorCache.put(cacheKey, extractLastId(page));
      page.clear();
    }

    cursorCache.remove(cacheKey);
    log.info("streamRecords:: completed [resource: {}, totalRecords: {}]", resourceType, totalRecords);
  }

  private int readNdjsonLines(BufferedReader reader, String resourceType,
                              Consumer<List<Map<String, Object>>> consumer,
                              ArrayList<Map<String, Object>> page,
                              String cacheKey) throws IOException {
    int count = 0;
    String line;

    while ((line = reader.readLine()) != null) {
      if (line.isBlank()) {
        continue;
      }
      var record = objectMapper.readValue(line, MAP_TYPE_REF);
      page.add(record);
      count++;

      if (page.size() >= properties.getPageSize()) {
        var lastId = extractLastId(page);
        consumer.accept(List.copyOf(page));
        page.clear();
        cursorCache.put(cacheKey, lastId);
        log.debug("streamRecords:: consumed page [resource: {}, pageSize: {}]", resourceType, properties.getPageSize());
      }
    }

    return count;
  }

  private String buildStreamUrl(String okapiUrl, String path, String cursor) {
    var url = okapiUrl + path + "?limit=" + properties.getStreamLimit();
    if (cursor != null) {
      url += "&cursor=" + cursor;
    }
    return url;
  }

  private static String extractLastId(List<Map<String, Object>> records) {
    return (String) records.getLast().get("id");
  }
}
