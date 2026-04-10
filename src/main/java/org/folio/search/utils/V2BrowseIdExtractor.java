package org.folio.search.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

/**
 * Extracts touched browse IDs from V2 document payloads.
 * Used by streaming reindex, temp consumer, and real-time Kafka paths.
 */
@UtilityClass
public class V2BrowseIdExtractor {

  /**
   * Dispatches to the correct browse ID extractor based on resource type.
   * Unifies the switch(resourceType) pattern used in KafkaMessageListener,
   * ReindexKafkaConsumerManager, and StreamingReindexService.
   */
  @SuppressWarnings("unchecked")
  public static TouchedBrowseIds computeFromRawRecord(String resourceType, Map<String, Object> rawRecord) {
    return switch (resourceType) {
      case "instance" -> computeFromRawInstanceRecord(rawRecord);
      case "item" -> computeFromRawItemRecord(rawRecord);
      default -> TouchedBrowseIds.empty();
    };
  }

  /**
   * Computes browse IDs from a raw instance record (pre-enrichment).
   */
  @SuppressWarnings("unchecked")
  static TouchedBrowseIds computeFromRawInstanceRecord(Map<String, Object> rawRecord) {
    var contributorIds = extractIds(
      (List<Map<String, Object>>) rawRecord.get("contributors"), V2BrowseIdComputer::computeContributorBrowseId);
    var subjectIds = extractIds(
      (List<Map<String, Object>>) rawRecord.get("subjects"), V2BrowseIdComputer::computeSubjectBrowseId);
    var classificationIds = extractIds(
      (List<Map<String, Object>>) rawRecord.get("classifications"), V2BrowseIdComputer::computeClassificationBrowseId);

    return new TouchedBrowseIds(contributorIds, subjectIds, classificationIds, new HashSet<>());
  }

  /**
   * Computes call number browse ID from a raw item record (pre-enrichment).
   */
  static TouchedBrowseIds computeFromRawItemRecord(Map<String, Object> rawRecord) {
    var callNumberIds = new HashSet<String>();
    var id = V2BrowseIdComputer.computeCallNumberBrowseId(rawRecord);
    if (id != null) {
      callNumberIds.add(id);
    }
    return new TouchedBrowseIds(new HashSet<>(), new HashSet<>(), new HashSet<>(), callNumberIds);
  }

  private static Set<String> extractIds(List<Map<String, Object>> items,
                                         Function<Map<String, Object>, String> idExtractor) {
    var ids = new HashSet<String>();
    if (items != null) {
      for (var item : items) {
        var id = idExtractor.apply(item);
        if (id != null) {
          ids.add(id);
        }
      }
    }
    return ids;
  }

  public record TouchedBrowseIds(
    Set<String> contributorIds,
    Set<String> subjectIds,
    Set<String> classificationIds,
    Set<String> callNumberIds
  ) {
    public static TouchedBrowseIds empty() {
      return new TouchedBrowseIds(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public void merge(TouchedBrowseIds other) {
      contributorIds.addAll(other.contributorIds);
      subjectIds.addAll(other.subjectIds);
      classificationIds.addAll(other.classificationIds);
      callNumberIds.addAll(other.callNumberIds);
    }

    public boolean isEmpty() {
      return contributorIds.isEmpty() && subjectIds.isEmpty()
        && classificationIds.isEmpty() && callNumberIds.isEmpty();
    }
  }
}
