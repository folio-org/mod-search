package org.folio.search.integration.error;

import static org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.TenantNotInitializedException;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class KafkaErrorHandler implements KafkaListenerErrorHandler {
  @Override
  public Object handleError(Message<?> message, ListenerExecutionFailedException exception) {
    if (isTenantNotInitializedYet(message, exception)) {
      throw new TenantNotInitializedException(getTenantNames(message), exception.getCause());
    }

    throw exception;
  }

  private boolean isTenantNotInitializedYet(Message<?> message, ListenerExecutionFailedException exception) {
    // In case when the schema for tenant is not created
    // DB will throw an error saying that a table does not exists
    // which is treated as a grammar exception.
    // It is possible to have false positives here, when an invalid SQL is used
    // but anyway it results in retrying the consume operation
    return getThrowableList(exception).stream().anyMatch(SQLGrammarException.class::isInstance)
      && isSupportedMessageType(message);
  }

  @SuppressWarnings("unchecked")
  private String[] getTenantNames(Message<?> message) {
    var consumerRecords = (List<ConsumerRecord<String, ResourceEventBody>>) message.getPayload();
    return consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(ResourceEventBody::getTenant)
      .distinct()
      .toArray(String[]::new);
  }

  @SuppressWarnings("rawtypes")
  private boolean isSupportedMessageType(Message<?> message) {
    if (!(message.getPayload() instanceof List)) {
      return false;
    }

    var payloads = (List) message.getPayload();
    return !payloads.isEmpty() && payloads.get(0) instanceof ConsumerRecord
      && ((ConsumerRecord) payloads.get(0)).value() instanceof ResourceEventBody;
  }
}
