package org.folio.search.controller;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.domain.dto.LanguageConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.rest.resource.ConfigApi;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.LanguageConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search/")
public class ConfigController implements ConfigApi {

  private final LanguageConfigService languageConfigService;
  private final FeatureConfigService featureConfigService;

  @Override
  public ResponseEntity<LanguageConfig> createLanguageConfig(@Valid LanguageConfig languageConfig) {
    log.info("Attempting to save language config [code: {}]", languageConfig.getCode());
    return ok(languageConfigService.create(languageConfig));
  }

  @Override
  public ResponseEntity<LanguageConfig> updateLanguageConfig(String code, LanguageConfig languageConfig) {
    return ok(languageConfigService.update(code, languageConfig));
  }

  @Override
  public ResponseEntity<Void> deleteLanguageConfig(String code) {
    log.info("Attempting to remove language config [code: {}]", code);
    languageConfigService.delete(code);
    return noContent().build();
  }

  @Override
  public ResponseEntity<LanguageConfigs> getAllLanguageConfigs() {
    return ok(languageConfigService.getAll());
  }

  @Override
  public ResponseEntity<FeatureConfigs> getAllFeatures() {
    return ok(featureConfigService.getAll());
  }

  @Override
  public ResponseEntity<FeatureConfig> saveFeatureConfiguration(FeatureConfig featureConfig) {
    return ResponseEntity.ok(featureConfigService.create(featureConfig));
  }

  @Override
  public ResponseEntity<FeatureConfig> updateFeatureConfiguration(String feature, FeatureConfig featureConfig) {
    return ResponseEntity.ok(featureConfigService.update(TenantConfiguredFeature.fromValue(feature), featureConfig));
  }

  @Override
  public ResponseEntity<Void> deleteFeatureConfigurationById(String feature) {
    featureConfigService.delete(TenantConfiguredFeature.fromValue(feature));
    return noContent().build();
  }
}
