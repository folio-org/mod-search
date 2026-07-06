package org.folio.api.browse;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.BrowseOptionType.ALL;
import static org.folio.search.domain.dto.BrowseOptionType.LC;
import static org.folio.search.domain.dto.BrowseOptionType.NLM;
import static org.folio.search.domain.dto.BrowseOptionType.OTHER;
import static org.folio.search.domain.dto.BrowseOptionType.SUDOC;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceCallNumberBrowsePath;
import static org.folio.support.utils.JsonTestUtils.parseResponse;
import static org.folio.support.utils.TestUtils.cnBrowseItem;
import static org.folio.support.utils.TestUtils.cnBrowseResult;
import static org.folio.support.utils.TestUtils.cnEmptyBrowseItem;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.CallNumberBrowseResult;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.support.TestRailCase;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class BrowseCallNumberIT extends BaseSharedTest {

  private static final String LC_TYPE_ID = "cbc422b0-1d17-4d43-9cc0-6c89b2efd014";
  private static final String LC2_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  private static final String LC3_TYPE_ID = "512173a7-bd09-490e-b773-17d83f2b63fe";
  private static final String DEWEY_TYPE_ID = "0b5d15ad-7f08-45f1-8504-7bab31a2b4e5";
  private static final String NLM_TYPE_ID = "530b84ea-c8b3-4a90-a2cd-2e4a7bb5f18e";
  private static final String SUDOC_TYPE_ID = "6b368b19-01af-4a44-a0d3-09ec5d1e3e19";
  private static final String OTHER_TYPE_ID = "cf74a451-5b9e-4a58-8c5c-2eff9fb4db67";

  @BeforeEach
  void setUp() {
    updateCnConfig(List.of(), ALL, ShelvingOrderAlgorithmType.DEFAULT, TENANT_ID);
    updateCnConfig(List.of(UUID.fromString(LC_TYPE_ID)), LC, ShelvingOrderAlgorithmType.LC, TENANT_ID);
    updateCnConfig(List.of(UUID.fromString(SUDOC_TYPE_ID)), SUDOC, ShelvingOrderAlgorithmType.SUDOC, TENANT_ID);
    updateCnConfig(List.of(UUID.fromString(NLM_TYPE_ID)), NLM, ShelvingOrderAlgorithmType.NLM, TENANT_ID);
    updateCnConfig(List.of(UUID.fromString(OTHER_TYPE_ID)), OTHER, ShelvingOrderAlgorithmType.DEFAULT, TENANT_ID);
  }

  @TestRailCase(477527)
  @MethodSource("callNumberBrowsingDataProvider")
  @DisplayName("browseByCallNumber_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}, value=''{1}'', limit={2}")
  void browseByCallNumber_parameterized(String query, String anchor, Integer limit,
                                        CallNumberBrowseResult expected) {
    var searchQuery = prepareQuery(query, '"' + anchor + '"');
    var request = get(instanceCallNumberBrowsePath(ALL))
      .param("expandAll", "true")
      .param(QUERY_PARAM, searchQuery)
      .param(LIMIT_PARAM, String.valueOf(limit));
    var actual = parseResponse(doGet(request, TENANT_ID), CallNumberBrowseResult.class);
    assertThat(actual).as("Expected browse result for query '%s'", searchQuery)
      .usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS).isEqualTo(expected);
  }

  /**
   * Only call numbers of the type(s) configured for a browse option appear as exact matches.
   * Call numbers of non-configured types must NOT produce an exact match; only the configured
   * type should.
   */
  @TestRailCase({627504, 627506, 627509, 627507})
  @ParameterizedTest(name = "[{index}] browseOption={0}, configuredTypeId={1}, exactMatchCallNumber={4}")
  @MethodSource("onlyConfiguredTypesReturnExactMatchProvider")
  void browseByCallNumber_onlyConfiguredTypesReturnExactMatch(BrowseOptionType browseOptionType,
                                                              String configuredTypeId,
                                                              ShelvingOrderAlgorithmType algorithmType,
                                                              List<String> nonMatchingCallNumbers,
                                                              String exactMatchCallNumber) {
    updateCnConfig(List.of(UUID.fromString(configuredTypeId)), browseOptionType, algorithmType, TENANT_ID);

    // Non-configured types should NOT produce an exact match
    for (var callNumber : nonMatchingCallNumbers) {
      assertThat(browse(callNumber, browseOptionType).getItems())
        .as("Expected no exact match for call number '%s' with %s option", callNumber, browseOptionType)
        .anySatisfy(item -> assertThat(item).usingRecursiveComparison()
          .ignoringFields(ENTRY_IGNORING_FIELDS).isEqualTo(cnEmptyBrowseItem(callNumber)));
    }

    // Configured type SHOULD produce an exact match
    assertThat(browse(exactMatchCallNumber, browseOptionType).getItems())
      .as("Expected exact match for call number '%s' with %s option", exactMatchCallNumber, browseOptionType)
      .anySatisfy(item -> {
        assertThat(item.getFullCallNumber()).isEqualTo(exactMatchCallNumber);
        assertThat(item.getIsAnchor()).isTrue();
        assertThat(item.getTotalRecords()).isGreaterThan(0);
      });
  }

  /**
   * When a browse config has no configured call number types (empty typeIds),
   * call numbers of every type should produce an exact match for that browse option.
   */
  @TestRailCase({627500, 627501, 627502, 627503})
  @ParameterizedTest(name = "[{0}] empty config - all types return exact match")
  @MethodSource("emptyConfigBrowseOptionProvider")
  void browseByCallNumber_emptyConfig_allTypesReturnExactMatch(BrowseOptionType browseOptionType,
                                                               ShelvingOrderAlgorithmType algorithmType) {
    updateCnConfig(emptyList(), browseOptionType, algorithmType, TENANT_ID);

    // LC, DEWEY, NLM, SUDOC, OTHER call-numbers should produce an exact match regardless of the browse option type
    // since there are no configured types to limit the matches
    for (var callNumber : List.of("Q127.U6U49", "338.1 MOG", "QV 18.2 L765 2015", "Y 10.13:980", "SYLY-12")) {
      assertThat(browse(callNumber, browseOptionType).getItems())
        .as("Expected exact match for '%s' with empty %s config", callNumber, browseOptionType)
        .anySatisfy(item -> {
          assertThat(item.getFullCallNumber()).isEqualTo(callNumber);
          assertThat(item.getIsAnchor()).isTrue();
          assertThat(item.getTotalRecords()).isGreaterThan(0);
        });
    }
  }

  @SuppressWarnings("checkstyle:MethodLength")
  @Test
  void browseByCallNumber_lcOption_browsingAroundAnchor() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var request = get(instanceCallNumberBrowsePath(LC))
      .param("expandAll", "true")
      .param(QUERY_PARAM, prepareQuery(aroundQuery, "\"RC280.N4 N49\""))
      .param(LIMIT_PARAM, "5");
    var actual = parseResponse(doGet(request, TENANT_ID), CallNumberBrowseResult.class);
    var expected = cnBrowseResult("QP363 .N6 1965 FT MEADE", "RJ421 .D3", 20, List.of(
      cnBrowseItem("QP363 .N6 1965", null, "FT MEADE", LC_TYPE_ID, 2, null),
      cnBrowseItem("QR1.I6", LC_TYPE_ID, 1, "Sociology of Education and Schooling"),
      cnBrowseItem("RC280.N4 N49", LC_TYPE_ID, 1, "Game Theory and Strategic Behavior", true),
      cnBrowseItem("RC667 .N47 2010", LC_TYPE_ID, 1, "Migration, Identity, and Belonging"),
      cnBrowseItem("RJ421 .D3", LC_TYPE_ID, 1, "The Ethics of Artificial Intelligence")
    ));
    assertThat(actual).usingRecursiveComparison().ignoringFields(COLLECTION_IGNORING_FIELDS).isEqualTo(expected);
  }

  private static Stream<Arguments> onlyConfiguredTypesReturnExactMatchProvider() {
    return Stream.of(
      // ALL option configured with SUDOC only — LC, DEWEY, NLM, OTHER must not match. TestRail case 627504
      arguments(ALL, SUDOC_TYPE_ID, ShelvingOrderAlgorithmType.DEFAULT,
        List.of("Q127.U6U49", "338.1 MOG", "QV 18.2 L765 2015", "SYLY-12"),
        "Y 10.13:980"),
      // LC option configured with LC only — SUDOC, DEWEY, NLM, OTHER must not match. TestRail case 627506
      arguments(LC, LC_TYPE_ID, ShelvingOrderAlgorithmType.LC,
        List.of("Y 10.13:980", "338.1 MOG", "QV 18.2 L765 2015", "SYLY-12"),
        "Q127.U6U49"),
      // SUDOC option configured with SUDOC only — LC, DEWEY, NLM, OTHER must not match. TestRail case 627509
      arguments(SUDOC, SUDOC_TYPE_ID, ShelvingOrderAlgorithmType.SUDOC,
        List.of("Q127.U6U49", "338.1 MOG", "QV 18.2 L765 2015", "SYLY-12"),
        "Y 10.13:980"),
      // NLM option configured with NLM only — LC, DEWEY, SUDOC, OTHER must not match. TestRail case 627507
      arguments(NLM, NLM_TYPE_ID, ShelvingOrderAlgorithmType.NLM,
        List.of("Q127.U6U49", "338.1 MOG", "Y 10.13:980", "SYLY-12"),
        "QV 18.2 L765 2015")
    );
  }

  private static Stream<Arguments> emptyConfigBrowseOptionProvider() {
    return Stream.of(
      // TestRail case 627500
      arguments(LC, ShelvingOrderAlgorithmType.LC),
      // TestRail case 627501
      arguments(NLM, ShelvingOrderAlgorithmType.NLM),
      // TestRail case 627502
      arguments(OTHER, ShelvingOrderAlgorithmType.DEFAULT),
      // TestRail case 627503
      arguments(SUDOC, ShelvingOrderAlgorithmType.SUDOC)
    );
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> callNumberBrowsingDataProvider() {
    var aroundQuery = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var forwardQuery = "fullCallNumber > {value}";
    var backwardQuery = "fullCallNumber < {value}";

    return Stream.of(
      // anchor call number appears in the middle of the result set
      arguments(aroundQuery, "TA357 .A78 2010", 5,
        cnBrowseResult("SDD 66727", "TK5105.88815 . A58 2004 FT MEADE c.2", 116, List.of(
          cnBrowseItem("SDD 66727", LC_TYPE_ID, 1, "Data Structures and Algorithm Design"),
          cnBrowseItem("SYLY-12", OTHER_TYPE_ID, 1, "Game Theory and Strategic Behavior"),
          cnBrowseItem("TA357 .A78 2010", LC_TYPE_ID, 3, null, true),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "Oversize", "c.1", LC3_TYPE_ID, 1,
            "A sem\\ntic web primer :0747-0850 & wolves"),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "REF", "c.2", LC3_TYPE_ID, 1,
            "A sem\\ntic web primer :0747-0850 & wolves")
        ))),

      // not existed anchor call number appears in the middle of the result set
      arguments(aroundQuery, "TA357 .A78 2011", 5,
        cnBrowseResult("SYLY-12", "TK5105.88815 . A58 2004 FT MEADE c.2", 116, List.of(
          cnBrowseItem("SYLY-12", OTHER_TYPE_ID, 1, "Game Theory and Strategic Behavior"),
          cnBrowseItem("TA357 .A78 2010", LC_TYPE_ID, 3, null),
          cnEmptyBrowseItem("TA357 .A78 2011"),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "Oversize", "c.1", LC3_TYPE_ID, 1,
            "A sem\\ntic web primer :0747-0850 & wolves"),
          cnBrowseItem("TK5105.88815 . A58 2004 FT MEADE", "REF", "c.2", LC3_TYPE_ID, 1,
            "A sem\\ntic web primer :0747-0850 & wolves")
        ))),

      // anchor call number appears first in the result set
      arguments(aroundQuery, "0.257638889", 5,
        cnBrowseResult(null, "0634V", 116, List.of(
          cnBrowseItem("0.257638889", "Oversize", null, OTHER_TYPE_ID, 1,
            "The Digital Transformation of Libraries", true),
          cnBrowseItem("015/.73", DEWEY_TYPE_ID, 1, "Advanced Topics in Molecular Biology"),
          cnBrowseItem("0634V", OTHER_TYPE_ID, 1, "Monetary Theory and Central Banking")
        ))),

      // not existed anchor call number appears first in the result set
      arguments(aroundQuery, "0.0", 5,
        cnBrowseResult(null, "015/.73", 116, List.of(
          cnEmptyBrowseItem("0.0"),
          cnBrowseItem("0.257638889", "Oversize", null, OTHER_TYPE_ID, 1,
            "The Digital Transformation of Libraries"),
          cnBrowseItem("015/.73", DEWEY_TYPE_ID, 1, "Advanced Topics in Molecular Biology")
        ))),

      // anchor call number appears last in the result set
      arguments(aroundQuery, "Z997.S93", 5,
        cnBrowseResult("Y10.2:P38", null, 116, List.of(
          cnBrowseItem("Y10.2:P38", "microfc", null, SUDOC_TYPE_ID, 1, "The Digital Transformation of Libraries"),
          cnBrowseItem("Y4.J89/2:R53", SUDOC_TYPE_ID, 1, "Philosophy of Science: An Introduction"),
          cnBrowseItem("Z997.S93", LC_TYPE_ID, 1, "Quantum Mechanics: Concepts and Applications", true)
        ))),

      // not existed anchor call number appears last in the result set
      arguments(aroundQuery, "ZZ", 5,
        cnBrowseResult("Y4.J89/2:R53", null, 116, List.of(
          cnBrowseItem("Y4.J89/2:R53", SUDOC_TYPE_ID, 1, "Philosophy of Science: An Introduction"),
          cnBrowseItem("Z997.S93", LC_TYPE_ID, 1, "Quantum Mechanics: Concepts and Applications"),
          cnEmptyBrowseItem("ZZ")
        ))),

      // call number with backslashes
      arguments(aroundQuery, "BR\\\\140 .J\\\\\\\\86", 5,
        cnBrowseResult("BJ1499.S65", "Collins", 116, List.of(
          cnBrowseItem("BJ1499.S65", LC_TYPE_ID, 1, "Ecology of Freshwater Systems"),
          cnBrowseItem("BLUE", "POP/ROCK", "CD", OTHER_TYPE_ID, 1,
            "The Psychology of Human Decision-Making"),
          cnBrowseItem("BR\\140 .J\\\\86", OTHER_TYPE_ID, 1, "The Rise of Global Financial Markets", true),
          cnBrowseItem("CHOPIN", "CD PIANO", null, OTHER_TYPE_ID, 1, "Foundations of Quantum Computing"),
          cnBrowseItem("Collins", "Picture Book", null, OTHER_TYPE_ID, 1,
            "Philosophy of Mind and Consciousness")
        ))),

      // forward browsing from the middle of the result set
      arguments(forwardQuery, "RC280.N4 N49", 5,
        cnBrowseResult("RC667 .N47 2010", "SDD 66727", 116, List.of(
          cnBrowseItem("RC667 .N47 2010", LC_TYPE_ID, 1, "Migration, Identity, and Belonging"),
          cnBrowseItem("RJ421 .D3", LC_TYPE_ID, 1, "The Ethics of Artificial Intelligence"),
          cnBrowseItem("Rowling", "jFICTION", null, OTHER_TYPE_ID, 1, "Media Studies and Public Discourse"),
          cnBrowseItem("SB1.P71", LC_TYPE_ID, 1, "Structural Analysis in Civil Engineering"),
          cnBrowseItem("SDD 66727", LC_TYPE_ID, 1, "Data Structures and Algorithm Design")
        ))),

      // forward browsing from the end of the result set
      arguments(forwardQuery, "Z997.S93", 5,
        cnBrowseResult(null, null, 116, emptyList())),

      // backward browsing from the middle of the result set
      arguments(backwardQuery, "RC280.N4 N49", 5,
        cnBrowseResult("QR1.I6", "RC2067 .X72 1978", 116, List.of(
          cnBrowseItem("QR1.I6", LC_TYPE_ID, 1, "Sociology of Education and Schooling"),
          cnBrowseItem("QV 18.2 L765 2015", "Stacks", null, NLM_TYPE_ID, 1,
            "Structural Analysis in Civil Engineering"),
          cnBrowseItem("QX 4 J647m", NLM_TYPE_ID, 1, "Monetary Theory and Central Banking"),
          cnBrowseItem("QZ 380 N494 1979", NLM_TYPE_ID, 3, null),
          cnBrowseItem("RC2067 .X72 1978", LC2_TYPE_ID, 1, "Environmental Policy and Governance")
        ))),

      // backward browsing from the beginning of the result set
      arguments(backwardQuery, "0.257638889", 5,
        cnBrowseResult(null, null, 116, emptyList()))
    );
  }

  private CallNumberBrowseResult browse(String fullCallNumber, BrowseOptionType browseOptionType) {
    var query = "fullCallNumber >= {value} or fullCallNumber < {value}";
    var request = get(instanceCallNumberBrowsePath(browseOptionType))
      .param("expandAll", "true")
      .param(QUERY_PARAM, prepareQuery(query, '"' + fullCallNumber + '"'))
      .param(LIMIT_PARAM, "5");
    return parseResponse(doGet(request, TENANT_ID), CallNumberBrowseResult.class);
  }
}
