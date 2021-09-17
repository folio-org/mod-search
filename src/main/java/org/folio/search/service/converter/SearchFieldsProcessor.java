package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.search.utils.CollectionUtils.noneMatch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchFieldsProcessor {

  private final JsonConverter jsonConverter;
  private final SearchConfigurationProperties searchConfiguration;
  private final Map<String, FieldProcessor<?, ?>> fieldProcessors;

  /**
   * Provides search fields as {@link Map} for given resource in the {@link ConversionContext} object.
   *
   * @param ctx resource conversion context as {@link ConversionContext} object
   * @return map with retrieved search fields
   */
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
    searchFields.forEach((name, fieldDescriptor) -> {
      var value = fieldDescriptor.isRawProcessing() ? data : resourceObject;
      if (isSearchProcessorEnabled(resourceDescription.getName(), name, fieldDescriptor)) {
        resultMap.putAll(getSearchFieldValue(value, ctx.getLanguages(), name, fieldDescriptor));
      } else {
        log.debug("Search processor has been ignored [processor: {}]", fieldDescriptor.getProcessor());
      }
    });
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getSearchFieldValue(
    Object resource, List<String> languages, String name, SearchFieldDescriptor descriptor) {

    var fieldProcessor = (FieldProcessor<Object, ?>) fieldProcessors.get(descriptor.getProcessor());
    try {
      var value = fieldProcessor.getFieldValue(resource);
      if (ObjectUtils.isNotEmpty(value)) {
        return SearchUtils.getPlainFieldValue(descriptor, name, value, languages);
      }
    } catch (Exception e) {
      log.warn("Failed to retrieve field value", e);
    }

    return emptyMap();
  }

  private boolean isSearchProcessorEnabled(String resourceName, String name, SearchFieldDescriptor desc) {
    var disabledSearchOptions = searchConfiguration.getDisabledSearchOptions();
    var resourceDisabledOptions = disabledSearchOptions.getOrDefault(resourceName, emptySet());
    var searchTypes = desc.getInventorySearchTypes();
    return isNotEmpty(searchTypes)
      ? noneMatch(searchTypes, resourceDisabledOptions::contains)
      : !resourceDisabledOptions.contains(name);
  }
}
