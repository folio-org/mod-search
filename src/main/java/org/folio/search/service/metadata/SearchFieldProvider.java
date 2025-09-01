package org.folio.search.service.metadata;

import java.util.List;
import java.util.Optional;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.ResponseGroupType;

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
   * @param resource   resource type as {@link ResourceType}
   * @param searchType search type as {@link String}
   * @return list of fields.
   */
  List<String> getFields(ResourceType resource, String searchType);

  /**
   * Provides plain field description for given path.
   *
   * @param resource resource type as {@link ResourceType}
   * @param path     path to field as {@link String}
   * @return {@link Optional} of resource field description by path, it would be empty if plain field by path not found
   */
  Optional<PlainFieldDescription> getPlainFieldByPath(ResourceType resource, String path);

  /**
   * Provides list of fields of source fields for resource and response group type.
   *
   * @param resource  resource type as {@link ResourceType}
   * @param groupType - response group type as {@link ResponseGroupType} object
   * @return array of fields.
   */
  String[] getSourceFields(ResourceType resource, ResponseGroupType groupType);

  String[] getSourceFields(ResourceType resource, ResponseGroupType groupType, List<String> requestedFields);

  /**
   * Checks if given language is supported.
   *
   * @return true if language is supported by system, false - otherwise.
   */
  boolean isSupportedLanguage(String languageCode);

  /**
   * Checks if field by path is multi-language or not.
   *
   * @param resourceType resource type as {@link ResourceType} object
   * @param path         path to the field as {@link String} object
   * @return true if field by path is multi-language, false - otherwise
   */
  boolean isMultilangField(ResourceType resourceType, String path);

  /**
   * Checks if field by path is full-text or not.
   *
   * @param resourceName resource type as {@link ResourceType} object
   * @param path         path to the field as {@link String} object
   * @return true if field by path is full-text, false - otherwise
   */
  boolean isFullTextField(ResourceType resourceName, String path);

  /**
   * Apply resource field modifiers for field.
   *
   * @param field    that should be modified {@link String} object
   * @param resource resource type as {@link ResourceType} object
   * @return modified field
   */
  String getModifiedField(String field, ResourceType resource);
}
