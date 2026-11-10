package org.folio.support.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.folio.support.TestConstants.reindexFileReadyTopic;
import static org.folio.support.TestConstants.reindexRecordsTopic;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.event.ReindexFileReadyEvent;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Plays the role of mod-inventory-storage for item records reindex tests. It answers the item count request and, for
 * every published or exported range of item records, delivers the configured records through Kafka the way
 * inventory does: as a {@code reindex-records} event in the PUBLISH mode, or as a {@code file-ready} event with the
 * file content served by {@link #file(String)} in the EXPORT mode.
 */
@Log4j2
public class ReindexInventoryStub implements RequestListener {

  private static final String COUNT_STUB_ID = "7b0b0c58-3a6c-4d02-9c52-2d1c5a5d3d11";

  private final WireMockServer wireMock;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final boolean export;
  private final Map<String, List<Map<String, Object>>> recordsByTenant = new ConcurrentHashMap<>();
  private final Map<String, String> files = new ConcurrentHashMap<>();
  private final List<PublishedRange> publishedRanges = new CopyOnWriteArrayList<>();
  private final List<PublishedRange> heldRanges = new CopyOnWriteArrayList<>();
  private volatile boolean active = true;
  private volatile boolean holdRanges;

  public ReindexInventoryStub(WireMockServer wireMock, KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper, boolean export) {
    this.wireMock = wireMock;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.export = export;
    wireMock.addMockServiceRequestListener(this);
    wireMock.stubFor(post(urlPathEqualTo("/inventory-reindex-records/publish")).willReturn(ok()));
    wireMock.stubFor(post(urlPathEqualTo("/inventory-reindex-records/export")).willReturn(ok()));
    itemCount(0);
  }

  /**
   * Sets the number of items inventory reports, which defines the number of merge ranges.
   */
  public void itemCount(int count) {
    var stub = get(urlPathEqualTo("/item-storage/items"))
      .withQueryParam("limit", equalTo("0"))
      .withId(UUID.fromString(COUNT_STUB_ID))
      .willReturn(ok("{\"items\": [], \"totalRecords\": " + count + "}")
        .withHeader("Content-Type", "application/json"));
    if (wireMock.getStubMapping(UUID.fromString(COUNT_STUB_ID)).getItem() == null) {
      wireMock.stubFor(stub);
    } else {
      wireMock.editStub(stub);
    }
  }

  /**
   * Sets the records inventory returns for the tenant, replacing the previous ones.
   */
  public void records(String tenant, List<Map<String, Object>> records) {
    recordsByTenant.put(tenant, List.copyOf(records));
  }

  /**
   * Holds published ranges instead of delivering them, until {@link #releaseHeldRanges()} is called.
   */
  public void holdRanges(boolean hold) {
    this.holdRanges = hold;
  }

  public List<PublishedRange> publishedRanges() {
    return List.copyOf(publishedRanges);
  }

  public List<PublishedRange> heldRanges() {
    return List.copyOf(heldRanges);
  }

  public void releaseHeldRanges() {
    var ranges = new ArrayList<>(heldRanges);
    heldRanges.clear();
    ranges.forEach(this::deliver);
  }

  public String file(String objectKey) {
    return files.get(objectKey);
  }

  public void reset() {
    recordsByTenant.clear();
    files.clear();
    publishedRanges.clear();
    heldRanges.clear();
    holdRanges = false;
    itemCount(0);
  }

  /**
   * The stub is registered in a WireMock server shared by all tests, so it must be switched off when a test ends.
   */
  public void deactivate() {
    active = false;
    wireMock.removeStubMapping(UUID.fromString(COUNT_STUB_ID));
  }

  @Override
  public void requestReceived(Request request, Response response) {
    if (!active || !request.getUrl().startsWith("/inventory-reindex-records/")) {
      return;
    }
    try {
      var body = objectMapper.readValue(request.getBodyAsString(), Map.class);
      if (!"item".equals(body.get("recordType"))) {
        return;
      }
      var tenant = request.getHeader("x-okapi-tenant");
      var range = new PublishedRange(String.valueOf(body.get("id")),
        body.get("traceId") == null ? UUID.randomUUID().toString() : String.valueOf(body.get("traceId")), tenant);
      publishedRanges.add(range);
      if (holdRanges) {
        heldRanges.add(range);
      } else {
        deliver(range);
      }
    } catch (Exception e) {
      log.error("Failed to handle reindex records request [url: {}]", request.getUrl(), e);
    }
  }

  private void deliver(PublishedRange range) {
    var records = recordsByTenant.getOrDefault(range.tenant(), List.of());
    if (export) {
      var objectKey = "item/" + range.rangeId();
      var content = new StringBuilder();
      records.forEach(record -> content.append(objectMapper.writeValueAsString(record)).append('\n'));
      files.put(objectKey, content.toString());
      var event = new ReindexFileReadyEvent(range.tenant(), ReindexRecordType.ITEM, range.rangeId(), range.traceId(),
        "bucket", objectKey, "2026-01-01T00:00:00Z");
      kafkaTemplate.send(reindexFileReadyTopic(range.tenant()), range.rangeId(), event);
    } else {
      var event = new ReindexRecordsEvent();
      event.setTenant(range.tenant());
      event.setRecordType(ReindexRecordType.ITEM);
      event.setRecords(new ArrayList<>(records));
      kafkaTemplate.send(reindexRecordsTopic(range.tenant()), range.rangeId(), event);
    }
  }

  public record PublishedRange(String rangeId, String traceId, String tenant) { }
}
