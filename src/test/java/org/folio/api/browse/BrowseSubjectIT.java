package org.folio.api.browse;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.subjectBrowseItem;
import static org.folio.support.utils.TestUtils.subjectBrowseResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.Subject;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class BrowseSubjectIT extends BaseIntegrationTest {

  private static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final String MUSIC_SOURCE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91b";
  private static final String MUSIC_SOURCE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bd";
  private static final String MUSIC_TYPE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91c";
  private static final String MUSIC_TYPE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21be";
  private static final Instance[] INSTANCES = instances();

  @BeforeAll
  static void prepare(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    setUpTenant();

    enableFeature(BROWSE_SUBJECTS);

    var timestamp = subResourcesLockRepository.lockSubResource(ReindexEntityType.SUBJECT, TENANT_ID);
    if (timestamp.isEmpty()) {
      throw new IllegalStateException("Unexpected state of database: unable to lock subject resource");
    }

    saveRecords(TENANT_ID, instanceSearchPath(), asList(INSTANCES), INSTANCES.length, emptyList(),
      instance -> inventoryApi.createInstance(TENANT_ID, instance));

    subResourcesLockRepository.unlockSubResource(ReindexEntityType.SUBJECT, timestamp.get(), TENANT_ID);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var counted = countIndexDocument(INSTANCE_SUBJECT, TENANT_ID);
      assertThat(counted).isEqualTo(28);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("subjectBrowsingDataProvider")
  @DisplayName("browseBySubject_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseBySubject_parameterized(String query, String anchor, Integer limit, SubjectBrowseResult expected) {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseBySubject_browsingAroundWithPrecedingRecordsCount() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery("value < {value} or value >= {value}", "\"water\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(28).prev("Textbooks")
      .items(List.of(
        subjectBrowseItem(1, "Textbooks"),
        subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
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
      .totalRecords(28).prev("Database management").next("History")
      .items(List.of(
        subjectBrowseItem(1, "Database management"),
        subjectBrowseItem(1, "Europe"),
        subjectBrowseItem(1, "Fantasy"),
        subjectBrowseItem(8, "History"))));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForSubjects_parameterized")
  void getFacetsForSubjects_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.SUBJECTS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @Test
  void browseBySubject_withSourceFilter() {
    var request = get(instanceSubjectBrowsePath()).param("query",
      "(" + prepareQuery("value >= {value} or value < {value}", '"' + "Philosophy" + '"') + ") "
        + "and sourceId==" + MUSIC_SOURCE_ID_1).param("limit", "5");

    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    var expected = new SubjectBrowseResult().totalRecords(4).prev("Music").next(null).items(
      List.of(
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1),
        subjectBrowseItem(1, "Music", null, MUSIC_SOURCE_ID_1, null),
        subjectBrowseItem(0, "Philosophy", true),
        subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2)));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseBySubject_withTypeFilter() {
    var request = get(instanceSubjectBrowsePath()).param("query",
      "(" + prepareQuery("value >= {value} or value < {value}", '"' + "Philosophy" + '"') + ") "
        + "and typeId==" + MUSIC_TYPE_ID_2).param("limit", "5");

    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    var expected = new SubjectBrowseResult().totalRecords(3).prev(null).next(null).items(
      List.of(
        subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2),
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, null, MUSIC_TYPE_ID_2),
        subjectBrowseItem(0, "Philosophy", true),
        subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2)));

    assertThat(actual).isEqualTo(expected);
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
        .totalRecords(28).prev("Textbooks").next("Water--Microbiology")
        .items(List.of(
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
          subjectBrowseItem(0, "water", true),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology")))),

      arguments(aroundQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(28).prev(null).next("Database design")
        .items(List.of(
          subjectBrowseItem(7, "Biography"),
          subjectBrowseItem(0, "biology", true),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design")))),

      arguments(aroundIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Textbooks").next("Water--Microbiology")
        .items(List.of(
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
          subjectBrowseItem(1, "Water", true),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology")))),

      arguments(aroundIncludingQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(28).prev(null).next("Database design")
        .items(List.of(
          subjectBrowseItem(7, "Biography"),
          subjectBrowseItem(0, "biology", true),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design")))),

      arguments(aroundIncludingQuery, "music", 25, new SubjectBrowseResult()
        .totalRecords(28).prev(null).next("Science--Philosophy")
        .items(List.of(
          subjectBrowseItem(7, "Biography"),
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(8, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_1, null, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, null, MUSIC_TYPE_ID_2, true),
          subjectBrowseItem(1, "Music", null, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_1, true),
          subjectBrowseItem(1, "Music", null, MUSIC_SOURCE_ID_1, null, true),
          subjectBrowseItem(2, "Music", null, null, MUSIC_TYPE_ID_1, true),
          subjectBrowseItem(1, "Philosophy"),
          subjectBrowseItem(1, "Religion"),
          subjectBrowseItem(1, "Rules"),
          subjectBrowseItem(1, "Science"),
          subjectBrowseItem(1, "Science--Methodology"),
          subjectBrowseItem(1, "Science--Philosophy")))),

      arguments(aroundIncludingQuery, "music", 11, new SubjectBrowseResult()
        .totalRecords(28).prev("Database design").next("Music")
        .items(List.of(
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(8, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_1, null, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, null, MUSIC_TYPE_ID_2, true),
          subjectBrowseItem(1, "Music", null, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_1, true),
          subjectBrowseItem(1, "Music", null, MUSIC_SOURCE_ID_1, null, true)))),

      arguments(aroundIncludingQuery, "FC", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Europe").next("Music")
        .items(List.of(
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(0, "FC", true),
          subjectBrowseItem(8, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2)))),

      arguments(aroundIncludingQuery, "a", 5, new SubjectBrowseResult()
        .totalRecords(28).prev(null).next("Book")
        .items(List.of(
          subjectBrowseItem(0, "a", true),
          subjectBrowseItem(7, "Biography"),
          subjectBrowseItem(1, "Book")))),

      arguments(aroundIncludingQuery, "z", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Water--Purification").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply"),
          subjectBrowseItem(0, "z", true)))),

      // browsing forward
      arguments(forwardQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Water--Analysis").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology"),
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply")))),

      arguments(forwardQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Fantasy").next("Music")
        .items(List.of(
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(8, "History"),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_1, null),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1)))),

      arguments(forwardQuery, "Z", 10, subjectBrowseResult(28, emptyList())),

      arguments(forwardIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Water").next(null)
        .items(List.of(
          subjectBrowseItem(1, "Water"),
          subjectBrowseItem(1, "Water--Analysis"),
          subjectBrowseItem(1, "Water--Microbiology"),
          subjectBrowseItem(1, "Water--Purification"),
          subjectBrowseItem(1, "Water-supply")))),

      arguments(forwardIncludingQuery, "biology", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      // browsing backward
      arguments(backwardQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Science--Methodology").next("United States")
        .items(List.of(
          subjectBrowseItem(1, "Science--Methodology"),
          subjectBrowseItem(1, "Science--Philosophy"),
          subjectBrowseItem(1, "Text"),
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2)))),

      arguments(backwardQuery, "fun", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      arguments(backwardQuery, "G", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Book").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(1, "Book"),
          subjectBrowseItem(1, "Database design"),
          subjectBrowseItem(1, "Database management"),
          subjectBrowseItem(1, "Europe"),
          subjectBrowseItem(1, "Fantasy")))),

      arguments(backwardQuery, "A", 10, subjectBrowseResult(28, emptyList())),

      arguments(backwardIncludingQuery, "water", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Science--Philosophy").next("Water")
        .items(List.of(
          subjectBrowseItem(1, "Science--Philosophy"),
          subjectBrowseItem(1, "Text"),
          subjectBrowseItem(1, "Textbooks"),
          subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
          subjectBrowseItem(1, "Water")))),

      arguments(backwardIncludingQuery, "fun", 5, new SubjectBrowseResult()
        .totalRecords(28).prev("Book").next("Fantasy")
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
          if (val instanceof List<?> list) {
            var subject =  new Subject().value(String.valueOf(list.get(0)));
            if (list.size() == 4) {
              subject.setAuthorityId(Objects.toString(list.get(1), null));
              subject.setSourceId(Objects.toString(list.get(2), null));
              subject.setTypeId(Objects.toString(list.get(3), null));
            }
            return subject;
          } else {
            return new Subject().value(String.valueOf(val));
          }
        }).toList())
      .staffSuppress(false)
      .discoverySuppress(false)
      .holdings(emptyList());
  }

  private static List<List<Object>> subjectBrowseInstanceData() {
    return List.of(
      List.of("instance #01", List.of("History",
        List.of("Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1),
        List.of("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2), "Biography")),
      List.of("instance #02", List.of("Fantasy",
        List.of("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2))),
      List.of("instance #03", List.of(Arrays.asList("United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2),
        "History", "Rules")),
      List.of("instance #04", List.of("Europe",
        Arrays.asList("Music", null, null, MUSIC_TYPE_ID_1))),
      List.of("instance #05", List.of("Book", "Text", "Biography")),
      List.of("instance #06", List.of("Religion", "History", "Philosophy")),
      List.of("instance #07", List.of("Science", "Science--Methodology", "Science--Philosophy")),
      List.of("instance #08", List.of("Water", "Water-supply")),
      List.of("instance #09", List.of("Water--Analysis", "Water--Purification", "Water--Microbiology")),
      List.of("instance #10", List.of("Database design", "Database management", "Textbooks")),
      List.of("instance #11", List.of("History",
        Arrays.asList("Music", null, null, MUSIC_TYPE_ID_1), "Biography")),
      List.of("instance #12", List.of("History",
        Arrays.asList("Music", MUSIC_AUTHORITY_ID_1, null, MUSIC_TYPE_ID_2), "Biography")),
      List.of("instance #13", List.of("History",
        Arrays.asList("Music", null, MUSIC_SOURCE_ID_1, null), "Biography")),
      List.of("instance #14", List.of("History",
        Arrays.asList("Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID_1, null), "Biography")),
      List.of("instance #15", List.of("History",
        Arrays.asList("Music", null, MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_1), "Biography"))
    );
  }

  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("cql.allRecords=1", array("sourceId"), mapOf("sourceId",
        facet(facetItem(MUSIC_SOURCE_ID_1, 4), facetItem(MUSIC_SOURCE_ID_2, 2)))),
      arguments("cql.allRecords=1", array("typeId"), mapOf("typeId",
        facet(facetItem(MUSIC_TYPE_ID_1, 3), facetItem(MUSIC_TYPE_ID_2, 3))))
    );
  }
}
