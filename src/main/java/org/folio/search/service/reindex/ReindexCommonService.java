package org.folio.search.service.reindex;

import static java.util.function.Function.identity;
import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.IndexService;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class ReindexCommonService {

  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;
  private final IndexService indexService;
  private final IndexRepository indexRepository;
  private final IndexNameProvider indexNameProvider;

  public ReindexCommonService(List<ReindexJdbcRepository> repositories, IndexService indexService,
                              IndexRepository indexRepository, IndexNameProvider indexNameProvider) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity(), (rep1, rep2) -> rep2));
    this.indexService = indexService;
    this.indexRepository = indexRepository;
    this.indexNameProvider = indexNameProvider;
  }

  @Transactional
  public void deleteAllRecords() {
    deleteAllRecords(null);
  }

  @Transactional
  public void deleteAllRecords(String tenantId) {
    for (ReindexEntityType entityType : ReindexEntityType.values()) {
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
   * Deletes documents from OpenSearch indexes for a specific tenant, preserving shared documents.
   * This method is used during tenant-specific reindex operations to clean existing tenant data
   * without affecting shared consortium instances.
   *
   * @param tenantId the tenant ID whose documents should be deleted
   */
  public void deleteIndexDocumentsByTenantId(String tenantId) {
    deleteIndexDocumentsByTenantId(tenantId, true);
  }

  /**
   * Deletes documents from OpenSearch indexes for a specific tenant with configurable shared preservation.
   * Processes all reindex entity types to ensure complete tenant data cleanup.
   *
   * @param tenantId the tenant ID whose documents should be deleted
   * @param preserveShared if true, preserves documents marked as shared (shared=true)
   */
  public void deleteIndexDocumentsByTenantId(String tenantId, boolean preserveShared) {
    log.info("deleteIndexDocumentsByTenantId:: starting deletion for [tenantId: {}, preserveShared: {}]", 
      tenantId, preserveShared);

    int successCount = 0;
    int errorCount = 0;

    for (ReindexEntityType entityType : ReindexEntityType.values()) {
      try {
        var resourceType = RESOURCE_NAME_MAP.get(entityType);
        if (resourceType != null) {
          var indexName = indexNameProvider.getIndexName(resourceType, tenantId);
          var result = indexRepository.deleteDocumentsByTenantId(indexName, tenantId, preserveShared);
          
          if (result != null) {
            successCount++;
            log.debug("deleteIndexDocumentsByTenantId:: completed for [entityType: {}, indexName: {}]", 
              entityType, indexName);
          } else {
            errorCount++;
            log.warn("deleteIndexDocumentsByTenantId:: failed for [entityType: {}, indexName: {}]", 
              entityType, indexName);
          }
        }
      } catch (Exception e) {
        errorCount++;
        log.error("deleteIndexDocumentsByTenantId:: error processing [entityType: {}, tenantId: {}, error: {}]", 
          entityType, tenantId, e.getMessage(), e);
      }
    }

    log.info("deleteIndexDocumentsByTenantId:: completed [tenantId: {}, preserveShared: {}, "
      + "successful: {}, errors: {}]", tenantId, preserveShared, successCount, errorCount);
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
