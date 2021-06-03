package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.metadata.PlainFieldDescription.MULTILANG_FIELD_TYPE;
import static org.folio.search.utils.SearchUtils.getMultilangValue;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.service.converter.SearchFieldProcessorTest.TestContextConfiguration;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@UnitTest
@Import(TestContextConfiguration.class)
@SpringBootTest(classes = SearchFieldProcessor.class, webEnvironment = NONE)
class SearchFieldProcessorTest {

  private static final String FIELD = "generated";
  @Autowired SearchFieldProcessor searchFieldProcessor;

  @Test
  void getSearchFields_positive_instanceWithKeywordField() {
    var desc = description(Instance.class, mapOf(FIELD, searchField("instanceTitleProcessor", "keyword")));
    var ctx = ConversionContext.of(TENANT_ID, emptyMap(), desc, emptyList());
    var actual = searchFieldProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "instance_title"));
  }

  @Test
  void getSearchFields_positive_instanceWithMultilangField() {
    var searchField = searchField("instanceTitleProcessor", MULTILANG_FIELD_TYPE);
    var desc = description(Instance.class, mapOf(FIELD, searchField));
    var ctx = ConversionContext.of(TENANT_ID, emptyMap(), desc, singletonList("eng"));

    var actual = searchFieldProcessor.getSearchFields(ctx);

    assertThat(actual).isEqualTo(getMultilangValue(FIELD, "instance_title", ctx));
  }

  @Test
  void getSearchFields_positive_testClass() {
    var desc = description(TestClass.class, mapOf(FIELD, searchField("testClassProcessor", "keyword")));
    var ctx = ConversionContext.of(TENANT_ID, emptyMap(), desc, emptyList());
    var actual = searchFieldProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "test_class_value"));
  }

  @Test
  void getSearchFields_positive_rawMapResource() {
    var desc = description(null, mapOf(FIELD, searchField("mapFieldProcessor", "keyword")));
    var ctx = ConversionContext.of(TENANT_ID, emptyMap(), desc, emptyList());
    var actual = searchFieldProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(mapOf(FIELD, "map_field"));
  }

  @Test
  void getSearchFields_negative_rawMapResourceWithInvalidProcessor() {
    var desc = description(null, mapOf(FIELD, searchField("testClassProcessor", "keyword")));
    var ctx = ConversionContext.of(TENANT_ID, emptyMap(), desc, emptyList());
    var actual = searchFieldProcessor.getSearchFields(ctx);
    assertThat(actual).isEqualTo(emptyMap());
  }

  private static ResourceDescription description(Class<?> clazz, Map<String, SearchFieldDescriptor> searchFields) {
    var resourceDescription = new ResourceDescription();
    resourceDescription.setEventBodyJavaClass(clazz);
    resourceDescription.setSearchFields(searchFields);
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
  }

  private static class TestClass {}
}
