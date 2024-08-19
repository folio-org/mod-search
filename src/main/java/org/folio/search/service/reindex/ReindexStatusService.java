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

  public void updateMergeRangesStarted(ReindexEntityType entityType, int totalMergeRanges) {
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  public void updateMergeRangesFailed(List<ReindexEntityType> entityTypes) {
    statusRepository.setMergeReindexFailed(entityTypes);
  }

  public void updateMergeRangesFailed() {
    updateMergeRangesFailed(MERGE_RANGE_ENTITY_TYPES);
  }

  private List<ReindexStatusEntity> constructNewStatusRecords(List<ReindexEntityType> entityTypes,
                                                              ReindexStatus status) {
    return entityTypes.stream()
      .map(entityType -> new ReindexStatusEntity(entityType, status))
      .toList();
  }
}
