package org.folio.search.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchCacheNames {

  public static final String ES_INDICES_CACHE = "es-indices";
  public static final String IDENTIFIER_IDS_CACHE = "identifier-types";
  public static final String ALTERNATIVE_TITLE_TYPES_CACHE = "alternative-title-types";
  public static final String SYSTEM_USER_CACHE = "system-user-cache";
  public static final String RESOURCE_LANGUAGE_CACHE = "tenant-languages";
}
