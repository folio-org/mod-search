package org.folio.search.integration;

import static java.util.Collections.singletonList;
import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.metadata.AuthorityPlainFieldDescription;
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
      if (entry.getValue() instanceof AuthorityPlainFieldDescription) {
        var desc = (AuthorityPlainFieldDescription) entry.getValue();
        fieldPerDistinctiveType.computeIfAbsent(desc.getDistinctType(), v -> new ArrayList<>()).add(entry.getKey());
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
   * @param resourceEvent - resource event to process as {@link ResourceEvent} object
   * @return list with divided authority event objects
   */
  public List<ResourceEvent> process(ResourceEvent resourceEvent) {
    var resultEvents = new ArrayList<ResourceEvent>();
    var eventPayload = getEventPayload(resourceEvent);
    int counter = 0;
    for (var entry : fieldTypes.entrySet()) {
      var fields = entry.getValue();
      if (anyMatch(fields, k -> !getValuesByPath(eventPayload, k).isEmpty())) {
        resultEvents.add(createResourceEvent(entry.getKey(), counter++, resourceEvent, fields));
      }
    }
    return resultEvents.isEmpty() ? singletonList(resourceEvent.id(0 + "_" + resourceEvent.getId())) : resultEvents;
  }

  private ResourceEvent createResourceEvent(String type, int counter, ResourceEvent event, List<String> fields) {
    var eventPayload = getEventPayload(event);
    var newEventBody = new LinkedHashMap<String, Object>();
    collectEntityFields(eventPayload, newEventBody, commonFields);
    collectEntityFields(eventPayload, newEventBody, fields);

    return new ResourceEvent()
      .id(getNewResourceId(type, counter, event.getId()))
      .resourceName(event.getResourceName())
      .tenant(event.getTenant())
      ._new(newEventBody)
      .type(event.getType());
  }

  private static String getNewResourceId(String type, int index, String eventId) {
    return index + "_" + eventId + "_" + type;
  }

  private static void collectEntityFields(Map<String, Object> source, Map<String, Object> target, List<String> fields) {
    for (var field : fields) {
      if (source.containsKey(field)) {
        target.put(field, source.get(field));
      }
    }
  }
}
