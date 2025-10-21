package org.folio.search.service.reindex;

import static java.util.function.Function.identity;
import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.IndexService;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class ReindexCommonService {

  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;
  private final IndexService indexService;
  private final PrimaryResourceRepository resourceRepository;
  private final IndexNameProvider indexNameProvider;

  public ReindexCommonService(List<ReindexJdbcRepository> repositories, IndexService indexService,
                              PrimaryResourceRepository resourceRepository, IndexNameProvider indexNameProvider) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity(), (rep1, rep2) -> rep2));
    this.indexService = indexService;
    this.resourceRepository = resourceRepository;
    this.indexNameProvider = indexNameProvider;
  }

  @Transactional
  public void deleteAllRecords(String tenantId) {
    for (var entityType : ReindexEntityType.values()) {
      if (tenantId != null) {
        // For tenant-specific refresh: only truncate staging tables
        repositories.get(entityType).truncateStaging();
      } else {
        // For full refresh: truncate all tables (existing behavior)
        repositories.get(entityType).truncate();
      }
    }
  }

  @Transactional
  public void deleteRecordsByTenantId(String tenantId) {
    // Delete in proper order to avoid foreign key constraint violations
    // First delete relationship tables (child entities)
    repositories.get(ReindexEntityType.SUBJECT).deleteByTenantId(tenantId);
    repositories.get(ReindexEntityType.CONTRIBUTOR).deleteByTenantId(tenantId);
    repositories.get(ReindexEntityType.CLASSIFICATION).deleteByTenantId(tenantId);
    repositories.get(ReindexEntityType.CALL_NUMBER).deleteByTenantId(tenantId);

    // Then delete main entity tables (parent entities)
    repositories.get(ReindexEntityType.ITEM).deleteByTenantId(tenantId);
    repositories.get(ReindexEntityType.HOLDINGS).deleteByTenantId(tenantId);
    repositories.get(ReindexEntityType.INSTANCE).deleteByTenantId(tenantId);

    log.info("Successfully deleted existing data for tenant: {}", tenantId);
  }

  /**
   * Deletes documents from OpenSearch indexes for a specific tenant with shared preservation.
   *
   * @param tenantId the tenant ID whose documents should be deleted
   */
  public void deleteInstanceDocumentsByTenantId(String tenantId) {
    log.info("deleteInstanceDocumentsByTenantId:: starting deletion for [tenantId: {}, ]", tenantId);

    try {
      var resourceType = ResourceType.INSTANCE;
      var indexName = indexNameProvider.getIndexName(resourceType, tenantId);
      var result = resourceRepository.deleteConsortiumDocumentsByTenantId(indexName, tenantId);

      if (result != null) {
        log.debug("deleteInstanceDocumentsByTenantId:: completed for [indexName: {}]", indexName);
      } else {
        log.warn("deleteInstanceDocumentsByTenantId:: failed for [indexName: {}]", indexName);
      }
    } catch (Exception e) {
      log.error("deleteInstanceDocumentsByTenantId:: error processing [tenantId: {}, error: {}]",
        tenantId, e.getMessage(), e);
    }

    log.info("deleteInstanceDocumentsByTenantId:: completed [tenantId: {}]", tenantId);
  }

  public void recreateIndex(ReindexEntityType reindexEntityType, String tenantId, IndexSettings indexSettings) {
    try {
      var resourceType = RESOURCE_NAME_MAP.get(reindexEntityType);
      indexService.dropIndex(resourceType, tenantId);
      indexService.createIndex(resourceType, tenantId, indexSettings);
    } catch (Exception e) {
      log.warn("Index cannot be recreated for resource={}, message={}", reindexEntityType, e.getMessage());
    }
  }

  public void ensureIndexExists(ReindexEntityType reindexEntityType, String tenantId, IndexSettings indexSettings) {
    try {
      var resourceType = RESOURCE_NAME_MAP.get(reindexEntityType);
      if (indexSettings != null) {
        indexService.createIndexIfNotExist(resourceType, tenantId, indexSettings);
      } else {
        indexService.createIndexIfNotExist(resourceType, tenantId);
      }
    } catch (Exception e) {
      log.warn("Index existence check/creation failed for resource={}, message={}", reindexEntityType, e.getMessage());
    }
  }
}
