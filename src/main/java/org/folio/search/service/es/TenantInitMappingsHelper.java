package org.folio.search.service.es;

import lombok.extern.log4j.Log4j2;
import org.folio.search.service.LanguageConfigService;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

/**
 * A {@link SearchMappingsHelper} variant used during tenant initialization.
 * Injects {@link LanguageConfigService} directly instead of the consortium decorator,
 * to avoid consortium tenant executor calls and cached cross-tenant responses.
 */
@Log4j2
@Service
public class TenantInitMappingsHelper extends SearchMappingsHelper {

  public TenantInitMappingsHelper(JsonConverter jsonConverter,
                                  SearchFieldProvider searchFieldProvider,
                                  LanguageConfigService languageConfigService,
                                  ResourceDescriptionService resourceDescriptionService) {
    super(jsonConverter, searchFieldProvider, languageConfigService, resourceDescriptionService);
  }
}


