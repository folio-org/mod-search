package org.folio.search.service.indexrebuild.callnumber;

import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CALL_NUMBER_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.PREFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.SUFFIX_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.utils.ShaUtils;

/**
 * Identity of a call number document, calculated from the effective call number components of an item.
 */
public record CallNumberKey(String id, String callNumber, String prefix, String suffix, String typeId) {

  private static final int CALL_NUMBER_MAX_LENGTH = 50;
  private static final int PREFIX_MAX_LENGTH = 20;
  private static final int SUFFIX_MAX_LENGTH = 25;
  private static final int TYPE_MAX_LENGTH = 40;

  /**
   * Creates a key from values of a call number.
   *
   * @return the key, or empty if the call number is blank
   */
  public static Optional<CallNumberKey> of(String callNumber, String prefix, String suffix, String typeId) {
    var normalized = prepareForExpectedFormat(callNumber, CALL_NUMBER_MAX_LENGTH);
    if (StringUtils.isBlank(normalized)) {
      return Optional.empty();
    }
    var normalizedPrefix = truncate(prefix, PREFIX_MAX_LENGTH);
    var normalizedSuffix = truncate(suffix, SUFFIX_MAX_LENGTH);
    var normalizedType = truncate(typeId, TYPE_MAX_LENGTH);
    var id = ShaUtils.sha(normalized, normalizedPrefix, normalizedSuffix, normalizedType);
    return Optional.of(new CallNumberKey(id, normalized, normalizedPrefix, normalizedSuffix, normalizedType));
  }

  /**
   * Creates a key from an item record of inventory.
   *
   * @return the key, or empty if the item has no call number
   */
  public static Optional<CallNumberKey> fromItem(Map<?, ?> item) {
    if (item == null || !(item.get(EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD) instanceof Map<?, ?> components)) {
      return Optional.empty();
    }
    return of(component(components, CALL_NUMBER_FIELD), component(components, PREFIX_FIELD),
      component(components, SUFFIX_FIELD), component(components, TYPE_ID_FIELD));
  }

  private static String component(Map<?, ?> components, String name) {
    return Objects.toString(components.get(name), null);
  }
}
