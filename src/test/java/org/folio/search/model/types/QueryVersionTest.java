package org.folio.search.model.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.folio.search.model.service.QueryResolution;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class QueryVersionTest {

  @Test
  void fromString_roundTripsV1ByValue() {
    var result = QueryVersion.fromString("1");
    assertThat(result).isEqualTo(QueryVersion.V1);
    assertThat(result.getValue()).isEqualTo("1");
    assertThat(result.getIndexPrefix()).isEqualTo("instance");
    assertThat(result.getResourceType()).isEqualTo(ResourceType.INSTANCE);
    assertThat(result.getPathType()).isEqualTo(QueryResolution.PathType.LEGACY);
  }

  @Test
  void fromString_roundTripsV2ByValue() {
    var result = QueryVersion.fromString("2");
    assertThat(result).isEqualTo(QueryVersion.V2);
    assertThat(result.getValue()).isEqualTo("2");
    assertThat(result.getIndexPrefix()).isEqualTo("instance_search");
    assertThat(result.getResourceType()).isEqualTo(ResourceType.INSTANCE_SEARCH);
    assertThat(result.getPathType()).isEqualTo(QueryResolution.PathType.FLAT);
  }

  @Test
  void fromString_acceptsEnumName() {
    assertThat(QueryVersion.fromString("V1")).isEqualTo(QueryVersion.V1);
    assertThat(QueryVersion.fromString("V2")).isEqualTo(QueryVersion.V2);
    assertThat(QueryVersion.fromString("v1")).isEqualTo(QueryVersion.V1);
  }

  @Test
  void fromString_throwsForUnknown() {
    assertThatThrownBy(() -> QueryVersion.fromString("3"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unknown query version: 3");
  }

  @Test
  void fromString_throwsForNull() {
    assertThatThrownBy(() -> QueryVersion.fromString(null))
      .isInstanceOf(Exception.class);
  }

  @Test
  void getDefault_returnsV1() {
    assertThat(QueryVersion.getDefault()).isEqualTo(QueryVersion.V1);
  }
}
