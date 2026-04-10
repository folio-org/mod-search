package org.folio.search.utils;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.entity.CallNumberEntity;

@UtilityClass
public class V2BrowseIdComputer {

  private static final int CONTRIBUTOR_NAME_MAX_LENGTH = 255;
  private static final int SUBJECT_VALUE_MAX_LENGTH = 255;
  private static final int CLASSIFICATION_NUMBER_MAX_LENGTH = 50;

  public static String computeContributorBrowseId(Map<String, Object> contributor) {
    var name = prepareForExpectedFormat(contributor.get("name"), CONTRIBUTOR_NAME_MAX_LENGTH);
    if (name.isBlank()) {
      return null;
    }
    var nameTypeId = Objects.toString(contributor.get("contributorNameTypeId"), EMPTY);
    var authorityId = Objects.toString(contributor.get(AUTHORITY_ID_FIELD), EMPTY);
    return ShaUtils.sha(name, nameTypeId, authorityId);
  }

  public static String computeSubjectBrowseId(Map<String, Object> subject) {
    var rawValue = subject.get(SUBJECT_VALUE_FIELD);
    // After multilang enrichment, value becomes a Map (e.g. {eng: ..., src: ...}).
    // Fall back to plain_value to ensure consistent browseId computation.
    if (rawValue instanceof Map) {
      rawValue = subject.get("plain_value");
    }
    var value = prepareForExpectedFormat(rawValue, SUBJECT_VALUE_MAX_LENGTH);
    if (value.isEmpty()) {
      return null;
    }
    var authorityId = Objects.toString(subject.get(AUTHORITY_ID_FIELD), EMPTY);
    var sourceId = Objects.toString(subject.get(SUBJECT_SOURCE_ID_FIELD), EMPTY);
    var typeId = Objects.toString(subject.get(SUBJECT_TYPE_ID_FIELD), EMPTY);
    return ShaUtils.sha(value, authorityId, sourceId, typeId);
  }

  public static String computeClassificationBrowseId(Map<String, Object> classification) {
    var number = prepareForExpectedFormat(classification.get(CLASSIFICATION_NUMBER_FIELD),
      CLASSIFICATION_NUMBER_MAX_LENGTH);
    if (number.isEmpty()) {
      return null;
    }
    var typeId = Objects.toString(classification.get(CLASSIFICATION_TYPE_FIELD), EMPTY);
    return ShaUtils.sha(number, typeId);
  }

  @SuppressWarnings("unchecked")
  public static String computeCallNumberBrowseId(Map<String, Object> itemRecord) {
    var components = (Map<String, Object>) MapUtils.getMap(itemRecord, "effectiveCallNumberComponents");
    if (components == null || components.isEmpty()) {
      return null;
    }
    var callNumber = MapUtils.getString(components, "callNumber");
    if (callNumber == null) {
      return null;
    }
    var entity = CallNumberEntity.builder()
      .callNumber(callNumber)
      .callNumberPrefix(MapUtils.getString(components, "prefix"))
      .callNumberSuffix(MapUtils.getString(components, "suffix"))
      .callNumberTypeId(MapUtils.getString(components, "typeId"))
      .build();
    return entity.getId();
  }
}
