package org.folio.search.model.types;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Getter
@RequiredArgsConstructor
public enum InventorySearchType {

  TITLE("title");

  @JsonValue
  private final String value;

  /**
   * Creates {@link InventorySearchType} object from {@link String} value.
   *
   * @param value string value to process
   * @return found {@link InventorySearchType} object, null if passed value is not matched
   */
  public static Optional<InventorySearchType> of(String value) {
    for (InventorySearchType inventorySearchType : values()) {
      if (StringUtils.equalsIgnoreCase(value, inventorySearchType.value)) {
        return Optional.of(inventorySearchType);
      }
    }
    return Optional.empty();
  }
}
