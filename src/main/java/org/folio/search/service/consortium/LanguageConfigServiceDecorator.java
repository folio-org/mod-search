package org.folio.search.service.consortium;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.service.LanguageConfigService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LanguageConfigServiceDecorator {

  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final LanguageConfigService languageConfigService;

  public LanguageConfig create(LanguageConfig languageConfig) {
    return consortiumTenantExecutor.execute(() -> languageConfigService.create(languageConfig));
  }

  public LanguageConfig update(String code, LanguageConfig languageConfig) {
    return consortiumTenantExecutor.execute(() -> languageConfigService.update(code, languageConfig));
  }

  public void delete(String code) {
    consortiumTenantExecutor.run(() -> languageConfigService.delete(code));
  }

  public LanguageConfigs getAll() {
    return consortiumTenantExecutor.execute(languageConfigService::getAll);
  }

  public Set<String> getAllLanguageCodes() {
    return consortiumTenantExecutor.execute(languageConfigService::getAllLanguageCodes);
  }

}
