package org.folio.api.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.ALL_RECORDS_QUERY;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.testdata.ConsortiumTestDataManager.MUSIC_AUTHORITY_ID_1;
import static org.folio.support.testdata.ConsortiumTestDataManager.MUSIC_SOURCE_ID_1;
import static org.folio.support.testdata.ConsortiumTestDataManager.MUSIC_SOURCE_ID_2;
import static org.folio.support.testdata.ConsortiumTestDataManager.MUSIC_TYPE_ID_1;
import static org.folio.support.testdata.ConsortiumTestDataManager.MUSIC_TYPE_ID_2;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.facet;
import static org.folio.support.utils.TestUtils.facetItem;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.subjectBrowseItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Facet;
import org.folio.search.domain.dto.FacetResult;
import org.folio.search.domain.dto.RecordType;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseSubjectConsortiumIT extends BaseConsortiumIntegrationTest {

  @Test
  void browseBySubject_browsingAround_shared() {
    var request = get(instanceSubjectBrowsePath())
      .param(QUERY_PARAM, "("
                          + prepareQuery("value < {value} or value >= {value}", "\"Rules\"") + ") "
                          + "and instances.shared==true")
      .param(LIMIT_PARAM, "5")
      .param(PRECEDING_RECORDS_COUNT_PARAM, "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(11).prev("Music").next(null)
      .items(List.of(
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_1),
        subjectBrowseItem(1, "Music", null, null, MUSIC_TYPE_ID_1),
        subjectBrowseItem(1, "Rules", true),
        subjectBrowseItem(1, "Text"),
        subjectBrowseItem(1, "United States", null, MUSIC_SOURCE_ID_1, MUSIC_TYPE_ID_2))));
  }

  @Test
  void browseBySubject_browsingAround_local() {
    var request = get(instanceSubjectBrowsePath())
      .param(QUERY_PARAM, "("
                          + prepareQuery("value < {value} or value >= {value}", "\"Science\"") + ") "
                          + "and instances.shared==false")
      .param(LIMIT_PARAM, "5")
      .param(PRECEDING_RECORDS_COUNT_PARAM, "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(22).prev("Philosophy").next("Science--Philosophy")
      .items(List.of(
        subjectBrowseItem(1, "Philosophy"),
        subjectBrowseItem(1, "Religion"),
        subjectBrowseItem(1, "Science", true),
        subjectBrowseItem(1, "Science--Methodology"),
        subjectBrowseItem(1, "Science--Philosophy"))));
  }

  @MethodSource("facetQueriesProvider")
  @ParameterizedTest(name = "[{index}] query={0}, facets={1}")
  @DisplayName("getFacetsForSubjects_parameterized")
  void getFacetsForSubjects_parameterized(String query, String[] facets, Map<String, Facet> expected) {
    var actual = parseResponse(doGet(recordFacetsPath(RecordType.SUBJECTS, query, facets)), FacetResult.class);

    expected.forEach((facetName, expectedFacet) -> {
      assertNotNull(actual.getFacets());
      var actualFacet = actual.getFacets().get(facetName);

      assertThat(actualFacet).isNotNull();
      assertThat(actualFacet.getValues())
        .containsExactlyInAnyOrderElementsOf(expectedFacet.getValues());
    });
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments(ALL_RECORDS_QUERY, array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 22), facetItem("true", 11)))),
      arguments(ALL_RECORDS_QUERY, array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(MEMBER_TENANT_ID, 22),
          facetItem(CENTRAL_TENANT_ID, 11)))),
      arguments(ALL_RECORDS_QUERY, array("sourceId"), mapOf("sourceId",
        facet(facetItem(MUSIC_SOURCE_ID_1, 4), facetItem(MUSIC_SOURCE_ID_2, 2)))),
      arguments(ALL_RECORDS_QUERY, array("typeId"), mapOf("typeId",
        facet(facetItem(MUSIC_TYPE_ID_1, 3), facetItem(MUSIC_TYPE_ID_2, 3)))),
      //cases with filter query
      arguments("sourceId==(\"%s\")".formatted(MUSIC_SOURCE_ID_1),
        array("instances.shared"), mapOf("instances.shared",
          facet(facetItem("false", 2), facetItem("true", 2)))),
      arguments("sourceId==(\"%s\") and typeId==(\"%s\")".formatted(MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2),
        array("instances.shared"), mapOf("instances.shared", facet(facetItem("true", 1)))),
      arguments("sourceId==(\"%s\")".formatted(MUSIC_SOURCE_ID_1),
        array("instances.tenantId"), mapOf("instances.tenantId",
          facet(facetItem(CENTRAL_TENANT_ID, 2), facetItem(MEMBER_TENANT_ID, 2)))),
      arguments("sourceId==(\"%s\") and typeId==(\"%s\")".formatted(MUSIC_SOURCE_ID_2, MUSIC_TYPE_ID_2),
        array("instances.tenantId"), mapOf("instances.tenantId", facet(facetItem(CENTRAL_TENANT_ID, 1)))),
      arguments("instances.shared==true", array("sourceId"), mapOf("sourceId",
        facet(facetItem(MUSIC_SOURCE_ID_1, 2), facetItem(MUSIC_SOURCE_ID_2, 1)))),
      arguments("instances.tenantId==(\"%s\")".formatted(MEMBER_TENANT_ID), array("typeId"), mapOf("typeId",
        facet(facetItem(MUSIC_TYPE_ID_1, 2), facetItem(MUSIC_TYPE_ID_2, 1))))
    );
  }
}
