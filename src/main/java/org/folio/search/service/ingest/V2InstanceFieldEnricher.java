package org.folio.search.service.ingest;

import static org.folio.search.utils.CollectionUtils.mergeSafely;
import static org.folio.search.utils.SearchConverterUtils.getMapValueByPath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.service.consortium.LanguageConfigServiceDecorator;
import org.folio.search.service.converter.SearchFieldsProcessor;
import org.folio.search.utils.ResourceFieldMapper;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.search.utils.V2BrowseIdComputer;
import org.springframework.stereotype.Service;

/**
 * Self-contained V2 instance enrichment service that produces the same Map output
 * as the V1-routed pipeline, but without the serialize/deserialize round-trip and
 * running only instance-relevant processors.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class V2InstanceFieldEnricher {

  private static final String INSTANCE_RESOURCE = "instance";

  private final V2ResourceDescriptionProvider descriptionProvider;
  private final SearchFieldsProcessor searchFieldsProcessor;
  private final LanguageConfigServiceDecorator languageConfigService;

  // Thread-safe profiling accumulators (concurrent Kafka consumers call this)
  private final AtomicLong fieldMapNs = new AtomicLong();
  private final AtomicLong searchFieldsNs = new AtomicLong();
  private final AtomicLong browseIdsNs = new AtomicLong();
  private final AtomicLong count = new AtomicLong();

  /**
   * Enriches a raw instance record into a Map suitable for the V2 flat index.
   *
   * @param rawRecord the raw instance record from inventory
   * @param tenantId  the tenant identifier
   * @return enriched fields map, or null if empty
   */
  public Map<String, Object> enrich(Map<String, Object> rawRecord, String tenantId) {
    var description = descriptionProvider.getV2InstanceDescription();

    // Resolve languages (replicate SearchDocumentConverter.getResourceLanguages logic)
    var languages = resolveLanguages(description.getLanguageSourcePaths(), rawRecord);

    // Base field mapping
    final long t0 = System.nanoTime();
    var baseFields = ResourceFieldMapper.convertMapUsingResourceFields(
      rawRecord, description.getFields(), languages, tenantId);
    final long fieldMapElapsed = System.nanoTime() - t0;

    // Search fields via processors
    var event = new ResourceEvent()
      .id(MapUtils.getString(rawRecord, "id"))
      .tenant(tenantId)
      .resourceName(INSTANCE_RESOURCE)
      ._new(rawRecord);
    var context = ConversionContext.of(event, description, languages, tenantId);
    final long sfStart = System.nanoTime();
    var searchFields = searchFieldsProcessor.getSearchFields(context);
    final long searchFieldsElapsed = System.nanoTime() - sfStart;

    // Merge base + search fields
    var result = mergeSafely(baseFields, searchFields);
    if (result == null) {
      result = new HashMap<>();
    }

    // Remove 'shared' — it is in instance.json fields and gets produced by ResourceFieldMapper,
    // but is set separately at the wrapper level by InstanceSearchDocumentConverter
    result.remove("shared");

    // Add browse IDs
    final long browseStart = System.nanoTime();
    addBrowseIdsToInstanceFields(result);
    final long browseElapsed = System.nanoTime() - browseStart;

    // Profiling
    fieldMapNs.addAndGet(fieldMapElapsed);
    searchFieldsNs.addAndGet(searchFieldsElapsed);
    browseIdsNs.addAndGet(browseElapsed);
    var currentCount = count.incrementAndGet();
    if (currentCount % 5000 == 0) {
      log.info("enrich:: profile [records: {}, fieldMap: {}ms, searchFields: {}ms, browseIds: {}ms]",
        currentCount,
        fieldMapNs.get() / 1_000_000,
        searchFieldsNs.get() / 1_000_000,
        browseIdsNs.get() / 1_000_000);
    }

    return result;
  }

  /**
   * Logs a profiling summary and resets counters.
   * Delegates to {@link SearchFieldsProcessor#logProfilingSummary()} as well.
   */
  public void logProfilingSummary() {
    var currentCount = count.get();
    if (currentCount > 0) {
      log.info("enrich:: SUMMARY [records: {}, fieldMap: {}ms (avg: {}us), "
          + "searchFields: {}ms (avg: {}us), browseIds: {}ms (avg: {}us)]",
        currentCount,
        fieldMapNs.get() / 1_000_000, fieldMapNs.get() / currentCount / 1_000,
        searchFieldsNs.get() / 1_000_000, searchFieldsNs.get() / currentCount / 1_000,
        browseIdsNs.get() / 1_000_000, browseIdsNs.get() / currentCount / 1_000);
    }
    resetProfilingCounters();
    searchFieldsProcessor.logProfilingSummary();
  }

  /**
   * Resets all profiling accumulators.
   */
  public void resetProfilingCounters() {
    fieldMapNs.set(0);
    searchFieldsNs.set(0);
    browseIdsNs.set(0);
    count.set(0);
  }

  private List<String> resolveLanguages(List<String> languageSourcePaths,
                                        Map<String, Object> rawRecord) {
    var supportedLanguages = languageConfigService.getAllLanguageCodes();
    return languageSourcePaths.stream()
      .map(sourcePath -> getMapValueByPath(sourcePath, rawRecord))
      .flatMap(SearchConverterUtils::getStringStreamFromValue)
      .distinct()
      .filter(supportedLanguages::contains)
      .toList();
  }

  @SuppressWarnings("unchecked")
  private void addBrowseIdsToInstanceFields(Map<String, Object> fields) {
    var contributors = (List<Map<String, Object>>) fields.get("contributors");
    if (contributors != null) {
      for (var contributor : contributors) {
        var browseId = V2BrowseIdComputer.computeContributorBrowseId(contributor);
        if (browseId != null) {
          contributor.put("browseId", browseId);
        }
      }
    }

    var subjects = (List<Map<String, Object>>) fields.get("subjects");
    if (subjects != null) {
      for (var subject : subjects) {
        var browseId = V2BrowseIdComputer.computeSubjectBrowseId(subject);
        if (browseId != null) {
          subject.put("browseId", browseId);
        }
      }
    }

    var classifications = (List<Map<String, Object>>) fields.get("classifications");
    if (classifications != null) {
      for (var classification : classifications) {
        var browseId = V2BrowseIdComputer.computeClassificationBrowseId(classification);
        if (browseId != null) {
          classification.put("browseId", browseId);
        }
      }
    }
  }
}
