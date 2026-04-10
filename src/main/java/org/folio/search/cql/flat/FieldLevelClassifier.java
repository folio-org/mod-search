package org.folio.search.cql.flat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Classifies CQL field paths by resource level (INSTANCE, HOLDING, or ITEM).
 * Loaded from instance_search_flat_field_map.json.
 *
 * <p>Bare field names that physically exist on multiple levels (notably {@code id} and
 * {@code hrid}) are listed ONLY under instanceFields so unprefixed CQL terms resolve to the
 * parent doc. Child-level lookups must use the explicit {@code holdings.}/{@code items.}
 * prefix, which the {@link #classify} method routes via prefix checks before consulting the
 * field map.
 */
@Log4j2
@Component
public class FieldLevelClassifier {

  @Getter
  public enum ResourceLevel {
    INSTANCE("instance"),
    HOLDING("holding"),
    ITEM("item");

    private final String joinType;

    ResourceLevel(String joinType) {
      this.joinType = joinType;
    }
  }

  private final Map<String, ResourceLevel> fieldLevelMap = new HashMap<>();

  public FieldLevelClassifier(ObjectMapper objectMapper) {
    try {
      var resource = new ClassPathResource("elasticsearch/instance_search_flat_field_map.json");
      if (resource.exists()) {
        var map = objectMapper.readValue(resource.getInputStream(),
          new TypeReference<Map<String, List<String>>>() { });
        populateMap(map.getOrDefault("instanceFields", List.of()), ResourceLevel.INSTANCE);
        populateMap(map.getOrDefault("holdingFields", List.of()), ResourceLevel.HOLDING);
        populateMap(map.getOrDefault("itemFields", List.of()), ResourceLevel.ITEM);
        log.info("FieldLevelClassifier:: loaded field map [fields: {}]", fieldLevelMap.size());
      } else {
        log.warn("FieldLevelClassifier:: field map file not found, using defaults");
        initializeDefaults();
      }
    } catch (IOException e) {
      log.warn("FieldLevelClassifier:: error loading field map, using defaults", e);
      initializeDefaults();
    }
  }

  public ResourceLevel classify(String fieldPath) {
    if (fieldPath.startsWith("holdings.")) {
      return ResourceLevel.HOLDING;
    }
    if (fieldPath.startsWith("items.") || fieldPath.startsWith("item.")) {
      return ResourceLevel.ITEM;
    }
    return fieldLevelMap.getOrDefault(fieldPath, ResourceLevel.INSTANCE);
  }

  /**
   * Converts a CQL field path to the namespaced ES field path.
   * CQL "holdings.permanentLocationId" → ES "holding.permanentLocationId"
   * CQL "items.barcode" → ES "item.barcode"
   * CQL "title" (instance field) → ES "instance.title"
   */
  public String normalizeField(String fieldPath) {
    if (fieldPath.startsWith("holdings.")) {
      return "holding." + fieldPath.substring("holdings.".length());
    }
    if (fieldPath.startsWith("items.")) {
      return "item." + fieldPath.substring("items.".length());
    }
    if (fieldPath.startsWith("item.")) {
      return "item." + fieldPath.substring("item.".length());
    }
    // Check if this is a holding or item field without prefix
    var level = fieldLevelMap.getOrDefault(fieldPath, ResourceLevel.INSTANCE);
    return switch (level) {
      case HOLDING -> "holding." + fieldPath;
      case ITEM -> "item." + fieldPath;
      case INSTANCE -> "instance." + fieldPath;
    };
  }

  private void populateMap(List<String> fields, ResourceLevel level) {
    for (var field : fields) {
      fieldLevelMap.put(field, level);
    }
  }

  private void initializeDefaults() {
    // Holdings-level fields
    for (var field : List.of("permanentLocationId", "holdingsTypeId", "holdingsSourceId",
      "holdingsNormalizedCallNumbers", "holdingsFullCallNumbers")) {
      fieldLevelMap.put(field, ResourceLevel.HOLDING);
    }
    // Item-level fields
    for (var field : List.of("barcode", "effectiveLocationId", "materialTypeId", "status.name",
      "effectiveCallNumberComponents", "itemStatus")) {
      fieldLevelMap.put(field, ResourceLevel.ITEM);
    }
  }
}
