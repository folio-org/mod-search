package org.folio.search.service.indexrebuild;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class IndexRebuildRangeIdsTest {

  @Test
  void isRebuildRange_positive_generatedId() {
    assertThat(IndexRebuildRangeIds.isRebuildRange(IndexRebuildRangeIds.newId().toString())).isTrue();
  }

  @Test
  void newId_positive_generatesUniqueIdsAcceptedByInventory() {
    var id = IndexRebuildRangeIds.newId();

    assertThat(id).isNotEqualTo(IndexRebuildRangeIds.newId());
    assertThat(id.toString()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
  }

  @Test
  void isRebuildRange_negative_regularReindexRangeId() {
    assertThat(IndexRebuildRangeIds.isRebuildRange(UUID.randomUUID().toString())).isFalse();
  }

  @Test
  void isRebuildRange_negative_invalidValues() {
    assertThat(IndexRebuildRangeIds.isRebuildRange(null)).isFalse();
    assertThat(IndexRebuildRangeIds.isRebuildRange("not-a-uuid")).isFalse();
  }
}
