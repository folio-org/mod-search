package org.folio.search.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.support.base.ApiEndpoints.authorityBrowsePath;
import static org.folio.search.utils.TestUtils.authorityBrowseResult;
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
    setUpTenant(29, authorities());
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
  void browseByAuthority_browsingAroundWithPrecedingRecordsCount() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"James Rollins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);
    assertThat(actual).isEqualTo(authorityBrowseResult(19, List.of(
      authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
      authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED),
      authorityBrowseItem("James Rollins", 5, "Personal Name", REFERENCE).isAnchor(true),
      authorityBrowseItem("North America", 17, "Geographic Name", REFERENCE),
      authorityBrowseItem("Novel", 25, "Genre", AUTHORIZED),
      authorityBrowseItem("Poetry", 26, "Genre", REFERENCE),
      authorityBrowseItem("Pratham Books", 7, "Corporate Name", AUTHORIZED)
    )));
  }

  @Test
  void browseByAuthority_browsingAroundWithoutHighlightMatch() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("headingRef < {value} or headingRef >= {value}", "\"fantasy\""))
      .param("limit", "5")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual).isEqualTo(authorityBrowseResult(19, List.of(
      authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
      authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
      authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
      authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
      authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED)
    )));
  }

  private static Stream<Arguments> authorityBrowsingDataProvider() {
    var aroundQuery = "headingRef > {value} or headingRef < {value}";
    var aroundIncludingQuery = "headingRef >= {value} or headingRef < {value}";
    var forwardQuery = "headingRef > {value}";
    var forwardIncludingQuery = "headingRef >= {value}";
    var backwardQuery = "headingRef < {value}";
    var backwardIncludingQuery = "headingRef <= {value}";

    return Stream.of(
      arguments(aroundQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        emptyAuthorityBrowseItem("Brian K. Vaughan"),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED)
      ))),

      arguments(aroundQuery, "harry", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        emptyAuthorityBrowseItem("harry"),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 5, "Personal Name", REFERENCE)
      ))),

      arguments(aroundIncludingQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED).isAnchor(true),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED)
      ))),

      arguments(aroundIncludingQuery, "harry", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        emptyAuthorityBrowseItem("harry"),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 5, "Personal Name", REFERENCE)
      ))),

      arguments(aroundIncludingQuery, "music", 25, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Asia Pacific", 16, "Geographic Name", AUTHORIZED),
        authorityBrowseItem("Asire, Nancy", 1, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 5, "Personal Name", REFERENCE),
        emptyAuthorityBrowseItem("music"),
        authorityBrowseItem("North America", 17, "Geographic Name", REFERENCE),
        authorityBrowseItem("Novel", 25, "Genre", AUTHORIZED),
        authorityBrowseItem("Poetry", 26, "Genre", REFERENCE),
        authorityBrowseItem("Pratham Books", 7, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Science", 22, "Topical", AUTHORIZED),
        authorityBrowseItem("Stairway Press", 8, "Corporate Name", REFERENCE),
        authorityBrowseItem("Stephen King", 0, "Personal Name", AUTHORIZED),
        authorityBrowseItem("War and Peace", 20, "Uniform Title", REFERENCE)
      ))),

      arguments(aroundIncludingQuery, "FC", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        emptyAuthorityBrowseItem("FC"),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED)
      ))),

      // browsing forward
      arguments(forwardQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED)
      ))),

      arguments(forwardQuery, "biology", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED)
      ))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE),
        authorityBrowseItem("Harry Potter", 19, "Uniform Title", AUTHORIZED),
        authorityBrowseItem("James Rollins", 5, "Personal Name", REFERENCE),
        authorityBrowseItem("North America", 17, "Geographic Name", REFERENCE)
      ))),

      arguments(forwardQuery, "Z", 10, authorityBrowseResult(19, emptyList())),

      arguments(forwardIncludingQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE),
        authorityBrowseItem("George R.R Martin", 2, "Personal Name", REFERENCE)
      ))),

      arguments(forwardIncludingQuery, "biology", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED)
      ))),

      // browsing backward
      arguments(backwardQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Asia Pacific", 16, "Geographic Name", AUTHORIZED),
        authorityBrowseItem("Asire, Nancy", 1, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE)
      ))),

      arguments(backwardQuery, "fun", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE)
      ))),

      arguments(backwardQuery, "G", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE)
      ))),

      arguments(backwardQuery, "A", 10, authorityBrowseResult(19, emptyList())),

      arguments(backwardIncludingQuery, "Brian K. Vaughan", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Asia Pacific", 16, "Geographic Name", AUTHORIZED),
        authorityBrowseItem("Asire, Nancy", 1, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Biomedical Symposium", 14, "Conference Name", REFERENCE),
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED)
      ))),

      arguments(backwardIncludingQuery, "fun", 5, authorityBrowseResult(19, List.of(
        authorityBrowseItem("Blumberg Green Beauty", 11, "Corporate Name", REFERENCE),
        authorityBrowseItem("Brian K. Vaughan", 4, "Personal Name", AUTHORIZED),
        authorityBrowseItem("Comic-Con", 13, "Conference Name", AUTHORIZED),
        authorityBrowseItem("Disney", 10, "Corporate Name", AUTHORIZED),
        authorityBrowseItem("Fantasy", 23, "Topical", REFERENCE)
      )))
    );
  }

  private static Authority[] authorities() {
    return new Authority[]
      {
        authority(0).personalName("Stephen King"),
        authority(1).personalName("Asire, Nancy"),
        authority(2).sftPersonalName(List.of("George R.R Martin")),
        authority(3).saftPersonalName(List.of("Michael Connelly")),
        authority(4).personalNameTitle("Brian K. Vaughan"),
        authority(5).sftPersonalNameTitle(List.of("James Rollins")),
        authority(6).saftPersonalNameTitle(List.of("Brad Thor")),
        authority(7).corporateName("Pratham Books"),
        authority(8).sftCorporateName(List.of("Stairway Press")),
        authority(9).saftCorporateName(List.of("Warner Bros. Pictures")),
        authority(10).corporateNameTitle("Disney"),
        authority(11).sftCorporateNameTitle(List.of("Blumberg Green Beauty")),
        authority(12).saftCorporateNameTitle(List.of("Amazon Kindle")),
        authority(13).meetingName("Comic-Con"),
        authority(14).sftMeetingName(List.of("Biomedical Symposium")),
        authority(15).saftMeetingName(List.of("World Conference On Corporate Accounting (WCCA)")),
        authority(16).geographicName("Asia Pacific"),
        authority(17).sftGeographicTerm(List.of("North America")),
        authority(18).saftGeographicTerm(List.of("Canada")),
        authority(19).uniformTitle("Harry Potter"),
        authority(20).sftUniformTitle(List.of("War and Peace")),
        authority(21).saftUniformTitle(List.of("The Lord of the Rings")),
        authority(22).topicalTerm("Science"),
        authority(23).sftTopicalTerm(List.of("Fantasy")),
        authority(24).saftTopicalTerm(List.of("History")),
        authority(25).genreTerm("Novel"),
        authority(26).sftGenreTerm(List.of("Poetry")),
        authority(27).saftGenreTerm(List.of("Prose", "Romance"))
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
