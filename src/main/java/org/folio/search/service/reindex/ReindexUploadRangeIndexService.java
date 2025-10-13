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
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ReindexRangeIndexEvent;
import org.folio.search.model.reindex.UploadRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.ResourceService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexUploadRangeIndexService {

  private final Map<ReindexEntityType, UploadRangeRepository> repositories;
  private final FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer;
  private final ReindexStatusService statusService;
  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;
  private final ResourceService resourceService;
  private final ConsortiumTenantService consortiumTenantService;

  public ReindexUploadRangeIndexService(List<UploadRangeRepository> repositories,
                                        FolioMessageProducer<ReindexRangeIndexEvent> indexRangeEventProducer,
                                        ReindexStatusService statusService,
                                        JdbcTemplate jdbcTemplate,
                                        FolioExecutionContext context,
                                        ResourceService resourceService,
                                        ConsortiumTenantService consortiumTenantService) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(UploadRangeRepository::entityType, identity()));
    this.indexRangeEventProducer = indexRangeEventProducer;
    this.statusService = statusService;
    this.jdbcTemplate = jdbcTemplate;
    this.context = context;
    this.resourceService = resourceService;
    this.consortiumTenantService = consortiumTenantService;
  }

  public void prepareAndSendIndexRanges(ReindexEntityType entityType) {
    var repository = Optional.ofNullable(repositories.get(entityType))
      .orElseThrow(() -> new UnsupportedOperationException("No repository found for entity type: " + entityType));

    // For member tenant reindex of child resources, use timestamp-based approach instead of ranges
    if (ReindexContext.isMemberTenantReindex() && isChildResource(entityType)) {
      prepareAndSendTimestampBasedIndexing(entityType, repository);
      return;
    }

    var uploadRanges = repository.createUploadRanges();
    updateStatusAndSendEvents(entityType, uploadRanges.size(), prepareEvents(uploadRanges));
  }

  public Collection<ResourceEvent> fetchRecordRange(ReindexRangeIndexEvent rangeIndexEvent) {
    var entityType = rangeIndexEvent.getEntityType();
    var repository = repositories.get(entityType);

    List<Map<String, Object>> recordMaps;
    if (rangeIndexEvent.isTimestampBasedRange() && rangeIndexEvent.getTimestampFilter() != null) {
      // Use timestamp-filtered range query for member tenant child resource reindex
      recordMaps = repository.fetchByIdRangeWithTimestamp(
        rangeIndexEvent.getLower(),
        rangeIndexEvent.getUpper(),
        rangeIndexEvent.getTimestampFilter());
      log.debug("fetchRecordRange:: Fetched {} records using timestamp-based range [entityType: {}, timestamp: {}]",
        recordMaps.size(), entityType, rangeIndexEvent.getTimestampFilter());
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


  private void prepareAndSendTimestampBasedIndexing(ReindexEntityType entityType, UploadRangeRepository repository) {
    log.info("prepareAndSendTimestampBasedIndexing:: Using timestamp-based upload for child resource "
      + "[entityType: {}]", entityType);

    try {
      // Child resources don't participate in merge phase, so they don't have merge start times
      // Use the earliest merge start time from entities that did participate in merge
      var mergeStartTime = statusService.getEarliestMergeStartTime();
      if (mergeStartTime == null) {
        log.warn("prepareAndSendTimestampBasedIndexing:: No earliest merge start time found [entityType: {}], "
          + "skipping", entityType);
        updateStatusAndSendEvents(entityType, 0, List.of());
        return;
      }

      var memberTenantId = ReindexContext.getMemberTenantId();
      var uploadRanges = repository.createUploadRanges();
      var timestampRangeEvents = createTimestampBasedRangeEvents(uploadRanges, entityType, mergeStartTime,
        memberTenantId);

      log.info("prepareAndSendTimestampBasedIndexing:: Created and sending {} timestamp ranges "
        + "[entityType: {}]", timestampRangeEvents.size(), entityType);
      updateStatusAndSendEvents(entityType, timestampRangeEvents.size(), timestampRangeEvents);

    } catch (Exception e) {
      log.error("prepareAndSendTimestampBasedIndexing:: Failed to create timestamp ranges "
        + "[entityType: {}]", entityType, e);
      statusService.updateReindexUploadFailed(entityType);
      throw e;
    }
  }

  private boolean isChildResource(ReindexEntityType entityType) {
    return entityType == ReindexEntityType.SUBJECT
        || entityType == ReindexEntityType.CONTRIBUTOR
        || entityType == ReindexEntityType.CLASSIFICATION
        || entityType == ReindexEntityType.CALL_NUMBER;
  }

  private void updateStatusAndSendEvents(ReindexEntityType entityType, int rangeCount,
                                         List<ReindexRangeIndexEvent> events) {
    statusService.updateReindexUploadStarted(entityType, rangeCount);
    indexRangeEventProducer.sendMessages(events);
  }

  private List<ReindexRangeIndexEvent> createTimestampBasedRangeEvents(
      List<UploadRangeEntity> uploadRanges, 
      ReindexEntityType entityType,
      Timestamp mergeStartTime,
      String memberTenantId) {
    
    return uploadRanges.stream()
      .map(range -> createRangeEvent(range, entityType, memberTenantId, mergeStartTime, true))
      .toList();
  }

  private List<ReindexRangeIndexEvent> prepareEvents(List<UploadRangeEntity> uploadRanges) {
    String memberTenantId = ReindexContext.getMemberTenantId();
    return uploadRanges.stream()
      .map(range -> createRangeEvent(range, range.getEntityType(), memberTenantId, null, false))
      .toList();
  }

  private ReindexRangeIndexEvent createRangeEvent(UploadRangeEntity range, ReindexEntityType entityType,
                                                  String memberTenantId, Timestamp timestampFilter,
                                                  boolean isTimestampBased) {
    if (isTimestampBased) {
      return ReindexRangeIndexEvent.createTimestampBased(
        range.getId(),
        entityType,
        range.getLower(),
        range.getUpper(),
        memberTenantId,
        timestampFilter
      );
    } else {
      return ReindexRangeIndexEvent.createStandard(
        range.getId(),
        entityType,
        range.getLower(),
        range.getUpper(),
        memberTenantId
      );
    }
  }
}
