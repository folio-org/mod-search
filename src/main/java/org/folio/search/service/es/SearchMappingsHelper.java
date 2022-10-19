package org.folio.search.service.es;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static org.folio.search.model.metadata.PlainFieldDescription.FULLTEXT_FIELD_TYPES;
import static org.folio.search.utils.SearchUtils.MULTILANG_SOURCE_SUBFIELD;
import static org.folio.search.utils.SearchUtils.PLAIN_FULLTEXT_PREFIX;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SearchMappingsHelper {

  private static final String MAPPING_PROPERTIES_FIELD = "properties";

  private final JsonConverter jsonConverter;
  private final SearchFieldProvider searchFieldProvider;
  private final LanguageConfigService languageConfigService;
  private final ResourceDescriptionService resourceDescriptionService;

  /**
   * Provides elasticsearch mappings for given resource name.
   *
   * @param resource resource name as {@link String} object
   * @return elasticsearch mappings as {@link String} object with JSON object inside
   */
  public String getMappings(String resource) {
    var description = resourceDescriptionService.get(resource);

    var indexMappings = createIndexMappingsObject();
    var mappingProperties = new LinkedHashMap<String, Object>();
    indexMappings.put(MAPPING_PROPERTIES_FIELD, mappingProperties);

    var mappingsSource = description.getMappingsSource();
    if (MapUtils.isNotEmpty(mappingsSource)) {
      indexMappings.put("_source", mappingsSource);
    }

    mappingProperties.putAll(createMappingsForFields(description.getFields()));
    mappingProperties.putAll(createMappingsForFields(description.getSearchFields()));
    var customIndexMappings = description.getIndexMappings();
    if (customIndexMappings != null) {
      mappingProperties.putAll(customIndexMappings);
    }

    return jsonConverter.toJson(indexMappings);
  }

  private static Map<String, Object> createIndexMappingsObject() {
    var indexMappings = new LinkedHashMap<String, Object>();
    indexMappings.put("date_detection", false);
    indexMappings.put("numeric_detection", false);
    return indexMappings;
  }

  private Map<String, JsonNode> createMappingsForFields(Map<String, ? extends FieldDescription> fields) {
    if (MapUtils.isEmpty(fields)) {
      return emptyMap();
    }

    var mappings = new LinkedHashMap<String, JsonNode>();
    fields.forEach((name, fieldDescription) -> mappings.putAll(getMappingForField(name, fieldDescription)));

    return mappings;
  }

  private Map<String, JsonNode> getMappingForField(String name, FieldDescription fieldDescription) {
    if (fieldDescription instanceof PlainFieldDescription) {
      return getMappingsForPlainField(name, (PlainFieldDescription) fieldDescription);
    }
    return getMappingForObjectField(name, (ObjectFieldDescription) fieldDescription);
  }

  private Map<String, JsonNode> getMappingsForPlainField(String name, PlainFieldDescription fieldDescription) {
    if (fieldDescription.isNotIndexed()) {
      return emptyMap();
    }

    var customMappings = fieldDescription.getMappings();
    var indexType = fieldDescription.getIndex();
    if (indexType == null) {
      return customMappings != null ? singletonMap(name, customMappings) : emptyMap();
    }

    var mappings = getSearchFieldTypeMappings(indexType);
    if (fieldDescription.hasFulltextIndex()) {
      if (fieldDescription.isMultilang()) {
        removeUnsupportedLanguages(mappings);
      }

      var fulltextEsMappings = new LinkedHashMap<String, JsonNode>(2, 1.0f);
      var plainFieldMappings = getSearchFieldTypeMappings(FULLTEXT_FIELD_TYPES.get(indexType));
      fulltextEsMappings.put(name, mappings);
      fulltextEsMappings.put(PLAIN_FULLTEXT_PREFIX + name, withCustomMappings(plainFieldMappings, customMappings));
      return fulltextEsMappings;
    }

    return singletonMap(name, withCustomMappings(mappings, customMappings));
  }

  private ObjectNode getSearchFieldTypeMappings(String indexType) {
    var searchFieldType = searchFieldProvider.getSearchFieldType(indexType);
    if (searchFieldType == null || searchFieldType.getMapping() == null) {
      throw new ResourceDescriptionException("Failed to find related mappings for index type: " + indexType);
    }
    return searchFieldType.getMapping().deepCopy();
  }

  private Map<String, JsonNode> getMappingForObjectField(String fieldName, ObjectFieldDescription fieldDescription) {
    var mappingProps = new LinkedHashMap<String, JsonNode>();
    fieldDescription.getProperties().forEach((name, desc) -> mappingProps.putAll(getMappingForField(name, desc)));
    var objectNodeMappings = singletonMap(MAPPING_PROPERTIES_FIELD, mappingProps);
    return singletonMap(fieldName, jsonConverter.toJsonTree(objectNodeMappings));
  }

  private void removeUnsupportedLanguages(ObjectNode mappings) {
    var languageConfigsMap = languageConfigService.getAll().getLanguageConfigs().stream()
      .collect(toMap(LanguageConfig::getCode, Function.identity()));

    var propertiesIterator = mappings.get(MAPPING_PROPERTIES_FIELD).fields();
    while (propertiesIterator.hasNext()) {
      var languageNode = propertiesIterator.next();
      var languageCode = languageNode.getKey();
      var languageConfig = languageConfigsMap.get(languageCode);

      if (languageConfig != null && languageConfig.getLanguageAnalyzer() != null) {
        ((ObjectNode) languageNode.getValue()).put("analyzer", languageConfig.getLanguageAnalyzer());
        continue;
      }

      if (languageConfig == null && !MULTILANG_SOURCE_SUBFIELD.equals(languageCode)) {
        propertiesIterator.remove();
      }
    }
  }

  private static ObjectNode withCustomMappings(ObjectNode sourceMappings, ObjectNode customMappings) {
    if (customMappings != null) {
      sourceMappings.setAll(customMappings);
    }
    return sourceMappings;
  }
}
