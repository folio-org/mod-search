package org.folio.search.service.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResourceType;

/**
 * Provides metadata resources.
 */
public interface MetadataResourceProvider {

  /**
   * Provides list of resource descriptions.
   *
   * @return {@link List} with {@link ResourceDescription} objects.
   */
  List<ResourceDescription> getResourceDescriptions();

  /**
   * Finds resource description by given resource name.
   *
   * @param resourceType resource name as {@link String} value.
   * @return {@link Optional} of {@link ResourceDescription} if it has been found, it would be empty otherwise.
   */
  default Optional<ResourceDescription> getResourceDescription(ResourceType resourceType) {
    return getResourceDescriptions().stream()
      .filter(desc -> desc.getName().equals(resourceType))
      .findFirst();
  }

  /**
   * Provides map with field type as key and {@link SearchFieldType} objects as value.
   *
   * @return {@link Map} with common index field types.
   */
  Map<String, SearchFieldType> getSearchFieldTypes();
}
