package org.folio.search.controller;

import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


public class SearchWithSpecialCharactersTest extends BaseIntegrationTest {
  private static final Instance[] INSTANCES = instances();

  @BeforeAll
  static void prepare() {
    setUpTenant(INSTANCES);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("specialCharactersInstanceData")
  @ParameterizedTest(name = "[{index}] title={0}")
  void searchByAllKeyword_parameterized(String title) throws Exception {
    var query = String.format("keyword all \"%s\"", title);
    doSearchByInstances(query)
      .andExpect(jsonPath("$.totalRecords", is(1)));
  }

  @MethodSource("specialCharactersInstanceData")
  @ParameterizedTest(name = "[{index}] title={0}")
  void searchByAllTitle_parameterized(String title) throws Exception {
    var query = String.format("title all \"%s\"", title);
    doSearchByInstances(query)
      .andExpect(jsonPath("$.totalRecords", is(1)));
  }

  private static Instance[] instances() {
    return specialCharactersInstanceData().stream()
      .map(SearchWithSpecialCharactersTest::instance)
      .toArray(Instance[]::new);
  }

  private static Instance instance(String title) {
    return new Instance().id(randomId())
      .title(title)
      .staffSuppress(false)
      .discoverySuppress(false)
      .isBoundWith(false);
  }

  private static List<String> specialCharactersInstanceData() {
    return List.of(
      "Classification & cataloging quarterly",
      "Classification one / cataloging quarterly",
      "Classification two \\ cataloging quarterly",
      "Classification three ] cataloging quarterly",
      "Classification five [ cataloging quarterly",
      "Classical form : a theory of formal functions for the instrumental music of Haydn, Mozart, and Beethoven",
      "Harry Potter and the cursed child : Part one; (a new play by Jack Thorne).",
      "'Harry Potter and the cursed child' / Part two : {a new play by Jack Thorne}",
      ".Harry Potter and the cursed child - Part three [a new play] by Jack Thorne !",
      "Harry Potter | and the cursed child-Part four ; a new play by Jack Thorne",
      "@ Harry Potter and the cursed child Part five a new play by Jack Thorne",
      "1960 hydrologic data ~ Mekong ` River ! Basin @ in Thailand # a by ^ Harza Engineering ( Company ) prepared - for _ Committee + for = Coordination { of Investigation } of the Lower Mekong [ River Basin ] and the U S Agency | for \\ International / Development : Données ; hydrologiques pour \" l'année 1960, basin \" [sic] du ' Mékong < en > Thaïlande : rapport / preparé par Harza Engineering Company ; pour le Comité des de coordination , des études sur le bassin du Mékong inférieur et l'Agence des Etats-Unis . pour le développment international ?"
    );
  }
}
