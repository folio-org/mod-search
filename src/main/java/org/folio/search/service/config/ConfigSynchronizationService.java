package org.folio.search.service.config;

import java.util.List;
import java.util.Optional;
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

    Optional.ofNullable(resourceType)
      .map(resource -> switch (resourceType) {
        case CLASSIFICATION_TYPE -> BrowseType.CLASSIFICATION;
        case CALL_NUMBER_TYPE -> BrowseType.CALL_NUMBER;
        default -> null; })
      .ifPresent(browseType -> {
        var ids = resourceEvent.stream().map(ResourceEvent::getId).toList();
        configService.deleteTypeIdsFromConfigs(browseType, ids);
      });
  }

}
