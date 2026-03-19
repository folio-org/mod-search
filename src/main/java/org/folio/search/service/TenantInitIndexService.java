package org.folio.search.service;

import org.folio.search.client.ResourceReindexClient;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.consortium.SimpleTenantProvider;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.es.TenantInitMappingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.stereotype.Service;

/**
 * A {@link IndexService} variant used during tenant initialization.
 * Uses {@link TenantInitMappingsHelper} (which injects {@link LanguageConfigService} directly
 * instead of the consortium decorator) and an {@link IndexNameProvider} backed by
 * {@link SimpleTenantProvider} (which returns the tenant id as-is without consortium lookup).
 * This prevents consortium tenant executor calls and cached cross-tenant responses
 * during tenant initialization index creation.
 */
@Service
public class TenantInitIndexService extends IndexService {

  public TenantInitIndexService(IndexRepository indexRepository,
                                TenantInitMappingsHelper mappingHelper,
                                SearchSettingsHelper settingsHelper,
                                ResourceReindexClient resourceReindexClient,
                                ResourceDescriptionService resourceDescriptionService,
                                SimpleTenantProvider simpleTenantProvider,
                                LocationService locationService) {
    super(indexRepository,
      mappingHelper,
      settingsHelper,
      resourceReindexClient,
      resourceDescriptionService,
      new IndexNameProvider(simpleTenantProvider),
      simpleTenantProvider,
      locationService);
  }
}

