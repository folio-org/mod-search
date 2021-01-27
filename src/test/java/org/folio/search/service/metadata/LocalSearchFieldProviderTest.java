package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.utils.TestUtils;
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

  private static final String TITLE_SEARCH_TYPE = "title";

  @InjectMocks private LocalSearchFieldProvider searchFieldProvider;
  @Mock private LocalResourceProvider localResourceProvider;

  @BeforeEach
  void setUp() {
    when(localResourceProvider.getSearchFieldTypes()).thenReturn(mapOf("keyword", new SearchFieldType()));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(resourceDescriptions());
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

  @Test
  void getFieldByInventorySearchType_positive() {
    var fields = searchFieldProvider.getFields(RESOURCE_NAME, TITLE_SEARCH_TYPE);
    assertThat(fields).containsExactly("title1.*", "title2.sub1", "title2.sub2.*");
  }

  @Test
  void getFieldByInventorySearchType_positive_nonExistingResource() {
    var fields = searchFieldProvider.getFields("some-resource", TITLE_SEARCH_TYPE);
    assertThat(fields).isEmpty();
  }

  private static List<ResourceDescription> resourceDescriptions() {
    return List.of(
      resourceDescription(mapOf(
        "id", TestUtils.plainField("keyword", "$.id"),
        "title1", plainField("multilang", TITLE_SEARCH_TYPE),
        "title2", objectField(mapOf(
          "sub1", plainField("keyword", TITLE_SEARCH_TYPE),
          "sub2", plainField("multilang", TITLE_SEARCH_TYPE))),
        "source", TestUtils.plainField("keyword", "$.source")))
    );
  }

  private static PlainFieldDescription plainField(String index, String... searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex(index);
    fieldDescription.setInventorySearchTypes(List.of(searchTypes));
    return fieldDescription;
  }
}
