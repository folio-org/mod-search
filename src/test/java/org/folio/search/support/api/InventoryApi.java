package org.folio.search.support.api;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections.MapUtils.getString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;
import static org.folio.search.utils.TestConstants.inventoryHoldingTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryItemTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.springframework.kafka.core.KafkaTemplate;

@Log4j2
@RequiredArgsConstructor
public class InventoryApi {

  private static final Map<String, Map<String, Map<String, Object>>> INSTANCE_STORE = new LinkedHashMap<>();
  private static final Map<String, Map<String, HoldingEvent>> HOLDING_STORE = new LinkedHashMap<>();
  private static final Map<String, Map<String, ItemEvent>> ITEM_STORE = new LinkedHashMap<>();
  private static final String ID_FIELD = "id";

  private final KafkaTemplate<String, ResourceEvent> kafkaTemplate;

  public void createInstance(String tenantId, Instance instance) {
    createInstance(tenantId, toMap(instance));
  }

  public void createInstance(String tenantId, Map<String, Object> instance) {
    var instanceId = getString(instance, "id");
    INSTANCE_STORE.computeIfAbsent(tenantId, k -> new LinkedHashMap<>()).put(instanceId, instance);

    var instanceEvent = kafkaResourceEvent(tenantId, CREATE, instance, null);
    kafkaTemplate.send(inventoryInstanceTopic(tenantId), instanceId, instanceEvent)
      .whenComplete((stringResourceEventSendResult, throwable) -> {
        if (throwable != null) {
          log.error("Failed sending instance resource event", throwable);
        } else {
          var topic = stringResourceEventSendResult.getRecordMetadata().topic();
          log.info("Succeeded sending instance resource event to topic: {}", topic);
        }
      });
    createNestedResources(instance, INSTANCE_HOLDING_FIELD_NAME, hr -> createHolding(tenantId, instanceId, hr));
    createNestedResources(instance, INSTANCE_ITEM_FIELD_NAME, item -> createItem(tenantId, instanceId, item));
  }

  public void updateInstance(String tenantId, Instance instance) {
    updateInstance(tenantId, toMap(instance));
  }

  public void updateInstance(String tenantId, Map<String, Object> instance) {
    var instanceId = getString(instance, "id");
    var previousInstance = INSTANCE_STORE.getOrDefault(tenantId, emptyMap()).get(instanceId);
    assertThat(previousInstance).isNotNull();
    INSTANCE_STORE.computeIfAbsent(tenantId, k -> new LinkedHashMap<>()).put(instanceId, instance);

    var instanceEvent = kafkaResourceEvent(tenantId, UPDATE, instance, previousInstance);
    kafkaTemplate.send(inventoryInstanceTopic(tenantId), instanceId, instanceEvent);
  }

  public void deleteInstance(String tenant, String id) {
    var instance = INSTANCE_STORE.get(tenant).remove(id);

    kafkaTemplate.send(inventoryInstanceTopic(tenant), getString(instance, ID_FIELD),
      resourceEvent(ResourceType.INSTANCE, null).old(instance).tenant(tenant).type(DELETE));
  }

  public void createHolding(String tenant, String instanceId, Map<String, Object> holding) {
    var event = new HoldingEvent(holding, instanceId);
    HOLDING_STORE.computeIfAbsent(tenant, k -> new LinkedHashMap<>()).put(getString(holding, ID_FIELD), event);
    kafkaTemplate.send(inventoryHoldingTopic(tenant), instanceId, kafkaResourceEvent(tenant, CREATE, event, null));
  }

  public void deleteHolding(String tenant, String id) {
    var holdingRecord = HOLDING_STORE.get(tenant).remove(id);
    var resourceEvent = kafkaResourceEvent(tenant, DELETE, null, holdingRecord);
    kafkaTemplate.send(inventoryHoldingTopic(tenant), holdingRecord.getInstanceId(), resourceEvent);
  }

  public void createItem(String tenant, String instanceId, Map<String, Object> item) {
    var event = new ItemEvent(item, instanceId);
    ITEM_STORE.computeIfAbsent(tenant, k -> new LinkedHashMap<>()).put(getString(item, ID_FIELD), event);
    kafkaTemplate.send(inventoryItemTopic(tenant), instanceId, kafkaResourceEvent(tenant, CREATE, event, null));
  }

  public void deleteItem(String tenant, String id) {
    var item = ITEM_STORE.get(tenant).remove(id);
    var resourceEvent = kafkaResourceEvent(tenant, DELETE, null, item);
    kafkaTemplate.send(inventoryItemTopic(tenant), item.getInstanceId(), resourceEvent);
  }

  public static Optional<Map<String, Object>> getInventoryView(String tenant, String id) {
    var instance = INSTANCE_STORE.getOrDefault(tenant, emptyMap()).get(id);

    var hrs = HOLDING_STORE.getOrDefault(tenant, emptyMap()).values().stream()
      .filter(hr -> hr.getInstanceId().equals(id))
      .map(HoldingEvent::getHolding)
      .toList();

    var items = ITEM_STORE.getOrDefault(tenant, emptyMap()).values().stream()
      .filter(item -> item.getInstanceId().equals(id))
      .map(ItemEvent::getItem)
      .toList();

    return Optional.ofNullable(instance)
      .map(inst -> putField(inst, INSTANCE_HOLDING_FIELD_NAME, hrs))
      .map(inst -> putField(inst, INSTANCE_ITEM_FIELD_NAME, items));
  }

  @SuppressWarnings("unchecked")
  private void createNestedResources(Map<String, Object> instance, String key, Consumer<Map<String, Object>> consumer) {
    var resourcesByKey = (List<Map<String, Object>>) MapUtils.getObject(instance, key);
    if (CollectionUtils.isNotEmpty(resourcesByKey)) {
      resourcesByKey.forEach(consumer);
    }
  }

  private static Map<String, Object> putField(Map<String, Object> instance, String key, Object subResources) {
    instance.put(key, subResources);
    return instance;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  private static final class HoldingEvent {

    @JsonUnwrapped
    private Map<String, Object> holding;
    private String instanceId;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  private static final class ItemEvent {

    @JsonUnwrapped
    private Map<String, Object> item;
    private String instanceId;
  }
}
