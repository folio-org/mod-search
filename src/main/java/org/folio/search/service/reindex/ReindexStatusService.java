package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

import java.util.List;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReindexStatusService {

  private final ReindexStatusRepository statusRepository;

  public ReindexStatusService(ReindexStatusRepository statusRepository) {
    this.statusRepository = statusRepository;
  }

  @Transactional
  public void recreateMergeStatusRecords() {
    var statusRecords = constructNewStatusRecords(MERGE_RANGE_ENTITY_TYPES, ReindexStatus.MERGE_IN_PROGRESS);
    statusRepository.truncate();
    statusRepository.saveReindexStatusRecords(statusRecords);
  }

  public void addProcessedMergeRanges(ReindexEntityType entityType, int processedMergeRanges) {
    statusRepository.addReindexCounts(entityType, processedMergeRanges, 0);
  }

  public void addProcessedUploadRanges(ReindexEntityType entityType, int processedUploadRanges) {
    statusRepository.addReindexCounts(entityType, 0, processedUploadRanges);
  }

  public void updateReindexMergeFailed(List<ReindexEntityType> entityTypes) {
    statusRepository.setMergeReindexFailed(entityTypes);
  }

  public void updateReindexMergeFailed() {
    updateReindexMergeFailed(MERGE_RANGE_ENTITY_TYPES);
  }

  public void updateReindexUploadFailed(ReindexEntityType entityType) {
    statusRepository.setReindexUploadFailed(entityType);
  }

  public void updateReindexMergeStarted(ReindexEntityType entityType, int totalMergeRanges) {
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  private List<ReindexStatusEntity> constructNewStatusRecords(List<ReindexEntityType> entityTypes,
                                                              ReindexStatus status) {
    return entityTypes.stream()
      .map(entityType -> new ReindexStatusEntity(entityType, status))
      .toList();
  }
}
