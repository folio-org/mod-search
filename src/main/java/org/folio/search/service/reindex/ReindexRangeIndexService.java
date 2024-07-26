package org.folio.search.service.reindex;

import static java.util.function.Function.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Service;

@Service
public class ReindexRangeIndexService {

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

  private List<ReindexRangeIndexEvent> prepareEvents(List<UploadRangeEntity> uploadRanges) {
    return uploadRanges.stream()
      .map(range -> {
        var event = new ReindexRangeIndexEvent();
        event.setEntityType(range.getEntityType());
        event.setOffset(range.getOffset());
        event.setLimit(range.getLimit());
        return event;
      })
      .toList();
  }
}
