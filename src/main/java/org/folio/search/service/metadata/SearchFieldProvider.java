package org.folio.search.service.metadata;

import java.util.List;
import java.util.Optional;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.SearchFieldType;

/**
 * Provides access to the index field type, which are used as reference in resource model and it's description in
 * datastore.
 */
public interface SearchFieldProvider {

  /**
   * Provides index field types with mappings and other settings for given field type name.
   *
   * @param fieldType field type name as {@link String}.
   * @return optional of {@link SearchFieldType} object for given field type.
   */
  SearchFieldType getSearchFieldType(String fieldType);

  /**
   * Provides list of fields for given search type.
   *
   * @param resource resource type as {@link String}
   * @param searchType search type as {@link String}
   * @return list of fields.
   */
  List<String> getFields(String resource, String searchType);

  /**
   * Provides list of fields for given path.
   *
   * @param resource resource type as {@link String}
   * @param path path to field as {@link String}
   * @return {@link Optional} of resource field description by path.
   */
  Optional<FieldDescription> getFieldByPath(String resource, String path);

  /**
   * Provides list of fields of source fields for resource.
   *
   * @param resource resource type as {@link String}
   * @return list of fields.
   */
  List<String> getSourceFields(String resource);
}
