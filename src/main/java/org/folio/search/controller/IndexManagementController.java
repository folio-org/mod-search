package org.folio.search.controller;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.CreateIndexRequest;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ReindexFullRequest;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.UpdateIndexDynamicSettingsRequest;
import org.folio.search.domain.dto.UpdateMappingsRequest;
import org.folio.search.model.reconciliation.ReconciliationReport;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.rest.resource.IndexManagementApi;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.IndexService;
import org.folio.search.service.ReconciliationService;
import org.folio.search.service.ResourceService;
import org.folio.search.service.catchup.CatchUpPhaseManager;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.ReindexService;
import org.folio.search.service.reindex.ReindexStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller with set of endpoints for manipulating with Elasticsearch index API.
 */
@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class IndexManagementController implements IndexManagementApi {

  private final IndexService indexService;
  private final ResourceService resourceService;
  private final ReindexService reindexService;
  private final ReindexStatusService reindexStatusService;
  private final EgressExecutionContextService executionService;
  private final IndexRepository indexRepository;
  private final IndexNameProvider indexNameProvider;
  private final ResourceDescriptionService resourceDescriptionService;
  private final ReconciliationService reconciliationService;

  @Nullable
  @Autowired(required = false)
  private CatchUpPhaseManager catchUpPhaseManager;

  @Override
  public ResponseEntity<FolioCreateIndexResponse> createIndices(String tenantId, CreateIndexRequest request) {
    return ResponseEntity.ok(indexService.createIndex(ResourceType.byName(request.getResourceName()), tenantId));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> indexRecords(String tenantId, List<ResourceEvent> events) {
    executionService.execute(tenantId, () -> resourceService.indexResources(events));
    return ResponseEntity.ok(resourceService.indexResources(events));
  }

  @Override
  public ResponseEntity<Void> reindexInstanceRecords(String tenantId, ReindexFullRequest reindexFullRequest) {
    var targetTenantId = reindexFullRequest != null && isNotBlank(reindexFullRequest.getTenantId())
      ? reindexFullRequest.getTenantId()
      : null;
    var indexSettings = reindexFullRequest != null ? reindexFullRequest.getIndexSettings() : null;

    log.info("Attempting to run full-reindex for instance records [requestingTenant: {}, targetTenant: {}]",
      tenantId, targetTenantId != null ? targetTenantId : "all consortium members");

    reindexService.submitFullReindex(tenantId, indexSettings, targetTenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> reindexUploadInstanceRecords(String tenantId, ReindexUploadDto reindexUploadDto) {
    reindexService.submitUploadReindex(tenantId, reindexUploadDto);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> reindexFailedMergeRanges(String tenantId) {
    reindexService.submitFailedMergeRangesReindex(tenantId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<ReindexJob> reindexInventoryRecords(String tenantId, ReindexRequest request) {
    log.info("Attempting to start reindex for inventory [tenant: {}]", tenantId);
    return ResponseEntity.ok(indexService.reindexInventory(tenantId, request));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateIndexDynamicSettings(
    String tenantId, UpdateIndexDynamicSettingsRequest request) {
    return ResponseEntity.ok(indexService.updateIndexSettings(ResourceType.byName(request.getResourceName()), tenantId,
      request.getIndexSettings()));
  }

  @Override
  public ResponseEntity<FolioIndexOperationResponse> updateMappings(String tenantId, UpdateMappingsRequest request) {
    return ResponseEntity.ok(indexService.updateMappings(ResourceType.byName(request.getResourceName()), tenantId));
  }

  @Override
  public ResponseEntity<List<ReindexStatusItem>> getReindexStatus(String tenantId) {
    return ResponseEntity.ok(reindexStatusService.getReindexStatuses(tenantId));
  }

  /**
   * Returns alias → concrete-index mapping for every resource type of the given tenant.
   * Returns an empty map for resource types that have no alias (bare-index deployments).
   */
  /**
   * Compares document counts in OpenSearch and row counts in key Postgres browse tables
   * between the baseline (v1) and the current (v2) instance for the given tenant.
   * Intended to be run during the maintenance window before cutover to confirm consistency.
   *
   * <p>Overall status:
   * <ul>
   *   <li>MATCH – all counts are equal</li>
   *   <li>MISMATCH – at least one count differs</li>
   *   <li>ERROR – at least one check could not be completed (index/table may not exist yet)</li>
   * </ul>
   */
  @GetMapping("/search/index/reconciliation/{tenantId}")
  public ResponseEntity<ReconciliationReport> reconcile(@PathVariable String tenantId) {
    log.info("reconcile:: request [tenantId: {}]", tenantId);
    return ResponseEntity.ok(reconciliationService.reconcile(tenantId));
  }

  /**
   * Atomically switches OpenSearch aliases for all resource types of the given tenant
   * from their current concrete index to the concrete index at {@code toVersion}.
   * The target concrete index must already exist and be fully populated before calling this.
   *
   * <p>Example: with INDEX_SUFFIX=_v2 and toVersion=1, switches
   * alias {@code folio_instance_diku_v2} from {@code folio_instance_diku_v2_0}
   * to {@code folio_instance_diku_v2_1}.
   *
   * @param tenantId  the tenant whose aliases should be switched
   * @param toVersion the numeric version suffix of the target concrete index
   */
  @PostMapping("/search/index/aliases/{tenantId}/switch")
  public ResponseEntity<Map<String, String>> switchAlias(@PathVariable String tenantId,
                                                         @RequestParam int toVersion) {
    log.info("switchAlias:: request [tenantId: {}, toVersion: {}]", tenantId, toVersion);
    return ResponseEntity.ok(indexService.switchAlias(tenantId, toVersion));
  }

  /**
   * Returns the alias → concrete-index mapping for every resource type of the given tenant.
   * Returns {@code "(no alias)"} for resource types that use a bare index (alias mode disabled).
   */
  @GetMapping("/search/index/aliases/{tenantId}")
  public ResponseEntity<Map<String, String>> getAliasStatus(@PathVariable String tenantId) {
    var aliases = resourceDescriptionService.getResourceTypes().stream()
      .collect(Collectors.toMap(
        ResourceType::getName,
        resourceType -> {
          var aliasName = indexNameProvider.getIndexName(resourceType, tenantId);
          return indexRepository.getAliasWriteIndex(aliasName)
            .map(concrete -> aliasName + " -> " + concrete)
            .orElse(aliasName + " (no alias)");
        }
      ));
    return ResponseEntity.ok(aliases);
  }

  /**
   * Returns the current phase of the v2 background indexer.
   * Only meaningful when the instance is deployed with {@code CATCH_UP_ENABLED=true}.
   *
   * <p>Phases:
   * <ul>
   *   <li>catch-up-enabled: false — standard v1 deployment, no phase tracking</li>
   *   <li>phase: REINDEXING — full reindex (Phase 1) in progress, live listeners paused</li>
   *   <li>phase: CATCHING_UP — real-time catch-up (Phase 2) active, all live listeners running</li>
   * </ul>
   */
  @GetMapping("/search/index/catch-up/status")
  public ResponseEntity<Map<String, Object>> getCatchUpStatus() {
    if (catchUpPhaseManager == null) {
      return ResponseEntity.ok(Map.of("catch-up-enabled", false));
    }
    var phase = catchUpPhaseManager.isCatchUpActive() ? "CATCHING_UP" : "REINDEXING";
    return ResponseEntity.ok(Map.of(
      "catch-up-enabled", true,
      "phase", phase
    ));
  }

  /**
   * Stops all real-time catch-up Kafka listeners. Call this at the start of the Saturday
   * maintenance window, after Kafka consumer lag has reached zero, before running the
   * reconciliation check. Only meaningful when {@code CATCH_UP_ENABLED=true}.
   *
   * @return 204 if listeners were stopped, 409 if catch-up mode is not active
   */
  @DeleteMapping("/search/index/catch-up/stop")
  public ResponseEntity<Void> stopCatchUp() {
    if (catchUpPhaseManager == null || !catchUpPhaseManager.isCatchUpActive()) {
      return ResponseEntity.status(409).build();
    }
    log.info("stopCatchUp:: Stopping real-time catch-up listeners");
    catchUpPhaseManager.stop();
    return ResponseEntity.noContent().build();
  }
}
