package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.CollectionUtils.mergeSafelyToSet;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.SearchUtils.getTotalPages;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.folio.search.utils.SearchUtils.updateMultilangPlainFieldKey;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.folio.search.utils.TestUtils.standardFulltextField;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Instance;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.service.MultilangValue;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
      .isInstanceOf(SearchOperationException.class)
      .hasMessage(String.format(
        "Failed to perform elasticsearch request [index=%s, type=%s, message: %s]", INDEX_NAME, "op", "err"));
  }

  @Test
  void getIndexName_cqlSearchRequest_positive() {
    var cqlSearchRequest = searchServiceRequest(null);
    var actual = getIndexName(cqlSearchRequest);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @Test
  void getIndexName_resourceNameAndTenantId_positive() {
    var actual = getIndexName(INSTANCE_RESOURCE, TENANT_ID);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @Test
  void getIndexName_positive_resourceEvent() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, emptyMap());
    var actual = getIndexName(resourceEvent);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @DisplayName("getTotalPages_parameterized")
  @CsvSource({"0,0", "1,1", "15,1", "21,2", "100,5", "101, 6"})
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void getTotalPages_parameterized(long total, long expected) {
    var totalPages = getTotalPages(total, 20);
    assertThat(totalPages).isEqualTo(expected);
  }

  @CsvSource({
    "path,path",
    "object.value,object.value",
    "field.*,plain_field",
    "field1.field2.*,field1.plain_field2",
    "field1.field2.field3.*,field1.field2.plain_field3"
  })
  @DisplayName("updatePathForTermQueries_parameterized")
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void updatePathForTermQueries_parameterized(String given, String expected) {
    var actual = SearchUtils.updatePathForTermQueries(given);
    assertThat(actual).isEqualTo(expected);
  }

  @CsvSource({
    "field,plain_field",
    "field1.field2,field1.plain_field2",
    "field1.field2.field3,field1.field2.plain_field3"
  })
  @DisplayName("getPathToFulltextPlainValue_parameterized")
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void getPathToFulltextPlainValue_parameterized(String given, String expected) {
    var actual = SearchUtils.getPathToFulltextPlainValue(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"field,field", "plain_field, field"})
  @DisplayName("updateMultilangPlainFieldKey_parameterized")
  void updateMultilangPlainFieldKey_positive(String given, String expected) {
    assertThat(updateMultilangPlainFieldKey(given)).isEqualTo(expected);
  }


  @Test
  void updatePathForFulltextField_positive_multilangField() {
    var actual = SearchUtils.updatePathForFulltextField(multilangField(), "field");
    assertThat(actual).isEqualTo("field.*");
  }

  @Test
  void updatePathForFulltextField_positive_standardFulltextField() {
    var actual = SearchUtils.updatePathForFulltextField(standardFulltextField(), "field");
    assertThat(actual).isEqualTo("field");
  }

  @Test
  void getPathForMultilangField_positive_multilangField() {
    var actual = SearchUtils.getPathForMultilangField("field");
    assertThat(actual).isEqualTo("field.*");
  }

  @Test
  void getStandardFulltextValue_positive_indexPlainFieldFalse() {
    var actual = SearchUtils.getStandardFulltextValue("key", "value", false);
    assertThat(actual).isEqualTo(singletonMap("key", "value"));
  }

  @Test
  void getStandardFulltextValue_positive_indexPlainFieldTrue() {
    var actual = SearchUtils.getStandardFulltextValue("key", "value", true);
    assertThat(actual).isEqualTo(mapOf("key", "value", "plain_key", "value"));
  }

  @Test
  void getPlainFieldValue_positive_multilangField() {
    var actual = SearchUtils.getPlainFieldValue(multilangField(), "key", "v", List.of("eng"));
    assertThat(actual).isEqualTo(mapOf("key", mapOf("eng", "v", "src", "v"), "plain_key", "v"));
  }

  @Test
  void getPlainFieldValue_positive_standardFulltextField() {
    var description = plainField(STANDARD_FIELD_TYPE);
    var actual = SearchUtils.getPlainFieldValue(description, "key", "v", List.of("eng"));
    assertThat(actual).isEqualTo(mapOf("key", "v", "plain_key", "v"));
  }

  @Test
  void getPlainFieldValue_positive_standardFulltextField_doNotIndexPlainValue() {
    var description = plainField(STANDARD_FIELD_TYPE);
    description.setIndexPlainValue(false);
    var actual = SearchUtils.getPlainFieldValue(description, "key", "v", List.of("eng"));
    assertThat(actual).isEqualTo(singletonMap("key", "v"));
  }

  @Test
  void getPlainFieldValue_positive_keywordFulltextField() {
    var actual = SearchUtils.getPlainFieldValue(keywordField(), "key", "v", List.of("eng"));
    assertThat(actual).isEqualTo(singletonMap("key", "v"));
  }

  @Test
  void getMultilangValue_positive_stringValue() {
    var value = "fieldValue";
    var actual = SearchUtils.getMultilangValue("key", value, List.of("eng"));
    assertThat(actual).isEqualTo(mapOf("plain_key", value, "key", mapOf("eng", value, "src", value)));
  }

  @Test
  void getMultilangValue_positive_collectionValue() {
    var value = List.of("fieldValue1", "fieldValue2");
    var actual = SearchUtils.getMultilangValue("key", value, List.of("eng"));
    assertThat(actual).isEqualTo(mapOf("plain_key", value, "key", mapOf("eng", value, "src", value)));
  }

  @Test
  void getMultilangValue_positive_multilangValue() {
    var plainValues = newLinkedHashSet("pv1", "pv2");
    var multilangValues = newLinkedHashSet("mv1", "mv2");
    var value = MultilangValue.of(plainValues, multilangValues);
    var actual = SearchUtils.getMultilangValue("key", value, List.of("eng"));
    assertThat(actual).isEqualTo(mapOf(
      "plain_key", mergeSafelyToSet(multilangValues, plainValues),
      "key", mapOf("eng", multilangValues, "src", multilangValues)));
  }

  @Test
  void getResourceName_positive_instance() {
    var actual = getResourceName(Instance.class);
    assertThat(actual).isEqualTo("instance");
  }

  @Test
  void getResourceName_positive_authority() {
    var actual = getResourceName(Authority.class);
    assertThat(actual).isEqualTo("authority");
  }

  @ParameterizedTest
  @CsvSource({"field.*,true", ",false", "field,false"})
  void isMultilangFieldPath_parameterized(String value, boolean expected) {
    var actual = SearchUtils.isMultilangFieldPath(value);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("isEmptyStringDataSource")
  void isEmptyString_positive(Object given, boolean expected) {
    var actual = SearchUtils.isEmptyString(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("getNumberOfRequestsDataSource")
  void getNumberOfRequests_parameterized(Map<String, List<SearchDocumentBody>> requests, int expected) {
    var actual = SearchUtils.getNumberOfRequests(requests);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideStringsForAlphanumericNormalization")
  void testNormalizeToAlphanumeric(String input, String expected) {
    assertThat(SearchUtils.normalizeToAlphaNumeric(input)).isEqualTo(expected);
  }

  private static Stream<Arguments> provideStringsForAlphanumericNormalization() {
    return Stream.of(
      Arguments.of(null, null),
      Arguments.of("   ", null),
      Arguments.of("abc123", "abc123"),
      Arguments.of(" abc#!@ 123 ", "abc123"),
      Arguments.of("#!@", null)
    );
  }

  private static Stream<Arguments> isEmptyStringDataSource() {
    return Stream.of(
      arguments(new Object(), false),
      arguments(null, false),
      arguments("", true),
      arguments("  ", false),
      arguments("value", false)
    );
  }

  private static Stream<Arguments> getNumberOfRequestsDataSource() {
    return Stream.of(
      arguments(emptyMap(), 0),
      arguments(null, 0),
      arguments(mapOf(RESOURCE_NAME, null), 0),
      arguments(mapOf(RESOURCE_NAME, List.of(searchDocumentBody(), searchDocumentBody())), 2),
      arguments(mapOf("r1", List.of(searchDocumentBody()), "r2", List.of(searchDocumentBody())), 2)
    );
  }
}
