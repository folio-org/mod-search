package org.folio.search.service.converter.preprocessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.springframework.stereotype.Component;

/**
 * Flattens raw inventory item events so that nested item properties match the flat fields of the item index.
 */
@Log4j2
@Component
public class ItemEventPreProcessor implements EventPreProcessor {

  private static final String STATUS = "status";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS = "effectiveCallNumberComponents";

  @Override
  public List<ResourceEvent> preProcess(ResourceEvent event) {
    log.debug("preProcess:: by [id: {}, tenant: {}, type: {}]", event.getId(), event.getTenant(), event.getType());

    if (!(event.getNew() instanceof Map<?, ?> source)) {
      return List.of(event);
    }

    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> result.put(String.valueOf(key), value));

    var status = nested(source, STATUS);
    putIfPresent(result, "statusName", status.get("name"));

    var components = nested(source, EFFECTIVE_CALL_NUMBER_COMPONENTS);
    putIfPresent(result, "callNumber", components.get("callNumber"));
    putIfPresent(result, "callNumberPrefix", components.get("prefix"));
    putIfPresent(result, "callNumberSuffix", components.get("suffix"));
    putIfPresent(result, "callNumberTypeId", components.get("typeId"));

    var processed = new ResourceEvent()
      .id(event.getId())
      .type(event.getType())
      .deleteEventSubType(event.getDeleteEventSubType())
      .tenant(event.getTenant())
      .resourceName(event.getResourceName())
      ._new(result)
      .old(event.getOld());
    return List.of(processed);
  }

  private static Map<?, ?> nested(Map<?, ?> source, String key) {
    return source.get(key) instanceof Map<?, ?> map ? map : Map.of();
  }

  private static void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
