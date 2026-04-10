package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class V2BrowseIdExtractorTest {

  @Test
  void computeFromRawInstanceRecord_computesBrowseIds() {
    var record = Map.<String, Object>of(
      "contributors", List.of(
        Map.of("name", "Smith, John", "contributorNameTypeId", "type-1")
      ),
      "subjects", List.of(
        Map.of("value", "Library science")
      ),
      "classifications", List.of(
        Map.of("classificationNumber", "QA76", "classificationTypeId", "lcc-type")
      )
    );

    var result = V2BrowseIdExtractor.computeFromRawInstanceRecord(record);

    assertThat(result.contributorIds()).hasSize(1);
    assertThat(result.subjectIds()).hasSize(1);
    assertThat(result.classificationIds()).hasSize(1);
    assertThat(result.callNumberIds()).isEmpty();
  }

  @Test
  void computeFromRawItemRecord_computesCallNumberBrowseId() {
    var record = Map.<String, Object>of(
      "effectiveCallNumberComponents", Map.of(
        "callNumber", "QA76.9.A25",
        "prefix", "FOLIO",
        "typeId", "type-1"
      )
    );

    var result = V2BrowseIdExtractor.computeFromRawItemRecord(record);

    assertThat(result.callNumberIds()).hasSize(1);
  }

  @Test
  void touchedBrowseIds_merge_combinesIds() {
    var ids1 = new V2BrowseIdExtractor.TouchedBrowseIds(
      new java.util.HashSet<>(java.util.Set.of("c1")),
      new java.util.HashSet<>(java.util.Set.of("s1")),
      new java.util.HashSet<>(),
      new java.util.HashSet<>()
    );
    var ids2 = new V2BrowseIdExtractor.TouchedBrowseIds(
      new java.util.HashSet<>(java.util.Set.of("c2")),
      new java.util.HashSet<>(java.util.Set.of("s1", "s2")),
      new java.util.HashSet<>(java.util.Set.of("cl1")),
      new java.util.HashSet<>(java.util.Set.of("cn1"))
    );

    ids1.merge(ids2);

    assertThat(ids1.contributorIds()).containsExactlyInAnyOrder("c1", "c2");
    assertThat(ids1.subjectIds()).containsExactlyInAnyOrder("s1", "s2");
    assertThat(ids1.classificationIds()).containsExactly("cl1");
    assertThat(ids1.callNumberIds()).containsExactly("cn1");
  }

  @Test
  void touchedBrowseIds_empty_isTrue() {
    var ids = V2BrowseIdExtractor.TouchedBrowseIds.empty();

    assertThat(ids.isEmpty()).isTrue();
  }

  @Test
  void touchedBrowseIds_empty_isFalseWhenHasIds() {
    var ids = new V2BrowseIdExtractor.TouchedBrowseIds(
      new java.util.HashSet<>(java.util.Set.of("c1")),
      new java.util.HashSet<>(),
      new java.util.HashSet<>(),
      new java.util.HashSet<>()
    );

    assertThat(ids.isEmpty()).isFalse();
  }
}
