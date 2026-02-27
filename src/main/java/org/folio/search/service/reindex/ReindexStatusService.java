package org.folio.search.service.reindex;

import static java.util.Collections.singletonList;

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

  private final ReindexStatusRepository statusRepository;
  private final ReindexStatusMapper reindexStatusMapper;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  public ReindexStatusService(ReindexStatusRepository statusRepository,
                              ReindexStatusMapper reindexStatusMapper,
                              ConsortiumTenantProvider consortiumTenantProvider) {
    this.statusRepository = statusRepository;
    this.reindexStatusMapper = reindexStatusMapper;
    this.consortiumTenantProvider = consortiumTenantProvider;
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
  public void recreateMergeStatusRecords() {
    log.info("recreateMergeStatusRecords:: recreating status records for reindex merge.");
    var statusRecords =
      constructNewStatusRecords(ReindexEntityType.supportMergeTypes(), ReindexStatus.MERGE_IN_PROGRESS);
    statusRepository.truncate();
    statusRepository.saveReindexStatusRecords(statusRecords);
  }

  @Transactional
  public void recreateUploadStatusRecord(ReindexEntityType entityType) {
    var uploadStatusEntity = new ReindexStatusEntity(entityType, ReindexStatus.UPLOAD_IN_PROGRESS);
    statusRepository.delete(entityType);
    statusRepository.saveReindexStatusRecords(List.of(uploadStatusEntity));
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

  /**
   * Checks if any reindex operation is currently in progress (merge or upload).
   *
   * @return true if any entity type has a status of MERGE_IN_PROGRESS or UPLOAD_IN_PROGRESS
   */
  public boolean isReindexInProgress() {
    return statusRepository.getReindexStatuses().stream()
      .anyMatch(status -> status.getStatus() == ReindexStatus.MERGE_IN_PROGRESS
                          || status.getStatus() == ReindexStatus.UPLOAD_IN_PROGRESS);
  }

  private List<ReindexStatusEntity> constructNewStatusRecords(List<ReindexEntityType> entityTypes,
                                                              ReindexStatus status) {
    return entityTypes.stream()
      .map(entityType -> new ReindexStatusEntity(entityType, status))
      .toList();
  }
}
