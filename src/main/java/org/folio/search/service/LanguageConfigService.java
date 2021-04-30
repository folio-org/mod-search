package org.folio.search.service;

import static org.folio.search.converter.LanguageConfigConverter.toLanguageConfig;
import static org.folio.search.converter.LanguageConfigConverter.toLanguageConfigEntity;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.converter.LanguageConfigConverter;
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
  private final TenantScopedExecutionService executionService;

  public LanguageConfig create(LanguageConfig languageConfig) {
    final var entity = toLanguageConfigEntity(languageConfig);

    if (!descriptionService.isSupportedLanguage(languageConfig.getCode())) {
      log.warn("There is no language analyzer configured for language {}", languageConfig.getCode());
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

  public LanguageConfig update(String code, LanguageConfig languageConfig) {
    var entity = toLanguageConfigEntity(languageConfig);

    if (!Objects.equals(languageConfig.getCode(), code)) {
      throw new ValidationException(
        "Request body language code must be the same as in the URL", "code", languageConfig.getCode());
    }

    var existingEntity = configRepository.findById(code)
      .orElseThrow(() -> new EntityNotFoundException("Language config not found for code: " + code));

    if (existingEntity.equals(entity)) {
      return languageConfig;
    }

    return toLanguageConfig(configRepository.save(entity));
  }

  public void delete(String code) {
    configRepository.deleteById(code);
  }

  public LanguageConfigs getAll() {
    final List<LanguageConfig> languageConfigs = configRepository.findAll().stream()
      .map(LanguageConfigConverter::toLanguageConfig)
      .collect(Collectors.toList());

    return new LanguageConfigs()
      .languageConfigs(languageConfigs)
      .totalRecords(languageConfigs.size());
  }

  public Set<String> getAllLanguageCodes() {
    return getAll().getLanguageConfigs().stream()
      .map(LanguageConfig::getCode)
      .collect(Collectors.toSet());
  }

  public Set<String> getAllLanguagesForTenant(String tenant) {
    return executionService.executeTenantScoped(tenant,
      () -> configRepository.findAll().stream()
        .map(LanguageConfigEntity::getCode)
        .collect(Collectors.toSet()));
  }
}
