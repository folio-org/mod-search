package org.folio.search.configuration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchCacheNames {

  public static final String ES_INDICES_CACHE = "es-indices";
  public static final String REFERENCE_DATA_CACHE = "reference-data-cache";
  public static final String RESOURCE_LANGUAGE_CACHE = "tenant-languages";
  public static final String TENANT_FEATURES_CACHE = "tenant-features";
  public static final String SEARCH_PREFERENCE_CACHE = "search-preference";
  public static final String USER_TENANTS_CACHE = "user-tenants";
  public static final String CONSORTIUM_TENANTS_CACHE = "consortium-tenants-cache";
}
