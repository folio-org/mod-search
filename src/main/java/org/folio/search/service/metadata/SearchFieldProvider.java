package org.folio.search.service.metadata;

import java.util.List;
import java.util.Optional;
import org.folio.search.model.metadata.PlainFieldDescription;
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
   * Provides plain field description for given path.
   *
   * @param resource resource type as {@link String}
   * @param path path to field as {@link String}
   * @return {@link Optional} of resource field description by path, it would be empty if plain field by path not found
   */
  Optional<PlainFieldDescription> getPlainFieldByPath(String resource, String path);

  /**
   * Provides list of fields of source fields for resource.
   *
   * @param resource resource type as {@link String}
   * @return list of fields.
   */
  List<String> getSourceFields(String resource);

  /**
   * Checks if given language is supported.
   *
   * @return true if language is supported by system, false - otherwise.
   */
  boolean isSupportedLanguage(String languageCode);

  /**
   * Checks if field by path is multi-language or not.
   *
   * @param resourceName resource name as {@link String} object
   * @param path path to the field as {@link String} object
   * @return true if field by path is multi-language, false - otherwise
   */
  boolean isMultilangField(String resourceName, String path);

  /**
   * Apply resource field modifiers for field.
   *
   * @param field that should be modified {@link String} object
   * @param resource resource name as {@link String} object
   * @return modified field
   */
  String getModifiedField(String field, String resource);
}
