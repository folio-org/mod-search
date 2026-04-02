package org.folio.indexing;

import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.inventoryAuthorityTopic;
import static org.folio.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;

import java.util.List;
import org.folio.search.domain.dto.Authority;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class IndexingAuthorityIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void shouldRemoveAuthority() {
    var authorityId = randomId();
    var authority = new Authority().id(authorityId).personalName("personal name")
      .corporateName("corporate name").uniformTitle("uniform title");
    var resourceEvent = resourceEvent(authorityId, AUTHORITY, toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), resourceEvent);
    assertCountByIds(authoritySearchPath(), List.of(authorityId), 3);

    var deleteEvent = resourceEvent(authorityId, AUTHORITY, null).type(DELETE).old(toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), deleteEvent);
    assertCountByIds(authoritySearchPath(), List.of(authorityId), 0);
  }
}
