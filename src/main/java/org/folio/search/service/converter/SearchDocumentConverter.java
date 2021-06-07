package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.getMultilangValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final JsonConverter jsonConverter;
  private final SearchFieldsProcessor searchFieldsProcessor;
  private final LanguageConfigService languageConfigService;
  private final ResourceDescriptionService descriptionService;

  /**
   * Converts list of {@link ResourceEventBody} object to the list of {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with resource events for conversion to elasticsearch document
   * @return list with elasticsearch documents.
   */
  public List<SearchDocumentBody> convert(List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .filter(this::canConvertEvent)
      .map(this::buildConvertContext)
      .map(this::convert)
      .collect(toList());
  }

  private SearchDocumentBody convert(ConversionContext context) {
    var resourceData = context.getResourceData();
    var resourceDescriptionFields = context.getResourceDescription().getFields();
    var baseFields = convertMapUsingResourceFields(resourceData, resourceDescriptionFields, context);
    var searchFields = searchFieldsProcessor.getSearchFields(context);
    var resultDocument = mergeSafely(baseFields, searchFields);

    return SearchDocumentBody.builder()
      .id(context.getId())
      .index(getElasticsearchIndexName(context.getResourceDescription().getName(), context.getTenant()))
      .routing(context.getTenant())
      .rawJson(jsonConverter.toJson(resultDocument))
      .action(INDEX)
      .build();
  }

  private List<String> getResourceLanguages(List<String> languageSource, Map<String, Object> resourceData) {
    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    return languageSource.stream()
      .map(sourcePath -> getMapValueByPath(sourcePath, resourceData))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(supportedLanguages::contains)
      .collect(toList());
  }

  private boolean canConvertEvent(ResourceEventBody resourceEventBody) {
    return resourceEventBody.getNew() instanceof Map;
  }

  @SuppressWarnings("unchecked")
  private ConversionContext buildConvertContext(ResourceEventBody event) {
    var resourceDescription = descriptionService.get(event.getResourceName());
    var resourceData = (Map<String, Object>) event.getNew();
    var resourceLanguages = getResourceLanguages(resourceDescription.getLanguageSourcePaths(), resourceData);
    return ConversionContext.of(event.getTenant(), resourceData, resourceDescription, resourceLanguages);
  }

  private static Map<String, Object> convertMapUsingResourceFields(
    Map<String, Object> data, Map<String, FieldDescription> fields, ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();
    fields.entrySet().forEach(entry -> resultMap.putAll(getFieldValue(data, entry, ctx)));
    return nullIfEmpty(resultMap);
  }

  private static Map<String, Object> getFieldValue(
    Map<String, Object> data, Entry<String, FieldDescription> descEntry, ConversionContext ctx) {
    var fieldDescription = descEntry.getValue();
    if (fieldDescription instanceof PlainFieldDescription) {
      return getPlainFieldValue(data, descEntry, ctx);
    }

    var objectFieldDescription = (ObjectFieldDescription) fieldDescription;
    var fieldName = descEntry.getKey();
    var objectMapValue = data.get(fieldName);
    var value = getObjectFieldValue(objectMapValue, objectFieldDescription.getProperties(), ctx);
    return value != null ? Map.of(fieldName, value) : emptyMap();
  }

  private static Map<String, Object> getPlainFieldValue(Map<String, Object> fieldData,
    Entry<String, FieldDescription> fieldEntry, ConversionContext ctx) {
    var fieldName = fieldEntry.getKey();
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (desc.isNotIndexed()) {
      return emptyMap();
    }
    var value = MapUtils.getObject(fieldData, fieldName, desc.getDefaultValue());
    if (value == null) {
      return emptyMap();
    }
    return desc.isMultilang() ? getMultilangValue(fieldName, value, ctx.getLanguages()) : singletonMap(fieldName, value);
  }

  @SuppressWarnings("unchecked")
  private static Object getObjectFieldValue(
    Object value, Map<String, FieldDescription> subfields, ConversionContext ctx) {
    if (value instanceof Map) {
      return convertMapUsingResourceFields((Map<String, Object>) value, subfields, ctx);
    }

    if (value instanceof List) {
      return ((List<Object>) value).stream()
        .map(listValue -> getObjectFieldValue(listValue, subfields, ctx))
        .filter(Objects::nonNull)
        .collect(toList());
    }

    return null;
  }
}
