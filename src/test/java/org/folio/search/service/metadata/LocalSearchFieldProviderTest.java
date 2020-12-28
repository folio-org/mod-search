package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.mockito.Mockito.when;

import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocalSearchFieldProviderTest {

  @Mock private LocalResourceProvider localResourceProvider;
  @InjectMocks private LocalSearchFieldProvider searchFieldProvider;

  @BeforeEach
  void setUp() {
    when(localResourceProvider.getSearchFieldTypes()).thenReturn(mapOf(
      "keyword", new SearchFieldType()));
    searchFieldProvider.init();
  }

  @Test
  void getSearchFieldType_positive() {
    var actual = searchFieldProvider.getSearchFieldType("keyword");
    assertThat(actual).isEqualTo(new SearchFieldType());
  }

  @Test
  void getSearchFieldType_negative() {
    assertThatThrownBy(() -> searchFieldProvider.getSearchFieldType(null))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessageContaining("Failed to find search field type [fieldType: null]");
  }
}
