package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseConfigCollection;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.service.config.BrowseConfigService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrowseConfigServiceDecorator {

  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final BrowseConfigService browseConfigService;

  public BrowseConfigCollection getConfigs(BrowseType type) {
    return consortiumTenantExecutor.execute(() -> browseConfigService.getConfigs(type));
  }

  public void upsertConfig(BrowseType type, BrowseOptionType configId, BrowseConfig config) {
    consortiumTenantExecutor.run(() -> browseConfigService.upsertConfig(type, configId, config));
  }
}
