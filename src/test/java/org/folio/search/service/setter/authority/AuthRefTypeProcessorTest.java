package org.folio.search.service.setter.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.support.utils.AuthoritySearchTestUtils.authorityField;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class AuthRefTypeProcessorTest {

  @InjectMocks
  private AuthRefTypeProcessor authRefTypeProcessor;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getFieldValue_positive() {
    var expectedAuthRefType = "Authorized";
    var desc = authorityField(null, expectedAuthRefType);

    when(searchFieldProvider.getPlainFieldByPath(AUTHORITY, "personalName")).thenReturn(Optional.of(desc));
    var actual = authRefTypeProcessor.getFieldValue(mapOf("personalName", "a personal name"));

    assertThat(actual).isEqualTo(expectedAuthRefType);
  }

  @Test
  void getFieldValue_positive_emptyValue() {
    var actual = authRefTypeProcessor.getFieldValue(mapOf("personalName", null));
    assertThat(actual).isNull();
  }
}
