package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.subjectBrowseItem;
import static org.folio.search.utils.TestUtils.subjectBrowseResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Subject;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.search.model.Pair;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class BrowseSubjectIT extends BaseIntegrationTest {

  private static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final Instance[] INSTANCES = instances();

  @BeforeAll
  static void prepare(@Autowired RestHighLevelClient restHighLevelClient) {
    setUpTenant(INSTANCES);
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var searchRequest = new SearchRequest()
        .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
        .indices(getIndexName(SearchUtils.INSTANCE_SUBJECT_RESOURCE, TENANT_ID));
      var searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
      assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(23);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  private static Stream<Arguments> subjectBrowsingDataProvider() {
    var aroundQuery = "value > {value} or value < {value}";
    var aroundIncludingQuery = "value >= {value} or value < {value}";
    var forwardQuery = "value > {value}";
    var forwardIncludingQuery = "value >= {value}";
    var backwardQuery = "value < {value}";
    var backwardIncludingQuery = "value <= {value}";

    return Stream.of(
      arguments(aroundQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Textbooks").next("Water--Microbiology")
        .items(List.of(
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States"),
          subjectBrowseItem(0, "water", true),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology")))),

      arguments(aroundQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(23).prev(null).next("Database design")
        .items(List.of(
          subjectBrowseItem(2, "Biography"),
          subjectBrowseItem(0, "biology", true),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design")))),

      arguments(aroundIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Textbooks").next("Water--Microbiology")
        .items(List.of(
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States"),
          subjectBrowseItem(1, "Water", true),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology")))),

      arguments(aroundIncludingQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(23).prev(null).next("Database design")
        .items(List.of(
          subjectBrowseItem(2, "Biography"),
          subjectBrowseItem(0, "biology", true),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design")))),

      arguments(aroundIncludingQuery, "music", 25, new SubjectBrowseResult()
        .totalRecords(23).prev(null).next("Water--Analysis")
        .items(List.of(
          subjectBrowseItem(2, "Biography"),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, true),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_1, true),
          subjectBrowseItem(1, "Philosophy"),
          subjectBrowseItem(1, "Religion"),
          subjectBrowseItem(1, "Rules"),
          subjectBrowseItem(1, "Science"),
          subjectBrowseItem(1, "Science--Methodology"),
          subjectBrowseItem(1, "Science--Philosophy"),
          subjectBrowseItem(1, "Text"),
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States"),
          subjectBrowseItem(1, "Water"),
          subjectBrowseItem(1, "Water--Analysis")))),

      arguments(aroundIncludingQuery, "music", 11, new SubjectBrowseResult()
        .totalRecords(23).prev("Database design").next("Science")
        .items(List.of(
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, true),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_1, true),
          subjectBrowseItem(1, "Philosophy"),
          subjectBrowseItem(1, "Religion"),
          subjectBrowseItem(1, "Rules"),
          subjectBrowseItem(1, "Science")))),

      arguments(aroundIncludingQuery, "FC", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Europe").next("Music")
        .items(List.of(
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(0, "FC", true),
          subjectBrowseItem(3, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2)))),

      arguments(aroundIncludingQuery, "a", 5, new SubjectBrowseResult()
        .totalRecords(23).prev(null).next("Book")
        .items(List.of(
          subjectBrowseItem(0, "a", true),
          subjectBrowseItem(2, "Biography"),
          subjectBrowseItem(1, "Book")))),

      arguments(aroundIncludingQuery, "z", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Water--Purification").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply"),
          subjectBrowseItem(0, "z", true)))),

      // browsing forward
      arguments(forwardQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Water--Analysis").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology"),
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply")))),

      arguments(forwardQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Fantasy").next("Philosophy")
        .items(List.of(
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_1),
          subjectBrowseItem(1, "Philosophy")))),

      arguments(forwardQuery, "Z", 10, subjectBrowseResult(23, emptyList())),

      arguments(forwardIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Water").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water"),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology"),
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply")))),

      arguments(forwardIncludingQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      // browsing backward
      arguments(backwardQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Science--Methodology").next("United States")
        .items(List.of(
          subjectBrowseItem(1, "Science--Methodology"),
          subjectBrowseItem(1, "Science--Philosophy"),
          subjectBrowseItem(1, "Text"),
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States")))),

      arguments(backwardQuery, "fun", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      arguments(backwardQuery, "G", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      arguments(backwardQuery, "A", 10, subjectBrowseResult(23, emptyList())),

      arguments(backwardIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Science--Philosophy").next("Water")
        .items(List.of(
          subjectBrowseItem(1, "Science--Philosophy"),
          subjectBrowseItem(1, "Text"),
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States"),
          subjectBrowseItem(1, "Water")))),

      arguments(backwardIncludingQuery, "fun", 5, new SubjectBrowseResult()
        .totalRecords(23).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"))))
    );
  }

  private static Instance[] instances() {
    return subjectBrowseInstanceData().stream()
      .map(BrowseSubjectIT::instance)
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

  @MethodSource("subjectBrowsingDataProvider")
  @DisplayName("browseBySubject_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseBySubject_parameterized(String query, String anchor, Integer limit, SubjectBrowseResult expected) {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);

    assertThat(actual).usingRecursiveComparison().ignoringFields("items.authorityId")
      .isEqualTo(expected);
  }

  @Test
  void browseBySubject_browsingAroundWithPrecedingRecordsCount() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery("value < {value} or value >= {value}", "\"water\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(23).prev("Textbooks")
      .items(List.of(
        subjectBrowseItem(1, "Textbooks"),
        subjectBrowseItem(1, "United States"),
        subjectBrowseItem(1, "Water", true),
        subjectBrowseItem(1, "Water--Analysis"),
        subjectBrowseItem(1, "Water--Microbiology"),
        subjectBrowseItem(1, "Water--Purification"),
        subjectBrowseItem(1, "Water-supply"))));
  }

  @Test
  void browseBySubject_browsingAroundWithoutHighlightMatch() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery("value < {value} or value >= {value}", "\"fantasy\""))
      .param("limit", "4")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);

    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(23).prev("Database management").next("History")
      .items(List.of(
        subjectBrowseItem(1, "Database management"),
        subjectBrowseItem(1, "Europe"),
        subjectBrowseItem(1, "Fantasy"),
        subjectBrowseItem(3, "History"))));
  }
}
