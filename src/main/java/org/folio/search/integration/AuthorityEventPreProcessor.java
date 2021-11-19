package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.StreamSupport.stream;
import static org.folio.search.utils.SearchConverterUtils.copyEntityFields;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.AUTHORITY_STREAMING_FILTER_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorityEventPreProcessor {

  private final ResourceDescriptionService resourceDescriptionService;
  private Map<String, List<String>> fieldTypes;
  private List<String> commonFields;

  /**
   * Initializes {@link AuthorityEventPreProcessor} spring bean.
   */
  @PostConstruct
  public void init() {
    var fields = resourceDescriptionService.get(AUTHORITY_RESOURCE);
    var fieldPerDistinctiveType = new LinkedHashMap<String, List<String>>();
    var commonFieldsList = new ArrayList<String>();
    for (var entry : fields.getFields().entrySet()) {
      if (entry.getValue() instanceof AuthorityFieldDescription) {
        var fieldName = entry.getKey();
        var fieldDesc = (AuthorityFieldDescription) entry.getValue();
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
  public List<ResourceEvent> process(ResourceEvent event) {
    var resultList = new ArrayList<ResourceEvent>();
    for (var entry : fieldTypes.entrySet()) {
      for (var field : entry.getValue()) {
        var counter = new AtomicInteger();
        resultList.addAll(createResourceEvents(event, entry.getKey(), field, counter));
      }
    }

    var other = getNewResourceId("other", event.getId(), 0);
    var result = resultList.isEmpty() ? singletonList(event.id(other)) : resultList;
    markFirstValueAsSourceForIdsStreaming(result);
    return result;
  }

  private List<ResourceEvent> createResourceEvents(ResourceEvent event, String type, String field,
    AtomicInteger counter) {
    var fieldValue = getEventPayload(event).get(field);
    if (fieldValue instanceof String) {
      return singletonList(createResourceEvent(type, event, Map.of(field, fieldValue), counter.getAndIncrement()));
    }

    if (fieldValue instanceof Iterable<?>) {
      return stream(((Iterable<?>) fieldValue).spliterator(), false)
        .map(value -> createResourceEvent(type, event, Map.of(field, List.of(value)), counter.getAndIncrement()))
        .collect(Collectors.toList());
    }

    return emptyList();
  }

  private ResourceEvent createResourceEvent(String type, ResourceEvent event, Map<String, Object> fields, int counter) {
    var eventPayload = getEventPayload(event);
    var newEventBody = new LinkedHashMap<>(fields);
    copyEntityFields(eventPayload, newEventBody, commonFields);

    return new ResourceEvent()
      .id(getNewResourceId(type, event.getId(), counter))
      .resourceName(event.getResourceName())
      .tenant(event.getTenant())
      ._new(newEventBody)
      .type(event.getType());
  }

  private static void markFirstValueAsSourceForIdsStreaming(List<ResourceEvent> resultList) {
    getEventPayload(resultList.get(0)).put(AUTHORITY_STREAMING_FILTER_FIELD, true);
  }

  private static String getNewResourceId(String type, String eventId, int counterValue) {
    return type + counterValue + "_" + eventId;
  }
}
