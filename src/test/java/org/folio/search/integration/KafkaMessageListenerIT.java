package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.CREATE;
import static org.folio.search.model.service.ResultList.asSinglePage;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.SearchApplication;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.TenantNotInitializedException;
import org.folio.search.integration.error.KafkaErrorHandler;
import org.folio.search.integration.inventory.InventoryViewClient;
import org.folio.search.support.extension.EnablePostgres;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.support.GenericMessage;

@IntegrationTest
@SpringBootTest(classes = SearchApplication.class)
@EnableAutoConfiguration
@EnablePostgres
class KafkaMessageListenerIT {
  @SpyBean
  private KafkaErrorHandler errorHandler;
  @MockBean
  private InventoryViewClient inventoryViewClient;
  @Autowired
  private KafkaMessageListener messageListener;

  @Test
  void shouldReConsumeMessageWhenTenantNotInitialized() {
    var tenantName = "not_existent_tenant";
    var instance = new Instance().id(randomId());
    var consumerRecords = List.of(
      new ConsumerRecord<>("inventory.instance", 0, 0, "id",
        new ResourceEventBody().type(CREATE)
          .tenant(tenantName)._new(Map.of("id", randomId()))));

    when(inventoryViewClient.getInstances(any(), anyInt()))
      .thenReturn(asSinglePage(new InventoryViewClient.InstanceView(
        instance, emptyList(), emptyList())));

    var exception = runAndReturnException(() -> {
      messageListener.handleEvents(consumerRecords);
      return null;
    });

    var handledException = runAndReturnException(() ->
      errorHandler.handleError(new GenericMessage<>(consumerRecords),
        new ListenerExecutionFailedException("Error", exception)));

    assertThat(handledException)
      .isInstanceOf(TenantNotInitializedException.class)
      .hasMessage("Following tenants might not be initialized yet: [not_existent_tenant]");
  }

  private Exception runAndReturnException(Callable<?> job) {
    try {
      job.call();
      throw new AssertionError("Expected exception");
    } catch (Exception ex) {
      return ex;
    }
  }
}
