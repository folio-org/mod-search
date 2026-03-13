package org.folio.search.service;

import java.util.Set;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;

public interface LanguageConfigServiceI {
  LanguageConfig create(LanguageConfig languageConfig);

  LanguageConfig update(String code, LanguageConfig languageConfig);

  void delete(String code);

  LanguageConfigs getAll();

  Set<String> getAllLanguageCodes();
}
