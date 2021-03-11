package org.folio.search.controller;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceTags;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
public class SearchInstanceFilterIT extends BaseIntegrationTest {

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
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[1]), List.of(IDS[0], IDS[1], IDS[3])),
      arguments(format("(id=* and instanceFormatId==%s) sortby title", FORMATS[2]), List.of(IDS[0], IDS[2], IDS[3])),
      arguments(format("(id=* and instanceFormatId==(%s or %s)) sortby title", FORMATS[1], FORMATS[2]),
        List.of(IDS[0], IDS[1], IDS[2], IDS[3])),

      arguments("(id=* and staffSuppress==true) sortby title", List.of(IDS[0], IDS[1], IDS[2])),
      arguments("(id=* and staffSuppress==false) sortby title", List.of(IDS[3], IDS[4])),

      arguments("(id=* and discoverySuppress==true) sortby title", List.of(IDS[0], IDS[1])),
      arguments("(id=* and discoverySuppress==false) sortby title", List.of(IDS[2], IDS[3], IDS[4])),
      arguments("(id=* and staffSuppress==true and discoverySuppress==false) sortby title", List.of(IDS[2])),

      arguments("(id=* and instanceTags==text) sortby title", List.of(IDS[0])),
      arguments("(id=* and instanceTags==science) sortby title", List.of(IDS[0], IDS[2]))
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
      .discoverySuppress(false)
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
      .staffSuppress(false)
      .discoverySuppress(false)
      .instanceTypeId(TYPES[1])
      .instanceFormatId(emptyList())
      .tags(instanceTags("cooking"));

    return instances;
  }

  private static InstanceTags instanceTags(String... tags) {
    return new InstanceTags().tagList(asList(tags));
  }

  private static String[] generateRandomIds(int size) {
    return IntStream.range(0, size).mapToObj(i -> randomId()).toArray(String[]::new);
  }

  private static ThrowingConsumer<ResultActions> assertions(ThrowingConsumer<ResultActions> actions) {
    return actions;
  }
}
