package org.folio.search.service.ingest;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.repository.InstanceSearchResourceRepository;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.springframework.stereotype.Service;

/**
 * Unified pipeline for indexing flat instance search documents.
 * Used by both realtime Kafka events and streaming reindex.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceSearchIndexingPipeline {

  private final InstanceSearchEnrichmentService enrichmentService;
  private final InstanceSearchDocumentConverter documentConverter;
  private final InstanceSearchResourceRepository resourceRepository;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  /**
   * Index a resource event to an already-resolved flat target index or alias while preserving the source tenant on the
   * indexed document.
   */
  public void indexFromEvent(String resourceType, Map<String, Object> eventPayload,
                             ResourceEventType eventType, String sourceTenantId, String targetIndexName) {
    processRecord(resourceType, eventPayload, eventType, sourceTenantId, targetIndexName);
  }

  /**
   * Index a record to an explicit physical index name (used by streaming reindex and temporary consumer).
   */
  public void indexToFamily(String resourceType, Map<String, Object> rawRecord,
                            String tenantId, String targetIndexName) {
    indexToFamily(resourceType, rawRecord, ResourceEventType.CREATE, tenantId, targetIndexName);
  }

  public void indexToFamily(String resourceType, Map<String, Object> rawRecord, ResourceEventType eventType,
                            String tenantId, String targetIndexName) {
    processRecord(resourceType, rawRecord, eventType, tenantId, targetIndexName);
  }

  /**
   * Batch index records to an explicit physical index name (used by streaming reindex).
   */
  public BatchProfiling indexBatchToFamily(String resourceType, List<Map<String, Object>> records,
                                           String tenantId, String targetIndexName) {
    var shared = consortiumTenantProvider.isCentralTenant(tenantId);
    long enrichNs = 0;
    long convertNs = 0;
    var t0 = System.nanoTime();
    var documents = new java.util.ArrayList<org.folio.search.model.index.InstanceSearchDocumentBody>(records.size());
    for (var record : records) {
      var te0 = System.nanoTime();
      var enriched = enrichmentService.enrich(record, resourceType, tenantId, shared);
      var te1 = System.nanoTime();
      var doc = documentConverter.convert(enriched, targetIndexName);
      var te2 = System.nanoTime();
      enrichNs += te1 - te0;
      convertNs += te2 - te1;
      documents.add(doc);
    }
    var t1 = System.nanoTime();

    if (!documents.isEmpty()) {
      resourceRepository.indexResources(documents);
    }
    var t2 = System.nanoTime();
    var profiling = new BatchProfiling(
      (t2 - t0) / 1_000_000,
      enrichNs / 1_000_000,
      convertNs / 1_000_000,
      (t2 - t1) / 1_000_000,
      documents.size());
    log.info("indexBatchToFamily:: [resource: {}, enrich: {}ms, convert: {}ms, osBulk: {}ms, docs: {}]",
      resourceType, profiling.enrichMs(), profiling.convertMs(), profiling.osBulkMs(), profiling.docs());
    return profiling;
  }

  public void logEnrichmentProfilingSummary() {
    enrichmentService.logProfilingSummary();
  }

  private void processRecord(String resourceType, Map<String, Object> record,
                              ResourceEventType eventType, String tenantId, String targetIndex) {
    if (eventType == ResourceEventType.DELETE) {
      handleDelete(record, resourceType, tenantId, targetIndex);
      return;
    }

    var shared = consortiumTenantProvider.isCentralTenant(tenantId);
    var enriched = enrichmentService.enrich(record, resourceType, tenantId, shared);
    var documentBody = documentConverter.convert(enriched, targetIndex);
    resourceRepository.indexResources(List.of(documentBody));
  }

  private void handleDelete(Map<String, Object> record, String resourceType, String tenantId, String targetIndex) {
    var id = (String) record.get("id");
    var instanceId = resolveInstanceIdForDelete(record, resourceType, id);
    var sourceVersion = resolveSourceVersionForDelete(record);

    var deleteBody = documentConverter.convertForDelete(id, instanceId, tenantId, targetIndex, sourceVersion);
    resourceRepository.indexResources(List.of(deleteBody));
  }

  private String resolveInstanceIdForDelete(Map<String, Object> record, String resourceType, String id) {
    return switch (resourceType) {
      case "instance" -> id;
      case "holding", "item" -> (String) record.get("instanceId");
      default -> id;
    };
  }

  private long resolveSourceVersionForDelete(Map<String, Object> record) {
    var version = record.get("_version");
    if (version instanceof Number num) {
      return num.longValue();
    }
    return System.currentTimeMillis();
  }

  public record BatchProfiling(long batchElapsedMs, long enrichMs, long convertMs, long osBulkMs, int docs) {
  }
}
