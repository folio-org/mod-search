package org.folio.search.controller;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.folio.search.sample.SampleLinkedData.getWork2SampleAsMap;
import static org.folio.search.sample.SampleLinkedData.getWorkSampleAsMap;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchLinkedDataWorkIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(LinkedDataWork.class, 2, getWorkSampleAsMap(), getWork2SampleAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @DisplayName("search by linked data works (all 2 works are found)")
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
  })
  void searchByLinkedDataWork_parameterized_allResults(int index, String query) throws Throwable {
    var asc = query.contains("titleAbc def") || query.contains("sortBy") && !query.contains("descending");
    doSearchByLinkedDataWork(query)
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.content[0].titles[0].value", is(asc ? "titleAbc def" : "titleAbc xyz")))
      .andExpect(jsonPath("$.content[1].titles[0].value", is(asc ? "titleAbc xyz" : "titleAbc def")));
  }

  @DisplayName("search by linked data work (single work is found)")
  @ParameterizedTest(name = "[{0}] {1}")
  @CsvSource({
    "1, title any \"def\"",
    "2, title = \"titleAbc def\"",
    "3, title == \"titleAbc def\"",
    "4, title ==/string \"titleAbc def\"",
    "5, isbn = \"*\"",
    "6, isbn = \"1234567890123\"",
    "7, isbn = \"1234*\"",
    "8, isbn == \"1234567890123\"",
    "9, isbn ==/string \"1234567890123\"",
    "10, isbn any \"1234567890123\"",
    "11, isbn any \"1234567890123 XXX\"",
    "12, isbn all \"1234567890123\"",
    "13, lccn = \"*\"",
    "14, lccn = \"2023202345\"",
    "15, lccn = \"2023*\"",
    "16, lccn == \"2023202345\"",
    "17, lccn ==/string \"2023202345\"",
    "18, lccn any \"2023202345\"",
    "19, lccn any \"2023202345 XXX\"",
    "20, lccn all \"2023202345\"",
    "21, contributor = Family",
    "22, contributor == Meeting",
    "23, contributor ==/string Organization",
    "24, contributor any Person",
    "25, contributor all Family"
  })
  void searchByLinkedDataWork_parameterized_singleResult(int index, String query) throws Throwable {
    doSearchByLinkedDataWork(query)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath(toId(toWork()), is("123456123456")))
      .andExpect(jsonPath(toTitleValue(toWork(), 0), is("titleAbc def")))
      .andExpect(jsonPath(toTitleType(toWork(), 0), is("Main")))
      .andExpect(jsonPath(toTitleValue(toWork(), 1), is("sub")))
      .andExpect(jsonPath(toTitleType(toWork(), 1), is("Sub")))
      .andExpect(jsonPath(toContributorName(toWork(), 0), is("Family")))
      .andExpect(jsonPath(toContributorType(toWork(), 0), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toWork(), 0), is(true)))
      .andExpect(jsonPath(toContributorName(toWork(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorType(toWork(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorIsCreator(toWork(), 1), is(false)))
      .andExpect(jsonPath(toContributorName(toWork(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorType(toWork(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorIsCreator(toWork(), 2), is(true)))
      .andExpect(jsonPath(toContributorName(toWork(), 3), is("Person")))
      .andExpect(jsonPath(toContributorType(toWork(), 3), is("Person")))
      .andExpect(jsonPath(toContributorIsCreator(toWork(), 3), is(false)))
      .andExpect(jsonPath(toContributorName(toWork(), 4), is("common")))
      .andExpect(jsonPath(toContributorType(toWork(), 4), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toWork(), 4), is(true)))
      .andExpect(jsonPath(toLanguage(0), is("eng")))
      .andExpect(jsonPath(toLanguage(1), is("rus")))
      .andExpect(jsonPath(toClassificationNumber(0), is("1234")))
      .andExpect(jsonPath(toClassificationSource(0), is("ddc")))
      .andExpect(jsonPath(toClassificationNumber(1), is("5678")))
      .andExpect(jsonPath(toClassificationSource(1), is("other")))
      .andExpect(jsonPath(toSubject(0), is("Subject 1")))
      .andExpect(jsonPath(toSubject(1), is("Subject 2")))
      .andExpect(jsonPath(toId(toInstance()), is("instance1")))
      .andExpect(jsonPath(toTitleValue(toInstance(), 0), is("Instance1_Title")))
      .andExpect(jsonPath(toTitleType(toInstance(), 0), is("Main")))
      .andExpect(jsonPath(toTitleValue(toInstance(), 1), is("Instance1_Subtitle")))
      .andExpect(jsonPath(toTitleType(toInstance(), 1), is("Sub")))
      .andExpect(jsonPath(toIdValue(0), is("1234567890123")))
      .andExpect(jsonPath(toIdType(0), is("ISBN")))
      .andExpect(jsonPath(toIdValue(1), is("  2023-202345/AC/r932")))
      .andExpect(jsonPath(toIdType(1), is("LCCN")))
      .andExpect(jsonPath(toContributorName(toInstance(), 0), is("Instance1_Family")))
      .andExpect(jsonPath(toContributorType(toInstance(), 0), is("Family")))
      .andExpect(jsonPath(toContributorIsCreator(toInstance(), 0), is(true)))
      .andExpect(jsonPath(toContributorName(toInstance(), 1), is("Instance1_Meeting")))
      .andExpect(jsonPath(toContributorType(toInstance(), 1), is("Meeting")))
      .andExpect(jsonPath(toContributorIsCreator(toInstance(), 1), is(false)))
      .andExpect(jsonPath(toContributorName(toInstance(), 2), is("Instance1_Organization")))
      .andExpect(jsonPath(toContributorType(toInstance(), 2), is("Organization")))
      .andExpect(jsonPath(toContributorIsCreator(toInstance(), 2), is(true)))
      .andExpect(jsonPath(toContributorName(toInstance(), 3), is("Instance1_Person")))
      .andExpect(jsonPath(toContributorType(toInstance(), 3), is("Person")))
      .andExpect(jsonPath(toContributorIsCreator(toInstance(), 3), is(false)))
      .andExpect(jsonPath(toPublicationName(0), is("publisher")))
      .andExpect(jsonPath(toPublicationDate(0), is("2023")))
      .andExpect(jsonPath(toPublicationName(1), is("publisher2")))
      .andExpect(jsonPath(toPublicationDate(1), is("2024")))
      .andExpect(jsonPath(toEditionStatement(0), is("1st edition")))
      .andExpect(jsonPath(toEditionStatement(1), is("2nd edition")))
      ;
  }

  @DisplayName("search by liked data work (nothing is found)")
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
  })
  void searchByLinkedDataWork_parameterized_zeroResults(int index, String query) throws Throwable {
    doSearchByLinkedDataWork(query)
      .andExpect(jsonPath("$.totalRecords", is(0)));
  }

  private String path(String path) {
    return format("['%s']", path);
  }

  private String arrayPath(String path) {
    return arrayPath(path, 0);
  }

  private String arrayPath(String path, int number) {
    return format("['%s'][%s]", path, number);
  }

  private String toWork() {
    return join(".", "$", arrayPath("content"));
  }

  private String toId(String base) {
    return join(".", base, path("id"));
  }

  private String toTitle(String base, int number) {
    return join(".", base, arrayPath("titles", number));
  }

  private String toTitleValue(String base, int number) {
    return join(".", toTitle(base, number), path("value"));
  }

  private String toTitleType(String base, int number) {
    return join(".", toTitle(base, number), path("type"));
  }

  private String toContributor(String base, int number) {
    return join(".", base, arrayPath("contributors", number));
  }

  private String toContributorName(String base, int number) {
    return join(".", toContributor(base, number), path("name"));
  }

  private String toContributorType(String base, int number) {
    return join(".", toContributor(base, number), path("type"));
  }

  private String toContributorIsCreator(String base, int number) {
    return join(".", toContributor(base, number), path("isCreator"));
  }

  private String toLanguage(int number) {
    return join(".", toWork(), arrayPath("languages", number), path("value"));
  }

  private String toClassification(int number) {
    return join(".", toWork(), arrayPath("classifications", number));
  }

  private String toClassificationNumber(int number) {
    return join(".", toClassification(number), path("number"));
  }

  private String toClassificationSource(int number) {
    return join(".", toClassification(number), path("source"));
  }

  private String toSubject(int number) {
    return join(".", toWork(), arrayPath("subjects", number), path("value"));
  }

  private String toInstance() {
    return join(".", toWork(), arrayPath("instances"));
  }

  private String toIdValue(int number) {
    return join(".", toInstance(), arrayPath("identifiers", number), path("value"));
  }

  private String toIdType(int number) {
    return join(".", toInstance(), arrayPath("identifiers", number), path("type"));
  }

  private String toPublicationName(int number) {
    return join(".", toInstance(), arrayPath("publications", number), path("name"));
  }

  private String toPublicationDate(int number) {
    return join(".", toInstance(), arrayPath("publications", number), path("date"));
  }

  private String toEditionStatement(int number) {
    return join(".", toInstance(), arrayPath("editionStatements", number), path("value"));
  }

}
