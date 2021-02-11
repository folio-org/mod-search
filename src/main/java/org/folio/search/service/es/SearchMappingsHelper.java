package org.folio.search.service.es;

import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.metadata.PlainFieldDescription.NONE_FIELD_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ObjectFieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
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
  private static final String MULTILANG_SOURCE_FIELD = "src";

  private final ResourceDescriptionService resourceDescriptionService;
  private final SearchFieldProvider searchFieldProvider;
  private final JsonConverter jsonConverter;
  private final ObjectMapper objectMapper;
  private final LanguageConfigService languageConfigService;

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

    mappingProperties.putAll(createMappingsForFields(description));
    mappingProperties.putAll(createMappingsForGroups(description));
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
    indexMappings.put("_routing", Map.of("required", true));
    return indexMappings;
  }

  private Map<String, JsonNode> createMappingsForFields(ResourceDescription description) {
    var fields = description.getFields();
    if (MapUtils.isEmpty(fields)) {
      return Collections.emptyMap();
    }

    var mappings = new LinkedHashMap<String, JsonNode>();
    fields.forEach((name, fieldDescription) -> {
      var fieldMapping = getMappingForField(fieldDescription);
      if (fieldMapping != null) {
        mappings.put(name, fieldMapping);
      }
    });

    return mappings;
  }

  private Map<String, JsonNode> createMappingsForGroups(ResourceDescription description) {
    var groups = description.getGroups();
    if (MapUtils.isEmpty(groups)) {
      return Collections.emptyMap();
    }

    var mappings = new LinkedHashMap<String, JsonNode>();
    groups.forEach((name, groupDescription) -> {
      var fieldMapping = getMappingForPlainField(groupDescription);
      if (fieldMapping != null) {
        mappings.put(name, fieldMapping);
      }
    });

    return mappings;
  }

  private JsonNode getMappingForField(FieldDescription fieldDescription) {
    if (fieldDescription instanceof PlainFieldDescription) {
      return getMappingForPlainField((PlainFieldDescription) fieldDescription);
    }
    return getMappingForObjectField((ObjectFieldDescription) fieldDescription);
  }

  private JsonNode getMappingForPlainField(PlainFieldDescription fieldDescription) {
    if (CollectionUtils.isNotEmpty(fieldDescription.getGroup())) {
      return null;
    }
    var indexType = fieldDescription.getIndex();
    ObjectNode mappings = null;
    if (indexType != null && !NONE_FIELD_TYPE.equals(indexType)) {
      mappings = searchFieldProvider.getSearchFieldType(indexType).getMapping().deepCopy();
    }

    var fieldDescriptionMappings = fieldDescription.getMappings();
    if (fieldDescriptionMappings != null) {
      if (mappings == null) {
        mappings = fieldDescriptionMappings.deepCopy();
      } else {
        if (MULTILANG_FIELD_TYPE.equals(fieldDescription.getIndex())) {
          ((ObjectNode) mappings.path(MAPPING_PROPERTIES_FIELD).path("src")).setAll(fieldDescriptionMappings);
        } else {
          mappings.setAll(fieldDescriptionMappings);
        }
      }
    }

    if (fieldDescription.isMultilang()) {
      removeUnsupportedLanguages(mappings);
    }

    return mappings;
  }

  private JsonNode getMappingForObjectField(ObjectFieldDescription fieldDescription) {
    var objectNodeMappings = new LinkedHashMap<String, JsonNode>();
    for (var entry : fieldDescription.getProperties().entrySet()) {
      objectNodeMappings.put(entry.getKey(), getMappingForField(entry.getValue()));
    }
    return objectMapper.valueToTree(Map.of(MAPPING_PROPERTIES_FIELD, objectNodeMappings));
  }

  private void removeUnsupportedLanguages(ObjectNode mappings) {
    if (mappings == null) {
      return;
    }

    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    var propertiesIterator = mappings.get(MAPPING_PROPERTIES_FIELD).fields();
    while (propertiesIterator.hasNext()) {
      var languageNode = propertiesIterator.next();
      if (!supportedLanguages.contains(languageNode.getKey())
        && !MULTILANG_SOURCE_FIELD.equals(languageNode.getKey())) {
        propertiesIterator.remove();
      }
    }
  }
}
