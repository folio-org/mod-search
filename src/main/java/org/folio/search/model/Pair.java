package org.folio.search.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Defines generic pair object that can hold 2 values.
 *
 * @param <L> left value generic type
 * @param <R> right value generic type
 */
@Data
@AllArgsConstructor(staticName = "of")
public class Pair<L, R> {

  /**
   * First value of pair object.
   */
  private L first;

  /**
   * Second value of pair object.
   */
  private R second;
}
