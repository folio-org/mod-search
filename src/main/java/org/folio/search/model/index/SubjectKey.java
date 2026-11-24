package org.folio.search.model.index;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.utils.ShaUtils;

/**
 * Identity of a subject document, calculated from the value, authority, source and type of an instance subject.
 */
public record SubjectKey(String id, String value, String authorityId, String sourceId, String typeId) {

  private static final int VALUE_MAX_LENGTH = 255;

  /**
   * Creates a key from values of a subject.
   *
   * @return the key, or empty if the subject value is blank
   */
  public static Optional<SubjectKey> of(String value, String authorityId, String sourceId, String typeId) {
    var normalized = prepareForExpectedFormat(value, VALUE_MAX_LENGTH);
    if (StringUtils.isBlank(normalized)) {
      return Optional.empty();
    }
    var id = ShaUtils.sha(normalized, authorityId, sourceId, typeId);
    return Optional.of(new SubjectKey(id, normalized, authorityId, sourceId, typeId));
  }

  /**
   * Creates a key from a subject of an inventory instance record.
   *
   * @return the key, or empty if the subject has no value
   */
  public static Optional<SubjectKey> fromSubject(Map<?, ?> subject) {
    if (subject == null) {
      return Optional.empty();
    }
    return of(field(subject, SUBJECT_VALUE_FIELD), field(subject, AUTHORITY_ID_FIELD),
      field(subject, SUBJECT_SOURCE_ID_FIELD), field(subject, SUBJECT_TYPE_ID_FIELD));
  }

  private static String field(Map<?, ?> subject, String name) {
    return Objects.toString(subject.get(name), null);
  }
}
