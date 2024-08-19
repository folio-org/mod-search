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
import org.folio.search.converter.ReindexStatusMapper;
import org.folio.search.domain.dto.ReindexStatusItem;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.reindex.jdbc.ReindexStatusRepository;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.stereotype.Service;

@Service
public class ReindexUploadRangeIndexService {

  static final String REQUEST_NOT_ALLOWED_MSG =
    "The request not allowed for member tenant of consortium environment";

  private final Map<ReindexEntityType, UploadRangeRepository> repositories;
  private final FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private final ReindexStatusRepository statusRepository;
  private final ReindexStatusMapper reindexStatusMapper;
  private final ConsortiumTenantService consortiumTenantService;

  public ReindexUploadRangeIndexService(List<UploadRangeRepository> repositories,
                                        FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer,
                                        ReindexStatusRepository statusRepository,
                                        ReindexStatusMapper reindexStatusMapper,
                                        ConsortiumTenantService consortiumTenantService) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(UploadRangeRepository::entityType, identity()));
    this.indexRangeEventProducer = indexRangeEventProducer;
    this.statusRepository = statusRepository;
    this.reindexStatusMapper = reindexStatusMapper;
    this.consortiumTenantService = consortiumTenantService;
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
        .resourceName(ReindexConstants.RESOURCE_NAME_MAP.get(entityType))
        ._new(map)
        .tenant(rangeIndexEvent.getTenant()))
      .toList();
  }

  public void updateFinishDate(ReindexRangeIndexEvent event) {
    var repository = repositories.get(event.getEntityType());
    repository.setIndexRangeFinishDate(event.getId(), Timestamp.from(Instant.now()));
  }

  public List<ReindexStatusItem> getReindexStatuses(String tenantId) {
    if (consortiumTenantService.isMemberTenantInConsortium(tenantId)) {
      throw new RequestValidationException(REQUEST_NOT_ALLOWED_MSG, XOkapiHeaders.TENANT, tenantId);
    }

    var statuses = statusRepository.getReindexStatuses();

    return statuses.stream().map(reindexStatusMapper::convert).toList();
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
