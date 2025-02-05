package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.junit.Assert.assertEquals;

import java.sql.BatchUpdateException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.AggregatedBatchUpdateException;

class LogUtilsTest {

  private static final List<String> BIG_LIST = Arrays.asList("One", "Two", "Three");
  private static final String MSG = "size of list ";
  private static final String SINGLE_EXCEPTION_MSG = buildSingleExceptionMessage();
  private static final String NESTED_EXCEPTION_MSG = buildNestedExceptionMessage();

  @Test
  void collectionToLogMsg_withoutHidingItems() {
    var actual = collectionToLogMsg(BIG_LIST, false);
    assertThat(actual).isEqualTo(BIG_LIST.toString());
  }

  @Test
  void testCollectionToLogMsg_withoutHidingEmptyList() {
    var actual = collectionToLogMsg(null, false);
    assertThat(actual).isEqualTo(MSG + 0);
  }

  @Test
  void collectionToLogMsg_Items() {
    var actual = collectionToLogMsg(BIG_LIST, false);
    assertThat(actual).isEqualTo(BIG_LIST.toString());
  }

  @Test
  void testCollectionToLogMsg_EmptyList() {
    var actual = collectionToLogMsg(null, false);
    assertThat(actual).isEqualTo(MSG + 0);
  }

  @Test
  void collectionToLogMsg_withHidingItems() {
    var actual = collectionToLogMsg(BIG_LIST, true);
    assertThat(actual).isEqualTo(MSG + BIG_LIST.size());
  }

  @Test
  void testCollectionToLogMsg_withHidingEmptyList() {
    var actual = collectionToLogMsg(null, true);
    assertThat(actual).isEqualTo(MSG + 0);
  }

  @Test
  void shouldCollectExceptionMsgFromSingleException() {
    var exception = new PessimisticLockingFailureException("Test exception");

    var result = LogUtils.collectExceptionMsg(exception);

    assertEquals(SINGLE_EXCEPTION_MSG, result);
  }

  @Test
  void shouldCollectExceptionMsgFromNestedExceptions() {
    BatchUpdateException batchUpdateException = new BatchUpdateException("Nested exception", new int[0]);
    var aggregatedException = new AggregatedBatchUpdateException(new int[0][0], batchUpdateException);
    var exception = new PessimisticLockingFailureException("Test exception", aggregatedException);

    var result = LogUtils.collectExceptionMsg(exception);

    assertEquals(NESTED_EXCEPTION_MSG, result);
  }

  @Test
  void shouldNotCollectExceptionMsgIfExceptionIsNull() {
    var result = LogUtils.collectExceptionMsg(null);

    assertEquals("", result);
  }

  private static String buildSingleExceptionMessage() {
    return String.format("%s%s", System.lineSeparator(),
      "org.springframework.dao.PessimisticLockingFailureException: Test exception");
  }

  private static String buildNestedExceptionMessage() {
    return String.format("%s%s%s", buildSingleExceptionMessage(), System.lineSeparator(),
      "org.springframework.jdbc.core.AggregatedBatchUpdateException: Nested exception");
  }
}
