package org.folio.api.search;

import static org.folio.cql2pgjson.model.CqlSort.ASCENDING;
import static org.folio.cql2pgjson.model.CqlSort.DESCENDING;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.allRecordsSortedBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class SortInstanceIT extends BaseSharedTest {

  private static final String ID_ANIMAL_FARM = "00000012-0000-4000-8000-000000000000";
  private static final String ID_ZERO_MINUS_TEN = "00000014-0000-4000-8000-000000000000";
  private static final String ID_CALLING_ME_HOME = "00000016-0000-4000-8000-000000000000";
  private static final String ID_WALK_IN_MY_SOUL = "00000018-0000-4000-8000-000000000000";
  private static final String ID_STAR_WARS = "00000020-0000-4000-8000-000000000000";

  private static final String TAG_SORT_TITLE_FILTER = "tags.tagList==\"sort-titles\"";
  private static final String TAG_SORT_INSTANCE_FILTER = "tags.tagList==\"sort-instance\"";

  private static final List<String> TITLES = List.of(
    "Ground water in Africa",
    "Ground water in North Africa",
    "Ground-water hydrology of the Chad Basin",
    "Ground-water resources of Bengasi area",
    "Ground-water exploration in Al Marj (1964)",
    "Regional ground-water hydrology of Tunisia",
    "Occurrence of ground water in Tabulbah (study 1)",
    "Evaluation of ground-water conditions in Cape Verde",
    "Significance of ground-water chemistry in North Sahara",
    "Occurrence of ground water in Tabulbah (study 2)",
    "Ground water in Sirte, Libya",
    "Ground water in Eastern and Southern Africa",
    "Ground-water exploration in Al Marj (1966)"
  );

  @Test
  void canSortInstancesByTitles_asc() throws Exception {
    var expectedTitleOrder = TITLES.stream()
      .sorted(String::compareToIgnoreCase)
      .toList();

    doSearchInstances(TAG_SORT_TITLE_FILTER + " sortBy title", TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(13)))
      .andExpect(jsonPath("instances[*].title", is(expectedTitleOrder)));
  }

  @Test
  void canSortInstancesByContributors_asc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy contributors/sort." + ASCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("1111 2222")))
      .andExpect(jsonPath("instances[1].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[0].name", is("Śląsk")))
      .andExpect(jsonPath("instances[4].contributors[0].name", is("yyy zzz")));
  }

  @Test
  void canSortInstancesByContributors_desc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy contributors/sort." + DESCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].contributors[0].name", is("yyy zzz")))
      .andExpect(jsonPath("instances[1].contributors[0].name", is("Śląsk")))
      .andExpect(jsonPath("instances[2].contributors[0].name", is("bcc ccc")))
      .andExpect(jsonPath("instances[3].contributors[1].name", is("bbb ccc")))
      .andExpect(jsonPath("instances[4].contributors[0].name", is("1111 2222")));
  }

  @Test
  void canSortInstancesByDate1_asc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy normalizedDate1/sort." + ASCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].dates.date1", is("19u5")))
      .andExpect(jsonPath("instances[1].dates.date1", is("198u")))
      .andExpect(jsonPath("instances[2].dates.date1", is("1999")))
      .andExpect(jsonPath("instances[3].dates.date1", is("2001")))
      .andExpect(jsonPath("instances[4].dates.date1", is("2021")));
  }

  @Test
  void canSortInstancesByDate1_desc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy normalizedDate1/sort." + DESCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].dates.date1", is("2021")))
      .andExpect(jsonPath("instances[1].dates.date1", is("2001")))
      .andExpect(jsonPath("instances[2].dates.date1", is("1999")))
      .andExpect(jsonPath("instances[3].dates.date1", is("198u")))
      .andExpect(jsonPath("instances[4].dates.date1", is("19u5")));
  }

  @Test
  void canSortInstancesByTitle_asc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy title/sort." + ASCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].title", is("Calling Me Home")))
      .andExpect(jsonPath("instances[1].title", is("Animal farm")))
      .andExpect(jsonPath("instances[2].title", is("Star Wars")))
      .andExpect(jsonPath("instances[3].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[4].title", is("Zero Minus Ten")));
  }

  @Test
  void canSortInstancesByTitle_desc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy title/sort." + DESCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].title", is("Zero Minus Ten")))
      .andExpect(jsonPath("instances[1].title", is("Walk in My Soul")))
      .andExpect(jsonPath("instances[2].title", is("Star Wars")))
      .andExpect(jsonPath("instances[3].title", is("Animal farm")))
      .andExpect(jsonPath("instances[4].title", is("Calling Me Home")));
  }

  @Test
  void canSortInstancesByMetadataCreatedDate_asc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy metadata.createdDate/sort." + ASCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_STAR_WARS)))
      .andExpect(jsonPath("instances[1].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[4].id", is(ID_ANIMAL_FARM)));
  }

  @Test
  void canSortInstancesByMetadataCreatedDate_desc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy metadata.createdDate/sort." + DESCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_ANIMAL_FARM)))
      .andExpect(jsonPath("instances[1].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[4].id", is(ID_STAR_WARS)));
  }

  @Test
  void canSortInstancesByMetadataUpdatedDate_asc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy metadata.updatedDate/sort." + ASCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_STAR_WARS)))
      .andExpect(jsonPath("instances[1].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[4].id", is(ID_ANIMAL_FARM)));
  }

  @Test
  void canSortInstancesByMetadataUpdatedDate_desc() throws Exception {
    doSearchInstances(TAG_SORT_INSTANCE_FILTER + " sortBy metadata.updatedDate/sort." + DESCENDING, TENANT_ID)
      .andExpect(jsonPath("totalRecords", is(5)))
      .andExpect(jsonPath("instances[0].id", is(ID_ANIMAL_FARM)))
      .andExpect(jsonPath("instances[1].id", is(ID_WALK_IN_MY_SOUL)))
      .andExpect(jsonPath("instances[2].id", is(ID_CALLING_ME_HOME)))
      .andExpect(jsonPath("instances[3].id", is(ID_ZERO_MINUS_TEN)))
      .andExpect(jsonPath("instances[4].id", is(ID_STAR_WARS)));
  }

  @Test
  void search_negative_invalidSortOption() throws Exception {
    attemptSearchInstances(allRecordsSortedBy("unknownSort", DESCENDING), TENANT_ID).andExpect(
        jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Sort field not found or cannot be used.")))
      .andExpect(jsonPath("$.errors[0].type", is("RequestValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("sortField")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("unknownSort")));
  }
}
