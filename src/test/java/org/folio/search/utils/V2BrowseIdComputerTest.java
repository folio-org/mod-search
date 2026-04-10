package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class V2BrowseIdComputerTest {

  @Test
  void computeContributorBrowseId_returnsStableId() {
    var contributor = Map.<String, Object>of(
      "name", "Smith, John",
      "contributorNameTypeId", "type-1",
      "authorityId", "auth-1"
    );

    var id = V2BrowseIdComputer.computeContributorBrowseId(contributor);

    assertThat(id).isNotNull().isNotBlank();
    // Same input produces same ID
    assertThat(V2BrowseIdComputer.computeContributorBrowseId(contributor)).isEqualTo(id);
  }

  @Test
  void computeContributorBrowseId_returnsNullForBlankName() {
    var contributor = Map.<String, Object>of(
      "name", "   ",
      "contributorNameTypeId", "type-1"
    );

    assertThat(V2BrowseIdComputer.computeContributorBrowseId(contributor)).isNull();
  }

  @Test
  void computeContributorBrowseId_handlesNullFields() {
    var contributor = new HashMap<String, Object>();
    contributor.put("name", "Smith");
    contributor.put("contributorNameTypeId", null);
    contributor.put("authorityId", null);

    var id = V2BrowseIdComputer.computeContributorBrowseId(contributor);

    assertThat(id).isNotNull();
  }

  @Test
  void computeContributorBrowseId_differentNameProducesDifferentId() {
    var c1 = Map.<String, Object>of("name", "Smith, John", "contributorNameTypeId", "type-1");
    var c2 = Map.<String, Object>of("name", "Doe, Jane", "contributorNameTypeId", "type-1");

    assertThat(V2BrowseIdComputer.computeContributorBrowseId(c1))
      .isNotEqualTo(V2BrowseIdComputer.computeContributorBrowseId(c2));
  }

  @Test
  void computeSubjectBrowseId_returnsStableId() {
    var subject = Map.<String, Object>of(
      "value", "Library science",
      "authorityId", "auth-1",
      "sourceId", "source-1",
      "typeId", "type-1"
    );

    var id = V2BrowseIdComputer.computeSubjectBrowseId(subject);

    assertThat(id).isNotNull().isNotBlank();
    assertThat(V2BrowseIdComputer.computeSubjectBrowseId(subject)).isEqualTo(id);
  }

  @Test
  void computeSubjectBrowseId_returnsNullForEmptyValue() {
    var subject = Map.<String, Object>of("value", "");

    assertThat(V2BrowseIdComputer.computeSubjectBrowseId(subject)).isNull();
  }

  @Test
  void computeClassificationBrowseId_returnsStableId() {
    var classification = Map.<String, Object>of(
      "classificationNumber", "QA76.9",
      "classificationTypeId", "type-1"
    );

    var id = V2BrowseIdComputer.computeClassificationBrowseId(classification);

    assertThat(id).isNotNull().isNotBlank();
    assertThat(V2BrowseIdComputer.computeClassificationBrowseId(classification)).isEqualTo(id);
  }

  @Test
  void computeClassificationBrowseId_returnsNullForEmptyNumber() {
    var classification = Map.<String, Object>of("classificationNumber", "");

    assertThat(V2BrowseIdComputer.computeClassificationBrowseId(classification)).isNull();
  }

  @Test
  void computeCallNumberBrowseId_returnsStableId() {
    var item = Map.<String, Object>of(
      "effectiveCallNumberComponents", Map.of(
        "callNumber", "QA76.9.A25",
        "prefix", "FOLIO",
        "suffix", "v.1",
        "typeId", "type-1"
      )
    );

    var id = V2BrowseIdComputer.computeCallNumberBrowseId(item);

    assertThat(id).isNotNull().isNotBlank();
    assertThat(V2BrowseIdComputer.computeCallNumberBrowseId(item)).isEqualTo(id);
  }

  @Test
  void computeCallNumberBrowseId_returnsNullForMissingComponents() {
    var item = Map.<String, Object>of("id", "item-1");

    assertThat(V2BrowseIdComputer.computeCallNumberBrowseId(item)).isNull();
  }

  @Test
  void computeCallNumberBrowseId_returnsNullForNullCallNumber() {
    var components = new HashMap<String, Object>();
    components.put("callNumber", null);
    var item = Map.<String, Object>of("effectiveCallNumberComponents", components);

    assertThat(V2BrowseIdComputer.computeCallNumberBrowseId(item)).isNull();
  }
}
