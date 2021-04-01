package org.folio.search.integration;

import static org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.integration.error.KafkaErrorHandler;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;

@IntegrationTest
class KafkaMessageListenerIT extends BaseIntegrationTest {
  @SpyBean
  private KafkaErrorHandler errorHandler;
  @SpyBean
  private ResourceFetchService fetchService;

  @Test
  void shouldReConsumeMessageWhenTenantNotInitialized() {
    var tenantName = "not_existent_tenant";
    inventoryApi.createInstance(tenantName,
      new Instance().id(randomId()));

    await().untilAsserted(() -> {
      var messageCaptor = forClass(Message.class);
      var exceptionCaptor = forClass(ListenerExecutionFailedException.class);

      verify(errorHandler, times(2)).handleError(messageCaptor.capture(), exceptionCaptor.capture());

      messageCaptor.getAllValues().forEach(message -> {
        @SuppressWarnings("unchecked")
        var consumerRecords = (List<ConsumerRecord<String, ResourceEventBody>>) message.getPayload();
        assertThat(consumerRecords).hasSizeGreaterThanOrEqualTo(1);
        assertThat(consumerRecords.get(0).value())
          .extracting(ResourceEventBody::getTenant).isEqualTo(tenantName);
      });

      exceptionCaptor.getAllValues()
        .forEach(ex -> assertThat(getThrowableList(ex))
          .hasAtLeastOneElementOfType(SQLGrammarException.class));
    });
  }
}
