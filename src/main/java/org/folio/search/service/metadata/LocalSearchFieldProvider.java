package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.types.SearchType.FACET;
import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.SearchUtils.ASTERISKS_SIGN;
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
  private Map<String, Map<String, List<String>>> fieldsBySearchAlias;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    var resourceDescriptions = metadataResourceProvider.getResourceDescriptions();
    elasticsearchFieldTypes = unmodifiableMap(metadataResourceProvider.getSearchFieldTypes());
    sourceFields = collectSourceFields(resourceDescriptions);
    supportedLanguages = getSupportedLanguages();
    fieldsBySearchAlias = resourceDescriptions.stream().collect(toUnmodifiableMap(
      ResourceDescription::getName, LocalSearchFieldProvider::collectFieldsBySearchAlias));
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
  public List<String> getFields(String resource, String searchAlias) {
    return fieldsBySearchAlias.getOrDefault(resource, emptyMap()).getOrDefault(searchAlias, emptyList());
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

  private static Map<String, List<String>> collectFieldsBySearchAlias(ResourceDescription resourceDescription) {
    var searchFieldByAlias = new LinkedHashMap<String, List<String>>();
    var fields = resourceDescription.getFlattenFields();

    for (var entry : fields.entrySet()) {
      var fieldDescription = entry.getValue();
      if (CollectionUtils.isEmpty(fieldDescription.getSearchAliases())) {
        continue;
      }

      var fieldPath = entry.getKey();
      var updatedPath = fieldDescription.isMultilang() ? getPathForMultilangField(fieldPath) : fieldPath;
      for (String alias : fieldDescription.getSearchAliases()) {
        var searchFieldsByAlias = getFieldsForSearchAlias(alias, updatedPath);
        searchFieldByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).addAll(searchFieldsByAlias);
      }
    }

    validateSearchAliases(searchFieldByAlias, resourceDescription);

    return unmodifiableMap(searchFieldByAlias);
  }

  private static void validateSearchAliases(LinkedHashMap<String, List<String>> fields, ResourceDescription desc) {
    var errors = new ArrayList<String>();
    var flattenFields = desc.getFlattenFields();
    fields.forEach((alias, searchFields) -> {
      var facetFieldDescriptionCount = getFacetFieldDescription(flattenFields, searchFields);
      if (facetFieldDescriptionCount != 0 && searchFields.size() > 1) {
        errors.add(String.format("Invalid plain field descriptor for search alias '%s'. "
          + "Alias for field with searchType='facet' can't group more than 1 field.", alias));
      }
    });

    if (CollectionUtils.isNotEmpty(errors)) {
      throw new ResourceDescriptionException(String.format(
        "Failed to create resource description for resource: '%s', reason:\n %s",
        desc.getName(), String.join("\n", errors)));
    }
  }

  private static long getFacetFieldDescription(Map<String, PlainFieldDescription> fields, List<String> fieldNames) {
    return fieldNames.stream()
      .map(LocalSearchFieldProvider::cleanUpFieldNameForValidation)
      .map(fields::get)
      .filter(desc -> anyMatch(desc.getSearchTypes(), FACET::equals))
      .count();
  }

  private static String cleanUpFieldNameForValidation(String field) {
    if (field.startsWith(PLAIN_FULLTEXT_PREFIX)) {
      return field.substring(PLAIN_FULLTEXT_PREFIX.length());
    }
    return field.endsWith(ASTERISKS_SIGN) ? field.substring(0, field.length() - 2) : field;
  }

  private static List<String> getFieldsForSearchAlias(String searchAlias, String path) {
    return searchAlias.startsWith(CQL_META_FIELD_PREFIX)
      ? List.of(path, PLAIN_FULLTEXT_PREFIX + path.substring(0, path.length() - 2))
      : singletonList(path);
  }

  private static Map<String, List<String>> collectSourceFields(List<ResourceDescription> descriptions) {
    var sourceFieldPerResource = new LinkedHashMap<String, List<String>>();
    for (var desc : descriptions) {
      var sourcePaths = desc.getFlattenFields().entrySet().stream()
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
