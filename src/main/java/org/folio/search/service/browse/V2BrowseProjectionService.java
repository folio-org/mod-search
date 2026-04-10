package org.folio.search.service.browse;

import static org.apache.commons.collections4.MapUtils.getString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.V2BrowseProjectionRepository;
import org.folio.search.utils.CallNumberUtils;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.folio.search.utils.V2BrowseIdExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Projects V2 browse documents from the V2 main index into V2 browse indices.
 * Each rebuild method queries the V2 main index for documents matching the given browse IDs,
 * aggregates them into the existing browse document shape, and upserts/deletes accordingly.
 */
@Log4j2
@Service
public class V2BrowseProjectionService {

  private final V2BrowseProjectionRepository repository;
  private final IndexRepository indexRepository;
  private final Executor browseUpsertExecutor;

  public V2BrowseProjectionService(V2BrowseProjectionRepository repository,
                                   IndexRepository indexRepository,
                                   @Qualifier("browseUpsertExecutor") Executor browseUpsertExecutor) {
    this.repository = repository;
    this.indexRepository = indexRepository;
    this.browseUpsertExecutor = browseUpsertExecutor;
  }

  /**
   * Refreshes the main index and rebuilds all browse projections for the touched browse IDs.
   */
  public void rebuildAll(V2BrowseIdExtractor.TouchedBrowseIds touched, String mainIndex,
                         Map<ResourceType, String> browseIndexNames) {
    indexRepository.refreshIndices(mainIndex);
    rebuildContributors(touched.contributorIds(), mainIndex, browseIndexNames.get(ResourceType.V2_CONTRIBUTOR));
    rebuildSubjects(touched.subjectIds(), mainIndex, browseIndexNames.get(ResourceType.V2_SUBJECT));
    rebuildClassifications(touched.classificationIds(), mainIndex,
      browseIndexNames.get(ResourceType.V2_CLASSIFICATION));
    rebuildCallNumbers(touched.callNumberIds(), mainIndex, browseIndexNames.get(ResourceType.V2_CALL_NUMBER));
  }

  /**
   * Rebuilds all V2 browse indices by scanning the V2 main index exactly once per source type.
   * Intended for full browse rebuilds after the browse indices have been cleared.
   */
  public void rebuildFull(String mainIndex, Map<ResourceType, String> browseIndexNames) {
    var t0 = System.nanoTime();
    indexRepository.refreshIndices(mainIndex);
    log.info("rebuildFull:: refresh done [elapsed: {}ms]", ms(System.nanoTime() - t0));

    var contributorDocs = new LinkedHashMap<String, ContributorDocAccumulator>();
    var subjectDocs = new LinkedHashMap<String, SubjectDocAccumulator>();
    var classificationDocs = new LinkedHashMap<String, ClassificationDocAccumulator>();

    t0 = System.nanoTime();
    repository.streamAllInstanceBrowseSourceDocs(mainIndex, hits -> {
      aggregateAllInstanceBrowse(hits, contributorDocs, subjectDocs, classificationDocs);
    }, "instance.contributors", "instance.subjects", "instance.classifications", "tenantId", "shared");
    log.info("rebuildFull:: instance stream+aggregate done [elapsed: {}ms, contributors: {}, subjects: {}, "
        + "classifications: {}]", ms(System.nanoTime() - t0), contributorDocs.size(), subjectDocs.size(),
      classificationDocs.size());

    // #2: Launch 3 upserts in parallel
    final var upsertStart = System.nanoTime();
    var contributorFuture = CompletableFuture.runAsync(() -> {
      var ut0 = System.nanoTime();
      bulkUpsertAccumulators(browseIndexNames.get(ResourceType.V2_CONTRIBUTOR), contributorDocs.values());
      log.info("rebuildFull:: contributor upsert done [elapsed: {}ms, docs: {}]",
        ms(System.nanoTime() - ut0), contributorDocs.size());
    }, browseUpsertExecutor);
    var subjectFuture = CompletableFuture.runAsync(() -> {
      var ut0 = System.nanoTime();
      bulkUpsertAccumulators(browseIndexNames.get(ResourceType.V2_SUBJECT), subjectDocs.values());
      log.info("rebuildFull:: subject upsert done [elapsed: {}ms, docs: {}]",
        ms(System.nanoTime() - ut0), subjectDocs.size());
    }, browseUpsertExecutor);
    var classificationFuture = CompletableFuture.runAsync(() -> {
      var ut0 = System.nanoTime();
      bulkUpsertAccumulators(browseIndexNames.get(ResourceType.V2_CLASSIFICATION), classificationDocs.values());
      log.info("rebuildFull:: classification upsert done [elapsed: {}ms, docs: {}]",
        ms(System.nanoTime() - ut0), classificationDocs.size());
    }, browseUpsertExecutor);

    // #3: Overlap item scroll with the 3 upserts
    var callNumberDocs = new LinkedHashMap<String, CallNumberDocAccumulator>();
    var itemStart = System.nanoTime();
    repository.streamAllItemBrowseSourceDocs(mainIndex, hits -> {
      var unwrapped = unwrapHits(hits, "item");
      aggregateCallNumbers(callNumberDocs, unwrapped);
    }, "item.itemCallNumberBrowseId", "item.effectiveCallNumberComponents", "tenantId", "shared",
      "instanceId", "item.effectiveLocationId");
    log.info("rebuildFull:: item stream+aggregate done [elapsed: {}ms, callNumbers: {}]",
      ms(System.nanoTime() - itemStart), callNumberDocs.size());

    // Wait for the 3 upserts to finish
    CompletableFuture.allOf(contributorFuture, subjectFuture, classificationFuture).join();
    log.info("rebuildFull:: parallel upserts done [wallTime: {}ms]", ms(System.nanoTime() - upsertStart));

    t0 = System.nanoTime();
    bulkUpsertAccumulators(browseIndexNames.get(ResourceType.V2_CALL_NUMBER), callNumberDocs.values());
    log.info("rebuildFull:: callNumber upsert done [elapsed: {}ms, docs: {}]",
      ms(System.nanoTime() - t0), callNumberDocs.size());
  }

