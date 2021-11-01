package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.CQL_META_FIELD_PREFIX;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.folio.search.utils.SearchUtils.PLAIN_FULLTEXT_PREFIX;
import static org.folio.search.utils.SearchUtils.getPathForMultilangField;
import static org.folio.search.utils.SearchUtils.getPathToFulltextPlainValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.springframework.stereotype.Component;

/**
 * Provides search fields from local JSON files with fields/resource descriptions.
 */
@Component
@RequiredArgsConstructor
public class LocalSearchFieldProvider implements SearchFieldProvider {

  private final MetadataResourceProvider metadataResourceProvider;

  private Set<String> supportedLanguages;
  private Map<String, List<String>> sourceFields;
  private Map<String, SearchFieldType> elasticsearchFieldTypes;
  private Map<String, Map<String, List<String>>> fieldBySearchType;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    var resourceDescriptions = metadataResourceProvider.getResourceDescriptions();
    elasticsearchFieldTypes = unmodifiableMap(metadataResourceProvider.getSearchFieldTypes());
    sourceFields = collectSourceFields(resourceDescriptions);
    supportedLanguages = getSupportedLanguages();
    fieldBySearchType = resourceDescriptions.stream().collect(toUnmodifiableMap(
      ResourceDescription::getName, desc -> collectFieldsBySearchType(desc.getFlattenFields())));
  }

  @Override
  public SearchFieldType getSearchFieldType(String fieldType) {
    var indexFieldType = elasticsearchFieldTypes.get(fieldType);
    if (indexFieldType == null) {
      throw new ResourceDescriptionException(String.format(
        "Failed to find search field type [fieldType: %s]", fieldType));
    }
    return indexFieldType;
  }

  @Override
  public List<String> getFields(String resource, String searchType) {
    return fieldBySearchType.getOrDefault(resource, emptyMap()).getOrDefault(searchType, emptyList());
  }

  @Override
  public List<String> getSourceFields(String resource) {
    return sourceFields.getOrDefault(resource, emptyList());
  }

  @Override
  public Optional<PlainFieldDescription> getPlainFieldByPath(String resource, String path) {
    return metadataResourceProvider.getResourceDescription(resource)
      .map(ResourceDescription::getFlattenFields)
      .map(flattenFields -> flattenFields.get(path));
  }

  @Override
  public boolean isSupportedLanguage(String languageCode) {
    return supportedLanguages.contains(languageCode);
  }

  @Override
  public boolean isMultilangField(String resourceName, String path) {
    return this.getPlainFieldByPath(resourceName, path).filter(PlainFieldDescription::isMultilang).isPresent();
  }

  private static Map<String, List<String>> collectFieldsBySearchType(Map<String, PlainFieldDescription> fields) {
    var resultMap = new LinkedHashMap<String, List<String>>();

    for (var entry : fields.entrySet()) {
      var fieldDescription = entry.getValue();
      if (CollectionUtils.isEmpty(fieldDescription.getInventorySearchTypes())) {
        continue;
      }

      var fieldPath = entry.getKey();
      var updatedPath = fieldDescription.isMultilang() ? getPathForMultilangField(fieldPath) : fieldPath;
      fieldDescription.getInventorySearchTypes().forEach(type ->
        resultMap.computeIfAbsent(type, k -> new ArrayList<>()).addAll(getFieldsForSearchType(type, updatedPath)));
    }

    return unmodifiableMap(resultMap);
  }

  private static List<String> getFieldsForSearchType(String searchType, String path) {
    return searchType.startsWith(CQL_META_FIELD_PREFIX)
      ? List.of(path, PLAIN_FULLTEXT_PREFIX + path.substring(0, path.length() - 2))
      : singletonList(path);
  }

  private static Map<String, List<String>> collectSourceFields(List<ResourceDescription> descriptions) {
    var sourceFieldPerResource = new LinkedHashMap<String, List<String>>();
    for (ResourceDescription desc : descriptions) {
      List<String> sourcePaths = desc.getFlattenFields().entrySet().stream()
        .filter(entry -> entry.getValue().isShowInResponse())
        .map(entry -> entry.getValue().isMultilang() ? getPathToFulltextPlainValue(entry.getKey()) : entry.getKey())
        .collect(toUnmodifiableList());

      sourceFieldPerResource.put(desc.getName(), sourcePaths);
    }

    return unmodifiableMap(sourceFieldPerResource);
  }

  private Set<String> getSupportedLanguages() {
    var indexFieldType = elasticsearchFieldTypes.get(MULTILANG_FIELD_TYPE);
    var supportedLanguagesSet = new LinkedHashSet<String>();
    var mapping = indexFieldType.getMapping();
    mapping.path("properties").fieldNames().forEachRemaining(field -> {
      if (!field.equals(MULTILANG_SOURCE_SUBFIELD)) {
        supportedLanguagesSet.add(field);
      }
    });
    return unmodifiableSet(supportedLanguagesSet);
  }
}
