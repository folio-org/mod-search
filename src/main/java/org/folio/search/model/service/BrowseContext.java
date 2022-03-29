package org.folio.search.model.service;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;

@Data
@Builder
@RequiredArgsConstructor
public class BrowseContext {

  private final RangeQueryBuilder precedingQuery;
  private final RangeQueryBuilder succeedingQuery;
  private final String anchor;

  private final Integer precedingLimit;
  private final Integer succeedingLimit;

  /**
   * List of query filters from user request.
   */
  @Builder.Default
  private final List<QueryBuilder> filters = Collections.emptyList();

  /**
   * Checks if created {@link BrowseContext} is purposed for browsing around.
   *
   * @return true - if context is purposed for browsing around
   */
  public boolean isAroundBrowsing() {
    return this.precedingQuery != null && this.succeedingQuery != null;
  }

  /**
   * Checks if created {@link BrowseContext} is purposed for browsing forward.
   *
   * @return true - if context is purposed for browsing forward
   */
  public boolean isBrowsingForward() {
    return this.succeedingQuery != null;
  }

  /**
   * Checks if anchor is included in the range query or not using browsing direction flag.
   *
   * @return {@code true} if anchor is included, {@code false} - otherwise
   */
  public boolean isAnchorIncluded(boolean isForward) {
    return isForward ? this.succeedingQuery.includeLower() : this.precedingQuery.includeUpper();
  }

  /**
   * Checks if anchor is included in the range query or not.
   *
   * @return {@code true} if anchor is included, {@code false} - otherwise
   */
  public boolean isAnchorIncluded() {
    return isAnchorIncluded(true);
  }

  /**
   * Returns limit for browsing.
   */
  public int getLimit(boolean isForward) {
    return isForward ? this.succeedingLimit : this.precedingLimit;
  }
}