  private static long ms(long nanos) {
    return nanos / 1_000_000;
  }

  public void rebuildContributors(Set<String> contributorIds, String mainIndex, String browseIndex) {
    this.<ContributorDocAccumulator>rebuildBrowseDocuments("rebuildContributors", contributorIds, mainIndex,
      browseIndex,
      consumer -> repository.streamByNestedBrowseIds(mainIndex, "instance.contributors.browseId", contributorIds,
        hits -> consumer.accept(unwrapHits(hits, "instance")),
        "instance.contributors", "tenantId", "shared"),
      (result, hits, targetIds) -> aggregateContributors(result, hits, targetIds));
  }

  public void rebuildSubjects(Set<String> subjectIds, String mainIndex, String browseIndex) {
    this.<SubjectDocAccumulator>rebuildBrowseDocuments("rebuildSubjects", subjectIds, mainIndex, browseIndex,
      consumer -> repository.streamByNestedBrowseIds(mainIndex, "instance.subjects.browseId", subjectIds,
        hits -> consumer.accept(unwrapHits(hits, "instance")),
        "instance.subjects", "tenantId", "shared"),
      (result, hits, targetIds) -> aggregateSubjects(result, hits, targetIds));
  }

  public void rebuildClassifications(Set<String> classificationIds, String mainIndex, String browseIndex) {
    this.<ClassificationDocAccumulator>rebuildBrowseDocuments("rebuildClassifications", classificationIds,
      mainIndex, browseIndex,
      consumer -> repository.streamByNestedBrowseIds(mainIndex, "instance.classifications.browseId",
        classificationIds,
        hits -> consumer.accept(unwrapHits(hits, "instance")),
        "instance.classifications", "tenantId", "shared"),
      (result, hits, targetIds) -> aggregateClassifications(result, hits, targetIds));
  }

  public void rebuildCallNumbers(Set<String> callNumberIds, String mainIndex, String browseIndex) {
    this.<CallNumberDocAccumulator>rebuildBrowseDocuments("rebuildCallNumbers", callNumberIds, mainIndex,
      browseIndex,
      consumer -> repository.streamItemsByCallNumberBrowseIds(mainIndex, callNumberIds,
        hits -> consumer.accept(unwrapHits(hits, "item")),
        "item.itemCallNumberBrowseId", "item.effectiveCallNumberComponents", "tenantId", "shared",
        "instanceId", "item.effectiveLocationId"),
      (result, hits, targetIds) -> aggregateCallNumbers(result, hits, targetIds));
  }

