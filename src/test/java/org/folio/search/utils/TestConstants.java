package org.folio.search.utils;

import static org.folio.search.utils.TestUtils.randomId;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String EMPTY_OBJECT = "{}";
  public static final String TENANT_ID = "test_tenant";
  public static final String RESOURCE_NAME = "test-resource";
  public static final String INDEX_NAME = RESOURCE_NAME + "_" + TENANT_ID;
  public static final String INVENTORY_INSTANCE_TOPIC = "inventory.instance";
  public static final String INVENTORY_HOLDING_TOPIC = "inventory.holdings-record";
  public static final String INVENTORY_ITEM_TOPIC = "inventory.item";
  public static final String ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String INVALID_ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String ISSN_IDENTIFIER_TYPE_ID = randomId();
  public static final String INVALID_ISSN_IDENTIFIER_TYPE_ID = randomId();
}
