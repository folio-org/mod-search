package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

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
    var statusRecords = constructNewStatusRecords(MERGE_RANGE_ENTITY_TYPES, ReindexStatus.MERGE_IN_PROGRESS);
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
    log.info("updateReindexMergeFailed:: for [entityTypes: {}]", MERGE_RANGE_ENTITY_TYPES);
    statusRepository.setMergeReindexFailed(MERGE_RANGE_ENTITY_TYPES);
  }

  public void updateReindexUploadFailed(ReindexEntityType entityType) {
    log.info("updateReindexUploadFailed:: for [entityType: {}]", entityType);
    statusRepository.setReindexUploadFailed(entityType);
  }

  public void updateReindexMergeStarted(ReindexEntityType entityType, int totalMergeRanges) {
    log.info("updateReindexMergeStarted:: for [entityType: {}, totalMergeRanges: {}]", entityType, totalMergeRanges);
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  public void updateReindexUploadStarted(ReindexEntityType entityType, int totalUploadRanges) {
    log.info("updateReindexUploadStarted:: for [entityType: {}, totalMergeRanges: {}]", entityType, totalUploadRanges);
    statusRepository.setUploadReindexStarted(entityType, totalUploadRanges);
  }

  public boolean isMergeCompleted() {
    return statusRepository.isMergeCompleted();
  }

  private List<ReindexStatusEntity> constructNewStatusRecords(Set<ReindexEntityType> entityTypes,
                                                              ReindexStatus status) {
    return entityTypes.stream()
      .map(entityType -> new ReindexStatusEntity(entityType, status))
      .toList();
  }
}
