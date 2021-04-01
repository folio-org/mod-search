package org.folio.search.integration.error;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.TenantNotInitializedException;
import org.folio.search.utils.types.UnitTest;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

@UnitTest
class KafkaErrorHandlerTest {
  private final KafkaErrorHandler errorHandler = new KafkaErrorHandler();

  @Test
  void shouldThrowTenantInitializationException() {
    var message = buildResourceEvents();
    var exception = buildException(new SQLGrammarException("grammar", new SQLException()));

    assertThatThrownBy(() -> errorHandler.handleError(message, exception))
      .isInstanceOf(TenantNotInitializedException.class)
      .hasMessage("Following tenants might not be initialized yet: [one, two]");
  }

  @Test
  void shouldThrowTenantInitializationExceptionEventWithUnsupportedMessage() {
    var message = buildMessage(Map.of());
    var exception = buildException(new SQLGrammarException("grammar", new SQLException()));

    assertThatThrownBy(() -> errorHandler.handleError(message, exception))
      .isInstanceOf(TenantNotInitializedException.class)
      .hasMessage("Following tenants might not be initialized yet: null");
  }

  @Test
  void shouldThrowOriginalCauseIfNotTenantInitException() {
    var message = buildResourceEvents();
    var exception = buildException(new IllegalStateException("illegal state exception"));

    assertThatThrownBy(() -> errorHandler.handleError(message, exception))
      .isInstanceOf(ListenerExecutionFailedException.class)
      .hasCauseExactlyInstanceOf(IllegalStateException.class);
  }

  private Message<List<ConsumerRecord<String, ResourceEventBody>>> buildResourceEvents() {
    var payloads = List.of(new ResourceEventBody().tenant("one"),
      new ResourceEventBody().tenant("two"));

    var consumerRecords = payloads.stream()
      .map(payload -> new ConsumerRecord<>("topic", 1, 0, "key", payload))
      .collect(Collectors.toList());

    return buildMessage(consumerRecords);
  }

  private <T> Message<T> buildMessage(T payload) {
    return new GenericMessage<>(payload);
  }

  private ListenerExecutionFailedException buildException(Throwable cause) {
    return new ListenerExecutionFailedException("exception", cause);
  }
}
