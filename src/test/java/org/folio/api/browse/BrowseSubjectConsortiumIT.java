package org.folio.api.browse;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.folio.support.base.BaseConsortiumIntegrationTest;
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
class BrowseSubjectConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final String MUSIC_SOURCE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91b";
  private static final String MUSIC_SOURCE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bd";
  private static final String MUSIC_TYPE_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91c";
  private static final String MUSIC_TYPE_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21be";
  private static final Instance[] INSTANCES_MEMBER = instancesMember();
  private static final Instance[] INSTANCES_CENTRAL = instancesCentral();

  @BeforeAll
  static void prepare(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);

    enableFeature(CENTRAL_TENANT_ID, BROWSE_SUBJECTS);

    var timestamp = subResourcesLockRepository.lockSubResource(ReindexEntityType.SUBJECT, CENTRAL_TENANT_ID);
    if (timestamp.isEmpty()) {
      throw new IllegalStateException("Unexpected state of database: unable to lock subject resource");
    }

    saveRecords(CENTRAL_TENANT_ID, instanceSearchPath(), asList(INSTANCES_CENTRAL),
      INSTANCES_CENTRAL.length,
      instance -> inventoryApi.createInstance(CENTRAL_TENANT_ID, instance));
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), asList(INSTANCES_MEMBER),
      INSTANCES_CENTRAL.length + INSTANCES_MEMBER.length,
      instance -> inventoryApi.createInstance(MEMBER_TENANT_ID, instance));

    subResourcesLockRepository.unlockSubResource(ReindexEntityType.SUBJECT, timestamp.get(), CENTRAL_TENANT_ID);

    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
      var counted = countIndexDocument(INSTANCE_SUBJECT, CENTRAL_TENANT_ID);
      assertThat(counted).isEqualTo(28);
    });
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void browseBySubject_browsingAround_shared() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", "("
                      + prepareQuery("value < {value} or value >= {value}", "\"Rules\"") + ") "
                      + "and instances.shared==true")
      .param("limit", "5")
      .param("precedingRecordsCount", "2");
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
      .param("query", "("
                      + prepareQuery("value < {value} or value >= {value}", "\"Science\"") + ") "
                      + "and instances.shared==false")
      .param("limit", "5")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(20).prev("Philosophy").next("Science--Philosophy")
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

  private static Instance[] instancesCentral() {
    return subjectBrowseInstanceData().subList(0, 5).stream()
      .map(BrowseSubjectConsortiumIT::instance)
      .toArray(Instance[]::new);
  }

  private static Instance[] instancesMember() {
    var instances = subjectBrowseInstanceData();
    return instances.subList(5, instances.size()).stream()
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
          if (val instanceof List<?> list) {
            var subject = new Subject().value(String.valueOf(list.get(0)));
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

  @SuppressWarnings("checkstyle:MethodLength")
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

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> facetQueriesProvider() {
    return Stream.of(
      arguments("cql.allRecords=1", array("instances.shared"), mapOf("instances.shared",
        facet(facetItem("false", 20), facetItem("true", 11)))),
      arguments("cql.allRecords=1", array("instances.tenantId"),
        mapOf("instances.tenantId", facet(facetItem(MEMBER_TENANT_ID, 20),
          facetItem(CENTRAL_TENANT_ID, 11)))),
      arguments("cql.allRecords=1", array("sourceId"), mapOf("sourceId",
        facet(facetItem(MUSIC_SOURCE_ID_1, 4), facetItem(MUSIC_SOURCE_ID_2, 2)))),
      arguments("cql.allRecords=1", array("typeId"), mapOf("typeId",
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
