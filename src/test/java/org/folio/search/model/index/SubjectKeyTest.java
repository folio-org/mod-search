package org.folio.search.model.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.folio.search.utils.ShaUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class SubjectKeyTest {

  @Test
  void of_positive_trimsValueAndCalculatesId() {
    assertThat(SubjectKey.of("  Music ", "auth", "source", "type")).hasValueSatisfying(key -> {
      assertThat(key.value()).isEqualTo("Music");
      assertThat(key.authorityId()).isEqualTo("auth");
      assertThat(key.sourceId()).isEqualTo("source");
      assertThat(key.typeId()).isEqualTo("type");
      assertThat(key.id()).isEqualTo(ShaUtils.sha("Music", "auth", "source", "type"));
    });
  }

  @Test
  void of_positive_nullAndEmptyIdsGiveSameId() {
    var withNulls = SubjectKey.of("Music", null, null, null).orElseThrow();
    var withEmpty = SubjectKey.of("Music", "", "", "").orElseThrow();

    assertThat(withNulls.id()).isEqualTo(withEmpty.id()).isEqualTo(ShaUtils.sha("Music", "", "", ""));
  }

  @Test
  void of_positive_sameValueWithDifferentSourceOrTypeGivesDifferentIds() {
    var plain = SubjectKey.of("Music", null, null, null).orElseThrow();
    var withSource = SubjectKey.of("Music", null, "source", null).orElseThrow();
    var withType = SubjectKey.of("Music", null, null, "type").orElseThrow();

    assertThat(plain.id()).isNotEqualTo(withSource.id()).isNotEqualTo(withType.id());
    assertThat(withSource.id()).isNotEqualTo(withType.id());
  }

  @Test
  void of_positive_valueIsTruncated() {
    assertThat(SubjectKey.of("v".repeat(300), null, null, null))
      .hasValueSatisfying(key -> assertThat(key.value()).hasSize(255));
  }

  @Test
  void of_negative_blankValue() {
    assertThat(SubjectKey.of("  ", "auth", "source", "type")).isEmpty();
    assertThat(SubjectKey.of(null, "auth", "source", "type")).isEmpty();
  }

  @Test
  void fromSubject_positive_readsSubjectFields() {
    var subject = Map.of("value", "Music", "authorityId", "auth", "sourceId", "source", "typeId", "type");

    assertThat(SubjectKey.fromSubject(subject)).hasValueSatisfying(key -> {
      assertThat(key.authorityId()).isEqualTo("auth");
      assertThat(key.sourceId()).isEqualTo("source");
      assertThat(key.typeId()).isEqualTo("type");
      assertThat(key.id()).isEqualTo(ShaUtils.sha("Music", "auth", "source", "type"));
    });
  }

  @Test
  void fromSubject_positive_missingIdsAreNull() {
    var subject = new HashMap<String, Object>();
    subject.put("value", "Music");
    subject.put("authorityId", null);

    assertThat(SubjectKey.fromSubject(subject)).hasValueSatisfying(key -> {
      assertThat(key.authorityId()).isNull();
      assertThat(key.sourceId()).isNull();
      assertThat(key.typeId()).isNull();
      assertThat(key.id()).isEqualTo(ShaUtils.sha("Music", "", "", ""));
    });
  }

  @Test
  void fromSubject_negative_noValue() {
    assertThat(SubjectKey.fromSubject(Map.of("authorityId", "auth"))).isEmpty();
    assertThat(SubjectKey.fromSubject(null)).isEmpty();
  }
}
