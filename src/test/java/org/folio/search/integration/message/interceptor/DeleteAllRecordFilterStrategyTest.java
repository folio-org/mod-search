package org.folio.search.integration.message.interceptor;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DeleteAllRecordFilterStrategyTest {

  private final ResourceType resourceType = ResourceType.INSTANCE;

  @Mock
  private PrimaryResourceRepository primaryResourceRepository;
  @InjectMocks
  private DeleteAllRecordFilterStrategy deleteAllRecordFilterStrategy;

  private ConsumerRecord<String, ResourceEvent> consumerRecord;
  private ResourceEvent resourceEvent;

  @BeforeEach
  void setUp() {
    resourceEvent = new ResourceEvent();
    resourceEvent.setType(ResourceEventType.DELETE_ALL);
    resourceEvent.setResourceName(resourceType.getName());
    resourceEvent.setTenant(TENANT_ID);

    consumerRecord = new ConsumerRecord<>("topic", 1, 1, "testKey", resourceEvent);
  }

  @Test
  void testDeleteAllEventIsFilteredOut() {
    assertTrue(deleteAllRecordFilterStrategy.filter(consumerRecord));
    verify(primaryResourceRepository).deleteResourceByTenantId(resourceType, resourceEvent.getTenant());
  }

  @Test
  void testNonDeleteAllEventIsNotFilteredOut() {
    resourceEvent.setType(ResourceEventType.CREATE);
    assertFalse(deleteAllRecordFilterStrategy.filter(consumerRecord));
    verify(primaryResourceRepository, never()).deleteResourceByTenantId(resourceType, resourceEvent.getTenant());
  }
}
