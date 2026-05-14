package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.support.base.ApiEndpoints.instanceClassificationBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.classificationBrowseItem;
import static org.folio.support.utils.TestUtils.classificationBrowseResult;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.ClassificationNumberBrowseResult;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseClassificationIT extends BaseSharedTest {

  private static final String TYPE1_ID = "42471af9-7d25-4f3a-bf78-60d29dcf463b";
  private static final String TYPE2_ID = "ce176ace-a53e-4b4d-aa89-725ed7b2edac";
  private static final String TYPE3_ID = "5af5cb9d-063f-48ea-8148-7da3ecaafd7d";
  private static final String TYPE4_ID = "7e5684a9-c8c1-4c1e-85b9-d047f53eeb6d";

  @BeforeEach
  void setUp() {
    updateClassLcConfig(List.of(UUID.fromString(TYPE1_ID)));
  }

  @MethodSource("classificationBrowsingDataProvider")
  @DisplayName("browseByClassification_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByClassification_parameterized(String query, String anchor, Integer limit,
                                            ClassificationNumberBrowseResult expected) {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.LC))
      .param("query", prepareQuery(query, '"' + anchor + '"'))
      .param("limit", String.valueOf(limit));
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);

    assertThat(actual)
      .as("Classification browse result should match expected for query='%s', anchor='%s'", query, anchor)
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS).isEqualTo(expected);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void browseByClassification_allOption_browsingAroundWithPrecedingRecordsCount() {
    // ALL browse includes 92 entries (all 4 type IDs). Anchor "QA76.73.C15" has TYPE3_ID (5af5cb9d).
    // precedingRecordsCount=2 means 2 items before anchor; remaining 7 items follow.
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"QA76.73.C15\""))
      .param("limit", "10")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual)
      .as("Browse result should have correct totalRecords, prev, and next pointers")
      .extracting(ClassificationNumberBrowseResult::getTotalRecords,
        ClassificationNumberBrowseResult::getPrev,
        ClassificationNumberBrowseResult::getNext)
      .contains(92, "Q9498 .N34 2020", "TK5105.88815");
    assertThat(actual.getItems())
      .as("Browse items should match expected classification entries with precedingRecordsCount=2")
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ENTRY_IGNORING_FIELDS)
      .startsWith(
        classificationBrowseItem("Q9498 .N34 2020", TYPE1_ID, 1,
          "Evolutionary Biology: Mechanisms and Patterns", "Scott, Matthew J.", "Lewis, Sharon M."),
        classificationBrowseItem("QA1771 .R93 1975", TYPE1_ID, 2)
      )
      .contains(
        classificationBrowseItem("QA76.73.C15", TYPE3_ID, 1, "Applied Linguistic Theory", true,
          "Moore, Nancy W."),
        classificationBrowseItem("QP7124 .M32 1979", TYPE1_ID, 1, "The Blade in the Ice",
          "Hernandez, Carlos M."),
        classificationBrowseItem("RC3154 .V85 1998", TYPE1_ID, 1, "Theories of International Relations",
          "Smith, John A.")
      )
      .endsWith(
        classificationBrowseItem("TA5656 .C45 2005", TYPE1_ID, 1, "Environmental Law and Policy",
          "Brown, Patricia K."),
        classificationBrowseItem("TK5105.88815", TYPE4_ID, 1, "Environmental Policy and Governance",
          "Campbell, Melissa U.", "Jones, Michael D.")
      );
  }

  @Test
  void browseByClassification_noExactMatch() {
    var request = get(instanceClassificationBrowsePath(BrowseOptionType.ALL))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"QA100 .X00 2000\""))
      .param("limit", "3")
      .param("precedingRecordsCount", "1");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual)
      .as("Browse result should include placeholder when anchor has no exact match")
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS)
      .isEqualTo(classificationBrowseResult("Q9498 .N34 2020", "QA1771 .R93 1975", 92, List.of(
        classificationBrowseItem("Q9498 .N34 2020", TYPE1_ID, 1, "Evolutionary Biology: Mechanisms and Patterns",
          "Scott, Matthew J.", "Lewis, Sharon M."),
        classificationBrowseItem("QA100 .X00 2000", null, 0, true),
        classificationBrowseItem("QA1771 .R93 1975", TYPE1_ID, 2)
      )));
  }

  @Test
  void browseByClassification_lcOptionConfiguredWithTwoIds() {
    updateClassLcConfig(List.of(UUID.fromString(TYPE1_ID), UUID.fromString(TYPE2_ID)));

    var request = get(instanceClassificationBrowsePath(BrowseOptionType.LC))
      .param("query", prepareQuery("number < {value} or number >= {value}", "\"HD8236 .Y68 2004\""))
      .param("limit", "5")
      .param("precedingRecordsCount", "2");
    var actual = parseResponse(doGet(request), ClassificationNumberBrowseResult.class);
    assertThat(actual)
      .as("Browse result should match expected when LC config is set to two type IDs")
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS)
      .isEqualTo(classificationBrowseResult("GE1748 .C51 1975", "HM3819 .L23 1998", 90, List.of(
        classificationBrowseItem("GE1748 .C51 1975", TYPE1_ID, 1, "Bioethics: Principles and Cases",
          "Gomez, Rachel Y."),
        classificationBrowseItem("HD1691 .I5 1967", TYPE2_ID, 1,
          "A sem\\ntic web primer :0747-0850 & wolves",
          "Ant\\niou, Grigoris matthew", "Van Harmelen, Frank"),
        classificationBrowseItem("HD8236 .Y68 2004", TYPE1_ID, 1, "Media Studies and Public Discourse",
          true, "Lee, Christopher Z."),
        classificationBrowseItem("HD8471 .J90 2001", TYPE1_ID, 1, "Sociology of Education and Schooling",
          "Moore, Nancy W."),
        classificationBrowseItem("HM3819 .L23 1998", TYPE1_ID, 1, "The Dynamics of Social Movements",
          "Anderson, Susan G.")
      )));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> classificationBrowsingDataProvider() {
    var aroundIncludingQuery = "number < {value} or number >= {value}";
    var forwardQuery = "number > {value}";
    var forwardIncludingQuery = "number >= {value}";
    var backwardQuery = "number < {value}";
    var backwardIncludingQuery = "number <= {value}";

    return Stream.of(
      // aroundIncluding at a mid-index anchor
      arguments(aroundIncludingQuery, "507.79", 5, classificationBrowseResult("412.98", "523.06", 89, List.of(
        classificationBrowseItem("412.98", TYPE1_ID, 1, "Cultural Methods in Social Science",
          "Green, Deborah O."),
        classificationBrowseItem("475.42", TYPE1_ID, 1, "Urban Ecology and Sustainable Cities",
          "White, Daniel P."),
        classificationBrowseItem("507.79", TYPE1_ID, 1, "Criminology and Criminal Justice",
          true, "Phillips, Rachel Z.", "Turner, Lawrence B."),
        classificationBrowseItem("508.83", TYPE1_ID, 1, "The Digital Transformation of Libraries",
          "Torres, Angela K."),
        classificationBrowseItem("523.06", TYPE1_ID, 1, "Structural Analysis in Civil Engineering",
          "Hernandez, Carlos M.", "Davis, Barbara E.")
      ))),

      // forwardQuery from anchor
      arguments(forwardQuery, "507.79", 5, classificationBrowseResult("508.83", "569.95", 89, List.of(
        classificationBrowseItem("508.83", TYPE1_ID, 1, "The Digital Transformation of Libraries",
          "Torres, Angela K."),
        classificationBrowseItem("523.06", TYPE1_ID, 1, "Structural Analysis in Civil Engineering",
          "Hernandez, Carlos M.", "Davis, Barbara E."),
        classificationBrowseItem("536.02", TYPE1_ID, 1, "Monetary Theory and Central Banking",
          "Roberts, Alan X."),
        classificationBrowseItem("537.76", TYPE1_ID, 1, "The Neuroscience of Learning and Memory",
          "Nelson, Virginia Q."),
        classificationBrowseItem("569.95", TYPE1_ID, 1, "The Fab Four: A Musical Biography",
          "John Lennon", "Paul McCartney", "George Harrison", "Ringo Starr")
      ))),

      // forwardQuery past end of index → empty result
      arguments(forwardQuery, "Z9999", 10, classificationBrowseResult(null, null, 89, emptyList())),

      // forwardIncludingQuery from anchor (anchor is included, no isAnchor flag for forward-only)
      arguments(forwardIncludingQuery, "507.79", 5, classificationBrowseResult("507.79", "537.76", 89, List.of(
        classificationBrowseItem("507.79", TYPE1_ID, 1, "Criminology and Criminal Justice",
          "Phillips, Rachel Z.", "Turner, Lawrence B."),
        classificationBrowseItem("508.83", TYPE1_ID, 1, "The Digital Transformation of Libraries",
          "Torres, Angela K."),
        classificationBrowseItem("523.06", TYPE1_ID, 1, "Structural Analysis in Civil Engineering",
          "Hernandez, Carlos M.", "Davis, Barbara E."),
        classificationBrowseItem("536.02", TYPE1_ID, 1, "Monetary Theory and Central Banking",
          "Roberts, Alan X."),
        classificationBrowseItem("537.76", TYPE1_ID, 1, "The Neuroscience of Learning and Memory",
          "Nelson, Virginia Q.")
      ))),

      // backwardQuery from anchor
      arguments(backwardQuery, "507.79", 5, classificationBrowseResult("307.06", "475.42", 89, List.of(
        classificationBrowseItem("307.06", TYPE1_ID, 1, "Cognitive Behavioral Approaches in Therapy",
          "Williams, Robert T."),
        classificationBrowseItem("395.99", TYPE1_ID, 1, "Language Acquisition and Development",
          "Anderson, Susan G."),
        classificationBrowseItem("405.55", TYPE1_ID, 1, "Ground-water exploration in Al Marj (1964)",
          "Jackson, Mark B."),
        classificationBrowseItem("412.98", TYPE1_ID, 1, "Cultural Methods in Social Science",
          "Green, Deborah O."),
        classificationBrowseItem("475.42", TYPE1_ID, 1, "Urban Ecology and Sustainable Cities",
          "White, Daniel P.")
      ))),

      // backwardQuery before start of index → empty result
      arguments(backwardQuery, "001", 10, classificationBrowseResult(null, null, 89, emptyList())),

      // backwardIncludingQuery from anchor (anchor included as last item)
      arguments(backwardIncludingQuery, "507.79", 5, classificationBrowseResult("395.99", "507.79", 89, List.of(
        classificationBrowseItem("395.99", TYPE1_ID, 1, "Language Acquisition and Development",
          "Anderson, Susan G."),
        classificationBrowseItem("405.55", TYPE1_ID, 1, "Ground-water exploration in Al Marj (1964)",
          "Jackson, Mark B."),
        classificationBrowseItem("412.98", TYPE1_ID, 1, "Cultural Methods in Social Science",
          "Green, Deborah O."),
        classificationBrowseItem("475.42", TYPE1_ID, 1, "Urban Ecology and Sustainable Cities",
          "White, Daniel P."),
        classificationBrowseItem("507.79", TYPE1_ID, 1, "Criminology and Criminal Justice",
          "Phillips, Rachel Z.", "Turner, Lawrence B.")
      ))),

      // aroundIncluding near end of index: only 4 items returned, next=null (last entry reached)
      arguments(aroundIncludingQuery, "Z4056 .U98 1999", 5, classificationBrowseResult("TR9115 .C83 1971",
        null, 89, List.of(
          classificationBrowseItem("TR9115 .C83 1971", TYPE1_ID, 1,
            "Architectural History of the Modern Era", "Hill, Michelle M.", "Wright, Lisa I."),
          classificationBrowseItem("Z2364 .G82 1987", TYPE1_ID, 1, "Ground water in Sirte, Libya",
            "Davis, Barbara E.", "White, Daniel P."),
          classificationBrowseItem("Z4056 .U98 1999", TYPE1_ID, 1, "Chopin: A Life in Music and Exile",
            true, "Thomas, Dorothy C.", "Parker, George D."),
          classificationBrowseItem("Z6032 .E89 1981", TYPE1_ID, 1,
            "Postcolonial Studies: Theory and Practice", "Harris, Elizabeth A.", "Johnson, Mary L.")
        )))
    );
  }
}
