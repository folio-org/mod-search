package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.model.types.ResourceType.UNKNOWN;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestUtils.keywordField;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.objectField;
import static org.folio.search.utils.TestUtils.plainField;
import static org.folio.search.utils.TestUtils.searchField;
import static org.folio.search.utils.TestUtils.secondaryResourceDescription;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.service.metadata.ResourceDescriptionServiceTest.TestContextConfiguration;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@Import(TestContextConfiguration.class)
@SpringBootTest(classes = ResourceDescriptionService.class, webEnvironment = NONE)
class ResourceDescriptionServiceTest {

  private static final String FIELD = "field";

  @Autowired
  private ResourceDescriptionService descriptionService;
  @MockitoBean
  private LocalResourceProvider localResourceProvider;

  @BeforeEach
  void setUp() {
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(
      resourceDescription(), secondaryResourceDescription(INSTANCE_SUBJECT, INSTANCE)));
    descriptionService.init();
  }

  @Test
  void get_positive() {
    var actual = descriptionService.get(INSTANCE);
    assertThat(actual).isEqualTo(resourceDescription());
  }

  @Test
  void get_negative() {
    assertThatThrownBy(() -> descriptionService.get(UNKNOWN))
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Resource description not found [resourceType: unknown]");
  }

  @Test
  void find_positive() {
    var actual = descriptionService.find(INSTANCE);
    assertThat(actual).isEqualTo(Optional.of(resourceDescription()));
  }

  @Test
  void find_negative() {
    var actual = descriptionService.find(UNKNOWN);
    assertThat(actual).isEmpty();
  }

  @Test
  void findAll_positive() {
    var actual = descriptionService.findAll();
    assertThat(actual).containsExactlyInAnyOrder(
      resourceDescription(), secondaryResourceDescription(INSTANCE_SUBJECT, INSTANCE));
  }

  @Test
  void getResourceTypes_positive() {
    var actual = descriptionService.getResourceTypes();
    assertThat(actual).containsExactly(INSTANCE, INSTANCE_SUBJECT);
  }

  @Test
  void shouldPassInitIfPropertyProcessorExists() {
    var resourceDescription = resourceDescription(null, Map.of(FIELD, searchField("testProcessor")));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    descriptionService.init();
    assertThat(descriptionService.get(INSTANCE).getSearchFields()).containsKey(FIELD);
  }

  @Test
  void initService_positive_validFieldProcessor() {
    var searchFields = Map.of(FIELD, searchField("testEntityClassProcessor"));
    var resourceDescription = resourceDescription(TestEntityClass.class, searchFields);
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    descriptionService.init();
    assertThat(descriptionService.get(INSTANCE).getSearchFields()).containsKey(FIELD);
  }

  @Test
  void shouldFailInitIfUndefinedPropertySetterSpecified() {
    var resourceDescription = resourceDescription(null, Map.of(FIELD, searchField("unknownProcessor")));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    assertThatThrownBy(() -> descriptionService.init())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Found error(s) in resource description(s):\n"
        + "instance: ('Field processor not found [field: 'field', processorName: 'unknownProcessor']')");
  }

  @Test
  void initService_negative_processorWithUnresolvedGenericsMultiple() {
    var resourceDescription = resourceDescription(null, mapOf(
      "field1", searchField("unresolvedGenerics"), "field2", searchField("unresolvedGenerics")));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    assertThatThrownBy(() -> descriptionService.init())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Found error(s) in resource description(s):\n"
        + "instance: ("
        + "'Generic class for field processor not found [field: 'field1', processorName: 'unresolvedGenerics']', "
        + "'Generic class for field processor not found [field: 'field2', processorName: 'unresolvedGenerics']')");
  }

  @Test
  void initService_negative_testEntityClassProcessorWithInvalidGenericType() {
    var resourceDescription = resourceDescription(TestEntityClass.class, mapOf(FIELD, searchField("testProcessor")));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    assertThatThrownBy(() -> descriptionService.init())
      .isInstanceOf(ResourceDescriptionException.class)
      .hasMessage("Found error(s) in resource description(s):\n"
        + "instance: ('Invalid generic type in field processor, "
        + "must be instance of 'org.folio.search.service.metadata.ResourceDescriptionServiceTest$TestEntityClass'"
        + ", resolved value was 'java.util.Map' [field: 'field', processorName: 'testProcessor']')");
  }

  @Test
  void initService_negative_testEntityClassProcessorValidForRawProcessing() {
    var testProcessor = searchField("testProcessor");
    testProcessor.setRawProcessing(true);
    var resourceDescription = resourceDescription(TestEntityClass.class, mapOf(FIELD, testProcessor));
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription));
    descriptionService.init();
    assertThat(descriptionService.get(INSTANCE).getSearchFields()).containsKey(FIELD);
  }

  @Test
  void getSecondaryResourceTypes_positive() {
    var actual = descriptionService.getSecondaryResourceTypes(INSTANCE);
    assertThat(actual).isEqualTo(List.of(INSTANCE_SUBJECT));
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

  private static ResourceDescription resourceDescription(
    Class<?> eventClass, Map<String, SearchFieldDescriptor> searchFields) {
    var resourceDescription = resourceDescription();
    resourceDescription.setEventBodyJavaClass(eventClass);
    resourceDescription.setSearchFields(searchFields);
    return resourceDescription;
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

  private static final class TestFieldDescription extends FieldDescription { }

  /**
   * Beans in that method cannot be converted to lambda because in that case they cannot be processed by spring tools
   * and verified by {@link ResourceDescriptionService} (impossible to extract generics from lambda implementation).
   */
  @TestConfiguration
  @SuppressWarnings("Convert2Lambda")
  static class TestContextConfiguration {

    @Bean
    FieldProcessor<Map<String, Object>, String> testProcessor() {
      return new FieldProcessor<>() {
        @Override
        public String getFieldValue(Map<String, Object> eventBody) {
          return null;
        }
      };
    }

    @Bean
    FieldProcessor<TestEntityClass, String> testEntityClassProcessor() {
      return new FieldProcessor<>() {
        @Override
        public String getFieldValue(TestEntityClass eventBody) {
          return null;
        }
      };
    }

    @Bean
    FieldProcessor<String, String> unresolvedGenerics() {
      return val -> null;
    }

    @Bean
    LocalSearchFieldProvider mockLocalSearchFieldProvider() {
      var fieldProvider = mock(LocalSearchFieldProvider.class);
      when(fieldProvider.getSearchFieldType(MULTILANG_FIELD_TYPE)).thenReturn(multilangField());
      return fieldProvider;
    }

    @Bean
    LocalResourceProvider mockLocalResourceProvider() {
      var resourceProvider = mock(LocalResourceProvider.class);
      when(resourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription()));
      return resourceProvider;
    }
  }

  private static final class TestEntityClass { }
}
