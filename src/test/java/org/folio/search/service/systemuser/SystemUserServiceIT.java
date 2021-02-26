package org.folio.search.service.systemuser;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.folio.search.repository.SystemUserRepository;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SystemUserServiceIT extends BaseIntegrationTest {
  @Autowired
  private SystemUserRepository userRepository;

  @Test
  void shouldCreateSystemUserDuringTenantInit() throws Exception {
    var tenantId = "shouldCreateSystemUserDuringTenantInit";

    mockMvc.perform(post("/_/tenant")
      .headers(defaultHeaders(tenantId))
      .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0"))))
      .andExpect(status().isOk());

    WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/users?query=username%3D%3Dmod-search")));
    WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/users")));
    WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/authn/credentials")));
    WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/perms/users")));
    WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/authn/login")));

    var systemUserOptional = userRepository.getByTenantId(tenantId);
    assertThat(systemUserOptional.isPresent(), is(true));
    assertThat(systemUserOptional.get().getTenantId(), is(tenantId));
    assertThat(systemUserOptional.get().getOkapiToken(), is("aa.bb.cc"));
    assertThat(systemUserOptional.get().getUsername(), is("mod-search"));
    assertThat(systemUserOptional.get().getPassword(), is("Mod-search-1-0-0"));
  }
}
