package org.folio.search.service.metadata;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.searchField;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.service.setter.FieldProcessor;
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
class ResourceDescriptionServiceTest {

  @Mock private LocalSearchFieldProvider localSearchFieldProvider;
  @Mock private LocalResourceProvider localResourceProvider;
  @InjectMocks private ResourceDescriptionService descriptionService;

  @BeforeEach
  void setUp() {
    var resourceDescription = resourceDescription();
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    when(localSearchFieldProvider.getSearchFieldType(MULTILANG_FIELD_TYPE))
      .thenReturn(multilangField());
    descriptionService.init();
  }

  @Test
  void get_positive() {
    var actual = descriptionService.get(RESOURCE_NAME);
    assertThat(actual).isEqualTo(resourceDescription());
  }

  @Test
  void get_negative() {
    assertThatThrownBy(() -> descriptionService.get("not_existing_resource"))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Resource description not found [resourceName: not_existing_resource]");
  }

  @Test
  void getAll_positive() {
    var actual = descriptionService.getAll();
    assertThat(actual).isEqualTo(List.of(resourceDescription()));
  }

  @Test
  void isSupportedLanguage_positive() {
    assertThat(descriptionService.isSupportedLanguage("eng")).isTrue();
  }

  @Test
  void isSupportedLanguage_negative() {
    assertThat(descriptionService.isSupportedLanguage("rus")).isFalse();
  }

  @Test
  void shouldPassInitIfPropertyProcessorExists() {
    var processors = Map.<String, FieldProcessor<?>>of("populatedByProcessor", map -> "populatedByValue");
    var resourceDescription = resourceDescription();
    resourceDescription.setSearchFields(Map.of("populatedByField",
      searchField("populatedByProcessor")));

    when(localResourceProvider.getResourceDescriptions())
      .thenReturn(List.of(resourceDescription));

    descriptionService = new ResourceDescriptionService(localSearchFieldProvider,
      localResourceProvider, processors);

    descriptionService.init();

    assertThat(descriptionService.get(RESOURCE_NAME).getSearchFields())
      .containsKey("populatedByField");
  }

  @Test
  void shouldFailInitIfUndefinedPropertySetterSpecified() {
    var resourceDescription = resourceDescription();
    resourceDescription.setSearchFields(Map.of("populatedByField",
      searchField("populatedByProcessor")));

    when(localResourceProvider.getResourceDescriptions())
      .thenReturn(List.of(resourceDescription));

    descriptionService = new ResourceDescriptionService(localSearchFieldProvider,
      localResourceProvider, emptyMap());

    assertThatThrownBy(() -> descriptionService.init())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("There is no such processor [populatedByProcessor] required for field "
        + "[populatedByField]");
  }

  private static Map<String, FieldDescription> resourceDescriptionFields() {
    return mapOf(
      "id", plainField("keyword"),
      "lang", keywordField(),
      "isbn", plainField("keyword"),
      "unsupportedField", new TestFieldDescription(),
      "nested", objectField(mapOf(
        "nested_language", keywordField())));
  }

  private static ResourceDescription resourceDescription() {
    return TestUtils.resourceDescription(resourceDescriptionFields());
  }

  private static SearchFieldType multilangField() {
    var indexFieldType = new SearchFieldType();
    indexFieldType.setMapping(jsonObject(
      "properties", jsonObject(
        "eng", jsonObject("type", "text"),
        "spa", jsonObject("type", "text"),
        "fra", jsonObject("type", "text"))));
    return indexFieldType;
  }

  private static class TestFieldDescription extends FieldDescription {}
}
