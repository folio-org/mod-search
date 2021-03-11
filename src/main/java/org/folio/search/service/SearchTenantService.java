package org.folio.search.service;

import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.model.SearchResource;
import org.folio.search.service.systemuser.SystemUserService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class SearchTenantService {
  private final IndexService indexService;
  private final FolioExecutionContext context;
  private final Set<String> initialLanguages;
  private final LanguageConfigService languageConfigService;
  private final SystemUserService systemUserService;

  public SearchTenantService(IndexService indexService, FolioExecutionContext context,
    @Value("${application.search-config.initial-languages}") Set<String> initialLanguages,
    LanguageConfigService languageConfigService, SystemUserService systemUserService) {

    this.indexService = indexService;
    this.context = context;
    this.initialLanguages = initialLanguages;
    this.languageConfigService = languageConfigService;
    this.systemUserService = systemUserService;
  }

  public void initializeTenant() {
    systemUserService.prepareSystemUser(context);

    var existingLanguages = languageConfigService.getAllLanguageCodes();

    log.info("Initializing tenant [initialLanguages={}, existingLanguages={}]",
      initialLanguages, existingLanguages);

    initialLanguages.stream()
      .filter(code -> !existingLanguages.contains(code))
      .map(code -> new LanguageConfig().code(code))
      .forEach(languageConfigService::create);

    for (SearchResource resource : SearchResource.values()) {
      indexService.createIndexIfNotExist(resource.getName(), context.getTenantId());
    }
  }
}
