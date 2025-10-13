package org.folio.api.search;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.support.base.ApiEndpoints.allRecordsSortedBy;
import static org.folio.support.sample.SampleInstances.getSemanticWeb;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Dates;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Metadata;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SortInstanceIT extends BaseIntegrationTest {

  private static final String ID_ANIMAL_FARM = randomId();
  private static final String ID_ZERO_MINUS_TEN = randomId();
  private static final String ID_CALLING_ME_HOME = randomId();
  private static final String ID_WALK_IN_MY_SOUL = randomId();
  private static final String ID_STAR_WARS = randomId();

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
    doSearchByInstances(allRecordsSortedBy("contributors", ASCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("1111 2222")))
      .andExpect(jsonPath("instances[1].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("Śląsk")))
      .andExpect(jsonPath("instances[4].contributors[0].name", is("yyy zzz")));
  }

  @Test
  void canSortInstancesByContributors_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("contributors", DESCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("yyy zzz")))
      .andExpect(jsonPath("instances[1].contributors[0].name", is("Śląsk")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[4].contributors[0].name", is("1111 2222")));
  }

  @Test
  void canSortInstancesByDate1_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("normalizedDate1", ASCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].dates.date1", is("19u5")))
      .andExpect(jsonPath("instances[1].dates.date1", is("199u")))
      .andExpect(jsonPath("instances[2].dates.date1", is("1999")))
      .andExpect(jsonPath("instances[3].dates.date1", is("2001")))
      .andExpect(jsonPath("instances[4].dates.date1", is("2021")));
  }

  @Test
  void canSortInstancesByDate1_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("normalizedDate1", DESCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].dates.date1", is("2021")))
      .andExpect(jsonPath("instances[1].dates.date1", is("2001")))
      .andExpect(jsonPath("instances[2].dates.date1", is("1999")))
      .andExpect(jsonPath("instances[3].dates.date1", is("199u")))
      .andExpect(jsonPath("instances[4].dates.date1", is("19u5")));
  }

  @Test
  void canSortInstancesByTitle_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("title", ASCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].title", is("Calling Me Home")))
      .andExpect(jsonPath("instances[1].title", is("Animal farm")))
      .andExpect(jsonPath("instances[2].title", is("Star Wars")))
      .andExpect(jsonPath("instances[3].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[4].title", is("Zero Minus Ten")));
  }

  @Test
  void canSortInstancesByTitle_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("title", DESCENDING)).andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].title", is("Zero Minus Ten")))
      .andExpect(jsonPath("instances[1].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[2].title", is("Star Wars")))
      .andExpect(jsonPath("instances[3].title", is("Animal farm")))
      .andExpect(jsonPath("instances[4].title", is("Calling Me Home")));
  }

  @Test
  void canSortInstancesByMetadataCreatedDate_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("metadata.createdDate", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_ANIMAL_FARM)))
      .andExpect(jsonPath("instances[1].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[4].id", is(ID_STAR_WARS)));
  }

  @Test
  void canSortInstancesByMetadataCreatedDate_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("metadata.createdDate", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_STAR_WARS)))
      .andExpect(jsonPath("instances[1].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[4].id", is(ID_ANIMAL_FARM)));
  }

  @Test
  void canSortInstancesByMetadataUpdatedDate_asc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("metadata.updatedDate", ASCENDING))
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_ANIMAL_FARM)))
      .andExpect(jsonPath("instances[1].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[4].id", is(ID_STAR_WARS)));
  }

  @Test
  void canSortInstancesByMetadataUpdatedDate_desc() throws Exception {
    doSearchByInstances(allRecordsSortedBy("metadata.updatedDate", DESCENDING))
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_STAR_WARS)))
      .andExpect(jsonPath("instances[1].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[4].id", is(ID_ANIMAL_FARM)));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchByInstances(allRecordsSortedBy("unknownSort", DESCENDING)).andExpect(
        jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }

  private static Instance[] instances() {
    var instances = new Instance[] {
      getSemanticWeb().id(ID_ANIMAL_FARM).contributors(new ArrayList<>()),
      getSemanticWeb().id(ID_ZERO_MINUS_TEN).contributors(new ArrayList<>()),
      getSemanticWeb().id(ID_CALLING_ME_HOME).contributors(new ArrayList<>()),
      getSemanticWeb().id(ID_WALK_IN_MY_SOUL).contributors(new ArrayList<>()),
      getSemanticWeb().id(ID_STAR_WARS).contributors(new ArrayList<>())
    };
    instances[0].title("Animal farm")
      .indexTitle("B1 Animal farm")
      .addContributorsItem(new Contributor().name("yyy zzz"))
      .dates(getDates("1999", "2000"))
      .metadata(new Metadata()
        .createdDate("2021-01-01T10:00:00.000+00:00")
        .updatedDate("2021-01-15T10:00:00.000+00:00"));

    instances[1].title("Zero Minus Ten")
      .indexTitle(null)
      .addContributorsItem(new Contributor().name("aaa bbb").primary(false))
      .addContributorsItem(new Contributor().name("bbb ccc").primary(true))
      .dates(getDates("199u", "2000"))
      .metadata(new Metadata()
        .createdDate("2021-02-01T10:00:00.000+00:00")
        .updatedDate("2021-02-15T10:00:00.000+00:00"));

    instances[2].title("Calling Me Home")
      .indexTitle("A1 Calling Me Home")
      .addContributorsItem(new Contributor().name("bcc ccc"))
      .dates(getDates("2021", "2022"))
      .metadata(new Metadata()
        .createdDate("2021-03-01T10:00:00.000+00:00")
        .updatedDate("2021-03-15T10:00:00.000+00:00"));

    instances[3].title("Walk in My Soul")
      .indexTitle(null)
      .addContributorsItem(new Contributor().name("1111 2222").primary(true))
      .dates(getDates("2001", "2002"))
      .metadata(new Metadata()
        .createdDate("2021-04-01T10:00:00.000+00:00")
        .updatedDate("2021-04-15T10:00:00.000+00:00"));

    instances[4].title("Star Wars").indexTitle(null)
      .addContributorsItem(new Contributor().name("Śląsk").primary(true))
      .dates(getDates("19u5", "1998"))
      .metadata(new Metadata()
        .createdDate("2021-05-01T10:00:00.000+00:00")
        .updatedDate("2021-05-15T10:00:00.000+00:00"));

    return instances;
  }

  private static Dates getDates(String date1, String date2) {
    Dates dates = new Dates();
    dates.setDate1(date1);
    dates.setDate2(date2);
    return dates;
  }
}
