package org.folio.search.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import org.folio.search.model.service.ResultList;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PermissionsClientTest {

  @Test
  void getUserPermissions_supportsApi() {
    var client = mock(PermissionsClient.class);
    when(client.getUserPermissions(any()))
      .thenAnswer(invocationOnMock -> {
        var responseAsJson = "{\"permissionNames\": [\"perm1\", \"perm2\"], \"totalRecords\": 2}";
        return OBJECT_MAPPER.readValue(responseAsJson, new TypeReference<ResultList<String>>() { });
      });

    assertThat(client.getUserPermissions("").getResult())
      .containsExactly("perm1", "perm2");
  }
}
