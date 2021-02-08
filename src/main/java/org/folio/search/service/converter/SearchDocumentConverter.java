package org.folio.search.service.converter;

import static java.util.stream.Collectors.toList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.NONE_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.setter.FieldSetter;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final JsonConverter jsonConverter;
  private final ResourceDescriptionService descriptionService;
  private final Map<String, FieldSetter<?>> fieldSetters;

  /**
   * Converts list of {@link ResourceEventBody} object to the list of {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with resource events for conversion to elasticsearch document
   * @return list with elasticsearch documents.
   */
  public List<SearchDocumentBody> convert(ConvertConfig config, List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .filter(this::canConvertEvent)
      .map(event -> buildConvertContext(config, event))
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

    Map<String, Object> resultDocument = convertMapUsingResourceFields(resourceData,
      context.getResourceDescription().getFields(), context);

    return SearchDocumentBody.builder()
      .id(context.getId())
      .index(getElasticsearchIndexName(context.getResourceName(), context.getTenant()))
      .routing(context.getTenant())
      .rawJson(jsonConverter.toJson(resultDocument))
      .build();
  }

  private List<String> getResourceLanguages(ConversionContext context) {
    return context.getLanguageSourcePaths().stream()
      .map(sourcePath -> SearchConverterUtils.getMapValueByPath(sourcePath, context.getResourceData()))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(context::isLanguageSupported)
      .collect(toList());
  }

  private boolean canConvertEvent(ResourceEventBody resourceEventBody) {
    return resourceEventBody.getNew() instanceof Map;
  }

  private ConversionContext buildConvertContext(ConvertConfig config, ResourceEventBody event) {
    final var context = ConversionContext.of(event, descriptionService
      .get(event.getResourceName()), config);

    return context.withLanguages(getResourceLanguages(context));
  }

  private Map<String, Object> convertMapUsingResourceFields(
    Map<String, Object> data, Map<String, FieldDescription> fields, ConversionContext ctx) {
    var resultMap = new LinkedHashMap<String, Object>();
    for (var fieldEntry : fields.entrySet()) {
      var fieldValue = getFieldValue(data, fieldEntry, ctx);
      if (fieldValue != null) {
        resultMap.put(fieldEntry.getKey(), fieldValue);
      }
    }
    return MapUtils.isNotEmpty(resultMap) ? resultMap : null;
  }

  private Object getFieldValue(
    Map<String, Object> data, Entry<String, FieldDescription> descEntry, ConversionContext ctx) {
    var fieldDescription = descEntry.getValue();
    if (fieldDescription instanceof PlainFieldDescription) {
      return getPlainFieldValue(data, descEntry, ctx);
    }

    var objectFieldDescription = (ObjectFieldDescription) fieldDescription;
    var objectMapValue = data.get(descEntry.getKey());
    return getObjectFieldValue(objectMapValue, objectFieldDescription.getProperties(), ctx);
  }

  private Object getPlainFieldValue(Map<String, Object> fieldData,
    Entry<String, FieldDescription> fieldEntry, ConversionContext ctx) {
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (isNotIndexedField(desc)) {
      return null;
    }
    Object plainFieldValue = getPlainFieldValue(fieldData, fieldEntry.getKey(), desc);
    return isMultilangField(desc) ? getMultilangValue(plainFieldValue, ctx) : plainFieldValue;
  }

  private Object getPlainFieldValue(Map<String, Object> fieldData, String fieldName,
    PlainFieldDescription desc) {

    if (!desc.hasPopulatedBy()) {
      return fieldData.get(fieldName);
    }

    final var propertySetter = fieldSetters.get(desc.getPopulatedBy());
    if (propertySetter == null) {
      throw new IllegalArgumentException("There is no such property setter: " + desc.getPopulatedBy());
    }

    return propertySetter.getFieldValue(fieldData);
  }

  @SuppressWarnings("unchecked")
  private Object getObjectFieldValue(
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

  private static boolean isMultilangField(PlainFieldDescription desc) {
    return MULTILANG_FIELD_TYPE.equals(desc.getIndex());
  }

  private static boolean isNotIndexedField(PlainFieldDescription desc) {
    return NONE_FIELD_TYPE.equals(desc.getIndex());
  }

  /**
   * The conversion context object.
   */
  @Getter
  @RequiredArgsConstructor(staticName = "of")
  @AllArgsConstructor
  private static class ConversionContext {
    private final ResourceEventBody event;
    private final ResourceDescription resourceDescription;
    private final ConvertConfig convertConfig;
    /**
     * List of supported language for resource.
     */
    @With
    private List<String> languages;

    @SuppressWarnings("unchecked")
    private Map<String, Object> getResourceData() {
      return (Map<String, Object>) event.getNew();
    }

    private List<String> getLanguageSourcePaths() {
      return resourceDescription.getLanguageSourcePaths();
    }

    private boolean isLanguageSupported(String code) {
      return convertConfig.isSupportedLanguageCode(getTenant(), code);
    }

    private String getTenant() {
      return event.getTenant();
    }

    public String getResourceName() {
      return event.getResourceName();
    }

    private String getId() {
      return MapUtils.getString(getResourceData(), "id");
    }
  }
}
