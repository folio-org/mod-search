package org.folio.search.service.setter.linkeddata.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.service.lccn.StringNormalizer;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LinkedDataAuthorityTypeProcessorTest {

  @Mock
  private StringNormalizer stringNormalizer;
  @InjectMocks
  private LinkedDataAuthorityTypeProcessor processor;

  @Test
  void getFieldValue_positive_returnsEmptySet_whenTypesAreNull() {
    var actual = processor.getFieldValue(new LinkedDataAuthority());

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_returnsEmptySet_whenTypesAreEmpty() {
    var authority = new LinkedDataAuthority().types(List.of());

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldValue_positive_normalizesTypesUsingStringNormalizer() {
    var authority = new LinkedDataAuthority().types(List.of("Personal Name", "Corporate Name"));
    lenient().when(stringNormalizer.apply(eq("Personal Name"))).thenReturn(Optional.of("personal name"));
    lenient().when(stringNormalizer.apply(eq("Corporate Name"))).thenReturn(Optional.of("corporate name"));

    var actual = processor.getFieldValue(authority);

    assertThat(actual).isEqualTo(Set.of("personal name", "corporate name"));
  }
}
