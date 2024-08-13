package org.folio.search.service.reindex;

import java.util.Arrays;
import java.util.List;
import org.folio.search.model.reindex.ReindexStatusEntity;
import org.folio.search.model.types.ReindexEntityType;
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
  public void recreateStatusRecords() {
    var statusRecords = constructNewStatusRecords();
    statusRepository.truncate();
    statusRepository.saveReindexStatusRecords(statusRecords);
  }

  public void updateMergeRangesStarted(ReindexEntityType entityType, int totalMergeRanges) {
    statusRepository.setMergeReindexStarted(entityType, totalMergeRanges);
  }

  public void updateMergeRangesFailed(ReindexEntityType entityType) {
    statusRepository.setReindexMergeFailed(entityType);
  }

  private List<ReindexStatusEntity> constructNewStatusRecords() {
    return Arrays.stream(ReindexEntityType.values())
      .map(ReindexStatusEntity::new)
      .toList();
  }
}
