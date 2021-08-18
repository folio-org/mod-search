package org.folio.search.utils;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.getTotalPages;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.folio.search.utils.TestUtils.standardFulltextField;

import java.io.IOException;
import java.util.List;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class SearchUtilsTest extends EnvironmentUnitTest {

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
      .hasMessage(String.format("Failed to perform elasticsearch request [index=%s, type=%s, message: %s]",
        INDEX_NAME, "op", "err"));
  }

  @Test
  void getElasticsearchIndexName_cqlSearchRequest_positive() {
    var cqlSearchRequest = searchServiceRequest(RESOURCE_NAME, null);
    var actual = getElasticsearchIndexName(cqlSearchRequest);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @Test
  void getElasticsearchIndexName_resourceNameAndTenantId_positive() {
    var actual = getElasticsearchIndexName(RESOURCE_NAME, TENANT_ID);
    assertThat(actual).isEqualTo(INDEX_NAME);
  }

  @Test
  void getElasticsearchIndexName_positive_resourceIdEvent() {
    var idEvent = ResourceIdEvent.of(randomId(), RESOURCE_NAME, TENANT_ID, IndexActionType.INDEX);
    var actual = getElasticsearchIndexName(idEvent);
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
  @DisplayName("getPathToPlainMultilangValue_parameterized")
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void getPathToPlainMultilangValue_parameterized(String given, String expected) {
    var actual = SearchUtils.getPathToPlainMultilangValue(given);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getMultilangValue_positive() {
    var value = "value";
    var actual = SearchUtils.getMultilangValue("field", value, List.of("eng", "ger"));
    assertThat(actual).isEqualTo(mapOf(
      "field", mapOf("eng", value, "ger", value, "src", value),
      "plain_field", value));
  }

  @Test
  void getEffectiveCallNumber_positive() {
    var actual = SearchUtils.getEffectiveCallNumber("prefix", "cn", null);
    assertThat(actual).isEqualTo("prefix cn");
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
  void updatePathForMultilangField_positive_multilangField() {
    var actual = SearchUtils.updatePathForMultilangField("field");
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
}
