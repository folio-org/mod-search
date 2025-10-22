package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.authorityBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.domain.dto.AuthorityBrowseResult;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@IntegrationTest
class BrowseAuthorityIT extends BaseIntegrationTest {

  private static final String REFERENCE = "Reference";
  private static final String AUTHORIZED = "Authorized";

  @BeforeAll
  static void prepare() {
    setUpTenant(46, authorities());
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
      .param("query", prepareQuery("(headingRef>={value} or headingRef<{value}) "
        + "and isTitleHeadingRef==false "
        + "and tenantId==" + TENANT_ID + " "
        + "and shared==false "
        + "and headingType==(\"Personal Name\")", "\"Ĵämes Röllins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(2).prev(null).next(null)
      .items(List.of(
        authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0),
        emptyAuthorityBrowseItem("Ĵämes Röllins"),
        authorityBrowseItem("Zappa Frank", 23, "Personal Name", AUTHORIZED, 0)
      )));
  }

  @Test
  void browseByAuthority_browsingAroundWithDiacritics() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("(headingRef>={value} or headingRef<{value})", "\"Ĵämes Röllins test\""))
      .param("limit", "3")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(31).prev("Harry Potter").next("Ĵämes Röllins test")
      .items(List.of(
        authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
        authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null),
        emptyAuthorityBrowseItem("Ĵämes Röllins test")
      )));
  }

  @Test
  void browseByAuthority_browsingAroundWithPrecedingRecordsCount() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"Ĵämes Röllins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(31).prev("Fantasy").next("Periodicals")
      .items(List.of(
        authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
        authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
        authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null).isAnchor(true),
        authorityBrowseItem("Knowledge", 29, "General Subdivision", REFERENCE, null),
        authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE, null),
        authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED, 0),
        authorityBrowseItem("Periodicals", 28, "General Subdivision", AUTHORIZED, 0))));
  }

  @Test
  void browseByAuthority_browsingAroundWithSeveralExactMatches() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"Zappa Frank\""))
      .param("limit", "3");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(31).prev("xsftMediumPerfTerm").next(null)
      .items(List.of(
        authorityBrowseItem("xsftMediumPerfTerm", 35, "Medium of Performance Term", REFERENCE, null),
        authorityBrowseItem("Zappa Frank", 23, "Personal Name", AUTHORIZED, 0).isAnchor(true),
        authorityBrowseItem("Zappa Frank", 24, "Topical", AUTHORIZED, 0).isAnchor(true)
      )));
  }

  @Test
  void browseByAuthority_browsingAroundWithoutHighlightMatch() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"fantasy\""))
      .param("limit", "5")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual).isEqualTo(new AuthorityBrowseResult()
      .totalRecords(31).prev("Disney").next("Ĵämes Röllins")
      .items(List.of(
        authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
        authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
        authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
        authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
        authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null))));
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
        .totalRecords(31).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          emptyAuthorityBrowseItem("Brian K. Vaughan"),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0)))),

      arguments(aroundQuery, "harry", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Eruption of Vesuvius").next("Ĵämes Röllins")
        .items(List.of(
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          emptyAuthorityBrowseItem("harry"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null)))),

      arguments(aroundQuery, "a", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next("Biomedical Symposium")
        .items(List.of(
          emptyAuthorityBrowseItem("a"),
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED, 0),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null)))),

      arguments(aroundQuery, "zz", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Zappa Frank").next(null)
        .items(List.of(
          authorityBrowseItem("Zappa Frank", 24, "Topical", AUTHORIZED, 0),
          authorityBrowseItem("Zappa Frank", 23, "Personal Name", AUTHORIZED, 0),
          emptyAuthorityBrowseItem("zz")))),

      arguments(aroundIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0).isAnchor(true),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0)))),

      arguments(aroundIncludingQuery, "harry", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Eruption of Vesuvius").next("Ĵämes Röllins")
        .items(List.of(
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          emptyAuthorityBrowseItem("harry"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null)))),

      arguments(aroundIncludingQuery, "music", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Ĵämes Röllins").next("Novel")
        .items(List.of(
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null),
          authorityBrowseItem("Knowledge", 29, "General Subdivision", REFERENCE, null),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE, null),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED, 0)))),

      arguments(aroundIncludingQuery, "music", 25, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next("xMediumPerfTerm")
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED, 0),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null),
          authorityBrowseItem("Knowledge", 29, "General Subdivision", REFERENCE, null),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE, null),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED, 0),
          authorityBrowseItem("Periodicals", 28, "General Subdivision", AUTHORIZED, 0),
          authorityBrowseItem("Poetry", 20, "Genre", REFERENCE, null),
          authorityBrowseItem("Revolution", 26, "Named Event", REFERENCE, null),
          authorityBrowseItem("Science", 16, "Topical", AUTHORIZED, 0),
          authorityBrowseItem("War and Peace", 14, "Uniform Title", REFERENCE, null),
          authorityBrowseItem("xChronSubdivision", 40, "Chronological Subdivision", AUTHORIZED, 0),
          authorityBrowseItem("xChronTerm", 31, "Chronological Term", AUTHORIZED, 0),
          authorityBrowseItem("xFormSubdivision", 43, "Form Subdivision", AUTHORIZED, 0),
          authorityBrowseItem("xGeographicSubdivision", 37, "Geographic Subdivision", AUTHORIZED, 0),
          authorityBrowseItem("xMediumPerfTerm", 34, "Medium of Performance Term", AUTHORIZED, 0)
        ))),

      arguments(aroundIncludingQuery, "music", 11, new AuthorityBrowseResult()
        .totalRecords(31).prev("Eruption of Vesuvius").next("Revolution")
        .items(List.of(
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null),
          authorityBrowseItem("Knowledge", 29, "General Subdivision", REFERENCE, null),
          emptyAuthorityBrowseItem("music"),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE, null),
          authorityBrowseItem("Novel", 19, "Genre", AUTHORIZED, 0),
          authorityBrowseItem("Periodicals", 28, "General Subdivision", AUTHORIZED, 0),
          authorityBrowseItem("Poetry", 20, "Genre", REFERENCE, null),
          authorityBrowseItem("Revolution", 26, "Named Event", REFERENCE, null)))),

      arguments(aroundIncludingQuery, "FC", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Eruption of Vesuvius").next("Ĵämes Röllins")
        .items(List.of(
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          emptyAuthorityBrowseItem("FC"),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null)))),

      // browsing forward
      arguments(forwardQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Brian K. Vaughan Title").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null)))),

      arguments(forwardQuery, "biology", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0)))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Fantasy").next("North America")
        .items(List.of(
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null),
          authorityBrowseItem("Harry Potter", 13, "Uniform Title", AUTHORIZED, 0),
          authorityBrowseItem("Ĵämes Röllins", 2, "Personal Name", REFERENCE, null),
          authorityBrowseItem("Knowledge", 29, "General Subdivision", REFERENCE, null),
          authorityBrowseItem("North America", 11, "Geographic Name", REFERENCE, null)))),

      arguments(forwardQuery, "ZZ", 10, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next(null)
        .items(emptyList())),

      arguments(forwardIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Brian K. Vaughan").next("Eruption of Vesuvius")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0)))),

      arguments(forwardIncludingQuery, "biology", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Biomedical Symposium").next("Comic-Con")
        .items(List.of(
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0)))),

      // browsing backward
      arguments(backwardQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next("Blumberg Green Beauty")
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED, 0),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null)))),

      arguments(backwardQuery, "fun", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Brian K. Vaughan Title").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null)))),

      arguments(backwardQuery, "G", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Brian K. Vaughan Title").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null)))),

      arguments(backwardQuery, "A", 10, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next(null)
        .items(emptyList())),

      arguments(backwardIncludingQuery, "Brian K. Vaughan", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev(null).next("Brian K. Vaughan")
        .items(List.of(
          authorityBrowseItem("Asia Pacific", 10, "Geographic Name", AUTHORIZED, 0),
          authorityBrowseItem("Biomedical Symposium", 8, "Conference Name", REFERENCE, null),
          authorityBrowseItem("Blumberg Green Beauty", 5, "Corporate Name", REFERENCE, null),
          authorityBrowseItem("Brian K. Vaughan", 22, "Personal Name", AUTHORIZED, 0)))),

      arguments(backwardIncludingQuery, "fun", 5, new AuthorityBrowseResult()
        .totalRecords(31).prev("Brian K. Vaughan Title").next("Fantasy")
        .items(List.of(
          authorityBrowseItem("Brian K. Vaughan Title", 1, "Personal Name", AUTHORIZED, 0),
          authorityBrowseItem("Comic-Con", 7, "Conference Name", AUTHORIZED, 0),
          authorityBrowseItem("Disney", 4, "Corporate Name", AUTHORIZED, 0),
          authorityBrowseItem("Eruption of Vesuvius", 25, "Named Event", AUTHORIZED, 0),
          authorityBrowseItem("Fantasy", 17, "Topical", REFERENCE, null))))
    );
  }

  private static Authority[] authorities() {
    return new Authority[]
      {
        authority(1).personalNameTitle("Brian K. Vaughan Title"),
        authority(2).sftPersonalNameTitle(List.of("Ĵämes Röllins")),
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
        authority(23).personalName("Zappa Frank"),
        authority(24).topicalTerm("Zappa Frank"),
        authority(25).namedEvent("Eruption of Vesuvius"),
        authority(26).sftNamedEvent(List.of("Revolution")),
        authority(27).saftNamedEvent(List.of("Stock Market Crash")),
        authority(28).generalSubdivision("Periodicals"),
        authority(29).sftGeneralSubdivision(List.of("Knowledge")),
        authority(30).saftGeneralSubdivision(List.of("Shrines")),
        authority(31).chronTerm("xChronTerm"),
        authority(32).sftChronTerm(List.of("xsftChronTerm")),
        authority(33).saftChronTerm(List.of("xsaftChronTerm")),
        authority(34).mediumPerfTerm("xMediumPerfTerm"),
        authority(35).sftMediumPerfTerm(List.of("xsftMediumPerfTerm")),
        authority(36).saftMediumPerfTerm(List.of("xsaftMediumPerfTerm")),
        authority(37).geographicSubdivision("xGeographicSubdivision"),
        authority(38).sftGeographicSubdivision(List.of("xsftGeographicSubdivision")),
        authority(39).saftGeographicSubdivision(List.of("xsaftGeographicSubdivision")),
        authority(40).chronSubdivision("xChronSubdivision"),
        authority(41).sftChronSubdivision(List.of("xsftChronSubdivision")),
        authority(42).saftChronSubdivision(List.of("xsaftChronSubdivision")),
        authority(43).formSubdivision("xFormSubdivision"),
        authority(44).sftFormSubdivision(List.of("xsftFormSubdivision")),
        authority(45).saftFormSubdivision(List.of("xsaftFormSubdivision"))
        };
  }

  private static Authority authority(int index) {
    return new Authority().id(getId(index)).tenantId(TENANT_ID)
      .subjectHeadings(String.format("Authority #%02d", index))
      .source("MARC")
      .sourceFileId("5de462a2-7a90-4467-b77f-b2057d6d69b6").naturalId("nbc123435");
  }

  private static Authority authority(int index, String headingRef, String headingType, String authRefType,
                                     Integer numberOfTitles) {
    return new Authority().id(getId(index)).headingRef(headingRef)
      .tenantId(TENANT_ID).shared(false)
      .authRefType(authRefType).headingType(headingType)
      .sourceFileId("5de462a2-7a90-4467-b77f-b2057d6d69b6").naturalId("nbc123435").numberOfTitles(numberOfTitles);
  }

  private static AuthorityBrowseItem authorityBrowseItem(String heading, int index, String type, String headingType,
                                                         Integer numberOfTitles) {
    return new AuthorityBrowseItem().headingRef(heading)
      .authority(authority(index, heading, type, headingType, numberOfTitles));
  }

  private static AuthorityBrowseItem emptyAuthorityBrowseItem(String heading) {
    return new AuthorityBrowseItem().headingRef(heading).isAnchor(true);
  }

  private static String getId(int index) {
    return String.format("%02d", index) + "320dbd-6de3-453a-b05d-452fc1eb1e0d";
  }
}
