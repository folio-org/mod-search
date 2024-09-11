package org.folio.search.integration.message.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

@UnitTest
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompositeRecordFilterStrategyTest {

  private @Mock RecordFilterStrategy<String, Integer> mockStrategy1;
  private @Mock RecordFilterStrategy<String, Integer> mockStrategy2;

  @Test
  void testRecordIsFilteredOut() {
    var testRecord = new ConsumerRecord<>("topic", 0, 0, "testkey", 100);
    when(mockStrategy1.filter(any())).thenReturn(false);
    when(mockStrategy2.filter(any())).thenReturn(true);

    var compositeStrategy = new CompositeRecordFilterStrategy<>(mockStrategy1, mockStrategy2);
    assertTrue(compositeStrategy.filter(testRecord));
  }

  @Test
  void testRecordNotFilteredOut() {
    var testRecord = new ConsumerRecord<>("topic", 0, 0, "testkey", 100);
    when(mockStrategy1.filter(any())).thenReturn(false);
    when(mockStrategy2.filter(any())).thenReturn(false);

    var compositeStrategy = new CompositeRecordFilterStrategy<>(mockStrategy1, mockStrategy2);
    assertFalse(compositeStrategy.filter(testRecord));
  }

  @Test
  void testRecordIsFilteredOutByFirstStrategy() {
    var testRecord = new ConsumerRecord<>("topic", 0, 0, "testkey", 100);
    when(mockStrategy1.filter(any())).thenReturn(true);
    when(mockStrategy2.filter(any())).thenReturn(false);

    var compositeStrategy = new CompositeRecordFilterStrategy<>(mockStrategy1, mockStrategy2);
    assertTrue(compositeStrategy.filter(testRecord));

    // As the record is already filtered out by the first strategy,
    // the second one should not be called
    verify(mockStrategy2, never()).filter(any());
  }
}
