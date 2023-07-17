package org.folio.search.service.consortia;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.service.LanguageConfigService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LanguageConfigServiceDecorator {

  private final ConsortiaTenantExecutor consortiaTenantExecutor;
  private final LanguageConfigService languageConfigService;

  public LanguageConfig create(LanguageConfig languageConfig) {
    return consortiaTenantExecutor.execute(() -> languageConfigService.create(languageConfig));
  }

  public LanguageConfig update(String code, LanguageConfig languageConfig) {
    return consortiaTenantExecutor.execute(() -> languageConfigService.update(code, languageConfig));
  }

  public void delete(String code) {
    consortiaTenantExecutor.run(() -> languageConfigService.delete(code));
  }

  public LanguageConfigs getAll() {
    return consortiaTenantExecutor.execute(languageConfigService::getAll);
  }

  public Set<String> getAllLanguageCodes() {
    return consortiaTenantExecutor.execute(languageConfigService::getAllLanguageCodes);
  }

  public Set<String> getAllLanguagesForTenant(String tenant) {
    return languageConfigService.getAllLanguagesForTenant(tenant);
  }

}
