package org.folio.search.service.converter;

import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.hasNoValue;
import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.CollectionUtils.nullIfEmpty;
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

    return SearchDocumentBody.builder()
      .id(context.getId())
      .index(getElasticsearchIndexName(context.getResourceName(), context.getTenant()))
      .routing(context.getTenant())
      .rawJson(jsonConverter.toJson(resultDocument))
      .build();
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
    var resourceLanguages = getResourceLanguages(resourceDescription
        .getLanguageSourcePaths(), resourceDataAsMap);

    return new ConversionContext(event.getTenant(), event.getResourceName(), resourceDataAsMap,
      resourceDescription, resourceLanguages);
  }

  private Map<String, Object> generateSearchFields(ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();

    ctx.getResourceDescription().getSearchFields().forEach(
      (fieldName, desc) -> {
        FieldProcessor<?> fieldProcessor = fieldProcessors.get(desc.getProcessor());

        Object fieldValue = fieldProcessor.getFieldValue(ctx.getResourceData());
        resultMap.put(fieldName, desc.isMultilang() ? getMultilangValue(fieldValue, ctx) : fieldValue);
      });

    return nullIfEmpty(resultMap);
  }

  private Map<String, Object> getResourceDataAsMap(ResourceEventBody event) {
    return jsonConverter.convert(event.getNew(), new TypeReference<>() {});
  }

  private static Map<String, Object> convertMapUsingResourceFields(
    Map<String, Object> data, Map<String, FieldDescription> fields, ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();
    for (var fieldEntry : fields.entrySet()) {
      var fieldValue = getFieldValue(data, fieldEntry, ctx);
      if (fieldValue != null) {
        resultMap.put(fieldEntry.getKey(), fieldValue);
      }
    }
    return nullIfEmpty(resultMap);
  }

  private static Object getFieldValue(
    Map<String, Object> data, Entry<String, FieldDescription> descEntry, ConversionContext ctx) {
    var fieldDescription = descEntry.getValue();
    if (fieldDescription instanceof PlainFieldDescription) {
      return getPlainFieldValue(data, descEntry, ctx);
    }

    var objectFieldDescription = (ObjectFieldDescription) fieldDescription;
    var objectMapValue = data.get(descEntry.getKey());
    return getObjectFieldValue(objectMapValue, objectFieldDescription.getProperties(), ctx);
  }

  private static Object getPlainFieldValue(Map<String, Object> fieldData,
    Entry<String, FieldDescription> fieldEntry, ConversionContext ctx) {
    var fieldName = fieldEntry.getKey();
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (!desc.isIndexed()) {
      return null;
    }
    Object plainFieldValue = hasNoValue(fieldData, fieldName)
      ? desc.getDefaultValue() : fieldData.get(fieldName);

    return desc.isMultilang() ? getMultilangValue(plainFieldValue, ctx) : plainFieldValue;
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

  private static Map<String, Object> getMultilangValue(Object plainFieldValue, ConversionContext ctx) {
    if (plainFieldValue != null) {
      var multilangValueMap = new LinkedHashMap<String, Object>();
      ctx.getLanguages().forEach(language -> multilangValueMap.put(language, plainFieldValue));
      multilangValueMap.put("src", plainFieldValue);
      return multilangValueMap;
    }

    return null;
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
