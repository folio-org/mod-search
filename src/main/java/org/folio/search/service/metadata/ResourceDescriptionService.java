package org.folio.search.service.metadata;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.springframework.core.ResolvableType.forClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

  private final Map<String, FieldProcessor<?, ?>> availableProcessors;
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
    mapping.path("properties").fieldNames().forEachRemaining(field -> {
      if (!field.equals(MULTILANG_SOURCE_SUBFIELD)) {
        supportedLanguagesSet.add(field);
      }
    });
    return supportedLanguagesSet;
  }

  private void validateResourceDescriptions(List<ResourceDescription> descriptors) {
    var validationErrors = new LinkedHashMap<String, List<String>>();
    descriptors.forEach(descriptor -> validationErrors
      .computeIfAbsent(descriptor.getName(), v -> new ArrayList<>())
      .addAll(checkIfProcessorExistForSearchFields(descriptor)));

    var errorString = validationErrors.entrySet().stream()
      .filter(entry -> CollectionUtils.isNotEmpty(entry.getValue()))
      .map(entry -> format("%s: ('%s')", entry.getKey(), String.join("', '", entry.getValue())))
      .collect(Collectors.joining("\n", "\n", ""));
    if (StringUtils.isNotBlank(errorString)) {
      throw new ResourceDescriptionException("Found error(s) in resource description(s):" + errorString);
    }
  }

  private List<String> checkIfProcessorExistForSearchFields(ResourceDescription resourceDescription) {
    var validationErrors = new ArrayList<String>();
    var eventBodyClass = resourceDescription.getEventBodyJavaClass();
    resourceDescription.getSearchFields().forEach((name, fieldDesc) ->
      validationErrors.addAll(checkThatFieldProcessorIsApplicable(name, eventBodyClass, fieldDesc.getProcessor())));
    return validationErrors;
  }

  private List<String> checkThatFieldProcessorIsApplicable(String field, Class<?> eventBodyClass, String processor) {
    var errorInfo = format(" [field: '%s', processorName: '%s']", field, processor);
    FieldProcessor<?, ?> fieldProcessor = availableProcessors.get(processor);
    if (fieldProcessor == null) {
      return singletonList("Field processor not found" + errorInfo);
    }
    Class<?> resolvedClass = forClass(FieldProcessor.class, fieldProcessor.getClass()).resolveGeneric(0);
    Class<?> requiredClass = eventBodyClass == null ? Map.class : eventBodyClass;
    if (resolvedClass == null) {
      return singletonList("Generic class for field processor not found" + errorInfo);
    }
    if (!requiredClass.isAssignableFrom(resolvedClass)) {
      return singletonList(format(
        "Invalid generic type in field processor, must be instance of '%s', resolved value was '%s'%s",
        requiredClass.getName(), resolvedClass.getName(), errorInfo));
    }
    return emptyList();
  }
}
