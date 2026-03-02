package org.folio.search.service.reindex;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.search.configuration.SearchCacheNames.REINDEX_TARGET_TENANT_CACHE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.converter.ReindexStatusMapper;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReindexStatusService {

  private final ReindexStatusRepository statusRepository;
  private final ReindexStatusMapper reindexStatusMapper;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public List<ReindexStatusItem> getReindexStatuses(String tenantId) {
    if (consortiumTenantProvider.isMemberTenant(tenantId)) {
      throw RequestValidationException.memberTenantNotAllowedException(tenantId);
    }

    var statuses = statusRepository.getReindexStatuses();

    return statuses.stream().map(reindexStatusMapper::convert).toList();
  }

  public Map<ReindexEntityType, ReindexStatus> getStatusesByType() {
    return statusRepository.getReindexStatuses().stream()
      .collect(Collectors.toMap(ReindexStatusEntity::getEntityType, ReindexStatusEntity::getStatus));
  }

  @Transactional
  @CacheEvict(cacheNames = REINDEX_TARGET_TENANT_CACHE, allEntries = true)
  public void recreateMergeStatusRecords(String targetTenantId) {
    log.info("recreateMergeStatusRecords:: recreating status records for reindex merge [targetTenant: {}].",
      targetTenantId);
    var statusRecords =
      constructNewStatusRecords(ReindexEntityType.supportMergeTypes(), targetTenantId);
    statusRepository.truncate();
    statusRepository.recreateReindexStatusTrigger(isNotBlank(targetTenantId));
    statusRepository.saveReindexStatusRecords(statusRecords);
  }

  @Transactional
  public void recreateUploadStatusRecord(ReindexEntityType entityType, String targetTenantId) {
    var uploadStatusEntity = new ReindexStatusEntity(entityType, ReindexStatus.UPLOAD_IN_PROGRESS);
    uploadStatusEntity.setTargetTenantId(targetTenantId);
    statusRepository.delete(entityType);
    statusRepository.saveReindexStatusRecords(List.of(uploadStatusEntity));
    
    log.debug("recreateUploadStatusRecord:: created upload record [entityType: {}, targetTenant: {}]", 
      entityType, targetTenantId);
  }

  public void addProcessedMergeRanges(ReindexEntityType entityType, int processedMergeRanges) {
    statusRepository.addReindexCounts(entityType, processedMergeRanges, 0);
  }

  public void addProcessedUploadRanges(ReindexEntityType entityType, int processedUploadRanges) {
    statusRepository.addReindexCounts(entityType, 0, processedUploadRanges);
  }

  public void updateReindexMergeFailed() {
    var entityTypes = ReindexEntityType.supportMergeTypes();
    log.info("updateReindexMergeFailed:: for [entityTypes: {}]", entityTypes);
    statusRepository.setMergeReindexFailed(entityTypes);
  }

  public void updateReindexMergeFailed(ReindexEntityType entityType) {
    log.info("updateReindexMergeFailed:: for [entityType: {}]", entityType);
    statusRepository.setMergeReindexFailed(singletonList(entityType));
  }

  public void updateReindexUploadFailed(ReindexEntityType entityType) {
    log.info("updateReindexUploadFailed:: for [entityType: {}]", entityType);
    statusRepository.setReindexUploadFailed(entityType);
  }

  public void updateStagingStarted() {
    var entityTypes = ReindexEntityType.supportMergeTypes();
    log.info("updateStagingStarted:: setting staging start time for [entityTypes: {}]", entityTypes);
    statusRepository.setStagingStarted(entityTypes);
  }

  public void updateStagingCompleted() {
    var entityTypes = ReindexEntityType.supportMergeTypes();
    log.info("updateStagingCompleted:: setting staging end time for [entityTypes: {}]", entityTypes);
    statusRepository.setStagingCompleted(entityTypes);
  }

  public void updateStagingFailed() {
    var entityTypes = ReindexEntityType.supportMergeTypes();
    log.info("updateStagingFailed:: setting status to STAGING_FAILED and end time for [entityTypes: {}]", entityTypes);
    statusRepository.setStagingFailed(entityTypes);
  }

  public void updateReindexMergeStarted(ReindexEntityType entityType, int totalMergeRanges) {
    log.info("updateReindexMergeStarted:: for [entityType: {}, totalMergeRanges: {}]", entityType, totalMergeRanges);
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  public void updateReindexMergeInProgress(Set<ReindexEntityType> entityTypes) {
    log.info("updateReindexMergeInProgress:: for [entityTypes: {}]", entityTypes);
    statusRepository.setMergeInProgress(entityTypes);
  }

  public void updateReindexUploadStarted(ReindexEntityType entityType, int totalUploadRanges) {
    log.info("updateReindexUploadStarted:: for [entityType: {}, totalUploadRanges: {}]", entityType, totalUploadRanges);
    statusRepository.setUploadReindexStarted(entityType, totalUploadRanges);
  }

  public boolean isMergeCompleted() {
    return statusRepository.isMergeCompleted();
  }

  /**
   * Checks if any reindex operation is currently in progress (merge, upload or staging).
   *
   * @return true if any entity type has a status of MERGE_IN_PROGRESS, UPLOAD_IN_PROGRESS or STAGING_IN_PROGRESS
   */
  public boolean isReindexInProgress() {
    return statusRepository.getReindexStatuses().stream()
      .anyMatch(status -> status.getStatus() == ReindexStatus.MERGE_IN_PROGRESS
                          || status.getStatus() == ReindexStatus.UPLOAD_IN_PROGRESS
                          || status.getStatus() == ReindexStatus.STAGING_IN_PROGRESS);
  }

  private List<ReindexStatusEntity> constructNewStatusRecords(List<ReindexEntityType> entityTypes,
                                                              String targetTenantId) {
    return entityTypes.stream()
      .map(entityType -> {
        var entity = new ReindexStatusEntity(entityType, ReindexStatus.MERGE_IN_PROGRESS);
        entity.setTargetTenantId(targetTenantId);
        return entity;
      })
      .toList();
  }

  /**
   * Gets the target tenant ID for the current reindex operation with caching.
   * Cache has 10-second TTL configured in CacheConfiguration to handle high-volume Kafka events efficiently.
   * Since only one reindex runs at a time per tenant, cached value remains valid for the operation duration.
   *
   * @return the target tenant ID if this is a tenant-specific reindex, null for full consortium reindex
   */
  @Cacheable(cacheNames = REINDEX_TARGET_TENANT_CACHE, key = "@folioExecutionContext.tenantId")
  public String getTargetTenantId() {
    try {
      return statusRepository.getTargetTenantId();
    } catch (Exception e) {
      log.debug("getTargetTenantId:: error retrieving target tenant ID: {}", e.getMessage());
      return null;
    }
  }
}
