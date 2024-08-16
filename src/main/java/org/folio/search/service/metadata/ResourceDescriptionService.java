package org.folio.search.service.metadata;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.springframework.core.ResolvableType.forClass;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.types.ResourceType;
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

  private final LocalResourceProvider localResourceProvider;
  private final Map<String, FieldProcessor<?, ?>> availableProcessors;
  private Map<ResourceType, ResourceDescription> resourceDescriptions;

  /**
   * Initializes bean after constructor call and loads required resources from local files.
   */
  @PostConstruct
  public void init() {
    log.debug("init::  Attempting to start loading required resources from local");

    var mapBuilder = new LinkedHashMap<ResourceType, ResourceDescription>();
    var loadedResourceDescriptions = localResourceProvider.getResourceDescriptions();

    validateResourceDescriptions(loadedResourceDescriptions);

    loadedResourceDescriptions.forEach(desc -> mapBuilder.put(desc.getName(), desc));
    this.resourceDescriptions = unmodifiableMap(mapBuilder);
  }

  /**
   * Provides {@link ResourceDescription} object for given resource name.
   *
   * @param resourceType name of resource as {@link String}
   * @return {@link ResourceDescription} object
   * @throws ResourceDescriptionException if resource description is not found for the given name.
   */
  public ResourceDescription get(ResourceType resourceType) {
    log.debug("get:: by [resourceType: {}]", resourceType);

    var resourceDescription = resourceDescriptions.get(resourceType);
    if (resourceDescription == null) {
      throw new ResourceDescriptionException(format(
        "Resource description not found [resourceType: %s]", resourceType.getName()));
    }
    return resourceDescription;
  }

  /**
   * Provides {@link ResourceDescription} object as {@link Optional} for given resource name.
   *
   * @param resourceType name of resource as {@link ResourceType}
   */
  public Optional<ResourceDescription> find(ResourceType resourceType) {
    return Optional.ofNullable(resourceDescriptions.get(resourceType));
  }

  /**
   * Returns all resource descriptions.
   *
   * @return {@link Collection} with all resource descriptions.
   */
  public Collection<ResourceDescription> findAll() {
    return resourceDescriptions.values();
  }

  /**
   * Returns all supported resource types.
   *
   * @return {@link Collection} with all resource names as {@link String} values.
   */
  public Collection<ResourceType> getResourceTypes() {
    return resourceDescriptions.keySet();
  }

  /**
   * Returns name of secondary resources that linked to the given resource name.
   *
   * @param resource - resource name to check as {@link String}.
   * @return {@link Collection} with secondary resource names.
   */
  public Collection<ResourceType> getSecondaryResourceTypes(ResourceType resource) {
    log.debug("getSecondaryResourceNames:: by [resource: {}]", resource);

    return resourceDescriptions.values().stream()
      .filter(desc -> resource == desc.getParent())
      .map(ResourceDescription::getName)
      .toList();
  }

  private void validateResourceDescriptions(List<ResourceDescription> descriptors) {
    log.debug("validateResourceDescriptions:: by [descriptors:: {}]", collectionToLogMsg(descriptors, true));

    var validationErrors = new LinkedHashMap<ResourceType, List<String>>();
    descriptors.forEach(descriptor -> validationErrors
      .computeIfAbsent(descriptor.getName(), v -> new ArrayList<>())
      .addAll(checkIfProcessorExistForSearchFields(descriptor)));

    var errorString = validationErrors.entrySet().stream()
      .filter(entry -> CollectionUtils.isNotEmpty(entry.getValue()))
      .map(entry -> format("%s: ('%s')", entry.getKey().getName(), String.join("', '", entry.getValue())))
      .collect(Collectors.joining("\n", "\n", ""));
    if (StringUtils.isNotBlank(errorString)) {
      throw new ResourceDescriptionException("Found error(s) in resource description(s):" + errorString);
    }
  }

  private List<String> checkIfProcessorExistForSearchFields(ResourceDescription resourceDescription) {
    var validationErrors = new ArrayList<String>();
    var eventBodyClass = resourceDescription.getEventBodyJavaClass();
    resourceDescription.getSearchFields().forEach((name, fieldDesc) ->
      validationErrors.addAll(checkThatFieldProcessorIsApplicable(name, eventBodyClass, fieldDesc)));
    return validationErrors;
  }

  private List<String> checkThatFieldProcessorIsApplicable(
    String field, Class<?> eventBodyClass, SearchFieldDescriptor descriptor) {
    var processor = descriptor.getProcessor();
    var errorInfo = format(" [field: '%s', processorName: '%s']", field, processor);
    FieldProcessor<?, ?> fieldProcessor = availableProcessors.get(processor);
    if (fieldProcessor == null) {
      return singletonList("Field processor not found" + errorInfo);
    }

    Class<?> resolvedClass = forClass(FieldProcessor.class, fieldProcessor.getClass()).resolveGeneric(0);
    if (resolvedClass == null) {
      return singletonList("Generic class for field processor not found" + errorInfo);
    }

    Class<?> requiredClass = eventBodyClass == null ? Map.class : eventBodyClass;
    if (!(descriptor.isRawProcessing() || requiredClass.isAssignableFrom(resolvedClass))) {
      return singletonList(format(
        "Invalid generic type in field processor, must be instance of '%s', resolved value was '%s'%s",
        requiredClass.getName(), resolvedClass.getName(), errorInfo));
    }

    return emptyList();
  }
}
