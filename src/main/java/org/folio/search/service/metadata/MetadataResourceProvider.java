package org.folio.search.service.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;

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

  default Optional<ResourceDescription> getResourceDescription(String resourceName) {
    return getResourceDescriptions().stream()
      .filter(desc -> desc.getName().equals(resourceName))
      .findFirst();
  }

  /**
   * Provides map with field type as key and {@link SearchFieldType} objects as value.
   *
   * @return {@link Map} with common index field types.
   */
  Map<String, SearchFieldType> getSearchFieldTypes();
}
