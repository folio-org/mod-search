package org.folio.search.controller;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SearchByEmptyValuesIT extends BaseIntegrationTest {

  private static final String INSTANCE_ID_1 = randomId();
  private static final String INSTANCE_ID_2 = randomId();

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, instances());
  }

  @AfterAll
  static void removeTenant(@Autowired MockMvc mockMvc) {
    removeTenant(mockMvc, TENANT_ID);
  }

  @CsvSource({
    "cql.allRecords=1, title1;title2",
    "instanceTypeId=\"\", title1;title2",
    "isbn=\"\",",
    "cql.allRecords=1 NOT isbn=\"\", title1;title2",
    "cql.allRecords=1 NOT instanceTypeId=\"\",",
    "contributors.name==[], ",
    "indexTitle==\"\", title2",
    "indexTitle=\"\" NOT indexTitle==\"\", title1",
    "cql.allRecords=1 NOT indexTitle=\"\",",
    "cql.allRecords=1 NOT indexTitle==\"\", title1",
    "subjects==[], title1;title2",
  })
  @ParameterizedTest
  void search_parameterized(String query, String titles) throws Exception {
    var expectedTitles = StringUtils.isNotEmpty(titles) ? asList(titles.split(";")) : emptyList();
    var searchResult = mockMvc.perform(get(searchInstancesByQuery(query + " sortBy title")).headers(defaultHeaders()));
    searchResult
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedTitles.size())))
      .andExpect(jsonPath("instances[*].title", is(expectedTitles)));
  }

  private static Instance[] instances() {
    return new Instance[] {
      new Instance()
        .id(INSTANCE_ID_1)
        .title("title1")
        .indexTitle("indexTitle")
        .languages(List.of("eng"))
        .instanceTypeId(randomId())
        .subjects(emptyList())
        .contributors(List.of(new InstanceContributors().name("c1"))),

      new Instance()
        .id(INSTANCE_ID_2)
        .title("title2")
        .indexTitle("")
        .instanceTypeId(randomId())
        .contributors(emptyList())};
  }
}
