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
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class IndexingAuthorityIT extends BaseSharedTest {

  @Test
  void shouldRemoveAuthority() {
    var authorityId = randomId();
    var authority = new Authority().id(authorityId).personalName("personal name")
      .corporateName("corporate name").uniformTitle("uniform title");
    var resourceEvent = resourceEvent(authorityId, AUTHORITY, toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), resourceEvent);
    assertSearchByIdsCount(authoritySearchPath(), List.of(authorityId), 3, TENANT_ID);

    var deleteEvent = resourceEvent(authorityId, AUTHORITY, null).type(DELETE).old(toMap(authority));
    kafkaTemplate.send(inventoryAuthorityTopic(TENANT_ID), deleteEvent);
    assertSearchByIdsCount(authoritySearchPath(), List.of(authorityId), 0, TENANT_ID);
  }
}
