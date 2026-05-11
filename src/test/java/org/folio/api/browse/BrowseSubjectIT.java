package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.instanceSubjectBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.subjectBrowseItem;
import static org.folio.support.utils.TestUtils.subjectBrowseResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.SubjectBrowseResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseSubjectIT extends BaseSharedTest {

  private static final String MUSIC_AUTHORITY_ID_1 = "e62bbefe-adf5-4b1e-b3e7-43d877b0c91a";
  private static final String MUSIC_AUTHORITY_ID_2 = "308c950f-8209-4f2e-9702-0c004a9f21bc";
  private static final String MUSIC_SOURCE_ID = "33e04938-720f-4814-82f6-416f91ac5795";
  private static final String MUSIC_TYPE_ID = "252681cd-2fa1-4c25-a5b8-a5213a99d073";
  private static final String ENV_DESIGN_AUTHORITY_ID = "55294032-fcf6-45cc-b6da-4420a61ef72e";
  private static final String SEMANTIC_WEB_AUTHORITY_ID = "55294032-fcf6-45cc-b6da-4420a61ef72d";

  @MethodSource("subjectBrowsingDataProvider")
  @DisplayName("browseBySubject_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseBySubject_parameterized(String query, String anchor, Integer limit, SubjectBrowseResult expected) {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseBySubject_browsingAroundWithPrecedingRecordsCount() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery("value < {value} or value >= {value}", "\"Machine learning\""))
      .param("limit", "7")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(52).prev("Library science").next("Music")
      .items(List.of(
        subjectBrowseItem(6, "Library science"),
        subjectBrowseItem(3, "Literature and society"),
        subjectBrowseItem(7, "Machine learning", true),
        subjectBrowseItem(3, "Media studies"),
        subjectBrowseItem(4, "Metadata"),
        subjectBrowseItem(8, "Molecular biology"),
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID, MUSIC_TYPE_ID))));
  }

  @Test
  void browseBySubject_browsingAroundWithoutHighlightMatch() {
    var request = get(instanceSubjectBrowsePath())
      .param("query", prepareQuery("value < {value} or value >= {value}", "\"genetics\""))
      .param("limit", "4")
      .param("highlightMatch", "false");
    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);

    assertThat(actual).isEqualTo(new SubjectBrowseResult()
      .totalRecords(52).prev("Fantasy").next("Global economics")
      .items(List.of(
        subjectBrowseItem(1, "Fantasy"),
        subjectBrowseItem(3, "Game theory"),
        subjectBrowseItem(8, "Genetics"),
        subjectBrowseItem(3, "Global economics"))));
  }

  @Test
  void browseBySubject_withSourceFilter() {
    var request = get(instanceSubjectBrowsePath()).param("query",
      "(" + prepareQuery("value >= {value} or value < {value}", '"' + "Philosophy" + '"') + ") "
      + "and sourceId==" + MUSIC_SOURCE_ID).param("limit", "5");

    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    var expected = new SubjectBrowseResult().totalRecords(2).prev(null).next(null).items(
      List.of(
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID, MUSIC_TYPE_ID),
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID, null),
        subjectBrowseItem(0, "Philosophy", true)));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void browseBySubject_withTypeFilter() {
    var request = get(instanceSubjectBrowsePath()).param("query",
      "(" + prepareQuery("value >= {value} or value < {value}", '"' + "Philosophy" + '"') + ") "
      + "and typeId==" + MUSIC_TYPE_ID).param("limit", "5");

    var actual = parseResponse(doGet(request), SubjectBrowseResult.class);
    var expected = new SubjectBrowseResult().totalRecords(1).prev(null).next(null).items(
      List.of(
        subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID, MUSIC_TYPE_ID),
        subjectBrowseItem(0, "Philosophy", true)));

    assertThat(actual).isEqualTo(expected);
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> subjectBrowsingDataProvider() {
    var aroundQuery = "value > {value} or value < {value}";
    var aroundIncludingQuery = "value >= {value} or value < {value}";
    var forwardQuery = "value > {value}";
    var forwardIncludingQuery = "value >= {value}";
    var backwardQuery = "value < {value}";
    var backwardIncludingQuery = "value <= {value}";

    return Stream.of(
      // browsing around (anchor is excluded)
      arguments(aroundQuery, "computer", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Clinical psychology").next("Computer vision")
        .items(List.of(
          subjectBrowseItem(5, "Clinical psychology"),
          subjectBrowseItem(3, "Communication theory"),
          subjectBrowseItem(0, "computer", true),
          subjectBrowseItem(3, "Computer science"),
          subjectBrowseItem(3, "Computer vision")))),

      arguments(aroundQuery, "applied", 5, new SubjectBrowseResult()
        .totalRecords(52).prev(null).next("Architectural design")
        .items(List.of(
          subjectBrowseItem(0, "applied", true),
          subjectBrowseItem(5, "Applied mathematics"),
          subjectBrowseItem(4, "Architectural design")))),

      // browsing around including (anchor is included or placeholder)
      arguments(aroundIncludingQuery, "genetics", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Fantasy").next("History")
        .items(List.of(
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "Game theory"),
          subjectBrowseItem(8, "Genetics", true),
          subjectBrowseItem(3, "Global economics"),
          subjectBrowseItem(3, "History")))),

      arguments(aroundIncludingQuery, "applied", 5, new SubjectBrowseResult()
        .totalRecords(52).prev(null).next("Architectural design")
        .items(List.of(
          subjectBrowseItem(0, "applied", true),
          subjectBrowseItem(5, "Applied mathematics"),
          subjectBrowseItem(4, "Architectural design")))),

      arguments(aroundIncludingQuery, "music", 25, new SubjectBrowseResult()
        .totalRecords(52).prev("History").next("Renewable energy")
        .items(List.of(
          subjectBrowseItem(3, "History"),
          subjectBrowseItem(8, "History of science"),
          subjectBrowseItem(1, "Information management"),
          subjectBrowseItem(2, "Information technology"),
          subjectBrowseItem(4, "International relations"),
          subjectBrowseItem(6, "Knowledge organization"),
          subjectBrowseItem(6, "Library science"),
          subjectBrowseItem(3, "Literature and society"),
          subjectBrowseItem(7, "Machine learning"),
          subjectBrowseItem(3, "Media studies"),
          subjectBrowseItem(4, "Metadata"),
          subjectBrowseItem(8, "Molecular biology"),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID, MUSIC_TYPE_ID, true),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID, null, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, true),
          subjectBrowseItem(5, "Natural language processing"),
          subjectBrowseItem(2, "Network security"),
          subjectBrowseItem(4, "Neuroscience"),
          subjectBrowseItem(3, "Operations research"),
          subjectBrowseItem(5, "Philosophy of mind"),
          subjectBrowseItem(8, "Political theory"),
          subjectBrowseItem(3, "Public administration"),
          subjectBrowseItem(3, "Quantum computing"),
          subjectBrowseItem(1, "Renewable energy")))),

      arguments(aroundIncludingQuery, "music", 11, new SubjectBrowseResult()
        .totalRecords(52).prev("Literature and society").next("Network security")
        .items(List.of(
          subjectBrowseItem(3, "Literature and society"),
          subjectBrowseItem(7, "Machine learning"),
          subjectBrowseItem(3, "Media studies"),
          subjectBrowseItem(4, "Metadata"),
          subjectBrowseItem(8, "Molecular biology"),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_2, MUSIC_SOURCE_ID, MUSIC_TYPE_ID, true),
          subjectBrowseItem(2, "Music", MUSIC_AUTHORITY_ID_2, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, MUSIC_SOURCE_ID, null, true),
          subjectBrowseItem(1, "Music", MUSIC_AUTHORITY_ID_1, true),
          subjectBrowseItem(5, "Natural language processing"),
          subjectBrowseItem(2, "Network security")))),

      // anchor not in data — tests preceding and following around absent point
      arguments(aroundIncludingQuery, "FC", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Environmental design").next("Genetics")
        .items(List.of(
          subjectBrowseItem(1, "Environmental design", ENV_DESIGN_AUTHORITY_ID),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(0, "FC", true),
          subjectBrowseItem(3, "Game theory"),
          subjectBrowseItem(8, "Genetics")))),

      // anchor before all subjects
      arguments(aroundIncludingQuery, "a", 5, new SubjectBrowseResult()
        .totalRecords(52).prev(null).next("Architectural design")
        .items(List.of(
          subjectBrowseItem(0, "a", true),
          subjectBrowseItem(5, "Applied mathematics"),
          subjectBrowseItem(4, "Architectural design")))),

      // anchor after all subjects
      arguments(aroundIncludingQuery, "z", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Urban planning").next(null)
        .items(List.of(
          subjectBrowseItem(6, "Urban planning"),
          subjectBrowseItem(1, "Urban sociology"),
          subjectBrowseItem(0, "z", true)))),

      // anchor with authority id
      arguments(aroundIncludingQuery, "Environmental design", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Economic development").next("Game theory")
        .items(List.of(
          subjectBrowseItem(5, "Economic development"),
          subjectBrowseItem(2, "Education reform"),
          subjectBrowseItem(1, "Environmental design", ENV_DESIGN_AUTHORITY_ID, true),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "Game theory")))),

      // subject with special characters
      arguments(aroundIncludingQuery, "backslash\\\\ \\\"double\\\\-quotes\\\\\\\" te\\\\st", 5,
        new SubjectBrowseResult()
          .totalRecords(52).prev("Architectural design").next("Climate change")
          .items(List.of(
            subjectBrowseItem(4, "Architectural design"),
            subjectBrowseItem(4, "Artificial intelligence"),
            subjectBrowseItem(1, "backslash\\ \"double\\-quotes\\\" te\\st", true),
            subjectBrowseItem(3, "Biography"),
            subjectBrowseItem(2, "Climate change")))),

      // browsing forward
      arguments(forwardQuery, "urban", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Urban planning").next(null)
        .items(List.of(
          subjectBrowseItem(6, "Urban planning"),
          subjectBrowseItem(1, "Urban sociology")))),

      arguments(forwardQuery, "applied", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Applied mathematics").next("Biography")
        .items(List.of(
          subjectBrowseItem(5, "Applied mathematics"),
          subjectBrowseItem(4, "Architectural design"),
          subjectBrowseItem(4, "Artificial intelligence"),
          subjectBrowseItem(1, "backslash\\ \"double\\-quotes\\\" te\\st"),
          subjectBrowseItem(3, "Biography")))),

      // checks if collapsing works in forward direction
      arguments(forwardQuery, "F", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Fantasy").next("History")
        .items(List.of(
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "Game theory"),
          subjectBrowseItem(8, "Genetics"),
          subjectBrowseItem(3, "Global economics"),
          subjectBrowseItem(3, "History")))),

      arguments(forwardQuery, "Z", 10, subjectBrowseResult(52, emptyList())),

      arguments(forwardIncludingQuery, "urban", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Urban planning").next(null)
        .items(List.of(
          subjectBrowseItem(6, "Urban planning"),
          subjectBrowseItem(1, "Urban sociology")))),

      arguments(forwardIncludingQuery, "applied", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Applied mathematics").next("Biography")
        .items(List.of(
          subjectBrowseItem(5, "Applied mathematics"),
          subjectBrowseItem(4, "Architectural design"),
          subjectBrowseItem(4, "Artificial intelligence"),
          subjectBrowseItem(1, "backslash\\ \"double\\-quotes\\\" te\\st"),
          subjectBrowseItem(3, "Biography")))),

      // browsing backward
      arguments(backwardQuery, "urban", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Quantum computing").next("Statistics")
        .items(List.of(
          subjectBrowseItem(3, "Quantum computing"),
          subjectBrowseItem(1, "Renewable energy"),
          subjectBrowseItem(1, "Semantic Web", SEMANTIC_WEB_AUTHORITY_ID),
          subjectBrowseItem(4, "Social sciences"),
          subjectBrowseItem(3, "Statistics")))),

      arguments(backwardQuery, "genetics", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Economic development").next("Game theory")
        .items(List.of(
          subjectBrowseItem(5, "Economic development"),
          subjectBrowseItem(2, "Education reform"),
          subjectBrowseItem(1, "Environmental design", ENV_DESIGN_AUTHORITY_ID),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "Game theory")))),

      arguments(backwardQuery, "G", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Digital humanities").next("Fantasy")
        .items(List.of(
          subjectBrowseItem(5, "Digital humanities"),
          subjectBrowseItem(5, "Economic development"),
          subjectBrowseItem(2, "Education reform"),
          subjectBrowseItem(1, "Environmental design", ENV_DESIGN_AUTHORITY_ID),
          subjectBrowseItem(1, "Fantasy")))),

      arguments(backwardQuery, "A", 10, subjectBrowseResult(52, emptyList())),

      arguments(backwardIncludingQuery, "urban", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Quantum computing").next("Statistics")
        .items(List.of(
          subjectBrowseItem(3, "Quantum computing"),
          subjectBrowseItem(1, "Renewable energy"),
          subjectBrowseItem(1, "Semantic Web", SEMANTIC_WEB_AUTHORITY_ID),
          subjectBrowseItem(4, "Social sciences"),
          subjectBrowseItem(3, "Statistics")))),

      arguments(backwardIncludingQuery, "genetics", 5, new SubjectBrowseResult()
        .totalRecords(52).prev("Education reform").next("Genetics")
        .items(List.of(
          subjectBrowseItem(2, "Education reform"),
          subjectBrowseItem(1, "Environmental design", ENV_DESIGN_AUTHORITY_ID),
          subjectBrowseItem(1, "Fantasy"),
          subjectBrowseItem(3, "Game theory"),
          subjectBrowseItem(8, "Genetics"))))
    );
  }
}
