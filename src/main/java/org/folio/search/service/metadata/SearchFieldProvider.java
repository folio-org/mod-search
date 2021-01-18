package org.folio.search.service.metadata;

import java.util.List;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.InventorySearchType;

/**
 * Provides access to the index field type, which are used as reference in resource model and it's description in
 * datastore.
 */
public interface SearchFieldProvider {

  /**
   * Provides index field types with mappings and other settings for given field type name.
   *
   * @param fieldType field type name as {@link String} object.
   * @return optional of {@link SearchFieldType} object for given field type.
   */
  SearchFieldType getSearchFieldType(String fieldType);

  /**
   * Provides list of fields for given search type.
   *
   * @param inventorySearchType {@link InventorySearchType} object
   * @param resource resource type as {@link String} object
   * @return list of fields.
   */
  List<String> getFields(String resource, InventorySearchType inventorySearchType);
}
