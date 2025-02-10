package org.folio.search.service;

import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.configuration.SearchCacheNames.TENANT_FEATURES_CACHE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.cleanUpCaches;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.converter.FeatureConfigMapperImpl;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.model.config.FeatureConfigEntity;
import org.folio.search.repository.FeatureConfigRepository;
import org.folio.search.service.FeatureConfigServiceTest.TestContextConfiguration;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@SpringBootTest(classes = {FeatureConfigService.class, TestContextConfiguration.class}, webEnvironment = NONE)
class FeatureConfigServiceTest {

  private static final TenantConfiguredFeature FEATURE = SEARCH_ALL_FIELDS;
  private static final String FEATURE_ID = SEARCH_ALL_FIELDS.getValue();

  @Autowired
  private CacheManager cacheManager;
  @Autowired
  private FeatureConfigService featureConfigService;
  @MockitoBean
  private FeatureConfigRepository featureConfigRepository;
  @MockitoBean
  private SearchConfigurationProperties searchConfigurationProperties;

  @BeforeEach
  void setUp() {
    cleanUpCaches(cacheManager);
  }

  @Test
  void create_positive_mustEvictCache() {
    when(searchConfigurationProperties.getSearchFeatures()).thenReturn(mapOf(FEATURE, false));
    assertThat(featureConfigService.isEnabled(FEATURE)).isFalse();
    assertThat(getCachedValue()).isEqualTo(Optional.of(false));

    var expectedEntity = FeatureConfigEntity.of(FEATURE_ID, true);
    when(featureConfigRepository.existsById(FEATURE_ID)).thenReturn(false);
    when(featureConfigRepository.save(expectedEntity)).thenReturn(expectedEntity);

    var feature = new FeatureConfig().feature(FEATURE).enabled(true);
    var actual = featureConfigService.create(feature);
    assertThat(actual).isEqualTo(feature);
    assertThat(getCachedValue()).isEmpty();
  }

  @Test
  void create_negative_featureAlreadyExists() {
    var featureId = SEARCH_ALL_FIELDS.getValue();
    when(featureConfigRepository.existsById(featureId)).thenReturn(true);

    var feature = new FeatureConfig().feature(FEATURE).enabled(true);
    assertThatThrownBy(() -> featureConfigService.create(feature))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Feature configuration already exists");
  }

  @Test
  void isEnabled_positive_featureEnabledInDatabase() {
    checkIfFeatureIsEnabled();
  }

  @Test
  void isEnabled_positive_featureDisabledInDatabase() {
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(of(FeatureConfigEntity.of(FEATURE_ID, false)));
    var actual = featureConfigService.isEnabled(FEATURE);
    assertThat(actual).isFalse();
    assertThat(getCachedValue()).isEqualTo(of(false));
  }

  @Test
  void isEnabled_positive_featureNotConfiguredYet() {
    when(searchConfigurationProperties.getSearchFeatures()).thenReturn(mapOf(FEATURE, false));
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(Optional.empty());
    var actual = featureConfigService.isEnabled(FEATURE);
    assertThat(actual).isFalse();
    assertThat(getCachedValue()).isEqualTo(of(false));
  }

  @Test
  void isEnabled_positive_featureIsNotConfiguredButEnabledInConfigurationProperties() {
    when(searchConfigurationProperties.getSearchFeatures()).thenReturn(mapOf(FEATURE, true));
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(Optional.empty());
    var actual = featureConfigService.isEnabled(FEATURE);
    assertThat(actual).isTrue();
    assertThat(getCachedValue()).isEqualTo(of(true));
  }

  @Test
  void update_positive() {
    checkIfFeatureIsEnabled();
    var expectedFeature = new FeatureConfig().feature(FEATURE).enabled(false);
    var expectedFeatureEntity = FeatureConfigEntity.of(FEATURE_ID, false);
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(of(FeatureConfigEntity.of(FEATURE_ID, true)));
    when(featureConfigRepository.save(expectedFeatureEntity)).thenReturn(expectedFeatureEntity);
    var actual = featureConfigService.update(FEATURE, expectedFeature);
    assertThat(actual).isEqualTo(expectedFeature);
    assertThat(getCachedValue()).isEmpty();
  }

  @Test
  void update_positive_sameValues() {
    var expectedFeature = new FeatureConfig().feature(FEATURE).enabled(true);
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(of(FeatureConfigEntity.of(FEATURE_ID, true)));
    var actual = featureConfigService.update(FEATURE, expectedFeature);
    assertThat(actual).isEqualTo(expectedFeature);
  }

  @Test
  void update_negative_differentIncorrectId() {
    var expectedFeature = new FeatureConfig().feature(null).enabled(true);
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(of(FeatureConfigEntity.of(FEATURE_ID, true)));
    assertThatThrownBy(() -> featureConfigService.update(FEATURE, expectedFeature))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Request body feature must be the same as in the URL");
  }

  @Test
  void update_negative_entityNotFound() {
    var expectedFeature = new FeatureConfig().feature(FEATURE).enabled(true);
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> featureConfigService.update(FEATURE, expectedFeature))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Feature configuration not found for id: " + FEATURE_ID);
  }

  @Test
  void getAll_positive() {
    when(featureConfigRepository.findAll()).thenReturn(List.of(FeatureConfigEntity.of(FEATURE_ID, true)));
    var actual = featureConfigService.getAll();
    var expectedFeature = new FeatureConfig().feature(FEATURE).enabled(true);
    assertThat(actual).isEqualTo(new FeatureConfigs().features(List.of(expectedFeature)).totalRecords(1));
  }

  @Test
  void delete_positive_mustEvictCache() {
    checkIfFeatureIsEnabled();
    when(featureConfigRepository.existsById(FEATURE_ID)).thenReturn(true);
    featureConfigService.delete(FEATURE);
    assertThat(getCachedValue()).isEmpty();
  }

  @Test
  void delete_negative_featureConfigNotFound() {
    when(featureConfigRepository.existsById(FEATURE_ID)).thenReturn(false);
    assertThatThrownBy(() -> featureConfigService.delete(FEATURE))
      .isInstanceOf(EntityNotFoundException.class)
      .hasMessage("Feature configuration not found for id: search.all.fields");
  }

  private void checkIfFeatureIsEnabled() {
    when(featureConfigRepository.findById(FEATURE_ID)).thenReturn(of(FeatureConfigEntity.of(FEATURE_ID, true)));
    var actual = featureConfigService.isEnabled(FEATURE);

    assertThat(actual).isTrue();
    assertThat(getCachedValue()).isEqualTo(Optional.of(true));
  }

  private Optional<Object> getCachedValue() {
    return ofNullable(cacheManager.getCache(TENANT_FEATURES_CACHE))
      .map(cache -> cache.get(TENANT_ID + ":" + FEATURE_ID))
      .map(ValueWrapper::get);
  }

  @EnableCaching
  @TestConfiguration
  @Import(FeatureConfigMapperImpl.class)
  static class TestContextConfiguration {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(TENANT_FEATURES_CACHE);
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, mapOf(TENANT, singletonList(TENANT_ID)));
    }
  }
}
