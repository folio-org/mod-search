package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.authorityBrowsePath;
import static org.folio.search.utils.TestUtils.parseResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.domain.dto.AuthorityBrowseResult;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class AuthorityBrowseIT extends BaseIntegrationTest {

  private static final String REFERENCE = "Reference";
  private static final String AUTHORIZED = "Authorized";

  @BeforeAll
  static void prepare() {
    setUpTenant(23, authorities());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @MethodSource("authorityBrowsingDataProvider")
  @DisplayName("browseByAuthority_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByAuthority_parameterized(String query, String anchor, Integer limit, AuthorityBrowseResult expected) {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByAuthority_browsingAroundWithAdditionalFilters() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("(headingRef>={value} or headingRef<{value}) " +
        "and isTitleHeadingRef==false " +
        "and headingType==(\"Personal Name\")", "\"James Rollins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(1).prev(null).next(null)
      .items(List.of(
        authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
        emptyAuthorityBrowseItem("James Rollins"))));
  }

  @Test
  void browseByAuthority_browsingAroundWithPrecedingRecordsCount() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"James Rollins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(15).prev("Fantasy").next("Science")
      .items(List.of(
        authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
        authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE).isAnchor(true),
        authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE),
        authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED),
        authorityBrowseItem("Poetry", 20, "Genre", REFERENCE),
        authorityBrowseItem("Science", 16, "Topical", AUTHORIZED))));
  }

  @Test
  void browseByAuthority_browsingAroundWithoutHighlightMatch() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"fantasy\""))
      .param("limit", "5")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(15).prev("Comic-Con").next("James Rollins")
      .items(List.of(
        authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
        authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE))));
  }

  private static Stream<Arguments> authorityBrowsingDataProvider() {
    var aroundQuery = "headingRef > {value} or headingRef < {value}";
    var aroundIncludingQuery = "headingRef >= {value} or headingRef < {value}";
    var forwardQuery = "headingRef > {value}";
    var forwardIncludingQuery = "headingRef >= {value}";
    var backwardQuery = "headingRef < {value}";
    var backwardIncludingQuery = "headingRef <= {value}";

    return Stream.of(
      arguments(aroundQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          emptyAuthorityBrowseItem("Brian K. Vaughan"),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED)))),

      arguments(aroundQuery, "harry", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Disney").next("James Rollins")
        .items(List.of(
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          emptyAuthorityBrowseItem("harry"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE)))),

      arguments(aroundQuery, "a", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next("Biomedical Symposium")
        .items(List.of(
          emptyAuthorityBrowseItem("a"),
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE)))),

      arguments(aroundQuery, "z", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Science").next(null)
        .items(List.of(
          authorityBrowseItem("Science", 16, "Topical", AUTHORIZED),
          authorityBrowseItem("War and Peace", 14, "Uniform Title", REFERENCE),
          emptyAuthorityBrowseItem("z")))),

      arguments(aroundIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED).isAnchor(true),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED)))),

      arguments(aroundIncludingQuery, "harry", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Disney").next("James Rollins")
        .items(List.of(
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          emptyAuthorityBrowseItem("harry"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE)))),

      arguments(aroundIncludingQuery, "music", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Harry Potter").next("Novel")
        .items(List.of(
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED)))),

      arguments(aroundIncludingQuery, "music", 25, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next(null)
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED),
          authorityBrowseItem("Poetry", 20, "Genre", REFERENCE),
          authorityBrowseItem("Science", 16, "Topical", AUTHORIZED),
          authorityBrowseItem("War and Peace", 14, "Uniform Title", REFERENCE)))),

      arguments(aroundIncludingQuery, "music", 11, new AuthorityBrowseResult()
        .totalRecords(15).prev("Comic-Con").next(null)
        .items(List.of(
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED),
          authorityBrowseItem("Poetry", 20, "Genre", REFERENCE),
          authorityBrowseItem("Science", 16, "Topical", AUTHORIZED),
          authorityBrowseItem("War and Peace", 14, "Uniform Title", REFERENCE)))),

      arguments(aroundIncludingQuery, "FC", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Disney").next("James Rollins")
        .items(List.of(
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          emptyAuthorityBrowseItem("FC"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE)))),

      // browsing forward
      arguments(forwardQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Brian K. Vaughan Title").next("Harry Potter")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED)))),

      arguments(forwardQuery, "biology", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED)))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Fantasy").next("Novel")
        .items(List.of(
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED),
          authorityBrowseItem("James Rollins", 2, "Personal Name", REFERENCE),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED)))),

      arguments(forwardQuery, "Z", 10, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next(null)
        .items(emptyList())),

      arguments(forwardIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Brian K. Vaughan").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE)))),

      arguments(forwardIncludingQuery, "biology", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED)))),

      // browsing backward
      arguments(backwardQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next("Blumberg Green Beauty")
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE)))),

      arguments(backwardQuery, "fun", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Brian K. Vaughan").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE)))),

      arguments(backwardQuery, "G", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Brian K. Vaughan").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE)))),

      arguments(backwardQuery, "A", 10, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next(null)
        .items(emptyList())),

      arguments(backwardIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev(null).next("Brian K. Vaughan")
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED)))),

      arguments(backwardIncludingQuery, "fun", 5, new AuthorityBrowseResult()
        .totalRecords(15).prev("Brian K. Vaughan").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE))))
    );
  }

  private static Authority[] authorities() {
    return new Authority[]
      {
        authority(1).personalNameTitle("Brian K. Vaughan Title"),
        authority(2).sftPersonalNameTitle(List.of("James Rollins")),
        authority(3).saftPersonalNameTitle(List.of("Brad Thor")),
        authority(4).corporateNameTitle("Disney"),
        authority(5).sftCorporateNameTitle(List.of("Blumberg Green Beauty")),
        authority(6).saftCorporateNameTitle(List.of("Amazon Kindle")),
        authority(7).meetingNameTitle("Comic-Con"),
        authority(8).sftMeetingNameTitle(List.of("Biomedical Symposium")),
        authority(9).saftMeetingNameTitle(List.of("World Conference On Corporate Accounting (WCCA)")),
        authority(10).geographicName("Asia Pacific"),
        authority(11).sftGeographicName(List.of("North America")),
        authority(12).saftGeographicName(List.of("Canada")),
        authority(13).uniformTitle("Harry Potter"),
        authority(14).sftUniformTitle(List.of("War and Peace")),
        authority(15).saftUniformTitle(List.of("The Lord of the Rings")),
        authority(16).topicalTerm("Science"),
        authority(17).sftTopicalTerm(List.of("Fantasy")),
        authority(18).saftTopicalTerm(List.of("History")),
        authority(19).genreTerm("Novel"),
        authority(20).sftGenreTerm(List.of("Poetry")),
        authority(21).saftGenreTerm(List.of("Prose", "Romance")),
        authority(22).personalName("Brian K. Vaughan"),
      };
  }

  private static Authority authority(int index) {
    return new Authority().id("id-" + index).subjectHeadings(String.format("Authority #%02d", index));
  }

  private static Authority authority(int index, String headingRef, String headingType, String authRefType) {
    return new Authority().id("id-" + index).headingRef(headingRef).authRefType(authRefType).headingType(headingType);
  }

  private static AuthorityBrowseItem authorityBrowseItem(String heading, int index, String type, String headingType) {
    return new AuthorityBrowseItem().headingRef(heading).authority(authority(index, heading, type, headingType));
  }

  private static AuthorityBrowseItem emptyAuthorityBrowseItem(String heading) {
    return new AuthorityBrowseItem().headingRef(heading).isAnchor(true);
  }
}
