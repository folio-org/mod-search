package org.folio.search.integration.folio;

import static org.folio.search.client.InventoryReindexRecordsClient.constructRequest;
import static org.folio.search.model.types.ReindexEntityType.INSTANCE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.folio.search.client.InventoryInstanceClient;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.configuration.RetryTemplateConfiguration;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@UnitTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {InventoryService.class, RetryTemplateConfiguration.class, FolioKafkaProperties.class,
                                 StreamIdsProperties.class, ReindexConfigurationProperties.class})
class InventoryServiceTest {

  @MockitoBean
  private InventoryInstanceClient inventoryInstanceClient;
  @MockitoBean
  private InventoryReindexRecordsClient reindexRecordsClient;

  @Autowired
  private InventoryService inventoryService;

  @Test
  void fetchInventoryRecordsCount_WhenRecordTypeIsNull() {
    int count = inventoryService.fetchInventoryRecordsCount(null);
    assertEquals(0, count);
  }

  @Test
  void fetchInventoryRecordsCount_ShouldReturnCorrectCount() {
    var uri = URI.create("instance-storage/instances?limit=0&totalRecords=exact");
    when(inventoryInstanceClient.getInventoryRecordsCount(uri)).thenReturn(
      new InventoryInstanceClient.InventoryRecordsCountDto(10));
    int count = inventoryService.fetchInventoryRecordsCount(InventoryRecordType.INSTANCE);
    assertEquals(10, count);
  }

  @Test
  void fetchInventoryRecordsCount_ShouldThrowExceptionOnClientFailure() {
    when(inventoryInstanceClient.getInventoryRecordsCount(any())).thenThrow(new RuntimeException("API failure"));
    assertThrows(FolioIntegrationException.class,
      () -> inventoryService.fetchInventoryRecordsCount(InventoryRecordType.INSTANCE));
  }

  @Test
  void publishReindexRecordsRange_ShouldIgnoreInvalidInput() {
    inventoryService.publishReindexRecordsRange(null);
    verify(reindexRecordsClient, never()).publishReindexRecords(any());
  }

  @Test
  void publishReindexRecordsRange_ValidExecutionPath() {
    var id = UUID.randomUUID();
    var validRange = new MergeRangeEntity(id, id, INSTANCE, TENANT_ID, "low", "high", Timestamp.from(
      Instant.now()), null, null);
    var request = constructRequest(id.toString(), id.toString(), INSTANCE.getType(), "low", "high");
    doNothing().when(reindexRecordsClient).exportReindexRecords(request);

    inventoryService.publishReindexRecordsRange(validRange);
    verify(reindexRecordsClient).exportReindexRecords(request);
  }

  @Test
  void publishReindexRecordsRange_ShouldRetryOnFailure() {
    var id = UUID.randomUUID();
    var validRange = new MergeRangeEntity(id, id, INSTANCE, TENANT_ID, "low", "high", Timestamp.from(
      Instant.now()), null, null);
    var request = constructRequest(id.toString(), id.toString(), INSTANCE.getType(), "low", "high");
    doThrow(new RuntimeException("API failure")).when(reindexRecordsClient).exportReindexRecords(request);

    assertThrows(FolioIntegrationException.class, () -> inventoryService.publishReindexRecordsRange(validRange));

    verify(reindexRecordsClient, times(6)).exportReindexRecords(request);
  }
}
