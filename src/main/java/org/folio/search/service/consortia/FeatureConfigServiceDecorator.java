package org.folio.search.service.consortia;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeatureConfigServiceDecorator {

  private final ConsortiaTenantExecutor consortiaTenantExecutor;
  private final FeatureConfigService featureConfigService;

  public boolean isEnabled(TenantConfiguredFeature feature) {
    return consortiaTenantExecutor.execute(() -> featureConfigService.isEnabled(feature));
  }

  public FeatureConfigs getAll() {
    return consortiaTenantExecutor.execute(featureConfigService::getAll);
  }

  public FeatureConfig create(FeatureConfig featureConfig) {
    return consortiaTenantExecutor.execute(() -> featureConfigService.create(featureConfig));
  }

  public FeatureConfig update(TenantConfiguredFeature feature, FeatureConfig featureConfig) {
    return consortiaTenantExecutor.execute(() -> featureConfigService.update(feature, featureConfig));
  }

  public void delete(TenantConfiguredFeature feature) {
    consortiaTenantExecutor.run(() -> featureConfigService.delete(feature));
  }

}
