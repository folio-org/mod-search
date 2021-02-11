package org.folio.search.service.metadata;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

/**
 * Spring component which responsible for holding fields and resource descriptions which are used for mapping resource
 * from event to elasticsearch document.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ResourceDescriptionService {

  private final LocalSearchFieldProvider localSearchFieldProvider;
  private final LocalResourceProvider localResourceProvider;
  private final Map<String, FieldProcessor<?>> availableProcessors;

  private Map<String, ResourceDescription> resourceDescriptions;
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
      throw new ResourceDescriptionException(format(
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

    validateResourceDescriptions(resources);

    for (var description : resources) {
      mapBuilder.put(description.getName(), description);
    }
    this.resourceDescriptions = unmodifiableMap(mapBuilder);
    this.supportedLanguages = unmodifiableSet(getSupportedLanguages());
  }

  private Set<String> getSupportedLanguages() {
    var indexFieldType = localSearchFieldProvider.getSearchFieldType(MULTILANG_FIELD_TYPE);
    var supportedLanguagesSet = new HashSet<String>();
    var mapping = indexFieldType.getMapping();
    mapping.path("properties").fieldNames().forEachRemaining(supportedLanguagesSet::add);
    return supportedLanguagesSet;
  }

  private void validateResourceDescriptions(List<ResourceDescription> descriptors) {
    descriptors.forEach(this::checkIfProcessorExistForExtendedFields);
  }

  private void checkIfProcessorExistForExtendedFields(ResourceDescription resourceDescription) {
    resourceDescription.getSearchFields().forEach((fieldName, fieldDesc) -> {
      if (!availableProcessors.containsKey(fieldDesc.getProcessor())) {
        throw new ResourceDescriptionException(
          format("There is no such processor [%s] required for field [%s]",
            fieldDesc.getProcessor(), fieldName));
      }
    });
  }
}
