package org.folio.api.search;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
public abstract class SortInstanceByTitleIT extends BaseIntegrationTest {

  public static final List<String> TITLES = List.of(
    "Ground water in Africa.",

    "Ground water in North and West Africa.",

    "Ground-water hydrology of the Chad Basin in Bornu and Dikwa Emirates, Northeastern Nigeria :with special emphasis "
      + "on the flow life of the Artesian system  /by R. E. Miller [and three others].",

    "Ground-water resources of the Bengasi area, Cyrenaica, United Kingdom of Libya / by W.W. Doyel and F.J. Maguire.",

    "Ground-water exploration in Al Marj area, Cyrenaica, United Kingdom of Libya "
      + "/ by T.G. Newport and Yousef Haddor.",

    "Regional geology and ground-water hydrology of the SaÌhÌʹil SuÌsah area, Tunisia "
      + "/ by L.C. Dutcher and H.E. Thomas.",

    "The occurrence, chemical quality and use of ground water in the TÌʹabulbah area, "
      + "Tunisia by L.C. Dutcher and H.E. Thomas.",

    "Evaluation of baseline ground-water conditions in the Mosteiros, Ribeira Paul, and Ribeira Fajã Basins, "
      + "Republic of Cape Verde, West Africa, 2005-06by Victor M. Heilweil ... [et al.] ; prepared in cooperation "
      + "with the Millennium Challenge Corporation, Millennium Challenge Account, and Instituto Nacional de "
      + "Gestão dos Recursos Hídricos.",

    "Significance of ground-water chemistry in performance of North Sahara tube wells "
      + "in Algeria and Tunisia by Frank E. Clarke and Blair F. Jones.",

    "The occurrence, chemical quality and use of ground water in the TÌʹabulbah area, Tunisia "
      + "/ by L.C. Dutcher and H.E. Thomas.",

    "Ground water in the Sirte area, Tripolitania United Kingdom of Libya  /by William Ogilbee.",

    "Ground water in Eastern, Central and Southern Africa /United Nations, "
      + "Department of Technical Co-operation for Development.",

    "Ground-water exploration in Al Marj area, Cyrenaica, United Kingdom of Libya by T.G. Newport and Yousef Haddor."
  );

  public static final List<String> TITLE_IDS = List.of(
      "00000028-0000-4000-8000-000000000000",
      "00000030-0000-4000-8000-000000000000",
      "00000032-0000-4000-8000-000000000000",
      "00000033-0000-4000-8000-000000000000",
      "00000034-0000-4000-8000-000000000000",
      "00000035-0000-4000-8000-000000000000",
      "00000036-0000-4000-8000-000000000000",
      "00000037-0000-4000-8000-000000000000",
      "00000038-0000-4000-8000-000000000000",
      "00000039-0000-4000-8000-000000000000",
      "00000040-0000-4000-8000-000000000000",
      "00000041-0000-4000-8000-000000000000",
      "00000043-0000-4000-8000-000000000000"
  );

  private static final String TAG_FILTER = "tags.tagList==\"sort-instance-by-title\"";

  @Test
  void canSortInstancesByContributors_asc() throws Exception {
    var expectedTitleOrder = TITLES.stream()
      .sorted(String::compareToIgnoreCase)
      .toList();

    doSearchByInstances(TAG_FILTER + " sortBy title")
      .andExpect(jsonPath("totalRecords", is(13)))
      .andExpect(jsonPath("instances[*].title", is(expectedTitleOrder)));
  }
}
