package org.folio.search.integration;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.model.Pair;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioMessageBatchProcessorTest {

  private final RetryTemplate retryTemplate = spy(RetryTemplate.builder().maxAttempts(3).fixedBackoff(1).build());
  private final RetryTemplate customRetryTemplate = spy(RetryTemplate.builder().maxAttempts(5).fixedBackoff(1).build());

  private final FolioMessageBatchProcessor folioMessageBatchProcessor = new FolioMessageBatchProcessor(
    mapOf("default", retryTemplate, "custom", customRetryTemplate), retryTemplate);

  @Test
  void consumeBatchWithFallback_positive() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    folioMessageBatchProcessor.consumeBatchWithFallback(List.of(1, 2, 3), "custom",
      consumedMessages::addAll, (value, err) -> failedMessages.add(Pair.of(value, err)));

    assertThat(consumedMessages).containsExactly(1, 2, 3);
    assertThat(failedMessages).isEmpty();
    verify(customRetryTemplate).execute(any());
    verify(retryTemplate, never()).execute(any());
  }

  @Test
  void consumeBatchWithFallback_positive_batchIsNull() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    folioMessageBatchProcessor.consumeBatchWithFallback((List<Integer>) null, "custom",
      consumedMessages::addAll, (value, err) -> failedMessages.add(Pair.of(value, err)));

    assertThat(consumedMessages).isEmpty();
    assertThat(failedMessages).isEmpty();
    verifyNoInteractions(customRetryTemplate);
    verify(retryTemplate, never()).execute(any());
  }

  @Test
  void consumeBatchWithFallback_positive_noBeanName() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    folioMessageBatchProcessor.consumeBatchWithFallback(List.of(1, 2, 3), null,
      consumedMessages::addAll, (value, err) -> failedMessages.add(Pair.of(value, err)));

    assertThat(consumedMessages).containsExactly(1, 2, 3);
    assertThat(failedMessages).isEmpty();
    verify(retryTemplate).execute(any());
    verify(customRetryTemplate, never()).execute(any());
  }

  @Test
  void consumeBatchWithFallback_positive_retryBatch() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    var attemptsCounter = new AtomicInteger(1);
    folioMessageBatchProcessor.consumeBatchWithFallback(List.of(1, 2, 3), "custom",
      attemptThrowingConsumer(consumedMessages, attemptsCounter, 2),
      (value, err) -> failedMessages.add(Pair.of(value, err)));
    assertThat(consumedMessages).containsExactly(1, 2, 3);
    assertThat(failedMessages).isEmpty();
  }

  @Test
  void consumeBatchWithFallback_positive_processingOneByOne() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    var attemptsCounter = new AtomicInteger(1);
    folioMessageBatchProcessor.consumeBatchWithFallback(List.of(1, 2, 3), null,
      attemptThrowingConsumer(consumedMessages, attemptsCounter, 3),
      (value, err) -> failedMessages.add(Pair.of(value, err)));
    assertThat(consumedMessages).containsExactly(1, 2, 3);
    assertThat(failedMessages).isEmpty();
  }

  @Test
  void consumeBatchWithFallback_positive_failedBatchOfSingleValue() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    var attemptsCounter = new AtomicInteger(1);
    folioMessageBatchProcessor.consumeBatchWithFallback(singletonList(1), null,
      attemptThrowingConsumer(consumedMessages, attemptsCounter, 3),
      (value, err) -> failedMessages.add(Pair.of(value, err)));
    assertThat(consumedMessages).isEmpty();
    assertThat(failedMessages).hasSize(1).satisfies(list -> verifyFailedMessage(list.get(0), 1));
  }

  @Test
  void consumeBatchWithFallback_positive_failedBatchOneByOne() {
    var consumedMessages = new ArrayList<Integer>();
    var failedMessages = new ArrayList<Pair<Integer, Exception>>();
    var attemptsCounter = new AtomicInteger(1);
    folioMessageBatchProcessor.consumeBatchWithFallback(List.of(1, 2, 3), null,
      attemptThrowingConsumer(consumedMessages, attemptsCounter, 12),
      (value, err) -> failedMessages.add(Pair.of(value, err)));
    assertThat(failedMessages).hasSize(3).satisfies(list -> {
      verifyFailedMessage(list.get(0), 1);
      verifyFailedMessage(list.get(1), 2);
      verifyFailedMessage(list.get(2), 3);
    });
  }

  private void verifyFailedMessage(Pair<Integer, Exception> value, int expectedValue) {
    assertThat(value.getFirst()).isEqualTo(expectedValue);
    assertThat(value.getSecond()).isInstanceOf(RuntimeException.class);
  }

  private static Consumer<List<Integer>> attemptThrowingConsumer(List<Integer> list, AtomicInteger cnt, int max) {
    return values -> {
      var currentAttempt = cnt.getAndIncrement();
      if (currentAttempt <= max) {
        throw new RuntimeException("error");
      }
      list.addAll(values);
    };
  }
}
