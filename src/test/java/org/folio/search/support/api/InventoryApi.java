package org.folio.search.support.api;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections.MapUtils.getString;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.inventoryHoldingTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryItemTopic;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.resourceEvent;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.Instance;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class InventoryApi {

  private static final Map<String, Map<String, Map<String, Object>>> INSTANCE_STORE = new LinkedHashMap<>();
  private static final Map<String, Map<String, HoldingEvent>> HOLDING_STORE = new LinkedHashMap<>();
  private static final Map<String, Map<String, ItemEvent>> ITEM_STORE = new LinkedHashMap<>();
  private static final String ID_FIELD = "id";

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void createInstance(String tenantId, Instance instance) {
    createInstance(tenantId, OBJECT_MAPPER.convertValue(instance, MAP_TYPE_REFERENCE));
  }

  public void createInstance(String tenantId, Map<String, Object> instance) {
    var instanceId = getString(instance, "id");
    INSTANCE_STORE.computeIfAbsent(tenantId, k -> new LinkedHashMap<>()).put(instanceId, instance);

    kafkaTemplate.send(inventoryInstanceTopic(tenantId), instanceId, resourceEvent(null, instance).tenant(tenantId));
    createNestedResources(instance, INSTANCE_HOLDING_FIELD_NAME, hr -> createHolding(tenantId, instanceId, hr));
    createNestedResources(instance, INSTANCE_ITEM_FIELD_NAME, item -> createItem(tenantId, instanceId, item));
  }

  @SuppressWarnings("unchecked")
  private void createNestedResources(Map<String, Object> instance, String key, Consumer<Map<String, Object>> consumer) {
    var items = (List<Map<String, Object>>) MapUtils.getObject(instance, key);
    if (items != null) {
      items.forEach(consumer);
    }
  }

  public void createHolding(String tenant, String instanceId, Map<String, Object> holding) {
    var event = new HoldingEvent(holding, instanceId);
    HOLDING_STORE.computeIfAbsent(tenant, k -> new LinkedHashMap<>()).put(getString(holding, ID_FIELD), event);
    kafkaTemplate.send(inventoryHoldingTopic(tenant), instanceId,
      resourceEvent(INSTANCE_RESOURCE, event).tenant(tenant));
  }

  public void createItem(String tenant, String instanceId, Map<String, Object> item) {
    var event = new ItemEvent(item, instanceId);
    ITEM_STORE.computeIfAbsent(tenant, k -> new LinkedHashMap<>()).put(getString(item, ID_FIELD), event);
    kafkaTemplate.send(inventoryItemTopic(tenant), instanceId,
      resourceEvent(INSTANCE_RESOURCE, event).tenant(tenant));
  }

  public void deleteItem(String tenant, String id) {
    var item = ITEM_STORE.get(tenant).remove(id);

    kafkaTemplate.send(inventoryItemTopic(tenant), item.getInstanceId(),
      resourceEvent(INSTANCE_RESOURCE, null).old(item).tenant(tenant).type(DELETE));
  }

  public void deleteHolding(String tenant, String id) {
    var hr = HOLDING_STORE.get(tenant).remove(id);

    kafkaTemplate.send(inventoryHoldingTopic(tenant), hr.getInstanceId(),
      resourceEvent(INSTANCE_RESOURCE, null).old(hr).tenant(tenant).type(DELETE));
  }

  public void deleteInstance(String tenant, String id) {
    var instance = INSTANCE_STORE.get(tenant).remove(id);

    kafkaTemplate.send(inventoryInstanceTopic(tenant), getString(instance, ID_FIELD),
      resourceEvent(INSTANCE_RESOURCE, null).old(instance).tenant(tenant).type(DELETE));
  }

  public static Optional<Map<String, Object>> getInventoryView(String tenant, String id) {
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
      .map(inst -> putField(inst, INSTANCE_HOLDING_FIELD_NAME, hrs))
      .map(inst -> putField(inst, INSTANCE_ITEM_FIELD_NAME, items));
  }

  private static Map<String, Object> putField(Map<String, Object> instance, String key, Object subResources) {
    instance.put(key, subResources);
    return instance;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  private static class HoldingEvent {

    @JsonUnwrapped
    private Map<String, Object> holding;
    private String instanceId;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  private static class ItemEvent {

    @JsonUnwrapped
    private Map<String, Object> item;
    private String instanceId;
  }
}
