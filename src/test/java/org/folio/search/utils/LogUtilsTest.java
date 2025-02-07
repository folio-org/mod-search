package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogUtilsTest {

  private static final List<String> BIG_LIST = Arrays.asList("One", "Two", "Three");
  private static final String MSG = "size of list ";

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
}
