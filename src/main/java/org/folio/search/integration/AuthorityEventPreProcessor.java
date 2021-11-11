package org.folio.search.integration;

import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.getEventPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.metadata.DistinctiveFieldDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorityEventPreProcessor {

  private final ResourceDescriptionService resourceDescriptionService;
  private Map<String, List<String>> fieldTypes;
  private List<String> commonFields;

  @PostConstruct
  public void init() {
    var fields = resourceDescriptionService.get(AUTHORITY_RESOURCE);
    var fieldPerDistinctiveType = new LinkedHashMap<String, List<String>>();
    var commonFieldsList = new ArrayList<String>();
    for (var entry : fields.getFields().entrySet()) {
      if (entry.getValue() instanceof DistinctiveFieldDescription) {
        var desc = (DistinctiveFieldDescription) entry.getValue();
        fieldPerDistinctiveType.computeIfAbsent(desc.getDistinctType(), v -> new ArrayList<>()).add(entry.getKey());
        continue;
      }
      commonFieldsList.add(entry.getKey());
    }

    this.fieldTypes = Collections.unmodifiableMap(fieldPerDistinctiveType);
    this.commonFields = Collections.unmodifiableList(commonFieldsList);
  }

  public List<ResourceEventBody> process(ResourceEventBody eventBody) {
    var event = eventBody.id(UUID.randomUUID().toString()).resourceName(AUTHORITY_RESOURCE);
    var resultEvents = new ArrayList<ResourceEventBody>();
    var eventPayload = getEventPayload(event);
    fieldTypes.forEach((key, fields) -> {
      if (anyMatch(fields, k -> !getValuesByPath(eventPayload, k).isEmpty())) {
        resultEvents.add(createResourceEvent(event, fields));
      }
    });
    return resultEvents.isEmpty() ? Collections.singletonList(eventBody) : resultEvents;
  }

  private ResourceEventBody createResourceEvent(ResourceEventBody event, List<String> fields) {
    var eventPayload = getEventPayload(event);
    var newEventBody = new LinkedHashMap<String, Object>();
    collectEntityFields(eventPayload, commonFields, newEventBody::put);
    collectEntityFields(eventPayload, fields, newEventBody::put);

    return new ResourceEventBody()
      .id(UUID.randomUUID().toString())
      .resourceName(AUTHORITY_RESOURCE)
      .tenant(event.getTenant())
      ._new(newEventBody)
      .type(event.getType());
  }

  private static void collectEntityFields(
    Map<String, Object> event, List<String> fields, BiConsumer<String, Object> keyValueConsumer) {
    fields.forEach(field -> {
      if (event.containsKey(field)) {
        keyValueConsumer.accept(field, event.get(field));
      }
    });
  }
}
