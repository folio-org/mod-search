package org.folio.search.controller;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.allRecordsSortedBy;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SortInstanceIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(instances());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void canSortInstancesByContributors_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("contributors", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("1111 2222")))
      .andExpect(jsonPath("instances[1].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("yyy zzz")));
  }

  @Test
  void canSortInstancesByContributors_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("contributors", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("yyy zzz")))
      .andExpect(jsonPath("instances[1].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[2].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("1111 2222")));
  }

  @Test
  void canSortInstancesByTitle_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("title", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].title", is("Calling Me Home")))
      .andExpect(jsonPath("instances[1].title", is("Animal farm")))
      .andExpect(jsonPath("instances[2].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[3].title", is("Zero Minus Ten")));
  }

  @Test
  void canSortInstancesByTitle_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("title", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(4)))
      .andExpect(jsonPath("instances[0].title", is("Zero Minus Ten")))
      .andExpect(jsonPath("instances[1].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[2].title", is("Animal farm")))
      .andExpect(jsonPath("instances[3].title", is("Calling Me Home")));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchByInstances(allRecordsSortedBy("unknownSort", DESCENDING))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }

  private static Instance[] instances() {
    var instances = new Instance[] {
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>()),
      getSemanticWeb().id(randomId()).contributors(new ArrayList<>())};

    instances[0]
      .title("Animal farm")
      .indexTitle("B1 Animal farm")
      .addContributorsItem(new Contributor().name("yyy zzz"));

    instances[1]
      .title("Zero Minus Ten")
      .indexTitle(null)
      .addContributorsItem(new Contributor().name("aaa bbb").primary(false))
      .addContributorsItem(new Contributor().name("bbb ccc").primary(true));

    instances[2]
      .title("Calling Me Home")
      .indexTitle("A1 Calling Me Home")
      .addContributorsItem(new Contributor().name("bcc ccc"));

    instances[3]
      .title("Walk in My Soul")
      .indexTitle(null)
      .addContributorsItem(new Contributor().name("1111 2222").primary(true));

    return instances;
  }
}
