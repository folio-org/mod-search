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
import org.folio.search.service.reindex.jdbc.UploadInstanceRepository;
import org.folio.search.service.reindex.jdbc.UploadRangeRepository;
import org.folio.search.utils.JdbcUtils;
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

  public void reuploadSharedInstances(String tenantId) {
    log.info("reuploadSharedInstances:: Starting shared instances re-upload for tenant: {}", tenantId);
    
    var instanceRepository = (UploadInstanceRepository) repositories.get(ReindexEntityType.INSTANCE);
    if (instanceRepository == null) {
      log.warn("reuploadSharedInstances:: No instance repository found, skipping shared instance re-upload");
      return;
    }
    
    // Get central tenant ID for consortium deployments
    var centralTenantId = consortiumTenantService.getCentralTenant(tenantId);
    if (centralTenantId.isEmpty()) {
      log.info("reuploadSharedInstances:: No central tenant found - non-consortium deployment, "
          + "skipping shared instance re-upload");
      return;
    }
    
    // Query central tenant's instance table for shared instances
    var moduleMetadata = context.getFolioModuleMetadata();
    var centralSchema = JdbcUtils.getSchemaName(centralTenantId.get(), moduleMetadata);
    var centralInstanceTable = centralSchema + ".instance";
    
    String sql = "SELECT id FROM " + centralInstanceTable + " WHERE shared = true";
    List<String> sharedInstanceIds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("id"));
    
    if (sharedInstanceIds.isEmpty()) {
      log.info("reuploadSharedInstances:: No shared instances found to re-upload");
      return;
    }
    
    log.info("reuploadSharedInstances:: Found {} shared instances to re-upload", sharedInstanceIds.size());
    
    // Fetch shared instances with all tenant data (method handles batching internally)
    var instances = instanceRepository.fetchSharedInstancesWithAllTenantData(sharedInstanceIds);
    
    // Convert to ResourceEvents for indexing
    var events = instances.stream()
        .map(instance -> new ResourceEvent()
            .id(getString(instance, ID_FIELD))
            .resourceName(ReindexConstants.RESOURCE_NAME_MAP.get(ReindexEntityType.INSTANCE).getName())
            ._new(instance)
            .tenant(tenantId))
        .toList();
    
    // Index the shared instances directly using ResourceService
    if (!events.isEmpty()) {
      log.info("reuploadSharedInstances:: Indexing {} shared instances", events.size());
      var response = resourceService.indexResources(events);
      
      if (response.getErrorMessage() != null) {
        log.error("reuploadSharedInstances:: Failed to index shared instances: {}", 
            response.getErrorMessage());
        throw new RuntimeException("Failed to index shared instances: " + response.getErrorMessage());
      }
      
      log.info("reuploadSharedInstances:: Successfully indexed {} shared instances", 
          events.size());
    }
    
    log.info("reuploadSharedInstances:: Completed shared instances re-upload");
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
