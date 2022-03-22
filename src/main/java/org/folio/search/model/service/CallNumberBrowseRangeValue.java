package org.folio.search.model.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;

@Data
@RequiredArgsConstructor(staticName = "of")
public class CallNumberBrowseRangeValue implements Comparable<CallNumberBrowseRangeValue> {

  /**
   * Value for call number browse range cache.
   */
  private final String key;

  /**
   * Numeric representation of key.
   */
  private final long keyAsLong;

  /**
   * Amount of result above the current key and before the next one.
   */
  private final long count;

  @Override
  public int compareTo(@NonNull CallNumberBrowseRangeValue value) {
    return StringUtils.compare(key, value.getKey());
  }
}
