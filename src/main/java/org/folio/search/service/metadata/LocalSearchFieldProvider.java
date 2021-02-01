package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
    final List<String> fieldList = new ArrayList<>(fieldBySearchType
      .getOrDefault(resource, emptyMap())
      .getOrDefault(searchType, emptyList()));

    if (isMultilangField(resource, searchType)) {
      fieldList.add(updatePathForMultilang(searchType));
    }

    return fieldList.stream()
      .distinct()
      .collect(Collectors.toList());
  }

  private boolean isMultilangField(String resourceName, String path) {
    return localResourceProvider.getResourceDescription(resourceName)
      .map(ResourceDescription::getFlattenFields)
      .map(map -> map.get(path))
      .map(PlainFieldDescription::isMultilang)
      .orElse(false);
  }

  private static Map<String, Map<String, List<String>>> collectFieldsBySearchType(
    List<ResourceDescription> resourceDescriptions) {

    var resultMap = new LinkedHashMap<String, Map<String, List<String>>>();
    for (ResourceDescription desc : resourceDescriptions) {
      resultMap.put(desc.getName(), collectFieldsBySearchType(desc));
    }

    return unmodifiableMap(resultMap);
  }

  private static Map<String, List<String>> collectFieldsBySearchType(ResourceDescription description) {
    var fieldsBySearchType = new LinkedHashMap<String, List<String>>();

    description.getFlattenFields().forEach((fieldPath, currentFieldDesc) -> {
      final var updatedPath = currentFieldDesc.isMultilang()
        ? updatePathForMultilang(fieldPath) : fieldPath;

      currentFieldDesc.getInventorySearchTypes().stream()
        .map(type -> fieldsBySearchType.computeIfAbsent(type, k -> new ArrayList<>()))
        .forEach(list -> list.add(updatedPath));
    });

    return fieldsBySearchType;
  }

  private static String updatePathForMultilang(String path) {
    return path + ".*";
  }
}
