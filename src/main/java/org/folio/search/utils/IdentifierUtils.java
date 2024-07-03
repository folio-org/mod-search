package org.folio.search.utils;

import static java.lang.String.format;
import static org.folio.search.domain.dto.BatchIdsDto.IdentifierTypeEnum.INSTANCEHRID;
import static org.folio.search.domain.dto.BatchIdsDto.IdentifierTypeEnum.ITEMBARCODE;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Item;


@UtilityClass
public class IdentifierUtils {

  public static final String HOLDING_IDENTIFIER_TYPE_NOT_SUPPORTED_PATTERN
    = "Holding identifier type %s isn't supported";

  public static Set<String> getItemIdentifierValue(BatchIdsDto.IdentifierTypeEnum identifierType, Item item) {
    switch (identifierType) {
      case ID -> {
        return Objects.nonNull(item.getId()) ? Set.of(item.getId()) : Set.of();
      }
      case HRID -> {
        return Objects.nonNull(item.getHrid()) ? Set.of(item.getHrid()) : Set.of();
      }
      case BARCODE -> {
        return Objects.nonNull(item.getBarcode()) ? Set.of(item.getBarcode()) : Set.of();
      }
      case ACCESSIONNUMBER -> {
        return Objects.nonNull(item.getAccessionNumber()) ? Set.of(item.getAccessionNumber()) : Set.of();
      }
      case FORMERIDS -> {
        return new HashSet<>(item.getFormerIds()); }
      case HOLDINGSRECORDID -> {
        return Objects.nonNull(item.getHoldingsRecordId()) ? Set.of(item.getHoldingsRecordId()) : Set.of();
      }
      default -> {
        return Set.of();
      }
    }
  }

  public static Set<String> getHoldingIdentifierValue(BatchIdsDto.IdentifierTypeEnum identifierType, Holding holding) {
    switch (identifierType) {
      case ID -> {
        return Objects.nonNull(holding.getId()) ? Set.of(holding.getId()) : Set.of();
      }
      case HRID -> {
        return Objects.nonNull(holding.getHrid()) ? Set.of(holding.getHrid()) : Set.of();
      }
      case INSTANCEHRID -> throw new UnsupportedOperationException(
        format(HOLDING_IDENTIFIER_TYPE_NOT_SUPPORTED_PATTERN, INSTANCEHRID.getValue()));
      case ITEMBARCODE -> throw new UnsupportedOperationException(
        format(HOLDING_IDENTIFIER_TYPE_NOT_SUPPORTED_PATTERN, ITEMBARCODE.getValue()));
      default -> throw new UnsupportedOperationException(
        format(HOLDING_IDENTIFIER_TYPE_NOT_SUPPORTED_PATTERN, identifierType.getValue()));
    }
  }
}
