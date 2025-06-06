package org.folio.search.service.reindex;

import static java.util.function.Function.identity;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Service;

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
    statusService.updateReindexUploadStarted(entityType, uploadRanges.size());
    indexRangeEventProducer.sendMessages(prepareEvents(uploadRanges));
  }

  public Collection<ResourceEvent> fetchRecordRange(ReindexRangeIndexEvent rangeIndexEvent) {
    var entityType = rangeIndexEvent.getEntityType();
    var repository = repositories.get(entityType);
    var recordMaps = repository.fetchByIdRange(rangeIndexEvent.getLower(), rangeIndexEvent.getUpper());
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

  private List<ReindexRangeIndexEvent> prepareEvents(List<UploadRangeEntity> uploadRanges) {
    return uploadRanges.stream()
      .map(range -> {
        var event = new ReindexRangeIndexEvent();
        event.setId(range.getId());
        event.setEntityType(range.getEntityType());
        event.setLower(range.getLower());
        event.setUpper(range.getUpper());
        return event;
      })
      .toList();
  }
}
