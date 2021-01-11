package org.folio.search.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String UNIT_TEST = "unit";
  public static final String EMPTY_OBJECT = "{}";
  public static final String TENANT_ID = "test-tenant";
  public static final String RESOURCE_NAME = "test-resource";
  public static final String INDEX_NAME = RESOURCE_NAME + "_" + TENANT_ID;
}
