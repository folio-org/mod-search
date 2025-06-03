package org.folio.search.service.config;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.springframework.stereotype.Service;

@Log4j2
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
          case CLASSIFICATION_TYPE -> BrowseType.INSTANCE_CLASSIFICATION;
          case CALL_NUMBER_TYPE -> BrowseType.INSTANCE_CALL_NUMBER;
          default -> null;
        }
      ).ifPresentOrElse(browseType -> {
        var ids = resourceEvent.stream().map(ResourceEvent::getId).toList();
        configService.deleteTypeIdsFromConfigs(browseType, ids);
      }, () -> log.warn("sync:: not supported resource type: [{}]", resourceType));
  }
}
