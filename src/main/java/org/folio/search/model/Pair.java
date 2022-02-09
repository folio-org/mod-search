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

  /**
   * Creates pair object from given first and second objects.
   *
   * @param first - First value of pair object.
   * @param second - Second value of pair object.
   * @param <F> - first value generic type
   * @param <S> - second value generic type
   * @return created {@link Pair} object
   */
  public static <F, S> Pair<F, S> pair(F first, S second) {
    return new Pair<>(first, second);
  }
}
