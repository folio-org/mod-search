package org.folio.search.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.exception.ValidationException;
import org.folio.search.model.config.LanguageConfigEntity;
import org.folio.search.repository.LanguageConfigRepository;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor
public class LanguageConfigService {
  private final LanguageConfigRepository configRepository;
  private final ResourceDescriptionService descriptionService;

  public LanguageConfig create(LanguageConfig languageConfig) {
    final LanguageConfigEntity entity = toLanguageConfigEntity(languageConfig);

    if (!descriptionService.isSupportedLanguage(languageConfig.getCode())) {
      log.warn("There is no language analyzer configured for language {}", languageConfig);
      throw new ValidationException("Language has no analyzer available",
        "code", languageConfig.getCode());
    }

    if (configRepository.count() > 4) {
      log.warn("Tenant is allowed to have only 5 languages configured");
      throw new ValidationException("Tenant is allowed to have only 5 languages configured",
        "code", languageConfig.getCode());
    }

    return toLanguageConfig(configRepository.save(entity));
  }

  public void delete(String id) {
    configRepository.deleteById(UUID.fromString(id));
  }

  public LanguageConfigs getAll() {
    final List<LanguageConfig> languageConfigs = configRepository.findAll().stream()
      .map(this::toLanguageConfig)
      .collect(Collectors.toList());

    return new LanguageConfigs()
      .languageConfigs(languageConfigs)
      .totalRecords(languageConfigs.size());
  }

  public Set<String> getAllSupportedLanguageCodes() {
    return getAll().getLanguageConfigs().stream()
      .map(LanguageConfig::getCode)
      .collect(Collectors.toSet());
  }

  private LanguageConfig toLanguageConfig(LanguageConfigEntity entity) {
    final LanguageConfig languageConfig = new LanguageConfig();

    languageConfig.setId(entity.getId().toString());
    languageConfig.setCode(entity.getCode());

    return languageConfig;
  }

  private LanguageConfigEntity toLanguageConfigEntity(LanguageConfig dto) {
    final LanguageConfigEntity languageConfig = new LanguageConfigEntity();

    languageConfig.setId(dto.getId() != null ? UUID.fromString(dto.getId()) : UUID.randomUUID());
    languageConfig.setCode(dto.getCode());

    return languageConfig;
  }
}
