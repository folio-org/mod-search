package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogUtilsTest {

  private static final List<String> BIG_LIST = Arrays.asList("One", "Two", "Three");
  private static final List<String> SMALL_LIST = Arrays.asList("One", "Two");
  private static final String MSG = "size of list ";


  @Test
  void collectionToLogMsg_moreThanThreeItems() {
    var actual = collectionToLogMsg(BIG_LIST);
    assertThat(actual).isEqualTo(MSG + BIG_LIST.size());
  }

  @Test
  void testCollectionToLogMsg_lessThenThreeItems() {
    var actual = collectionToLogMsg(SMALL_LIST);
    assertThat(actual).isEqualTo(SMALL_LIST.toString());
  }

  @Test
  void testCollectionToLogMsg_nullOrEmptyList() {
    var actual = collectionToLogMsg(null);
    assertThat(actual).isEqualTo(MSG + 0);
  }

  @Test
  void collectionToLogMsg_hideDisabled_moreThanThreeItems() {
    var actual = collectionToLogMsg(BIG_LIST, false);
    assertThat(actual).isEqualTo(MSG + BIG_LIST.size());
  }

  @Test
  void testCollectionToLogMsg_hideEnabled_lessThenThreeItems() {
    var actual = collectionToLogMsg(SMALL_LIST, true);
    assertThat(actual).isEqualTo(MSG + SMALL_LIST.size());
  }

  @Test
  void testCollectionToLogMsg_hideEnabled_nullOrEmptyList() {
    var actual = collectionToLogMsg(new ArrayList<>(), true);
    assertThat(actual).isEqualTo(MSG + 0);
  }
}
