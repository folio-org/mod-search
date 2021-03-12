package org.folio.search.controller;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.getFacets;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.facet;
import static org.folio.search.utils.TestUtils.facetItem;
import static org.folio.search.utils.TestUtils.facetResult;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceTags;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SearchInstanceFilterIT extends BaseIntegrationTest {

  private static final String TENANT_ID = "filter_test_instance";

  private static final String[] IDS = generateRandomIds(5);
  private static final String[] FORMATS = generateRandomIds(3);
  private static final String[] TYPES = generateRandomIds(2);

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, instances());
  }

  @AfterAll
  static void removeTenant(@Autowired RestHighLevelClient client, @Autowired JdbcTemplate template) {
    removeTenant(client, template, TENANT_ID);
  }

  @MethodSource("filteredSearchQueriesProvider")
  @DisplayName("searchByInstances_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByInstances_parameterized(String query, List<String> expectedIds) throws Exception {
    mockMvc.perform(get(searchInstancesByQuery(query)).headers(defaultHeaders(TENANT_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
      .andExpect(jsonPath("instances[*].id", is(expectedIds)));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForInstances_parameterized")
  void getFacetsForInstances_parameterized(String query, String[] facets, Map<String, Facet> expected)
    throws Throwable {
    var jsonString = mockMvc.perform(get(getFacets(query, facets)).headers(defaultHeaders(TENANT_ID)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var actual = OBJECT_MAPPER.readValue(jsonString, FacetResult.class);
    assertThat(actual).isEqualTo(facetResult(expected));
  }

  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
      arguments("(id=*) sortby title", List.of(IDS)),
      arguments("(id=* and source==\"MARC\") sortby title", List.of(IDS[0], IDS[1], IDS[3])),
      arguments("(id=* and source==\"FOLIO\") sortby title", List.of(IDS[2], IDS[4])),

      arguments("(id=* and languages==\"eng\") sortby title", List.of(IDS[0], IDS[1], IDS[4])),
      arguments("(id=* and languages==\"ger\") sortby title", List.of(IDS[1])),
      arguments("(id=* and languages==\"ita\") sortby title", List.of(IDS[0], IDS[3])),
      arguments("(id=* and languages==\"fra\") sortby title", List.of(IDS[1], IDS[4])),
      arguments("(id=* and languages==\"rus\") sortby title", List.of(IDS[2])),
      arguments("(id=* and languages==\"ukr\") sortby title", List.of(IDS[2])),

      arguments("(id=* and source==\"MARC\" and languages==\"eng\") sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and source==\"FOLIO\" and languages==\"eng\") sortby title", List.of(IDS[4])),

      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[0]), List.of(IDS[1], IDS[2])),
      arguments(format("(id=* and instanceTypeId==%s) sortby title", TYPES[1]), List.of(IDS[0], IDS[3], IDS[4])),

      arguments(format("(id=* and instanceFormatId==\"%s\") sortby title", FORMATS[0]), List.of(IDS[1], IDS[3])),
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[1]),
        List.of(IDS[0], IDS[1], IDS[3], IDS[4])),
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[2]), List.of(IDS[0], IDS[2], IDS[3])),
      arguments(format("(id=* and instanceFormatId==(%s or %s)) sortby title", FORMATS[1], FORMATS[2]), List.of(IDS)),

      arguments("(id=* and staffSuppress==true) sortby title", List.of(IDS[0], IDS[1], IDS[2])),
      arguments("(id=* and staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),

      arguments("(id=* and discoverySuppress==true) sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(id=* and staffSuppress==true and discoverySuppress==false) sortby title", List.of(IDS[2])),

      arguments("(id=* and instanceTags==text) sortby title", List.of(IDS[0])),
      arguments("(id=* and instanceTags==science) sortby title", List.of(IDS[0], IDS[2]))
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    var allFacets = array("discoverySuppress", "staffSuppress", "languages", "instanceTags", "source",
      "instanceTypeId", "instanceFormatId");
    return Stream.of(
      arguments("id=*", allFacets, mapOf(
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)),
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)),
        "languages", facet(facetItem("eng", 3), facetItem("fra", 2), facetItem("ita", 2),
          facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)),
        "instanceTags", facet(facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
          facetItem("casual", 1), facetItem("text", 1)),
        "source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)),
        "instanceTypeId", facet(facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)),
        "instanceFormatId", facet(facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 2)))),

      arguments("id=*", array("source"), mapOf("source", facet(facetItem("MARC", 3), facetItem("FOLIO", 2)))),

      arguments("id=*", array("languages"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2),
        facetItem("ita", 2), facetItem("ger", 1), facetItem("rus", 1), facetItem("ukr", 1)))),

      arguments("id=*", array("languages:2"), mapOf("languages", facet(facetItem("eng", 3), facetItem("fra", 2)))),

      arguments("id=*", array("discoverySuppress"), mapOf(
        "discoverySuppress", facet(facetItem("false", 3), facetItem("true", 2)))),

      arguments("id=*", array("staffSuppress"), mapOf(
        "staffSuppress", facet(facetItem("true", 3), facetItem("false", 2)))),

      arguments("id=*", array("instanceTags"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2),
        facetItem("casual", 1), facetItem("text", 1)))),

      arguments("id=*", array("instanceTags:3"), mapOf("instanceTags", facet(
        facetItem("cooking", 2), facetItem("future", 2), facetItem("science", 2)))),

      arguments("id=*", array("instanceTypeId"), mapOf("instanceTypeId", facet(
        facetItem(TYPES[1], 3), facetItem(TYPES[0], 2)))),

      arguments("id=*", array("instanceFormatId"), mapOf("instanceFormatId", facet(
        facetItem(FORMATS[1], 4), facetItem(FORMATS[2], 3), facetItem(FORMATS[0], 2))))
    );
  }

  private static Instance[] instances() {
    var instances = IntStream.range(0, 5)
      .mapToObj(i -> new Instance().id(IDS[i]).title("Resource" + i))
      .toArray(Instance[]::new);

    instances[0]
      .source("MARC")
      .languages(List.of("eng", "ita"))
      .instanceTypeId(TYPES[1])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatId(List.of(FORMATS[1], FORMATS[2]))
      .tags(instanceTags("text", "science"));

    instances[1]
      .source("MARC")
      .languages(List.of("eng", "ger", "fra"))
      .instanceTypeId(TYPES[0])
      .staffSuppress(true)
      .discoverySuppress(true)
      .instanceFormatId(List.of(FORMATS[0], FORMATS[1]))
      .tags(instanceTags("future"));

    instances[2]
      .source("FOLIO")
      .languages(List.of("rus", "ukr"))
      .instanceTypeId(TYPES[0])
      .staffSuppress(true)
      .instanceFormatId(List.of(FORMATS[2]))
      .tags(instanceTags("future", "science"));

    instances[3]
      .source("MARC")
      .languages(List.of("ita"))
      .staffSuppress(false)
      .discoverySuppress(false)
      .instanceTypeId(TYPES[1])
      .instanceFormatId(List.of(FORMATS))
      .tags(instanceTags("casual", "cooking"));

    instances[4]
      .source("FOLIO")
      .languages(List.of("eng", "fra"))
      .instanceTypeId(TYPES[1])
      .instanceFormatId(List.of(FORMATS[1]))
      .tags(instanceTags("cooking"));

    return instances;
  }

  private static InstanceTags instanceTags(String... tags) {
    return new InstanceTags().tagList(asList(tags));
  }

  private static String[] generateRandomIds(int size) {
    return IntStream.range(0, size).mapToObj(i -> randomId()).toArray(String[]::new);
  }
}
