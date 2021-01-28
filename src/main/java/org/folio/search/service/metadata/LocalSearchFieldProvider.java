package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
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

  private final LocalResourceProvider localResourceProvider;

  private Map<String, SearchFieldType> elasticsearchFieldTypes;
  private Map<String, Map<String, List<String>>> fieldBySearchType;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    elasticsearchFieldTypes = unmodifiableMap(localResourceProvider.getSearchFieldTypes());
    fieldBySearchType = collectFieldsBySearchType(localResourceProvider.getResourceDescriptions());
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
    return fieldBySearchType
      .getOrDefault(resource, emptyMap())
      .getOrDefault(searchType, emptyList());
  }

  private static Map<String, Map<String, List<String>>> collectFieldsBySearchType(
    List<ResourceDescription> resourceDescriptions) {
    var resultMap = new LinkedHashMap<String, Map<String, List<String>>>();
    for (ResourceDescription description : resourceDescriptions) {
      resultMap.put(description.getName(), collectFieldsBySearchType(description));
    }
    return unmodifiableMap(resultMap);
  }

  private static Map<String, List<String>> collectFieldsBySearchType(ResourceDescription description) {
    var fieldsBySearchType = new LinkedHashMap<String, List<String>>();
    description.getFields().forEach((name, desc) -> addFieldsToResultMap(name, desc, null, fieldsBySearchType));
    return unmodifiableMap(fieldsBySearchType);
  }

  private static Map<String, List<String>> getFieldPathsBySearchType(
    String fieldName, FieldDescription fieldDescription, String prefix) {
    if (fieldDescription instanceof PlainFieldDescription) {
      var plainFieldDescription = (PlainFieldDescription) fieldDescription;
      var searchTypes = plainFieldDescription.getInventorySearchTypes();
      if (CollectionUtils.isNotEmpty(searchTypes)) {
        var fieldsBySearchType = new LinkedHashMap<String, List<String>>();
        var fieldPath = MULTILANG_FIELD_TYPE.equals(plainFieldDescription.getIndex()) ? fieldName + ".*" : fieldName;
        searchTypes.forEach(type -> fieldsBySearchType.put(type, List.of(getFullPathToField(prefix, fieldPath))));
        return fieldsBySearchType;
      } else {
        return emptyMap();
      }
    }

    var fieldsBySearchType = new LinkedHashMap<String, List<String>>();
    ((ObjectFieldDescription) fieldDescription).getProperties().forEach((name, desc) ->
      addFieldsToResultMap(name, desc, fieldName, fieldsBySearchType));
    return fieldsBySearchType;
  }

  private static void addFieldsToResultMap(String fieldName, FieldDescription description, String prefix,
    Map<String, List<String>> map) {
    getFieldPathsBySearchType(fieldName, description, prefix).forEach((searchType, fieldNames) ->
      map.computeIfAbsent(searchType, v -> new ArrayList<>()).addAll(fieldNames));
  }

  private static String getFullPathToField(String prefix, String fieldName) {
    return StringUtils.isEmpty(prefix) ? fieldName : prefix + "." + fieldName;
  }
}
