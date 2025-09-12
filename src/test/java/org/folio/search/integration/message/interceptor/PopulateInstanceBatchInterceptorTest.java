package org.folio.search.integration.message.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.folio.spring.exception.SystemUserAuthorizationException;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopulateInstanceBatchInterceptorTest {

  private static final String TENANT_ID = "tenantId";

  @Mock
  private ConsortiumTenantExecutor executionService;
  @Mock
  private SystemUserScopedExecutionService systemUserScopedExecutionService;
  @Mock
  private MergeInstanceRepository instanceRepository;
  @Mock
  private Consumer<String, ResourceEvent> consumer;

  private PopulateInstanceBatchInterceptor populateInstanceBatchInterceptor;

  @BeforeEach
  void setUp() {
    when(instanceRepository.entityType()).thenCallRealMethod();
    populateInstanceBatchInterceptor = new PopulateInstanceBatchInterceptor(
      List.of(instanceRepository),
      executionService,
      systemUserScopedExecutionService
    );
  }

  @Test
  void shouldHandleSystemUserAuthorizationExceptionInIntercept() {
    // Arrange
    var resourceEvent = new ResourceEvent().tenant(TENANT_ID).resourceName("instance");
    var consumerRecord = new ConsumerRecord<>("topic", 0, 0L, "key", resourceEvent);
    var records = new ConsumerRecords<>(Map.of(new TopicPartition("topic", 0), List.of(consumerRecord)));

    doThrow(new SystemUserAuthorizationException("Authorization failed"))
      .when(systemUserScopedExecutionService).executeSystemUserScoped(eq(TENANT_ID), any());

    // Act
    populateInstanceBatchInterceptor.intercept(records, consumer);

    // Assert
    verify(systemUserScopedExecutionService).executeSystemUserScoped(eq(TENANT_ID), any());
    verify(executionService, never()).execute(any());
  }

  @Test
  void shouldProcessRecordsSuccessfullyInIntercept() {
    // Arrange
    mockExecutionServices();

    var resourceEvent = new ResourceEvent().tenant(TENANT_ID).resourceName("instance");
    var consumerRecord = new ConsumerRecord<>("topic", 0, 0L, "key", resourceEvent);
    var records = new ConsumerRecords<>(Map.of(new TopicPartition("topic", 0), List.of(consumerRecord)));

    // Act
    populateInstanceBatchInterceptor.intercept(records, consumer);

    // Assert
    verify(systemUserScopedExecutionService).executeSystemUserScoped(eq(TENANT_ID), any());
    verify(executionService).execute(any());
  }

  @Test
  void shouldProcessOnlyLatestRecordsSuccessfullyInIntercept() {
    // Arrange
    mockExecutionServices();

    var now = System.currentTimeMillis();
    var expected = Map.<String, Object>of("id", 3);
    var consumerRecord1 = createConsumerRecord(Map.of("id", 1), now);
    var consumerRecord2 = createConsumerRecord(Map.of("id", 2), now + 1);
    var consumerRecord3 = createConsumerRecord(expected, now + 2);
    var records = new ConsumerRecords<>(Map.of(new TopicPartition("topic", 0),
      List.of(consumerRecord3, consumerRecord2, consumerRecord1)));

    // Act
    populateInstanceBatchInterceptor.intercept(records, consumer);

    // Assert
    verify(instanceRepository).saveEntities(TENANT_ID, List.of(expected));
  }

  private void mockExecutionServices() {
    doAnswer(invocation -> {
      Supplier<?> operation = invocation.getArgument(0);
      return operation.get();
    }).when(executionService).execute(any(Supplier.class));

    doAnswer(invocation -> {
      Callable<?> action = invocation.getArgument(1);
      return action.call();
    }).when(systemUserScopedExecutionService).executeSystemUserScoped(any(String.class), any(Callable.class));
  }

  private ConsumerRecord<String, ResourceEvent> createConsumerRecord(Map<String, Object> resourceNew, long timestamp) {
    var resourceEvent = new ResourceEvent()
      .tenant(TENANT_ID)
      .resourceName("instance")
      ._new(resourceNew);
    return new ConsumerRecord<>("topic", 0, 0L, timestamp, TimestampType.CREATE_TIME, 0, 0, "key",
      resourceEvent, new RecordHeaders(), Optional.empty());
  }
}
