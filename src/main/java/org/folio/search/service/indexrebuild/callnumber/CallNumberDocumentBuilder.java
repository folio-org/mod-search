package org.folio.search.service.indexrebuild.callnumber;

import static org.folio.search.utils.CallNumberUtils.calculateFullCallNumber;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_BROWSING_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_PREFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_SUFFIX_FIELD;
import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.EFFECTIVE_LOCATION_ID_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.SUB_RESOURCE_INSTANCES_FIELD;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.MapUtils;

/**
 * Builds the content of a call number document from all item documents having that call number.
 */
@UtilityClass
public class CallNumberDocumentBuilder {

  private static final String LOCATION_ID_FIELD = "locationId";

  /**
   * Builds the document.
   *
   * @param items item documents with the same call number, not empty
   * @return document fields, or empty if the items have no valid call number
   */
  public static Optional<Map<String, Object>> build(List<Map<String, Object>> items) {
    var first = items.getFirst();
    return CallNumberKey.of(MapUtils.getString(first, CALL_NUMBER_FIELD),
        MapUtils.getString(first, CALL_NUMBER_PREFIX_FIELD), MapUtils.getString(first, CALL_NUMBER_SUFFIX_FIELD),
        MapUtils.getString(first, CALL_NUMBER_TYPE_ID_FIELD))
      .map(key -> toDocument(key, items));
  }

  private static Map<String, Object> toDocument(CallNumberKey key, List<Map<String, Object>> items) {
    var document = new HashMap<String, Object>();
    document.put(ID_FIELD, key.id());
    document.put(CALL_NUMBER_BROWSING_FIELD, calculateFullCallNumber(key.callNumber(), key.suffix()));
    document.put(CALL_NUMBER_FIELD, key.callNumber());
    document.put(CALL_NUMBER_PREFIX_FIELD, key.prefix());
    document.put(CALL_NUMBER_SUFFIX_FIELD, key.suffix());
    document.put(CALL_NUMBER_TYPE_ID_FIELD, key.typeId());
    document.put(SUB_RESOURCE_INSTANCES_FIELD, toInstances(items));
    return document;
  }

  private static List<Map<String, Object>> toInstances(List<Map<String, Object>> items) {
    return items.stream()
      .filter(item -> item.get(INSTANCE_ID_FIELD) != null)
      .collect(Collectors.groupingBy(CallNumberDocumentBuilder::groupOf, LinkedHashMap::new,
        Collectors.mapping(item -> String.valueOf(item.get(INSTANCE_ID_FIELD)), Collectors.toCollection(TreeSet::new))))
      .entrySet().stream()
      .map(entry -> {
        var instance = new HashMap<>(entry.getKey());
        instance.put(INSTANCE_ID_FIELD, List.copyOf(entry.getValue()));
        return (Map<String, Object>) instance;
      })
      .toList();
  }

  private static Map<String, Object> groupOf(Map<String, Object> item) {
    var group = new HashMap<String, Object>();
    group.put(TENANT_ID_FIELD_NAME, item.get(TENANT_ID_FIELD_NAME));
    group.put(SHARED_FIELD_NAME, Boolean.TRUE.equals(item.get(SHARED_FIELD_NAME)));
    group.put(LOCATION_ID_FIELD, item.get(EFFECTIVE_LOCATION_ID_FIELD));
    return group;
  }
}
