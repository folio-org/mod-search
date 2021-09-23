package org.folio.search.model.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(staticName = "empty")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MultilangValue {

  /**
   * List of plain values.
   */
  private Set<String> plainValues = new LinkedHashSet<>();

  /**
   * List of multi-language values.
   */
  private Set<String> multilangValues = new LinkedHashSet<>();

  /**
   * Adds value to the {@link MultilangValue} object, using isMultilang flag.
   *
   * @param value string value as {@link String} object
   * @param isMultilang isMultilang flag as {@code boolean} value.
   */
  public void addValue(String value, boolean isMultilang) {
    if (isMultilang) {
      multilangValues.add(value);
      return;
    }
    plainValues.add(value);
  }

  /**
   * Creates empty {@link MultilangValue} object.
   *
   * @return empty {@link MultilangValue} object with immutable collections inside.
   */
  public static MultilangValue of(Collection<String> plainValues, Collection<String> multilangValues) {
    return new MultilangValue(new LinkedHashSet<>(plainValues), new LinkedHashSet<>(multilangValues));
  }
}
