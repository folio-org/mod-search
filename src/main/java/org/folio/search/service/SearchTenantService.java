package org.folio.search.service;

import static java.lang.Boolean.parseBoolean;

import java.util.Collection;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.model.SearchResource;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SearchTenantService {
  private static final String REINDEX_PARAM_NAME = "runReindex";

  private final IndexService indexService;
  private final FolioExecutionContext context;
  private final SystemUserService systemUserService;
  private final LanguageConfigService languageConfigService;
  private final SearchConfigurationProperties searchConfigurationProperties;

  public void initializeTenant(TenantAttributes tenantAttributes) {
    systemUserService.prepareSystemUser();

    var existingLanguages = languageConfigService.getAllLanguageCodes();

    var initialLanguages = searchConfigurationProperties.getInitialLanguages();
    log.info("Initializing tenant [initialLanguages={}, existingLanguages={}]", initialLanguages, existingLanguages);

    initialLanguages.stream()
      .filter(code -> !existingLanguages.contains(code))
      .map(code -> new LanguageConfig().code(code))
      .forEach(languageConfigService::create);

    for (SearchResource resource : SearchResource.values()) {
      indexService.createIndexIfNotExist(resource.getName(), context.getTenantId());
    }
    Stream.ofNullable(tenantAttributes.getParameters()).flatMap(Collection::stream)
      .filter(parameter -> parameter.getKey().equals(REINDEX_PARAM_NAME) && parseBoolean(parameter.getValue()))
      .findFirst().ifPresent(parameter -> indexService.reindexInventory(context.getTenantId(), null));
  }

  public void removeElasticsearchIndexes() {
    for (SearchResource resource : SearchResource.values()) {
      log.info("Removing elasticsearch index [resourceName={}, tenant={}]",
        resource.getName(), context.getTenantId());

      indexService.dropIndex(resource.getName(), context.getTenantId());
    }
  }
}
