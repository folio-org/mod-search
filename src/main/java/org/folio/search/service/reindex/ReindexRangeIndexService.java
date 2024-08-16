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
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Service;

@Service
public class ReindexRangeIndexService {

  private static final Map<ReindexEntityType, String> RESOURCE_NAME_MAP = Map.of(
    ReindexEntityType.INSTANCE, ResourceType.INSTANCE.getName(),
    ReindexEntityType.SUBJECT, ResourceType.INSTANCE_SUBJECT.getName(),
    ReindexEntityType.CLASSIFICATION, ResourceType.INSTANCE_CLASSIFICATION.getName(),
    ReindexEntityType.CONTRIBUTOR, ResourceType.INSTANCE_CONTRIBUTOR.getName()
  );

  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;
  private final FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;

  public ReindexRangeIndexService(List<ReindexJdbcRepository> repositories,
                                  FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
    this.indexRangeEventProducer = indexRangeEventProducer;
  }

  public void prepareAndSendIndexRanges(ReindexEntityType entityType) {
    var repository = Optional.ofNullable(repositories.get(entityType))
      .orElseThrow(() -> new UnsupportedOperationException("No repository found for entity type: " + entityType));

    var uploadRanges = repository.getUploadRanges(true);
    indexRangeEventProducer.sendMessages(prepareEvents(uploadRanges));
  }

  public Collection<ResourceEvent> fetchRecordRange(ReindexRangeIndexEvent rangeIndexEvent) {
    var entityType = rangeIndexEvent.getEntityType();
    var repository = repositories.get(entityType);
    var recordMaps = repository.fetchBy(rangeIndexEvent.getLimit(), rangeIndexEvent.getOffset());
    return recordMaps.stream()
      .map(map -> new ResourceEvent().id(getString(map, ID_FIELD))
        .resourceName(RESOURCE_NAME_MAP.get(entityType))
        ._new(map)
        .tenant(rangeIndexEvent.getTenant()))
      .toList();
  }

  public void updateFinishDate(ReindexRangeIndexEvent event) {
    var repository = repositories.get(event.getEntityType());
    repository.setIndexRangeFinishDate(event.getId(), Timestamp.from(Instant.now()));
  }

  private List<ReindexRangeIndexEvent> prepareEvents(List<UploadRangeEntity> uploadRanges) {
    return uploadRanges.stream()
      .map(range -> {
        var event = new ReindexRangeIndexEvent();
        event.setId(range.getId());
        event.setEntityType(range.getEntityType());
        event.setOffset(range.getOffset());
        event.setLimit(range.getLimit());
        return event;
      })
      .toList();
  }
}
