package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
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
import java.util.Arrays;
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
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResourceType;
import org.springframework.stereotype.Component;

/**
 * Provides search fields from local JSON files with fields/resource descriptions.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LocalSearchFieldProvider implements SearchFieldProvider {

  private static final String FIELD_TYPE_NOT_FOUND_ERROR =
    "Failed to find search field type [fieldType: %s]";
  private static final String RESOURCE_DESCRIPTION_ERROR =
    "Failed to create resource description for resource: '%s', errors: %s";
  private static final String MORE_THAN_ONE_FIELD_MESSAGE =
    "Invalid plain field descriptor for search alias '%s'. Alias for field with %s can't group more than 1 field.";
  private static final String FACET_VALIDATION_MESSAGE = "searchType='facet'";
  private static final String SEARCH_TERM_PROCESSOR_VALIDATION_MESSAGE = "searchTermProcessor";

  private final MetadataResourceProvider metadataResourceProvider;
  private final Map<String, SearchFieldModifier> searchFieldModifiers;

  private Set<String> supportedLanguages;
  private Map<ResourceType, String[]> defaultSourceFields;
  private Map<String, SearchFieldType> elasticsearchFieldTypes;
  private Map<ResourceType, Map<String, List<String>>> fieldsBySearchAlias;
  private Map<ResourceType, Map<String, String>> sourceFieldsByResourceType;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    log.debug("init:: Attempting to start loading defined e.s. fields from local");

    var resourceDescriptions = metadataResourceProvider.getResourceDescriptions();
    elasticsearchFieldTypes = unmodifiableMap(metadataResourceProvider.getSearchFieldTypes());
    defaultSourceFields = collectDefaultSourceFields(resourceDescriptions);
    sourceFieldsByResourceType = collectSourceFields(resourceDescriptions);
    supportedLanguages = getSupportedLanguages();
    fieldsBySearchAlias = buildFieldsBySearchAliasMap(resourceDescriptions);
  }

  @Override
  public SearchFieldType getSearchFieldType(String fieldType) {
    log.debug("getSearchFieldType:: by [fieldType: {}]", fieldType);

    var indexFieldType = elasticsearchFieldTypes.get(fieldType);
    if (indexFieldType == null) {
      throw new ResourceDescriptionException(String.format(FIELD_TYPE_NOT_FOUND_ERROR, fieldType));
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
  public String[] getSourceFields(ResourceType resource, List<String> requestedFields) {
    var defaultSourceFieldArray = defaultSourceFields.getOrDefault(resource, new String[0]);
    var requestedSourceFields = getValidSourceFields(resource, requestedFields);

    return Stream.concat(Arrays.stream(defaultSourceFieldArray), Arrays.stream(requestedSourceFields))
      .distinct()
      .toArray(String[]::new);
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
    return metadataResourceProvider.getResourceDescription(resource)
      .map(this::applyFieldModifications)
      .map(modifiers -> applyModifiers(field, modifiers))
      .orElse(field);
  }

  private String[] getValidSourceFields(ResourceType resource, List<String> requestedFields) {
    return sourceFieldsByResourceType.getOrDefault(resource, emptyMap()).entrySet().stream()
      .filter(entry -> requestedFields.contains(entry.getKey()))
      .map(Entry::getValue)
      .toArray(String[]::new);
  }

  private Map<ResourceType, Map<String, List<String>>> buildFieldsBySearchAliasMap(
    List<ResourceDescription> resourceDescriptions) {
    return resourceDescriptions.stream()
      .collect(toUnmodifiableMap(ResourceDescription::getName, this::collectFieldsBySearchAlias));
  }

  private Map<String, List<String>> collectFieldsBySearchAlias(ResourceDescription resourceDescription) {
    var searchFieldByAlias = new LinkedHashMap<String, List<String>>();
    var fields = resourceDescription.getFlattenFields();

    fields.entrySet().stream()
      .filter(entry -> hasSearchAliases(entry.getValue()))
      .forEach(entry -> processFieldAliases(entry, searchFieldByAlias));

    validateSearchAliases(searchFieldByAlias, resourceDescription);
    return unmodifiableMap(searchFieldByAlias);
  }

  private boolean hasSearchAliases(PlainFieldDescription fieldDescription) {
    return CollectionUtils.isNotEmpty(fieldDescription.getSearchAliases());
  }

  private void processFieldAliases(Entry<String, PlainFieldDescription> fieldEntry,
                                   Map<String, List<String>> searchFieldByAlias) {
    var fieldPath = fieldEntry.getKey();
    var fieldDescription = fieldEntry.getValue();
    var updatedPath = fieldDescription.isMultilang() ? getPathForMultilangField(fieldPath) : fieldPath;

    fieldDescription.getSearchAliases().forEach(alias -> {
      var searchFieldsByAlias = getFieldsForSearchAlias(alias, updatedPath);
      searchFieldByAlias.computeIfAbsent(alias, k -> new ArrayList<>()).addAll(searchFieldsByAlias);
    });
  }

  private static void validateSearchAliases(Map<String, List<String>> fields, ResourceDescription description) {
    var errors = new ArrayList<String>();
    var flattenFields = description.getFlattenFields();

    fields.forEach((alias, searchFields) -> {
      validateFacetFields(alias, searchFields, flattenFields, errors);
      validateSearchTermProcessorFields(alias, searchFields, flattenFields, errors);
    });

    if (CollectionUtils.isNotEmpty(errors)) {
      throw new ResourceDescriptionException(String.format(
        RESOURCE_DESCRIPTION_ERROR, description.getName().getName(), errors));
    }
  }

  private static void validateFacetFields(String alias, List<String> searchFields,
                                          Map<String, PlainFieldDescription> flattenFields,
                                          List<String> errors) {
    var facetFieldCount = countFieldsWithFacetType(flattenFields, searchFields);
    if (facetFieldCount > 0 && searchFields.size() > 1) {
      errors.add(String.format(MORE_THAN_ONE_FIELD_MESSAGE, alias, FACET_VALIDATION_MESSAGE));
    }
  }

  private static void validateSearchTermProcessorFields(String alias, List<String> searchFields,
                                                        Map<String, PlainFieldDescription> flattenFields,
                                                        List<String> errors) {
    var searchTermProcessorFieldCount = countFieldsWithSearchTermProcessor(flattenFields, searchFields);
    if (searchTermProcessorFieldCount > 0 && searchFields.size() > 1) {
      errors.add(String.format(MORE_THAN_ONE_FIELD_MESSAGE, alias, SEARCH_TERM_PROCESSOR_VALIDATION_MESSAGE));
    }
  }

  private static long countFieldsWithFacetType(Map<String, PlainFieldDescription> fields, List<String> fieldNames) {
    return fieldNames.stream()
      .map(LocalSearchFieldProvider::cleanUpFieldNameForValidation)
      .map(fields::get)
      .filter(desc -> anyMatch(desc.getSearchTypes(), FACET::equals))
      .count();
  }

  private static long countFieldsWithSearchTermProcessor(Map<String, PlainFieldDescription> fields,
                                                         List<String> fieldNames) {
    return fieldNames.stream()
      .map(LocalSearchFieldProvider::cleanUpFieldNameForValidation)
      .map(fields::get)
      .filter(desc -> Objects.nonNull(desc.getSearchTermProcessor()))
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

  private Map<ResourceType, Map<String, String>> collectSourceFields(
    List<ResourceDescription> descriptions) {
    var fieldsMap = new LinkedHashMap<ResourceType, Map<String, String>>();

    descriptions.forEach(description -> {
      var sourceFields = buildSourceFieldsMap(description);
      fieldsMap.put(description.getName(), unmodifiableMap(sourceFields));
    });

    return fieldsMap;
  }

  private Map<String, String> buildSourceFieldsMap(ResourceDescription description) {
    var allSourceFields = new LinkedHashMap<String, String>();

    description.getFlattenFields().forEach((path, fieldDesc) -> {
      if (!(fieldDesc instanceof SearchFieldDescriptor)) {
        var sourcePath = determineDefaultFieldPath(path, fieldDesc);
        allSourceFields.put(path, sourcePath);
      }
    });

    return allSourceFields;
  }

  private static String determineDefaultFieldPath(String path, PlainFieldDescription fieldDesc) {
    return fieldDesc.hasFulltextIndex() ? getPathToFulltextPlainValue(path) : path;
  }

  private static Map<ResourceType, String[]> collectDefaultSourceFields(List<ResourceDescription> descriptions) {
    var sourceFieldPerResource = new LinkedHashMap<ResourceType, String[]>();

    descriptions.forEach(description -> {
      var defaultFields = extractDefaultFields(description);
      sourceFieldPerResource.put(description.getName(), defaultFields);
    });

    return unmodifiableMap(sourceFieldPerResource);
  }

  private static String[] extractDefaultFields(ResourceDescription description) {
    return description.getFlattenFields().entrySet().stream()
      .filter(entry -> entry.getValue().isShowInResponse())
      .map(entry -> determineDefaultFieldPath(entry.getKey(), entry.getValue()))
      .toArray(String[]::new);
  }

  private List<SearchFieldModifier> applyFieldModifications(ResourceDescription description) {
    return description.getSearchFieldModifiers().stream()
      .map(searchFieldModifiers::get)
      .filter(Objects::nonNull)
      .toList();
  }

  private String applyModifiers(String field, List<SearchFieldModifier> modifiers) {
    var modifiedField = field;
    for (var modifier : modifiers) {
      modifiedField = modifier.modify(modifiedField);
    }
    return modifiedField;
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
