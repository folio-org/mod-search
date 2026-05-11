package org.folio.api.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.instanceContributorBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.contributorBrowseItem;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.ContributorBrowseResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseContributorIT extends BaseSharedTest {

  private static final String[] NAME_TYPE_IDS =
    array("e2ef4075-310a-4447-a231-712bf10cc985",
      "0ad0a89a-741d-4f1a-85a6-ada214751013",
      "1f857623-89ca-4f0b-ab56-5c30f706df3e",
      "2b94c631-fca9-4892-a730-03ee529ffe2a",
      "9fb7f83e-260e-479f-9539-dfd9a628b858");

  private static final String[] TYPE_IDS =
    array("2a165833-1673-493f-934b-f3d3c8fcb299",
      "3ae36e29-e38f-457c-8fcf-1974a6cb63d3",
      "653ffe66-aa3f-4f1c-a090-c42c4011ef40",
      "6e09d47d-95e2-4d8a-831b-f777b8ef6d81",
      "9deb29d1-3e71-4951-9413-a80adac703d0");

  private static final String[] AUTHORITY_IDS =
    array("0a4c6d10-2161-4f64-aace-9e919489b6c9",
      "7ff32633-cc49-4332-870a-b05e329d2a2d",
      "55294032-fcf6-45cc-b6da-4420a61ef72e");

  @MethodSource("contributorBrowsingDataProvider")
  @DisplayName("browseByContributor_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByContributor_parameterized(String query, String anchor, Integer limit,
                                         ContributorBrowseResult expected) {
    var request = get(instanceContributorBrowsePath()).param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseByContributor_withNameTypeFilter() {
    var request = get(instanceContributorBrowsePath()).param("query",
      "(" + prepareQuery("name >= {value} or name < {value}", '"' + "John Lennon" + '"') + ") "
      + "and contributorNameTypeId==" + NAME_TYPE_IDS[0]).param("limit", "5");

    var actual = parseResponse(doGet(request), ContributorBrowseResult.class);
    // Only 6 browse items have nameTypeId = e2ef4075 in the shared test dataset:
    // Anthony Kiedis, Bon Jovi, Darth Vader, Klaus Meine, Paul McCartney×2
    // "John Lennon" does not exist with this nameType, so it becomes a placeholder.
    // Darth Vader is now between Bon Jovi and John Lennon alphabetically.
    var expected = new ContributorBrowseResult().totalRecords(6).prev("Bon Jovi").next("Paul McCartney").items(
      List.of(
        contributorBrowseItem(2, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
        contributorBrowseItem(1, "Darth Vader", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0]),
        contributorBrowseItem(0, true, "John Lennon"),
        contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
        contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1])));

    assertThat(actual).isEqualTo(expected);
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> contributorBrowsingDataProvider() {
    var aroundQuery = "name > {value} or name < {value}";
    var aroundIncludingQuery = "name >= {value} or name < {value}";
    var forwardQuery = "name > {value}";
    var forwardIncludingQuery = "name >= {value}";
    var backwardQuery = "name < {value}";
    var backwardIncludingQuery = "name <= {value}";

    return Stream.of(
      // around query: "John" is between "Jane Kowalski" and "John Lennon"
      arguments(aroundQuery, "John", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Jackson, Mark B.").next("John Lennon").items(List.of(
          contributorBrowseItem(2, "Jackson, Mark B.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(1, "Jane Kowalski", NAME_TYPE_IDS[3], AUTHORITY_IDS[2], TYPE_IDS[3]),
          contributorBrowseItem(0, true, "John"),
          contributorBrowseItem(2, "John Lennon", NAME_TYPE_IDS[2], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(1, "John Lennon", NAME_TYPE_IDS[2], null, TYPE_IDS[0])))),

      // around query: "Klausy" sits between the two Klaus Meine items and "Lee, Christopher Z."
      arguments(aroundQuery, "Klausy", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Klaus Meine").next("Lewis, Sharon M.").items(List.of(
          contributorBrowseItem(1, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[3]),
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(0, true, "Klausy"),
          contributorBrowseItem(4, "Lee, Christopher Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(2, "Lewis, Sharon M.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding query: "Lenon" is between "Lee, Christopher Z." and "Lewis, Sharon M."
      arguments(aroundIncludingQuery, "Lenon", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Klaus Meine").next("Lopez, Margaret Y.").items(List.of(
          contributorBrowseItem(2, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(4, "Lee, Christopher Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(0, true, "Lenon"),
          contributorBrowseItem(2, "Lewis, Sharon M.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(3, "Lopez, Margaret Y.", NAME_TYPE_IDS[3], null, TYPE_IDS[3],
            TYPE_IDS[4])))),

      // aroundIncluding: exact match "Klaus Meine" — both items are returned as anchor
      arguments(aroundIncludingQuery, "Klaus Meine", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Jones, Michael D.").next("Lee, Christopher Z.").items(
          List.of(
            contributorBrowseItem(2, "Jones, Michael D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(1, "King, Jason H.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(1, true, "Klaus Meine", NAME_TYPE_IDS[1], AUTHORITY_IDS[0], TYPE_IDS[3]),
            contributorBrowseItem(2, true, "Klaus Meine", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(4, "Lee, Christopher Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding: "Bon Jovi" with wide window shows band-member items and regular contributors
      arguments(aroundIncludingQuery, "Bon Jovi", 10,
        new ContributorBrowseResult().totalRecords(68).prev("Anthony Kiedis").next("Carter, Julie W.").items(
          List.of(
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
            contributorBrowseItem(2, "Baker, Raymond R.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(1, "bbb ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(1, "bcc ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, true, "Bon Jovi", NAME_TYPE_IDS[0], AUTHORITY_IDS[0],
              TYPE_IDS[0], TYPE_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(1, true, "Bon Jovi", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0]),
            contributorBrowseItem(1, "Brown, Patricia K.", NAME_TYPE_IDS[3], null, TYPE_IDS[4]),
            contributorBrowseItem(1, "Campbell, Melissa U.", NAME_TYPE_IDS[3], null, TYPE_IDS[4]),
            contributorBrowseItem(2, "Carter, Julie W.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding: "PMC" falls between Paul McCartney entries and Ringo Starr
      arguments(aroundIncludingQuery, "PMC", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Paul McCartney").next("Rivera, Anthony T.").items(
          List.of(
            contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(4, "Phillips, Rachel Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(0, true, "PMC"),
            contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(1, "Rivera, Anthony T.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding: "0" is before all contributors — no preceding items, prev=null
      arguments(aroundIncludingQuery, "0", 3,
        new ContributorBrowseResult().totalRecords(68).prev(null).next("1111 2222").items(List.of(
          contributorBrowseItem(0, true, "0"),
          contributorBrowseItem(1, "1111 2222", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding: "Young, Steven F." is near the end of the index — next=null
      arguments(aroundIncludingQuery, "Young, Steven F.", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Wilson, James F.").next(null).items(List.of(
          contributorBrowseItem(2, "Wilson, James F.", NAME_TYPE_IDS[3], null, TYPE_IDS[3], TYPE_IDS[4]),
          contributorBrowseItem(2, "Wright, Lisa I.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(2, true, "Young, Steven F.", NAME_TYPE_IDS[3], null,
            TYPE_IDS[3], TYPE_IDS[4]),
          contributorBrowseItem(1, "yyy zzz", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // aroundIncluding: contributor with backslashes in name
      arguments(aroundIncludingQuery, "Wi\\\\lly \\\\ Wonka", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Walker, Donna E.").next("Wilson, James F.").items(
          List.of(
            contributorBrowseItem(1, "Walker, Donna E.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, "White, Daniel P.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(1, true, "Wi\\lly \\ Wonka", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(4, "Williams, Robert T.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, "Wilson, James F.", NAME_TYPE_IDS[3], null,
              TYPE_IDS[3], TYPE_IDS[4])))),

      // browsing forward: from "ringo" shows Ringo Starr and next regular contributors
      arguments(forwardQuery, "ringo", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Ringo Starr").next("Scott, Matthew J.").items(List.of(
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Rivera, Anthony T.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(2, "Roberts, Alan X.", NAME_TYPE_IDS[3], null, TYPE_IDS[3], TYPE_IDS[4]),
          contributorBrowseItem(1, "Robinson, Paul D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(3, "Scott, Matthew J.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // browsing forward: from "anthony" shows Anthony Kiedis (both entries) and next contributors
      arguments(forwardQuery, "anthony", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Anthony Kiedis").next("bcc ccc").items(List.of(
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "Baker, Raymond R.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(1, "bbb ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(1, "bcc ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // browsing forward from "Z": nothing in index after "Z" — items=null
      arguments(forwardQuery, "Z", 10, new ContributorBrowseResult().totalRecords(68).items(null)),

      // forwardIncluding: same result as forward "ringo" since "Ringo Starr" != "ringo"
      arguments(forwardIncludingQuery, "Ringo Starr", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Ringo Starr").next("Scott, Matthew J.").items(List.of(
          contributorBrowseItem(2, "Ringo Starr", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[0], TYPE_IDS[1]),
          contributorBrowseItem(1, "Rivera, Anthony T.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(2, "Roberts, Alan X.", NAME_TYPE_IDS[3], null, TYPE_IDS[3], TYPE_IDS[4]),
          contributorBrowseItem(1, "Robinson, Paul D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(3, "Scott, Matthew J.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // forwardIncluding: same result as forward "anthony"
      arguments(forwardIncludingQuery, "anthony", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Anthony Kiedis").next("bcc ccc").items(List.of(
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[1], AUTHORITY_IDS[1], TYPE_IDS[2]),
          contributorBrowseItem(1, "Anthony Kiedis", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[0]),
          contributorBrowseItem(2, "Baker, Raymond R.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(1, "bbb ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
          contributorBrowseItem(1, "bcc ccc", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // browsing backward: from "Ringo Starr" shows 5 items ending at Phillips
      arguments(backwardQuery, "Ringo Starr", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Nelson, Virginia Q.").next("Phillips, Rachel Z.").items(
          List.of(
            contributorBrowseItem(3, "Nelson, Virginia Q.", NAME_TYPE_IDS[3], null,
              TYPE_IDS[3], TYPE_IDS[4]),
            contributorBrowseItem(4, "Parker, George D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(4, "Phillips, Rachel Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // browsing backward from "R": same items as backward "Ringo Starr"
      arguments(backwardQuery, "R", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Nelson, Virginia Q.").next("Phillips, Rachel Z.").items(
          List.of(
            contributorBrowseItem(3, "Nelson, Virginia Q.", NAME_TYPE_IDS[3], null,
              TYPE_IDS[3], TYPE_IDS[4]),
            contributorBrowseItem(4, "Parker, George D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(4, "Phillips, Rachel Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))),

      // browsing backward from "0": "0" is before all contributors — items=null
      arguments(backwardQuery, "0", 10, new ContributorBrowseResult().totalRecords(68).items(null)),

      // backwardIncluding "ringo": same result as backward "Ringo Starr" (no exact match)
      arguments(backwardIncludingQuery, "ringo", 5,
        new ContributorBrowseResult().totalRecords(68).prev("Nelson, Virginia Q.").next("Phillips, Rachel Z.").items(
          List.of(
            contributorBrowseItem(3, "Nelson, Virginia Q.", NAME_TYPE_IDS[3], null,
              TYPE_IDS[3], TYPE_IDS[4]),
            contributorBrowseItem(4, "Parker, George D.", NAME_TYPE_IDS[3], null, TYPE_IDS[3]),
            contributorBrowseItem(2, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[0], TYPE_IDS[0], TYPE_IDS[1]),
            contributorBrowseItem(1, "Paul McCartney", NAME_TYPE_IDS[0], AUTHORITY_IDS[1], TYPE_IDS[2]),
            contributorBrowseItem(4, "Phillips, Rachel Z.", NAME_TYPE_IDS[3], null, TYPE_IDS[3])))));
  }
}
