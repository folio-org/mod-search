package org.folio.search.service.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.springframework.stereotype.Component;

/**
 * Spring component which responsible for holding fields and resource descriptions which are used
 * for mapping resource from event to elasticsearch document.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ResourceDescriptionService {

  private final LocalSearchFieldProvider localSearchFieldProvider;
  private final LocalResourceProvider localResourceProvider;

  private Map<String, ResourceDescription> resourceDescriptions;
  private Map<String, List<String>> languageSourcePaths;
  private Set<String> supportedLanguages;

  /**
   * Provides {@link ResourceDescription} object for given resource name.
   *
   * @param resourceName name of resource as {@link String}
   * @return {@link ResourceDescription} object
   */
  public ResourceDescription get(String resourceName) {
    var resourceDescription = resourceDescriptions.get(resourceName);
    if (resourceDescription == null) {
      throw new ResourceDescriptionException(String.format(
        "Resource description not found [resourceName: %s]", resourceName));
    }
    return resourceDescription;
  }

  /**
   * Returns all existing resource descriptions as list.
   *
   * @return {@link List} with {@link ResourceDescription} objects.
   */
  public List<ResourceDescription> getAll() {
    return new ArrayList<>(resourceDescriptions.values());
  }

  /**
   * Provides list of language source JSON paths for given resource name.
   *
   * @param resourceName name of resource as {@link String}
   * @return {@link List} with {@link String} JSON paths values.
   */
  public List<String> getLanguageSourcePaths(String resourceName) {
    return languageSourcePaths.getOrDefault(resourceName, emptyList());
  }

  /**
   * Checks if passed language is supported by mod-search application.
   *
   * @param language language value as {@link String}
   * @return true if language is supported, false - otherwise
   */
  public boolean isSupportedLanguage(String language) {
    return supportedLanguages.contains(language);
  }

  /**
   * Initializes bean after constructor call and loads required resources from local files.
   */
  @PostConstruct
  public void init() {
    var mapBuilder = new LinkedHashMap<String, ResourceDescription>();
    var resources = localResourceProvider.getResourceDescriptions();
    for (var description : resources) {
      mapBuilder.put(description.getName(), description);
    }
    this.resourceDescriptions = unmodifiableMap(mapBuilder);
    this.languageSourcePaths = unmodifiableMap(getLanguageSourcePathsAsMap());
    this.supportedLanguages = unmodifiableSet(getSupportedLanguages());
  }

  private Map<String, List<String>> getLanguageSourcePathsAsMap() {
    var map = new HashMap<String, List<String>>();
    for (var entry : resourceDescriptions.entrySet()) {
      var languageFieldPaths = entry.getValue().getFields().values().stream()
        .map(this::getLanguageSourcePath)
        .flatMap(Collection::stream)
        .collect(toUnmodifiableList());
      map.put(entry.getKey(), languageFieldPaths);
    }
    return map;
  }

  private List<String> getLanguageSourcePath(FieldDescription fieldDescription) {
    if (fieldDescription instanceof ObjectFieldDescription) {
      var objectFieldDesc = (ObjectFieldDescription) fieldDescription;
      return objectFieldDesc.getProperties().values().stream()
        .map(this::getLanguageSourcePath)
        .flatMap(Collection::stream)
        .collect(toList());
    }

    if (fieldDescription instanceof PlainFieldDescription) {
      var plainFieldDesc = (PlainFieldDescription) fieldDescription;
      if (plainFieldDesc.isLanguageSource()) {
        return singletonList(plainFieldDesc.getSourcePath());
      }
    }

    return emptyList();
  }

  private Set<String> getSupportedLanguages() {
    var indexFieldType = localSearchFieldProvider.getSearchFieldType(MULTILANG_FIELD_TYPE);
    var supportedLanguagesSet = new HashSet<String>();
    var mapping = indexFieldType.getMapping();
    mapping.path("properties").fieldNames().forEachRemaining(supportedLanguagesSet::add);
    return supportedLanguagesSet;
  }
}
