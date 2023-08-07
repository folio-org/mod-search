package org.folio.search.controller;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.CONSORTIUM_TENANT_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.subjectBrowseItem;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class BrowseSubjectConsortiumIT extends BaseIntegrationTest {

  private static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final Instance[] INSTANCES_MEMBER = instancesMember();
  private static final Instance[] INSTANCES_CENTRAL = instancesCentral();

  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) {
    setUpTenant(CONSORTIUM_TENANT_ID, INSTANCES_CENTRAL.length, INSTANCES_CENTRAL);
    setUpTenant(TENANT_ID, INSTANCES_CENTRAL.length + INSTANCES_MEMBER.length, INSTANCES_MEMBER);

    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
        .indices(getIndexName(SearchUtils.INSTANCE_SUBJECT_RESOURCE, centralTenant));
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(23);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  private static Instance[] instancesCentral() {
    return subjectBrowseInstanceData().subList(0, 5).stream()
      .map(BrowseSubjectConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  private static Instance[] instancesMember() {
    return subjectBrowseInstanceData().subList(5, 10).stream()
      .map(BrowseSubjectConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  @SuppressWarnings("unchecked")
  private static Instance instance(List<Object> data) {
    return new Instance()
      .id(randomId())
      .title((String) data.get(0))
      .subjects(((List<Object>) data.get(1)).stream()
        .map(val -> {
          if (val instanceof Pair<?, ?> pair) {
            return new Subject().value(String.valueOf(pair.getFirst())).authorityId(String.valueOf(pair.getSecond()));
          } else {
            return new Subject().value(String.valueOf(val));
          }
        }).collect(Collectors.toList()))
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());
  }

  private static List<List<Object>> subjectBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of("History", pair("Music", MUSIC_AUTHORITY_ID_1), "Biography")),
      List.of("instance #02", List.of("Fantasy", pair("Music", MUSIC_AUTHORITY_ID_2))),
      List.of("instance #03", List.of("United States", "History", "Rules")),
      List.of("instance #04", List.of("Europe", pair("Music", MUSIC_AUTHORITY_ID_1))),
      List.of("instance #05", List.of("Book", "Text", "Biography")),
      List.of("instance #06", List.of("Religion", "History", "Philosophy")),
      List.of("instance #07", List.of("Science", "Science--Methodology", "Science--Philosophy")),
      List.of("instance #08", List.of("Water", "Water-supply", pair("Music", MUSIC_AUTHORITY_ID_2))),
      List.of("instance #09", List.of("Water--Analysis", "Water--Purification", "Water--Microbiology")),
      List.of("instance #10", List.of("Database design", "Database management", "Textbooks"))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"tenantId==consortium", "shared==true"})
  void browseBySubject_browsingAroundWithConsortiumInstanceFilter(String instanceFilterQuery) {
    var request = get(instanceSubjectBrowsePath())
      .param("query", "("
        + prepareQuery("value < {value} or value >= {value}", "\"Rules\"") + ") "
        + "and instances." + instanceFilterQuery)
      .param("limit", "5")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(10).prev("Music")
      .items(List.of(
        subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2),
        subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_1),
        subjectBrowseItem(1, "Rules", true),
        subjectBrowseItem(1, "Text"),
        subjectBrowseItem(1, "United States"))));
  }

  //todo: move 4 methods below to consortium integration test base in a scope of MSEARCH-562
  @SneakyThrows
  protected static void setUpTenant(String tenantName, int expectedCount, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> { }, asList(instances), expectedCount,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  private static <T> void setUpTenant(String tenant, String validationPath, Runnable postInitAction,
                                      List<T> records, Integer expectedCount, Consumer<T> consumer) {
    enableTenant(tenant);
    postInitAction.run();
    saveRecords(tenant, validationPath, records, expectedCount, consumer);
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");
    tenantAttributes.addParametersItem(new Parameter("centralTenantId").value(CONSORTIUM_TENANT_ID));

    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(tenantAttributes))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  private static <T> void saveRecords(String tenant, String validationPath, List<T> records, Integer expectedCount,
                                      Consumer<T> consumer) {
    records.forEach(consumer);
    if (records.size() > 0) {
      checkThatEventsFromKafkaAreIndexed(tenant, validationPath, expectedCount);
    }
  }
}
