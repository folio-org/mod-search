package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.StreamSupport.stream;
import static org.folio.search.utils.CollectionUtils.toMap;
import static org.folio.search.utils.SearchConverterUtils.copyEntityFields;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class AuthorityEventPreProcessor implements EventPreProcessor {

  private final ResourceDescriptionService resourceDescriptionService;
  private final ConsortiumTenantProvider consortiumTenantProvider;
  private Map<String, List<String>> fieldTypes;
  private List<String> commonFields;

  /**
   * Initializes {@link AuthorityEventPreProcessor} spring bean.
   */
  @PostConstruct
  public void init() {
    log.debug("init:: PostConstruct stated");
    var fields = resourceDescriptionService.get(AUTHORITY_RESOURCE);
    var fieldPerDistinctiveType = new LinkedHashMap<String, List<String>>();
    var commonFieldsList = new ArrayList<String>();
    for (var entry : fields.getFields().entrySet()) {
      if (entry.getValue() instanceof AuthorityFieldDescription fieldDesc) {
        var fieldName = entry.getKey();
        fieldPerDistinctiveType.computeIfAbsent(fieldDesc.getDistinctType(), v -> new ArrayList<>()).add(fieldName);
        continue;
      }
      commonFieldsList.add(entry.getKey());
    }

    this.fieldTypes = Collections.unmodifiableMap(fieldPerDistinctiveType);
    this.commonFields = Collections.unmodifiableList(commonFieldsList);
  }

  /**
   * Divides authority record event into several events based on distinctive type of resource description fields.
   *
   * @param event - resource event to process as {@link ResourceEvent} object
   * @return list with divided authority event objects
   */
  @Override
  public List<ResourceEvent> preProcess(ResourceEvent event) {
    log.debug("process:: by [id: {}, tenant: {}, resourceType: {}]",
      event.getId(), event.getTenant(), event.getType());

    if (!isDeleteOperation(event.getType())) {
      var eventNewPart = getNewAsMap(event);
      eventNewPart.put("tenantId", event.getTenant());

      if (consortiumTenantProvider.isCentralTenant(event.getTenant())) {
        eventNewPart.put("shared", true);
      }
    }
    if (event.getType() == ResourceEventType.UPDATE) {
      return getResourceEventsToUpdate(event);
    }
    return getResourceEvents(event, event.getType());
  }

  private List<ResourceEvent> getResourceEvents(ResourceEvent event, ResourceEventType eventType) {
    log.debug("getResourceEvents:: by [id: {}, tenant: {}, type: {}]", event.getId(), event.getTenant(), eventType);

    var isCreateOperation = isCreateOperation(eventType);
    var events = generateResourceEvents(event, eventType, isCreateOperation ? getNewAsMap(event) : getOldAsMap(event));
    if (events.isEmpty()) {
      return singletonList(event.id("other" + 0 + "_" + event.getId()));
    }
    return events;
  }

  private List<ResourceEvent> getResourceEventsToUpdate(ResourceEvent event) {
    var eventsToDeleteMap = toMap(getResourceEvents(event, ResourceEventType.DELETE), ResourceEvent::getId, identity());
    var resultResourceEvents = new ArrayList<>(getResourceEvents(event, ResourceEventType.CREATE));
    resultResourceEvents.forEach(evt -> eventsToDeleteMap.remove(evt.getId()));
    resultResourceEvents.addAll(eventsToDeleteMap.values());
    return resultResourceEvents;
  }

  private List<ResourceEvent> generateResourceEvents(ResourceEvent event, ResourceEventType eventType,
                                                     Map<String, Object> eventPayload) {
    var result = new ArrayList<ResourceEvent>();
    for (var entry : fieldTypes.entrySet()) {
      for (var field : entry.getValue()) {
        var counter = new AtomicInteger();
        result.addAll(createResourceEvents(event, entry.getKey(), field, counter, eventType, eventPayload));
      }
    }
    return result;
  }

  private List<ResourceEvent> createResourceEvents(ResourceEvent event, String type, String name,
                                                   AtomicInteger counter, ResourceEventType eventType,
                                                   Map<String, Object> body) {
    var value = body.get(name);
    if (value instanceof String) {
      return singletonList(createResourceEvent(type, event, name, value, eventType, counter.getAndIncrement(), body));
    }

    if (value instanceof Iterable<?>) {
      return stream(((Iterable<?>) value).spliterator(), false)
        .map(v -> createResourceEvent(type, event, name, singletonList(v), eventType, counter.getAndIncrement(), body))
        .toList();
    }

    return emptyList();
  }

  private ResourceEvent createResourceEvent(String type, ResourceEvent sourceEvent,
                                            String fieldName, Object fieldValue, ResourceEventType eventType,
                                            int counter, Map<String, Object> eventPayload) {
    return new ResourceEvent()
      .id(type + counter + "_" + sourceEvent.getId())
      .resourceName(sourceEvent.getResourceName())
      .tenant(sourceEvent.getTenant())
      ._new(isCreateOperation(eventType) ? getNewEventBody(eventPayload, fieldName, fieldValue) : null)
      .type(eventType);
  }

  private LinkedHashMap<String, Object> getNewEventBody(Map<String, Object> eventPayload, String field, Object value) {
    var newEventBody = new LinkedHashMap<String, Object>();
    newEventBody.put(field, value);
    copyEntityFields(eventPayload, newEventBody, commonFields);
    return newEventBody;
  }

  private static boolean isCreateOperation(ResourceEventType typeEnum) {
    return typeEnum == ResourceEventType.CREATE || typeEnum == ResourceEventType.REINDEX;
  }

  private static boolean isDeleteOperation(ResourceEventType typeEnum) {
    return typeEnum == ResourceEventType.DELETE || typeEnum == ResourceEventType.DELETE_ALL;
  }
}
