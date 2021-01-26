package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.getTotalPages;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.util.stream.Stream;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class SearchUtilsTest {

  @Test
  void performExceptionalOperation_positive() {
    var actual = performExceptionalOperation(() -> 0, INDEX_NAME, "op");
    assertThat(actual).isZero();
  }

  @Test
  void performExceptionalOperation_negative() {
    assertThatThrownBy(() -> performExceptionalOperation(() -> {
      throw new IOException("err");
    }, INDEX_NAME, "op"))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage(String.format("Failed to perform elasticsearch request [index=%s, type=%s, message: %s]",
        INDEX_NAME, "op", "err"));
  }

  @Test
  void getElasticsearchIndexName_cqlSearchRequest_positive() {
    var cqlSearchRequest = CqlSearchRequest.of(RESOURCE_NAME, null, TENANT_ID, null, null);
    var actual = getElasticsearchIndexName(cqlSearchRequest);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @Test
  void getElasticsearchIndexName_resourceNameAndTenantId_positive() {
    var actual = getElasticsearchIndexName(RESOURCE_NAME, TENANT_ID);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @MethodSource("totalPagesTestData")
  @ParameterizedTest(name = "[{index}] total={0}")
  @DisplayName("should calculate total pages count")
  void getTotalPages_parameterized(long total, long expected) {
    var totalPages = getTotalPages(total, 20);
    assertThat(totalPages).isEqualTo(expected);
  }

  @Test
  void isMultiLanguageField_positive() {
    var actual = SearchUtils.isMultiLanguageField(mapOf("src", "v1", "eng", "v1"));
    assertThat(actual).isTrue();
  }

  @Test
  void isMultiLanguageField_positive_nullValue() {
    var actual = SearchUtils.isMultiLanguageField(mapOf("src", null));
    assertThat(actual).isTrue();
  }

  @Test
  void isMultiLanguageField_negative() {
    var actual = SearchUtils.isMultiLanguageField(mapOf("prop1", "v1", "prop2", "v1"));
    assertThat(actual).isFalse();
  }

  @Test
  void getMultiLanguageField_positive() {
    var actual = SearchUtils.getSourceMultilangValue(mapOf("src", "v1", "eng", "v1"));
    assertThat(actual).isEqualTo("v1");
  }

  @Test
  void getMultiLanguageField_positive_nullValue() {
    var actual = SearchUtils.getSourceMultilangValue(mapOf("src", null, "eng", "v1"));
    assertThat(actual).isNull();
  }

  @Test
  void getMultiLanguageField_negative_stringValue() {
    var actual = SearchUtils.getSourceMultilangValue("value");
    assertThat(actual).isNull();
  }

  private static Stream<Arguments> totalPagesTestData() {
    return Stream.of(
      arguments(0, 0),
      arguments(1, 1),
      arguments(15, 1),
      arguments(21, 2),
      arguments(100, 5),
      arguments(101, 6)
    );
  }
}
