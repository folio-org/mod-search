package org.folio.search.service.indexrebuild.derived;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.callnumber.CallNumberRebuildStrategy;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class DerivedRebuildStrategiesTest {

  private static final DerivedRebuildStrategy CALL_NUMBER_STRATEGY = new CallNumberRebuildStrategy(null, null);
  private final DerivedRebuildStrategies strategies = new DerivedRebuildStrategies(List.of(CALL_NUMBER_STRATEGY));

  @Test
  void get_positive_strategyOfTarget() {
    assertThat(strategies.get(IndexRebuildTarget.CALL_NUMBER)).isSameAs(CALL_NUMBER_STRATEGY);
  }

  @Test
  void get_negative_targetNotDerived() {
    assertThatThrownBy(() -> strategies.get(IndexRebuildTarget.ITEM)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findBySource_positive() {
    assertThat(strategies.findBySource(IndexRebuildTarget.ITEM)).containsExactly(CALL_NUMBER_STRATEGY);
    assertThat(strategies.findBySource(IndexRebuildTarget.CALL_NUMBER)).isEmpty();
  }

  @Test
  void derivedTargets_sourceIsDependency() {
    for (var target : IndexRebuildTarget.values()) {
      if (!target.isBuiltFromRecords()) {
        var strategy = strategies.get(target);
        assertThat(target.getDependsOn()).as(target.name()).contains(strategy.source());
      }
    }
  }

  @Test
  void keysOf_positive_singleAndMultipleKeys() {
    assertThat(DerivedIndexReader.keysOf(CALL_NUMBER_STRATEGY, Map.of("callNumberId", "a"))).containsExactly("a");
    assertThat(DerivedIndexReader.keysOf(CALL_NUMBER_STRATEGY, Map.of("callNumberId", List.of("a", "b"))))
      .containsExactlyInAnyOrder("a", "b");
    assertThat(DerivedIndexReader.keysOf(CALL_NUMBER_STRATEGY, Map.of())).isEmpty();
  }

  @Test
  void fromResource_positive() {
    assertThat(IndexRebuildTarget.fromResource("call_number")).isEqualTo(IndexRebuildTarget.CALL_NUMBER);
    assertThatThrownBy(() -> IndexRebuildTarget.fromResource("unknown")).isInstanceOf(IllegalArgumentException.class);
  }
}
