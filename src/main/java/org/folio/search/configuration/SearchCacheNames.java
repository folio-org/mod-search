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
  public static final String ACTIVE_INDEX_FAMILY_CACHE = "active-index-family";
  public static final String CUTTING_OVER_INDEX_FAMILY_CACHE = "cutting-over-index-family";
  public static final String PHYSICAL_INDEX_EXISTS_CACHE = "physical-index-exists";
}
