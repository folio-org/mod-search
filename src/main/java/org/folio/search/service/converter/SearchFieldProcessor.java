package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.folio.search.utils.SearchUtils.getMultilangValue;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.MapUtils;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchFieldProcessor {

  @SuppressWarnings("rawtypes")
  private final Map<String, FieldProcessor> fieldProcessors;
  private final JsonConverter jsonConverter;

  public Map<String, Object> getSearchFields(ConversionContext ctx) {
    var resourceDescription = ctx.getResourceDescription();
    var searchFields = resourceDescription.getSearchFields();
    if (MapUtils.isEmpty(searchFields)) {
      return emptyMap();
    }
    var data = ctx.getResourceData();
    var resourceClass = resourceDescription.getEventBodyJavaClass();
    var resourceObject = resourceClass != null ? jsonConverter.convert(data, resourceClass) : data;

    var resultMap = new LinkedHashMap<String, Object>();
    searchFields.forEach((name, desc) -> resultMap.putAll(getSearchFieldValue(resourceObject, ctx, name, desc)));
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getSearchFieldValue(Object resourceObject, ConversionContext ctx,
    String name, SearchFieldDescriptor descriptor) {
    var fieldProcessor = fieldProcessors.get(descriptor.getProcessor());

    try {
      var value = fieldProcessor.getFieldValue(resourceObject);
      if (value != null) {
        return descriptor.isMultilang() ? getMultilangValue(name, value, ctx) : singletonMap(name, value);
      }
    } catch (Exception e) {
      log.warn("Failed to retrieve field value", e);
    }

    return emptyMap();
  }
}
