package org.folio.search.service.config;

import static org.folio.support.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConfigSynchronizationServiceTest {

  @Mock
  private BrowseConfigServiceDecorator configService;

  @InjectMocks
  private ConfigSynchronizationService syncService;

  private List<ResourceEvent> resourceEvents;

  @BeforeEach
  void setUp() {
    resourceEvents = List.of(new ResourceEvent().id(randomId()), new ResourceEvent().id(randomId()));
  }

  @Test
  void shouldSyncClassificationTypeResources() {
    syncService.sync(resourceEvents, ResourceType.CLASSIFICATION_TYPE);

    var expectedIds = resourceEvents.stream().map(ResourceEvent::getId).toList();
    verify(configService).deleteTypeIdsFromConfigs(BrowseType.INSTANCE_CLASSIFICATION, expectedIds);
  }

  @Test
  void shouldSyncCallNumberTypeResources() {
    syncService.sync(resourceEvents, ResourceType.CALL_NUMBER_TYPE);

    var expectedIds = resourceEvents.stream().map(ResourceEvent::getId).toList();
    verify(configService).deleteTypeIdsFromConfigs(BrowseType.INSTANCE_CALL_NUMBER, expectedIds);
  }

  @Test
  void shouldNotSync_WhenNullResourceType() {
    syncService.sync(resourceEvents, null);

    verify(configService, never()).deleteTypeIdsFromConfigs(any(), anyList());
  }

  @NullAndEmptySource
  @ParameterizedTest
  void shouldNotSync_WhenNullResources(List<ResourceEvent> resourceEvents) {
    syncService.sync(resourceEvents, ResourceType.CLASSIFICATION_TYPE);

    verify(configService, never()).deleteTypeIdsFromConfigs(any(), anyList());
  }
}
