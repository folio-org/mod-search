package org.folio.search.utils;

import static java.lang.String.format;
import static org.folio.search.utils.SearchUtils.INSTANCE_HOLDING_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;

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
  public static final String VALUE_PATTERN = "%s.%s";

  public static String getHoldingTargetField(BatchIdsDto.IdentifierTypeEnum identifierType) {
    return switch (identifierType) {
      case ITEM_BARCODE -> "items.barcode";
      case INSTANCE_HRID -> "hrid";
      default -> format(VALUE_PATTERN, INSTANCE_HOLDING_FIELD_NAME, identifierType.getValue());
    };
  }

  public static String getItemTargetField(BatchIdsDto.IdentifierTypeEnum identifierType) {
    return format(VALUE_PATTERN, INSTANCE_ITEM_FIELD_NAME, identifierType.getValue());
  }

  public static Set<String> getItemIdentifierValue(BatchIdsDto.IdentifierTypeEnum identifierType, Item item) {
    return switch (identifierType) {
      case ID -> Objects.nonNull(item.getId()) ? Set.of(item.getId()) : Set.of();
      case HRID -> Objects.nonNull(item.getHrid()) ? Set.of(item.getHrid()) : Set.of();
      case BARCODE -> Objects.nonNull(item.getBarcode()) ? Set.of(item.getBarcode()) : Set.of();
      case ACCESSION_NUMBER -> Objects.nonNull(item.getAccessionNumber())
        ? Set.of(item.getAccessionNumber()) : Set.of();
      case FORMER_IDS -> new HashSet<>(item.getFormerIds());
      case HOLDINGS_RECORD_ID ->
        Objects.nonNull(item.getHoldingsRecordId()) ? Set.of(item.getHoldingsRecordId()) : Set.of();
      default -> Set.of();
    };
  }

  public static Set<String> getHoldingIdentifierValue(BatchIdsDto.IdentifierTypeEnum identifierType, Holding holding) {
    return switch (identifierType) {
      case ID -> Objects.nonNull(holding.getId()) ? Set.of(holding.getId()) : Set.of();
      case HRID -> Objects.nonNull(holding.getHrid()) ? Set.of(holding.getHrid()) : Set.of();
      default -> throw new UnsupportedOperationException(
        format(HOLDING_IDENTIFIER_TYPE_NOT_SUPPORTED_PATTERN, identifierType.getValue()));
    };
  }
}
