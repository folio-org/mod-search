package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getMultilangValue;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.converter.ConversionContext;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.consortium.FeatureConfigServiceDecorator;
import org.folio.search.service.converter.SearchFieldsProcessorTest.TestContextConfiguration;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@UnitTest
@Import(TestContextConfiguration.class)
@SpringBootTest(classes = SearchFieldsProcessor.class, webEnvironment = NONE)
class SearchFieldsProcessorTest {

  private static final String FIELD = "generated";
  @Autowired
  private SearchFieldsProcessor searchFieldsProcessor;
  @MockBean
  private FeatureConfigServiceDecorator featureConfigService;

  @Test
  void getSearchFields_positive_emptySearchFields() {
    var desc = description(Instance.class, emptyMap());
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());
    var actual = searchFieldsProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getSearchFields_positive_instanceWithKeywordField() {
    var desc = description(Instance.class, mapOf(FIELD, searchField("instanceTitleProcessor", "keyword")));
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());
    var actual = searchFieldsProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "instance_title"));
  }

  @Test
  void getSearchFields_positive_instanceWithMapFieldProcessor() {
    var searchFieldDescriptor = searchField("mapFieldProcessor", "keyword");
    searchFieldDescriptor.setRawProcessing(true);
    var desc = description(Instance.class, mapOf(FIELD, searchFieldDescriptor));
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());

    var actual = searchFieldsProcessor.getSearchFields(ctx);

    assertThat(actual).isEqualTo(mapOf(FIELD, "map_field"));
  }

  @Test
  void getSearchFields_positive_searchFieldForEnabledFeature() {
    var searchFieldDescriptor = searchField("instanceTitleProcessor", "keyword");
    searchFieldDescriptor.setDependsOnFeature(SEARCH_ALL_FIELDS);

    var desc = description(Instance.class, mapOf(FIELD, searchFieldDescriptor));
    when(featureConfigService.isEnabled(SEARCH_ALL_FIELDS)).thenReturn(true);
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());

    var actual = searchFieldsProcessor.getSearchFields(ctx);

    assertThat(actual).isEqualTo(mapOf("generated", "instance_title"));
  }

  @Test
  void getSearchFields_positive_searchFieldForDisabledFeature() {
    var searchFieldDescriptor = searchField("instanceTitleProcessor", "keyword");
    searchFieldDescriptor.setDependsOnFeature(SEARCH_ALL_FIELDS);

    var desc = description(Instance.class, mapOf(FIELD, searchFieldDescriptor));
    when(featureConfigService.isEnabled(SEARCH_ALL_FIELDS)).thenReturn(false);
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());

    var actual = searchFieldsProcessor.getSearchFields(ctx);

    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getSearchFields_positive_instanceWithMultilangField() {
    var searchField = searchField("instanceTitleProcessor", MULTILANG_FIELD_TYPE);
    var desc = description(Instance.class, mapOf(FIELD, searchField));
    var languages = singletonList("eng");
    var ctx = ConversionContext.of(resourceEvent(), desc, languages);

    var actual = searchFieldsProcessor.getSearchFields(ctx);

    assertThat(actual).isEqualTo(getMultilangValue(FIELD, "instance_title", languages));
  }

  @Test
  void getSearchFields_positive_testClass() {
    var desc = description(TestClass.class, mapOf(FIELD, searchField("testClassProcessor", "keyword")));
    var ctx = ConversionContext.of(resourceEvent(), desc, emptyList());
    var actual = searchFieldsProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "test_class_value"));
  }

  @Test
  void getSearchFields_positive_rawMapResource() {
    var desc = description(null, mapOf(FIELD, searchField("mapFieldProcessor", "keyword")));
    var ctx = ConversionContext.of(resourceEvent(RESOURCE_NAME, emptyMap()), desc, emptyList());
    var actual = searchFieldsProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "map_field"));
  }

  @DisplayName("getSearchFields_negative_parameterized")
  @ParameterizedTest(name = "[{index}] given={0}, type={1}, expected='{}'")
  @CsvSource({
    "testClassProcessor,object",
    "throwingExceptionProcessor,object",
    "emptyValueProcessor,object",
    "emptyValueProcessor,list",
    "emptyValueProcessor,array",
    "emptyValueProcessor,set",
    "emptyValueProcessor,string",
    "emptyValueProcessor,map"
  })
  void getSearchFields_negative_parameterized(String processorName, String type) {
    var desc = description(null, mapOf(FIELD, searchField(processorName, "keyword")));
    var ctx = ConversionContext.of(resourceEvent(RESOURCE_NAME, mapOf("type", type)), desc, emptyList());
    var actual = searchFieldsProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(emptyMap());
  }

  private static ResourceDescription description(Class<?> clazz, Map<String, SearchFieldDescriptor> searchFields) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setEventBodyJavaClass(clazz);
    resourceDescription.setSearchFields(searchFields);
    resourceDescription.setName(RESOURCE_NAME);
    return resourceDescription;
  }

  private static SearchFieldDescriptor searchField(String processor, String index) {
    var searchFieldDescriptor = new SearchFieldDescriptor();
    searchFieldDescriptor.setProcessor(processor);
    searchFieldDescriptor.setIndex(index);
    return searchFieldDescriptor;
  }

  @TestConfiguration
  static class TestContextConfiguration {

    @Bean
    JsonConverter jsonConverter() {
      return new JsonConverter(OBJECT_MAPPER);
    }

    @Bean
    FieldProcessor<Instance, String> instanceTitleProcessor() {
      return value -> "instance_title";
    }

    @Bean
    FieldProcessor<Map<String, Object>, String> mapFieldProcessor() {
      return map -> "map_field";
    }

    @Bean
    FieldProcessor<TestClass, String> testClassProcessor() {
      return test -> "test_class_value";
    }

    @Bean
    FieldProcessor<Map<String, Object>, String> throwingExceptionProcessor() {
      return test -> {
        throw new RuntimeException("error");
      };
    }

    @Bean
    FieldProcessor<Map<String, Object>, Object> emptyValueProcessor() {
      return value -> {
        var type = MapUtils.getString(value, "type");
        switch (type) {
          case "array":
            return new String[] { };
          case "list":
            return emptyList();
          case "map":
            return emptyMap();
          case "set":
            return emptySet();
          case "string":
            return "";
          default:
            return null;
        }
      };
    }
  }

  private static class TestClass { }
}
