package org.folio.search.service.indexrebuild.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.item.ItemRecordsRebuildStrategy;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RecordsRebuildStrategiesTest {

  private static final RecordsRebuildStrategy ITEM_STRATEGY = new ItemRecordsRebuildStrategy(null, null);
  private final RecordsRebuildStrategies strategies = new RecordsRebuildStrategies(List.of(ITEM_STRATEGY));

  @Test
  void get_positive_strategyOfTarget() {
    assertThat(strategies.get(IndexRebuildTarget.ITEM)).isSameAs(ITEM_STRATEGY);
  }

  @Test
  void get_negative_targetNotBuiltFromRecords() {
    assertThatThrownBy(() -> strategies.get(IndexRebuildTarget.CALL_NUMBER))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void find_positive_byRecordType() {
    assertThat(strategies.find(ReindexRecordType.ITEM)).containsSame(ITEM_STRATEGY);
    assertThat(strategies.find(ReindexRecordType.HOLDINGS)).isEmpty();
  }

  @Test
  void builtFromRecords_matchesRegisteredStrategies() {
    for (var target : IndexRebuildTarget.values()) {
      assertThat(target.isBuiltFromRecords()).as(target.name())
        .isEqualTo(target == IndexRebuildTarget.ITEM);
    }
  }
}
