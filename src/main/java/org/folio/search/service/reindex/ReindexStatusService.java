package org.folio.search.service.reindex;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.converter.ReindexStatusMapper;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class ReindexStatusService {

  private static final long CACHE_TTL_MS = 10_000; // 10 seconds

  private final ReindexStatusRepository statusRepository;
  private final ReindexStatusMapper reindexStatusMapper;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  // Caching for targetTenantId to avoid DB hits for every Kafka event
  private volatile String cachedTargetTenantId;
  private volatile long cacheTimestamp;

  public ReindexStatusService(ReindexStatusRepository statusRepository,
                              ReindexStatusMapper reindexStatusMapper,
                              ConsortiumTenantProvider consortiumTenantProvider) {
    this.statusRepository = statusRepository;
    this.reindexStatusMapper = reindexStatusMapper;
    this.consortiumTenantProvider = consortiumTenantProvider;
    this.cachedTargetTenantId = null;
    this.cacheTimestamp = 0;
  }

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
  public void recreateMergeStatusRecords(String targetTenantId) {
    log.info("recreateMergeStatusRecords:: recreating status records for reindex merge [targetTenant: {}].",
      targetTenantId);
    var statusRecords =
      constructNewStatusRecords(ReindexEntityType.supportMergeTypes(), targetTenantId);
    statusRepository.truncate();
    statusRepository.recreateReindexStatusTrigger(isNotBlank(targetTenantId));
    statusRepository.saveReindexStatusRecords(statusRecords);

    // Clear cache when starting new reindex to force fresh data
    cachedTargetTenantId = null;
  }

  @Transactional
  public void recreateUploadStatusRecord(ReindexEntityType entityType) {
    // Preserve the target_tenant_id from the merge phase
    String currentTargetTenantId = null;
    try {
      currentTargetTenantId = statusRepository.getTargetTenantId();
    } catch (Exception e) {
      log.debug("recreateUploadStatusRecord:: could not retrieve existing target tenant ID: {}", e.getMessage());
    }
    
    var uploadStatusEntity = new ReindexStatusEntity(entityType, ReindexStatus.UPLOAD_IN_PROGRESS);
    uploadStatusEntity.setTargetTenantId(currentTargetTenantId);
    statusRepository.delete(entityType);
    statusRepository.saveReindexStatusRecords(List.of(uploadStatusEntity));
    
    log.debug("recreateUploadStatusRecord:: created upload record [entityType: {}, targetTenant: {}]", 
      entityType, currentTargetTenantId);
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

  public void updateReindexMergeStarted(ReindexEntityType entityType, int totalMergeRanges) {
    log.info("updateReindexMergeStarted:: for [entityType: {}, totalMergeRanges: {}]", entityType, totalMergeRanges);
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  public void updateReindexMergeInProgress(Set<ReindexEntityType> entityTypes) {
    log.info("updateReindexMergeInProgress:: for [entityTypes: {}]", entityTypes);
    statusRepository.setMergeInProgress(entityTypes);
  }

  public void updateReindexUploadStarted(ReindexEntityType entityType, int totalUploadRanges) {
    log.info("updateReindexUploadStarted:: for [entityType: {}, totalMergeRanges: {}]", entityType, totalUploadRanges);
    statusRepository.setUploadReindexStarted(entityType, totalUploadRanges);
  }

  public boolean isMergeCompleted() {
    return statusRepository.isMergeCompleted();
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
   * Since only one reindex runs at a time, the cached value is valid for the entire operation.
   * Cache has 5-second TTL to handle high-volume Kafka events efficiently.
   *
   * @return the target tenant ID if this is a tenant-specific reindex, null for full consortium reindex
   */
  public String getTargetTenantId() {
    long now = System.currentTimeMillis();
    // Use cache if still valid
    if (cachedTargetTenantId != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
      return cachedTargetTenantId.isEmpty() ? null : cachedTargetTenantId; // empty string means null
    }

    // Fetch from DB and cache (store empty string for null to distinguish from not-cached)
    try {
      var dbValue = statusRepository.getTargetTenantId();
      cachedTargetTenantId = dbValue == null ? "" : dbValue;
      cacheTimestamp = now;
      return dbValue;
    } catch (Exception e) {
      log.debug("getTargetTenantId:: error retrieving target tenant ID: {}", e.getMessage());
      // Cache the null result to avoid repeated failed queries
      cachedTargetTenantId = "";
      cacheTimestamp = now;
      return null;
    }
  }
}
