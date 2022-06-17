package org.folio.search.service.setter.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.metadata.PlainFieldDescription.STANDARD_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.search.model.metadata.AuthorityFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HeadingTypeProcessorTest {

  @InjectMocks
  private HeadingTypeProcessor headingTypeProcessor;
  @Mock
  private SearchFieldProvider searchFieldProvider;

  @Test
  void getFieldValue_positive() {
    var expectedHeadingType = "Personal Name";
    var desc = new AuthorityFieldDescription();
    desc.setIndex(STANDARD_FIELD_TYPE);
    desc.setHeadingType(expectedHeadingType);

    when(searchFieldProvider.getPlainFieldByPath(AUTHORITY_RESOURCE, "personalName")).thenReturn(Optional.of(desc));
    var actual = headingTypeProcessor.getFieldValue(mapOf("personalName", "a personal name"));

    assertThat(actual).isEqualTo(expectedHeadingType);
  }

  @Test
  void getFieldValue_positive_emptyValue() {
    var actual = headingTypeProcessor.getFieldValue(mapOf("personalName", null));
    assertThat(actual).isEqualTo("Other");
  }
}
