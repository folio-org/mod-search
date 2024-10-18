package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.sample.SampleInstances.getSemanticWebMatchers;
import static org.folio.search.support.base.ApiEndpoints.consortiumInstanceSearchPath;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;

import java.util.List;
import java.util.UUID;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@IntegrationTest
class ConsortiumSearchInstanceIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID, getSemanticWebMatchers(), getSemanticWeb());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void doGetConsortiumInstance_returns200AndInstanceRecord() {;
    var result = doGet(consortiumInstanceSearchPath(getSemanticWebId()), CENTRAL_TENANT_ID);
    var actual = parseResponse(result, Instance.class);

    assertThat(actual.getId()).isEqualTo(getSemanticWebId());
    assertThat(actual.getItems()).isNotEmpty();
    assertThat(actual.getHoldings()).isNotEmpty();
  }

  @Test
  void doGetConsortiumInstance_returns404WhenNoInstanceFound() throws Exception {
    tryGet(consortiumInstanceSearchPath(getSemanticWebId()), CENTRAL_TENANT_ID)
      .andExpect(MockMvcResultMatchers.status().isNotFound());
  }
}
