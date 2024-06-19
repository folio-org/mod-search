package org.folio.search.service;

import static org.folio.search.configuration.SearchCacheNames.RESOURCE_LANGUAGE_CACHE;
import static org.folio.search.converter.LanguageConfigConverter.toLanguageConfig;
import static org.folio.search.converter.LanguageConfigConverter.toLanguageConfigEntity;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.converter.LanguageConfigConverter;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.exception.ValidationException;
import org.folio.search.repository.LanguageConfigRepository;
import org.folio.search.service.metadata.LocalSearchFieldProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@AllArgsConstructor
public class LanguageConfigService {

  private final LanguageConfigRepository configRepository;
  private final LocalSearchFieldProvider searchFieldProvider;
  private final TenantScopedExecutionService executionService;
  private final SearchConfigurationProperties searchConfiguration;

  /**
   * Creates tenant's language configuration using given {@link LanguageConfig} dto object.
   *
   * @param languageConfig language config dto as {@link LanguageConfig} object
   * @return created {@link LanguageConfig} dto.
   */
  @CacheEvict(cacheNames = RESOURCE_LANGUAGE_CACHE, key = "@folioExecutionContext.tenantId")
  public LanguageConfig create(LanguageConfig languageConfig) {
    log.debug("create:: by [languageConfig: {}]", languageConfig);

    var entity = toLanguageConfigEntity(languageConfig);
    var languageCode = languageConfig.getCode();

    if (!searchFieldProvider.isSupportedLanguage(languageCode)) {
      throw new ValidationException("Language has no analyzer available", "code", languageCode);
    }

    var maxSupportedLanguages = searchConfiguration.getMaxSupportedLanguages();
    if (configRepository.count() >= maxSupportedLanguages) {
      throw new ValidationException(String.format(
        "Tenant is allowed to have only %s languages configured", maxSupportedLanguages),
        "code", languageCode);
    }

    return toLanguageConfig(configRepository.save(entity));
  }

  /**
   * Updates tenant's language configuration by given code and {@link LanguageConfig} dto.
   *
   * @param code           language code as {@link String} object
   * @param languageConfig language config dto as {@link LanguageConfig} object
   * @return updated {@link LanguageConfig} dto.
   */
  @CacheEvict(cacheNames = RESOURCE_LANGUAGE_CACHE, key = "@folioExecutionContext.tenantId")
  public LanguageConfig update(String code, LanguageConfig languageConfig) {
    log.debug("update:: by [code: {}, languageConfig: {}]", code, languageConfig);
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

  /**
   * Deletes tenant's language configuration by language code.
   *
   * @param code language code as {@link String} object
   */
  @CacheEvict(cacheNames = RESOURCE_LANGUAGE_CACHE, key = "@folioExecutionContext.tenantId")
  public void delete(String code) {
    log.debug("Attempts to delete languageConfig by [code: {}]", code);

    configRepository.deleteById(code);
  }

  /**
   * Returns all existing language configs for tenant.
   *
   * @return all existing language configs as {@link LanguageConfigs} object.
   */
  public LanguageConfigs getAll() {
    final List<LanguageConfig> languageConfigs = configRepository.findAll().stream()
      .map(LanguageConfigConverter::toLanguageConfig)
      .toList();

    return new LanguageConfigs()
      .languageConfigs(languageConfigs)
      .totalRecords(languageConfigs.size());
  }

  /**
   * Returns all language configuration codes.
   *
   * @return {@link Set} with language configuration codes.
   */
  @Cacheable(cacheNames = RESOURCE_LANGUAGE_CACHE, key = "@folioExecutionContext.tenantId")
  public Set<String> getAllLanguageCodes() {
    return getAll().getLanguageConfigs().stream()
      .map(LanguageConfig::getCode)
      .collect(Collectors.toSet());
  }

}
