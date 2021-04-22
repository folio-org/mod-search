package org.folio.search.service.converter;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.folio.search.utils.SearchUtils.PLAIN_MULTILANG_PREFIX;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final JsonConverter jsonConverter;
  private final LanguageConfigService languageConfigService;
  private final ResourceDescriptionService descriptionService;
  private final Map<String, FieldProcessor<?>> fieldProcessors;

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

  /**
   * Converts single {@link ResourceEventBody} object to the {@link SearchDocumentBody} object with all required data
   * for elasticsearch index operation.
   *
   * @param context - conversion context.
   * @return elasticsearch document
   */
  private SearchDocumentBody convert(ConversionContext context) {
    final var resourceData = context.getResourceData();

    Map<String, Object> baseFields = convertMapUsingResourceFields(resourceData,
      context.getResourceDescription().getFields(), context);
    Map<String, Object> searchFields = generateSearchFields(context);

    Map<String, Object> resultDocument = mergeSafely(baseFields, searchFields);

    return populateBaseFields(context.getId(), context.getResourceName(), context.getTenant())
      .rawJson(jsonConverter.toJson(resultDocument))
      .build();
  }

  public List<SearchDocumentBody> convertDeleteEvents(List<ResourceIdEvent> resourceEvents) {
    return resourceEvents.stream()
      .map(event -> populateBaseFields(event.getId(), event.getType(), event.getTenant()).build())
      .collect(toList());
  }

  private List<String> getResourceLanguages(List<String> languageSource, Map<String, Object> resourceData) {
    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    return languageSource.stream()
      .map(sourcePath -> SearchConverterUtils.getMapValueByPath(sourcePath, resourceData))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(supportedLanguages::contains)
      .collect(toList());
  }

  private boolean canConvertEvent(ResourceEventBody resourceEventBody) {
    return resourceEventBody.getNew() instanceof Map;
  }

  private ConversionContext buildConvertContext(ResourceEventBody event) {
    var resourceDescription = descriptionService.get(event.getResourceName());
    var resourceDataAsMap = getResourceDataAsMap(event);
    var resourceLanguages = getResourceLanguages(resourceDescription.getLanguageSourcePaths(), resourceDataAsMap);

    return new ConversionContext(event.getTenant(), event.getResourceName(), resourceDataAsMap,
      resourceDescription, resourceLanguages);
  }

  private Map<String, Object> generateSearchFields(ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();

    ctx.getResourceDescription().getSearchFields().forEach((name, desc) -> {
      var fieldProcessor = fieldProcessors.get(desc.getProcessor());
      var value = fieldProcessor.getFieldValue(ctx.getResourceData());
      if (value != null) {
        resultMap.putAll(desc.isMultilang() ? getMultilangValue(name, value, ctx) : Map.of(name, value));
      }
    });

    return nullIfEmpty(resultMap);
  }

  private Map<String, Object> getResourceDataAsMap(ResourceEventBody event) {
    return jsonConverter.convert(event.getNew(), new TypeReference<>() {});
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
    var plainFieldValue = MapUtils.getObject(fieldData, fieldName, desc.getDefaultValue());
    if (plainFieldValue == null) {
      return emptyMap();
    }
    return desc.isMultilang()
      ? getMultilangValue(fieldName, plainFieldValue, ctx)
      : singletonMap(fieldName, plainFieldValue);
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

  private static Map<String, Object> getMultilangValue(String key, Object plainFieldValue, ConversionContext ctx) {
    var multilangValueMap = new LinkedHashMap<String, Object>();
    var languages = ctx.getLanguages();
    languages.forEach(language -> multilangValueMap.put(language, plainFieldValue));
    if (languages.isEmpty()) {
      multilangValueMap.put(MULTILANG_SOURCE_SUBFIELD, plainFieldValue);
    }

    var resultMap = new LinkedHashMap<String, Object>(2);
    resultMap.put(key, multilangValueMap);
    resultMap.put(PLAIN_MULTILANG_PREFIX + key, plainFieldValue);

    return resultMap;
  }

  private static SearchDocumentBody.SearchDocumentBodyBuilder populateBaseFields(
    String id, String resource, String tenant) {

    return SearchDocumentBody.builder()
      .id(id)
      .index(getElasticsearchIndexName(resource, tenant))
      .routing(tenant);
  }

  /**
   * The conversion context object.
   */
  @Getter
  @RequiredArgsConstructor
  private static class ConversionContext {

    private final String tenant;
    private final String resourceName;
    private final Map<String, Object> resourceData;
    private final ResourceDescription resourceDescription;
    /**
     * List of supported language for resource.
     */
    private final List<String> languages;

    private String getId() {
      return MapUtils.getString(resourceData, "id");
    }
  }
}
