package org.folio.search.service.setter.authority;

import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.utils.AuthoritySearchTestUtils.authorityField;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.keywordField;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HeadingRefProcessorTest {

  @InjectMocks
  private HeadingRefProcessor headingRefProcessor;
  @Mock
  private SearchFieldProvider fieldProvider;

  @Test
  void getFieldValue_positive_personalName() {
    when(fieldProvider.getPlainFieldByPath(AUTHORITY, "id")).thenReturn(of(keywordField()));
    when(fieldProvider.getPlainFieldByPath(AUTHORITY, "personalName")).thenReturn(of(authorityField()));
    var authority = new Authority().id(RESOURCE_ID).personalName("test-name").saftPersonalName(List.of("test-name-2"));
    var actual = headingRefProcessor.getFieldValue(toMap(authority));
    assertThat(actual).isEqualTo("test-name");
  }

  @Test
  void getFieldValue_positive_sftPersonalName() {
    when(fieldProvider.getPlainFieldByPath(AUTHORITY, "sftPersonalName")).thenReturn(of(authorityField()));
    var authority = new Authority().sftPersonalName(List.of("test-name")).saftPersonalName(List.of("test-name-2"));
    var actual = headingRefProcessor.getFieldValue(toMap(authority));
    assertThat(actual).isEqualTo("test-name");
  }

  @Test
  void getFieldValue_positive_personalNameNullValue() {
    var actual = headingRefProcessor.getFieldValue(mapOf("personalName", null));
    assertThat(actual).isNull();
  }

  @Test
  void getFieldValue_positive_invalidValueByPath() {
    when(fieldProvider.getPlainFieldByPath(AUTHORITY, "testField")).thenReturn(of(authorityField()));
    var actual = headingRefProcessor.getFieldValue(mapOf("testField", 123));
    assertThat(actual).isNull();
  }

  @Test
  void getFieldValue_positive_invalidIterableValueByPath() {
    when(fieldProvider.getPlainFieldByPath(AUTHORITY, "testField")).thenReturn(of(authorityField()));
    var actual = headingRefProcessor.getFieldValue(mapOf("testField", singletonList(mapOf("key", "value"))));
    assertThat(actual).isNull();
  }
}
