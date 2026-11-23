package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.utils.ShaUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnitTest
class ContributorIdsProcessorTest {

  private final ContributorIdsProcessor processor = new ContributorIdsProcessor();

  @Test
  @DisplayName("Returns empty set when contributors are absent")
  void getFieldValue_positive_noContributors() {
    assertThat(processor.getFieldValue(new Instance())).isEmpty();
    assertThat(processor.getFieldValue(new Instance().contributors(List.of()))).isEmpty();
  }

  @Test
  @DisplayName("Skips contributors with blank name")
  void getFieldValue_positive_blankName() {
    var instance = new Instance().contributors(List.of(
      new Contributor().name(" "), new Contributor().name(null)));

    assertThat(processor.getFieldValue(instance)).isEmpty();
  }

  @Test
  @DisplayName("Computes id from trimmed name, name type and authority")
  void getFieldValue_positive_idFormula() {
    var instance = new Instance().contributors(List.of(
      new Contributor().name(" John ").contributorNameTypeId("type").authorityId("auth"),
      new Contributor().name("Jane")));

    assertThat(processor.getFieldValue(instance)).containsExactlyInAnyOrder(
      ShaUtils.sha("John", "type", "auth"), ShaUtils.sha("Jane", "", ""));
  }

  @Test
  @DisplayName("Same name with different type or authority gives different ids, duplicates collapse")
  void getFieldValue_positive_differentIds() {
    var instance = new Instance().contributors(List.of(
      new Contributor().name("John").contributorNameTypeId("type1").authorityId("auth"),
      new Contributor().name("John").contributorNameTypeId("type2").authorityId("auth"),
      new Contributor().name("John").contributorNameTypeId("type1"),
      new Contributor().name("John").contributorNameTypeId("type1").authorityId("auth")));

    assertThat(processor.getFieldValue(instance)).containsExactlyInAnyOrder(
      ShaUtils.sha("John", "type1", "auth"),
      ShaUtils.sha("John", "type2", "auth"),
      ShaUtils.sha("John", "type1", ""));
  }
}
