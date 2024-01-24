package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.client.AuthnClient;
import org.folio.spring.client.PermissionsClient;
import org.folio.spring.client.UsersClient;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.system-user.enabled=false")
class DisabledSystemUserIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare(@Autowired ApplicationContext ctx) {
    assertThatThrownBy(() -> ctx.getBean(AuthnClient.class)).isInstanceOf(NoSuchBeanDefinitionException.class);
    assertThatThrownBy(() -> ctx.getBean(UsersClient.class)).isInstanceOf(NoSuchBeanDefinitionException.class);
    assertThatThrownBy(() -> ctx.getBean(PermissionsClient.class)).isInstanceOf(NoSuchBeanDefinitionException.class);

    setUpTenant(Instance.class, getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void searchByInstances_parameterized_singleResult() throws Exception {
    doSearchByInstances(prepareQuery("cql.allRecords = 1", ""))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.instances[0].id", is(getSemanticWebId())));
  }
}
