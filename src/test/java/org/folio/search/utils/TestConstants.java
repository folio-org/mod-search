package org.folio.search.utils;

import static org.folio.search.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestUtils.randomId;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.utils.TestUtils.TestResource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String ENV = "folio";
  public static final String TENANT_ID = "test_tenant";
  public static final String MODULE_NAME = "mod-search";
  public static final String RESOURCE_ID = "d148dd82-56b0-4ddb-a638-83ca1ee97e0a";
  public static final String EMPTY_OBJECT = "{}";
  public static final String RESOURCE_NAME = getResourceName(TestResource.class);
  public static final String INDEX_NAME = String.join("_", ENV, RESOURCE_NAME, TENANT_ID);

  public static final String AUTHORITY_TOPIC = "inventory.authority";
  public static final String INVENTORY_ITEM_TOPIC = "inventory.item";
  public static final String INVENTORY_INSTANCE_TOPIC = "inventory.instance";
  public static final String INVENTORY_HOLDING_TOPIC = "inventory.holdings-record";

  public static final String ISSN_IDENTIFIER_TYPE_ID = randomId();
  public static final String ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String UNIFORM_ALTERNATIVE_TITLE_ID = randomId();
  public static final String INVALID_ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String INVALID_ISSN_IDENTIFIER_TYPE_ID = randomId();

  public static String inventoryInstanceTopic() {
    return inventoryInstanceTopic(TENANT_ID);
  }

  public static String inventoryInstanceTopic(String tenantId) {
    return getTopicName(tenantId, INVENTORY_INSTANCE_TOPIC);
  }

  public static String inventoryItemTopic() {
    return inventoryItemTopic(TENANT_ID);
  }

  public static String inventoryItemTopic(String tenantId) {
    return getTopicName(tenantId, INVENTORY_ITEM_TOPIC);
  }

  public static String inventoryHoldingTopic() {
    return inventoryHoldingTopic(TENANT_ID);
  }

  public static String inventoryHoldingTopic(String tenantId) {
    return getTopicName(tenantId, INVENTORY_HOLDING_TOPIC);
  }

  public static String inventoryAuthorityTopic() {
    return inventoryAuthorityTopic(TENANT_ID);
  }

  public static String inventoryAuthorityTopic(String tenantId) {
    return getTopicName(tenantId, AUTHORITY_TOPIC);
  }

  private static String getTopicName(String tenantId, String topic) {
    return String.format("%s.%s.%s", getFolioEnvName(), tenantId, topic);
  }
}
