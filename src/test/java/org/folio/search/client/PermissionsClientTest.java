package org.folio.search.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;

import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PermissionsClientTest {

  @Test
  void getUserPermissions_supportsApi() throws Exception {
    var responseAsJson = "{\"permissionNames\": [\"perm1\", \"perm2\"], \"totalRecords\": 2}";

    var permissionsClass = OBJECT_MAPPER
      .readValue(responseAsJson, PermissionsClient.Permissions.class);

    assertThat(permissionsClass.getPermissions()).containsExactly("perm1", "perm2");
  }
}
