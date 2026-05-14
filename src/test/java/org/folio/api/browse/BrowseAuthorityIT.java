package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.folio.api.browse.BrowseAuthorityIT.BrowseItem.anchor;
import static org.folio.api.browse.BrowseAuthorityIT.BrowseItem.item;
import static org.folio.api.browse.BrowseAuthorityIT.BrowseResult.result;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.authorityBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.AuthorityBrowseItem;
import org.folio.search.domain.dto.AuthorityBrowseResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseAuthorityIT extends BaseSharedTest {

  private static final String BROWSE_SOURCE_FILE_ID = "b4000001-5de4-4467-b77f-b2057d6d69b6";
  private static final String AUTHORITY_SCOPE_FILTER = "sourceFileId==\"" + BROWSE_SOURCE_FILE_ID + "\"";

  @MethodSource("authorityBrowsingDataProvider")
  @DisplayName("browseByAuthority_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByAuthority_parameterized(String query, String anchor, Integer limit, BrowseResult expected) {
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery(query, '"' + anchor + '"')))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(60);
    assertThat(actual.getPrev()).isEqualTo(expected.prev());
    assertThat(actual.getNext()).isEqualTo(expected.next());
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactlyElementsOf(
        expected.items().stream()
          .map(i -> tuple(i.headingRef(), i.isAnchor()))
          .toList());
  }

  @Test
  void browseByAuthority_browsingAroundWithAdditionalFilters() {
    var request = get(authorityBrowsePath())
      .param("query", prepareQuery("(headingRef>={value} or headingRef<{value}) "
                                   + "and isTitleHeadingRef==false "
                                   + "and tenantId==" + TENANT_ID + " "
                                   + "and shared==false "
                                   + "and headingType==(\"Personal Name\") "
                                   + "and " + AUTHORITY_SCOPE_FILTER, "\"Ĵämes Röllins\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(2);
    assertThat(actual.getPrev()).isNull();
    assertThat(actual.getNext()).isNull();
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactly(
        tuple("Brian K. Vaughan", null),
        tuple("Ĵämes Röllins", true),
        tuple("Zappa Frank", null));
  }

  @Test
  void browseByAuthority_browsingAroundWithDiacritics() {
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery("(headingRef>={value} or headingRef<{value})", "\"Ĵämes Röllins test\"")))
      .param("limit", "3")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(60);
    assertThat(actual.getPrev()).isEqualTo("International Biomedical Conference");
    assertThat(actual.getNext()).isEqualTo("Ĵämes Röllins test");
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactly(
        tuple("International Biomedical Conference", null),
        tuple("Ĵämes Röllins", null),
        tuple("Ĵämes Röllins test", true));
  }

  @Test
  void browseByAuthority_browsingAroundWithPrecedingRecordsCount() {
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery("headingRef < {value} or headingRef >= {value}", "\"Ĵämes Röllins\"")))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(60);
    assertThat(actual.getPrev()).isEqualTo("Historical studies");
    assertThat(actual.getNext()).isEqualTo("Late medieval era");
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactly(
        tuple("Historical studies", null),
        tuple("International Biomedical Conference", null),
        tuple("Ĵämes Röllins", true),
        tuple("Keyboard instrument", null),
        tuple("Knowledge", null),
        tuple("Knowledge and learning", null),
        tuple("Late medieval era", null));
  }

  @Test
  void browseByAuthority_browsingAroundAtIndexEnd() {
    // Verifies correct boundary handling when the anchor is the last item in the index
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery("headingRef < {value} or headingRef >= {value}", "\"Zappa Frank Songs\"")))
      .param("limit", "3");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(60);
    assertThat(actual.getPrev()).isEqualTo("Zappa Frank");
    assertThat(actual.getNext()).isNull();
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactly(
        tuple("Zappa Frank", null),
        tuple("Zappa Frank Songs", true));
  }

  @Test
  void browseByAuthority_browsingAroundWithoutHighlightMatch() {
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery("headingRef < {value} or headingRef >= {value}", "\"fantasy\"")))
      .param("limit", "5")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getTotalRecords()).isEqualTo(60);
    assertThat(actual.getPrev()).isEqualTo("Early Modern period");
    assertThat(actual.getNext()).isEqualTo("French Revolution");
    assertThat(actual.getItems())
      .extracting(AuthorityBrowseItem::getHeadingRef, AuthorityBrowseItem::getIsAnchor)
      .containsExactly(
        tuple("Early Modern period", null),
        tuple("Eruption of Vesuvius", null),
        tuple("Fantasy", null),
        tuple("Fantasy literature", null),
        tuple("French Revolution", null));
  }

  @Test
  void browseByAuthority_checkReturnedFields() {
    var request = get(authorityBrowsePath())
      .param("query", scoped(prepareQuery("headingRef >= {value}", "\"Brian K. Vaughan\"")))
      .param("limit", "1");
    var actual = parseResponse(doGet(request), AuthorityBrowseResult.class);

    assertThat(actual.getItems()).hasSize(1);
    var item = actual.getItems().getFirst();
    assertThat(item.getHeadingRef()).isEqualTo("Brian K. Vaughan");
    assertThat(item.getIsAnchor()).isNull();

    var authority = item.getAuthority();
    assertThat(authority).isNotNull();
    assertThat(authority.getHeadingRef()).isEqualTo("Brian K. Vaughan");
    assertThat(authority.getHeadingType()).isEqualTo("Personal Name");
    assertThat(authority.getAuthRefType()).isEqualTo("Authorized");
    assertThat(authority.getSourceFileId()).isEqualTo(BROWSE_SOURCE_FILE_ID);
    assertThat(authority.getNaturalId()).isNotNull();
    assertThat(authority.getNumberOfTitles()).isZero();
    assertThat(authority.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(authority.getShared()).isFalse();
  }

  private static String scoped(String query) {
    return "(" + query + ") and " + AUTHORITY_SCOPE_FILTER;
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> authorityBrowsingDataProvider() {
    var aroundQuery = "headingRef > {value} or headingRef < {value}";
    var aroundIncludingQuery = "headingRef >= {value} or headingRef < {value}";
    var forwardQuery = "headingRef > {value}";
    var forwardIncludingQuery = "headingRef >= {value}";
    var backwardQuery = "headingRef < {value}";
    var backwardIncludingQuery = "headingRef <= {value}";

    return Stream.of(
      // browsing around (anchor excluded, placeholder injected)
      arguments(aroundQuery, "Brian K. Vaughan", 5, result("Biomedical Symposium", "Canadian Provinces",
        List.of(
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty"),
          anchor("Brian K. Vaughan"),
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces")))),

      arguments(aroundQuery, "harry", 5, result("French Revolution", "Highland areas",
        List.of(
          item("French Revolution"),
          item("Green Beauty Holdings"),
          anchor("harry"),
          item("Harry Potter"),
          item("Highland areas")))),

      arguments(aroundQuery, "a", 5, result(null, "Antiquity",
        List.of(
          anchor("a"),
          item("Amazon Digital Services"),
          item("Antiquity")))),

      arguments(aroundQuery, "zz", 5, result("Zappa Frank", null,
        List.of(
          item("Zappa Frank"),
          item("Zappa Frank Songs"),
          anchor("zz")))),

      // browsing around including (anchor included if in index, placeholder only if not found)
      arguments(aroundIncludingQuery, "Brian K. Vaughan", 5, result("Biomedical Symposium", "Canadian Provinces",
        List.of(
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty"),
          anchor("Brian K. Vaughan"),
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces")))),

      arguments(aroundIncludingQuery, "harry", 5, result("French Revolution", "Highland areas",
        List.of(
          item("French Revolution"),
          item("Green Beauty Holdings"),
          anchor("harry"),
          item("Harry Potter"),
          item("Highland areas")))),

      arguments(aroundIncludingQuery, "music", 5, result("Maps", "North America Region",
        List.of(
          item("Maps"),
          item("Mountain ranges"),
          anchor("music"),
          item("North America"),
          item("North America Region")))),

      arguments(aroundIncludingQuery, "music", 25, result("Highland areas", "Rollins, James",
        List.of(
          item("Highland areas"),
          item("Historical studies"),
          item("International Biomedical Conference"),
          item("Ĵämes Röllins"),
          item("Keyboard instrument"),
          item("Knowledge"),
          item("Knowledge and learning"),
          item("Late medieval era"),
          item("Late medieval period"),
          item("Manuscripts, Medieval"),
          item("Maps"),
          item("Mountain ranges"),
          anchor("music"),
          item("North America"),
          item("North America Region"),
          item("Novel"),
          item("Orchestra"),
          item("Periodicals"),
          item("Poetry"),
          item("Poetry collections"),
          item("Prose fiction"),
          item("Renaissance period"),
          item("Revolution"),
          item("River valleys"),
          item("Rollins, James")))),

      arguments(aroundIncludingQuery, "music", 11, result("Late medieval era", "Periodicals",
        List.of(
          item("Late medieval era"),
          item("Late medieval period"),
          item("Manuscripts, Medieval"),
          item("Maps"),
          item("Mountain ranges"),
          anchor("music"),
          item("North America"),
          item("North America Region"),
          item("Novel"),
          item("Orchestra"),
          item("Periodicals")))),

      arguments(aroundIncludingQuery, "FC", 5, result("Fantasy", "Green Beauty Holdings",
        List.of(
          item("Fantasy"),
          item("Fantasy literature"),
          anchor("FC"),
          item("French Revolution"),
          item("Green Beauty Holdings")))),

      // browsing forward
      arguments(forwardQuery, "Brian K. Vaughan", 5, result("Brian K. Vaughan Title", "Coastal deltas",
        List.of(
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces"),
          item("Cartographic materials"),
          item("Classical period"),
          item("Coastal deltas")))),

      arguments(forwardQuery, "biology", 5, result("Biomedical Symposium", "Canadian Provinces",
        List.of(
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty"),
          item("Brian K. Vaughan"),
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces")))),

      // checks forward browsing starting from a single-letter anchor
      arguments(forwardQuery, "F", 5, result("Fantasy", "Harry Potter",
        List.of(
          item("Fantasy"),
          item("Fantasy literature"),
          item("French Revolution"),
          item("Green Beauty Holdings"),
          item("Harry Potter")))),

      arguments(forwardQuery, "ZZ", 10, result(null, null, emptyList())),

      arguments(forwardIncludingQuery, "Brian K. Vaughan", 5, result("Brian K. Vaughan", "Classical period",
        List.of(
          item("Brian K. Vaughan"),
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces"),
          item("Cartographic materials"),
          item("Classical period")))),

      arguments(forwardIncludingQuery, "biology", 5, result("Biomedical Symposium", "Canadian Provinces",
        List.of(
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty"),
          item("Brian K. Vaughan"),
          item("Brian K. Vaughan Title"),
          item("Canadian Provinces")))),

      // browsing backward
      arguments(backwardQuery, "Brian K. Vaughan", 5, result("Antiquity", "Blumberg Green Beauty",
        List.of(
          item("Antiquity"),
          item("Asia Pacific"),
          item("Bibliographies and indexes"),
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty")))),

      arguments(backwardQuery, "fun", 5, result("Early Modern period", "French Revolution",
        List.of(
          item("Early Modern period"),
          item("Eruption of Vesuvius"),
          item("Fantasy"),
          item("Fantasy literature"),
          item("French Revolution")))),

      arguments(backwardQuery, "G", 5, result("Early Modern period", "French Revolution",
        List.of(
          item("Early Modern period"),
          item("Eruption of Vesuvius"),
          item("Fantasy"),
          item("Fantasy literature"),
          item("French Revolution")))),

      arguments(backwardQuery, "A", 10, result(null, null, emptyList())),

      arguments(backwardIncludingQuery, "Brian K. Vaughan", 5, result("Asia Pacific", "Brian K. Vaughan",
        List.of(
          item("Asia Pacific"),
          item("Bibliographies and indexes"),
          item("Biomedical Symposium"),
          item("Blumberg Green Beauty"),
          item("Brian K. Vaughan")))),

      arguments(backwardIncludingQuery, "fun", 5, result("Early Modern period", "French Revolution",
        List.of(
          item("Early Modern period"),
          item("Eruption of Vesuvius"),
          item("Fantasy"),
          item("Fantasy literature"),
          item("French Revolution"))))
    );
  }

  record BrowseResult(String prev, String next, List<BrowseItem> items) {

    static BrowseResult result(String prev, String next, List<BrowseItem> items) {
      return new BrowseResult(prev, next, items);
    }
  }

  record BrowseItem(String headingRef, Boolean isAnchor) {

    static BrowseItem item(String headingRef) {
      return new BrowseItem(headingRef, null);
    }

    static BrowseItem anchor(String headingRef) {
      return new BrowseItem(headingRef, Boolean.TRUE);
    }
  }
}
