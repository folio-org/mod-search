package org.folio.api.search;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.LinkedDataTestUtils.toId;
import static org.folio.support.utils.LinkedDataTestUtils.toIdType;
import static org.folio.support.utils.LinkedDataTestUtils.toIdValue;
import static org.folio.support.utils.LinkedDataTestUtils.toLabel;
import static org.folio.support.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.support.utils.LinkedDataTestUtils.toTotalRecords;
import static org.folio.support.utils.LinkedDataTestUtils.toType;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class SearchLinkedDataAuthorityIT extends BaseSharedTest {

  @DisplayName("search by linked data authorities (all 2 authorities are found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, cql.allRecords = 1",
    "2, label any \"Label\"",
    "3, label all \"Label\"",
    "4, keyword any \"Label\"",
    "5, id any \"*\"",
    "6, identifier = \"*\"",
    "7, type = \"*\"",
  })
  void searchByLinkedDataAuthority_parameterized_allResults(int index, String query) throws Throwable {
    doSearchLinkedDataAuthority(query, TENANT_ID)
      .andExpect(jsonPath(toTotalRecords(), is(2)));
  }

  @DisplayName("search by linked data authority (single authority is found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, keyword = Authority1",
    "2, keyword = common",
    "3, keyword = Family",
    "4, id = Authority1",
    "5, id == Authority1",
    "6, label = \"Authority1 Label common\"",
    "7, label == \"Authority1 Label common\"",
    "8, label any \"common\"",
    "9, label all \"Authority1 Label common\"",
    "10, identifier = \"047144250X\"",
    "11, identifier = \"0471442*\"",
    "12, identifier == \"047144250X\"",
    "13, identifier any \"047144250X\"",
    "14, identifier all \"047144250X\"",
    "15, identifier = \"2023202345\"",
    "16, identifier = \"2023*\"",
    "17, identifier == \"2023202345\"",
    "18, type = \"Family\"",
    "19, type == \"Family\"",
    "20, type any \"Family Person XXX\"",
    "21, type all \"Family\"",
  })
  void searchByLinkedDataAuthority_parameterized_singleResult(int index, String query) throws Throwable {
    doSearchLinkedDataAuthority(query, TENANT_ID)
      .andExpect(jsonPath(toTotalRecords(), is(1)))
      .andExpect(jsonPath(toId(toRootContent()), is("Authority1")))
      .andExpect(jsonPath(toLabel(toRootContent()), is("Authority1 Label common")))
      .andExpect(jsonPath(toIdValue(toRootContent(), 0), is("047144250X")))
      .andExpect(jsonPath(toIdType(toRootContent(), 0), is("ISBN")))
      .andExpect(jsonPath(toIdValue(toRootContent(), 1), is("  2023202345")))
      .andExpect(jsonPath(toIdType(toRootContent(), 1), is("LCCN")))
      .andExpect(jsonPath(toType(toRootContent(), 0), is("Family")))
      .andExpect(jsonPath(toType(toRootContent(), 1), is("Person")))
    ;
  }

  @DisplayName("search by linked data authority (nothing is found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, label ==/string \"Authority1 Label\"",
    "2, label == \"nonexistent label\"",
    "3, label == \"Authority2 Label common\"",
    "4, id == \"XXX\"",
    "5, identifier == \"9999999999999\"",
    "6, identifier any \"9999999999999\"",
    "7, type == \"XXX\"",
    "8, keyword == \"XXX\"",
  })
  void searchByLinkedDataAuthority_parameterized_zeroResults(int index, String query) throws Throwable {
    doSearchLinkedDataAuthority(query, TENANT_ID)
      .andExpect(jsonPath(toTotalRecords(), is(0)));
  }
}
