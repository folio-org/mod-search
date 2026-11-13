package org.folio.search.service.converter.preprocessor;

import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.PREFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.SUFFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_ID_FIELD;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.indexrebuild.callnumber.CallNumberKey;
import org.springframework.stereotype.Component;

/**
 * Flattens raw inventory item events so that nested item properties match the flat fields of the item index.
 */
@Log4j2
@Component
public class ItemEventPreProcessor implements EventPreProcessor {

  private static final String STATUS = "status";

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

    var components = nested(source, EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD);
    putIfPresent(result, "callNumber", components.get(CALL_NUMBER_FIELD));
    putIfPresent(result, "callNumberPrefix", components.get(PREFIX_FIELD));
    putIfPresent(result, "callNumberSuffix", components.get(SUFFIX_FIELD));
    putIfPresent(result, "callNumberTypeId", components.get(TYPE_ID_FIELD));
    CallNumberKey.fromItem(source).ifPresent(key -> result.put(CALL_NUMBER_ID_FIELD, key.id()));

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
