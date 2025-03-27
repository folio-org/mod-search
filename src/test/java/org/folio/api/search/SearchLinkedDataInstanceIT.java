package org.folio.api.search;

import static org.folio.support.sample.SampleLinkedData.getInstance2SampleAsMap;
import static org.folio.support.sample.SampleLinkedData.getInstanceSampleAsMap;
import static org.folio.support.utils.LinkedDataTestUtils.toClassificationAdditionalNumber;
import static org.folio.support.utils.LinkedDataTestUtils.toClassificationNumber;
import static org.folio.support.utils.LinkedDataTestUtils.toClassificationType;
import static org.folio.support.utils.LinkedDataTestUtils.toContributorIsCreator;
import static org.folio.support.utils.LinkedDataTestUtils.toContributorName;
import static org.folio.support.utils.LinkedDataTestUtils.toContributorType;
import static org.folio.support.utils.LinkedDataTestUtils.toEditionStatement;
import static org.folio.support.utils.LinkedDataTestUtils.toFormat;
import static org.folio.support.utils.LinkedDataTestUtils.toHubAap;
import static org.folio.support.utils.LinkedDataTestUtils.toId;
import static org.folio.support.utils.LinkedDataTestUtils.toIdType;
import static org.folio.support.utils.LinkedDataTestUtils.toIdValue;
import static org.folio.support.utils.LinkedDataTestUtils.toLanguage;
import static org.folio.support.utils.LinkedDataTestUtils.toNoteType;
import static org.folio.support.utils.LinkedDataTestUtils.toNoteValue;
import static org.folio.support.utils.LinkedDataTestUtils.toParentWork;
import static org.folio.support.utils.LinkedDataTestUtils.toPublicationDate;
import static org.folio.support.utils.LinkedDataTestUtils.toPublicationName;
import static org.folio.support.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.support.utils.LinkedDataTestUtils.toSubject;
import static org.folio.support.utils.LinkedDataTestUtils.toSuppressFromDiscovery;
import static org.folio.support.utils.LinkedDataTestUtils.toSuppressStaff;
import static org.folio.support.utils.LinkedDataTestUtils.toTitleType;
import static org.folio.support.utils.LinkedDataTestUtils.toTitleValue;
import static org.folio.support.utils.LinkedDataTestUtils.toTotalRecords;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchLinkedDataInstanceIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(LinkedDataInstance.class, getInstanceSampleAsMap(), getInstance2SampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @DisplayName("search by linked data instances (all 2 instances are found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, cql.allRecords = 1",
    "2, title all \"titleAbc\"",
    "3, title any \"titleAbc\"",
    "4, title any \"titleAbc def\"",
    "5, title any \"titleAbc XXX\"",
    "6, title = \"titleAbc\"",
    "7, title <> \"titleXXX\"",
    "8, title = \"title*\"",
    "9, title = \"*\"",
    "10, isbn <> \"1234\"",
    "11, lccn <> \"2023\"",
    "12, contributor all \"common\"",
    "13, contributor any \"common\"",
    "14, contributor = \"common\"",
    "15, contributor <> \"commonXXX\"",
    "16, contributor = \"com*\"",
    "17, contributor = \"*\"",
    "18, (title all \"titleAbc\") sortBy title",
    "19, title all \"titleAbc\" sortBy title",
    "20, title all \"titleAbc\" sortBy title/sort.ascending",
    "21, title all \"titleAbc\" sortBy title/sort.descending",
    "22, classificationType <> \"aaa\"",
    "23, classificationNumber <> \"000\"",
    "24, classificationAdditionalNumber <> \"000\"",
  })
  void searchByLinkedDataInstance_parameterized_allResults(int index, String query) throws Throwable {
    var asc = !query.contains("descending");
    doSearchByLinkedDataInstance(query)
      .andExpect(jsonPath(toTotalRecords(), is(2)))
      .andExpect(jsonPath(toTitleValue(toRootContent(0), 0), is(asc ? "titleAbc def" : "titleAbc xyz")))
      .andExpect(jsonPath(toTitleValue(toRootContent(1), 0), is(asc ? "titleAbc xyz" : "titleAbc def")));
  }

  @DisplayName("search by linked data instance (single instance is found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, keyword = Instance1_Family",
    "2, keyword = titleAbc def",
    "3, keyword = 1234567890123",
    "4, keyword = hubAAP1",
    "5, keyword = first instance note",
    "6, title any \"def\"",
    "7, title = \"titleAbc def\"",
    "8, title == \"titleAbc def\"",
    "9, title ==/string \"titleAbc def\"",
    "10, isbn = \"*\"",
    "11, isbn = \"1234567890123\"",
    "12, isbn = \"1234*\"",
    "13, isbn == \"1234567890123\"",
    "14, isbn ==/string \"1234567890123\"",
    "15, isbn any \"1234567890123\"",
    "16, isbn any \"1234567890123 XXX\"",
    "17, isbn all \"1234567890123\"",
    "18, lccn = \"*\"",
    "19, lccn = \"2023202345\"",
    "20, lccn = \"2023*\"",
    "21, lccn == \"2023202345\"",
    "22, lccn ==/string \"2023202345\"",
    "23, lccn any \"2023202345\"",
    "24, lccn any \"2023202345 XXX\"",
    "25, lccn all \"2023202345\"",
    "26, lccn all \"a b24123456 \"",
    "27, contributor = Family",
    "28, contributor == Meeting",
    "29, contributor ==/string Organization",
    "30, contributor any Person",
    "31, contributor all Family",
    "32, hub = *",
    "33, hub = hubA*",
    "34, hub = hubAAP1",
    "35, hub == hubAAP2",
    "36, hub ==/string hubAAP1",
    "37, hub any \"hubAAP1 hubAAP2 XXX\"",
    "38, hub all hubAAP1",
    "39, note = *",
    "40, note = first*",
    "41, note = first instance note",
    "42, note == first instance note",
    "43, note ==/string first instance note",
    "44, note any \"first instance note XXX\"",
    "45, note all \"first instance note\"",
    "46, lang = *",
    "47, lang = ru*",
    "48, lang = eng",
    "49, lang == rus",
    "50, lang ==/string eng",
    "51, lang == (\"rus\" or \"eng\" or \"XXX\")",
    "52, lang all rus",
    "53, format = *",
    "54, format = Mono*",
    "55, format = monograph",
    "56, format == monograph",
    "57, format ==/string Monograph",
    "58, format any \"Monograph XXX\"",
    "59, format all Monograph",
    "60, suppressFromDiscovery = false",
    "61, suppressFromDiscovery == false",
    "62, staffSuppress = true",
    "63, staffSuppress == true",
    "64, publicationDate = 2023",
    "65, publicationDate == 2024",
    "66, publicationDate ==/string 2023",
    "67, publicationDate == (\"2023\" or \"2024\" or \"2020\")",
    "68, publicationDate all 2024",
    "69, classificationType == \"ddc\"",
    "70, classificationNumber == \"123\"",
    "71, classificationAdditionalNumber == \"456\"",
    "72, classificationNumber == \"1 2  3\"",
    "73, classificationAdditionalNumber == \"45  6\"",
  })
  void searchByLinkedDataInstance_parameterized_singleResult(int index, String query) throws Throwable {
    doSearchByLinkedDataInstance(query)
      .andExpect(jsonPath(toTotalRecords(), is(1)))
      .andExpect(jsonPath(toId(toRootContent()), is("instance1")))
      .andExpect(jsonPath(toClassificationType(toParentWork(), 0), is("ddc")))
      .andExpect(jsonPath(toClassificationNumber(toParentWork(), 0), is("123")))
      .andExpect(jsonPath(toClassificationAdditionalNumber(toParentWork(), 0), is("456")))
      .andExpect(jsonPath(toClassificationType(toParentWork(), 1), is("lc")))
      .andExpect(jsonPath(toClassificationNumber(toParentWork(), 1), is("789")))
      .andExpect(jsonPath(toClassificationAdditionalNumber(toParentWork(), 1), is("012")))
      .andExpect(jsonPath(toContributorName(toRootContent(), 0), is("Instance1_Family")))
      .andExpect(jsonPath(toContributorType(toRootContent(), 0), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toRootContent(), 0), is(true)))
      .andExpect(jsonPath(toContributorName(toRootContent(), 1), is("Instance1_Meeting")))
      .andExpect(jsonPath(toContributorType(toRootContent(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorIsCreator(toRootContent(), 1), is(false)))
      .andExpect(jsonPath(toContributorName(toRootContent(), 2), is("Instance1_Organization")))
      .andExpect(jsonPath(toContributorType(toRootContent(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorIsCreator(toRootContent(), 2), is(true)))
      .andExpect(jsonPath(toContributorName(toRootContent(), 3), is("Instance1_Person")))
      .andExpect(jsonPath(toContributorType(toRootContent(), 3), is("Person")))
      .andExpect(jsonPath(toContributorIsCreator(toRootContent(), 3), is(false)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 4), is("common")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 4), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 4), is(true)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 0), is("Family")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 0), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 0), is(true)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 1), is(false)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 2), is(true)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 3), is("Person")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 3), is("Person")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 3), is(false)))
      .andExpect(jsonPath(toContributorName(toParentWork(), 4), is("common")))
      .andExpect(jsonPath(toContributorType(toParentWork(), 4), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toParentWork(), 4), is(true)))
      .andExpect(jsonPath(toEditionStatement(toRootContent(), 0), is("1st edition")))
      .andExpect(jsonPath(toEditionStatement(toRootContent(), 1), is("2nd edition")))
      .andExpect(jsonPath(toFormat(toRootContent()), is("Monograph")))
      .andExpect(jsonPath(toHubAap(toParentWork(), 0), is("hubAAP1")))
      .andExpect(jsonPath(toHubAap(toParentWork(), 1), is("hubAAP2")))
      .andExpect(jsonPath(toIdValue(toRootContent(), 0), is("1234567890123")))
      .andExpect(jsonPath(toIdType(toRootContent(), 0), is("ISBN")))
      .andExpect(jsonPath(toIdValue(toRootContent(), 1), is("  2023202345")))
      .andExpect(jsonPath(toIdType(toRootContent(), 1), is("LCCN")))
      .andExpect(jsonPath(toLanguage(toParentWork(), 0), is("eng")))
      .andExpect(jsonPath(toLanguage(toParentWork(), 1), is("rus")))
      .andExpect(jsonPath(toNoteValue(toRootContent(), 0), is("first instance note")))
      .andExpect(jsonPath(toNoteType(toRootContent(), 0), is("firstInstanceNoteType")))
      .andExpect(jsonPath(toNoteValue(toRootContent(), 1), is("second instance note")))
      .andExpect(jsonPath(toNoteType(toRootContent(), 1), is("secondInstanceNoteType")))
      .andExpect(jsonPath(toNoteValue(toParentWork(), 0), is("first work note")))
      .andExpect(jsonPath(toNoteType(toParentWork(), 0), is("firstWorkNoteType")))
      .andExpect(jsonPath(toNoteValue(toParentWork(), 1), is("second work note")))
      .andExpect(jsonPath(toNoteType(toParentWork(), 1), is("secondWorkNoteType")))
      .andExpect(jsonPath(toPublicationName(toRootContent(), 0), is("publisher")))
      .andExpect(jsonPath(toPublicationDate(toRootContent(), 0), is("2023")))
      .andExpect(jsonPath(toPublicationName(toRootContent(), 1), is("publisher2")))
      .andExpect(jsonPath(toPublicationDate(toRootContent(), 1), is("2024")))
      .andExpect(jsonPath(toSubject(toParentWork(), 0), is("Subject 1")))
      .andExpect(jsonPath(toSubject(toParentWork(), 1), is("Subject 2")))
      .andExpect(jsonPath(toSuppressFromDiscovery(toRootContent()), is(false)))
      .andExpect(jsonPath(toSuppressStaff(toRootContent()), is(true)))
      .andExpect(jsonPath(toTitleValue(toRootContent(), 0), is("titleAbc def")))
      .andExpect(jsonPath(toTitleType(toRootContent(), 0), is("Main")))
      .andExpect(jsonPath(toTitleValue(toRootContent(), 1), is("sub")))
      .andExpect(jsonPath(toTitleType(toRootContent(), 1), is("Sub")))
      .andExpect(jsonPath(toTitleValue(toParentWork(), 0), is("WorkTitle")))
      .andExpect(jsonPath(toTitleType(toParentWork(), 0), is("Main")))
      .andExpect(jsonPath(toTitleValue(toParentWork(), 1), is("sub")))
      .andExpect(jsonPath(toTitleType(toParentWork(), 1), is("Sub")))
    ;
  }

  @DisplayName("search by liked data instance (nothing is found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, title ==/string \"titleAbc\"",
    "2, title ==/string \"def\"",
    "3, title ==/string \"xyz\"",
    "4, title == \"def titleAbc\"",
    "5, title == \"titleAbcdef def\"",
    "6, title all \"titleAbcdef\"",
    "7, title any \"titleAbcdef\"",
    "8, title = \"titleAbcdef\"",
    "9, title <> \"titleAbc\"",
    "10, isbn ==/string \"1234\"",
    "11, isbn == \"1234\"",
    "12, isbn any \"1234\"",
    "13, isbn any \"12345678901231\"",
    "14, isbn all \"1234\"",
    "15, isbn = \"1234\"",
    "16, lccn ==/string \"2023\"",
    "17, lccn == \"2023\"",
    "18, lccn any \"2023\"",
    "19, lccn any \"202320231\"",
    "20, lccn all \"2023\"",
    "21, lccn = \"2023\"",
    "22, contributor ==/string \"Famil\"",
    "23, contributor ==/string \"Meeting1\"",
    "24, contributor ==/string \"rganizatio\"",
    "25, contributor == \"Person common\"",
    "26, contributor == \"common Person\"",
    "27, contributor all \"comm\"",
    "28, contributor any \"comm\"",
    "29, contributor = \"comm\"",
    "30, contributor <> \"common\"",
    "31, classificationType == \"aaa\"",
    "31, classificationNumber == \"000\"",
    "33, classificationAdditionalNumber == \"000\"",
  })
  void searchByLinkedDataInstance_parameterized_zeroResults(int index, String query) throws Throwable {
    doSearchByLinkedDataInstance(query)
      .andExpect(jsonPath(toTotalRecords(), is(0)));
  }

}
