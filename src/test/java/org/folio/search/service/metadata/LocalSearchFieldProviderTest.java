package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
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
    assertThat(fields).containsExactlyInAnyOrder("title1.*", "title2.sub1", "title2.sub2.*", "title2.sub3.sub4");
  }

  @Test
  void getFieldByInventorySearchType_positive_nonExistingResource() {
    var fields = searchFieldProvider.getFields("some-resource", TITLE_SEARCH_TYPE);
    assertThat(fields).isEmpty();
  }

  @Test
  void getSourceFields_positive() {
    var actual = searchFieldProvider.getSourceFields(RESOURCE_NAME);
    assertThat(actual).containsExactlyInAnyOrder("id", "title1.src", "title2.sub1",
      "title2.sub3.sub5.src", "source");
  }

  @Test
  void getSourceFields_positive_nonExistingResource() {
    var actual = searchFieldProvider.getSourceFields("unknown-resource");
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldAddStarNotationForMultilangField() {
    when(localResourceProvider.getResourceDescription(eq(RESOURCE_NAME)))
      .thenReturn(Optional.of(resourceDescriptions().get(0)));

    assertThat(searchFieldProvider.getFields(RESOURCE_NAME, "title2.sub3.sub5"))
      .containsExactly("title2.sub3.sub5.*");
  }

  private static List<ResourceDescription> resourceDescriptions() {
    return List.of(
      resourceDescription(mapOf(
        "id", plainField("keyword", true),
        "title1", plainField("multilang", true, TITLE_SEARCH_TYPE),
        "title2", objectField(mapOf(
          "sub1", plainField("keyword", true, TITLE_SEARCH_TYPE),
          "sub2", plainField("multilang", false, TITLE_SEARCH_TYPE),
          "sub3", objectField(mapOf(
            "sub4", plainField("keyword", false, TITLE_SEARCH_TYPE),
            "sub5", plainField("multilang", true))))),
        "source", plainField("keyword", true)))
    );
  }

  private static PlainFieldDescription plainField(String index, boolean showInResponse, String... searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex(index);
    fieldDescription.setInventorySearchTypes(List.of(searchTypes));
    fieldDescription.setShowInResponse(showInResponse);
    return fieldDescription;
  }
}
