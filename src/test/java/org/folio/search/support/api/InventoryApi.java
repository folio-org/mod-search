package org.folio.search.support.api;

import static java.util.Collections.emptyMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_HOLDING_TOPIC;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.INVENTORY_ITEM_TOPIC;
import static org.folio.search.utils.TestUtils.eventBody;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class InventoryApi {
  private static final Map<String, Map<String, Instance>> INSTANCE_STORE = new HashMap<>();
  private static final Map<String, Map<String, HoldingEvent>> HOLDING_STORE = new HashMap<>();
  private static final Map<String, Map<String, ItemEvent>> ITEM_STORE = new HashMap<>();

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void createInstance(String tenantName, Instance instance) {
    INSTANCE_STORE.computeIfAbsent(tenantName, k -> new HashMap<>())
      .put(instance.getId(), instance);

    kafkaTemplate.send(INVENTORY_INSTANCE_TOPIC, instance.getId(),
      eventBody(INSTANCE_RESOURCE, instance).tenant(tenantName));

    if (instance.getHoldings() != null) {
      for (Holding holdingsRecord : instance.getHoldings()) {
        createHolding(tenantName, instance.getId(), holdingsRecord);
      }
    }

    if (instance.getItems() != null) {
      for (Item item : instance.getItems()) {
        createItem(tenantName, instance.getId(), item);
      }
    }
  }

  public void createHolding(String tenant, String instanceId, Holding holding) {
    var event = new HoldingEvent(holding, instanceId);
    HOLDING_STORE.computeIfAbsent(tenant, k -> new HashMap<>())
      .put(holding.getId(), event);

    kafkaTemplate.send(INVENTORY_HOLDING_TOPIC, instanceId,
      eventBody(INSTANCE_RESOURCE, event).tenant(tenant));
  }

  public void createItem(String tenant, String instanceId, Item item) {
    var event = new ItemEvent(item, instanceId);
    ITEM_STORE.computeIfAbsent(tenant, k -> new HashMap<>())
      .put(item.getId(), event);

    kafkaTemplate.send(INVENTORY_ITEM_TOPIC, instanceId,
      eventBody(INSTANCE_RESOURCE, event).tenant(tenant));
  }

  public static Optional<Instance> getInventoryView(String tenant, String id) {
    var instance = INSTANCE_STORE.getOrDefault(tenant, emptyMap()).get(id);

    var hrs = HOLDING_STORE.getOrDefault(tenant, emptyMap()).values().stream()
      .filter(hr -> hr.getInstanceId().equals(id))
      .map(HoldingEvent::getHolding)
      .collect(Collectors.toList());

    var items = ITEM_STORE.getOrDefault(tenant, emptyMap()).values().stream()
      .filter(item -> item.getInstanceId().equals(id))
      .map(ItemEvent::getItem)
      .collect(Collectors.toList());

    return Optional.ofNullable(instance)
      .map(inst -> inst.holdings(hrs))
      .map(inst -> inst.items(items));
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  private static class HoldingEvent {
    @JsonUnwrapped
    private Holding holding;
    private String instanceId;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  private static class ItemEvent {
    @JsonUnwrapped
    private Item item;
    private String instanceId;
  }
}
