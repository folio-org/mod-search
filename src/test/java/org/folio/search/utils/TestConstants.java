package org.folio.search.utils;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.spring.config.properties.FolioEnvironment.getFolioEnvName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.utils.TestUtils.TestResource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String ENV = "folio";
  public static final String TENANT_ID = "test_tenant";
  public static final String MEMBER_TENANT_ID = "member_tenant";
  public static final String CENTRAL_TENANT_ID = "consortium";
  public static final String RESOURCE_ID = "d148dd82-56b0-4ddb-a638-83ca1ee97e0a";
  public static final String RESOURCE_ID_SECOND = "d148dd82-56b0-4ddb-a638-83ca1ee97e0b";
  public static final String EMPTY_OBJECT = "{}";
  public static final JsonNode EMPTY_JSON_OBJECT = JsonNodeFactory.instance.objectNode();
  public static final String RESOURCE_NAME = getResourceName(TestResource.class);
  public static final String INDEX_NAME = indexName(TENANT_ID);
  public static final List<String> EMPTY_TERM_MODIFIERS = List.of();
  public static final List<String> STRING_TERM_MODIFIERS = List.of("string");

  public static final String AUTHORITY_TOPIC = "authorities.authority";
  public static final String LOCATION_TOPIC = "inventory.location";
  public static final String CONTRIBUTOR_TOPIC = "search.instance-contributor";
  public static final String INVENTORY_ITEM_TOPIC = "inventory.item";
  public static final String INVENTORY_INSTANCE_TOPIC = "inventory.instance";
  public static final String INVENTORY_HOLDING_TOPIC = "inventory.holdings-record";
  public static final String INVENTORY_BOUND_WITH_TOPIC = "inventory.bound-with";
  public static final String INVENTORY_CLASSIFICATION_TYPE_TOPIC = "inventory.classification-type";
  public static final String CONSORTIUM_INSTANCE_TOPIC = "search.consortium.instance";
  public static final String BIBFRAME_TOPIC = "search.bibframe";
  public static final String BIBFRAME_AUTHORITY_TOPIC = "search.bibframe-authorities";
  public static final String CAMPUS_TOPIC = "inventory.campus";
  public static final String INSTITUTION_TOPIC = "inventory.institution";
  public static final String LIBRARY_TOPIC = "inventory.library";

  public static final String LOCAL_CN_TYPE = "6fd29f52-5c9c-44d0-b529-e9c5eb3a0aba";
  public static final String FOLIO_CN_TYPE = "6e4d7565-b277-4dfa-8b7d-fbf306d9d0cd";

  public static final String LCCN_IDENTIFIER_TYPE_ID = randomId();
  public static final String ISSN_IDENTIFIER_TYPE_ID = randomId();
  public static final String ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String OCLC_IDENTIFIER_TYPE_ID = randomId();
  public static final String CANCELED_OCLC_IDENTIFIER_TYPE_ID = randomId();
  public static final String UNIFORM_ALTERNATIVE_TITLE_ID = randomId();
  public static final String INVALID_ISBN_IDENTIFIER_TYPE_ID = randomId();
  public static final String INVALID_ISSN_IDENTIFIER_TYPE_ID = randomId();
  public static final String LINKING_ISSN_IDENTIFIER_TYPE_ID = randomId();

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

  public static String inventoryLocationTopic(String tenantId) {
    return getTopicName(tenantId, LOCATION_TOPIC);
  }

  public static String inventoryClassificationTopic() {
    return inventoryClassificationTopic(TENANT_ID);
  }

  public static String inventoryClassificationTopic(String tenantId) {
    return getTopicName(tenantId, INVENTORY_CLASSIFICATION_TYPE_TOPIC);
  }

  public static String inventoryContributorTopic() {
    return inventoryContributorTopic(TENANT_ID);
  }

  public static String inventoryContributorTopic(String tenantId) {
    return getTopicName(tenantId, CONTRIBUTOR_TOPIC);
  }

  public static String consortiumInstanceTopic() {
    return consortiumInstanceTopic(TENANT_ID);
  }

  public static String consortiumInstanceTopic(String tenantId) {
    return getTopicName(tenantId, CONSORTIUM_INSTANCE_TOPIC);
  }

  public static String inventoryBoundWithTopic() {
    return inventoryBoundWithTopic(TENANT_ID);
  }

  public static String inventoryBoundWithTopic(String tenantId) {
    return getTopicName(tenantId, INVENTORY_BOUND_WITH_TOPIC);
  }

  public static String bibframeTopic(String tenantId) {
    return getTopicName(tenantId, BIBFRAME_TOPIC);
  }

  public static String bibframeAuthorityTopic(String tenantId) {
    return getTopicName(tenantId, BIBFRAME_AUTHORITY_TOPIC);
  }

  public static String inventoryCampusTopic(String tenantId) {
    return getTopicName(tenantId, CAMPUS_TOPIC);
  }

  public static String inventoryInstitutionTopic(String tenantId) {
    return getTopicName(tenantId, INSTITUTION_TOPIC);
  }

  public static String inventoryLibraryTopic(String tenantId) {
    return getTopicName(tenantId, LIBRARY_TOPIC);
  }

  public static String indexName(String tenantId) {
    return String.join("_", ENV, INSTANCE_RESOURCE, tenantId);
  }

  private static String getTopicName(String tenantId, String topic) {
    return String.format("%s.%s.%s", getFolioEnvName(), tenantId, topic);
  }
}
