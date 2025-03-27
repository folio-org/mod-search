package org.folio.search.service;

import static org.folio.search.configuration.SearchCacheNames.TENANT_FEATURES_CACHE;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.converter.FeatureConfigMapper;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.config.FeatureConfigEntity;
import org.folio.search.repository.FeatureConfigRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@AllArgsConstructor
public class FeatureConfigService {

  private final FeatureConfigMapper featureConfigMapper;
  private final FeatureConfigRepository featureConfigRepository;
  private final SearchConfigurationProperties searchConfigurationProperties;

  /**
   * Checks if feature is enabled for tenant by id or not.
   *
   * @param feature - feature id as {@link TenantConfiguredFeature} object
   * @return true if feature is enabled for tenant, false - otherwise.
   */
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = TENANT_FEATURES_CACHE, key = "@folioExecutionContext.tenantId + ':' + #feature.value")
  public boolean isEnabled(TenantConfiguredFeature feature) {
    log.debug("isEnabled:: by [feature.value: {}]", feature.getValue());
    return featureConfigRepository.findById(feature.getValue())
      .map(FeatureConfigEntity::isEnabled)
      .orElseGet(() -> searchConfigurationProperties.getSearchFeatures().getOrDefault(feature, false));
  }

  /**
   * Returns all existing features configs for tenant.
   *
   * @return all existing feature configs as {@link FeatureConfigs} object.
   */
  @Transactional(readOnly = true)
  public FeatureConfigs getAll() {
    final List<FeatureConfig> tenantFeatures = featureConfigRepository.findAll().stream()
      .map(featureConfigMapper::convert)
      .toList();

    return new FeatureConfigs().features(tenantFeatures).totalRecords(tenantFeatures.size());
  }

  /**
   * Creates tenant's feature configuration using given {@link FeatureConfig} dto object.
   *
   * @param featureConfig - feature config dto as {@link FeatureConfig} object
   * @return created {@link FeatureConfig} dto.
   */
  @Transactional
  @CacheEvict(cacheNames = TENANT_FEATURES_CACHE,
    key = "@folioExecutionContext.tenantId + ':' + #featureConfig.feature.value")
  public FeatureConfig create(FeatureConfig featureConfig) {
    log.info("Attempting to create feature configuration [feature: {}]", featureConfig.getFeature().getValue());

    var entity = featureConfigMapper.convert(featureConfig);
    var featureId = entity.getFeatureId();
    if (featureConfigRepository.existsById(featureId)) {
      throw new RequestValidationException("Feature configuration already exists", "feature", featureId);
    }

    var savedEntity = featureConfigRepository.save(entity);
    return featureConfigMapper.convert(savedEntity);
  }

  /**
   * Updates tenant's feature configuration by given code and {@link FeatureConfig} dto.
   *
   * @param feature       - feature id as {@link TenantConfiguredFeature} object
   * @param featureConfig - feature config dto as {@link FeatureConfig} object
   * @return updated {@link FeatureConfig} dto.
   */
  @Transactional
  @CacheEvict(cacheNames = TENANT_FEATURES_CACHE, key = "@folioExecutionContext.tenantId + ':' + #feature.value")
  public FeatureConfig update(TenantConfiguredFeature feature, FeatureConfig featureConfig) {
    log.debug("Attempting to update feature configuration [feature: {}]", feature.getValue());

    var featureId = feature.getValue();
    var entity = featureConfigMapper.convert(featureConfig);

    if (!Objects.equals(entity.getFeatureId(), featureId)) {
      throw new RequestValidationException(
        "Request body feature must be the same as in the URL", "feature", featureId);
    }

    var existingEntity = featureConfigRepository.findById(featureId)
      .orElseThrow(() -> new EntityNotFoundException("Feature configuration not found for id: " + featureId));

    if (existingEntity.equals(entity)) {
      return featureConfig;
    }

    var updatedEntity = featureConfigRepository.save(entity);
    return featureConfigMapper.convert(updatedEntity);
  }

  /**
   * Deletes tenant's feature configuration by feature id.
   *
   * @param feature - feature id as {@link TenantConfiguredFeature} object
   */
  @Transactional
  @CacheEvict(cacheNames = TENANT_FEATURES_CACHE, key = "@folioExecutionContext.tenantId + ':' + #feature.value")
  public void delete(TenantConfiguredFeature feature) {
    log.debug("Attempts to delete feature configuration [feature: {}]", feature.getValue());

    if (!featureConfigRepository.existsById(feature.getValue())) {
      throw new EntityNotFoundException("Feature configuration not found for id: " + feature.getValue());
    }
    featureConfigRepository.deleteById(feature.getValue());
  }
}
