package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
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

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.cql.SearchFieldModifier;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.Pair;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.ResponseGroupType;
import org.springframework.stereotype.Component;

/**
 * Provides search fields from local JSON files with fields/resource descriptions.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LocalSearchFieldProvider implements SearchFieldProvider {

  private static final String MORE_THEN_ONE_FIELD_MESSAGE =
    "Invalid plain field descriptor for search alias '%s'. Alias for field with %s can't group more than 1 field.";

  private final MetadataResourceProvider metadataResourceProvider;
  private final Map<String, SearchFieldModifier> searchFieldModifiers;

  private Set<String> supportedLanguages;
  private Map<ResourceType, Map<ResponseGroupType, String[]>> sourceFields;
  private Map<String, SearchFieldType> elasticsearchFieldTypes;
  private Map<ResourceType, Map<String, List<String>>> fieldsBySearchAlias;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    log.debug("init::  Attempting to start loading defined e.s. fields from local");

    var resourceDescriptions = metadataResourceProvider.getResourceDescriptions();
    elasticsearchFieldTypes = unmodifiableMap(metadataResourceProvider.getSearchFieldTypes());
    sourceFields = collectSourceFields(resourceDescriptions);
    supportedLanguages = getSupportedLanguages();
    fieldsBySearchAlias = resourceDescriptions.stream()
      .collect(toUnmodifiableMap(ResourceDescription::getName, LocalSearchFieldProvider::collectFieldsBySearchAlias));
  }

  @Override
  public SearchFieldType getSearchFieldType(String fieldType) {
    log.debug("getSearchFieldType:: by [fieldType: {}]", fieldType);

    var indexFieldType = elasticsearchFieldTypes.get(fieldType);
    if (indexFieldType == null) {
      throw new ResourceDescriptionException(String.format(
        "Failed to find search field type [fieldType: %s]", fieldType));
    }
    return indexFieldType;
  }

  @Override
  public List<String> getFields(ResourceType resource, String searchAlias) {
    return fieldsBySearchAlias.getOrDefault(resource, emptyMap()).getOrDefault(searchAlias, emptyList());
  }

  @Override
  public Optional<PlainFieldDescription> getPlainFieldByPath(ResourceType resource, String path) {
    return metadataResourceProvider.getResourceDescription(resource)
      .map(ResourceDescription::getFlattenFields)
      .map(flattenFields -> flattenFields.get(path));
  }

  @Override
  public String[] getSourceFields(ResourceType resource, ResponseGroupType groupType) {
    return sourceFields.getOrDefault(resource, emptyMap()).get(groupType);
  }

  @Override
  public boolean isSupportedLanguage(String languageCode) {
    return supportedLanguages.contains(languageCode);
  }

  @Override
  public boolean isMultilangField(ResourceType resourceType, String path) {
    return this.getPlainFieldByPath(resourceType, path).filter(PlainFieldDescription::isMultilang).isPresent();
  }

  @Override
  public boolean isFullTextField(ResourceType resourceName, String path) {
    return this.getPlainFieldByPath(resourceName, path).filter(PlainFieldDescription::hasFulltextIndex).isPresent();
  }

  @Override
  public String getModifiedField(String field, ResourceType resource) {
    var queryWrapper = new Object() {
      String value = field;
    };

    metadataResourceProvider.getResourceDescription(resource)
      .ifPresent(description -> description.getSearchFieldModifiers().stream()
        .map(searchFieldModifiers::get)
        .filter(Objects::nonNull)
        .forEach(modifier ->
          queryWrapper.value = modifier.modify(queryWrapper.value))
      );

    return queryWrapper.value;
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
      var facetFieldDescriptionCount = getFacetFieldCount(flattenFields, searchFields);
      if (facetFieldDescriptionCount != 0 && searchFields.size() > 1) {
        errors.add(String.format(MORE_THEN_ONE_FIELD_MESSAGE, alias, "searchType='facet'"));
      }
      var searchTermProcessorFieldCount = getSearchTermProcessorFieldCount(flattenFields, searchFields);
      if (searchTermProcessorFieldCount != 0 && searchFields.size() > 1) {
        errors.add(String.format(MORE_THEN_ONE_FIELD_MESSAGE, alias, "searchTermProcessor"));
      }
    });

    if (CollectionUtils.isNotEmpty(errors)) {
      throw new ResourceDescriptionException(String.format(
        "Failed to create resource description for resource: '%s', errors: %s", desc.getName().getName(), errors));
    }
  }

  private static long getFacetFieldCount(Map<String, PlainFieldDescription> fields, List<String> fieldNames) {
    return fieldNames.stream()
      .map(LocalSearchFieldProvider::cleanUpFieldNameForValidation)
      .map(fields::get)
      .filter(desc -> anyMatch(desc.getSearchTypes(), FACET::equals))
      .count();
  }

  private static long getSearchTermProcessorFieldCount(Map<String, PlainFieldDescription> fields,
                                                       List<String> fieldNames) {
    return fieldNames.stream()
      .map(LocalSearchFieldProvider::cleanUpFieldNameForValidation)
      .map(fields::get)
      .filter(desc -> !Objects.isNull(desc.getSearchTermProcessor()))
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

  private static Map<ResourceType, Map<ResponseGroupType, String[]>> collectSourceFields(
    List<ResourceDescription> descriptions) {
    var sourceFieldPerResource = new LinkedHashMap<ResourceType, Map<ResponseGroupType, String[]>>();

    for (var desc : descriptions) {
      var sourcePaths = desc.getFlattenFields().entrySet().stream()
        .flatMap(LocalSearchFieldProvider::getResponseGroupFieldNamePairs)
        .collect(groupingBy(Pair::getFirst, mapping(
          Pair::getSecond, collectingAndThen(toList(), list -> list.toArray(String[]::new)))));
      sourceFieldPerResource.put(desc.getName(), sourcePaths);
    }

    return unmodifiableMap(sourceFieldPerResource);
  }

  private static Stream<Pair<ResponseGroupType, String>> getResponseGroupFieldNamePairs(
    Entry<String, PlainFieldDescription> entry) {
    var name = entry.getKey();
    var fieldDesc = entry.getValue();
    var sourceFieldName = fieldDesc.hasFulltextIndex() ? getPathToFulltextPlainValue(name) : name;
    return fieldDesc.getShowInResponse().stream().map(groupType -> Pair.of(groupType, sourceFieldName));
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
