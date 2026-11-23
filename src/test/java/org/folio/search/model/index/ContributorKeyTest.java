package org.folio.search.model.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.utils.ShaUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ContributorKeyTest {

  @Test
  void of_positive_idIsCompatibleWithContributorTables() {
    assertThat(ContributorKey.of("  John ", "nameType", "auth")).hasValueSatisfying(key -> {
      assertThat(key.name()).isEqualTo("John");
      assertThat(key.id()).isEqualTo(ShaUtils.sha("John", "nameType", "auth"));
    });
  }

  @Test
  void of_positive_nullAndEmptyIdsGiveSameId() {
    var withNulls = ContributorKey.of("John", null, null).orElseThrow();
    var withEmpty = ContributorKey.of("John", "", "").orElseThrow();

    assertThat(withNulls.id()).isEqualTo(withEmpty.id()).isEqualTo(ShaUtils.sha("John", "", ""));
  }

  @Test
  void of_positive_sameNameWithDifferentNameTypeOrAuthorityGivesDifferentIds() {
    var plain = ContributorKey.of("John", null, null).orElseThrow();
    var withNameType = ContributorKey.of("John", "nameType", null).orElseThrow();
    var withAuthority = ContributorKey.of("John", null, "auth").orElseThrow();

    assertThat(plain.id()).isNotEqualTo(withNameType.id()).isNotEqualTo(withAuthority.id());
    assertThat(withNameType.id()).isNotEqualTo(withAuthority.id());
  }

  @Test
  void of_positive_nameIsTruncated() {
    assertThat(ContributorKey.of("n".repeat(300), null, null))
      .hasValueSatisfying(key -> assertThat(key.name()).hasSize(255));
  }

  @Test
  void of_negative_blankName() {
    assertThat(ContributorKey.of("  ", "nameType", "auth")).isEmpty();
    assertThat(ContributorKey.of(null, "nameType", "auth")).isEmpty();
  }

  @Test
  void fromContributor_positive_readsContributorFields() {
    var contributor = Map.of("name", "John", "contributorNameTypeId", "nameType", "authorityId", "auth",
      "contributorTypeId", "type");

    assertThat(ContributorKey.fromContributor(contributor)).hasValueSatisfying(key -> {
      assertThat(key.nameTypeId()).isEqualTo("nameType");
      assertThat(key.authorityId()).isEqualTo("auth");
      assertThat(key.id()).isEqualTo(ShaUtils.sha("John", "nameType", "auth"));
    });
  }

  @Test
  void fromContributor_positive_missingIdsAreNull() {
    var contributor = new HashMap<String, Object>();
    contributor.put("name", "John");
    contributor.put("authorityId", null);

    assertThat(ContributorKey.fromContributor(contributor)).hasValueSatisfying(key -> {
      assertThat(key.nameTypeId()).isNull();
      assertThat(key.authorityId()).isNull();
      assertThat(key.id()).isEqualTo(ShaUtils.sha("John", "", ""));
    });
  }

  @Test
  void fromContributor_negative_noName() {
    assertThat(ContributorKey.fromContributor(Map.of("authorityId", "auth"))).isEmpty();
    assertThat(ContributorKey.fromContributor(null)).isEmpty();
  }
}
