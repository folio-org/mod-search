package org.folio.search.service.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigSynchronizationService {

  private final BrowseConfigServiceDecorator configService;

  public void sync(List<ResourceEvent> resourceEvent, ResourceType resourceType) {
    if (resourceEvent == null || resourceEvent.isEmpty()) {
      return;
    }
    if (resourceType == ResourceType.CLASSIFICATION_TYPE) {
      var ids = resourceEvent.stream().map(ResourceEvent::getId).toList();
      configService.deleteTypeIdsFromConfigs(BrowseType.INSTANCE_CLASSIFICATION, ids);
    }
  }

}
