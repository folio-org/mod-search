package org.folio.search.service.converter;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.NONE_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.indexing.IndexingConfig;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionScopeExecutionContextManager;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SearchDocumentConverter {

  private final JsonConverter jsonConverter;
  private final ResourceDescriptionService descriptionService;
  private final LanguageConfigService languageConfigService;
  private final FolioModuleMetadata moduleMetadata;

  public List<SearchDocumentBody> convert(List<ResourceEventBody> resources) {
    return resources.stream()
      .collect(groupingBy(ResourceEventBody::getTenant))
      .entrySet().stream()
      .flatMap(entry -> convertForTenant(entry.getKey(), entry.getValue()))
      .collect(toList());
  }

  /**
   * Converts list of {@link ResourceEventBody} object to the list of {@link SearchDocumentBody} objects.
   *
   * @param resourceEvents list with resource events for conversion to elasticsearch document
   * @return list with elasticsearch documents.
   */
  List<SearchDocumentBody> convert(IndexingConfig indexingConfig, List<ResourceEventBody> resourceEvents) {
    return resourceEvents.stream()
      .map(event -> convert(indexingConfig, event))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(toList());
  }

  /**
   * Converts single {@link ResourceEventBody} object to the {@link SearchDocumentBody} object with all required data
   * for elasticsearch index operation.
   *
   * @param event resource event body for conversion
   * @return elasticsearch document
   */
  @SuppressWarnings("unchecked")
  private Optional<SearchDocumentBody> convert(IndexingConfig config, ResourceEventBody event) {
    var newData = event.getNew();
    if (!(newData instanceof Map)) {
      return Optional.empty();
    }
    var resourceData = (Map<String, Object>) newData;
    var resourceDescription = descriptionService.get(event.getResourceName());
    var fields = resourceDescription.getFields();

    var conversionContext = ConversionContext.of(
      getResourceLanguages(config, resourceData, resourceDescription.getLanguageSourcePaths()));
    Map<String, Object> resultDocument = convertMapUsingResourceFields(resourceData, fields, conversionContext);

    return Optional.of(SearchDocumentBody.builder()
      .id(MapUtils.getString(resourceData, "id"))
      .index(getElasticsearchIndexName(event.getResourceName(), event.getTenant()))
      .routing(event.getTenant())
      .rawJson(jsonConverter.toJson(resultDocument))
      .build());
  }

  private List<String> getResourceLanguages(IndexingConfig indexingConfig, Map<String, Object> resourceData,
    List<String> languageSourcePaths) {

    return languageSourcePaths.stream()
      .map(sourcePath -> SearchConverterUtils.getMapValueByPath(sourcePath, resourceData))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(indexingConfig::isSupportedLanguage)
      .collect(toList());
  }

  private Stream<SearchDocumentBody> convertForTenant(String tenant, List<ResourceEventBody> events) {
    try {
      beginFolioExecutionContext(tenant);
      final var indexingConfig = new IndexingConfig(languageConfigService.getAllSupportedLanguageCodes());
      return convert(indexingConfig, events).stream();
    } finally {
      endFolioExecutionContext();
    }
  }

  void beginFolioExecutionContext(String tenant) {
    final var folioExecutionContext =
      new DefaultFolioExecutionContext(moduleMetadata, Map.of(TENANT, List.of(tenant)));

    FolioExecutionScopeExecutionContextManager
      .beginFolioExecutionContext(folioExecutionContext);
  }

  void endFolioExecutionContext() {
    FolioExecutionScopeExecutionContextManager.endFolioExecutionContext();
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
    return MapUtils.isNotEmpty(resultMap) ? resultMap : null;
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
    var desc = (PlainFieldDescription) fieldEntry.getValue();
    if (isNotIndexedField(desc)) {
      return null;
    }
    Object plainFieldValue = fieldData.get(fieldEntry.getKey());
    return isMultilangField(desc) ? getMultilangValue(plainFieldValue, ctx) : plainFieldValue;
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
  private static class ConversionContext {

    /**
     * List of supported language for resource.
     */
    private final List<String> languages;
  }
}