  private <T extends BrowseDocAccumulator> void rebuildBrowseDocuments(
    String operation, Set<String> browseIds, String mainIndex, String browseIndex,
    Consumer<Consumer<List<Map<String, Object>>>> streamer,
    BatchAggregator<T> aggregator) {
    if (browseIds.isEmpty()) {
      return;
    }

    log.debug("{}:: rebuilding [count: {}, mainIndex: {}, browseIndex: {}]",
      operation, browseIds.size(), mainIndex, browseIndex);

    var aggregated = new LinkedHashMap<String, T>(browseIds.size());
    streamer.accept(hits -> aggregator.accept(aggregated, hits, browseIds));
    var toUpsert = new ArrayList<Map<String, Object>>(browseIds.size());
    var toDelete = new HashSet<String>(browseIds.size());
    for (var id : browseIds) {
      var doc = aggregated.get(id);
      if (doc != null) {
        toUpsert.add(doc.document());
      } else {
        toDelete.add(id);
      }
    }

    repository.bulkUpsert(browseIndex, toUpsert);
    repository.bulkDelete(browseIndex, toDelete);
  }

  @SuppressWarnings("unchecked")
  private static void aggregateAllInstanceBrowse(List<Map<String, Object>> rawHits,
                                                 Map<String, ContributorDocAccumulator> contributorDocs,
                                                 Map<String, SubjectDocAccumulator> subjectDocs,
                                                 Map<String, ClassificationDocAccumulator> classificationDocs) {
    for (var rawHit : rawHits) {
      var instance = (Map<String, Object>) rawHit.get("instance");
      if (instance == null) {
        continue;
      }
      var tenantId = (String) rawHit.get("tenantId");
      var shared = Boolean.TRUE.equals(rawHit.get("shared"));

      var contributors = (List<Map<String, Object>>) instance.get("contributors");
      if (contributors != null) {
        for (var contributor : contributors) {
          var browseId = getString(contributor, "browseId");
          if (browseId == null) {
            continue;
          }
          var doc = contributorDocs.computeIfAbsent(browseId, id -> new ContributorDocAccumulator(id, contributor));
          doc.addInstance(tenantId, shared, getString(contributor, "contributorTypeId"));
        }
      }

      var subjects = (List<Map<String, Object>>) instance.get("subjects");
      if (subjects != null) {
        for (var subject : subjects) {
          var browseId = getString(subject, "browseId");
          if (browseId == null) {
            continue;
          }
          var doc = subjectDocs.computeIfAbsent(browseId, id -> new SubjectDocAccumulator(id, subject));
          doc.addInstance(tenantId, shared);
        }
      }

      var classifications = (List<Map<String, Object>>) instance.get("classifications");
      if (classifications != null) {
        for (var classification : classifications) {
          var browseId = getString(classification, "browseId");
          if (browseId == null) {
            continue;
          }
          var doc = classificationDocs.computeIfAbsent(browseId,
            id -> new ClassificationDocAccumulator(id, classification));
          doc.addInstance(tenantId, shared);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void aggregateContributors(Map<String, ContributorDocAccumulator> result, List<Map<String, Object>> hits) {
    aggregateContributors(result, hits, null);
  }

  @SuppressWarnings("unchecked")
  private void aggregateContributors(Map<String, ContributorDocAccumulator> result, List<Map<String, Object>> hits,
                                     Set<String> targetIds) {
    for (var hit : hits) {
      var tenantId = getString(hit, "tenantId");
      var shared = Boolean.TRUE.equals(hit.get("shared"));
      var contributors = (List<Map<String, Object>>) hit.get("contributors");
      if (contributors == null) {
        continue;
      }
      for (var contributor : contributors) {
        var browseId = getString(contributor, "browseId");
        if (!isTargetBrowseId(browseId, targetIds)) {
          continue;
        }

        var doc = result.computeIfAbsent(browseId, id -> new ContributorDocAccumulator(id, contributor));
        doc.addInstance(tenantId, shared, getString(contributor, "contributorTypeId"));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void aggregateSubjects(Map<String, SubjectDocAccumulator> result, List<Map<String, Object>> hits) {
    aggregateSubjects(result, hits, null);
  }

  @SuppressWarnings("unchecked")
  private void aggregateSubjects(Map<String, SubjectDocAccumulator> result, List<Map<String, Object>> hits,
                                 Set<String> targetIds) {
    for (var hit : hits) {
      var tenantId = getString(hit, "tenantId");
      var shared = Boolean.TRUE.equals(hit.get("shared"));
      var subjects = (List<Map<String, Object>>) hit.get("subjects");
      if (subjects == null) {
        continue;
      }
      for (var subject : subjects) {
        var browseId = getString(subject, "browseId");
        if (!isTargetBrowseId(browseId, targetIds)) {
          continue;
        }

        var doc = result.computeIfAbsent(browseId, id -> new SubjectDocAccumulator(id, subject));
        doc.addInstance(tenantId, shared);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void aggregateClassifications(Map<String, ClassificationDocAccumulator> result,
                                        List<Map<String, Object>> hits) {
    aggregateClassifications(result, hits, null);
  }

  @SuppressWarnings("unchecked")
  private void aggregateClassifications(Map<String, ClassificationDocAccumulator> result,
                                        List<Map<String, Object>> hits,
                                        Set<String> targetIds) {
    for (var hit : hits) {
      var tenantId = getString(hit, "tenantId");
      var shared = Boolean.TRUE.equals(hit.get("shared"));
      var classifications = (List<Map<String, Object>>) hit.get("classifications");
      if (classifications == null) {
        continue;
      }
      for (var classification : classifications) {
        var browseId = getString(classification, "browseId");
        if (!isTargetBrowseId(browseId, targetIds)) {
          continue;
        }

        var doc = result.computeIfAbsent(browseId, id -> new ClassificationDocAccumulator(id, classification));
        doc.addInstance(tenantId, shared);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void aggregateCallNumbers(Map<String, CallNumberDocAccumulator> result, List<Map<String, Object>> hits) {
    aggregateCallNumbers(result, hits, null);
  }

  @SuppressWarnings("unchecked")
  private void aggregateCallNumbers(Map<String, CallNumberDocAccumulator> result, List<Map<String, Object>> hits,
                                    Set<String> targetIds) {
    for (var hit : hits) {
      var browseId = getString(hit, "itemCallNumberBrowseId");
      if (!isTargetBrowseId(browseId, targetIds)) {
        continue;
      }

      var tenantId = getString(hit, "tenantId");
      var shared = Boolean.TRUE.equals(hit.get("shared"));
      var instanceId = getString(hit, "instanceId");
      var locationId = getString(hit, "effectiveLocationId");

      var doc = result.computeIfAbsent(browseId, id -> new CallNumberDocAccumulator(id, hit));
      doc.addInstance(tenantId, shared, locationId, instanceId);
    }
  }

  private void bulkUpsertAccumulators(String browseIndex, Collection<? extends BrowseDocAccumulator> docs) {
    if (docs.isEmpty()) {
      return;
    }
    repository.bulkUpsert(browseIndex, docs.stream().map(BrowseDocAccumulator::document).toList());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> unwrapHits(List<Map<String, Object>> hits, String namespace) {
    return hits.stream().<Map<String, Object>>map(hit -> {
      var flat = new HashMap<>(hit);
      var nested = flat.remove(namespace);
      if (nested instanceof Map<?, ?> map) {
        flat.putAll((Map<String, Object>) map);
      }
      return flat;
    }).toList();
  }

  private static boolean isTargetBrowseId(String browseId, Set<String> targetIds) {
    return browseId != null && (targetIds == null || targetIds.contains(browseId));
  }

  private static void addShelvingOrders(Map<String, Object> doc, String number,
                                        boolean includeExtended) {
    doc.put("defaultShelvingOrder",
      ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.DEFAULT));
    doc.put("lcShelvingOrder",
      ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.LC));
    doc.put("deweyShelvingOrder",
      ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.DEWEY));
    if (includeExtended) {
      doc.put("nlmShelvingOrder",
        ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.NLM));
      doc.put("sudocShelvingOrder",
        ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.SUDOC));
    }
  }

  private static Map<String, Object> baseCountBucket(String tenantId, boolean shared) {
    var map = baseBucket(tenantId, shared);
    map.put("count", 0);
    return map;
  }

  private static Map<String, Object> baseBucket(String tenantId, boolean shared) {
    var map = new HashMap<String, Object>();
    map.put("tenantId", tenantId);
    map.put("shared", shared);
    return map;
  }

  private interface BrowseDocAccumulator {
    Map<String, Object> document();
  }

  @FunctionalInterface
  private interface BatchAggregator<T extends BrowseDocAccumulator> {
    void accept(Map<String, T> result, List<Map<String, Object>> hits, Set<String> targetIds);
  }

  private record TenantSharedKey(String tenantId, boolean shared) { }

  private record CallNumberBucketKey(String tenantId, boolean shared, String locationId) { }

  private abstract static class AbstractBrowseDocAccumulator<K> implements BrowseDocAccumulator {

    private final Map<String, Object> document = new HashMap<>();
    private final List<Map<String, Object>> instances = new ArrayList<>();
    private final Map<K, Map<String, Object>> instanceBuckets = new HashMap<>();

    protected AbstractBrowseDocAccumulator(String browseId) {
      document.put("id", browseId);
      document.put("instances", instances);
    }

    @Override
    public Map<String, Object> document() {
      return document;
    }

    protected Map<String, Object> documentMap() {
      return document;
    }

    protected Map<String, Object> bucket(K key, Supplier<Map<String, Object>> supplier) {
      var bucket = instanceBuckets.get(key);
      if (bucket != null) {
        return bucket;
      }

      bucket = supplier.get();
      instanceBuckets.put(key, bucket);
      instances.add(bucket);
      return bucket;
    }
  }

  private abstract static class CountedTenantSharedDocAccumulator
    extends AbstractBrowseDocAccumulator<TenantSharedKey> {

    protected CountedTenantSharedDocAccumulator(String browseId) {
      super(browseId);
    }

    protected Map<String, Object> addInstance(String tenantId, boolean shared) {
      var bucket = bucket(new TenantSharedKey(tenantId, shared), () -> baseCountBucket(tenantId, shared));
      bucket.put("count", ((Number) bucket.get("count")).intValue() + 1);
      return bucket;
    }
  }

  private static final class ContributorDocAccumulator extends CountedTenantSharedDocAccumulator {

    private ContributorDocAccumulator(String browseId, Map<String, Object> contributor) {
      super(browseId);
      var document = documentMap();
      document.put("name", contributor.get("name"));
      document.put("contributorNameTypeId", contributor.get("contributorNameTypeId"));
      document.put("authorityId", contributor.get("authorityId"));
    }

    private void addInstance(String tenantId, boolean shared, String typeId) {
      var bucket = addInstance(tenantId, shared);
      if (typeId != null) {
        @SuppressWarnings("unchecked")
        var typeIds = (Set<String>) bucket.computeIfAbsent("typeId", k -> new HashSet<>());
        typeIds.add(typeId);
      }
    }
  }

  private static final class SubjectDocAccumulator extends CountedTenantSharedDocAccumulator {

    private SubjectDocAccumulator(String browseId, Map<String, Object> subject) {
      super(browseId);
      var document = documentMap();
      document.put("value", subject.get("plain_value"));
      document.put("authorityId", subject.get("authorityId"));
      document.put("sourceId", subject.get("sourceId"));
      document.put("typeId", subject.get("typeId"));
    }
  }

  private static final class ClassificationDocAccumulator extends CountedTenantSharedDocAccumulator {

    private ClassificationDocAccumulator(String browseId, Map<String, Object> classification) {
      super(browseId);
      var number = getString(classification, "classificationNumber");
      var document = documentMap();
      document.put("number", number);
      document.put("typeId", classification.get("classificationTypeId"));
      if (number != null) {
        addShelvingOrders(document, number, false);
      }
    }
  }

  private static final class CallNumberDocAccumulator extends AbstractBrowseDocAccumulator<CallNumberBucketKey> {

    @SuppressWarnings("unchecked")
    private CallNumberDocAccumulator(String browseId, Map<String, Object> hit) {
      super(browseId);
      var components = (Map<String, Object>) hit.get("effectiveCallNumberComponents");
      var callNumber = components != null ? getString(components, "callNumber") : null;
      var prefix = components != null ? getString(components, "prefix") : null;
      var suffix = components != null ? getString(components, "suffix") : null;
      var typeId = components != null ? getString(components, "typeId") : null;

      var fullCallNumber = CallNumberUtils.getEffectiveCallNumber(prefix, callNumber, suffix);
      var document = documentMap();
      document.put("fullCallNumber", fullCallNumber);
      document.put("callNumber", callNumber);
      document.put("callNumberPrefix", prefix);
      document.put("callNumberSuffix", suffix);
      document.put("callNumberTypeId", typeId);
      if (fullCallNumber != null) {
        addShelvingOrders(document, fullCallNumber, true);
      }
    }

    private void addInstance(String tenantId, boolean shared, String locationId, String instanceId) {
      var bucket = bucket(new CallNumberBucketKey(tenantId, shared, locationId), () -> {
        var map = baseBucket(tenantId, shared);
        map.put("locationId", locationId);
        return map;
      });
      if (instanceId != null) {
        @SuppressWarnings("unchecked")
        var instanceIds = (Set<String>) bucket.computeIfAbsent("instanceId", k -> new HashSet<>());
        instanceIds.add(instanceId);
      }
    }
  }
}
