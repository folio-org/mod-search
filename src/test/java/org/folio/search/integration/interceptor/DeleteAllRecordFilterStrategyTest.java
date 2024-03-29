package org.folio.search.integration.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DeleteAllRecordFilterStrategyTest {

  @Mock
  private PrimaryResourceRepository primaryResourceRepository;

  @InjectMocks
  private DeleteAllRecordFilterStrategy deleteAllRecordFilterStrategy;

  private ConsumerRecord<String, ResourceEvent> consumerRecord;
  private ResourceEvent resourceEvent;

  @BeforeEach
  public void setUp() {
    resourceEvent = new ResourceEvent();
    resourceEvent.setType(ResourceEventType.DELETE_ALL);
    resourceEvent.setResourceName("testResource");
    resourceEvent.setTenant("testTenant");

    consumerRecord = new ConsumerRecord<>("topic", 1, 1, "testKey", resourceEvent);
  }

  @Test
  public void testDeleteAllEventIsFilteredOut() {
    assertTrue(deleteAllRecordFilterStrategy.filter(consumerRecord));
    verify(primaryResourceRepository).deleteResourceByTenantId(resourceEvent.getResourceName(),
      resourceEvent.getTenant());
  }

  @Test
  public void testNonDeleteAllEventIsNotFilteredOut() {
    resourceEvent.setType(ResourceEventType.CREATE);
    assertFalse(deleteAllRecordFilterStrategy.filter(consumerRecord));
    verify(primaryResourceRepository, never()).deleteResourceByTenantId(resourceEvent.getResourceName(),
      resourceEvent.getTenant());
  }
}
