package org.folio.search.service.ingest;

import static org.apache.commons.collections4.MapUtils.getBoolean;
import static org.apache.commons.collections4.MapUtils.getMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.CallNumberUtils.getEffectiveCallNumber;
import static org.folio.search.utils.CallNumberUtils.normalizeCallNumberComponents;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.index.InstanceSearchDocument;
import org.folio.search.utils.V2BrowseIdComputer;
import org.springframework.stereotype.Service;

/**
 * Enriches raw inventory records into flat InstanceSearchDocument objects.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceSearchEnrichmentService {

  private static final String INSTANCE_TYPE = "instance";
  private static final String HOLDING_TYPE = "holding";
  private static final String ITEM_TYPE = "item";

  private final V2InstanceFieldEnricher v2InstanceFieldEnricher;

  public void resetProfilingCounters() {
    v2InstanceFieldEnricher.resetProfilingCounters();
  }

  public void logProfilingSummary() {
    v2InstanceFieldEnricher.logProfilingSummary();
  }

  public InstanceSearchDocument enrich(Map<String, Object> rawRecord, String resourceType, String tenantId,
                                       boolean shared) {
    var id = getString(rawRecord, "id");
    var instanceId = resolveInstanceId(rawRecord, resourceType, id);
    var sourceVersion = resolveSourceVersion(rawRecord);

    var joinField = buildJoinField(resourceType, instanceId);
    var fields = enrichFields(rawRecord, resourceType, tenantId);

    return InstanceSearchDocument.builder()
      .id(id)
      .resourceType(resourceType)
      .instanceId(instanceId)
      .tenantId(tenantId)
      .shared(shared)
      .joinField(joinField)
      .sourceVersion(sourceVersion)
      .fields(fields)
      .build();
  }

  private String resolveInstanceId(Map<String, Object> rawRecord, String resourceType, String id) {
    return switch (resourceType) {
      case INSTANCE_TYPE -> id;
      case HOLDING_TYPE, ITEM_TYPE -> getString(rawRecord, "instanceId");
      default -> throw new IllegalArgumentException("Unknown resource type: " + resourceType);
    };
  }

  private long resolveSourceVersion(Map<String, Object> rawRecord) {
    var version = rawRecord.get("_version");
    if (version instanceof Number num) {
      return num.longValue();
    }
    var metadata = rawRecord.get("metadata");
    if (metadata instanceof Map<?, ?> metadataMap) {
      var updatedDate = metadataMap.get("updatedDate");
      if (updatedDate instanceof String dateStr) {
        try {
          return java.time.Instant.parse(dateStr).toEpochMilli();
        } catch (Exception e) {
          log.debug("resolveSourceVersion:: could not parse updatedDate [value: {}]", dateStr);
        }
      }
    }
    return System.currentTimeMillis();
  }

  private Map<String, Object> buildJoinField(String resourceType, String instanceId) {
    var joinField = new HashMap<String, Object>();
    joinField.put("name", resourceType);
    if (!INSTANCE_TYPE.equals(resourceType)) {
      joinField.put("parent", instanceId);
    }
    return joinField;
  }

  private Map<String, Object> enrichFields(Map<String, Object> rawRecord, String resourceType, String tenantId) {
    return switch (resourceType) {
      case INSTANCE_TYPE -> enrichInstanceFields(rawRecord, tenantId);
      case HOLDING_TYPE -> enrichHoldingFields(rawRecord);
      case ITEM_TYPE -> enrichItemFields(rawRecord);
      default -> throw new IllegalArgumentException("Unknown resource type: " + resourceType);
    };
  }

  private Map<String, Object> enrichInstanceFields(Map<String, Object> rawRecord, String tenantId) {
    return v2InstanceFieldEnricher.enrich(rawRecord, tenantId);
  }

  private Map<String, Object> enrichHoldingFields(Map<String, Object> rawRecord) {
    var fields = baseFields(rawRecord);
    fields.put("holdingsRecordId", getString(rawRecord, "id"));
    fields.put("holdingsSourceId", getString(rawRecord, "sourceId"));
    fields.put("holdingsHrid", getString(rawRecord, "hrid"));
    fields.put("holdingsElectronicAccess", rawRecord.get("electronicAccess"));
    fields.put("holdingsAdministrativeNotes", rawRecord.get("administrativeNotes"));
    fields.put("holdingsNotes", extractNotes(rawRecord.get("notes")));
    fields.put("holdingsStatisticalCodeIds", rawRecord.get("statisticalCodeIds"));
    fields.put("holdingsDiscoverySuppress", rawRecord.get("discoverySuppress"));
    fields.put("holdingsIdentifiers", collectNonBlankStrings(
      getString(rawRecord, "id"),
      getString(rawRecord, "hrid")));
    appendAll((Collection<String>) fields.get("holdingsIdentifiers"), rawRecord.get("formerIds"));
    fields.put("holdingsPublicNotes", extractPublicNotes(rawRecord.get("notes"), null));
    addHoldingCallNumberAliases(fields, rawRecord);
    addTagAliases(fields, rawRecord, "holdingsTags");
    return fields;
  }

  private Map<String, Object> enrichItemFields(Map<String, Object> rawRecord) {
    var fields = baseFields(rawRecord);
    fields.put("itemHrid", getString(rawRecord, "hrid"));
    fields.put("itemElectronicAccess", rawRecord.get("electronicAccess"));
    fields.put("itemAdministrativeNotes", rawRecord.get("administrativeNotes"));
    fields.put("itemNotes", extractNotes(rawRecord.get("notes")));
    fields.put("itemCirculationNotes", extractNotes(rawRecord.get("circulationNotes")));
    fields.put("itemStatisticalCodeIds", rawRecord.get("statisticalCodeIds"));
    fields.put("itemDiscoverySuppress", rawRecord.get("discoverySuppress"));
    fields.put("itemStatus", getStringValue(getMap(rawRecord, "status"), "name"));
    fields.put("itemIdentifiers", collectNonBlankStrings(
      getString(rawRecord, "id"),
      getString(rawRecord, "hrid"),
      getString(rawRecord, "accessionNumber"),
      getString(rawRecord, "itemIdentifier")));
    appendAll((Collection<String>) fields.get("itemIdentifiers"), rawRecord.get("formerIds"));
    fields.put("itemPublicNotes", extractPublicNotes(rawRecord.get("notes"), rawRecord.get("circulationNotes")));
    addItemCallNumberAliases(fields, rawRecord);
    addTagAliases(fields, rawRecord, "itemTags");
    var callNumberBrowseId = V2BrowseIdComputer.computeCallNumberBrowseId(rawRecord);
    if (callNumberBrowseId != null) {
      fields.put("itemCallNumberBrowseId", callNumberBrowseId);
    }
    return fields;
  }

  private Map<String, Object> baseFields(Map<String, Object> rawRecord) {
    var fields = new HashMap<>(rawRecord);
    fields.remove("_version");
    return fields;
  }

  private void addHoldingCallNumberAliases(Map<String, Object> fields, Map<String, Object> rawRecord) {
    var fullCallNumber = getEffectiveCallNumber(
      getString(rawRecord, "callNumberPrefix"),
      getString(rawRecord, "callNumber"),
      getString(rawRecord, "callNumberSuffix"));
    if (StringUtils.isNotBlank(fullCallNumber)) {
      fields.put("holdingsFullCallNumbers", List.of(fullCallNumber));
      fields.put("holdingsNormalizedCallNumbers", List.of(
        normalizeCallNumberComponents(
          getString(rawRecord, "callNumberPrefix"),
          getString(rawRecord, "callNumber"),
          getString(rawRecord, "callNumberSuffix")),
        normalizeCallNumberComponents(
          getString(rawRecord, "callNumber"),
          getString(rawRecord, "callNumberSuffix"))));
    }
  }

  @SuppressWarnings("unchecked")
  private void addItemCallNumberAliases(Map<String, Object> fields, Map<String, Object> rawRecord) {
    var effectiveCallNumberComponents = getMap(rawRecord, "effectiveCallNumberComponents");
    if (effectiveCallNumberComponents == null || effectiveCallNumberComponents.isEmpty()) {
      return;
    }
    var prefix = getStringValue(effectiveCallNumberComponents, "prefix");
    var callNumber = getStringValue(effectiveCallNumberComponents, "callNumber");
    var suffix = getStringValue(effectiveCallNumberComponents, "suffix");
    var fullCallNumber = getEffectiveCallNumber(prefix, callNumber, suffix);
    if (StringUtils.isNotBlank(fullCallNumber)) {
      fields.put("itemFullCallNumbers", List.of(fullCallNumber));
      fields.put("itemNormalizedCallNumbers", List.of(
        normalizeCallNumberComponents(prefix, callNumber, suffix),
        normalizeCallNumberComponents(callNumber, suffix)));
    }
  }

  @SuppressWarnings("unchecked")
  private void addTagAliases(Map<String, Object> fields, Map<String, Object> rawRecord, String aliasName) {
    var tags = getMap(rawRecord, "tags");
    if (tags == null) {
      return;
    }
    var tagList = tags.get("tagList");
    if (tagList instanceof Collection<?> collection) {
      fields.put(aliasName, collection);
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> extractNotes(Object notesValue) {
    if (!(notesValue instanceof Collection<?> collection)) {
      return List.of();
    }
    return collection.stream()
      .filter(Map.class::isInstance)
      .map(Map.class::cast)
      .map(note -> getString(note, "note"))
      .filter(StringUtils::isNotBlank)
      .toList();
  }

  private List<String> extractPublicNotes(Object notesValue, Object circulationNotesValue) {
    var notes = new LinkedHashSet<String>();
    extractPublicNotes(notesValue).forEach(notes::add);
    extractPublicNotes(circulationNotesValue).forEach(notes::add);
    return List.copyOf(notes);
  }

  @SuppressWarnings("unchecked")
  private List<String> extractPublicNotes(Object notesValue) {
    if (!(notesValue instanceof Collection<?> collection)) {
      return List.of();
    }
    return collection.stream()
      .filter(Map.class::isInstance)
      .map(Map.class::cast)
      .filter(note -> !Boolean.TRUE.equals(getBoolean(note, "staffOnly")))
      .map(note -> getString(note, "note"))
      .filter(StringUtils::isNotBlank)
      .toList();
  }

  @SuppressWarnings("unchecked")
  private void appendAll(Collection<String> target, Object additionalValues) {
    if (target == null || !(additionalValues instanceof Collection<?> collection)) {
      return;
    }
    collection.stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .filter(StringUtils::isNotBlank)
      .forEach(target::add);
  }

  private LinkedHashSet<String> collectNonBlankStrings(String... values) {
    var result = new LinkedHashSet<String>();
    for (var value : values) {
      if (StringUtils.isNotBlank(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private String getStringValue(Map<?, ?> map, String key) {
    if (map == null) {
      return null;
    }
    var value = map.get(key);
    return value instanceof String stringValue ? stringValue : null;
  }
}
