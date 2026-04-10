package org.folio.search.service.browse;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.QueryVersion;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.IndexFamilyService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.search.service.reindex.jdbc.V2BrowseDirtyIdRepository;
import org.folio.search.utils.V2BrowseIdExtractor;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ScheduledV2BrowseReconcilerService {

  private final TenantRepository tenantRepository;
  private final V2BrowseDirtyIdRepository dirtyIdRepository;
  private final IndexFamilyService indexFamilyService;
  private final V2BrowseProjectionService browseProjectionService;
  private final V2BrowseDirtyIdEnqueueHelper enqueueHelper;
  private final SystemUserScopedExecutionService executionService;
  private final int batchSize;

  public ScheduledV2BrowseReconcilerService(TenantRepository tenantRepository,
                                            V2BrowseDirtyIdRepository dirtyIdRepository,
                                            IndexFamilyService indexFamilyService,
                                            V2BrowseProjectionService browseProjectionService,
                                            V2BrowseDirtyIdEnqueueHelper enqueueHelper,
                                            SystemUserScopedExecutionService executionService,
                                            SearchConfigurationProperties configProperties) {
    this.tenantRepository = tenantRepository;
    this.dirtyIdRepository = dirtyIdRepository;
    this.indexFamilyService = indexFamilyService;
    this.browseProjectionService = browseProjectionService;
    this.enqueueHelper = enqueueHelper;
    this.executionService = executionService;
    this.batchSize = configProperties.getIndexing().getV2BrowseReconcilerBatchSize();
  }

  @Scheduled(fixedDelayString = "#{searchConfigurationProperties.indexing.v2BrowseReconcilerDelayMs}")
  public void reconcile() {
    tenantRepository.fetchDataTenantIds()
      .forEach(tenant -> executionService.executeSystemUserScoped(tenant, () -> {
        reconcileTenant(tenant);
        return null;
      }));
  }

  private void reconcileTenant(String tenant) {
    var activeFamily = indexFamilyService.findActiveFamily(tenant, QueryVersion.V2);
    if (activeFamily.isEmpty()) {
      return;
    }

    var claimed = dirtyIdRepository.claimBatch(tenant, batchSize);
    if (claimed.isEmpty()) {
      return;
    }

    var touched = groupToTouchedBrowseIds(claimed);
    var mainAlias = indexFamilyService.getAliasName(tenant, QueryVersion.V2);
    var browseAliases = indexFamilyService.getV2BrowseAliasMap(tenant);

    try {
      browseProjectionService.rebuildAll(touched, mainAlias, browseAliases);
    } catch (Exception e) {
      enqueueHelper.enqueueTouched(tenant, touched);
      log.error("reconcile failed for tenant {}, re-enqueued {} IDs", tenant, claimed.size(), e);
    }
  }

  private V2BrowseIdExtractor.TouchedBrowseIds groupToTouchedBrowseIds(
    java.util.List<V2BrowseDirtyIdRepository.DirtyBrowseIdRow> claimed) {
    Set<String> contributors = new HashSet<>();
    Set<String> subjects = new HashSet<>();
    Set<String> classifications = new HashSet<>();
    Set<String> callNumbers = new HashSet<>();

    for (var row : claimed) {
      var entityType = ReindexEntityType.fromValue(row.browseType());
      switch (entityType) {
        case CONTRIBUTOR -> contributors.add(row.browseId());
        case SUBJECT -> subjects.add(row.browseId());
        case CLASSIFICATION -> classifications.add(row.browseId());
        case CALL_NUMBER -> callNumbers.add(row.browseId());
        default -> log.warn("Unexpected browse type: {}", row.browseType());
      }
    }
    return new V2BrowseIdExtractor.TouchedBrowseIds(contributors, subjects, classifications, callNumbers);
  }
}
