package org.folio.search.integration.message.interceptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.util.Assert;

public class CompositeRecordFilterStrategy<K, V> implements RecordFilterStrategy<K, V> {

  private final Collection<RecordFilterStrategy<K, V>> delegates = new ArrayList<>();

  /**
   * Construct an instance with the provided delegates.
   *
   * @param delegates the delegates.
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public CompositeRecordFilterStrategy(RecordFilterStrategy<K, V>... delegates) {
    Assert.notNull(delegates, "'delegates' cannot be null");
    Assert.noNullElements(delegates, "'delegates' cannot have null entries");
    this.delegates.addAll(Arrays.asList(delegates));
  }

  @Override
  public boolean filter(@NotNull ConsumerRecord<K, V> consumerRecord) {
    for (RecordFilterStrategy<K, V> delegate : delegates) {
      if (delegate.filter(consumerRecord)) {
        return true;
      }
    }
    return false;
  }
}
