package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.FeatureConfigs;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.service.FeatureConfigService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeatureConfigServiceDecorator {

  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final FeatureConfigService featureConfigService;

  public boolean isEnabled(TenantConfiguredFeature feature) {
    return consortiumTenantExecutor.execute(() -> featureConfigService.isEnabled(feature));
  }

  public FeatureConfigs getAll() {
    return consortiumTenantExecutor.execute(featureConfigService::getAll);
  }

  public FeatureConfig create(FeatureConfig featureConfig) {
    return consortiumTenantExecutor.execute(() -> featureConfigService.create(featureConfig));
  }

  public FeatureConfig update(TenantConfiguredFeature feature, FeatureConfig featureConfig) {
    return consortiumTenantExecutor.execute(() -> featureConfigService.update(feature, featureConfig));
  }

  public void delete(TenantConfiguredFeature feature) {
    consortiumTenantExecutor.run(() -> featureConfigService.delete(feature));
  }

}
