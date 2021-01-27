package org.folio.search.converter;

import static org.folio.search.converter.LanguageConfigConverter.toLanguageConfigEntity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;
import org.folio.search.domain.dto.LanguageConfig;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LanguageConfigConverterTest {
  @Test
  void shouldGenerateIdForEntityIfNotSet() {
    assertThat(toLanguageConfigEntity(new LanguageConfig().code("eng")), allOf(
      hasProperty("code", is("eng")),
      hasProperty("id", notNullValue())
    ));
  }

  @Test
  void shouldUseExistingIdForEntityIfSet() {
    final var expectedId = UUID.randomUUID();
    final var config = new LanguageConfig().code("eng").id(expectedId.toString());

    assertThat(toLanguageConfigEntity(config), allOf(
      hasProperty("code", is("eng")),
      hasProperty("id", is(expectedId))
    ));
  }
}
