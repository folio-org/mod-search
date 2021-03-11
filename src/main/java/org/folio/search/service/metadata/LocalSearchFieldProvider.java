package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.folio.search.utils.SearchUtils.updatePathForMultilangField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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

  private final MetadataResourceProvider metadataResourceProvider;

  private Map<String, SearchFieldType> elasticsearchFieldTypes;
  private Map<String, Map<String, List<String>>> fieldBySearchType;
  private Map<String, List<String>> sourceFields;

  /**
   * Loads local defined elasticsearch field type from json.
   */
  @PostConstruct
  public void init() {
    elasticsearchFieldTypes = unmodifiableMap(metadataResourceProvider.getSearchFieldTypes());
    var resourceDescriptions = metadataResourceProvider.getResourceDescriptions();
    fieldBySearchType = collectFieldsBySearchType(resourceDescriptions);
    sourceFields = collectSourceFields(resourceDescriptions);
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
      fieldList.add(updatePathForMultilangField(searchType));
    }

    return fieldList.stream()
      .distinct()
      .collect(Collectors.toList());
  }

  @Override
  public List<String> getSourceFields(String resource) {
    return sourceFields.getOrDefault(resource, emptyList());
  }

  @Override
  public Optional<FieldDescription> getFieldByPath(String resource, String path) {
    var optResourceDescription = metadataResourceProvider.getResourceDescription(resource);
    if (optResourceDescription.isEmpty() || StringUtils.isBlank(path)) {
      return Optional.empty();
    }
    var resourceDescription = optResourceDescription.get();
    return findFieldByPath(resourceDescription.getFields(), path)
      .or(() -> findFieldByPath(resourceDescription.getSearchFields(), path));
  }

  @Override
  public Optional<PlainFieldDescription> getPlainFieldByPath(String resource, String path) {
    return getFieldByPath(resource, path)
      .filter(PlainFieldDescription.class::isInstance)
      .map(PlainFieldDescription.class::cast);
  }

  private static Optional<FieldDescription> findFieldByPath(
    Map<String, ? extends FieldDescription> fields, String path) {
    var pathValues = path.split("\\.");
    FieldDescription currentField = fields.get(pathValues[0]);
    for (int i = 1; i < pathValues.length; i++) {
      if (currentField instanceof ObjectFieldDescription) {
        currentField = ((ObjectFieldDescription) currentField).getProperties().get(pathValues[i]);
      } else {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(currentField);
  }

  private boolean isMultilangField(String resourceName, String path) {
    return metadataResourceProvider.getResourceDescription(resourceName)
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
        ? updatePathForMultilangField(fieldPath) : fieldPath;

      currentFieldDesc.getInventorySearchTypes().stream()
        .map(type -> fieldsBySearchType.computeIfAbsent(type, k -> new ArrayList<>()))
        .forEach(list -> list.add(updatedPath));
    });

    return fieldsBySearchType;
  }

  private static Map<String, List<String>> collectSourceFields(List<ResourceDescription> descriptions) {
    var sourceFieldPerResource = new LinkedHashMap<String, List<String>>();
    for (ResourceDescription desc : descriptions) {
      final List<String> sourcePaths = desc.getFlattenFields().entrySet().stream()
        .filter(entry -> entry.getValue().isShowInResponse())
        .map(LocalSearchFieldProvider::getSourcePath)
        .collect(Collectors.toList());

      sourceFieldPerResource.put(desc.getName(), sourcePaths);
    }

    return unmodifiableMap(sourceFieldPerResource);
  }

  private static String getSourcePath(Map.Entry<String, PlainFieldDescription> entry) {
    final String path = entry.getKey();
    final PlainFieldDescription descriptor = entry.getValue();

    return descriptor.isMultilang() ? path + "." + MULTILANG_SOURCE_SUBFIELD : path;
  }
}
