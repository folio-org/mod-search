package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class V2BrowseIndexNameResolverTest {

  @Test
  void resolveV2BrowseType_mapsContributor() {
    assertThat(V2BrowseIndexNameResolver.resolveV2BrowseType(ResourceType.INSTANCE_CONTRIBUTOR))
      .isEqualTo(ResourceType.V2_CONTRIBUTOR);
  }

  @Test
  void resolveV2BrowseType_mapsSubject() {
    assertThat(V2BrowseIndexNameResolver.resolveV2BrowseType(ResourceType.INSTANCE_SUBJECT))
      .isEqualTo(ResourceType.V2_SUBJECT);
  }

  @Test
  void resolveV2BrowseType_mapsClassification() {
    assertThat(V2BrowseIndexNameResolver.resolveV2BrowseType(ResourceType.INSTANCE_CLASSIFICATION))
      .isEqualTo(ResourceType.V2_CLASSIFICATION);
  }

  @Test
  void resolveV2BrowseType_mapsCallNumber() {
    assertThat(V2BrowseIndexNameResolver.resolveV2BrowseType(ResourceType.INSTANCE_CALL_NUMBER))
      .isEqualTo(ResourceType.V2_CALL_NUMBER);
  }

  @Test
  void resolveV2BrowseType_throwsForNonBrowseType() {
    assertThatThrownBy(() -> V2BrowseIndexNameResolver.resolveV2BrowseType(ResourceType.INSTANCE))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isV1BrowseType_trueForBrowseTypes() {
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.INSTANCE_CONTRIBUTOR)).isTrue();
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.INSTANCE_SUBJECT)).isTrue();
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.INSTANCE_CLASSIFICATION)).isTrue();
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.INSTANCE_CALL_NUMBER)).isTrue();
  }

  @Test
  void isV1BrowseType_falseForNonBrowseTypes() {
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.INSTANCE)).isFalse();
    assertThat(V2BrowseIndexNameResolver.isV1BrowseType(ResourceType.V2_CONTRIBUTOR)).isFalse();
  }
}
