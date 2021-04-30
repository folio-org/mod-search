package org.folio.search.converter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.model.config.LanguageConfigEntity;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LanguageConfigConverter {
  public static LanguageConfig toLanguageConfig(LanguageConfigEntity entity) {
    final var languageConfig = new LanguageConfig();

    languageConfig.setCode(entity.getCode());
    languageConfig.setLanguageAnalyzer(entity.getEsAnalyzer());

    return languageConfig;
  }

  public static LanguageConfigEntity toLanguageConfigEntity(LanguageConfig dto) {
    final var languageConfig = new LanguageConfigEntity();

    languageConfig.setCode(dto.getCode());
    languageConfig.setEsAnalyzer(dto.getLanguageAnalyzer());

    return languageConfig;
  }
}
