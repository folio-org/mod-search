package org.folio.search.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.multilangField;
import static org.folio.search.utils.TestUtils.objectField;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.folio.search.exception.ResourceDescriptionException;
import org.folio.search.model.metadata.FieldDescription;
import org.folio.search.model.metadata.PlainFieldDescription;
import org.folio.search.model.metadata.PostProcessResourceDescriptionConverter;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.SearchFieldDescriptor;
import org.folio.search.model.metadata.SearchFieldType;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class LocalSearchFieldProviderTest {

  private static final String TITLE_SEARCH_TYPE = "title";

  @InjectMocks private LocalSearchFieldProvider searchFieldProvider;
  @Mock private LocalResourceProvider localResourceProvider;
  private final PostProcessResourceDescriptionConverter converter = new PostProcessResourceDescriptionConverter();

  @BeforeEach
  void setUp() {
    when(localResourceProvider.getResourceDescriptions()).thenReturn(List.of(resourceDescription()));
    when(localResourceProvider.getSearchFieldTypes()).thenReturn(mapOf(
      "keyword", new SearchFieldType(), "multilang", SearchFieldType.of(multilangMappings())));
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
    assertThat(fields).containsExactlyInAnyOrder(
      "title1.*", "title2.sub1", "title2.sub2.*", "title2.sub3.sub4", "search1");
  }

  @Test
  void getFieldByInventorySearchType_positive_nonExistingResource() {
    var fields = searchFieldProvider.getFields("some-resource", TITLE_SEARCH_TYPE);
    assertThat(fields).isEmpty();
  }

  @Test
  void getSourceFields_positive() {
    var actual = searchFieldProvider.getSourceFields(RESOURCE_NAME);
    assertThat(actual).containsExactlyInAnyOrder("id", "plain_title1", "title2.sub1",
      "title2.sub3.plain_sub5", "source");
  }

  @Test
  void getSourceFields_positive_nonExistingResource() {
    var actual = searchFieldProvider.getSourceFields("unknown-resource");
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldAddStarNotationForMultilangField() {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    assertThat(searchFieldProvider.getFields(RESOURCE_NAME, "title2.sub3.sub5")).containsExactly("title2.sub3.sub5.*");
  }

  @MethodSource("getPlainFieldsByPathDataProvider")
  @ParameterizedTest(name = "[{index}] path={0}")
  void getPlainFieldByPath_positive_parameterized(String path, FieldDescription expected) {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, path);
    assertThat(actual).isEqualTo(Optional.ofNullable(expected));
  }

  @Test
  void getPlainFieldByPath_positive() {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "id");
    assertThat(actual).isPresent().get().isEqualTo(plainField("keyword", true));
  }

  @DisplayName("getPlainFieldByPath_parameterized")
  @ParameterizedTest(name = "[{index}] value=''{0}''")
  @CsvSource({",", "'',", "'   '", "path", "title.sub3"})
  void getPlainFieldByPath_negative_parameterized(String path) {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, path);
    assertThat(actual).isEmpty();
  }

  @Test
  void getFieldByPath_negative_resourceDescriptionNotFound() {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.empty());
    var actual = searchFieldProvider.getPlainFieldByPath(RESOURCE_NAME, "id");
    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("isSupportedLanguage_parameterized")
  @CsvSource({"eng,true", "ara,false", "spa,true"})
  void isSupportedLanguage_parameterized(String language, boolean expected) {
    var actual = searchFieldProvider.isSupportedLanguage(language);
    assertThat(actual).isEqualTo(expected);
  }

  @CsvSource({
    "cql.all,allInstance.*;plain_allInstance;allItems.*;plain_allItems;allHoldings.*;plain_allHoldings",
    "cql.allInstance,allInstance.*;plain_allInstance",
    "cql.allItems,allItems.*;plain_allItems",
    "cql.allHoldings,allHoldings.*;plain_allHoldings",
  })
  @ParameterizedTest
  @DisplayName("getFields_cqlAll_parameterized")
  void getFields_cqlAll_parameterized(String inventorySearchType, String fieldsAsString) {
    var expectedFields = List.of(fieldsAsString.split(";"));
    var actual = searchFieldProvider.getFields(RESOURCE_NAME, inventorySearchType);
    assertThat(actual).isEqualTo(expectedFields);
  }

  @ParameterizedTest
  @DisplayName("isMultilangField_parameterized")
  @CsvSource({"id,false", "allItems,true", "title1,true", "title2,false", "title2.sub1,false", "title2.sub2,true"})
  void isMultilangField_parameterized(String fieldName, boolean expected) {
    when(localResourceProvider.getResourceDescription(RESOURCE_NAME)).thenReturn(Optional.of(resourceDescription()));
    var actual = searchFieldProvider.isMultilangField(RESOURCE_NAME, fieldName);
    assertThat(actual).isEqualTo(expected);
  }

  private ResourceDescription resourceDescription() {
    return converter.convert(
      resourceDescription(mapOf(
          "id", plainField("keyword", true),
          "allInstance", multilangField("cql.all", "cql.allInstance"),
          "allItems", multilangField("cql.all", "cql.allItems"),
          "allHoldings", multilangField("cql.all", "cql.allHoldings"),
          "title1", plainField("multilang", true, TITLE_SEARCH_TYPE),
          "title2", objectField(mapOf(
            "sub1", plainField("keyword", true, TITLE_SEARCH_TYPE),
            "sub2", plainField("multilang", false, TITLE_SEARCH_TYPE),
            "sub3", objectField(mapOf(
              "sub4", plainField("keyword", false, TITLE_SEARCH_TYPE),
              "sub5", plainField("multilang", true))))),
          "source", plainField("keyword", true)),
        mapOf(
          "search1", searchField(TITLE_SEARCH_TYPE),
          "search2", searchField()))
    );
  }

  private static ResourceDescription resourceDescription(
    Map<String, FieldDescription> fields, Map<String, SearchFieldDescriptor> searchFields) {

    var resourceDescription = TestUtils.resourceDescription(fields);
    resourceDescription.setSearchFields(searchFields);

    return resourceDescription;
  }

  private ObjectNode multilangMappings() {
    return jsonObject("properties", jsonObject(
      "eng", jsonObject("type", "text", "analyzer", "english"),
      "ger", jsonObject("type", "text", "analyzer", "german"),
      "spa", jsonObject("type", "text", "analyzer", "spanish"),
      "src", jsonObject("type", "text", "analyzer", "standard")
    ));
  }

  private static Stream<Arguments> getPlainFieldsByPathDataProvider() {
    return Stream.of(
      arguments("id", plainField("keyword", true)),
      arguments("title2.sub1", plainField("keyword", true, TITLE_SEARCH_TYPE)),
      arguments("title2.sub3.sub4", plainField("keyword", false, TITLE_SEARCH_TYPE)),
      arguments("title2.sub3", null),
      arguments("title4", null),
      arguments("title1.subfield", null),
      arguments("title2.subfield", null),
      arguments("title2.sub.sub.sub", null)
    );
  }

  private static PlainFieldDescription plainField(String index, boolean showInResponse, String... searchTypes) {
    var fieldDescription = new PlainFieldDescription();
    fieldDescription.setIndex(index);
    fieldDescription.setInventorySearchTypes(List.of(searchTypes));
    fieldDescription.setShowInResponse(showInResponse);
    return fieldDescription;
  }

  private static SearchFieldDescriptor searchField(String... searchTypes) {
    var fieldDescription = new SearchFieldDescriptor();
    fieldDescription.setIndex("keyword");
    fieldDescription.setInventorySearchTypes(List.of(searchTypes));
    fieldDescription.setProcessor("processor");
    return fieldDescription;
  }
}
