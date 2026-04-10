package org.folio.search.controller;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.model.reindex.IndexFamilyEntity;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.QueryVersionResolver;
import org.folio.search.service.browse.V2BrowseFullRebuildService;
import org.folio.search.service.reindex.StreamingReindexService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class IndexFamilyController {

  private final IndexFamilyService indexFamilyService;
  private final StreamingReindexService streamingReindexService;
  private final QueryVersionResolver queryVersionResolver;
  private final V2BrowseFullRebuildService browseFullRebuildService;
  private final FolioExecutionContext context;

  @GetMapping("/search/index/families")
  public ResponseEntity<FamilyListResponse> listFamilies() {
    var tenantId = context.getTenantId();
    var families = indexFamilyService.findAllFamilies(tenantId).stream()
      .map(IndexFamilyController::toDto)
      .toList();
    return ResponseEntity.ok(new FamilyListResponse(families, families.size()));
  }

  @PostMapping("/search/index/reindex/stream")
  public ResponseEntity<ReindexJobResponse> startStreamingReindex(
    @RequestParam(defaultValue = "2") String queryVersion,
    @RequestBody(required = false) IndexSettings indexSettings) {
    var tenantId = context.getTenantId();
    var version = QueryVersion.fromString(queryVersion);
    var job = streamingReindexService.startStreamingReindex(tenantId, version, indexSettings);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
      .body(new ReindexJobResponse(job.jobId().toString(), job.familyId().toString(),
        version.getValue(), "ACCEPTED"));
  }

  @PostMapping("/search/index/families/{id}/resume")
  public ResponseEntity<ReindexJobResponse> resumeStreamingReindex(@PathVariable UUID id) {
    var job = streamingReindexService.resumeStreamingReindex(id);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
      .body(new ReindexJobResponse(job.jobId().toString(), job.familyId().toString(),
        null, "BUILDING"));
  }

  @PostMapping("/search/index/families/{id}/switch-over")
  public ResponseEntity<StatusResponse> switchOver(@PathVariable UUID id) {
    indexFamilyService.switchOver(id);
    return ResponseEntity.ok(new StatusResponse("SWITCHED_OVER", id.toString()));
  }

  @PostMapping("/search/index/families/{id}/retire")
  public ResponseEntity<StatusResponse> retire(@PathVariable UUID id) {
    indexFamilyService.retireFamily(id);
    return ResponseEntity.ok(new StatusResponse("RETIRED", id.toString()));
  }

  @PostMapping("/search/index/families/{id}/rebuild-browse")
  public ResponseEntity<StatusResponse> rebuildBrowse(@PathVariable UUID id) {
    browseFullRebuildService.rebuildBrowseAsync(id);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
      .body(new StatusResponse("REBUILD_BROWSE_STARTED", id.toString()));
  }

  @DeleteMapping("/search/index/families/{id}")
  public ResponseEntity<Void> deleteFailedFamily(@PathVariable UUID id) {
    indexFamilyService.cleanupFailedFamily(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search/index/query-versions")
  public ResponseEntity<QueryVersionListResponse> listQueryVersions() {
    var tenantId = context.getTenantId();
    var defaultVersion = queryVersionResolver.getDefaultVersion();

    var versions = Arrays.stream(QueryVersion.values())
      .map(v -> {
        var activeFamily = indexFamilyService.findActiveFamily(tenantId, v);
        return new QueryVersionDto(
          v.getValue(),
          v.getPathType().name(),
          activeFamily.isPresent(),
          activeFamily.isPresent() ? indexFamilyService.getAliasName(tenantId, v) : null,
          v.getValue().equals(defaultVersion));
      })
      .toList();

    return ResponseEntity.ok(new QueryVersionListResponse(versions, defaultVersion));
  }

  private static FamilyDto toDto(IndexFamilyEntity entity) {
    return new FamilyDto(
      entity.getId().toString(),
      entity.getGeneration(),
      entity.getIndexName(),
      entity.getStatus().getValue(),
      entity.getQueryVersion().getValue(),
      entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
      entity.getActivatedAt() != null ? entity.getActivatedAt().toString() : null,
      entity.getRetiredAt() != null ? entity.getRetiredAt().toString() : null);
  }

  record FamilyDto(String id, int generation, String indexName,
                   String status, String queryVersion,
                   String createdAt, String activatedAt, String retiredAt) { }

  record FamilyListResponse(List<FamilyDto> families, int totalRecords) { }

  record ReindexJobResponse(String jobId, String familyId, String queryVersion, String status) { }

  record StatusResponse(String status, String familyId) { }

  record QueryVersionDto(String version, String pathType, boolean hasActiveFamily,
                         String aliasName, boolean isDefault) { }

  record QueryVersionListResponse(List<QueryVersionDto> versions, String defaultVersion) { }
}
