package org.folio.search.controller;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.allInstancesSortedBy;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SortInstanceIT extends BaseIntegrationTest {
  private static final String TENANT = "sort_it_test_tenant";

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc, @Autowired KafkaTemplate<String, Object> kafka) {
    setUpTenant(TENANT, mockMvc, kafka, createFourInstances());
  }

  @AfterAll
  static void removeTenant(@Autowired RestHighLevelClient client, @Autowired JdbcTemplate template) {
    removeTenant(client, template, TENANT);
  }

  @Test
  void canSortInstancesByContributors_asc() throws Exception {
    mockMvc.perform(get(allInstancesSortedBy("contributors", ASCENDING))
      .headers(defaultHeaders(TENANT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("1111 2222")))
      .andExpect(jsonPath("instances[1].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("yyy zzz")));
  }

  @Test
  void canSortInstancesByContributors_desc() throws Exception {
    mockMvc.perform(get(allInstancesSortedBy("contributors", DESCENDING))
      .headers(defaultHeaders(TENANT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("yyy zzz")))
      .andExpect(jsonPath("instances[1].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[2].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("1111 2222")));
  }

  @Test
  void canSortInstancesByTitle_asc() throws Exception {
    mockMvc.perform(get(allInstancesSortedBy("title", ASCENDING))
      .headers(defaultHeaders(TENANT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].title", is("Aaa")))
      .andExpect(jsonPath("instances[1].title", is("Ccc")))
      .andExpect(jsonPath("instances[2].title", is("www")))
      .andExpect(jsonPath("instances[3].title", is("Zzz")));
  }

  @Test
  void canSortInstancesByTitle_desc() throws Exception {
    mockMvc.perform(get(allInstancesSortedBy("title", DESCENDING))
      .headers(defaultHeaders(TENANT)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].title", is("Zzz")))
      .andExpect(jsonPath("instances[1].title", is("www")))
      .andExpect(jsonPath("instances[2].title", is("Ccc")))
      .andExpect(jsonPath("instances[3].title", is("Aaa")));
  }

  private static Instance[] createFourInstances() {
    final var instances = new Instance[]{
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>())};

    instances[0]
      .title("Aaa")
      .addContributorsItem(new InstanceContributors().name("yyy zzz"));

    instances[1]
      .title("Zzz")
      .addContributorsItem(new InstanceContributors().name("aaa bbb").primary(false))
      .addContributorsItem(new InstanceContributors().name("bbb ccc").primary(true));

    instances[2]
      .title("Ccc")
      .addContributorsItem(new InstanceContributors().name("bcc ccc"));

    instances[3]
      .title("www")
      .addContributorsItem(new InstanceContributors().name("1111 2222").primary(true));

    return instances;
  }
}
