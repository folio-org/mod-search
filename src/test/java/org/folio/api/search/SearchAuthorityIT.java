package org.folio.api.search;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.sample.SampleAuthorities.getAuthorityNaturalId;
import static org.folio.support.sample.SampleAuthorities.getAuthoritySampleAsMap;
import static org.folio.support.sample.SampleAuthorities.getAuthoritySampleId;
import static org.folio.support.sample.SampleAuthorities.getAuthoritySourceFileId;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.search.domain.dto.AlternativeTitle;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthoritySearchResult;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SeriesItem;
import org.folio.search.domain.dto.Subject;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchAuthorityIT extends BaseIntegrationTest {

  private static final String AUTHORIZED_TYPE = "Authorized";
  private static final String REFERENCE_TYPE = "Reference";
  private static final String AUTH_REF_TYPE = "Auth/Ref";

  @BeforeAll
  static void prepare() {
    setUpTenant(Authority.class, 51, getAuthoritySampleAsMap());

    //set up linked instances
    var instance1 = new Instance().id(randomId()).title("test-resource")
      .subjects(List.of(new Subject().value("s1").authorityId(getAuthoritySampleId())));
    var instance2 = new Instance().id(randomId()).title("test-resource")
      .contributors(List.of(new Contributor().name("c1").authorityId(getAuthoritySampleId())));
    var instance3 = new Instance().id(randomId()).title("test-resource")
      .alternativeTitles(List.of(new AlternativeTitle().alternativeTitle("a1").authorityId(getAuthoritySampleId())));
    var instance4 = new Instance().id(randomId()).title("test-resource")
      .series(List.of(new SeriesItem().value("s1").authorityId(getAuthoritySampleId())));

    inventoryApi.createInstance(TENANT_ID, instance1);
    inventoryApi.createInstance(TENANT_ID, instance2);
    inventoryApi.createInstance(TENANT_ID, instance3);
    inventoryApi.createInstance(TENANT_ID, instance4);
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, instanceSearchPath(), 4, emptyList());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @CsvFileSource(resources = "/test-resources/authority-search-test-queries.csv")
  @DisplayName("search by authorities (single authority found)")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  void searchByAuthorities_parameterized(String query, String value) throws Exception {
    doSearchByAuthorities(prepareQuery(query, value))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.authorities[0].id", is(getAuthoritySampleId())));
  }

  @Test
  @DisplayName("search by authorities (no authority found)")
  void searchBy_parameterized_noResult() throws Throwable {
    doSearchByAuthorities(prepareQuery("id=\"{value}\"", "random-val"))
      .andExpect(jsonPath("$.totalRecords", is(0)))
      .andExpect(jsonPath("$.authorities", notNullValue()));
  }

  @CsvSource({
    "cql.allRecords=1,",
    "id={value}, \"\"",
    "id=={value}, 55294032-fcf6-45cc-b6da-4420a61ef72c",
    "id=={value}, 55294032-fcf6-45cc-b6da-*"
  })
  @SuppressWarnings("checkstyle:MethodLength")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}''")
  @DisplayName("search by authorities (check that they are divided correctly)")
  void searchByAuthorities_parameterized_all(String query, String value) throws Exception {
    var response = doSearchByAuthorities(prepareQuery(query, value)).andExpect(jsonPath("$.totalRecords", is(51)));
    var actual = parseResponse(response, AuthoritySearchResult.class);
    assertThat(actual.getAuthorities()).asInstanceOf(InstanceOfAssertFactories.LIST).containsOnly(
      authority("Personal Name", AUTHORIZED_TYPE, "Gary A. Wills", 4),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal name", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal name", null),

      authority("Personal Name", AUTHORIZED_TYPE, "a personal title", 4),
      authority("Personal Name", REFERENCE_TYPE, "a sft personal title", null),
      authority("Personal Name", AUTH_REF_TYPE, "a saft personal title", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate name", 4),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate name", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate name", null),

      authority("Corporate Name", AUTHORIZED_TYPE, "a corporate title", 4),
      authority("Corporate Name", REFERENCE_TYPE, "a sft corporate title", null),
      authority("Corporate Name", AUTH_REF_TYPE, "a saft corporate title", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference name", 4),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference name", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference name", null),

      authority("Conference Name", AUTHORIZED_TYPE, "a conference title", 4),
      authority("Conference Name", REFERENCE_TYPE, "a sft conference title", null),
      authority("Conference Name", AUTH_REF_TYPE, "a saft conference title", null),

      authority("Geographic Name", AUTHORIZED_TYPE, "a geographic name", 4),
      authority("Geographic Name", REFERENCE_TYPE, "a sft geographic name", null),
      authority("Geographic Name", AUTH_REF_TYPE, "a saft geographic name", null),

      authority("Uniform Title", AUTHORIZED_TYPE, "an uniform title", 4),
      authority("Uniform Title", REFERENCE_TYPE, "a sft uniform title", null),
      authority("Uniform Title", AUTH_REF_TYPE, "a saft uniform title", null),

      authority("Named Event", AUTHORIZED_TYPE, "a named event", 4),
      authority("Named Event", REFERENCE_TYPE, "a sft named event", null),
      authority("Named Event", AUTH_REF_TYPE, "a saft named event", null),

      authority("General Subdivision", AUTHORIZED_TYPE, "a general subdivision", 4),
      authority("General Subdivision", REFERENCE_TYPE, "a sft general subdivision", null),
      authority("General Subdivision", AUTH_REF_TYPE, "a saft general subdivision", null),

      authority("Topical", AUTHORIZED_TYPE, "a topical term", 4),
      authority("Topical", REFERENCE_TYPE, "a sft topical term", null),
      authority("Topical", AUTH_REF_TYPE, "a saft topical term", null),

      authority("Genre", AUTHORIZED_TYPE, "a genre term", 4),
      authority("Genre", REFERENCE_TYPE, "a sft genre term", null),
      authority("Genre", AUTH_REF_TYPE, "a saft genre term", null),

      authority("Chronological Term", AUTHORIZED_TYPE, "a chron term", 4),
      authority("Chronological Term", REFERENCE_TYPE, "a sft chron term", null),
      authority("Chronological Term", AUTH_REF_TYPE, "a saft chron term", null),

      authority("Medium of Performance Term", AUTHORIZED_TYPE, "a medium perf term", 4),
      authority("Medium of Performance Term", REFERENCE_TYPE, "a sft medium perf term", null),
      authority("Medium of Performance Term", AUTH_REF_TYPE, "a saft medium perf term", null),

      authority("Geographic Subdivision", AUTHORIZED_TYPE, "a geographic subdivision", 4),
      authority("Geographic Subdivision", REFERENCE_TYPE, "a sft geographic subdivision", null),
      authority("Geographic Subdivision", AUTH_REF_TYPE, "a saft geographic subdivision", null),

      authority("Chronological Subdivision", AUTHORIZED_TYPE, "a chron subdivision", 4),
      authority("Chronological Subdivision", REFERENCE_TYPE, "a sft chron subdivision", null),
      authority("Chronological Subdivision", AUTH_REF_TYPE, "a saft chron subdivision", null),

      authority("Form Subdivision", AUTHORIZED_TYPE, "a form subdivision", 4),
      authority("Form Subdivision", REFERENCE_TYPE, "a sft form subdivision", null),
      authority("Form Subdivision", AUTH_REF_TYPE, "a saft form subdivision", null)
    );
  }

  private static Authority authority(String headingType, String authRefType, String headingRef,
                                     Integer numberOfTitles) {
    return new Authority()
      .id(getAuthoritySampleId())
      .tenantId(TENANT_ID)
      .shared(false)
      .sourceFileId(getAuthoritySourceFileId())
      .naturalId(getAuthorityNaturalId())
      .headingType(headingType)
      .authRefType(authRefType)
      .headingRef(headingRef)
      .numberOfTitles(numberOfTitles);
  }
}
