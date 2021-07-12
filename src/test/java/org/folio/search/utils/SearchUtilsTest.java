package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.getTotalPages;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.searchServiceRequest;

import java.io.IOException;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @DisplayName("getTotalPages_parameterized")
  @CsvSource({"0,0", "1,1", "15,1", "21,2", "100,5", "101, 6"})
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void getTotalPages_parameterized(long total, long expected) {
    var totalPages = getTotalPages(total, 20);
    assertThat(totalPages).isEqualTo(expected);
  }

  @DisplayName("updatePathForTermQueries_parameterized")
  @CsvSource({
    "path,path",
    "object.value,object.value",
    "field.*,plain_field",
    "field1.field2.*,field1.plain_field2",
    "field1.field2.field3.*,field1.field2.plain_field3"
  })
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void updatePathForTermQueries_parameterized(String given, String expected) {
    var actual = SearchUtils.updatePathForTermQueries(given);
    assertThat(actual).isEqualTo(expected);
  }

  @DisplayName("getPathToPlainMultilangValue_parameterized")
  @CsvSource({
    "field,plain_field",
    "field1.field2,field1.plain_field2",
    "field1.field2.field3,field1.field2.plain_field3"
  })
  @ParameterizedTest(name = "[{index}] total={0}, expected={1}")
  void getPathToPlainMultilangValue_parameterized(String given, String expected) {
    var actual = SearchUtils.getPathToPlainMultilangValue(given);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getElasticsearchIndexName_positive_resourceIdEvent() {
    var actual = getElasticsearchIndexName(ResourceIdEvent.of(null, RESOURCE_NAME, TENANT_ID));
    assertThat(actual).isEqualTo(INDEX_NAME);
  }
}
