package org.folio.search.utils;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContributorUtils {

  private static final int NAME_MAX_LENGTH = 255;

  public static String prepareName(Object name) {
    return prepareForExpectedFormat(name, NAME_MAX_LENGTH);
  }

  public static String getContributorId(String preparedName, Object nameTypeId, Object authorityId) {
    return ShaUtils.sha(preparedName, Objects.toString(nameTypeId, EMPTY), Objects.toString(authorityId, EMPTY));
  }
}
