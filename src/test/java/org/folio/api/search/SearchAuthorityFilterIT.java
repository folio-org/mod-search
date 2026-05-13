package org.folio.api.search;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class SearchAuthorityFilterIT extends BaseSharedTest {

  private static final int RECORDS_COUNT = 15;
  private static final String[] IDS = {
    "00000001-0000-4000-8000-000000000000",
    "00000046-0000-4000-8000-000000000000",
    "00000005-0000-4000-8000-000000000000",
    "00000009-0000-4000-8000-000000000000",
    "6edc7db0-5363-41ec-bf63-0242ac130002",
    "75b6e1e8-5363-41ec-bf63-0242ac130002",
    "62f72eeb-ed5a-4619-b01f-1750d5528d27",
    "62f72eeb-ed5a-4619-b01f-1750d5528d28",
    "7bfb7550-5363-41ec-bf63-0242ac130002",
    "3ec9be46-b002-472e-87c9-0e8a4c9eb8d2",
    "74f97d56-37ce-44b8-9316-4e5dd4efc103",
    "62f72eeb-ed5a-4619-b01f-1750d5528d32",
    "8eb6625e-5363-41ec-bf63-0242ac130002",
    "0d4cfb3c-0d5d-4bab-8e99-8f077b09bd34",
    "2dfd4b30-8c45-4a2e-8779-05f338513584"
  };
  private static final String AUTHORITY_ID_FILTER =
    "id==(" + String.join(" OR ", IDS) + ")";

  private static String scoped(String query) {
    return AUTHORITY_ID_FILTER + " AND (" + query + ")";
  }

  @MethodSource("filteredSearchQueriesProvider")
  @DisplayName("searchByAuthorities_parameterized")
  @ParameterizedTest(name = "[{index}] query={0}")
  void searchByAuthorities_parameterized(String query, List<String> expectedIds) throws Exception {
    var resultActions = doSearchByAuthorities(scoped(query))
      .andExpect(status().isOk());
    if (expectedIds != null) {
      resultActions
        .andExpect(jsonPath("totalRecords", is(expectedIds.size())))
        .andExpect(jsonPath("authorities[*].id", containsInAnyOrder(expectedIds.toArray(String[]::new))));
    } else {
      resultActions
        .andExpect(jsonPath("totalRecords", is(0)));
    }
  }

  @MethodSource("invalidDateSearchQueriesProvider")
  @DisplayName("searchByInvalidDates_parameterized")
  @ParameterizedTest(name = "[{index}] value={1}")
  void searchByAuthorities_negative_invalidDateFormat(String name, String value) throws Exception {
    attemptSearchByAuthorities("(" + name + "==" + value + ")")
      .andExpect(status().isUnprocessableContent())
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", is("Invalid date format")))
      .andExpect(jsonPath("$.errors[0].type", is("ValidationException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is(name)))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(value)));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> filteredSearchQueriesProvider() {
    return Stream.of(
      arguments("(id=* and headingType==\"Conference Name\")", List.of(IDS[4])),
      arguments("(id=* and headingType==\"Geographic Name\")", List.of(IDS[5])),
      arguments("(id=* and headingType==\"Genre\")", List.of(IDS[6], IDS[7])),
      arguments("(id=* and headingType==\"Corporate Name\")", List.of(IDS[8], IDS[9])),
      arguments("(id=* and headingType==\"Topical\")", List.of(IDS[10])),
      arguments("(id=* and headingType==\"Uniform Title\")", List.of(IDS[11], IDS[12], IDS[13])),
      arguments("(headingType==\"Uniform Title\")", List.of(IDS[11], IDS[12], IDS[13])),

      arguments("(isTitleHeadingRef==true)", List.of(IDS[2], IDS[4])),
      arguments("(isTitleHeadingRef==false and headingType==\"Personal Name\")",
        List.of(IDS[0], IDS[1], IDS[3], IDS[14])),

      arguments("(id=* and authRefType==\"Auth/Ref\" and headingType==\"Other\")", null),
      arguments("(id=* and authRefType==\"Auth/Ref\" and headingType==\"Uniform Title\")", List.of(IDS[13])),
      arguments("(id=* and authRefType==\"Authorized\" and headingType==\"Personal Name\")",
        List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(authRefType==\"Authorized\" and headingType==\"Conference Name\")", List.of(IDS[4])),

      arguments("(id=* and subjectHeadings==\"a\")", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(id=* and subjectHeadings==\"b\")", List.of(IDS[4])),
      arguments("(id=* and subjectHeadings==\"c\")", List.of(IDS[5], IDS[6], IDS[7])),
      arguments("(id=* and subjectHeadings==\"d\")", List.of(IDS[8], IDS[9])),
      arguments("(id=* and subjectHeadings==\"k\")", List.of(IDS[10])),
      arguments("(id=* and subjectHeadings==\"n\")", List.of(IDS[11], IDS[12])),
      arguments("(subjectHeadings==\"n\")", List.of(IDS[11], IDS[12])),

      arguments("(metadata.createdDate >= 2021-03-01) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate >= 2021-03-01T00:00:00.000) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate >= 2021-03-01T00:00:00.000Z) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate >= 2021-03-01T00:00:00.000+00:00) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate > 2021-03-01) ", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.createdDate >= 2021-03-01 and metadata.createdDate < 2021-03-10) ",
        List.of(IDS[0], IDS[2])),

      arguments("(metadata.updatedDate >= 2021-03-14) ", List.of(IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-01) ", List.of(IDS[0], IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate > 2021-03-05) ", List.of(IDS[1], IDS[2], IDS[3])),
      arguments("(metadata.updatedDate < 2021-03-15) ", List.of(IDS[0], IDS[1])),
      arguments("(metadata.updatedDate > 2021-03-14 and metadata.updatedDate < 2021-03-16) ",
        List.of(IDS[2], IDS[3]))
    );
  }

  private static Stream<Arguments> invalidDateSearchQueriesProvider() {
    return Stream.of(
      arguments("metadata.createdDate", "12345"),
      arguments("metadata.createdDate", "2022-6-27"),
      arguments("metadata.createdDate", "2022-06-1"),
      arguments("metadata.createdDate", "2022-06-40"),
      arguments("metadata.updatedDate", "2022-15-01"),
      arguments("metadata.updatedDate", "invalidDate")
    );
  }
}
