package org.folio.search.service.reindex;

import static java.util.function.Function.identity;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.service.reindex.StagingMigrationService.RESOURCE_REINDEX_TIMESTAMP;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexUploadRangeIndexService {

  private final Map<ReindexEntityType, UploadRangeRepository> repositories;
  private final FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private final ReindexStatusService statusService;

  public ReindexUploadRangeIndexService(List<UploadRangeRepository> repositories,
                                        FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer,
                                        ReindexStatusService statusService) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(UploadRangeRepository::entityType, identity()));
    this.indexRangeEventProducer = indexRangeEventProducer;
    this.statusService = statusService;
  }

  public void prepareAndSendIndexRanges(ReindexEntityType entityType) {
    var repository = Optional.ofNullable(repositories.get(entityType))
      .orElseThrow(() -> new UnsupportedOperationException("No repository found for entity type: " + entityType));

    var uploadRanges = repository.createUploadRanges();

    // For member tenant reindex of instances, add member tenant ID to the events
    if (ReindexContext.isMemberTenantReindex() && entityType == ReindexEntityType.INSTANCE) {
      var memberTenantId = ReindexContext.getMemberTenantId();
      updateStatusAndSendEvents(entityType, uploadRanges.size(), memberTenantId, uploadRanges);
    } else {
      updateStatusAndSendEvents(entityType, uploadRanges.size(), uploadRanges);
    }
  }

  public Collection<ResourceEvent> fetchRecordRange(ReindexRangeIndexEvent rangeIndexEvent) {
    var entityType = rangeIndexEvent.getEntityType();
    var repository = repositories.get(entityType);

    List<Map<String, Object>> recordMaps;
    if (rangeIndexEvent.getMemberTenantId() != null) {
      // Use timestamp-filtered range query for member tenant child resource reindex
      recordMaps = repository.fetchByIdRangeWithTimestamp(
        rangeIndexEvent.getLower(),
        rangeIndexEvent.getUpper(),
        RESOURCE_REINDEX_TIMESTAMP);
      log.debug(
        "fetchRecordRange:: Fetched {} records for consortium member reindex [entityType: {}, member tenant: {}]",
        recordMaps.size(), entityType, rangeIndexEvent.getMemberTenantId());
    } else {
      // Use regular ID range query for standard reindex
      recordMaps = repository.fetchByIdRange(rangeIndexEvent.getLower(), rangeIndexEvent.getUpper());
      log.debug("fetchRecordRange:: Fetched {} records using standard ID range [entityType: {}]",
        recordMaps.size(), entityType);
    }

    return recordMaps.stream()
      .map(map -> new ResourceEvent().id(getString(map, ID_FIELD))
        .resourceName(ReindexConstants.RESOURCE_NAME_MAP.get(entityType).getName())
        ._new(map)
        .tenant(rangeIndexEvent.getTenant()))
      .toList();
  }

  public void updateStatus(ReindexRangeIndexEvent event, ReindexRangeStatus status, String failCause) {
    var repository = repositories.get(event.getEntityType());
    repository.updateRangeStatus(event.getId(), Timestamp.from(Instant.now()), status, failCause);
  }

  private void updateStatusAndSendEvents(ReindexEntityType entityType, int rangeCount,
                                         List<UploadRangeEntity> rangeEntities) {
    updateStatusAndSendEvents(entityType, rangeCount, null, rangeEntities);
  }

  private void updateStatusAndSendEvents(ReindexEntityType entityType, int rangeCount, String memberTenantId,
                                         List<UploadRangeEntity> rangeEntities) {
    statusService.updateReindexUploadStarted(entityType, rangeCount);
    var events = prepareEvents(memberTenantId, rangeEntities);
    indexRangeEventProducer.sendMessages(events);
  }

  private List<ReindexRangeIndexEvent> prepareEvents(String memberTenantId, List<UploadRangeEntity> uploadRanges) {
    return uploadRanges.stream()
      .map(range -> {
        var event = new ReindexRangeIndexEvent();
        event.setId(range.getId());
        event.setEntityType(range.getEntityType());
        event.setLower(range.getLower());
        event.setUpper(range.getUpper());
        event.setTenant(memberTenantId);
        return event;
      })
      .toList();
  }
}
