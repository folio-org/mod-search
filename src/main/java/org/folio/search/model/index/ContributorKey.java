package org.folio.search.model.index;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.utils.ShaUtils;

/**
 * Identity of a contributor document, calculated from the name, name type and authority of an instance contributor.
 */
public record ContributorKey(String id, String name, String nameTypeId, String authorityId) {

  private static final String NAME_FIELD = "name";
  private static final String NAME_TYPE_ID_FIELD = "contributorNameTypeId";
  private static final int NAME_MAX_LENGTH = 255;

  /**
   * Creates a key from values of a contributor.
   *
   * @return the key, or empty if the contributor name is blank
   */
  public static Optional<ContributorKey> of(String name, String nameTypeId, String authorityId) {
    var normalized = prepareForExpectedFormat(name, NAME_MAX_LENGTH);
    if (StringUtils.isBlank(normalized)) {
      return Optional.empty();
    }
    var id = ShaUtils.sha(normalized, nameTypeId, authorityId);
    return Optional.of(new ContributorKey(id, normalized, nameTypeId, authorityId));
  }

  /**
   * Creates a key from a contributor of an inventory instance record.
   *
   * @return the key, or empty if the contributor has no name
   */
  public static Optional<ContributorKey> fromContributor(Map<?, ?> contributor) {
    if (contributor == null) {
      return Optional.empty();
    }
    return of(field(contributor, NAME_FIELD), field(contributor, NAME_TYPE_ID_FIELD),
      field(contributor, AUTHORITY_ID_FIELD));
  }

  private static String field(Map<?, ?> contributor, String name) {
    return Objects.toString(contributor.get(name), null);
  }
}
